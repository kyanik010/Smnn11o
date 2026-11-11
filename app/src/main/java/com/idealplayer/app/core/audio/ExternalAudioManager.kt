package com.idealplayer.app.core.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.idealplayer.app.core.model.Channel
import com.idealplayer.app.core.model.Playlist
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

internal fun externalAudioRetryDelayMs(attempt: Int): Long {
    val boundedAttempt = attempt.coerceAtLeast(1)
    return when (boundedAttempt) {
        1 -> 1000L
        2 -> 2000L
        3 -> 4000L
        4 -> 8000L
        5 -> 16000L
        else -> 30000L
    }
}

data class ExternalAudioState(
    val isActive: Boolean = false,
    val selectedChannel: Channel? = null,
    val extractionState: AudioExtractionState = AudioExtractionState.IDLE,
    val errorMessage: String? = null,
    val errorCode: AudioErrorCode? = null,
    val volume: Float = 1.0f,
    val syncOffsetMs: Long = 0L,
    val isExtractorFallbackActive: Boolean = false,
    val availableAudioSources: List<Channel> = emptyList(),
    val metrics: AudioEngineMetrics = AudioEngineMetrics()
)

@Singleton
class ExternalAudioManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionManager = AudioSessionManager()
    private val generationId = AtomicLong(0)

    private val _state = MutableStateFlow(ExternalAudioState())
    val state: StateFlow<ExternalAudioState> = _state.asStateFlow()

    private var retryRunnable: Runnable? = null
    private var retryAttempt = 0
    private var currentActivePlaylist: Playlist? = null
    @Volatile private var latestVideoPositionMs: Long = 0L
    @Volatile private var currentPlaybackIsLive: Boolean = false
    private var lastSyncAtMs: Long = 0L

    // Callbacks to mute/restore video audio
    var muteVideoAudioAction: (() -> Unit)? = null
    var restoreVideoAudioAction: (() -> Unit)? = null

    private val audioPlayer = ExternalAudioPlayer(
        context = context,
        onBuffering = {
            _state.update { it.copy(extractionState = AudioExtractionState.STREAM_OPENING) }
        },
        onPlaying = {
            retryRunnable?.let(mainHandler::removeCallbacks)
            retryRunnable = null
            retryAttempt = 0
            muteVideoAudioAction?.invoke()
            _state.update { it.copy(extractionState = AudioExtractionState.PLAYING, errorMessage = null) }
        },
        onError = { errName ->
            scheduleRetry(generationId.get())
        },
        onEnded = {
            scheduleRetry(generationId.get())
        }
    )

    fun populateCandidates(channels: List<Channel>, currentPlayingChannelId: Long?) {
        val classified = channels.filter { ch ->
            if (currentPlayingChannelId != null && ch.id == currentPlayingChannelId) {
                false
            } else {
                val group = AudioSourceClassifier.classify(ch.name, ch.groupTitle)
                group != AudioChannelGroup.OTHER && ch.streamId > 0
            }
        }
        _state.update { it.copy(availableAudioSources = classified) }
    }

    fun startExternalAudio(channel: Channel, playlist: Playlist) {
        sessionManager.stopCurrentSession()
        currentActivePlaylist = playlist
        val gen = generationId.incrementAndGet()
        retryRunnable?.let(mainHandler::removeCallbacks)
        retryRunnable = null
        retryAttempt = 0

        _state.update {
            it.copy(
                isActive = true,
                selectedChannel = channel,
                extractionState = AudioExtractionState.STREAM_OPENING,
                errorMessage = null,
                errorCode = null,
                isExtractorFallbackActive = false
            )
        }

        val streamUrl = buildXtreamUrl(channel, playlist)
        val path = try { java.net.URI(streamUrl).path.orEmpty() } catch (_: Exception) { "" }
        val lastSegment = path.substringAfterLast('/')
        val extension = lastSegment.substringAfterLast('.', "").lowercase().ifBlank { "ts" }

        // Transport-stream and HLS IPTV channels must pass through the audio-only
        // demux/decode/output pipeline first. Raw AAC/MP3 files can use Media3 directly.
        if (extension in setOf("ts", "m3u8", "")) {
            startExtractorFallback(gen, channel, playlist)
        } else {
            startDirectStreamAttempt(gen, channel, playlist)
        }
    }

    private fun startDirectStreamAttempt(gen: Long, channel: Channel, playlist: Playlist) {
        if (gen != generationId.get() || !_state.value.isActive) return

        val streamUrl = buildXtreamUrl(channel, playlist)
        val extension = streamExtension(streamUrl)
        audioPlayer.play(streamUrl, if (extension == "aac") "aac" else if (extension == "mp3") "mp3" else null)
    }

    private fun scheduleRetry(gen: Long) {
        if (gen != generationId.get() || !_state.value.isActive) return
        audioPlayer.stop()
        // Do not leave the primary video muted while the secondary stream is reconnecting.
        restoreVideoAudioAction?.invoke()

        retryAttempt = (retryAttempt + 1).coerceAtMost(6)
        val channel = _state.value.selectedChannel
        val playlist = currentActivePlaylist

        if (channel != null && playlist != null && isExtractionStream(channel) && !_state.value.isExtractorFallbackActive && retryAttempt >= 2) {
            startExtractorFallback(gen, channel, playlist)
            return
        }
        if (channel != null && playlist != null && _state.value.isExtractorFallbackActive && retryAttempt >= 4) {
            // If extraction repeatedly fails, retain a last-resort audio-only Media3 path.
            _state.update { it.copy(isExtractorFallbackActive = false) }
            startDirectStreamAttempt(gen, channel, playlist)
            return
        }

        val delayMs = externalAudioRetryDelayMs(retryAttempt)
        _state.update { it.copy(extractionState = AudioExtractionState.STREAM_OPENING) }

        val runnable = Runnable {
            if (gen == generationId.get() && _state.value.isActive && channel != null && playlist != null) {
                if (_state.value.isExtractorFallbackActive) {
                    startExtractorFallback(gen, channel, playlist)
                } else {
                    startDirectStreamAttempt(gen, channel, playlist)
                }
            }
        }
        retryRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun startExtractorFallback(gen: Long, channel: Channel, playlist: Playlist) {
        if (gen != generationId.get() || !_state.value.isActive) return
        _state.update {
            it.copy(
                isExtractorFallbackActive = true,
                extractionState = AudioExtractionState.STREAM_OPENING
            )
        }

        val streamUrl = buildXtreamUrl(channel, playlist)
        try {
            sessionManager.startSession(
                streamId = channel.streamId.toString(),
                sourceUrl = streamUrl
            ) { state, message, errCode, readyUrl ->
                mainHandler.post {
                    if (gen != generationId.get() || !_state.value.isActive) return@post
                    _state.update {
                        it.copy(
                            extractionState = state,
                            errorMessage = message,
                            errorCode = errCode,
                            metrics = sessionManager.getSessionMetrics() ?: it.metrics
                        )
                    }

                    if (state == AudioExtractionState.RESOURCE_READY && !readyUrl.isNullOrBlank()) {
                        retryAttempt = 0
                        audioPlayer.play(readyUrl, "aac")
                    } else if (errCode != null) {
                        scheduleRetry(gen)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Fallback extraction failed")
            scheduleRetry(gen)
        }
    }

    private fun streamExtension(url: String): String {
        val path = try { java.net.URI(url).path.orEmpty() } catch (_: Exception) { "" }
        return path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
    }

    private fun isExtractionStream(channel: Channel): Boolean {
        val extension = streamExtension(channel.streamUrl)
        return extension.isBlank() || extension == "ts" || extension == "m3u8"
    }

    private fun buildXtreamUrl(channel: Channel, playlist: Playlist): String {
        return if (channel.streamUrl.startsWith("http://") || channel.streamUrl.startsWith("https://")) {
            channel.streamUrl
        } else {
            val server = playlist.serverUrl.trimEnd('/')
            val user = playlist.username
            val pass = playlist.password
            val id = channel.streamId
            "$server/live/$user/$pass/$id.ts"
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = clamped) }
        audioPlayer.setVolume(clamped)
    }

    fun setSyncOffset(offsetMs: Long) {
        val previousOffset = _state.value.syncOffsetMs
        val clamped = offsetMs.coerceIn(-10000L, 10000L)
        _state.update { it.copy(syncOffsetMs = clamped) }
        if (currentPlaybackIsLive) {
            // A live window position is not an absolute VOD timestamp. Apply only the
            // user's relative adjustment to the external stream's own timeline.
            val delta = clamped - previousOffset
            audioPlayer.seekTo((audioPlayer.currentPosition - delta).coerceAtLeast(0L))
        } else {
            syncToVideoPosition(latestVideoPositionMs, isLive = false, force = true)
        }
    }

    /**
     * Keep seekable VOD timelines aligned. Live IPTV windows have independent origins,
     * so they must not be hard-seeked to the video's elapsed position.
     */
    fun syncToVideoPosition(videoPositionMs: Long, isLive: Boolean = false, force: Boolean = false) {
        currentPlaybackIsLive = isLive
        if (!_state.value.isActive || _state.value.extractionState !in setOf(AudioExtractionState.PLAYING, AudioExtractionState.RESOURCE_READY)) return
        latestVideoPositionMs = videoPositionMs.coerceAtLeast(0L)
        if (isLive) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSyncAtMs < 1500L) return

        val target = ExternalAudioSyncPolicy.targetPositionMs(
            videoPositionMs = latestVideoPositionMs,
            offsetMs = _state.value.syncOffsetMs,
            audioDurationMs = audioPlayer.duration
        )
        val current = audioPlayer.currentPosition
        if (abs(current - target) >= 900L) {
            lastSyncAtMs = now
            audioPlayer.seekTo(target)
        }
    }

    fun pause() {
        audioPlayer.pause()
    }

    fun resume() {
        if (_state.value.isActive) {
            audioPlayer.resume()
        }
    }

    fun stop() {
        generationId.incrementAndGet()
        retryRunnable?.let(mainHandler::removeCallbacks)
        retryRunnable = null
        retryAttempt = 0

        audioPlayer.stop()
        sessionManager.stopCurrentSession()

        restoreVideoAudioAction?.invoke()

        _state.update {
            it.copy(
                isActive = false,
                selectedChannel = null,
                extractionState = AudioExtractionState.STOPPED,
                errorMessage = null,
                errorCode = null,
                isExtractorFallbackActive = false
            )
        }
    }

    fun release() {
        // This manager is a process-wide @Singleton. ViewModel teardown must stop the
        // current session, but must not shut down its reusable session executor.
        stop()
        currentActivePlaylist = null
    }
}
