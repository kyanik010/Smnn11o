package com.idealplayer.app.core.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native Audio decoding engine that demuxes IPTV MPEG-TS or raw audio streams,
 * completely discards all video data, and decodes compressed audio (AAC, MP3, AC-3)
 * to genuine 16-bit PCM sample buffers using hardware/native Android MediaCodec.
 *
 * Synthetic PCM generation is strictly prohibited in the production decoding pipeline.
 */
class AudioDecoderEngine(
    private val onPcmDecoded: (pcmBytes: ByteArray, sampleRate: Int, channels: Int, ptsUs: Long) -> Unit,
    private val onFormatDiscovered: ((sampleRate: Int, channels: Int, mimeType: String) -> Unit)? = null,
    private val onError: (errorCode: AudioErrorCode, message: String) -> Unit
) {

    private val isRunning = AtomicBoolean(false)
    @Volatile private var totalPcmBytesProduced = 0L
    @Volatile private var totalPcmSamplesProduced = 0L
    @Volatile private var detectedSampleRate = 48000
    @Volatile private var detectedChannels = 2
    @Volatile private var detectedMimeType = "audio/mp4a-latm"
    @Volatile private var detectedAudioPid = -1
    @Volatile private var detectedVideoPid = -1
    @Volatile private var isHardwareCodec = true

    fun getMetrics(): AudioEngineMetrics = AudioEngineMetrics(
        pcmBytesProduced = totalPcmBytesProduced,
        pcmSamplesDecoded = totalPcmSamplesProduced,
        sampleRate = detectedSampleRate,
        channelCount = detectedChannels,
        audioCodec = detectedMimeType,
        audioPid = detectedAudioPid,
        videoPid = detectedVideoPid,
        isHardwareAccelerated = isHardwareCodec,
        lastState = if (isRunning.get()) AudioExtractionState.DECODING_PCM else AudioExtractionState.IDLE
    )

    fun stop() {
        isRunning.set(false)
    }

    /**
     * Process an input stream of MPEG-TS or raw audio.
     * Demuxes audio packets, filters out ALL video packets, and decodes audio to PCM.
     */
    fun processStream(inputStream: InputStream, defaultMimeType: String = "audio/mp4a-latm") {
        isRunning.set(true)
        detectedMimeType = defaultMimeType

        var currentCodec: MediaCodec? = null
        var activeMimeType = defaultMimeType

        val demuxer = TsAudioDemuxer(
            onFrameExtracted = { frame ->
                if (!isRunning.get()) return@TsAudioDemuxer

                // Ensure decoder is initialized for this frame's mimeType, sample rate, and channels
                if (currentCodec == null || activeMimeType != frame.mimeType) {
                    try {
                        currentCodec?.stop()
                        currentCodec?.release()
                    } catch (_: Exception) {}

                    activeMimeType = frame.mimeType
                    detectedMimeType = frame.mimeType
                    detectedSampleRate = frame.sampleRate
                    detectedChannels = frame.channelCount

                    currentCodec = createAndStartDecoder(activeMimeType, frame.sampleRate, frame.channelCount)
                    if (currentCodec == null) {
                        onError(
                            AudioErrorCode.DECODER_UNAVAILABLE,
                            "مفكك ترميز $activeMimeType غير متاح على هذا الجهاز"
                        )
                        isRunning.set(false)
                        return@TsAudioDemuxer
                    }
                    onFormatDiscovered?.invoke(frame.sampleRate, frame.channelCount, activeMimeType)
                }

                currentCodec?.let { codec ->
                    decodeFrame(codec, frame.data, frame.ptsUs)
                }
            },
            onAudioDiscovered = { pid, mime, sampleRate, channels ->
                detectedAudioPid = pid
                detectedMimeType = mime
                detectedSampleRate = sampleRate
                detectedChannels = channels
                AudioLog.d(TAG, "Audio stream discovered: PID=$pid, MIME=$mime, rate=$sampleRate, channels=$channels")
            },
            onVideoDiscovered = { vpid, streamType ->
                detectedVideoPid = vpid
                AudioLog.d(TAG, "Video stream discarded: PID=$vpid, StreamType=0x${Integer.toHexString(streamType)}")
            }
        )

        try {
            val bufferedInput = BufferedInputStream(inputStream, 65536)
            val buffer = ByteArray(TsAudioDemuxer.TS_PACKET_SIZE * 32) // 32 MPEG-TS packets buffer

            while (isRunning.get()) {
                val bytesRead = bufferedInput.read(buffer)
                if (bytesRead <= 0) break

                val isTs = buffer.isNotEmpty() && buffer[0] == TsAudioDemuxer.TS_SYNC_BYTE
                if (isTs) {
                    try {
                        demuxer.processTsData(buffer, bytesRead)
                    } catch (e: Exception) {
                        AudioLog.w(TAG, "Non-fatal TS demux error in chunk: ${e.message}")
                    }
                } else {
                    // Raw audio stream (e.g. direct AAC ADTS or MP3)
                    val rawFrames: List<ExtractedAudioFrame> = extractRawAdtsFrames(buffer, bytesRead, detectedSampleRate, detectedChannels)
                    for (frame in rawFrames) {
                        if (!isRunning.get()) break
                        if (currentCodec == null) {
                            currentCodec = createAndStartDecoder(activeMimeType, frame.sampleRate, frame.channelCount)
                            if (currentCodec == null) {
                                onError(AudioErrorCode.DECODER_UNAVAILABLE, "فشل تهيئة مفكك ترميز $activeMimeType")
                                isRunning.set(false)
                                break
                            }
                        }
                        currentCodec?.let { decodeFrame(it, frame.data, frame.ptsUs) }
                    }
                }
            }

            // Flush remaining decoder frames at end of stream
            currentCodec?.let { flushDecoder(it) }

        } catch (e: InterruptedException) {
            // Normal cancellation
        } catch (e: java.net.SocketTimeoutException) {
            if (isRunning.get()) {
                AudioLog.e(TAG, "Stream read socket timed out", e)
                onError(AudioErrorCode.SOURCE_TIMEOUT, "انتهت مهلة استلام بيانات الصوت من الخادم")
            }
        } catch (e: java.io.IOException) {
            if (isRunning.get()) {
                AudioLog.e(TAG, "Network stream I/O error", e)
                onError(AudioErrorCode.SOURCE_HTTP_ERROR, "انقطع اتصال البث الصوتي: ${e.message ?: "I/O Error"}")
            }
        } catch (e: MediaCodec.CodecException) {
            if (isRunning.get()) {
                AudioLog.e(TAG, "Hardware codec exception", e)
                val diag = e.diagnosticInfo ?: (e.message ?: "خطأ في وحدة فك التشفير")
                onError(AudioErrorCode.DECODER_OUTPUT_ERROR, "خطأ في وحدة فك التشفير ($diag)")
            }
        } catch (e: Throwable) {
            if (isRunning.get()) {
                AudioLog.e(TAG, "Decoding stream failed: ${e.javaClass.simpleName} - ${e.message}", e)
                val detail = e.message ?: e.javaClass.simpleName
                onError(AudioErrorCode.DECODER_OUTPUT_ERROR, "خطأ أثناء فك ترميز الصوت ($detail)")
            }
        } finally {
            try {
                currentCodec?.stop()
                currentCodec?.release()
            } catch (_: Exception) {}
            isRunning.set(false)
        }
    }

    private fun createAndStartDecoder(mimeType: String, sampleRate: Int, channels: Int): MediaCodec? {
        return try {
            val codec = MediaCodec.createDecoderByType(mimeType)
            val format = MediaFormat.createAudioFormat(mimeType, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
                if (mimeType == "audio/mp4a-latm") {
                    // Set 2-byte AudioSpecificConfig (AAC-LC) for optimal decoder compatibility
                    val freqIdx = getFreqIndex(sampleRate)
                    val csd0 = ByteArray(2)
                    csd0[0] = ((2 shl 3) or (freqIdx shr 1)).toByte()
                    csd0[1] = (((freqIdx and 1) shl 7) or (channels shl 3)).toByte()
                    setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
                }
            }
            codec.configure(format, null, null, 0)
            codec.start()
            isHardwareCodec = try {
                codec.codecInfo?.isHardwareAccelerated ?: true
            } catch (_: Exception) {
                true
            }
            AudioLog.i(TAG, "MediaCodec decoder initialized: mime=$mimeType, rate=$sampleRate, channels=$channels, hw=$isHardwareCodec")
            codec
        } catch (e: Throwable) {
            AudioLog.e(TAG, "Failed to initialize MediaCodec decoder for $mimeType", e)
            null
        }
    }

    private fun decodeFrame(codec: MediaCodec, frameData: ByteArray, ptsUs: Long) {
        if (frameData.isEmpty()) return

        try {
            val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex >= 0) {
                val inputBuffer: ByteBuffer? = codec.getInputBuffer(inIndex)
                inputBuffer?.clear()
                if (inputBuffer != null) {
                    val bytesToPut = frameData.size.coerceAtMost(inputBuffer.remaining())
                    inputBuffer.put(frameData, 0, bytesToPut)
                    codec.queueInputBuffer(inIndex, 0, bytesToPut, ptsUs, 0)
                }
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            while (outIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val pcmChunk = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.get(pcmChunk, 0, bufferInfo.size)

                    totalPcmBytesProduced += pcmChunk.size
                    totalPcmSamplesProduced += (pcmChunk.size / (2 * detectedChannels.coerceAtLeast(1)))
                    onPcmDecoded(pcmChunk, detectedSampleRate, detectedChannels, bufferInfo.presentationTimeUs)
                }
                codec.releaseOutputBuffer(outIndex, false)
                outIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            }

            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                val newSampleRate = if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                } else detectedSampleRate

                val newChannels = if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else detectedChannels

                detectedSampleRate = newSampleRate
                detectedChannels = newChannels
                AudioLog.d(TAG, "Decoder output format changed: rate=$newSampleRate, channels=$newChannels")
                onFormatDiscovered?.invoke(newSampleRate, newChannels, detectedMimeType)
            }
        } catch (e: Exception) {
            if (isRunning.get()) {
                AudioLog.w(TAG, "Frame decode error: ${e.message}")
            }
        }
    }

    private fun flushDecoder(codec: MediaCodec) {
        try {
            val bufferInfo = MediaCodec.BufferInfo()
            var outIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            while (outIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val pcmChunk = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.get(pcmChunk, 0, bufferInfo.size)
                    onPcmDecoded(pcmChunk, detectedSampleRate, detectedChannels, bufferInfo.presentationTimeUs)
                }
                codec.releaseOutputBuffer(outIndex, false)
                outIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "SONIXAudioDecoder"
        private const val TIMEOUT_US = 10_000L

        fun getFreqIndex(sampleRate: Int): Int = when (sampleRate) {
            96000 -> 0; 88200 -> 1; 64000 -> 2; 48000 -> 3; 44100 -> 4
            32000 -> 5; 24000 -> 6; 22050 -> 7; 16000 -> 8; 12000 -> 9
            11025 -> 10; 8000 -> 11; 7350 -> 12
            else -> 3
        }

        /**
         * Extracts raw AAC ADTS frames from an arbitrary byte buffer.
         */
        fun extractRawAdtsFrames(
            data: ByteArray,
            length: Int,
            fallbackSampleRate: Int = 48000,
            fallbackChannels: Int = 2
        ): List<ExtractedAudioFrame> {
            val frames = mutableListOf<ExtractedAudioFrame>()
            var i = 0
            var pts = 0L

            while (i + 7 <= length) {
                if ((data[i].toInt() and 0xFF) == 0xFF && (data[i + 1].toInt() and 0xF0) == 0xF0) {
                    val frameLength = ((data[i + 3].toInt() and 0x03) shl 11) or
                            ((data[i + 4].toInt() and 0xFF) shl 3) or
                            ((data[i + 5].toInt() and 0xE0) shr 5)

                    if (frameLength in 7..4096 && i + frameLength <= length) {
                        val freqIdx = (data[i + 2].toInt() and 0x3C) shr 2
                        val sampleRate = when (freqIdx) {
                            0 -> 96000; 1 -> 88200; 2 -> 64000; 3 -> 48000; 4 -> 44100
                            5 -> 32000; 6 -> 24000; 7 -> 22050; 8 -> 16000; else -> fallbackSampleRate
                        }
                        val channelConfig = ((data[i + 2].toInt() and 0x01) shl 2) or
                                ((data[i + 3].toInt() and 0xC0) shr 6)
                        val channels = if (channelConfig in 1..8) channelConfig else fallbackChannels

                        val frame = ByteArray(frameLength)
                        System.arraycopy(data, i, frame, 0, frameLength)
                        frames.add(
                            ExtractedAudioFrame(
                                data = frame,
                                sampleRate = sampleRate,
                                channelCount = channels,
                                ptsUs = pts,
                                mimeType = "audio/mp4a-latm"
                            )
                        )
                        pts += (1024L * 1_000_000L / sampleRate)
                        i += frameLength
                        continue
                    }
                }
                i++
            }
            return frames
        }
    }
}

/**
 * Backwards compatibility alias for tests referencing SimpleTsAudioDemuxer.
 */
class SimpleTsAudioDemuxer {
    private val demuxer: TsAudioDemuxer
    private val extractedFrames = mutableListOf<ByteArray>()

    init {
        demuxer = TsAudioDemuxer(
            onFrameExtracted = { frame ->
                extractedFrames.add(frame.data)
            }
        )
    }

    fun extractAudioPayloads(tsData: ByteArray, length: Int): List<ByteArray> {
        extractedFrames.clear()
        demuxer.processTsData(tsData, length)
        return extractedFrames.toList()
    }
}
