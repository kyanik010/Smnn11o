package com.idealplayer.app.core.audio

import android.util.Log
import com.idealplayer.app.core.common.SensitiveLog
import java.io.InputStream
import java.net.HttpURLConnection
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages audio extraction sessions, guaranteeing single-session isolation,
 * thread-safe cleanup, prevention of race conditions, and strict progression
 * of states (RESOURCE_READY is dispatched only after genuine AAC frames are ready).
 */
class AudioSessionManager {

    companion object {
        private const val TAG = "SONIXAudioSession"
    }

    private val sessionExecutor = Executors.newSingleThreadExecutor()
    private val activeSession = AtomicReference<ActiveSession?>(null)

    data class ActiveSession(
        val sessionId: String,
        val streamId: String,
        val sourceUrl: String,
        val localAudioUrl: String,
        val outputEngine: AudioOnlyOutputEngine,
        val decoderEngine: AudioDecoderEngine,
        @Volatile var workerFuture: Future<*>? = null,
        @Volatile var connection: HttpURLConnection? = null,
        val isReady: AtomicBoolean = AtomicBoolean(false),
        val isCancelled: AtomicBoolean = AtomicBoolean(false)
    )

    fun startSession(
        streamId: String,
        sourceUrl: String,
        onStateChange: (AudioExtractionState, String, AudioErrorCode?, String?) -> Unit
    ): String {
        // Stop and recycle any existing session first
        stopCurrentSession()

        val sessionId = UUID.randomUUID().toString().take(8)
        val safeSourceLog = SensitiveLog.redactUrl(sourceUrl)
        AudioLog.i(TAG, "Starting audio session: id=$sessionId, streamId=$streamId, url=$safeSourceLog")

        onStateChange(AudioExtractionState.STREAM_OPENING, "جارٍ الاتصال بمصدر القناة...", null, null)

        lateinit var outputEngine: AudioOnlyOutputEngine
        lateinit var decoderEngine: AudioDecoderEngine

        outputEngine = AudioOnlyOutputEngine(
            sessionId = sessionId,
            sampleRate = 48000,
            channels = 2,
            onFirstFrameProduced = {
                val current = activeSession.get()
                if (current != null && current.sessionId == sessionId && !current.isCancelled.get()) {
                    if (current.isReady.compareAndSet(false, true)) {
                        AudioLog.i(TAG, "Audio resource is VERIFIED and READY for Media3 playback: port=${outputEngine.serverPort}")
                        onStateChange(
                            AudioExtractionState.OUTPUT_PRODUCING,
                            "جارٍ إنتاج المورد الصوتي المستقل",
                            null,
                            null
                        )
                        onStateChange(
                            AudioExtractionState.RESOURCE_READY,
                            "المورد الصوتي المستقل جاهز للتشغيل",
                            null,
                            current.localAudioUrl
                        )
                    }
                }
            },
            onError = { errCode, msg ->
                val current = activeSession.get()
                if (current != null && current.sessionId == sessionId && !current.isCancelled.get()) {
                    AudioLog.e(TAG, "Output engine error: $errCode - $msg")
                    onStateChange(AudioExtractionState.FAILED_OUTPUT, msg, errCode, null)
                    stopCurrentSession()
                }
            }
        )

        val localAudioUrl = outputEngine.start()

        decoderEngine = AudioDecoderEngine(
            onPcmDecoded = { pcmBytes, sampleRate, channels, ptsUs ->
                outputEngine.ingestPcm(pcmBytes, ptsUs, channels)
            },
            onFormatDiscovered = { sampleRate, channels, mimeType ->
                outputEngine.updateFormat(sampleRate, channels)
            },
            onError = { errCode, msg ->
                val current = activeSession.get()
                if (current != null && current.sessionId == sessionId && !current.isCancelled.get()) {
                    AudioLog.e(TAG, "Decoder error: $errCode - $msg")
                    onStateChange(AudioExtractionState.FAILED_DECODING, msg, errCode, null)
                    stopCurrentSession()
                }
            }
        )

        // Store active session BEFORE submitting worker to eliminate race conditions
        val session = ActiveSession(
            sessionId = sessionId,
            streamId = streamId,
            sourceUrl = sourceUrl,
            localAudioUrl = localAudioUrl,
            outputEngine = outputEngine,
            decoderEngine = decoderEngine
        )
        activeSession.set(session)

        val future = sessionExecutor.submit {
            runExtractionPipeline(session, onStateChange)
        }
        session.workerFuture = future

        return localAudioUrl
    }

    private fun runExtractionPipeline(
        session: ActiveSession,
        onStateChange: (AudioExtractionState, String, AudioErrorCode?, String?) -> Unit
    ) {
        var activeConnection: HttpURLConnection? = null
        try {
            if (session.isCancelled.get()) return

            onStateChange(AudioExtractionState.STREAM_OPENING, "جارٍ فتح المصدر والاتصال بالبث...", null, null)

            // Inspect stream
            val inspection = AudioStreamInspector.inspectStream(session.sourceUrl)
            if (!inspection.isSupported) {
                val err = inspection.errorCode ?: AudioErrorCode.SOURCE_HTTP_ERROR
                val msg = inspection.errorMessage ?: "مصدر البث غير مدعوم"
                onStateChange(AudioExtractionState.FAILED_INSPECTION, msg, err, null)
                return
            }

            val targetUrl = inspection.hlsAudioOnlyUrl ?: session.sourceUrl

            // Open HTTP connection with automatic redirects and safe headers
            activeConnection = AudioStreamInspector.openHttpConnectionWithRedirects(targetUrl)
            session.connection = activeConnection

            val responseCode = activeConnection.responseCode
            if (responseCode !in 200..299) {
                val err = if (responseCode in listOf(401, 403)) AudioErrorCode.SOURCE_AUTH_ERROR else AudioErrorCode.SOURCE_HTTP_ERROR
                onStateChange(
                    AudioExtractionState.FAILED_GENERAL,
                    "فشل الاتصال بالبث (HTTP $responseCode)",
                    err,
                    null
                )
                return
            }

            if (session.isCancelled.get()) return

            onStateChange(
                AudioExtractionState.STREAM_INSPECTED,
                "تم فحص المصدر وعزل مسار الفيديو",
                null,
                null
            )
            onStateChange(
                AudioExtractionState.DECODING_PCM,
                "جارٍ فك ترميز الصوت إلى PCM",
                null,
                null
            )

            val inputStream: InputStream = activeConnection.inputStream

            // Process audio through the demuxing and decoding pipeline
            session.decoderEngine.processStream(inputStream, inspection.audioMimeType)

        } catch (e: InterruptedException) {
            // Clean cancellation
            AudioLog.i(TAG, "Audio session extraction cancelled")
        } catch (e: Throwable) {
            if (!session.isCancelled.get()) {
                val safeErr = e.message ?: "Stream error"
                AudioLog.e(TAG, "Pipeline error: $safeErr", e)
                onStateChange(
                    AudioExtractionState.FAILED_GENERAL,
                    "انقطع تدفق الصوت: $safeErr",
                    AudioErrorCode.SOURCE_TIMEOUT,
                    null
                )
            }
        } finally {
            try { activeConnection?.disconnect() } catch (_: Exception) {}
        }
    }

    fun stopCurrentSession() {
        val current = activeSession.getAndSet(null) ?: return
        current.isCancelled.set(true)
        AudioLog.i(TAG, "Stopping active audio session: ${current.sessionId}")
        try {
            current.workerFuture?.cancel(true)
            current.connection?.disconnect()
            current.decoderEngine.stop()
            current.outputEngine.stop()
        } catch (e: Exception) {
            AudioLog.w(TAG, "Error cleaning up session: ${e.message}")
        }
    }

    fun getCurrentLocalAudioUrl(): String? = activeSession.get()?.localAudioUrl

    fun isSessionReady(): Boolean = activeSession.get()?.isReady?.get() == true

    fun getSessionMetrics(): AudioEngineMetrics? {
        val session = activeSession.get() ?: return null
        val decMetrics = session.decoderEngine.getMetrics()
        val outMetrics = session.outputEngine.getMetrics()
        return AudioEngineMetrics(
            sessionId = session.sessionId,
            streamId = session.streamId,
            pcmBytesProduced = decMetrics.pcmBytesProduced,
            pcmSamplesDecoded = decMetrics.pcmSamplesDecoded,
            encodedFramesCount = outMetrics.encodedFramesCount,
            sampleRate = decMetrics.sampleRate,
            channelCount = decMetrics.channelCount,
            audioCodec = decMetrics.audioCodec,
            audioPid = decMetrics.audioPid,
            videoPid = decMetrics.videoPid,
            isHardwareAccelerated = decMetrics.isHardwareAccelerated,
            localServerPort = outMetrics.localServerPort,
            lastState = if (session.isReady.get()) AudioExtractionState.RESOURCE_READY else AudioExtractionState.DECODING_PCM
        )
    }

    fun shutdown() {
        stopCurrentSession()
        sessionExecutor.shutdownNow()
    }
}
