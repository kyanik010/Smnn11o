package com.idealplayer.app.data.parser

import com.idealplayer.app.core.common.Constants
import com.idealplayer.app.core.common.normalizeRemoteArtworkUrl
import com.idealplayer.app.core.common.rethrowIfCancellation
import com.idealplayer.app.core.database.*
import com.idealplayer.app.core.model.Channel
import com.idealplayer.app.core.model.ContentType
import com.idealplayer.app.core.network.XtreamApi
import com.idealplayer.app.core.network.dto.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamClient @Inject constructor(
    private val api: XtreamApi
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun buildApiUrl(serverUrl: String): String {
        val base = serverUrl.trimEnd('/')
        return "$base${Constants.XTREAM_API_PATH}"
    }

    private fun buildStreamUrl(
        serverUrl: String,
        pathSegment: String,
        username: String,
        password: String,
        streamId: String,
        extension: String? = null
    ): String {
        val base = serverUrl.trimEnd('/')
        val normalizedExtension = extension
            ?.trim()
            ?.trimStart('.')
            ?.takeIf { it.isNotBlank() }
        val normalizedStreamId = streamId.trim()

        if (normalizedStreamId.isHttpStreamUrl()) {
            return normalizedStreamId
        }

        return if (
            normalizedExtension != null &&
            !normalizedStreamId.endsWith(".$normalizedExtension", ignoreCase = true)
        ) {
            "$base/$pathSegment/$username/$password/$normalizedStreamId.$normalizedExtension"
        } else {
            "$base/$pathSegment/$username/$password/$normalizedStreamId"
        }
    }

    suspend fun authenticate(serverUrl: String, username: String, password: String): Result<XtreamAuthResponse> {
        return try {
            val url = buildApiUrl(serverUrl)
            val response = api.authenticate(url, username, password)
            if (response.userInfo?.status?.trim().equals("Active", ignoreCase = true)) {
                Result.success(response)
            } else {
                Result.failure(Exception("Account not active: ${response.userInfo?.status}"))
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logRequestFailure("Xtream authentication", e)
            Result.failure(e)
        }
    }

    suspend fun loadContent(
        serverUrl: String,
        username: String,
        password: String,
        playlistId: Long
    ): XtreamContentResult {
        require(serverUrl.isNotBlank()) { "Xtream server URL is required" }
        require(username.isNotBlank()) { "Xtream username is required" }
        require(password.isNotBlank()) { "Xtream password is required" }

        // Some Xtream panels do not reliably accept catalog actions until player_api.php has
        // authenticated the credentials once. Always validate the exact values entered on the
        // first save instead of letting an unauthenticated empty catalog look like success.
        val authentication = authenticate(serverUrl, username, password).getOrThrow()
        val url = buildApiUrl(serverUrl)
        val channels = mutableListOf<ChannelEntity>()
        val movies = mutableListOf<MovieEntity>()
        val series = mutableListOf<SeriesEntity>()
        val categories = mutableListOf<CategoryEntity>()
        val loadedSections = mutableSetOf(XtreamContentSection.LIVE)
        val liveExtension = preferredLiveExtension(
            authentication.userInfo?.allowedOutputFormats.orEmpty()
        )

        // Live TV is the required Xtream surface. VOD and series are optional: a number of
        // live-only panels explicitly reject those actions. Only known "unsupported action"
        // HTTP responses are treated as absent; transport and server failures still abort the
        // refresh so a transient error cannot replace a persisted catalog with a partial one.
        val (liveCategories, liveStreams) = coroutineScope {
            val categoriesRequest = async {
                api.getLiveCategories(url, username, password)
            }
            val streamsRequest = async {
                api.getLiveStreams(url, username, password)
            }
            categoriesRequest.await() to streamsRequest.await()
        }
        categories.addAll(liveCategories.map {
            CategoryEntity(
                playlistId = playlistId,
                categoryId = it.categoryId.toIntOrNull() ?: 0,
                name = it.categoryName,
                contentType = ContentType.LIVE.name,
                parentId = it.parentId
            )
        })
        val liveCategoryNames = liveCategories.associate { it.categoryId to it.categoryName }

        channels.addAll(liveStreams.mapIndexed { index, stream ->
            val categoryName = liveCategoryNames[stream.categoryId].orEmpty()
            ChannelEntity(
                playlistId = playlistId,
                streamId = stream.streamId,
                name = stream.name,
                logoUrl = normalizeRemoteArtworkUrl(serverUrl, stream.streamIcon),
                groupTitle = categoryName,
                streamUrl = buildStreamUrl(
                    serverUrl,
                    "live",
                    username,
                    password,
                    stream.streamId.toString(),
                    liveExtension
                ),
                epgChannelId = stream.epgChannelId ?: "",
                catchupSource = if (stream.tvArchive > 0) "xtream" else "",
                sortOrder = index
            )
        })

        val vodCategories = loadOptionalCatalogSection("VOD categories") {
            api.getVodCategories(url, username, password)
        }
        val vodStreams = vodCategories?.let {
            loadOptionalCatalogSection("VOD streams") {
                api.getVodStreams(url, username, password)
            }
        }
        if (vodCategories != null && vodStreams != null) {
            val vodCategoryNames = vodCategories.associate { it.categoryId to it.categoryName }
            categories.addAll(vodCategories.map {
                CategoryEntity(
                    playlistId = playlistId,
                    categoryId = it.categoryId.toIntOrNull() ?: 0,
                    name = it.categoryName,
                    contentType = ContentType.MOVIE.name,
                    parentId = it.parentId
                )
            })
            movies.addAll(vodStreams.mapIndexed { index, stream ->
                val categoryName = vodCategoryNames[stream.categoryId].orEmpty()
                MovieEntity(
                    playlistId = playlistId,
                    streamId = stream.streamId,
                    name = stream.name,
                    posterUrl = normalizeRemoteArtworkUrl(serverUrl, stream.streamIcon),
                    streamUrl = buildStreamUrl(
                        serverUrl = serverUrl,
                        pathSegment = "movie",
                        username = username,
                        password = password,
                        streamId = stream.streamId.toString(),
                        extension = stream.containerExtension
                    ),
                    genre = stream.genre,
                    plot = stream.plot,
                    cast = stream.cast,
                    director = stream.director,
                    releaseDate = stream.releaseDate,
                    year = stream.year.toIntOrNull() ?: 0,
                    rating = stream.rating.toDoubleOrNull() ?: (stream.rating5based * 2),
                    containerExtension = stream.containerExtension,
                    categoryId = stream.categoryId.toIntOrNull() ?: 0,
                    categoryName = categoryName,
                    tmdbId = stream.tmdbId?.toIntOrNull() ?: 0,
                    duration = stream.episodeRunTime.toIntOrNull() ?: 0,
                    sourceOrder = index
                )
            })
            loadedSections += XtreamContentSection.MOVIES
        }

        val seriesCategories = loadOptionalCatalogSection("Series categories") {
            api.getSeriesCategories(url, username, password)
        }
        val seriesStreams = seriesCategories?.let {
            loadOptionalCatalogSection("Series streams") {
                api.getSeriesStreams(url, username, password)
            }
        }
        if (seriesCategories != null && seriesStreams != null) {
            val seriesCategoryNames = seriesCategories.associate { it.categoryId to it.categoryName }
            categories.addAll(seriesCategories.map {
                CategoryEntity(
                    playlistId = playlistId,
                    categoryId = it.categoryId.toIntOrNull() ?: 0,
                    name = it.categoryName,
                    contentType = ContentType.SERIES.name,
                    parentId = it.parentId
                )
            })
            series.addAll(seriesStreams.mapIndexed { index, stream ->
                val categoryName = seriesCategoryNames[stream.categoryId].orEmpty()
                SeriesEntity(
                    playlistId = playlistId,
                    seriesId = stream.seriesId,
                    name = stream.name,
                    posterUrl = normalizeRemoteArtworkUrl(serverUrl, stream.cover),
                    backdropUrl = normalizeRemoteArtworkUrl(serverUrl, stream.backdropPath?.firstOrNull().orEmpty()),
                    genre = stream.genre,
                    plot = stream.plot,
                    cast = stream.cast,
                    director = stream.director,
                    releaseDate = stream.releaseDate,
                    year = stream.year.toIntOrNull() ?: 0,
                    rating = stream.rating.toDoubleOrNull() ?: (stream.rating5based * 2),
                    categoryId = stream.categoryId.toIntOrNull() ?: 0,
                    categoryName = categoryName,
                    tmdbId = stream.tmdbId?.toIntOrNull() ?: 0,
                    sourceOrder = index
                )
            })
            loadedSections += XtreamContentSection.SERIES
        }

        return XtreamContentResult(
            channels = channels,
            movies = movies,
            series = series,
            categories = categories,
            loadedSections = loadedSections
        )
    }

    private fun preferredLiveExtension(allowedOutputFormats: List<String>): String {
        val normalized = allowedOutputFormats
            .map { it.trim().trimStart('.').lowercase() }
            .filter(String::isNotBlank)

        return when {
            "m3u8" in normalized -> "m3u8"
            "ts" in normalized -> "ts"
            else -> "m3u8"
        }
    }

    private suspend fun <T> loadOptionalCatalogSection(
        operation: String,
        request: suspend () -> T
    ): T? = try {
        request()
    } catch (error: HttpException) {
        if (error.code() in OPTIONAL_CATALOG_UNSUPPORTED_HTTP_CODES) {
            Timber.i("Xtream %s is unavailable (HTTP %d)", operation, error.code())
            null
        } else {
            throw error
        }
    }

    suspend fun loadSeriesEpisodes(
        serverUrl: String,
        username: String,
        password: String,
        seriesId: Int,
        dbSeriesId: Long
    ): List<EpisodeEntity> {
        return try {
            val url = buildApiUrl(serverUrl)
            val info = api.getSeriesInfo(url, username, password, seriesId = seriesId)
            val episodes = mutableListOf<EpisodeEntity>()
            parseSeriesEpisodes(info.episodes).forEach { (seasonKey, episodeList) ->
                val seasonNum = seasonKey.toIntOrNull()
                    ?: Regex("""\d+""").find(seasonKey)?.value?.toIntOrNull()
                    ?: 1
                episodeList.forEachIndexed { index, episode ->
                    val streamId = episode.id.ifBlank { episode.episodeNum.toString() }
                    val episodeNumber = episode.episodeNum.takeIf { it > 0 } ?: (index + 1)
                    val streamUrl = episode.directSource
                        .trim()
                        .takeIf { it.isHttpStreamUrl() }
                        ?: buildStreamUrl(
                            serverUrl = serverUrl,
                            pathSegment = "series",
                            username = username,
                            password = password,
                            streamId = streamId,
                            extension = episode.containerExtension
                        )
                    episodes.add(
                        EpisodeEntity(
                            seriesId = dbSeriesId,
                            seasonNumber = seasonNum,
                            episodeNumber = episodeNumber,
                            name = episode.title.ifBlank { "Episode $episodeNumber" },
                            plot = episode.info?.plot ?: "",
                            posterUrl = episode.info?.movieImage ?: "",
                            streamUrl = streamUrl,
                            duration = episode.info?.durationSecs ?: 0,
                            rating = episode.info?.rating ?: 0.0,
                            containerExtension = episode.containerExtension
                        )
                    )
                }
            }
            episodes
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            logRequestFailure("Series episode loading", e)
            emptyList()
        }
    }

    suspend fun loadVodMetadata(
        serverUrl: String,
        username: String,
        password: String,
        vodId: Int
    ): XtreamVodMetadata? {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank() || vodId <= 0) return null
        return try {
            parseXtreamVodMetadata(
                element = api.getVodInfo(buildApiUrl(serverUrl), username, password, vodId = vodId),
                serverUrl = serverUrl
            )
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            logRequestFailure("VOD metadata loading for stream $vodId", error)
            null
        }
    }

    suspend fun loadSeriesMetadata(
        serverUrl: String,
        username: String,
        password: String,
        seriesId: Int
    ): XtreamVodMetadata? {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank() || seriesId <= 0) return null
        return try {
            val detail = api.getSeriesInfo(
                buildApiUrl(serverUrl),
                username,
                password,
                seriesId = seriesId
            ).info ?: return null
            XtreamVodMetadata(
                posterUrl = normalizeRemoteArtworkUrl(serverUrl, detail.cover),
                backdropUrl = normalizeRemoteArtworkUrl(
                    serverUrl,
                    detail.backdropPath?.firstOrNull().orEmpty()
                ),
                plot = detail.plot,
                cast = detail.cast,
                director = detail.director,
                genre = detail.genre,
                releaseDate = detail.releaseDate,
                duration = parseDurationMinutes(detail.episodeRunTime),
                rating = detail.rating.toDoubleOrNull() ?: 0.0,
                tmdbId = detail.tmdbId?.toIntOrNull() ?: 0
            ).takeIf { metadata ->
                metadata.posterUrl.isNotBlank() ||
                    metadata.backdropUrl.isNotBlank() ||
                    metadata.plot.isNotBlank() ||
                    metadata.tmdbId > 0
            }
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            logRequestFailure("Series metadata loading for series $seriesId", error)
            null
        }
    }

    suspend fun loadLiveEpgPrograms(
        serverUrl: String,
        username: String,
        password: String,
        channels: List<Channel>
    ): List<EpgProgramEntity> {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            return emptyList()
        }

        val url = buildApiUrl(serverUrl)
        val programs = mutableListOf<EpgProgramEntity>()
        channels
            .asSequence()
            .filter { it.streamId > 0 }
            .distinctBy { it.streamId }
            .forEach { channel ->
                programs += loadLiveEpgProgramsForChannel(url, username, password, channel)
            }
        return programs
    }

    private suspend fun loadLiveEpgProgramsForChannel(
        url: String,
        username: String,
        password: String,
        channel: Channel
    ): List<EpgProgramEntity> {
        val shortEpg = runCatching {
            api.getLiveEpg(
                url = url,
                username = username,
                password = password,
                action = "get_short_epg",
                streamId = channel.streamId,
                limit = 6
            )
        }
            .map { response -> parseXtreamEpgPrograms(response, channel) }
            .getOrElse { error ->
                error.rethrowIfCancellation()
                logRequestFailure("Short EPG loading for stream ${channel.streamId}", error)
                emptyList()
            }

        if (shortEpg.isNotEmpty()) return shortEpg

        return runCatching {
            api.getLiveEpg(
                url = url,
                username = username,
                password = password,
                action = "get_simple_data_table",
                streamId = channel.streamId
            )
        }
            .map { response -> parseXtreamEpgPrograms(response, channel) }
            .getOrElse { error ->
                error.rethrowIfCancellation()
                logRequestFailure("Simple EPG loading for stream ${channel.streamId}", error)
                emptyList()
            }
    }

    private fun parseSeriesEpisodes(element: JsonElement?): List<Pair<String, List<XtreamEpisode>>> {
        return when (element) {
            is JsonObject -> element.mapNotNull { (seasonKey, seasonValue) ->
                val episodes = decodeEpisodeCollection(seasonValue)
                if (episodes.isEmpty()) null else seasonKey to episodes
            }
            is JsonArray -> {
                val episodes = decodeEpisodeCollection(element)
                if (episodes.isEmpty()) emptyList() else listOf("1" to episodes)
            }
            else -> emptyList()
        }
    }

    private fun decodeEpisodeCollection(element: JsonElement): List<XtreamEpisode> {
        return when (element) {
            is JsonArray -> element.mapNotNull(::decodeEpisode)
            is JsonObject -> {
                decodeEpisode(element)?.let { listOf(it) }
                    ?: element.values.mapNotNull(::decodeEpisode)
            }
            else -> emptyList()
        }
    }

    private fun decodeEpisode(element: JsonElement): XtreamEpisode? {
        val decodedEpisode = runCatching {
            json.decodeFromJsonElement<XtreamEpisode>(element)
        }.getOrNull()
        val flexibleEpisode = (element as? JsonObject)?.toFlexibleXtreamEpisode()
        val episode = when {
            decodedEpisode == null -> flexibleEpisode
            flexibleEpisode == null -> decodedEpisode
            else -> decodedEpisode.withFallback(flexibleEpisode)
        }

        return episode?.takeIf {
            it.id.isNotBlank() ||
                it.episodeNum > 0 ||
                it.directSource.isNotBlank()
        }
    }

    private fun XtreamEpisode.withFallback(fallback: XtreamEpisode): XtreamEpisode = copy(
        id = id.ifBlank { fallback.id },
        episodeNum = episodeNum.takeIf { it > 0 } ?: fallback.episodeNum,
        title = title.ifBlank { fallback.title },
        containerExtension = containerExtension.ifBlank { fallback.containerExtension },
        directSource = directSource.ifBlank { fallback.directSource },
        info = info ?: fallback.info
    )

    private fun JsonObject.toFlexibleXtreamEpisode(): XtreamEpisode {
        val infoObject = this["info"] as? JsonObject
        return XtreamEpisode(
            id = textValue("id", "stream_id"),
            episodeNum = intValue("episode_num", "episode", "episode_number"),
            title = textValue("title", "name"),
            containerExtension = textValue("container_extension", "extension"),
            directSource = textValue("direct_source", "stream_url"),
            info = infoObject?.let {
                XtreamEpisodeInfo(
                    plot = it.textValue("plot", "description"),
                    durationSecs = it.intValue("duration_secs", "duration_seconds"),
                    duration = it.textValue("duration"),
                    movieImage = it.textValue("movie_image", "cover", "poster"),
                    rating = it.doubleValue("rating")
                )
            }
        )
    }

    private fun JsonObject.textValue(vararg keys: String): String {
        keys.forEach { key ->
            val value = runCatching { (this[key] as? JsonPrimitive)?.content }
                .getOrNull()
                ?.trim()
            if (!value.isNullOrBlank() && !value.equals("null", ignoreCase = true)) {
                return value
            }
        }
        return ""
    }

    private fun JsonObject.intValue(vararg keys: String): Int =
        textValue(*keys).toIntOrNull() ?: 0

    private fun JsonObject.doubleValue(vararg keys: String): Double =
        textValue(*keys).toDoubleOrNull() ?: 0.0

    private fun String.isHttpStreamUrl(): Boolean =
        startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)

    private fun logRequestFailure(operation: String, error: Throwable) {
        // Retrofit exceptions may retain a request URL containing Xtream credentials.
        Timber.w("%s failed: %s", operation, error.javaClass.simpleName)
    }

    private companion object {
        // These codes are commonly returned by live-only panels for an unknown Xtream action.
        // Do not include auth, rate-limit, or 5xx failures: those need to preserve the snapshot
        // and surface a refresh error to the caller.
        val OPTIONAL_CATALOG_UNSUPPORTED_HTTP_CODES = setOf(400, 404, 405, 501)
    }
}

enum class XtreamContentSection {
    LIVE,
    MOVIES,
    SERIES
}

data class XtreamContentResult(
    val channels: List<ChannelEntity>,
    val movies: List<MovieEntity>,
    val series: List<SeriesEntity>,
    val categories: List<CategoryEntity>,
    /** Sections that were fetched successfully and may replace their persisted snapshot. */
    val loadedSections: Set<XtreamContentSection> = XtreamContentSection.entries.toSet()
)

data class XtreamVodMetadata(
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val plot: String = "",
    val cast: String = "",
    val director: String = "",
    val genre: String = "",
    val releaseDate: String = "",
    val duration: Int = 0,
    val rating: Double = 0.0,
    val tmdbId: Int = 0
)

internal fun parseXtreamVodMetadata(element: JsonElement, serverUrl: String): XtreamVodMetadata? {
    val root = element as? JsonObject ?: return null
    val info = root["info"] as? JsonObject ?: JsonObject(emptyMap())
    val movieData = root["movie_data"] as? JsonObject ?: JsonObject(emptyMap())

    fun JsonObject.firstText(vararg keys: String): String {
        keys.forEach { key ->
            val candidate = when (val value = this[key]) {
                is JsonPrimitive -> value.content.trim()
                is JsonArray -> value.firstOrNull()?.let { (it as? JsonPrimitive)?.content?.trim() }.orEmpty()
                else -> ""
            }
            if (candidate.isNotBlank() && !candidate.equals("null", ignoreCase = true)) return candidate
        }
        return ""
    }

    fun value(vararg keys: String): String = info.firstText(*keys)
        .ifBlank { movieData.firstText(*keys) }
        .ifBlank { root.firstText(*keys) }

    val metadata = XtreamVodMetadata(
        posterUrl = normalizeRemoteArtworkUrl(
            serverUrl,
            value("movie_image", "cover_big", "cover", "stream_icon", "poster", "poster_path")
        ),
        backdropUrl = normalizeRemoteArtworkUrl(
            serverUrl,
            value("backdrop_path", "backdrop", "background")
        ),
        plot = value("plot", "description"),
        cast = value("cast", "actors"),
        director = value("director"),
        genre = value("genre"),
        releaseDate = value("releasedate", "release_date", "date"),
        duration = value("duration_secs", "duration_seconds").toLongOrNull()
            ?.let { seconds -> (seconds / 60L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
            ?: parseDurationMinutes(value("duration", "episode_run_time")),
        rating = value("rating", "rating_5based").toDoubleOrNull() ?: 0.0,
        tmdbId = value("tmdb_id", "tmdb").toIntOrNull() ?: 0
    )
    return metadata.takeIf {
        it.posterUrl.isNotBlank() || it.backdropUrl.isNotBlank() || it.plot.isNotBlank() || it.tmdbId > 0
    }
}

private fun parseDurationMinutes(value: String): Int {
    val parts = value.split(':').mapNotNull(String::toIntOrNull)
    return when (parts.size) {
        3 -> parts[0] * 60 + parts[1]
        2 -> parts[0] * 60 + parts[1]
        1 -> parts[0]
        else -> 0
    }
}
