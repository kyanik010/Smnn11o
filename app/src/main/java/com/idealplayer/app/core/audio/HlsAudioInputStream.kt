package com.idealplayer.app.core.audio

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Presents a live or VOD HLS media playlist as one sequential InputStream.
 *
 * MPEG-TS segments are concatenated without rewriting their payload so TsAudioDemuxer
 * can select the audio PID and discard video. Encrypted and fragmented-MP4 playlists
 * are rejected explicitly because this decoder pipeline does not decrypt AES keys or
 * parse fMP4/CMAF boxes.
 */
internal class HlsAudioInputStream(
    private val playlistUrl: String,
    private val refreshDelayMs: Long = 1_000L
) : InputStream() {
    private val closed = AtomicBoolean(false)
    private var currentSegment: ByteArrayInputStream? = null
    @Volatile private var activeConnection: HttpURLConnection? = null
    private var lastSequence = -1L
    private var endListReached = false
    private var emptyRefreshes = 0

    override fun read(): Int {
        val one = ByteArray(1)
        val count = read(one, 0, 1)
        return if (count < 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || length > buffer.size - offset) {
            throw IndexOutOfBoundsException()
        }
        if (length == 0) return 0
        if (closed.get()) return -1

        while (!closed.get()) {
            val active = currentSegment
            if (active != null) {
                val count = active.read(buffer, offset, length)
                if (count >= 0) return count
                try { active.close() } catch (_: Exception) {}
                currentSegment = null
            }

            if (endListReached) return -1
            loadNextSegment()
        }
        return -1
    }

    private fun loadNextSegment() {
        while (!closed.get()) {
            var connection: HttpURLConnection? = null
            try {
                connection = AudioStreamInspector.openHttpConnectionWithRedirects(playlistUrl)
                activeConnection = connection
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("HLS playlist HTTP $code")

                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (text.length > MAX_PLAYLIST_CHARS) throw IOException("HLS playlist exceeds safety limit")
                val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
                if (lines.firstOrNull() != "#EXTM3U") throw IOException("Invalid HLS media playlist")

                val keyLine = lines.firstOrNull { it.startsWith("#EXT-X-KEY:") }
                if (keyLine != null && !keyLine.contains("METHOD=NONE")) {
                    throw IOException("Encrypted HLS is not supported by the current audio extraction pipeline")
                }
                if (lines.any { it.startsWith("#EXT-X-MAP:") }) {
                    throw IOException("Fragmented MP4/CMAF HLS is not supported by the MPEG-TS demuxer")
                }
                if (lines.any { it.startsWith("#EXT-X-BYTERANGE:") }) {
                    throw IOException("HLS byte-range segments are not supported by the current segment reader")
                }

                val mediaSequence = lines.firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }
                    ?.substringAfter(':')?.toLongOrNull() ?: 0L
                val segmentUrls = lines.filterNot { it.startsWith("#") }
                    .map { AudioStreamInspector.resolveRelativeUrl(playlistUrl, it) }
                val newSegments = segmentUrls.mapIndexedNotNull { index, url ->
                    val sequence = mediaSequence + index
                    if (sequence > lastSequence) sequence to url else null
                }

                if (newSegments.isEmpty()) {
                    endListReached = lines.any { it == "#EXT-X-ENDLIST" }
                    if (endListReached) return
                    emptyRefreshes++
                    if (emptyRefreshes > MAX_EMPTY_REFRESHES) {
                        throw IOException("HLS playlist did not publish new segments")
                    }
                    Thread.sleep(refreshDelayMs)
                    continue
                }

                emptyRefreshes = 0
                for ((sequence, segmentUrl) in newSegments) {
                    if (closed.get()) return
                    val segmentConnection = AudioStreamInspector.openHttpConnectionWithRedirects(segmentUrl)
                    activeConnection = segmentConnection
                    try {
                        val segmentCode = segmentConnection.responseCode
                        if (segmentCode !in 200..299) throw IOException("HLS segment HTTP $segmentCode")
                        val bytes = segmentConnection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val chunk = ByteArray(16 * 1024)
                            var total = 0
                            while (true) {
                                if (closed.get()) throw IOException("HLS stream closed")
                                val read = input.read(chunk)
                                if (read < 0) break
                                total += read
                                if (total > MAX_SEGMENT_BYTES) throw IOException("HLS segment exceeds safety limit")
                                output.write(chunk, 0, read)
                            }
                            output.toByteArray()
                        }
                        if (bytes.isNotEmpty()) {
                            lastSequence = sequence
                            currentSegment = ByteArrayInputStream(bytes)
                            endListReached = lines.any { it == "#EXT-X-ENDLIST" } && sequence == newSegments.last().first
                            return
                        }
                        lastSequence = sequence
                    } finally {
                        if (activeConnection === segmentConnection) activeConnection = null
                        segmentConnection.disconnect()
                    }
                }

                endListReached = lines.any { it == "#EXT-X-ENDLIST" } && currentSegment == null
                if (!endListReached) Thread.sleep(refreshDelayMs)
            } finally {
                if (activeConnection === connection) activeConnection = null
                try { connection?.disconnect() } catch (_: Exception) {}
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try { currentSegment?.close() } catch (_: Exception) {}
        currentSegment = null
        try { activeConnection?.disconnect() } catch (_: Exception) {}
        activeConnection = null
    }

    companion object {
        private const val MAX_PLAYLIST_CHARS = 1_000_000
        private const val MAX_SEGMENT_BYTES = 8 * 1024 * 1024
        private const val MAX_EMPTY_REFRESHES = 30
    }
}
