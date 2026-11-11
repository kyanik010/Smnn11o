package com.idealplayer.app.core.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.idealplayer.app.core.model.Channel
import com.idealplayer.app.core.model.Playlist
import com.idealplayer.app.core.player.parsePlaybackSource
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

internal const val MAX_EXTERNAL_AUDIO_RETRIES = 5
internal const val STABLE_PLAYBACK_THRESHOLD_MS = 5_000L
internal const val BUFFERING_TIMEOUT_MS = 10_000L

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
    private var bufferingTimeoutRunnable: Runnable? = null
    private var stablePlaybackRunnable: Runnable? = null
    private var retryAttempt = 0
    private var isVideoMutedForExternalAudio = false
    private var currentActivePlaylist: Playlist? = null
    @Volatile private var latestVideoPositionMs: Long = 0L
    @Volatile private var currentPlaybackIsLive: Boolean = false
    private var lastSyncAtMs: Long = 0L

    // Callbacks to mute/restore video audio
    var muteVideoAudioAction: (() -> Unit)? = null
    var restoreVideoAudioAction: (() -> Unit)? = null

    internal fun getRetryAttempt(): Int = retryAttempt
    internal fun isVideoMuted(): Boolean = isVideoMutedForExternalAudio

    private fun muteVideoAudio() {
        if (!isVideoMutedForExternalAudio) {
            isVideoMutedForExternalAudio = true
            try {
                muteVideoAudioAction?.invoke()
            } catch (e: Exception) {
                Timber.e(e, "Error executing muteVideoAudioAction")
            }
        }
    }

    private fun restoreVideoAudio() {
        if (isVideoMutedForExternalAudio) {
            isVideoMutedForExternalAudio = false
            try {
                restoreVideoAudioAction?.invoke()
            } catch (e: Exception) {
                Timber.e(e, "Error executing restoreVideoAudioAction")
            }
        }
    }

    internal fun handleAudioPlayerBuffering() {
        val gen = generationId.get()
        cancelStablePlaybackCheck()
        _state.update { it.copy(extractionState = AudioExtractionState.STREAM_OPENING) }
        if (isVideoMutedForExternalAudio) {
            scheduleBufferingTimeout(gen)
        }
    }

    internal fun handleAudioPlayerReady() {
        // Player is prepared and connected, but audio rendering has not yet begun.
        // Do NOT mute the primary video audio yet, and do NOT reset retry attempts.
        _state.update {
            if (it.extractionState != AudioExtractionState.PLAYING) {
                it.copy(extractionState = AudioExtractionState.RESOURCE_READY)
            } else it
        }
    }

    internal fun handleAudioPlayerPlaying() {
        val gen = generationId.get()
        cancelBufferingTimeout()
        cancelRetry()
        muteVideoAudio()
        _state.update {
            it.copy(
                extractionState = AudioExtractionState.PLAYING,
                errorMessage = null,
                errorCode = null
            )
        }
        scheduleStablePlaybackCheck(gen)
    }

    internal fun handleAudioPlayerPaused() {
        cancelStablePlaybackCheck()
    }

    internal fun handleAudioPlayerError(errName: String, errMsg: String? = null) {
        val gen = generationId.get()
        cancelStablePlaybackCheck()
        cancelBufferingTimeout()
        val mappedCode = mapPlaybackExceptionToAudioErrorCode(errName)
        val detailedMessage = if (!errMsg.isNullOrBlank()) "$errName: $errMsg" else errName
        Timber.w(
            "External audio error: %s (attempt %d/%d)",
            detailedMessage,
            retryAttempt + 1,
            MAX_EXTERNAL_AUDIO_RETRIES
        )
        _state.update {
            it.copy(
                errorMessage = detailedMessage,
                errorCode = mappedCode
            )
        }
        handleFailureOrRetry(gen, detailedMessage, mappedCode)
    }

    internal fun handleAudioPlayerEnded() {
        val gen = generationId.get()
        cancelStablePlaybackCheck()
        cancelBufferingTimeout()
        Timber.i("External audio stream ended (gen=%d)", gen)
        handleFailureOrRetry(gen, "STREAM_ENDED", AudioErrorCode.SOURCE_TIMEOUT)
    }

    private val audioPlayer = ExternalAudioPlayer(
        context = context,
        onBuffering = ::handleAudioPlayerBuffering,
        onReady = ::handleAudioPlayerReady,
        onPlaying = ::handleAudioPlayerPlaying,
        onPaused = ::handleAudioPlayerPaused,
        onError = { errName, errMsg, _ -> handleAudioPlayerError(errName, errMsg) },
        onEnded = ::handleAudioPlayerEnded
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
        cancelRetry()
        cancelBufferingTimeout()
        cancelStablePlaybackCheck()
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

        startDirectStreamAttempt(gen, channel, playlist)
    }

    private fun startDirectStreamAttempt(gen: Long, channel: Channel, playlist: Playlist) {
        if (gen != generationId.get() || !_state.value.isActive) return

        val source = parsePlaybackSource(buildXtreamUrl(channel, playlist))
        val extension = streamExtension(source.url)
        _state.update { it.copy(isExtractorFallbackActive = false) }
        audioPlayer.play(
            source.url,
            if (extension == "aac") "aac" else if (extension == "mp3") "mp3" else null,
            source.headers
        )
    }

    private fun handleFailureOrRetry(gen: Long, reason: String, errorCode: AudioErrorCode) {
        if (gen != generationId.get() || !_state.value.isActive) return

        if (retryAttempt >= MAX_EXTERNAL_AUDIO_RETRIES) {
            handlePersistentFailure(gen, reason, errorCode)
        } else {
            scheduleRetry(gen, reason)
        }
    }

    private fun handlePersistentFailure(gen: Long, reason: String, errorCode: AudioErrorCode) {
        if (gen != generationId.get() || !_state.value.isActive) return

        Timber.e(
            "External audio persistent failure reached after %d attempts: %s. Restoring primary video audio.",
            retryAttempt,
            reason
        )

        cancelRetry()
        cancelBufferingTimeout()
        cancelStablePlaybackCheck()

        audioPlayer.stop()

        // Critical: Restore primary video audio upon persistent failure
        restoreVideoAudio()

        _state.update {
            it.copy(
                extractionState = AudioExtractionState.FAILED_GENERAL,
                errorMessage = reason,
                errorCode = errorCode
            )
        }
    }

    private fun scheduleRetry(gen: Long, reason: String = "RETRY") {
        if (gen != generationId.get() || !_state.value.isActive) return

        cancelRetry()
        cancelBufferingTimeout()
        cancelStablePlaybackCheck()

        audioPlayer.stop()

        retryAttempt++
        val channel = _state.value.selectedChannel
        val playlist = currentActivePlaylist

        val delayMs = externalAudioRetryDelayMs(retryAttempt)
        Timber.w(
            "External audio reconnect scheduled: attempt=%d/%d delay=%dms reason=%s selectedChannelId=%s",
            retryAttempt,
            MAX_EXTERNAL_AUDIO_RETRIES,
            delayMs,
            reason,
            channel?.id
        )

        _state.update { it.copy(extractionState = AudioExtractionState.STREAM_OPENING) }

        val runnable = Runnable {
            if (gen == generationId.get() && _state.value.isActive && channel != null && playlist != null) {
                startDirectStreamAttempt(gen, channel, playlist)
            }
        }
        retryRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun scheduleBufferingTimeout(gen: Long) {
        cancelBufferingTimeout()
        val runnable = Runnable {
            if (gen == generationId.get() && _state.value.isActive && _state.value.extractionState == AudioExtractionState.STREAM_OPENING) {
                Timber.w("External audio buffering timed out after %d ms", BUFFERING_TIMEOUT_MS)
                handleFailureOrRetry(gen, "BUFFERING_TIMEOUT", AudioErrorCode.SOURCE_TIMEOUT)
            }
        }
        bufferingTimeoutRunnable = runnable
        mainHandler.postDelayed(runnable, BUFFERING_TIMEOUT_MS)
    }

    private fun cancelBufferingTimeout() {
        bufferingTimeoutRunnable?.let(mainHandler::removeCallbacks)
        bufferingTimeoutRunnable = null
    }

    private fun scheduleStablePlaybackCheck(gen: Long) {
        cancelStablePlaybackCheck()
        val runnable = Runnable {
            if (gen == generationId.get() && _state.value.isActive && _state.value.extractionState == AudioExtractionState.PLAYING) {
                Timber.d("External audio confirmed stable after %d ms. Resetting retry attempt count.", STABLE_PLAYBACK_THRESHOLD_MS)
                retryAttempt = 0
            }
        }
        stablePlaybackRunnable = runnable
        mainHandler.postDelayed(runnable, STABLE_PLAYBACK_THRESHOLD_MS)
    }

    private fun cancelStablePlaybackCheck() {
        stablePlaybackRunnable?.let(mainHandler::removeCallbacks)
        stablePlaybackRunnable = null
    }

    private fun cancelRetry() {
        retryRunnable?.let(mainHandler::removeCallbacks)
        retryRunnable = null
    }

    private fun mapPlaybackExceptionToAudioErrorCode(errorCodeName: String): AudioErrorCode {
        return when {
            errorCodeName.contains("NETWORK") || errorCodeName.contains("TIMEOUT") ->
                AudioErrorCode.SOURCE_TIMEOUT
            errorCodeName.contains("IO") ->
                AudioErrorCode.SOURCE_HTTP_ERROR
            errorCodeName.contains("PARSER") || errorCodeName.contains("CONTAINER") || errorCodeName.contains("MALFORMED") ->
                AudioErrorCode.TS_DEMUX_ERROR
            errorCodeName.contains("DECODER") ->
                AudioErrorCode.DECODER_INIT_ERROR
            errorCodeName.contains("AUTH") || errorCodeName.contains("FORBIDDEN") ->
                AudioErrorCode.SOURCE_AUTH_ERROR
            else ->
                AudioErrorCode.MEDIA3_PLAYBACK_ERROR
        }
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
                        handleFailureOrRetry(gen, message ?: "FALLBACK_ERROR", errCode)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Fallback extraction failed")
            handleFailureOrRetry(gen, e.message ?: "FALLBACK_EXCEPTION", AudioErrorCode.SOURCE_HTTP_ERROR)
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
        cancelRetry()
        cancelBufferingTimeout()
        cancelStablePlaybackCheck()
        retryAttempt = 0

        audioPlayer.stop()
        sessionManager.stopCurrentSession()

        restoreVideoAudio()

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
