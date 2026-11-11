package com.idealplayer.app.core.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.idealplayer.app.core.model.Channel
import com.idealplayer.app.core.model.Playlist
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
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

        // Direct stream attempt first (same as SONIX)
        startDirectStreamAttempt(gen, channel, playlist)
    }

    private fun startDirectStreamAttempt(gen: Long, channel: Channel, playlist: Playlist) {
        if (gen != generationId.get() || !_state.value.isActive) return

        val streamUrl = buildXtreamUrl(channel, playlist)
        val extension = channel.streamUrl.substringAfterLast('.', "ts").lowercase()

        audioPlayer.play(streamUrl, if (extension == "aac") "aac" else if (extension == "mp3") "mp3" else null)
    }

    private fun scheduleRetry(gen: Long) {
        if (gen != generationId.get() || !_state.value.isActive) return
        audioPlayer.stop()

        retryAttempt = (retryAttempt + 1).coerceAtMost(6)
        val channel = _state.value.selectedChannel
        val playlist = currentActivePlaylist

        if (channel != null && playlist != null && !_state.value.isExtractorFallbackActive && retryAttempt >= 2) {
            // Activate Demuxer + Decoder + Output Engine fallback
            startExtractorFallback(gen, channel, playlist)
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
        val clamped = offsetMs.coerceIn(-10000L, 10000L)
        _state.update { it.copy(syncOffsetMs = clamped) }
        applySyncOffset(clamped)
    }

    private fun applySyncOffset(offsetMs: Long) {
        val current = audioPlayer.currentPosition
        val target = (current - offsetMs).coerceAtLeast(0L)
        audioPlayer.seekTo(target)
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
        stop()
        sessionManager.shutdown()
        currentActivePlaylist = null
        muteVideoAudioAction = null
        restoreVideoAudioAction = null
    }
}
