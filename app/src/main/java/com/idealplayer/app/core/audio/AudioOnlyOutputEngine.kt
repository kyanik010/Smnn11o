package com.idealplayer.app.core.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Output engine that takes genuine decoded PCM audio from AudioDecoderEngine,
 * encodes it into independent AAC ADTS frames using native Android MediaCodec
 * (strictly eliminating any video tracks/packets), and serves it via an internal
 * loopback HTTP stream for Media3 ExoPlayer.
 *
 * Synthetic audio generation is completely removed from the production pipeline.
 */
class AudioOnlyOutputEngine(
    val sessionId: String,
    private var sampleRate: Int = 48000,
    private var channels: Int = 2,
    private val bitrate: Int = 128000,
    private val onFirstFrameProduced: (() -> Unit)? = null,
    private val onError: ((AudioErrorCode, String) -> Unit)? = null
) {

    companion object {
        private const val TAG = "SONIXAudioOutput"
        private const val TIMEOUT_US = 5000L
        private const val MAX_INPUT_BUFFER_RETRIES = 100
        private const val RECENT_BUFFER_MAX_FRAMES = 60 // ~1.3 seconds burst for instant playback
        const val OUTPUT_CHANNELS = 2 // Always stereo for universal Android hardware encoder compatibility

        /**
         * Generates standard 7-byte ADTS header for AAC frame.
         */
        fun addAdtsHeader(aacPayload: ByteArray, sampleRate: Int, channels: Int): ByteArray {
            val frameLength = aacPayload.size + 7
            val header = ByteArray(7)
            val freqIdx = getFreqIndex(sampleRate)
            val profile = 2 // AAC LC

            header[0] = 0xFF.toByte()
            header[1] = 0xF1.toByte() // MPEG-4, Layer 0, No CRC
            header[2] = (((profile - 1) shl 6) or (freqIdx shl 2) or ((channels shr 2) and 1)).toByte()
            header[3] = (((channels and 3) shl 6) or ((frameLength shr 11) and 3)).toByte()
            header[4] = ((frameLength shr 3) and 0xFF).toByte()
            header[5] = (((frameLength and 7) shl 5) or 0x1F).toByte()
            header[6] = 0xFC.toByte()

            val fullFrame = ByteArray(frameLength)
            System.arraycopy(header, 0, fullFrame, 0, 7)
            System.arraycopy(aacPayload, 0, fullFrame, 7, aacPayload.size)
            return fullFrame
        }

        private fun getFreqIndex(sampleRate: Int): Int = when (sampleRate) {
            96000 -> 0; 88200 -> 1; 64000 -> 2; 48000 -> 3; 44100 -> 4
            32000 -> 5; 24000 -> 6; 22050 -> 7; 16000 -> 8; 12000 -> 9
            11025 -> 10; 8000 -> 11; 7350 -> 12
            else -> 3 // Default 48000 Hz
        }

        /**
         * Downmixes 16-bit PCM audio of arbitrary channel count (e.g. 5.1 surround or mono) to stereo (2 channels).
         */
        fun downmixToStereo(pcmBytes: ByteArray, inputChannels: Int): ByteArray {
            if (inputChannels == 2 || inputChannels <= 0 || pcmBytes.isEmpty()) return pcmBytes
            val bytesPerSample = 2 // 16-bit PCM
            val inputFrameSize = inputChannels * bytesPerSample
            val totalFrames = pcmBytes.size / inputFrameSize
            if (totalFrames <= 0) return pcmBytes

            val outputBytes = ByteArray(totalFrames * 2 * bytesPerSample)
            var inOffset = 0
            var outOffset = 0

            if (inputChannels == 1) {
                // Mono to stereo: duplicate sample to both channels
                for (f in 0 until totalFrames) {
                    val b0 = pcmBytes[inOffset]
                    val b1 = pcmBytes[inOffset + 1]
                    inOffset += 2

                    outputBytes[outOffset] = b0
                    outputBytes[outOffset + 1] = b1
                    outputBytes[outOffset + 2] = b0
                    outputBytes[outOffset + 3] = b1
                    outOffset += 4
                }
            } else if (inputChannels >= 6) {
                // 5.1 surround sound: Left, Right, Center, LFE, Left Surround, Right Surround (ITU-R BS.775)
                for (f in 0 until totalFrames) {
                    val l = (pcmBytes[inOffset].toInt() and 0xFF) or (pcmBytes[inOffset + 1].toInt() shl 8)
                    val r = (pcmBytes[inOffset + 2].toInt() and 0xFF) or (pcmBytes[inOffset + 3].toInt() shl 8)
                    val c = (pcmBytes[inOffset + 4].toInt() and 0xFF) or (pcmBytes[inOffset + 5].toInt() shl 8)
                    val ls = (pcmBytes[inOffset + 8].toInt() and 0xFF) or (pcmBytes[inOffset + 9].toInt() shl 8)
                    val rs = (pcmBytes[inOffset + 10].toInt() and 0xFF) or (pcmBytes[inOffset + 11].toInt() shl 8)
                    inOffset += inputFrameSize

                    val lSample = l.toShort()
                    val rSample = r.toShort()
                    val cSample = c.toShort()
                    val lsSample = ls.toShort()
                    val rsSample = rs.toShort()

                    val leftMixed = (lSample + 0.707f * cSample + 0.707f * lsSample).toInt().coerceIn(-32768, 32767)
                    val rightMixed = (rSample + 0.707f * cSample + 0.707f * rsSample).toInt().coerceIn(-32768, 32767)

                    outputBytes[outOffset] = (leftMixed and 0xFF).toByte()
                    outputBytes[outOffset + 1] = ((leftMixed shr 8) and 0xFF).toByte()
                    outputBytes[outOffset + 2] = (rightMixed and 0xFF).toByte()
                    outputBytes[outOffset + 3] = ((rightMixed shr 8) and 0xFF).toByte()
                    outOffset += 4
                }
            } else {
                // Fallback for 3, 4, 5 channels: copy first 2 channels
                for (f in 0 until totalFrames) {
                    outputBytes[outOffset] = pcmBytes[inOffset]
                    outputBytes[outOffset + 1] = pcmBytes[inOffset + 1]
                    outputBytes[outOffset + 2] = pcmBytes[inOffset + 2]
                    outputBytes[outOffset + 3] = pcmBytes[inOffset + 3]
                    inOffset += inputFrameSize
                    outOffset += 4
                }
            }

            return outputBytes
        }
    }

    private val isRunning = AtomicBoolean(false)
    private val serverExecutor = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null
    @Volatile var serverPort: Int = 0
        private set

    // Sliding window of recent frames so any connecting client gets an immediate burst
    private val recentFramesLock = Any()
    private val recentFrames = ArrayDeque<ByteArray>(RECENT_BUFFER_MAX_FRAMES + 10)

    // Per-client broadcast queues: each client gets its own queue so they don't consume each other's frames
    private val clientQueues = ConcurrentHashMap<Socket, ConcurrentLinkedQueue<ByteArray>>()

    private val totalFramesProduced = AtomicLong(0)
    private val totalBytesServed = AtomicLong(0)
    private val firstFrameReported = AtomicBoolean(false)

    private var encoderCodec: MediaCodec? = null

    init {
        initEncoder()
    }

    @Synchronized
    fun updateFormat(newSampleRate: Int, newChannels: Int) {
        if (newSampleRate == sampleRate && encoderCodec != null) {
            channels = newChannels
            return
        }
        AudioLog.i(TAG, "Reconfiguring AAC encoder: rate=$newSampleRate, input channels=$newChannels -> stereo output")
        sampleRate = newSampleRate
        channels = newChannels

        try {
            encoderCodec?.stop()
            encoderCodec?.release()
        } catch (_: Exception) {}
        encoderCodec = null

        initEncoder()
    }

    private fun initEncoder() {
        try {
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            // Always encode 2 channels (Stereo) for 100% Android TV hardware encoder support
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, OUTPUT_CHANNELS).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            encoderCodec = encoder
            AudioLog.i(TAG, "MediaCodec AAC Encoder successfully initialized: rate=$sampleRate, stereo output")
        } catch (e: Throwable) {
            AudioLog.e(TAG, "Failed to initialize MediaCodec AAC encoder", e)
            encoderCodec = null
            onError?.invoke(AudioErrorCode.AAC_ENCODER_ERROR, "تعذر تهيئة مشفر AAC على هذا الجهاز: ${e.message}")
        }
    }

    fun start(): String {
        isRunning.set(true)
        val loopback = InetAddress.getByName("127.0.0.1")
        val socket = ServerSocket(0, 10, loopback)
        serverSocket = socket
        serverPort = socket.localPort

        serverExecutor.execute {
            listenForClients(socket)
        }

        return "http://127.0.0.1:$serverPort/sonix_audio_stream_$sessionId.aac"
    }

    fun hasProducedFrames(): Boolean = totalFramesProduced.get() > 0

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}

        clientQueues.keys.forEach { client ->
            try { client.close() } catch (_: Exception) {}
        }
        clientQueues.clear()

        try {
            encoderCodec?.stop()
            encoderCodec?.release()
        } catch (_: Exception) {}
        encoderCodec = null

        serverExecutor.shutdownNow()
        synchronized(recentFramesLock) {
            recentFrames.clear()
        }
    }

    /**
     * Ingests decoded raw PCM audio, downmixes multi-channel audio (e.g. 5.1) to stereo,
     * encodes it to AAC ADTS using native MediaCodec, and broadcasts it to connected clients.
     */
    fun ingestPcm(pcmData: ByteArray, ptsUs: Long, inputChannels: Int = channels) {
        if (!isRunning.get() || pcmData.isEmpty()) return

        val stereoPcm = downmixToStereo(pcmData, inputChannels)
        val encoder = encoderCodec
        if (encoder != null) {
            encodePcmWithMediaCodec(encoder, stereoPcm, ptsUs)
        } else {
            AudioLog.w(TAG, "Encoder codec is null; PCM data dropped")
            onError?.invoke(AudioErrorCode.AAC_ENCODER_ERROR, "مشفر AAC غير متاح")
        }
    }

    /**
     * Directly queues an already-encoded valid AAC ADTS frame (e.g. from tests or direct AAC pass-through).
     */
    fun enqueueRawAdtsFrame(adtsFrame: ByteArray) {
        if (!isRunning.get() || adtsFrame.isEmpty()) return
        broadcastFrame(adtsFrame)
    }

    private fun encodePcmWithMediaCodec(encoder: MediaCodec, pcmData: ByteArray, ptsUs: Long) {
        try {
            var offset = 0
            var noInputBufferAttempts = 0
            while (offset < pcmData.size && isRunning.get()) {
                val inIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex < 0) {
                    drainEncoderOutput(encoder)
                    noInputBufferAttempts++
                    if (noInputBufferAttempts >= MAX_INPUT_BUFFER_RETRIES) {
                        throw IllegalStateException("AAC encoder input buffer remained unavailable")
                    }
                    continue
                }
                noInputBufferAttempts = 0

                val inBuf = encoder.getInputBuffer(inIndex)
                if (inBuf == null) {
                    encoder.queueInputBuffer(inIndex, 0, 0, ptsUs, 0)
                    throw IllegalStateException("AAC encoder returned a null input buffer")
                }
                inBuf.clear()
                val bytesToPut = (pcmData.size - offset).coerceAtMost(inBuf.remaining())
                if (bytesToPut <= 0) {
                    encoder.queueInputBuffer(inIndex, 0, 0, ptsUs, 0)
                    throw IllegalStateException("AAC encoder input buffer has no capacity")
                }
                inBuf.put(pcmData, offset, bytesToPut)
                val bytesPerPcmFrame = OUTPUT_CHANNELS * 2
                val chunkPtsUs = ptsUs + (offset / bytesPerPcmFrame) * 1_000_000L / sampleRate.coerceAtLeast(1)
                encoder.queueInputBuffer(inIndex, 0, bytesToPut, chunkPtsUs, 0)
                offset += bytesToPut
                drainEncoderOutput(encoder)
            }
            drainEncoderOutput(encoder)
        } catch (e: Exception) {
            if (isRunning.get()) {
                AudioLog.e(TAG, "Encoding PCM to AAC failed: ${e.message}", e)
                onError?.invoke(AudioErrorCode.AAC_ENCODER_ERROR, e.message ?: "فشل ترميز الصوت")
            }
        }
    }

    private fun drainEncoderOutput(encoder: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        var outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0L)
        while (outIndex >= 0) {
            val outBuf = encoder.getOutputBuffer(outIndex)
            val isCodecConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
            if (outBuf != null && bufferInfo.size > 0 && !isCodecConfig) {
                val rawAac = ByteArray(bufferInfo.size)
                outBuf.position(bufferInfo.offset)
                outBuf.get(rawAac, 0, bufferInfo.size)
                broadcastFrame(addAdtsHeader(rawAac, sampleRate, OUTPUT_CHANNELS))
            }
            encoder.releaseOutputBuffer(outIndex, false)
            outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0L)
        }
    }

    private fun broadcastFrame(frame: ByteArray) {
        // 1. Maintain sliding window of recent frames
        synchronized(recentFramesLock) {
            if (recentFrames.size >= RECENT_BUFFER_MAX_FRAMES) {
                recentFrames.removeFirst()
            }
            recentFrames.addLast(frame)
        }

        // 2. Deliver to all active client queues
        clientQueues.forEach { (_, queue) ->
            while (queue.size > 150) {
                queue.poll() // Discard stale frames to prevent buffer bloat
            }
            queue.offer(frame)
        }

        totalFramesProduced.incrementAndGet()

        if (firstFrameReported.compareAndSet(false, true)) {
            AudioLog.i(TAG, "First valid AAC frame produced! sessionId=$sessionId, rate=$sampleRate, ch=$channels")
            onFirstFrameProduced?.invoke()
        }
    }

    private fun listenForClients(server: ServerSocket) {
        while (isRunning.get() && !server.isClosed) {
            try {
                val client = server.accept()
                val queue = ConcurrentLinkedQueue<ByteArray>()

                // Pre-populate queue with recent frames for fast buffering
                synchronized(recentFramesLock) {
                    recentFrames.forEach { queue.offer(it) }
                }

                clientQueues[client] = queue
                serverExecutor.execute {
                    handleClient(client, queue)
                }
            } catch (_: Exception) {
                break
            }
        }
    }

    private fun handleClient(client: Socket, queue: ConcurrentLinkedQueue<ByteArray>) {
        try {
            client.soTimeout = 15000
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // Read HTTP GET request line
            val requestLine = input.bufferedReader(Charsets.UTF_8).readLine() ?: return
            if (!requestLine.startsWith("GET")) return

            AudioLog.d(TAG, "Media3 client connected to local loopback: port=$serverPort")

            // Respond with HTTP 200 Audio-Only stream
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: audio/aac\r\n" +
                    "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                    "Pragma: no-cache\r\n" +
                    "Connection: close\r\n" +
                    "Accept-Ranges: none\r\n\r\n"
            output.write(header.toByteArray(Charsets.UTF_8))
            output.flush()

            // Stream continuous live AAC frames to client
            while (isRunning.get() && !client.isClosed) {
                val frame = queue.poll()
                if (frame != null) {
                    output.write(frame)
                    output.flush()
                    totalBytesServed.addAndGet(frame.size.toLong())
                } else {
                    Thread.sleep(10)
                }
            }
        } catch (_: Exception) {
            // Client disconnected or timed out
        } finally {
            clientQueues.remove(client)
            try { client.close() } catch (_: Exception) {}
        }
    }

    fun getMetrics(): AudioEngineMetrics = AudioEngineMetrics(
        sessionId = sessionId,
        encodedFramesCount = totalFramesProduced.get(),
        pcmBytesProduced = totalBytesServed.get(),
        sampleRate = sampleRate,
        channelCount = channels,
        activeBitrate = bitrate,
        localServerPort = serverPort,
        audioCodec = "audio/mp4a-latm (AAC ADTS Native MediaCodec)"
    )
}
