package com.idealplayer.app.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class XtreamAuthResponse(
    @SerialName("user_info") val userInfo: XtreamUserInfo? = null,
    @SerialName("server_info") val serverInfo: XtreamServerInfo? = null
)

@Serializable
data class XtreamUserInfo(
    @SerialName("username") val username: String = "",
    @SerialName("password") val password: String = "",
    @SerialName("status") val status: String = "",
    @SerialName("exp_date") val expDate: String? = null,
    @SerialName("is_trial") val isTrial: String = "0",
    @SerialName("active_cons") val activeCons: String = "0",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("max_connections") val maxConnections: String = "1",
    @SerialName("allowed_output_formats") val allowedOutputFormats: List<String> = emptyList()
)

@Serializable
data class XtreamServerInfo(
    @SerialName("url") val url: String = "",
    @SerialName("port") val port: String = "",
    @SerialName("https_port") val httpsPort: String = "",
    @SerialName("server_protocol") val serverProtocol: String = "http",
    @SerialName("timestamp_now") val timestampNow: Long = 0,
    @SerialName("time_now") val timeNow: String = ""
)

@Serializable
data class XtreamCategory(
    @SerialName("category_id") val categoryId: String = "",
    @SerialName("category_name") val categoryName: String = "",
    @SerialName("parent_id") val parentId: Int = 0
)

@Serializable
data class XtreamLiveStream(
    @SerialName("num") val num: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("stream_type") val streamType: String = "",
    @SerialName("stream_id") val streamId: Int = 0,
    @SerialName("stream_icon") val streamIcon: String = "",
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    @SerialName("category_id") val categoryId: String = "",
    @SerialName("category_ids") val categoryIds: List<Int>? = null,
    @SerialName("tv_archive") val tvArchive: Int = 0,
    @SerialName("tv_archive_duration") val tvArchiveDuration: String? = null
)

@Serializable
data class XtreamVodStream(
    @SerialName("num") val num: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("stream_type") val streamType: String = "",
    @SerialName("stream_id") val streamId: Int = 0,
    @SerialName("stream_icon") val streamIcon: String = "",
    @SerialName("rating") val rating: String = "",
    @SerialName("rating_5based") val rating5based: Double = 0.0,
    @SerialName("category_id") val categoryId: String = "",
    @SerialName("container_extension") val containerExtension: String = "",
    @SerialName("plot") val plot: String = "",
    @SerialName("cast") val cast: String = "",
    @SerialName("director") val director: String = "",
    @SerialName("genre") val genre: String = "",
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("episode_run_time") val episodeRunTime: String = "",
    @SerialName("year") val year: String = "",
    @SerialName("tmdb_id") val tmdbId: String? = null
)

@Serializable
data class XtreamSeriesStream(
    @SerialName("num") val num: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("series_id") val seriesId: Int = 0,
    @SerialName("cover") val cover: String = "",
    @SerialName("plot") val plot: String = "",
    @SerialName("cast") val cast: String = "",
    @SerialName("director") val director: String = "",
    @SerialName("genre") val genre: String = "",
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("rating") val rating: String = "",
    @SerialName("rating_5based") val rating5based: Double = 0.0,
    @SerialName("backdrop_path") val backdropPath: List<String>? = null,
    @SerialName("youtube_trailer") val youtubeTrailer: String = "",
    @SerialName("category_id") val categoryId: String = "",
    @SerialName("tmdb_id") val tmdbId: String? = null,
    @SerialName("episode_run_time") val episodeRunTime: String = "",
    @SerialName("year") val year: String = "",
    @SerialName("last_modified") val lastModified: String? = null
)

@Serializable
data class XtreamSeriesInfo(
    @SerialName("info") val info: XtreamSeriesDetail? = null,
    @SerialName("episodes") val episodes: JsonElement? = null,
    @SerialName("seasons") val seasons: List<XtreamSeason>? = null
)

@Serializable
data class XtreamSeriesDetail(
    @SerialName("name") val name: String = "",
    @SerialName("cover") val cover: String = "",
    @SerialName("plot") val plot: String = "",
    @SerialName("cast") val cast: String = "",
    @SerialName("director") val director: String = "",
    @SerialName("genre") val genre: String = "",
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("rating") val rating: String = "",
    @SerialName("backdrop_path") val backdropPath: List<String>? = null,
    @SerialName("tmdb_id") val tmdbId: String? = null,
    @SerialName("episode_run_time") val episodeRunTime: String = ""
)

@Serializable
data class XtreamSeason(
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("cover") val cover: String = "",
    @SerialName("episode_count") val episodeCount: String? = null
)

@Serializable
data class XtreamEpisode(
    @SerialName("id") val id: String = "",
    @SerialName("episode_num") val episodeNum: Int = 0,
    @SerialName("title") val title: String = "",
    @SerialName("container_extension") val containerExtension: String = "",
    @SerialName("direct_source") val directSource: String = "",
    @SerialName("info") val info: XtreamEpisodeInfo? = null
)

@Serializable
data class XtreamEpisodeInfo(
    @SerialName("plot") val plot: String = "",
    @SerialName("duration_secs") val durationSecs: Int = 0,
    @SerialName("duration") val duration: String = "",
    @SerialName("movie_image") val movieImage: String = "",
    @SerialName("rating") val rating: Double = 0.0
)

// TMDB DTOs
@Serializable
data class TmdbSearchResponse(
    @SerialName("results") val results: List<TmdbSearchResult> = emptyList(),
    @SerialName("total_results") val totalResults: Int = 0
)

@Serializable
data class TmdbSearchResult(
    @SerialName("id") val id: Int = 0,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("overview") val overview: String = "",
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("popularity") val popularity: Double = 0.0,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("media_type") val mediaType: String = ""
)

@Serializable
data class TmdbDetailResponse(
    @SerialName("id") val id: Int = 0,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("tagline") val tagline: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("overview") val overview: String = "",
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("runtime") val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int>? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("genres") val genres: List<TmdbGenre> = emptyList(),
    @SerialName("credits") val credits: TmdbCredits? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("external_ids") val externalIds: TmdbExternalIds? = null,
    @SerialName("translations") val translations: TmdbTranslations? = null,
    @SerialName("videos") val videos: TmdbVideos? = null,
    @SerialName("created_by") val createdBy: List<TmdbCreator>? = null
)

@Serializable
data class TmdbGenre(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = ""
)

@Serializable
data class TmdbCredits(
    @SerialName("cast") val cast: List<TmdbCast> = emptyList(),
    @SerialName("crew") val crew: List<TmdbCrew> = emptyList()
)

@Serializable
data class TmdbCast(
    @SerialName("name") val name: String = "",
    @SerialName("character") val character: String = "",
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("order") val order: Int = 0
)

@Serializable
data class TmdbCrew(
    @SerialName("name") val name: String = "",
    @SerialName("job") val job: String = ""
)

@Serializable
data class TmdbExternalIds(
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("tvdb_id") val tvdbId: Int? = null
)

@Serializable
data class TmdbTranslations(
    @SerialName("translations") val translations: List<TmdbTranslation> = emptyList()
)

@Serializable
data class TmdbTranslation(
    @SerialName("iso_3166_1") val country: String = "",
    @SerialName("iso_639_1") val language: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("english_name") val englishName: String = "",
    @SerialName("data") val data: TmdbTranslationData? = null
)

@Serializable
data class TmdbTranslationData(
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("overview") val overview: String = "",
    @SerialName("tagline") val tagline: String = ""
)

@Serializable
data class TmdbVideos(
    @SerialName("results") val results: List<TmdbVideo> = emptyList()
)

@Serializable
data class TmdbVideo(
    @SerialName("key") val key: String = "",
    @SerialName("site") val site: String = "",
    @SerialName("type") val type: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("official") val official: Boolean = false
)

@Serializable
data class TmdbCreator(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("profile_path") val profilePath: String? = null
)
