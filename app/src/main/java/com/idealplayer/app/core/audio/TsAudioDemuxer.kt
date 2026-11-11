package com.idealplayer.app.core.audio

/**
 * Elementary audio frame extracted from MPEG-TS or raw stream.
 */
data class ExtractedAudioFrame(
    val data: ByteArray,
    val sampleRate: Int,
    val channelCount: Int,
    val ptsUs: Long,
    val mimeType: String
)

/**
 * Standard MPEG-TS Demuxer (ISO/IEC 13818-1) dedicated to isolating audio
 * elementary streams, completely eliminating all video packets (H.264 / HEVC / MPEG-2),
 * and stripping PES headers down to pure Elementary Stream (ES) frames.
 */
class TsAudioDemuxer(
    private val onFrameExtracted: (ExtractedAudioFrame) -> Unit,
    private val onAudioDiscovered: (audioPid: Int, mimeType: String, sampleRate: Int, channels: Int) -> Unit = { _, _, _, _ -> },
    private val onVideoDiscovered: (videoPid: Int, streamType: Int) -> Unit = { _, _ -> }
) {

    companion object {
        const val TS_PACKET_SIZE = 188
        const val TS_SYNC_BYTE = 0x47.toByte()
        private const val MAX_PES_BUFFER_SIZE = 131072 // 128KB limit to prevent memory bloat

        // Stream types from ISO/IEC 13818-1, DVB, & ATSC A/52
        const val STREAM_TYPE_MP1_AUDIO = 0x03
        const val STREAM_TYPE_MP2_AUDIO = 0x04
        const val STREAM_TYPE_AAC_ADTS = 0x0F
        const val STREAM_TYPE_AAC_LATM = 0x11
        const val STREAM_TYPE_AAC_RAW = 0x1C
        const val STREAM_TYPE_AC3 = 0x81
        const val STREAM_TYPE_EAC3 = 0x87
        const val STREAM_TYPE_DVB_AC3 = 0x06 // DVB PES private data (AC-3, E-AC-3, DTS)

        // Sampling frequencies for ADTS (ISO/IEC 14496-3)
        private val ADTS_FREQUENCIES = intArrayOf(
            96000, 88200, 64000, 48000, 44100, 32000,
            24000, 22050, 16000, 12000, 11025, 8000, 7350
        )
    }

    var audioPid: Int = -1
        private set
    var videoPid: Int = -1
        private set
    var pmtPid: Int = -1
        private set

    var detectedMimeType: String = "audio/mp4a-latm"
        private set
    var detectedSampleRate: Int = 48000
        private set
    var detectedChannels: Int = 2
        private set

    private val currentPesBuffer = mutableListOf<Byte>()
    private var lastPtsUs: Long = 0L

    // Rollover buffer for reassembling split 188-byte TS packets across TCP reads
    private val remainderBuffer = ByteArray(TS_PACKET_SIZE)
    private var remainderLength = 0

    fun reset() {
        audioPid = -1
        videoPid = -1
        pmtPid = -1
        detectedMimeType = "audio/mp4a-latm"
        detectedSampleRate = 48000
        detectedChannels = 2
        currentPesBuffer.clear()
        lastPtsUs = 0L
        remainderLength = 0
    }

    /**
     * Ingests a raw byte array buffer containing MPEG-TS stream data.
     * Accurately reassembles packets that are split across TCP reads.
     */
    fun processTsData(data: ByteArray, length: Int) {
        if (length <= 0) return

        var offset = 0

        // 1. If we have leftover bytes from the previous read, complete the packet first
        if (remainderLength > 0) {
            val needed = TS_PACKET_SIZE - remainderLength
            if (length >= needed) {
                System.arraycopy(data, 0, remainderBuffer, remainderLength, needed)
                offset += needed
                remainderLength = 0
                if (remainderBuffer[0] == TS_SYNC_BYTE) {
                    try {
                        processSinglePacket(remainderBuffer, 0, TS_PACKET_SIZE)
                    } catch (e: Exception) {
                        AudioLog.w("TsAudioDemuxer", "Error in reassembled TS packet: ${e.message}")
                    }
                }
            } else {
                // Not enough bytes to complete 188 bytes; accumulate and return
                System.arraycopy(data, 0, remainderBuffer, remainderLength, length)
                remainderLength += length
                return
            }
        }

        // 2. Process all complete 188-byte packets in the current buffer
        while (offset + TS_PACKET_SIZE <= length) {
            if (data[offset] != TS_SYNC_BYTE) {
                offset++
                continue
            }

            try {
                processSinglePacket(data, offset, length)
            } catch (e: Exception) {
                AudioLog.w("TsAudioDemuxer", "Skipping malformed TS packet at offset $offset: ${e.message}")
            }
            offset += TS_PACKET_SIZE
        }

        // 3. Save any trailing unaligned bytes (< 188) to the rollover buffer
        val trailing = length - offset
        if (trailing in 1 until TS_PACKET_SIZE) {
            System.arraycopy(data, offset, remainderBuffer, 0, trailing)
            remainderLength = trailing
        } else {
            remainderLength = 0
        }
    }

    private fun processSinglePacket(data: ByteArray, offset: Int, totalLength: Int) {
        val packetEnd = offset + TS_PACKET_SIZE
        if (packetEnd > totalLength || packetEnd > data.size) return

        val b1 = data[offset + 1].toInt() and 0xFF
        val b2 = data[offset + 2].toInt() and 0xFF
        val b3 = data[offset + 3].toInt() and 0xFF

        val transportError = (b1 and 0x80) != 0
        if (transportError) return // Corrupt packet; drop immediately

        val payloadUnitStart = (b1 and 0x40) != 0
        val pid = ((b1 and 0x1F) shl 8) or b2
        val adaptationFieldControl = (b3 and 0x30) shr 4

        var payloadStart = offset + 4
        if (adaptationFieldControl == 0 || adaptationFieldControl == 2) {
            // Adaptation only or reserved; no payload
            return
        } else if (adaptationFieldControl == 3) {
            val adaptationLength = data[offset + 4].toInt() and 0xFF
            payloadStart = offset + 5 + adaptationLength
        }

        if (payloadStart >= packetEnd) return

        // Fast Audio Stream Sniffing Heuristic:
        // If audio PID is not yet known (or PMT delayed), sniff PES audio header directly
        if (audioPid == -1 && payloadUnitStart && payloadStart + 4 <= packetEnd) {
            val p0 = data[payloadStart].toInt() and 0xFF
            val p1 = data[payloadStart + 1].toInt() and 0xFF
            val p2 = data[payloadStart + 2].toInt() and 0xFF
            val p3 = data[payloadStart + 3].toInt() and 0xFF
            if (p0 == 0x00 && p1 == 0x00 && p2 == 0x01) {
                if ((p3 in 0xC0..0xDF) || p3 == 0xBD) {
                    audioPid = pid
                    detectedMimeType = if (p3 == 0xBD) "audio/ac3" else "audio/mp4a-latm"
                    AudioLog.i("TsAudioDemuxer", "Discovered audio PID=$pid via PES streamId=0x${Integer.toHexString(p3)}")
                    onAudioDiscovered(pid, detectedMimeType, detectedSampleRate, detectedChannels)
                }
            }
        }

        // 1. PAT (Program Association Table) - PID 0
        if (pid == 0) {
            parsePat(data, payloadStart, packetEnd, payloadUnitStart)
            return
        }

        // 2. PMT (Program Map Table)
        if (pid == pmtPid && pmtPid != -1) {
            parsePmt(data, payloadStart, packetEnd, payloadUnitStart)
            return
        }

        // 3. Audio Stream PID
        if (pid == audioPid && audioPid != -1) {
            if (payloadUnitStart) {
                // Previous PES packet is complete; flush and parse
                flushCurrentPes()
            }
            if (currentPesBuffer.size < MAX_PES_BUFFER_SIZE) {
                for (i in payloadStart until packetEnd) {
                    currentPesBuffer.add(data[i])
                }
            } else {
                // Buffer overflow protection: flush accumulated data
                flushCurrentPes()
                for (i in payloadStart until packetEnd) {
                    currentPesBuffer.add(data[i])
                }
            }
            return
        }

        // 4. Any other PID, specifically ALL VIDEO PIDs, are completely discarded here!
    }

    private fun parsePat(data: ByteArray, start: Int, packetEnd: Int, pusi: Boolean) {
        var pos = start
        if (pusi) {
            if (pos >= packetEnd) return
            val pointer = data[pos].toInt() and 0xFF
            pos += 1 + pointer
        }

        if (pos + 8 > packetEnd) return
        val tableId = data[pos].toInt() and 0xFF
        if (tableId != 0x00) return

        val sectionLength = ((data[pos + 1].toInt() and 0x0F) shl 8) or (data[pos + 2].toInt() and 0xFF)
        val end = (pos + 3 + sectionLength - 4).coerceAtMost(packetEnd)
        var p = pos + 8 // Skip transport_stream_id, version, section numbers

        while (p + 4 <= end) {
            val programNum = ((data[p].toInt() and 0xFF) shl 8) or (data[p + 1].toInt() and 0xFF)
            val pid = ((data[p + 2].toInt() and 0x1F) shl 8) or (data[p + 3].toInt() and 0xFF)
            if (programNum != 0) {
                pmtPid = pid
                break
            }
            p += 4
        }
    }

    private fun parsePmt(data: ByteArray, start: Int, packetEnd: Int, pusi: Boolean) {
        var pos = start
        if (pusi) {
            if (pos >= packetEnd) return
            val pointer = data[pos].toInt() and 0xFF
            pos += 1 + pointer
        }

        if (pos + 12 > packetEnd) return
        val tableId = data[pos].toInt() and 0xFF
        if (tableId != 0x02) return

        val sectionLength = ((data[pos + 1].toInt() and 0x0F) shl 8) or (data[pos + 2].toInt() and 0xFF)
        val end = (pos + 3 + sectionLength - 4).coerceAtMost(packetEnd)
        val programInfoLength = ((data[pos + 10].toInt() and 0x0F) shl 8) or (data[pos + 11].toInt() and 0xFF)
        var esPos = pos + 12 + programInfoLength
        if (esPos > end) return

        var chosenAudioPid = -1
        var chosenMime = "audio/mp4a-latm"

        while (esPos + 5 <= end) {
            val streamType = data[esPos].toInt() and 0xFF
            val elementaryPid = ((data[esPos + 1].toInt() and 0x1F) shl 8) or (data[esPos + 2].toInt() and 0xFF)
            val esInfoLength = ((data[esPos + 3].toInt() and 0x0F) shl 8) or (data[esPos + 4].toInt() and 0xFF)

            when (streamType) {
                // Video stream types (H.264, H.265/HEVC, MPEG-2 Video)
                0x1B, 0x24, 0x02 -> {
                    videoPid = elementaryPid
                    onVideoDiscovered(elementaryPid, streamType)
                }
                // Audio stream types
                STREAM_TYPE_AAC_ADTS, STREAM_TYPE_AAC_RAW -> {
                    // AAC ADTS is preferred
                    chosenAudioPid = elementaryPid
                    chosenMime = "audio/mp4a-latm"
                }
                STREAM_TYPE_AAC_LATM -> {
                    if (chosenAudioPid == -1) {
                        chosenAudioPid = elementaryPid
                        chosenMime = "audio/mp4a-latm"
                    }
                }
                STREAM_TYPE_MP1_AUDIO, STREAM_TYPE_MP2_AUDIO -> {
                    if (chosenAudioPid == -1) {
                        chosenAudioPid = elementaryPid
                        chosenMime = "audio/mpeg"
                    }
                }
                STREAM_TYPE_AC3 -> {
                    if (chosenAudioPid == -1) {
                        chosenAudioPid = elementaryPid
                        chosenMime = "audio/ac3"
                    }
                }
                STREAM_TYPE_EAC3 -> {
                    if (chosenAudioPid == -1) {
                        chosenAudioPid = elementaryPid
                        chosenMime = "audio/eac3"
                    }
                }
                STREAM_TYPE_DVB_AC3 -> {
                    // DVB stream type 0x06 with AC-3 / E-AC-3 descriptors
                    if (chosenAudioPid == -1) {
                        chosenAudioPid = elementaryPid
                        chosenMime = "audio/ac3"
                    }
                }
            }

            val advance = 5 + esInfoLength
            if (advance <= 0) break
            esPos += advance
        }

        if (chosenAudioPid != -1 && chosenAudioPid != audioPid) {
            audioPid = chosenAudioPid
            detectedMimeType = chosenMime
            onAudioDiscovered(audioPid, detectedMimeType, detectedSampleRate, detectedChannels)
        }
    }

    private fun flushCurrentPes() {
        if (currentPesBuffer.size < 6) {
            currentPesBuffer.clear()
            return
        }

        val pesBytes = currentPesBuffer.toByteArray()
        currentPesBuffer.clear()

        // Verify PES start code prefix: 0x000001
        if (pesBytes[0] != 0x00.toByte() || pesBytes[1] != 0x00.toByte() || pesBytes[2] != 0x01.toByte()) {
            return
        }

        val streamId = pesBytes[3].toInt() and 0xFF
        // Audio stream IDs are 0xC0..0xDF or 0xBD (private stream 1 / AC-3)
        val isAudioStream = (streamId in 0xC0..0xDF) || streamId == 0xBD
        if (!isAudioStream) return

        var payloadOffset = 6
        var ptsUs: Long = lastPtsUs

        // If standard PES header with flags (streamId != program_stream_map, padding, etc.)
        if (pesBytes.size >= 9) {
            val flags2 = pesBytes[7].toInt() and 0xFF
            val headerDataLen = pesBytes[8].toInt() and 0xFF
            val ptsDtsFlags = (flags2 and 0xC0) shr 6

            if ((ptsDtsFlags == 2 || ptsDtsFlags == 3) && pesBytes.size >= 14 && headerDataLen >= 5) {
                // Extract 33-bit PTS
                val b0 = pesBytes[9].toLong() and 0xFFL
                val b1 = pesBytes[10].toLong() and 0xFFL
                val b2 = pesBytes[11].toLong() and 0xFFL
                val b3 = pesBytes[12].toLong() and 0xFFL
                val b4 = pesBytes[13].toLong() and 0xFFL

                val pts33 = ((b0 and 0x0EL) shl 29) or
                        ((b1 and 0xFFL) shl 22) or
                        ((b2 and 0xFEL) shl 14) or
                        ((b3 and 0xFFL) shl 7) or
                        ((b4 and 0xFEL) shr 1)

                ptsUs = (pts33 * 1_000_000L) / 90_000L
                lastPtsUs = ptsUs
            }

            payloadOffset = 9 + headerDataLen
        }

        if (payloadOffset >= pesBytes.size) return
        val esSize = pesBytes.size - payloadOffset
        val esBytes = ByteArray(esSize)
        System.arraycopy(pesBytes, payloadOffset, esBytes, 0, esSize)

        // If AAC: attempt to extract individual valid ADTS frames
        if (detectedMimeType == "audio/mp4a-latm") {
            val adtsFrames = parseAdtsElementaryStream(esBytes, ptsUs)
            if (adtsFrames.isNotEmpty()) {
                for (frame in adtsFrames) {
                    onFrameExtracted(frame)
                }
            } else {
                // Fallback: raw AAC access unit or unaligned frame
                onFrameExtracted(
                    ExtractedAudioFrame(
                        data = esBytes,
                        sampleRate = detectedSampleRate,
                        channelCount = detectedChannels,
                        ptsUs = ptsUs,
                        mimeType = detectedMimeType
                    )
                )
            }
        } else {
            // Raw MP3 / AC-3 elementary stream chunk
            onFrameExtracted(
                ExtractedAudioFrame(
                    data = esBytes,
                    sampleRate = detectedSampleRate,
                    channelCount = detectedChannels,
                    ptsUs = ptsUs,
                    mimeType = detectedMimeType
                )
            )
        }
    }

    /**
     * Parses AAC ADTS frames from an Elementary Stream payload.
     * ADTS syncword is 0xFFF (12 bits).
     */
    private fun parseAdtsElementaryStream(esData: ByteArray, basePtsUs: Long): List<ExtractedAudioFrame> {
        val frames = mutableListOf<ExtractedAudioFrame>()
        var i = 0
        var currentPtsUs = basePtsUs

        while (i + 7 <= esData.size) {
            val b0 = esData[i].toInt() and 0xFF
            val b1 = esData[i + 1].toInt() and 0xFF

            if (b0 == 0xFF && (b1 and 0xF0) == 0xF0) {
                val b2 = esData[i + 2].toInt() and 0xFF
                val b3 = esData[i + 3].toInt() and 0xFF
                val b4 = esData[i + 4].toInt() and 0xFF
                val b5 = esData[i + 5].toInt() and 0xFF

                val frameLen = ((b3 and 0x03) shl 11) or (b4 shl 3) or ((b5 and 0xE0) shr 5)
                if (frameLen in 7..8191 && i + frameLen <= esData.size) {
                    // Extract sampling frequency index
                    val freqIdx = (b2 and 0x3C) shr 2
                    val sampleRate = if (freqIdx in ADTS_FREQUENCIES.indices) ADTS_FREQUENCIES[freqIdx] else 48000
                    detectedSampleRate = sampleRate

                    // Extract channel configuration
                    val channelConfig = ((b2 and 0x01) shl 2) or ((b3 and 0xC0) shr 6)
                    val channels = if (channelConfig in 1..8) channelConfig else 2
                    detectedChannels = channels

                    // MediaCodec's audio/mp4a-latm decoder consumes AAC access units,
                    // not ADTS transport headers. Strip the 7/9-byte ADTS header and provide
                    // AudioSpecificConfig through MediaFormat csd-0 in AudioDecoderEngine.
                    val headerLength = if ((b1 and 0x01) != 0) 7 else 9
                    if (frameLen <= headerLength) {
                        i++
                        continue
                    }
                    val payloadLength = frameLen - headerLength
                    val frameData = ByteArray(payloadLength)
                    System.arraycopy(esData, i + headerLength, frameData, 0, payloadLength)

                    frames.add(
                        ExtractedAudioFrame(
                            data = frameData,
                            sampleRate = sampleRate,
                            channelCount = channels,
                            ptsUs = currentPtsUs,
                            mimeType = "audio/mp4a-latm"
                        )
                    )

                    // Increment PTS by 1024 samples per AAC frame
                    currentPtsUs += (1024L * 1_000_000L / sampleRate)
                    i += frameLen
                    continue
                }
            }
            i++
        }
        return frames
    }
}
