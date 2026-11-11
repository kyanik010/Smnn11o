package com.idealplayer.app.core.audio

import android.net.Uri
import android.util.Log
import com.idealplayer.app.core.common.SensitiveLog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Inspects IPTV streams (HLS m3u8, MPEG-TS, AAC) to identify container format,
 * audio and video tracks, detect HLS audio renditions, and determine extraction feasibility.
 */
object AudioStreamInspector {

    private const val TAG = "SONIXStreamInspector"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    const val USER_AGENT = "SONIX-TV/1.0"

    fun inspectStream(streamUrl: String): AudioInspectionResult {
        val trimmedUrl = streamUrl.trim()
        val safeLogUrl = SensitiveLog.redactUrl(trimmedUrl)
        AudioLog.i(TAG, "Inspecting stream: $safeLogUrl")

        val scheme = try {
            java.net.URI(trimmedUrl).scheme?.lowercase()
        } catch (_: Exception) {
            try { Uri.parse(trimmedUrl).scheme?.lowercase() } catch (_: Exception) { null }
        }

        if (scheme != "http" && scheme != "https") {
            return AudioInspectionResult(
                isSupported = false,
                containerType = "UNKNOWN",
                audioMimeType = "",
                errorCode = AudioErrorCode.SOURCE_HTTP_ERROR,
                errorMessage = "بروتوكول البث غير مدعوم، يجب استخدام HTTP أو HTTPS"
            )
        }

        val lastSegment = try {
            java.net.URI(trimmedUrl).path?.substringAfterLast('/') ?: ""
        } catch (_: Exception) {
            try { Uri.parse(trimmedUrl).lastPathSegment ?: "" } catch (_: Exception) { "" }
        }
        val extension = lastSegment.substringAfterLast('.', "").lowercase()

        return when (extension) {
            "m3u8" -> inspectHlsStream(trimmedUrl)
            "ts" -> AudioInspectionResult(
                isSupported = true,
                containerType = "MPEG_TS",
                audioMimeType = "audio/mp4a-latm",
                sampleRate = 48000,
                channelCount = 2,
                bitrate = 128000,
                hasVideoTrack = true,
                videoMimeType = "video/avc"
            )
            "aac" -> AudioInspectionResult(
                isSupported = true,
                containerType = "AAC_RAW",
                audioMimeType = "audio/mp4a-latm",
                sampleRate = 48000,
                channelCount = 2,
                bitrate = 128000,
                hasVideoTrack = false
            )
            "mp3" -> AudioInspectionResult(
                isSupported = true,
                containerType = "MP3_RAW",
                audioMimeType = "audio/mpeg",
                sampleRate = 44100,
                channelCount = 2,
                bitrate = 128000,
                hasVideoTrack = false
            )
            else -> inspectGenericStream(trimmedUrl, extension)
        }
    }

    fun inspectHlsStream(playlistUrl: String): AudioInspectionResult {
        var connection: HttpURLConnection? = null
        try {
            connection = openHttpConnectionWithRedirects(playlistUrl)
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return AudioInspectionResult(
                    isSupported = false,
                    containerType = "HLS",
                    audioMimeType = "",
                    errorCode = AudioErrorCode.SOURCE_HTTP_ERROR,
                    errorMessage = "فشل الاتصال بقائمة HLS (رمز الاستجابة $responseCode)"
                )
            }

            val effectivePlaylistUrl = connection.url?.toString()?.takeIf { it.isNotBlank() } ?: playlistUrl
            val lines = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).readLines()
            if (lines.none { it.startsWith("#EXTM3U") }) {
                return AudioInspectionResult(
                    isSupported = false,
                    containerType = "HLS",
                    audioMimeType = "",
                    errorCode = AudioErrorCode.INVALID_HLS_PLAYLIST,
                    errorMessage = "الملف ليس قائمة تشغيل HLS صالحة"
                )
            }

            // 1. Detect separate Audio Renditions: #EXT-X-MEDIA:TYPE=AUDIO
            var separateAudioUri: String? = null
            for (line in lines) {
                if (line.startsWith("#EXT-X-MEDIA:TYPE=AUDIO")) {
                    val uriMatch = Regex("""URI="([^"]+)"""").find(line)
                    if (uriMatch != null) {
                        val rawUri = uriMatch.groupValues[1]
                        separateAudioUri = resolveRelativeUrl(effectivePlaylistUrl, rawUri)
                        break
                    }
                }
            }

            // 2. If no separate audio rendition, check variant streams: #EXT-X-STREAM-INF
            var firstVariantUri: String? = null
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    for (j in i + 1 until lines.size) {
                        val nextLine = lines[j].trim()
                        if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                            firstVariantUri = resolveRelativeUrl(effectivePlaylistUrl, nextLine)
                            break
                        }
                    }
                    if (firstVariantUri != null) break
                }
                i++
            }

            val hasVideo = lines.any { it.startsWith("#EXT-X-STREAM-INF") }

            return AudioInspectionResult(
                isSupported = true,
                containerType = if (separateAudioUri != null) "HLS_MASTER_WITH_AUDIO_RENDITION" else "HLS_STREAM",
                audioMimeType = "audio/mp4a-latm",
                sampleRate = 48000,
                channelCount = 2,
                bitrate = 128000,
                hasVideoTrack = hasVideo,
                videoMimeType = if (hasVideo) "video/avc" else null,
                hlsAudioOnlyUrl = separateAudioUri ?: firstVariantUri
            )
        } catch (e: Exception) {
            AudioLog.e(TAG, "Error inspecting HLS stream: ${e.message}", e)
            return AudioInspectionResult(
                isSupported = false,
                containerType = "HLS",
                audioMimeType = "",
                errorCode = AudioErrorCode.SOURCE_TIMEOUT,
                errorMessage = "خطأ أثناء فحص HLS: ${e.message}"
            )
        } finally {
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun inspectGenericStream(streamUrl: String, extension: String): AudioInspectionResult {
        return AudioInspectionResult(
            isSupported = true,
            containerType = if (extension.isNotBlank()) extension.uppercase() else "MPEG_TS",
            audioMimeType = "audio/mp4a-latm",
            sampleRate = 48000,
            channelCount = 2,
            bitrate = 128000,
            hasVideoTrack = true,
            videoMimeType = "video/avc"
        )
    }

    fun parseHlsAudioRenditions(playlistContent: String, baseUrl: String): List<String> {
        val audioUrls = mutableListOf<String>()
        val lines = playlistContent.lineSequence()
        for (line in lines) {
            if (line.startsWith("#EXT-X-MEDIA:TYPE=AUDIO")) {
                val uriMatch = Regex("""URI="([^"]+)"""").find(line)
                if (uriMatch != null) {
                    val resolved = resolveRelativeUrl(baseUrl, uriMatch.groupValues[1])
                    audioUrls.add(resolved)
                }
            }
        }
        return audioUrls
    }

    fun parseHlsSegments(playlistContent: String, baseUrl: String): List<String> {
        val segments = mutableListOf<String>()
        val lines = playlistContent.lineSequence()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                segments.add(resolveRelativeUrl(baseUrl, trimmed))
            }
        }
        return segments
    }

    fun resolveRelativeUrl(baseUrl: String, target: String): String {
        if (target.startsWith("http://") || target.startsWith("https://")) return target
        return try {
            val base = URL(baseUrl)
            URL(base, target).toString()
        } catch (_: Exception) {
            target
        }
    }

    fun openHttpConnectionWithRedirects(sourceUrl: String, maxRedirects: Int = 5): HttpURLConnection {
        var currentUrl = sourceUrl
        var redirects = 0

        while (redirects < maxRedirects) {
            val urlObj = URL(currentUrl)
            val connection = (urlObj.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
                instanceFollowRedirects = false
            }

            val statusCode = connection.responseCode
            if (statusCode in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (!location.isNullOrBlank()) {
                    currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else {
                        URL(urlObj, location).toString()
                    }
                    redirects++
                    continue
                }
            }

            return connection
        }
        throw IllegalStateException("تجاوز عدد عمليات إعادة التوجيه المسموح بها ($maxRedirects)")
    }
}
