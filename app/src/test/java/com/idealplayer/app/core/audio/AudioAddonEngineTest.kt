package com.idealplayer.app.core.audio

import com.idealplayer.app.core.audio.AudioChannelGroup
import com.idealplayer.app.core.audio.AudioDecoderEngine
import com.idealplayer.app.core.audio.AudioErrorCode
import com.idealplayer.app.core.audio.AudioExtractionState
import com.idealplayer.app.core.audio.AudioOnlyOutputEngine
import com.idealplayer.app.core.audio.AudioSessionManager
import com.idealplayer.app.core.audio.AudioSourceClassifier
import com.idealplayer.app.core.audio.AudioStreamInspector
import com.idealplayer.app.core.audio.ExtractedAudioFrame
import com.idealplayer.app.core.audio.TsAudioDemuxer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 34])
class AudioAddonEngineTest {

    // =========================================================================
    // 1. Channel Discovery and Classification Tests
    // =========================================================================

    @Test
    fun classify_beInSports_englishVariants() {
        assertEquals(AudioChannelGroup.BEIN_SPORTS, AudioSourceClassifier.classify("beIN SPORTS 1 HD", "SPORTS"))
        assertEquals(AudioChannelGroup.BEIN_SPORTS, AudioSourceClassifier.classify("beIN Sports 2 Premium", "VIP Sports"))
        assertEquals(AudioChannelGroup.BEIN_SPORTS, AudioSourceClassifier.classify("beINSPORT 3 4K", "beIN Package"))
        assertEquals(AudioChannelGroup.BEIN_SPORTS, AudioSourceClassifier.classify("BE IN SPORTS NEWS", "News"))
        assertEquals(AudioChannelGroup.BEIN_SPORTS, AudioSourceClassifier.classify("beIN SPORTS AFC 1", "AFC"))
    }

    @Test
    fun classify_beInSports_arabicVariants() {
        assertEquals(AudioChannelGroup.BEIN_SPORTS, AudioSourceClassifier.classify("بي ان سبورت 1", "الرياضة"))
        assertEquals(AudioChannelGroup.BEIN_SPORTS, AudioSourceClassifier.classify("بي إن سبورت 2 إتش دي", "باقة بي ان"))
        assertEquals(AudioChannelGroup.BEIN_SPORTS, AudioSourceClassifier.classify("بين سبورت الإخبارية", "أخبار"))
        assertEquals(AudioChannelGroup.BEIN_SPORTS, AudioSourceClassifier.classify("قناة بي ان ماكس 1", "كأس أفريقيا"))
    }

    @Test
    fun classify_alwan_englishAndArabic() {
        assertEquals(AudioChannelGroup.ALWAN, AudioSourceClassifier.classify("ALWAN 1 HD", "Audio Commentary"))
        assertEquals(AudioChannelGroup.ALWAN, AudioSourceClassifier.classify("Al Wan Audio Sport", "Audio Feeds"))
        assertEquals(AudioChannelGroup.ALWAN, AudioSourceClassifier.classify("صوتية ألوان 1", "قنوات صوتية"))
        assertEquals(AudioChannelGroup.ALWAN, AudioSourceClassifier.classify("راديو الوان سبورت 2", "صوت"))
    }

    @Test
    fun classify_excludesIrrelevantChannels() {
        assertEquals(AudioChannelGroup.OTHER, AudioSourceClassifier.classify("Abu Dhabi Sports 1 HD", "UAE Sports"))
        assertEquals(AudioChannelGroup.OTHER, AudioSourceClassifier.classify("SSC 1 HD", "Saudi Sports"))
        assertEquals(AudioChannelGroup.OTHER, AudioSourceClassifier.classify("الكاس 1 HD", "قطر"))
        assertEquals(AudioChannelGroup.OTHER, AudioSourceClassifier.classify("Rotana Cinema", "Movies"))
        assertEquals(AudioChannelGroup.OTHER, AudioSourceClassifier.classify("Dubai Sports 2", "Dubai"))
        assertEquals(AudioChannelGroup.OTHER, AudioSourceClassifier.classify("MBC 1", "General"))
    }

    @Test
    fun classify_handlesDiacriticsAndSpaces() {
        val test1 = AudioSourceClassifier.classify("بَيْنَ سْبُورْتْ   ١   عَالِيَةُ الدِّقَّةِ")
        assertEquals(AudioChannelGroup.BEIN_SPORTS, test1)

        val test2 = AudioSourceClassifier.classify("  أَلْوَانْ - القَنَاةُ الصَّوْتِيَّةُ  ")
        assertEquals(AudioChannelGroup.ALWAN, test2)
    }

    // =========================================================================
    // 2. Stream Inspection and Container Parsing Tests
    // =========================================================================

    @Test
    fun inspectHls_detectsSeparateAudioRenditions() {
        val masterPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-aac",NAME="Arabic",DEFAULT=YES,AUTOSELECT=YES,LANGUAGE="ar",URI="tracks/audio_ar.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,CODECS="avc1.64001f,mp4a.40.2",RESOLUTION=1920x1080,AUDIO="audio-aac"
            video_1080p.m3u8
        """.trimIndent()

        val renditions = AudioStreamInspector.parseHlsAudioRenditions(masterPlaylist, "http://provider.tv/live/master.m3u8")
        assertEquals(1, renditions.size)
        assertEquals("http://provider.tv/live/tracks/audio_ar.m3u8", renditions[0])
    }

    @Test
    fun inspectHls_parsesMediaSegments() {
        val mediaPlaylist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            segment0.ts
            #EXTINF:6.0,
            segment1.ts
        """.trimIndent()

        val segments = AudioStreamInspector.parseHlsSegments(mediaPlaylist, "http://provider.tv/live/media.m3u8")
        assertEquals(2, segments.size)
        assertEquals("http://provider.tv/live/segment0.ts", segments[0])
        assertEquals("http://provider.tv/live/segment1.ts", segments[1])
    }

    @Test
    fun inspect_rejectsUnsafeSchemes() {
        val fileResult = AudioStreamInspector.inspectStream("file:///sdcard/stream.ts")
        assertFalse(fileResult.isSupported)
        assertTrue(fileResult.errorMessage?.contains("بروتوكول") == true)

        val contentResult = AudioStreamInspector.inspectStream("content://media/external/123")
        assertFalse(contentResult.isSupported)
    }

    // =========================================================================
    // 3. MPEG-TS Demuxer, Audio PID Isolation, and PES Header Stripping
    // =========================================================================

    @Test
    fun tsDemuxer_discardsVideoPidsEntirely() {
        var videoDiscarded = false
        val demuxer = TsAudioDemuxer(
            onFrameExtracted = {},
            onVideoDiscovered = { pid, streamType ->
                videoDiscarded = true
                assertEquals(0x0100, pid)
                assertEquals(0x1B, streamType) // H.264 video
            }
        )

        // Build PMT with Video PID 0x0100 (Stream Type 0x1B)
        val pmtPacket = ByteArray(188)
        pmtPacket[0] = 0x47.toByte() // sync
        pmtPacket[1] = 0x40.toByte() // PUSI + PID 0
        pmtPacket[2] = 0x64.toByte() // PID 100 (PMT)
        pmtPacket[3] = 0x10.toByte() // payload only
        pmtPacket[4] = 0x00.toByte() // pointer field
        pmtPacket[5] = 0x02.toByte() // table_id 2 (PMT)
        pmtPacket[6] = 0xB0.toByte()
        pmtPacket[7] = 0x17.toByte() // section length 23
        // Program info length = 0
        pmtPacket[15] = 0x00.toByte()
        pmtPacket[16] = 0x00.toByte()
        // ES 1: Video PID 0x0100, type 0x1B
        pmtPacket[17] = 0x1B.toByte() // H.264
        pmtPacket[18] = 0xE1.toByte() // PID 0x0100
        pmtPacket[19] = 0x00.toByte()
        pmtPacket[20] = 0xF0.toByte() // ES info length 0
        pmtPacket[21] = 0x00.toByte()

        // Set pmtPid manually to 100
        val pmtPidField = TsAudioDemuxer::class.java.getDeclaredField("pmtPid")
        pmtPidField.isAccessible = true
        pmtPidField.set(demuxer, 100)

        demuxer.processTsData(pmtPacket, 188)
        assertTrue("Video stream must be discovered and marked for discarding", videoDiscarded)

        // Send a video packet
        val videoPacket = ByteArray(188)
        videoPacket[0] = 0x47.toByte()
        videoPacket[1] = 0x01.toByte()
        videoPacket[2] = 0x00.toByte() // PID 0x0100
        videoPacket[3] = 0x10.toByte()

        var extractedAudioCount = 0
        val demuxer2 = TsAudioDemuxer(
            onFrameExtracted = { extractedAudioCount++ }
        )
        demuxer2.processTsData(videoPacket, 188)
        assertEquals("Video packets must never produce audio frames", 0, extractedAudioCount)
    }

    @Test
    fun tsDemuxer_stripsPesHeaderAndExtractsAdtsFrames() {
        val extractedFrames = mutableListOf<ExtractedAudioFrame>()
        val demuxer = TsAudioDemuxer(
            onFrameExtracted = { extractedFrames.add(it) }
        )

        // Set audioPid to 200, MIME to audio/mp4a-latm
        val audioPidField = TsAudioDemuxer::class.java.getDeclaredField("audioPid")
        audioPidField.isAccessible = true
        audioPidField.set(demuxer, 200)

        // Create a valid 7-byte ADTS AAC frame
        val rawAac = ByteArray(64) { 0x55.toByte() }
        val validAdts = AudioOnlyOutputEngine.addAdtsHeader(rawAac, 48000, 2)

        // Build a PES packet containing the ADTS frame:
        // 00 00 01 C0 (audio stream 0), length, flags, header data length (PTS), then ES data
        val pesHeader = byteArrayOf(
            0x00, 0x00, 0x01, 0xC0.toByte(), // start code + stream_id
            0x00, 0x50, // packet length
            0x80.toByte(), 0x80.toByte(), // flags (PTS present: 0x80)
            0x05, // header data length 5
            0x21, 0x00, 0x01, 0x00, 0x01 // PTS data (33-bit)
        )
        val fullPes = pesHeader + validAdts

        // Wrap into TS packets with PID 200 and PUSI = 1
        val tsPacket1 = ByteArray(188)
        tsPacket1[0] = 0x47.toByte()
        tsPacket1[1] = 0x40.toByte() // PUSI = 1, PID upper bits = 0
        tsPacket1[2] = 200.toByte() // PID = 200
        tsPacket1[3] = 0x10.toByte() // payload only
        System.arraycopy(fullPes, 0, tsPacket1, 4, fullPes.size.coerceAtMost(184))

        // Next packet triggers flush
        val tsPacket2 = ByteArray(188)
        tsPacket2[0] = 0x47.toByte()
        tsPacket2[1] = 0x40.toByte() // PUSI = 1 (triggers flush of packet 1)
        tsPacket2[2] = 200.toByte()
        tsPacket2[3] = 0x10.toByte()

        demuxer.processTsData(tsPacket1 + tsPacket2, 376)

        assertEquals("Should extract exactly 1 valid ADTS frame", 1, extractedFrames.size)
        val frame = extractedFrames[0]
        assertEquals(48000, frame.sampleRate)
        assertEquals(2, frame.channelCount)
        assertEquals("audio/mp4a-latm", frame.mimeType)
        // Verify ADTS syncword on extracted data (0xFFF)
        assertEquals(0xFF.toByte(), frame.data[0])
        assertEquals(0xF0.toByte(), (frame.data[1].toInt() and 0xF0).toByte())
        assertEquals(validAdts.size, frame.data.size)
    }

    // =========================================================================
    // 4. ADTS Header Creation and Parameter Integrity Tests
    // =========================================================================

    @Test
    fun adtsHeader_encodesSampleRateAndChannelsCorrectly() {
        val payload = ByteArray(100) { 0x11.toByte() }

        // Test 48000 Hz (index 3), 2 channels
        val frame48k = AudioOnlyOutputEngine.addAdtsHeader(payload, 48000, 2)
        assertEquals(107, frame48k.size) // 100 + 7
        assertEquals(0xFF.toByte(), frame48k[0])
        assertEquals(0xF1.toByte(), frame48k[1])
        // Profile LC (2) -> (2 - 1) shl 6 = 0x40; freqIdx 3 shl 2 = 0x0C; channels 2 shr 2 = 0
        // byte 2 = 0x40 | 0x0C = 0x4C
        assertEquals(0x4C.toByte(), frame48k[2])

        // Test 44100 Hz (index 4), 1 channel
        val frame44k = AudioOnlyOutputEngine.addAdtsHeader(payload, 44100, 1)
        assertEquals(0x50.toByte(), frame44k[2]) // 0x40 | (4 shl 2) = 0x50
    }

    // =========================================================================
    // 5. Independent Audio Output and Loopback Server Tests
    // =========================================================================

    @Test
    fun outputEngine_streamsValidAdtsOverHttpLoopback() {
        val outputEngine = AudioOnlyOutputEngine(sessionId = "srv123", sampleRate = 48000, channels = 2)
        val streamUrl = outputEngine.start()

        assertTrue(streamUrl.startsWith("http://127.0.0.1:"))
        assertTrue(streamUrl.endsWith(".aac"))

        // Enqueue valid ADTS frame
        val testPayload = ByteArray(120) { 0xAA.toByte() }
        val testFrame = AudioOnlyOutputEngine.addAdtsHeader(testPayload, 48000, 2)
        outputEngine.enqueueRawAdtsFrame(testFrame)

        // Connect via HTTP client
        val url = URL(streamUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.requestMethod = "GET"

        assertEquals(200, connection.responseCode)
        assertEquals("audio/aac", connection.contentType)

        val inStream = connection.inputStream
        val receivedHeader = ByteArray(7)
        val read = inStream.read(receivedHeader)
        assertEquals(7, read)

        // Verify standard ADTS syncword 0xFFF
        assertEquals(0xFF.toByte(), receivedHeader[0])
        assertEquals((0xF0).toByte(), (receivedHeader[1].toInt() and 0xF0).toByte())

        connection.disconnect()
        outputEngine.stop()
    }

    // =========================================================================
    // 6. Session Lifecycle and Clean Cancellation Tests
    // =========================================================================

    @Test
    fun sessionManager_cleansUpOnStop() {
        val sessionManager = AudioSessionManager()
        val states = mutableListOf<AudioExtractionState>()

        val localUrl = sessionManager.startSession(
            streamId = "1001",
            sourceUrl = "http://127.0.0.1:9999/test.ts"
        ) { state, _, _, _ ->
            states.add(state)
        }

        assertTrue("Initial state must be STREAM_OPENING", states.contains(AudioExtractionState.STREAM_OPENING))
        assertFalse("Must NOT emit RESOURCE_READY before audio is verified", states.contains(AudioExtractionState.RESOURCE_READY))

        // Stop session cleanly
        sessionManager.stopCurrentSession()
        assertFalse(sessionManager.isSessionReady())
        sessionManager.shutdown()
    }

    // =========================================================================
    // 7. Multi-Channel PCM Downmix & Split Packet Reassembly Tests
    // =========================================================================

    @Test
    fun downmixToStereo_converts5Point1ToStereoCorrectly() {
        // 1 frame of 5.1 PCM (6 channels * 2 bytes = 12 bytes)
        // L=1000, R=2000, C=1000, LFE=5000, Ls=1000, Rs=1000
        val pcm51 = ByteArray(12)
        // L = 1000
        pcm51[0] = (1000 and 0xFF).toByte()
        pcm51[1] = ((1000 shr 8) and 0xFF).toByte()
        // R = 2000
        pcm51[2] = (2000 and 0xFF).toByte()
        pcm51[3] = ((2000 shr 8) and 0xFF).toByte()
        // C = 1000
        pcm51[4] = (1000 and 0xFF).toByte()
        pcm51[5] = ((1000 shr 8) and 0xFF).toByte()
        // LFE = 5000
        pcm51[6] = (5000 and 0xFF).toByte()
        pcm51[7] = ((5000 shr 8) and 0xFF).toByte()
        // Ls = 1000
        pcm51[8] = (1000 and 0xFF).toByte()
        pcm51[9] = ((1000 shr 8) and 0xFF).toByte()
        // Rs = 1000
        pcm51[10] = (1000 and 0xFF).toByte()
        pcm51[11] = ((1000 shr 8) and 0xFF).toByte()

        val stereo = AudioOnlyOutputEngine.downmixToStereo(pcm51, 6)
        assertEquals("Stereo output must be 4 bytes per frame", 4, stereo.size)

        val outL = ((stereo[0].toInt() and 0xFF) or (stereo[1].toInt() shl 8)).toShort()
        val outR = ((stereo[2].toInt() and 0xFF) or (stereo[3].toInt() shl 8)).toShort()

        // Expected:
        // outL = 1000 + 0.707*1000 + 0.707*1000 = 1000 + 707 + 707 = ~2414
        // outR = 2000 + 0.707*1000 + 0.707*1000 = 2000 + 707 + 707 = ~3414
        assertTrue("outL must be approximately 2414", outL in 2410..2420)
        assertTrue("outR must be approximately 3414", outR in 3410..3420)
    }

    @Test
    fun downmixToStereo_convertsMonoToStereoDuplicate() {
        // 1 frame of mono PCM = 2 bytes
        val mono = byteArrayOf(0x34.toByte(), 0x12.toByte()) // 0x1234
        val stereo = AudioOnlyOutputEngine.downmixToStereo(mono, 1)

        assertEquals(4, stereo.size)
        // Left
        assertEquals(0x34.toByte(), stereo[0])
        assertEquals(0x12.toByte(), stereo[1])
        // Right (duplicated)
        assertEquals(0x34.toByte(), stereo[2])
        assertEquals(0x12.toByte(), stereo[3])
    }

    @Test
    fun demuxer_reassemblesSplitPacketsAcrossTcpReads() {
        val extractedFrames = mutableListOf<ExtractedAudioFrame>()
        val demuxer = TsAudioDemuxer(onFrameExtracted = { extractedFrames.add(it) })

        val payload = ByteArray(30) { 0x55.toByte() }
        val validAdts = AudioOnlyOutputEngine.addAdtsHeader(payload, 48000, 2)
        val pesHeader = byteArrayOf(
            0x00, 0x00, 0x01, 0xC0.toByte(),
            0x00, 0x30,
            0x80.toByte(), 0x80.toByte(),
            0x05,
            0x21, 0x00, 0x01, 0x00, 0x01
        )
        val fullPes = pesHeader + validAdts

        val tsPacket1 = ByteArray(188)
        tsPacket1[0] = 0x47.toByte()
        tsPacket1[1] = 0x40.toByte()
        tsPacket1[2] = 200.toByte()
        tsPacket1[3] = 0x10.toByte()
        System.arraycopy(fullPes, 0, tsPacket1, 4, fullPes.size.coerceAtMost(184))

        val tsPacket2 = ByteArray(188)
        tsPacket2[0] = 0x47.toByte()
        tsPacket2[1] = 0x40.toByte() // PUSI = 1 flush
        tsPacket2[2] = 200.toByte()
        tsPacket2[3] = 0x10.toByte()

        val fullData = tsPacket1 + tsPacket2 // 376 bytes
        // Split deliberately in the middle of tsPacket1 at byte 100
        val chunk1 = fullData.copyOfRange(0, 100)
        val chunk2 = fullData.copyOfRange(100, 376)

        demuxer.processTsData(chunk1, chunk1.size)
        demuxer.processTsData(chunk2, chunk2.size)

        assertEquals("Should successfully extract 1 frame despite split TCP delivery", 1, extractedFrames.size)
        assertEquals(48000, extractedFrames[0].sampleRate)
        assertEquals(2, extractedFrames[0].channelCount)
    }
}
