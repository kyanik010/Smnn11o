package com.idealplayer.app.core.model

import kotlinx.serialization.Serializable

enum class PlaylistType {
    M3U_URL,
    M3U_FILE,
    XTREAM_CODES
}

enum class ContentType {
    LIVE,
    MOVIE,
    SERIES
}



@Serializable
data class Playlist(
    val id: Long = 0,
    val name: String,
    val type: PlaylistType,
    val url: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val filePath: String = "",
    val epgUrl: String = "",
    val lastUpdated: Long = System.currentTimeMillis(),
    val lastUsed: Long = 0,
    val isActive: Boolean = false,
    val channelCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0
)

data class Channel(
    val id: Long = 0,
    val playlistId: Long,
    val streamId: Int = 0,
    val name: String,
    val logoUrl: String = "",
    val groupTitle: String = "",
    val streamUrl: String,
    val epgChannelId: String = "",
    val catchupSource: String = "",
    val isFavorite: Boolean = false,
    val lastWatched: Long = 0,
    val sortOrder: Int = 0
)

data class Movie(
    val id: Long = 0,
    val playlistId: Long,
    val streamId: Int = 0,
    val name: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val streamUrl: String,
    val genre: String = "",
    val plot: String = "",
    val cast: String = "",
    val director: String = "",
    val releaseDate: String = "",
    val year: Int = 0,
    val duration: Int = 0,
    val rating: Double = 0.0,
    val imdbId: String = "",
    val tmdbId: Int = 0,
    val containerExtension: String = "",
    val categoryId: Int = 0,
    val categoryName: String = "",
    val isFavorite: Boolean = false,
    val lastPosition: Long = 0,
    val lastWatched: Long = 0,
    val totalDuration: Long = 0,
    val addedAt: Long = 0,
    val sourceOrder: Int = 0
)

data class Series(
    val id: Long = 0,
    val playlistId: Long,
    val seriesId: Int = 0,
    val name: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val genre: String = "",
    val plot: String = "",
    val cast: String = "",
    val director: String = "",
    val releaseDate: String = "",
    val year: Int = 0,
    val rating: Double = 0.0,
    val imdbId: String = "",
    val tmdbId: Int = 0,
    val categoryId: Int = 0,
    val categoryName: String = "",
    val isFavorite: Boolean = false,
    val lastWatchedEpisodeId: Long = 0,
    val seasonCount: Int = 0,
    val episodeCount: Int = 0,
    val addedAt: Long = 0,
    val sourceOrder: Int = 0
)

data class Season(
    val id: Long = 0,
    val seriesId: Long,
    val seasonNumber: Int,
    val name: String = "",
    val posterUrl: String = "",
    val episodeCount: Int = 0
)

data class Episode(
    val id: Long = 0,
    val seriesId: Long,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val plot: String = "",
    val posterUrl: String = "",
    val streamUrl: String,
    val duration: Int = 0,
    val rating: Double = 0.0,
    val containerExtension: String = "",
    val isFavorite: Boolean = false,
    val lastPosition: Long = 0,
    val lastWatched: Long = 0,
    val totalDuration: Long = 0
)

data class Category(
    val id: Long = 0,
    val playlistId: Long,
    val categoryId: Int = 0,
    val name: String,
    val contentType: ContentType,
    val parentId: Int = 0,
    val itemCount: Int = 0
)

data class EpgProgram(
    val id: Long = 0,
    val channelId: String,
    val title: String,
    val description: String = "",
    val startTime: Long,
    val endTime: Long,
    val posterUrl: String = "",
    val genre: String = ""
)

data class WatchHistoryItem(
    val id: Long = 0,
    val contentId: Long,
    val contentType: ContentType,
    val title: String,
    val posterUrl: String = "",
    val streamUrl: String,
    val position: Long = 0,
    val totalDuration: Long = 0,
    val progress: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val seriesName: String = ""
)

data class SearchResult(
    val id: Long,
    val title: String,
    val posterUrl: String = "",
    val contentType: ContentType,
    val genre: String = "",
    val year: Int = 0,
    val rating: Double = 0.0,
    val streamUrl: String = ""
)

data class ContentDetail(
    val id: Long,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val genre: String,
    val plot: String,
    val cast: String,
    val director: String,
    val year: Int,
    val duration: Int,
    val rating: Double,
    val contentType: ContentType,
    val streamUrl: String,
    val isFavorite: Boolean,
    val lastPosition: Long,
    val seasons: List<Season> = emptyList(),
    val episodes: List<Episode> = emptyList()
)
