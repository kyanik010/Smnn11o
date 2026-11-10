package com.idealplayer.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
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

@Entity(
    tableName = "channels",
    indices = [Index(value = ["playlistId"]), Index(value = ["groupTitle"])]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val sortOrder: Int = 0,
    val isOnline: Boolean = true  // false = dead stream (health check failed)
)

@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["categoryId"]),
        Index(value = ["playlistId", "addedAt", "sourceOrder"])
    ]
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["categoryId"]),
        Index(value = ["playlistId", "addedAt", "sourceOrder"])
    ]
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(
    tableName = "episodes",
    indices = [Index(value = ["seriesId"]), Index(value = ["seasonNumber"])]
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(
    tableName = "categories",
    indices = [Index(value = ["playlistId"])]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val categoryId: Int = 0,
    val name: String,
    val contentType: String,
    val parentId: Int = 0,
    val itemCount: Int = 0
)

@Entity(
    tableName = "epg_programs",
    indices = [
        Index(value = ["channelId"]),
        Index(value = ["channelId", "endTime"]),
        // A channel airs exactly one programme at a given start time, so this is the
        // natural key. Making it unique lets EpgDao.insertAll (OnConflictStrategy.REPLACE)
        // overwrite the previous copy of a slot on every sync instead of accumulating
        // duplicate rows (the auto-generated id never collides, so REPLACE was a no-op).
        Index(value = ["channelId", "startTime"], unique = true),
        Index(value = ["endTime"])
    ]
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val title: String,
    val description: String = "",
    val startTime: Long,
    val endTime: Long,
    val posterUrl: String = "",
    val genre: String = ""
)

@Entity(
    tableName = "watch_history",
    indices = [Index(value = ["contentId", "contentType"])]
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: Long,
    val contentType: String,
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

@Entity(
    tableName = "favorites",
    indices = [Index(value = ["contentId", "contentType"], unique = true)]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: Long,
    val contentType: String,
    val title: String,
    val posterUrl: String = "",
    val streamUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "metadata_cache",
    indices = [Index(value = ["title", "year", "language"])]
)
data class MetadataCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val year: Int = 0,
    val language: String = "",
    val tmdbId: Int = 0,
    val imdbId: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val overview: String = "",
    val tagline: String = "",
    val genre: String = "",
    val cast: String = "",
    val director: String = "",
    val runtime: Int = 0,
    val rating: Double = 0.0,
    val trailerUrl: String = "",
    val contentType: String = "",
    val cachedAt: Long = System.currentTimeMillis()
)
