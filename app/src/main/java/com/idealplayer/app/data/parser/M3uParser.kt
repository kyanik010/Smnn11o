package com.idealplayer.app.data.parser

import com.idealplayer.app.core.database.ChannelEntity
import com.idealplayer.app.core.database.MovieEntity
import com.idealplayer.app.core.database.SeriesEntity
import com.idealplayer.app.core.common.rethrowIfCancellation
import com.idealplayer.app.core.model.ContentType
import com.idealplayer.app.core.player.parsePlaybackSource
import com.idealplayer.app.core.player.withPlaybackHeaders
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

data class M3uEntry(
    val name: String,
    val logoUrl: String = "",
    val groupTitle: String = "",
    val tvgId: String = "",
    val tvgName: String = "",
    val url: String,
    val contentType: ContentType = ContentType.LIVE,
    val tvgLanguage: String = "",
    val catchupSource: String = ""
)

@Singleton
class M3uParser @Inject constructor() {
    private val epgUrlAttributeKeys = listOf(
        "x-tvg-url",
        "url-tvg",
        "tvg-url",
        "epg-url",
        "epg"
    )

    fun parse(input: InputStream, playlistId: Long): M3uParseResult {
        val entries = mutableListOf<M3uEntry>()
        val reader = BufferedReader(InputStreamReader(input))
        var currentLine: String?
        var extinf: String? = null
        var epgUrls = emptyList<String>()
        var playbackHeaders = linkedMapOf<String, String>()

        try {
            while (reader.readLine().also { currentLine = it } != null) {
                val line = currentLine?.trim() ?: continue
                when {
                    line.startsWith("#EXTM3U") -> {
                        epgUrls = parseHeaderEpgUrls(line)
                    }
                    line.startsWith("#EXTINF:") -> {
                        extinf = line
                        playbackHeaders.putAll(parseEntryPlaybackHeaders(line))
                    }
                    line.startsWith("#") -> {
                        parsePlaybackOptions(line).forEach { (name, value) ->
                            playbackHeaders[name] = value
                        }
                        val match = Regex("^#(?:x-tvg-url|url-tvg|tvg-url|epg-url|epg)\\s*[:=]\\s*(.+)$", RegexOption.IGNORE_CASE)
                            .find(line)
                        if (match != null) {
                            val urls = match.groupValues[1].splitEpgUrls()
                            if (urls.isNotEmpty()) {
                                epgUrls = (epgUrls + urls).distinct()
                            }
                        }
                    }
                    line.isNotEmpty() && extinf != null -> {
                        val streamUrl = withPlaybackHeaders(line, playbackHeaders)
                        val entry = parseEntry(extinf, streamUrl)
                        if (entry != null) entries.add(entry)
                        extinf = null
                        playbackHeaders.clear()
                    }
                    line.isNotEmpty() && extinf == null -> {
                        val streamUrl = withPlaybackHeaders(line, playbackHeaders)
                        entries.add(M3uEntry(name = line.substringAfterLast("/"), url = streamUrl))
                        playbackHeaders.clear()
                    }
                }
            }
        } catch (e: IOException) {
            // Returning entries accumulated up to this point would make a caller mistake a
            // truncated source for an authoritative playlist and replace valid stored content
            // with only that partial result. Individual malformed EXTINF rows are still handled
            // inside parseEntry, so only stream/transport failures abort the import.
            e.rethrowIfCancellation()
            throw e
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e("M3U parsing failed: %s", e.javaClass.simpleName)
            // The only intentionally recoverable parse failures are handled per-entry in
            // parseEntry. Anything escaping the main loop means the input is not a complete,
            // trustworthy snapshot and must not be persisted as a partial playlist.
            throw e
        }

        return categorize(entries, playlistId, epgUrls)
    }

    fun parseText(text: String, playlistId: Long): M3uParseResult {
        return parse(text.byteInputStream(), playlistId)
    }

    private fun parseEntry(extinf: String, url: String): M3uEntry? {
        try {
            val attributes = parseAttributes(extinf)
            val name = extinf.substringAfterLast(",").trim()
            val groupTitle = attributes["group-title"] ?: ""
            val tvgId = attributes["tvg-id"] ?: ""
            val tvgName = attributes["tvg-name"] ?: ""
            val logo = attributes["tvg-logo"] ?: ""
            val catchup = attributes["catchup-source"] ?: ""

            val contentType = detectContentType(url, groupTitle)

            return M3uEntry(
                name = name,
                logoUrl = logo,
                groupTitle = groupTitle,
                tvgId = tvgId,
                tvgName = tvgName,
                url = url,
                contentType = contentType,
                catchupSource = catchup
            )
        } catch (e: Exception) {
            // EXTINF attributes can carry provider-specific request headers. Do not emit the
            // raw line to debug logs.
            Timber.w("Failed to parse EXTINF metadata: %s", e.javaClass.simpleName)
            return null
        }
    }

    private fun parseAttributes(extinf: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        val regex = Regex("""([\w-]+)\s*=\s*(?:"([^"]*?)"|'([^']*?)'|([^\s]+))""")
        regex.findAll(extinf).forEach { match ->
            attrs[match.groupValues[1].lowercase()] = match.groupValues
                .drop(2)
                .firstOrNull(String::isNotBlank)
                .orEmpty()
        }
        return attrs
    }

    private fun parseEntryPlaybackHeaders(extinf: String): Map<String, String> {
        val attributes = parseAttributes(extinf)
        return buildMap {
            attributes["http-user-agent"]
                .orEmpty()
                .ifBlank { attributes["user-agent"].orEmpty() }
                .takeIf(String::isNotBlank)
                ?.let { put("User-Agent", it) }
            attributes["http-referrer"]
                .orEmpty()
                .ifBlank { attributes["referer"].orEmpty() }
                .ifBlank { attributes["referrer"].orEmpty() }
                .takeIf(String::isNotBlank)
                ?.let { put("Referer", it) }
            attributes["http-origin"]
                .orEmpty()
                .ifBlank { attributes["origin"].orEmpty() }
                .takeIf(String::isNotBlank)
                ?.let { put("Origin", it) }
        }
    }

    private fun parsePlaybackOptions(line: String): Map<String, String> {
        val vlcMatch = Regex(
            pattern = "^#EXTVLCOPT:([^=]+)=(.*)$",
            option = RegexOption.IGNORE_CASE
        ).find(line)
        if (vlcMatch != null) {
            val value = vlcMatch.groupValues[2].trim().takeIf(String::isNotBlank) ?: return emptyMap()
            val headerName = when (vlcMatch.groupValues[1].trim().lowercase()) {
                "http-user-agent" -> "User-Agent"
                "http-referrer", "http-referer" -> "Referer"
                "http-origin" -> "Origin"
                "http-cookie", "http-cookies" -> "Cookie"
                else -> return emptyMap()
            }
            return mapOf(headerName to value)
        }

        val kodiPrefix = "#KODIPROP:inputstream.adaptive.stream_headers="
        if (line.startsWith(kodiPrefix, ignoreCase = true)) {
            val payload = line.substring(kodiPrefix.length)
            return parsePlaybackSource("https://header.invalid|$payload").headers
        }

        if (line.startsWith("#EXTHTTP:", ignoreCase = true)) {
            val payload = line.substringAfter(':').trim()
            return runCatching {
                Json.parseToJsonElement(payload).jsonObject
                    .mapValues { (_, value) -> value.jsonPrimitive.content }
            }.getOrDefault(emptyMap())
        }

        return emptyMap()
    }

    private fun parseHeaderEpgUrls(header: String): List<String> {
        val attributes = parseAttributes(header)
        return epgUrlAttributeKeys
            .flatMap { key -> attributes[key].orEmpty().splitEpgUrls() }
            .distinct()
    }

    private fun String.splitEpgUrls(): List<String> =
        split(',', ';')
            .map(String::trim)
            .filter(String::isNotBlank)

    private fun detectContentType(url: String, group: String): ContentType {
        val sourceUrl = parsePlaybackSource(url).url
        val lowerUrl = sourceUrl.lowercase()
        val pathExtension = sourceUrl.toHttpUrlOrNull()
            ?.encodedPath
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            .orEmpty()
        val lowerGroup = group.lowercase()
        return when {
            lowerUrl.contains("/series/") || lowerGroup.contains("series") || lowerGroup.contains("dizi") -> ContentType.SERIES
            lowerUrl.contains("/movie/") ||
                lowerGroup.contains("movie") ||
                lowerGroup.contains("film") ||
                pathExtension in VOD_FILE_EXTENSIONS -> ContentType.MOVIE
            else -> ContentType.LIVE
        }
    }

    private fun categorize(entries: List<M3uEntry>, playlistId: Long, epgUrls: List<String>): M3uParseResult {
        val channels = mutableListOf<ChannelEntity>()
        val movies = mutableListOf<MovieEntity>()
        val series = mutableMapOf<String, MutableList<M3uEntry>>()
        val seriesSourceOrder = mutableMapOf<String, Int>()
        val seriesCategory = mutableMapOf<String, String>()

        entries.forEachIndexed { index, entry ->
            when (entry.contentType) {
                ContentType.LIVE -> channels.add(
                    ChannelEntity(
                        playlistId = playlistId,
                        streamId = extractXtreamStreamId(entry.url),
                        name = entry.name,
                        logoUrl = entry.logoUrl,
                        groupTitle = entry.groupTitle,
                        streamUrl = entry.url,
                        epgChannelId = entry.tvgId,
                        catchupSource = entry.catchupSource,
                        sortOrder = index
                    )
                )
                ContentType.MOVIE -> movies.add(
                    MovieEntity(
                        playlistId = playlistId,
                        streamId = extractXtreamStreamId(entry.url),
                        name = entry.name,
                        posterUrl = entry.logoUrl,
                        streamUrl = entry.url,
                        categoryName = entry.groupTitle,
                        genre = entry.groupTitle,
                        sourceOrder = index
                    )
                )
                ContentType.SERIES -> {
                    val seriesKey = extractSeriesName(entry.name)
                    seriesSourceOrder.putIfAbsent(seriesKey, index)
                    seriesCategory.putIfAbsent(seriesKey, entry.groupTitle)
                    series.getOrPut(seriesKey) { mutableListOf() }.add(entry)
                }
            }
        }

        val seriesEntities = series.map { (name, _) ->
            SeriesEntity(
                playlistId = playlistId,
                name = name,
                categoryName = seriesCategory[name] ?: "",
                sourceOrder = seriesSourceOrder[name] ?: 0
            )
        }

        return M3uParseResult(
            channels = channels,
            movies = movies,
            series = seriesEntities,
            seriesEpisodes = series,
            epgUrls = epgUrls
        )
    }

    private fun extractSeriesName(name: String): String {
        val patterns = listOf(
            Regex("""^(.+?)\s*[Ss]\d+\s*[Ee]\d+"""),
            Regex("""^(.+?)\s*Season\s*\d+""", RegexOption.IGNORE_CASE),
            Regex("""^(.+?)\s*\d+x\d+""")
        )
        for (pattern in patterns) {
            val match = pattern.find(name)
            if (match != null) return match.groupValues[1].trim()
        }
        return name.trim()
    }

    private fun extractXtreamStreamId(url: String): Int {
        val sourceUrl = parsePlaybackSource(url).url
        sourceUrl.toHttpUrlOrNull()?.let { httpUrl ->
            listOf("stream_id", "streamId", "stream").forEach { key ->
                httpUrl.queryParameter(key)?.toIntOrNull()?.let { return it }
            }
        }

        return Regex(
            pattern = """/(?:live|movie|series)/[^/?#]+/[^/?#]+/(\d+)(?:\.[^/?#]+)?(?:[?#].*)?$""",
            option = RegexOption.IGNORE_CASE
        )
            .find(sourceUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }

    private companion object {
        val VOD_FILE_EXTENSIONS = setOf("mp4", "m4v", "mov", "mkv", "webm", "ogv")
    }
}

data class M3uParseResult(
    val channels: List<ChannelEntity>,
    val movies: List<MovieEntity>,
    val series: List<SeriesEntity>,
    val seriesEpisodes: Map<String, List<M3uEntry>>,
    val epgUrls: List<String> = emptyList()
) {
    val epgUrl: String
        get() = epgUrls.firstOrNull().orEmpty()
}
