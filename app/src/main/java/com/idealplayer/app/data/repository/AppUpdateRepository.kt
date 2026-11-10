package com.idealplayer.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.idealplayer.app.BuildConfig
import com.idealplayer.app.core.common.limitedTo
import com.idealplayer.app.core.update.AppUpdateCheckResult
import com.idealplayer.app.core.update.AppUpdateInfo
import com.idealplayer.app.core.update.AppUpdateManifest
import com.idealplayer.app.core.update.isTrustedUpdateSigner
import com.idealplayer.app.core.update.isTrustedUpdateUrl
import com.idealplayer.app.core.update.resolveTrustedUpdateRedirect
import com.idealplayer.app.core.update.toAvailableUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }
    // Redirects are followed manually below so an HTTPS update source cannot downgrade to
    // cleartext through the globally cleartext-compatible IPTV client configuration.
    private val updateHttpClient by lazy {
        okHttpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    suspend fun checkForUpdate(): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        if (!BuildConfig.SELF_HOSTED_UPDATES_ENABLED) {
            return@withContext AppUpdateCheckResult.Disabled
        }
        val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL.trim()
        if (manifestUrl.isBlank()) return@withContext AppUpdateCheckResult.Disabled
        val parsedManifestUrl = manifestUrl.toHttpUrlOrNull()
            ?.takeIf { it.isTrustedUpdateUrl() }
            ?: throw IllegalStateException("Update manifest URL must use HTTPS")

        executeSecureGet(parsedManifestUrl, cacheControl = "no-cache").use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Update check failed: HTTP ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Update manifest is empty")
            if (body.contentLength() > MAX_MANIFEST_BYTES) {
                throw IllegalStateException("Update manifest is larger than the allowed limit")
            }
            val manifestPayload = body.byteStream()
                .limitedTo(MAX_MANIFEST_BYTES)
                .readBytes()
                .toString(Charsets.UTF_8)
            val manifest = parseManifest(manifestPayload)
            val update = manifest.toAvailableUpdate(
                currentVersionCode = BuildConfig.VERSION_CODE,
                // A manifest may have followed an allowed HTTPS redirect; relative APK links
                // must resolve against that final location rather than the original URL.
                resolvedApkUrl = resolveApkUrl(response.request.url.toString(), manifest.apkUrl)
            )

            if (update == null) AppUpdateCheckResult.NotAvailable else AppUpdateCheckResult.Available(update)
        }
    }

    private fun parseManifest(payload: String): AppUpdateManifest {
        val root = json.parseToJsonElement(payload).jsonObject
        return AppUpdateManifest(
            versionCode = root["versionCode"]?.jsonPrimitive?.intOrNull ?: 0,
            versionName = root["versionName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            apkUrl = root["apkUrl"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            sha256 = root["sha256"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            mandatory = root["mandatory"]?.jsonPrimitive?.booleanOrNull ?: false,
            minSupportedVersionCode = root["minSupportedVersionCode"]?.jsonPrimitive?.intOrNull ?: 0,
            releaseNotes = root["releaseNotes"]?.jsonPrimitive?.contentOrNull.orEmpty()
        )
    }

    suspend fun downloadApk(
        update: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val apkUrl = update.apkUrl.toHttpUrlOrNull()
            ?.takeIf { it.isTrustedUpdateUrl() }
            ?: throw IllegalStateException("Update APK URL must use HTTPS")
        val outputDir = File(context.cacheDir, "shared/updates").apply { mkdirs() }
        val outputFile = File(outputDir, "IdealPlayer-${update.versionCode}.apk")
        val tempFile = File(outputDir, "${outputFile.name}.download")
        val digest = MessageDigest.getInstance("SHA-256")

        try {
            executeSecureGet(apkUrl).use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("APK download failed: HTTP ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("APK download returned an empty response")
                val totalBytes = body.contentLength()
                if (totalBytes > MAX_APK_BYTES) {
                    throw IllegalStateException("APK download is larger than the allowed limit")
                }
                var readBytes = 0L

                body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            readBytes += read
                            if (readBytes > MAX_APK_BYTES) {
                                throw IllegalStateException("APK download is larger than the allowed limit")
                            }
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            if (totalBytes > 0) {
                                onProgress(((readBytes * 100) / totalBytes).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
            }

            val actualSha256 = digest.digest().toHex()
            val expectedSha256 = update.sha256.normalizedSha256()
            if (expectedSha256.length != 64 || actualSha256 != expectedSha256) {
                throw IllegalStateException("Downloaded APK checksum did not match")
            }

            if (outputFile.exists() && !outputFile.delete()) {
                throw IllegalStateException("Previous APK update could not be replaced")
            }
            if (!tempFile.renameTo(outputFile)) {
                throw IllegalStateException("Downloaded APK could not be saved")
            }
            verifyDownloadedApk(outputFile, update.versionCode)
        } catch (error: Exception) {
            tempFile.delete()
            outputFile.delete()
            throw error
        }

        onProgress(100)
        outputFile
    }

    fun canRequestPackageInstalls(): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun createInstallIntent(apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )

        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun createUnknownAppSourcesIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun resolveApkUrl(manifestUrl: String, apkUrl: String): String {
        if (apkUrl.isBlank()) return ""
        return runCatching {
            manifestUrl.toHttpUrlOrNull()?.resolve(apkUrl)?.toString()
        }.getOrNull() ?: apkUrl
    }

    /**
     * Follows only a small chain of explicit HTTPS redirects. OkHttp's default redirect policy
     * would permit an HTTPS request to downgrade through an HTTP Location because the shared
     * client supports user-supplied cleartext IPTV endpoints.
     */
    private fun executeSecureGet(initialUrl: okhttp3.HttpUrl, cacheControl: String? = null): Response {
        var currentUrl = initialUrl
        repeat(MAX_UPDATE_REDIRECTS + 1) { redirectCount ->
            val request = Request.Builder()
                .url(currentUrl)
                .get()
                .apply {
                    cacheControl?.let { header("Cache-Control", it) }
                }
                .build()
            val response = updateHttpClient.newCall(request).execute()
            if (!response.isRedirect) return response

            val redirectUrl = resolveTrustedUpdateRedirect(currentUrl, response.header("Location"))
            response.close()
            if (redirectUrl == null) {
                throw IllegalStateException("Update redirect must use HTTPS without URL credentials")
            }
            if (redirectCount == MAX_UPDATE_REDIRECTS) {
                throw IllegalStateException("Update redirect limit exceeded")
            }
            currentUrl = redirectUrl
        }
        error("Unreachable")
    }

    private fun verifyDownloadedApk(apkFile: File, expectedVersionCode: Int) {
        val packageManager = context.packageManager
        val flags = packageSigningFlags()
        val archiveInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: throw IllegalStateException("Downloaded file is not a valid APK")
        check(archiveInfo.packageName == context.packageName) {
            "Downloaded APK package does not match this app"
        }
        check(archiveInfo.versionCodeCompat() == expectedVersionCode.toLong()) {
            "Downloaded APK version does not match the update manifest"
        }

        val installedInfo = packageManager.getPackageInfo(context.packageName, flags)
        check(
            isTrustedUpdateSigner(
                installedCurrentSigners = installedInfo.currentSignerDigests(),
                archiveCurrentSigners = archiveInfo.currentSignerDigests(),
                archiveSignerLineage = archiveInfo.signerLineageDigests()
            )
        ) {
            "Downloaded APK signer does not match this app"
        }
    }

    private fun packageSigningFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }

    private fun PackageInfo.currentSignerDigests(): Set<String> = signingCertificates(
        includeHistory = false
    )

    private fun PackageInfo.signerLineageDigests(): Set<String> = signingCertificates(
        includeHistory = true
    )

    private fun PackageInfo.signingCertificates(includeHistory: Boolean): Set<String> {
        val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.let { signingInfo ->
                if (includeHistory && signingInfo.hasPastSigningCertificates()) {
                    signingInfo.signingCertificateHistory
                } else {
                    signingInfo.apkContentsSigners
                }
            }
        } else {
            @Suppress("DEPRECATION")
            signatures
        }
        return certificates.orEmpty()
            .map { certificate -> MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray()).toHex() }
            .toSet()
    }

    private fun PackageInfo.versionCodeCompat(): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun String.normalizedSha256(): String =
        trim()
            .replace(":", "")
            .lowercase(Locale.ROOT)

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val MAX_MANIFEST_BYTES = 256L * 1024L
        const val MAX_APK_BYTES = 500L * 1024L * 1024L
        const val MAX_UPDATE_REDIRECTS = 5
    }
}
