package com.idealplayer.app.data.repository

import android.content.Context
import com.idealplayer.app.BuildConfig
import com.idealplayer.app.core.common.rethrowIfCancellation
import com.idealplayer.app.core.connected.CONNECTED_IDEALPLAYER_PAYLOAD_SOURCE
import com.idealplayer.app.core.connected.CONNECTED_IDEALPLAYER_PAYLOAD_VERSION
import com.idealplayer.app.core.connected.CONNECTED_PAIRING_ALPHABET
import com.idealplayer.app.core.connected.CONNECTED_PAIRING_CODE_LENGTH
import com.idealplayer.app.core.connected.CONNECTED_SETUP_MAX_PLAYLIST_BYTES
import com.idealplayer.app.core.connected.ConnectedPairingResponse
import com.idealplayer.app.core.connected.ConnectedSetupPayload
import com.idealplayer.app.core.datastore.SettingsDataStore
import com.idealplayer.app.core.model.Playlist
import com.idealplayer.app.core.model.PlaylistType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

data class ConnectedPairingSession(
    val code: String,
    val url: String,
    val webBaseUrl: String,
    val expiresAtMs: Long
)

data class ConnectedPlaylistDraft(
    val playlist: Playlist,
    val rememberOnStart: Boolean,
    val epgAutoSync: Boolean,
    val fileName: String = "",
    val fileContent: String = ""
)

@Singleton
class ConnectedSetupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val playlistRepository: PlaylistRepository,
    private val settingsDataStore: SettingsDataStore
) {
    private val secureRandom = SecureRandom()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    val isEnabled: Boolean
        get() = BuildConfig.REMOTE_SETUP_ENABLED &&
            normalizeConnectedSetupBaseUrl(BuildConfig.REMOTE_SETUP_API_BASE_URL, allowLocalhost = false).isNotBlank() &&
            normalizeConnectedSetupBaseUrl(BuildConfig.REMOTE_SETUP_WEB_BASE_URL, allowLocalhost = false).isNotBlank()

    fun createPairingSession(nowMs: Long = System.currentTimeMillis()): ConnectedPairingSession {
        val code = buildConnectedPairingCode(secureRandom)
        val webBaseUrl = requireWebBaseUrl()
        return ConnectedPairingSession(
            code = code,
            url = buildConnectedPairingUrl(
                webBaseUrl = webBaseUrl,
                apiBaseUrl = requireApiBaseUrl(),
                pairingCode = code
            ),
            webBaseUrl = webBaseUrl,
            expiresAtMs = nowMs + PAIRING_TIMEOUT_MS
        )
    }

    suspend fun openPairing(code: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedCode = requireConnectedPairingCode(code)
            val apiBaseUrl = requireApiBaseUrl()
            val requestBody = buildJsonObject {
                put("pairingCode", normalizedCode)
                put("status", PAIRING_STATUS_PENDING)
                put("payload", buildJsonObject { })
            }.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("$apiBaseUrl/api/pairings")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response -> response.isSuccessful }
        }.getOrElse { error ->
            error.rethrowIfCancellation()
            Timber.w("Connected setup pairing open failed: %s", error.javaClass.simpleName)
            false
        }
    }

    suspend fun pollPairing(code: String): ConnectedPairingResponse? = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedCode = requireConnectedPairingCode(code)
            val apiBaseUrl = requireApiBaseUrl()
            val request = Request.Builder()
                .url("$apiBaseUrl/api/pairings/$normalizedCode")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                json.decodeFromString<ConnectedPairingResponse>(body)
            }
        }.getOrElse { error ->
            error.rethrowIfCancellation()
            Timber.w("Connected setup pairing poll failed: %s", error.javaClass.simpleName)
            null
        }
    }

    suspend fun saveConnectedPlaylist(
        expectedPairingCode: String,
        payloadJson: JsonObject
    ): Playlist = withContext(Dispatchers.IO) {
        val payload = json.decodeFromJsonElement<ConnectedSetupPayload>(payloadJson)
        val draft = validateConnectedSetupPayload(expectedPairingCode, payload)
        val filePath = if (draft.playlist.type == PlaylistType.M3U_FILE) {
            writeRemotePlaylistFile(draft.fileName, draft.fileContent)
        } else {
            ""
        }
        val playlist = draft.playlist.copy(filePath = filePath)
        val id = playlistRepository.savePlaylist(playlist)
        settingsDataStore.updateOpenLastPlaylist(draft.rememberOnStart)
        settingsDataStore.updateEpgAutoSync(draft.epgAutoSync)
        // EPG source ownership is app-global and follows the active playlist. The onboarding
        // flow saves a connected playlist before activating it, so defer this assignment to
        // PlaylistRepository after activation rather than replacing another playlist's source.
        playlist.copy(id = id)
    }

    private fun requireApiBaseUrl(): String =
        normalizeConnectedSetupBaseUrl(BuildConfig.REMOTE_SETUP_API_BASE_URL, allowLocalhost = false)
            .ifBlank { error("Connected IdealPlayer API URL must be HTTPS.") }

    private fun requireWebBaseUrl(): String =
        normalizeConnectedSetupBaseUrl(BuildConfig.REMOTE_SETUP_WEB_BASE_URL, allowLocalhost = false)
            .ifBlank { error("Connected IdealPlayer web URL must be HTTPS.") }

    private fun writeRemotePlaylistFile(fileName: String, content: String): String {
        val contentBytes = content.toByteArray(Charsets.UTF_8)
        require(contentBytes.size <= CONNECTED_SETUP_MAX_PLAYLIST_BYTES) {
            "Uzaktan gönderilen playlist dosyası 5 MB sınırını aşıyor."
        }

        val safeName = File(fileName).name
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(96)
            .takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: "connected_${System.currentTimeMillis()}.m3u"
        val directory = File(context.filesDir, "playlists").apply { mkdirs() }
        return File(directory, safeName).apply { writeBytes(contentBytes) }.absolutePath
    }

    companion object {
        const val PAIRING_STATUS_PENDING = "pending"
        const val PAIRING_STATUS_COMPLETED = "completed"
        const val PAIRING_STATUS_ERROR = "error"
        const val PAIRING_STATUS_EXPIRED = "expired"
        const val PAIRING_TIMEOUT_MS = 10 * 60 * 1000L
        const val PAIRING_POLL_INTERVAL_MS = 2_000L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

internal fun buildConnectedPairingCode(
    random: SecureRandom,
    length: Int = CONNECTED_PAIRING_CODE_LENGTH
): String = buildString(length) {
    repeat(length) {
        append(CONNECTED_PAIRING_ALPHABET[random.nextInt(CONNECTED_PAIRING_ALPHABET.length)])
    }
}

internal fun buildConnectedPairingUrl(
    webBaseUrl: String,
    apiBaseUrl: String,
    pairingCode: String
): String {
    val web = normalizeConnectedSetupBaseUrl(webBaseUrl, allowLocalhost = false)
        .ifBlank { error("Connected IdealPlayer web URL must be HTTPS.") }
    val api = normalizeConnectedSetupBaseUrl(apiBaseUrl, allowLocalhost = false)
        .ifBlank { error("Connected IdealPlayer API URL must be HTTPS.") }
    val normalizedCode = requireConnectedPairingCode(pairingCode)
    val encodedCode = URLEncoder.encode(normalizedCode, "UTF-8")
    val encodedApi = URLEncoder.encode(api, "UTF-8")
    return "$web/?code=$encodedCode&api=$encodedApi"
}

internal fun normalizeConnectedSetupBaseUrl(value: String, allowLocalhost: Boolean): String {
    val url = value.trim().toHttpUrlOrNull() ?: return ""
    val isLocalhost = url.host.equals("localhost", ignoreCase = true) || url.host == "127.0.0.1"
    if (url.isHttps.not() && !(allowLocalhost && isLocalhost)) return ""
    return url.newBuilder()
        .query(null)
        .fragment(null)
        .encodedPath(url.encodedPath.trimEnd('/').ifBlank { "/" })
        .build()
        .toString()
        .trimEnd('/')
}

internal fun validateConnectedSetupPayload(
    expectedPairingCode: String,
    payload: ConnectedSetupPayload
): ConnectedPlaylistDraft {
    require(payload.version == CONNECTED_IDEALPLAYER_PAYLOAD_VERSION) {
        "Desteklenmeyen Connected IdealPlayer paket sürümü."
    }
    val source = payload.source.trim().lowercase(Locale.ROOT)
    require(source == CONNECTED_IDEALPLAYER_PAYLOAD_SOURCE) {
        "Connected IdealPlayer kaynak bilgisi doğrulanamadı."
    }
    val expectedCode = normalizeConnectedPairingCode(expectedPairingCode)
    val payloadCode = normalizeConnectedPairingCode(payload.pairingCode)
    require(
        expectedCode.length == CONNECTED_PAIRING_CODE_LENGTH &&
            payloadCode.length == CONNECTED_PAIRING_CODE_LENGTH &&
            payloadCode == expectedCode
    ) {
        "Eşleştirme kodu doğrulanamadı."
    }

    val info = payload.playlist
    val playlistName = info.name.trim().take(96)
    require(playlistName.isNotBlank()) { "Liste adı gerekli." }
    val epgUrl = info.epgUrl.trim()
    if (epgUrl.isNotBlank()) require(isHttpOrHttpsUrl(epgUrl)) { "EPG URL geçerli değil." }

    return when (info.type.trim().lowercase(Locale.ROOT)) {
        "xtream", "xc", "portal" -> {
            val serverUrl = info.serverUrl.trim()
            require(isHttpOrHttpsUrl(serverUrl)) { "Xtream sunucu URL geçerli değil." }
            require(info.username.isNotBlank()) { "Xtream kullanıcı adı gerekli." }
            require(info.password.isNotBlank()) { "Xtream şifresi gerekli." }
            ConnectedPlaylistDraft(
                playlist = Playlist(
                    name = playlistName,
                    type = PlaylistType.XTREAM_CODES,
                    epgUrl = epgUrl,
                    serverUrl = serverUrl,
                    username = info.username.trim(),
                    password = info.password
                ),
                rememberOnStart = info.rememberOnStart,
                epgAutoSync = info.epgAutoSync
            )
        }

        "m3u", "m3u_url", "url" -> {
            val playlistUrl = info.url.trim()
            require(isHttpOrHttpsUrl(playlistUrl)) { "M3U URL geçerli değil." }
            ConnectedPlaylistDraft(
                playlist = Playlist(
                    name = playlistName,
                    type = PlaylistType.M3U_URL,
                    url = playlistUrl,
                    epgUrl = epgUrl
                ),
                rememberOnStart = info.rememberOnStart,
                epgAutoSync = info.epgAutoSync
            )
        }

        "file", "m3u_file" -> {
            val content = info.fileContent
            val bytes = content.toByteArray(Charsets.UTF_8)
            require(content.isNotBlank()) { "M3U dosyası boş." }
            require(info.fileSize <= 0 || info.fileSize <= CONNECTED_SETUP_MAX_PLAYLIST_BYTES) {
                "Uzaktan gönderilen playlist dosyası 5 MB sınırını aşıyor."
            }
            require(bytes.size <= CONNECTED_SETUP_MAX_PLAYLIST_BYTES) {
                "Uzaktan gönderilen playlist dosyası 5 MB sınırını aşıyor."
            }
            ConnectedPlaylistDraft(
                playlist = Playlist(
                    name = playlistName,
                    type = PlaylistType.M3U_FILE,
                    epgUrl = epgUrl
                ),
                rememberOnStart = info.rememberOnStart,
                epgAutoSync = info.epgAutoSync,
                fileName = info.fileName,
                fileContent = content
            )
        }

        else -> throw IllegalArgumentException("Desteklenmeyen playlist tipi.")
    }
}

internal fun normalizeConnectedPairingCode(value: String): String =
    value.trim().uppercase(Locale.ROOT).filter { it in CONNECTED_PAIRING_ALPHABET }

private fun requireConnectedPairingCode(value: String): String =
    normalizeConnectedPairingCode(value).also { normalizedCode ->
        require(normalizedCode.length == CONNECTED_PAIRING_CODE_LENGTH) {
            "Connected IdealPlayer pairing code must be $CONNECTED_PAIRING_CODE_LENGTH characters."
        }
    }

private fun isHttpOrHttpsUrl(value: String): Boolean =
    value.trim().toHttpUrlOrNull()?.let { it.isHttps || it.scheme == "http" } == true
