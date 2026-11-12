package com.idealplayer.app.core.player

import kotlinx.coroutines.flow.StateFlow

enum class PlaybackProfile {
    LIVE,
    VOD
}

/**
 * Display / Aspect Ratio modes available to the user.
 */
enum class AspectRatioMode(val label: String) {
    AUTO("Auto"),
    FIT("Fit to Screen"),
    FILL("Fill Screen"),
    ZOOM("Zoom / Crop"),
    STRETCH("Stretch"),
    ORIGINAL("Original"),
    FORCE_16_9("Force 16:9"),
    FORCE_4_3("Force 4:3")
}

/**
 * Video quality preference mode.
 */
enum class VideoQualityMode(val label: String) {
    AUTO("Auto"),
    BEST("Best Quality"),
    BALANCED("Balanced"),
    DATA_SAVER("Data Saver")
}

/**
 * Live stream latency preference.
 */
enum class LiveLatencyMode(val label: String) {
    LOW_LATENCY("Low Latency"),
    BALANCED("Balanced"),
    STABLE("Stable")
}

/**
 * Playback lifecycle states.
 */
enum class PlaybackState {
    IDLE, BUFFERING, PLAYING, PAUSED, ENDED, ERROR, STOPPED
}

/**
 * Represents a selectable audio or subtitle track.
 */
data class TrackInfo(
    val index: Int,
    val name: String,
    val language: String = "",
    val isSelected: Boolean = false,
    val codec: String = "",
    val channelCount: Int = 0,
    val role: String = "",
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isHearingImpaired: Boolean = false
)

/**
 * Represents a selectable video quality track.
 */
data class QualityOption(
    val index: Int,
    val label: String,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Int = 0,
    val isSelected: Boolean = false,
    val isAdaptive: Boolean = false
)

/**
 * Full player state exposed to the UI.
 */
data class PlayerState(
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val bufferedPosition: Long = 0,
    val isSeekable: Boolean = false,
    val playbackSpeed: Float = 1f,
    val audioTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),
    val selectedAudioTrack: Int = -1,
    val selectedSubtitleTrack: Int = -1,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val errorMessage: String? = null,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    // Quality fields
    val availableQualities: List<QualityOption> = emptyList(),
    val videoQualityMode: VideoQualityMode = VideoQualityMode.AUTO,
    val currentVideoResolution: String = "",
    val currentVideoBitrate: String = "",
    val currentVideoCodec: String = "",
    val currentVideoFps: String = "",
    val networkSpeedKbps: Long = 0L,
    val isAdaptiveStream: Boolean = false,
    val hasVideoTrack: Boolean = false,
    val hasAudioTrack: Boolean = false,
    val isSurfaceReady: Boolean = false,
    val hasRenderedFirstFrame: Boolean = false,
    val isPlaybackConfirmed: Boolean = false,
    val audioSessionId: Int = 0
)

/**
 * Playback control contract shared by the app's runtime playback engines.
 */
interface PlayerEngine {
    val state: StateFlow<PlayerState>
    val engineName: String
    fun isAvailable(): Boolean = true

    fun initialize()
    fun release()
    fun play(url: String, startPosition: Long = 0, profile: PlaybackProfile = PlaybackProfile.VOD)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(position: Long)
    fun seekForward(ms: Long)
    fun seekBackward(ms: Long)
    fun setPlaybackSpeed(speed: Float)
    fun selectAudioTrack(index: Int)
    fun selectSubtitleTrack(index: Int)
    fun disableSubtitles()
    fun setAspectRatio(mode: AspectRatioMode)
    fun setVideoQualityMode(mode: VideoQualityMode) {}
    fun selectVideoTrack(index: Int) {}
    fun setPlaybackConfiguration(
        bufferDurationMs: Long,
        liveLatencyMode: String,
        preferHwDecoding: Boolean,
        allowQualityFallback: Boolean = true
    ) {}
}

/**
 * Resolves an absolute seek request against the latest engine state. A null result means the
 * current stream has no finite seek window (the usual case for live IPTV).
 */
internal fun resolveSeekPosition(
    requestedPosition: Long,
    duration: Long,
    isSeekable: Boolean
): Long? = if (isSeekable && duration > 0L) {
    requestedPosition.coerceIn(0L, duration)
} else {
    null
}

internal fun resolveRelativeSeekPosition(
    currentPosition: Long,
    deltaMs: Long,
    duration: Long,
    isSeekable: Boolean
): Long? {
    val requested = when {
        deltaMs > 0L && currentPosition > Long.MAX_VALUE - deltaMs -> Long.MAX_VALUE
        deltaMs < 0L && currentPosition < Long.MIN_VALUE - deltaMs -> Long.MIN_VALUE
        else -> currentPosition + deltaMs
    }
    return resolveSeekPosition(requested, duration, isSeekable)
}

internal enum class TrackKind(val fallbackLabel: String) {
    AUDIO("Audio"),
    SUBTITLE("Subtitle")
}

/** Builds a non-empty title without dropping tracks whose provider metadata is incomplete. */
internal fun buildTrackTitle(
    kind: TrackKind,
    ordinal: Int,
    label: String?,
    language: String?,
    isDefault: Boolean = false,
    isForced: Boolean = false,
    isHearingImpaired: Boolean = false,
    role: String? = null
): String {
    val base = label?.trim().takeUnless { it.isNullOrBlank() }
        ?: language?.trim().takeUnless { it.isNullOrBlank() || it.equals("und", ignoreCase = true) }
        ?: "${kind.fallbackLabel} $ordinal"
    val qualifiers = buildList {
        role?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        if (isDefault) add("Default")
        if (isForced) add("Forced")
        if (isHearingImpaired) add("CC")
    }
    return if (qualifiers.isEmpty()) base else "$base (${qualifiers.joinToString()})"
}

/**
 * Providers often expose several tracks with the same label/language. Keep every selectable
 * entry and make duplicates deterministic using codec, channel count, then ordinal.
 */
internal fun disambiguateTrackNames(tracks: List<TrackInfo>): List<TrackInfo> {
    val duplicateNames = tracks
        .groupingBy { it.name.trim().lowercase() }
        .eachCount()
        .filterValues { it > 1 }
        .keys
    if (duplicateNames.isEmpty()) return tracks

    return tracks.mapIndexed { ordinal, track ->
        if (track.name.trim().lowercase() !in duplicateNames) {
            track
        } else {
            val details = buildList {
                track.codec.trim().takeIf { it.isNotBlank() }?.uppercase()?.let(::add)
                if (track.channelCount > 0) add("${track.channelCount}ch")
            }.ifEmpty { listOf("#${ordinal + 1}") }
            track.copy(name = "${track.name} • ${details.joinToString(" • ")}")
        }
    }
}
