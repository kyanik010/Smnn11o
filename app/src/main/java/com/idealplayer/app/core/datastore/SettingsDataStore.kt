package com.idealplayer.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.idealplayer.app.core.common.Constants

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "idealplayer_settings")

data class AppSettings(
    // App General
    val appLanguage: String = "ar",
    val playerEngine: String = "EXOPLAYER",


    val seekForwardMs: Long = Constants.DEFAULT_SEEK_FORWARD_MS,
    val seekBackwardMs: Long = Constants.DEFAULT_SEEK_BACKWARD_MS,
    val aspectRatio: String = "FIT",
    val defaultPlaybackSpeed: Float = 1f,
    val autoResumePlayback: Boolean = true,
    val continueWatching: Boolean = true,
    val autoPlayNextEpisode: Boolean = true,
    val controllerAutoHideMs: Long = Constants.CONTROLLER_AUTO_HIDE_MS,

    // Audio & Subtitle
    val defaultSubtitleLanguage: String = "off",
    val defaultAudioLanguage: String = "system",
    val autoEnableSubtitles: Boolean = false,
    val subtitleSize: Int = 18,
    val subtitleColor: Long = 0xFFFFFFFF,
    val subtitleBackground: Long = 0x80000000,
    val rememberTrackPerContent: Boolean = false,

    // Video Quality
    val videoQualityMode: String = "AUTO",
    val defaultDisplayMode: String = "FIT",
    val preferHwDecoding: Boolean = true,
    val allowQualityFallback: Boolean = true,
    val autoPlayerFallback: Boolean = true,
    val hardwareAcceleration: Int = 1,
    val bufferDurationMs: Int = Constants.DEFAULT_BUFFER_MS,

    // Live TV
    val liveLatencyMode: String = "BALANCED",
    val liveReconnectOnFailure: Boolean = true,
    val rememberLastChannel: Boolean = true,
    val startFullscreenLive: Boolean = true,

    // Playlist
    val openLastPlaylist: Boolean = false,
    val lastPlaylistId: Long = -1,

    // Quality per channel (legacy)
    val rememberQualityPerChannel: Boolean = false,

    // EPG
    val epgUrl: String = "",
    val epgLastSync: Long = 0L,
    val epgAutoSync: Boolean = true
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val store = context.dataStore

    private object Keys {
        // App General
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val PLAYER_ENGINE = stringPreferencesKey("player_engine")


        val SEEK_FORWARD = longPreferencesKey("seek_forward_ms")
        val SEEK_BACKWARD = longPreferencesKey("seek_backward_ms")
        val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
        val DEFAULT_PLAYBACK_SPEED = floatPreferencesKey("default_playback_speed")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume_playback")
        val CONTINUE_WATCHING = booleanPreferencesKey("continue_watching")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val CONTROLLER_AUTO_HIDE = longPreferencesKey("controller_auto_hide_ms")

        // Audio & Subtitle
        val SUBTITLE_LANG = stringPreferencesKey("subtitle_language")
        val AUDIO_LANG = stringPreferencesKey("audio_language")
        val AUTO_ENABLE_SUBTITLES = booleanPreferencesKey("auto_enable_subtitles")
        val SUBTITLE_SIZE = intPreferencesKey("subtitle_size")
        val SUBTITLE_COLOR = longPreferencesKey("subtitle_color")
        val SUBTITLE_BG = longPreferencesKey("subtitle_background")
        val REMEMBER_TRACK_PER_CONTENT = booleanPreferencesKey("remember_track_per_content")

        // Video Quality
        val VIDEO_QUALITY_MODE = stringPreferencesKey("video_quality_mode")
        val DEFAULT_DISPLAY_MODE = stringPreferencesKey("default_display_mode")
        val PREFER_HW_DECODING = booleanPreferencesKey("prefer_hw_decoding")
        val ALLOW_QUALITY_FALLBACK = booleanPreferencesKey("allow_quality_fallback")
        val AUTO_PLAYER_FALLBACK = booleanPreferencesKey("auto_player_fallback")
        val PLAYBACK_ENGINE_PROFILES = stringSetPreferencesKey("playback_engine_profiles")
        val HW_ACCEL = intPreferencesKey("hw_acceleration")
        val BUFFER_DURATION = intPreferencesKey("buffer_duration_ms")

        // Live TV
        val LIVE_LATENCY_MODE = stringPreferencesKey("live_latency_mode")
        val LIVE_RECONNECT = booleanPreferencesKey("live_reconnect_on_failure")
        val REMEMBER_LAST_CHANNEL = booleanPreferencesKey("remember_last_channel")
        val START_FULLSCREEN_LIVE = booleanPreferencesKey("start_fullscreen_live")

        // Playlist
        val OPEN_LAST_PLAYLIST = booleanPreferencesKey("open_last_playlist")
        val LAST_PLAYLIST_ID = longPreferencesKey("last_playlist_id")
        val REMEMBER_QUALITY = booleanPreferencesKey("remember_quality_per_channel")

        // EPG
        val EPG_URL = stringPreferencesKey("epg_url")
        val EPG_LAST_SYNC = longPreferencesKey("epg_last_sync")
        val EPG_AUTO_SYNC = booleanPreferencesKey("epg_auto_sync")
    }

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            appLanguage = prefs[Keys.APP_LANGUAGE] ?: "ar",
            playerEngine = prefs[Keys.PLAYER_ENGINE] ?: "EXOPLAYER",

            seekForwardMs = prefs[Keys.SEEK_FORWARD] ?: Constants.DEFAULT_SEEK_FORWARD_MS,
            seekBackwardMs = prefs[Keys.SEEK_BACKWARD] ?: Constants.DEFAULT_SEEK_BACKWARD_MS,
            aspectRatio = prefs[Keys.ASPECT_RATIO] ?: prefs[Keys.DEFAULT_DISPLAY_MODE] ?: "FIT",
            defaultPlaybackSpeed = prefs[Keys.DEFAULT_PLAYBACK_SPEED] ?: 1f,
            autoResumePlayback = prefs[Keys.AUTO_RESUME] ?: true,
            continueWatching = prefs[Keys.CONTINUE_WATCHING] ?: true,
            autoPlayNextEpisode = prefs[Keys.AUTO_PLAY_NEXT] ?: true,
            controllerAutoHideMs = prefs[Keys.CONTROLLER_AUTO_HIDE] ?: Constants.CONTROLLER_AUTO_HIDE_MS,

            defaultSubtitleLanguage = prefs[Keys.SUBTITLE_LANG] ?: "off",
            defaultAudioLanguage = prefs[Keys.AUDIO_LANG] ?: "system",
            autoEnableSubtitles = prefs[Keys.AUTO_ENABLE_SUBTITLES] ?: false,
            subtitleSize = prefs[Keys.SUBTITLE_SIZE] ?: 18,
            subtitleColor = prefs[Keys.SUBTITLE_COLOR] ?: 0xFFFFFFFF,
            subtitleBackground = prefs[Keys.SUBTITLE_BG] ?: 0x80000000,
            rememberTrackPerContent = prefs[Keys.REMEMBER_TRACK_PER_CONTENT] ?: false,

            videoQualityMode = prefs[Keys.VIDEO_QUALITY_MODE] ?: "AUTO",
            defaultDisplayMode = prefs[Keys.DEFAULT_DISPLAY_MODE] ?: prefs[Keys.ASPECT_RATIO] ?: "FIT",
            preferHwDecoding = prefs[Keys.PREFER_HW_DECODING] ?: true,
            allowQualityFallback = prefs[Keys.ALLOW_QUALITY_FALLBACK] ?: true,
            autoPlayerFallback = prefs[Keys.AUTO_PLAYER_FALLBACK] ?: true,
            hardwareAcceleration = prefs[Keys.HW_ACCEL] ?: 1,
            bufferDurationMs = prefs[Keys.BUFFER_DURATION] ?: Constants.DEFAULT_BUFFER_MS,

            liveLatencyMode = prefs[Keys.LIVE_LATENCY_MODE] ?: "BALANCED",
            liveReconnectOnFailure = prefs[Keys.LIVE_RECONNECT] ?: true,
            rememberLastChannel = prefs[Keys.REMEMBER_LAST_CHANNEL] ?: true,
            startFullscreenLive = prefs[Keys.START_FULLSCREEN_LIVE] ?: true,

            openLastPlaylist = prefs[Keys.OPEN_LAST_PLAYLIST] ?: false,
            lastPlaylistId = prefs[Keys.LAST_PLAYLIST_ID] ?: -1,
            rememberQualityPerChannel = prefs[Keys.REMEMBER_QUALITY] ?: false,

            epgUrl = prefs[Keys.EPG_URL] ?: "",
            epgLastSync = prefs[Keys.EPG_LAST_SYNC] ?: 0L,
            epgAutoSync = prefs[Keys.EPG_AUTO_SYNC] ?: true
        )
    }

    // ━━━━━━ App General ━━━━━━
    suspend fun updateAppLanguage(language: String) { store.edit { it[Keys.APP_LANGUAGE] = language } }
    suspend fun updatePlayerEngine(engine: String) { store.edit { it[Keys.PLAYER_ENGINE] = engine } }


    suspend fun updateSeekForward(ms: Long) { store.edit { it[Keys.SEEK_FORWARD] = ms } }
    suspend fun updateSeekBackward(ms: Long) { store.edit { it[Keys.SEEK_BACKWARD] = ms } }
    suspend fun updateAspectRatio(ratio: String) {
        store.edit {
            it[Keys.ASPECT_RATIO] = ratio
            it[Keys.DEFAULT_DISPLAY_MODE] = ratio.uppercase()
        }
    }
    suspend fun updateDefaultPlaybackSpeed(speed: Float) { store.edit { it[Keys.DEFAULT_PLAYBACK_SPEED] = speed } }
    suspend fun updateAutoResume(enabled: Boolean) { store.edit { it[Keys.AUTO_RESUME] = enabled } }
    suspend fun updateContinueWatching(enabled: Boolean) { store.edit { it[Keys.CONTINUE_WATCHING] = enabled } }
    suspend fun updateAutoPlayNext(enabled: Boolean) { store.edit { it[Keys.AUTO_PLAY_NEXT] = enabled } }
    suspend fun updateControllerAutoHide(ms: Long) { store.edit { it[Keys.CONTROLLER_AUTO_HIDE] = ms } }

    // ━━━━━━ Audio & Subtitle ━━━━━━
    suspend fun updateSubtitleLanguage(lang: String) { store.edit { it[Keys.SUBTITLE_LANG] = lang } }
    suspend fun updateAudioLanguage(lang: String) { store.edit { it[Keys.AUDIO_LANG] = lang } }
    suspend fun updateAutoEnableSubtitles(enabled: Boolean) { store.edit { it[Keys.AUTO_ENABLE_SUBTITLES] = enabled } }
    suspend fun updateSubtitleSize(size: Int) { store.edit { it[Keys.SUBTITLE_SIZE] = size } }
    suspend fun updateRememberTrackPerContent(enabled: Boolean) { store.edit { it[Keys.REMEMBER_TRACK_PER_CONTENT] = enabled } }

    // ━━━━━━ Video Quality ━━━━━━
    suspend fun updateVideoQualityMode(mode: String) { store.edit { it[Keys.VIDEO_QUALITY_MODE] = mode } }
    suspend fun updateDefaultDisplayMode(mode: String) {
        store.edit {
            it[Keys.DEFAULT_DISPLAY_MODE] = mode
            it[Keys.ASPECT_RATIO] = mode
        }
    }
    suspend fun updatePreferHwDecoding(enabled: Boolean) { store.edit { it[Keys.PREFER_HW_DECODING] = enabled } }
    suspend fun updateAllowQualityFallback(enabled: Boolean) { store.edit { it[Keys.ALLOW_QUALITY_FALLBACK] = enabled } }
    suspend fun updateAutoPlayerFallback(enabled: Boolean) { store.edit { it[Keys.AUTO_PLAYER_FALLBACK] = enabled } }
    suspend fun updateBufferDuration(ms: Int) { store.edit { it[Keys.BUFFER_DURATION] = ms } }

    // ━━━━━━ Live TV ━━━━━━
    suspend fun updateLiveLatencyMode(mode: String) { store.edit { it[Keys.LIVE_LATENCY_MODE] = mode } }
    suspend fun updateLiveReconnect(enabled: Boolean) { store.edit { it[Keys.LIVE_RECONNECT] = enabled } }
    suspend fun updateRememberLastChannel(enabled: Boolean) { store.edit { it[Keys.REMEMBER_LAST_CHANNEL] = enabled } }
    suspend fun updateStartFullscreenLive(enabled: Boolean) { store.edit { it[Keys.START_FULLSCREEN_LIVE] = enabled } }

    // ━━━━━━ Playlist ━━━━━━
    suspend fun updateOpenLastPlaylist(enabled: Boolean) { store.edit { it[Keys.OPEN_LAST_PLAYLIST] = enabled } }
    suspend fun updateLastPlaylistId(id: Long) { store.edit { it[Keys.LAST_PLAYLIST_ID] = id } }
    suspend fun updateRememberQuality(enabled: Boolean) { store.edit { it[Keys.REMEMBER_QUALITY] = enabled } }

    suspend fun getPlaybackEngineProfile(profileKey: String): String? {
        return store.data.first()[Keys.PLAYBACK_ENGINE_PROFILES]
            .orEmpty()
            .asSequence()
            .mapNotNull(::parsePlaybackEngineProfile)
            .firstOrNull { it.profileKey == profileKey }
            ?.engine
    }

    suspend fun updatePlaybackEngineProfile(profileKey: String, engine: String) {
        if (profileKey.isBlank()) return
        store.edit { preferences ->
            val retained = preferences[Keys.PLAYBACK_ENGINE_PROFILES]
                .orEmpty()
                .asSequence()
                .mapNotNull(::parsePlaybackEngineProfile)
                .filterNot { it.profileKey == profileKey }
                .sortedByDescending(StoredPlaybackEngineProfile::updatedAt)
                .take(MAX_STORED_PLAYBACK_ENGINE_PROFILES - 1)
                .map(StoredPlaybackEngineProfile::encoded)
                .toMutableSet()
            retained += StoredPlaybackEngineProfile(
                updatedAt = System.currentTimeMillis(),
                profileKey = profileKey,
                engine = engine
            ).encoded()
            preferences[Keys.PLAYBACK_ENGINE_PROFILES] = retained
        }
    }

    suspend fun removePlaybackEngineProfile(profileKey: String) {
        store.edit { preferences ->
            preferences[Keys.PLAYBACK_ENGINE_PROFILES] = preferences[Keys.PLAYBACK_ENGINE_PROFILES]
                .orEmpty()
                .mapNotNull(::parsePlaybackEngineProfile)
                .filterNot { it.profileKey == profileKey }
                .map(StoredPlaybackEngineProfile::encoded)
                .toSet()
        }
    }

    // ━━━━━━ EPG ━━━━━━
    suspend fun updateEpgUrl(url: String) { store.edit { it[Keys.EPG_URL] = url } }
    suspend fun updateEpgLastSync(time: Long) { store.edit { it[Keys.EPG_LAST_SYNC] = time } }
    suspend fun updateEpgAutoSync(enabled: Boolean) { store.edit { it[Keys.EPG_AUTO_SYNC] = enabled } }

    // ━━━━━━ Bulk clear ━━━━━━
    suspend fun clearWatchHistory() {
        // This is a signal — actual clearing is done in ContentRepository
    }

    suspend fun clearImageCache() {
        // This is a signal — actual clearing is done in the image loader
    }

    private data class StoredPlaybackEngineProfile(
        val updatedAt: Long,
        val profileKey: String,
        val engine: String
    ) {
        fun encoded(): String = "$updatedAt:$profileKey:$engine"
    }

    private fun parsePlaybackEngineProfile(value: String): StoredPlaybackEngineProfile? {
        val parts = value.split(':', limit = 3)
        val updatedAt = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val profileKey = parts.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
        val engine = parts.getOrNull(2)?.takeIf(String::isNotBlank) ?: return null
        return StoredPlaybackEngineProfile(updatedAt, profileKey, engine)
    }

    private companion object {
        const val MAX_STORED_PLAYBACK_ENGINE_PROFILES = 100
    }
}
