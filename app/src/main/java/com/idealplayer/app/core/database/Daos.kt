package com.idealplayer.app.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY lastUsed DESC")
    fun getAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    fun getActiveFlow(): Flow<PlaylistEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Delete
    suspend fun delete(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE playlists SET isActive = 1, lastUsed = :time WHERE id = :id")
    suspend fun activate(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE playlists SET channelCount = :channels, movieCount = :movies, seriesCount = :series, lastUpdated = :time WHERE id = :id")
    suspend fun updateCounts(id: Long, channels: Int, movies: Int, series: Int, time: Long = System.currentTimeMillis())
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY sortOrder, name")
    fun getByPlaylist(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND lastWatched > 0 ORDER BY lastWatched DESC LIMIT :limit")
    fun getRecentlyWatched(playlistId: Long, limit: Int = 20): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY sortOrder, name")
    suspend fun getByPlaylistSnapshot(playlistId: Long): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY sortOrder, name LIMIT :limit OFFSET :offset")
    suspend fun getByPlaylistPaged(playlistId: Long, limit: Int, offset: Int): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND groupTitle = :group ORDER BY sortOrder, name")
    fun getByGroup(playlistId: Long, group: String): Flow<List<ChannelEntity>>

    @Query("SELECT groupTitle FROM channels WHERE playlistId = :playlistId AND TRIM(groupTitle) != '' GROUP BY groupTitle ORDER BY MIN(sortOrder), MIN(id)")
    fun getGroups(playlistId: Long): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isFavorite = 1 ORDER BY name")
    fun getFavorites(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%' ORDER BY name")
    fun search(playlistId: Long, query: String): Flow<List<ChannelEntity>>

    @Query("""
        SELECT * FROM channels
        WHERE playlistId = :playlistId
        AND (
            name LIKE '%' || :query || '%'
            OR groupTitle LIKE '%' || :query || '%'
            OR epgChannelId LIKE '%' || :query || '%'
            OR CAST(streamId AS TEXT) LIKE '%' || :query || '%'
            OR (
                :token != ''
                AND (
                    name LIKE '%' || :token || '%'
                    OR groupTitle LIKE '%' || :token || '%'
                    OR epgChannelId LIKE '%' || :token || '%'
                )
            )
        )
        ORDER BY
            CASE
                WHEN name LIKE :query || '%' THEN 0
                WHEN name LIKE '%' || :query || '%' THEN 1
                ELSE 3
            END,
            sortOrder,
            name
        LIMIT :limit
    """)
    suspend fun searchCandidates(
        playlistId: Long,
        query: String,
        token: String,
        limit: Int
    ): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE channels SET lastWatched = :time WHERE id = :id")
    suspend fun updateLastWatched(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE channels SET lastWatched = 0")
    suspend fun clearWatchState()

    @Query("UPDATE channels SET isOnline = :isOnline WHERE id = :id")
    suspend fun setStreamOnline(id: Long, isOnline: Boolean)

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isOnline = 0")
    fun getDeadChannels(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int
}

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies WHERE playlistId = :playlistId ORDER BY name")
    fun getByPlaylist(playlistId: Long): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND categoryName = :category ORDER BY name")
    fun getByCategory(playlistId: Long, category: String): Flow<List<MovieEntity>>

    @Query("SELECT categoryName FROM movies WHERE playlistId = :playlistId AND TRIM(categoryName) != '' GROUP BY categoryName ORDER BY MIN(id)")
    fun getCategories(playlistId: Long): Flow<List<String>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND isFavorite = 1 ORDER BY name")
    fun getFavorites(playlistId: Long): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId ORDER BY lastWatched DESC LIMIT 20")
    fun getRecentlyWatched(playlistId: Long): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId ORDER BY addedAt DESC, sourceOrder ASC, id DESC LIMIT :limit")
    fun getLatestAdded(playlistId: Long, limit: Int = 30): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%' ORDER BY name")
    fun search(playlistId: Long, query: String): Flow<List<MovieEntity>>

    @Query("""
        SELECT * FROM movies
        WHERE playlistId = :playlistId
        AND (
            name LIKE '%' || :query || '%'
            OR categoryName LIKE '%' || :query || '%'
            OR genre LIKE '%' || :query || '%'
            OR releaseDate LIKE '%' || :query || '%'
            OR imdbId LIKE '%' || :query || '%'
            OR CAST(streamId AS TEXT) LIKE '%' || :query || '%'
            OR CAST(year AS TEXT) LIKE '%' || :query || '%'
            OR (
                :token != ''
                AND (
                    name LIKE '%' || :token || '%'
                    OR categoryName LIKE '%' || :token || '%'
                    OR genre LIKE '%' || :token || '%'
                    OR releaseDate LIKE '%' || :token || '%'
                )
            )
        )
        ORDER BY
            CASE
                WHEN name LIKE :query || '%' THEN 0
                WHEN name LIKE '%' || :query || '%' THEN 1
                ELSE 3
            END,
            name
        LIMIT :limit
    """)
    suspend fun searchCandidates(
        playlistId: Long,
        query: String,
        token: String,
        limit: Int
    ): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun getById(id: Long): MovieEntity?

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId")
    suspend fun getByPlaylistSnapshot(playlistId: Long): List<MovieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<MovieEntity>)

    @Query("UPDATE movies SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE movies SET lastPosition = :position, totalDuration = :total, lastWatched = :time WHERE id = :id")
    suspend fun updateProgress(id: Long, position: Long, total: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE movies SET lastPosition = 0, totalDuration = 0, lastWatched = 0")
    suspend fun clearWatchProgress()

    @Query("DELETE FROM movies WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND lastPosition > 0 ORDER BY lastWatched DESC LIMIT 20")
    fun getContinueWatching(playlistId: Long): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND categoryName = :category AND id != :excludeId ORDER BY rating DESC LIMIT 10")
    fun getSimilar(playlistId: Long, category: String, excludeId: Long): Flow<List<MovieEntity>>
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY name")
    fun getByPlaylist(playlistId: Long): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND categoryName = :category ORDER BY name")
    fun getByCategory(playlistId: Long, category: String): Flow<List<SeriesEntity>>

    @Query("SELECT categoryName FROM series WHERE playlistId = :playlistId AND TRIM(categoryName) != '' GROUP BY categoryName ORDER BY MIN(id)")
    fun getCategories(playlistId: Long): Flow<List<String>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND isFavorite = 1 ORDER BY name")
    fun getFavorites(playlistId: Long): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY addedAt DESC, sourceOrder ASC, id DESC LIMIT :limit")
    fun getLatestAdded(playlistId: Long, limit: Int = 30): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%' ORDER BY name")
    fun search(playlistId: Long, query: String): Flow<List<SeriesEntity>>

    @Query("""
        SELECT * FROM series
        WHERE playlistId = :playlistId
        AND (
            name LIKE '%' || :query || '%'
            OR categoryName LIKE '%' || :query || '%'
            OR genre LIKE '%' || :query || '%'
            OR releaseDate LIKE '%' || :query || '%'
            OR imdbId LIKE '%' || :query || '%'
            OR CAST(seriesId AS TEXT) LIKE '%' || :query || '%'
            OR CAST(year AS TEXT) LIKE '%' || :query || '%'
            OR (
                :token != ''
                AND (
                    name LIKE '%' || :token || '%'
                    OR categoryName LIKE '%' || :token || '%'
                    OR genre LIKE '%' || :token || '%'
                    OR releaseDate LIKE '%' || :token || '%'
                )
            )
        )
        ORDER BY
            CASE
                WHEN name LIKE :query || '%' THEN 0
                WHEN name LIKE '%' || :query || '%' THEN 1
                ELSE 3
            END,
            name
        LIMIT :limit
    """)
    suspend fun searchCandidates(
        playlistId: Long,
        query: String,
        token: String,
        limit: Int
    ): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun getById(id: Long): SeriesEntity?

    @Query("SELECT * FROM series WHERE playlistId = :playlistId")
    suspend fun getByPlaylistSnapshot(playlistId: Long): List<SeriesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(series: List<SeriesEntity>)

    @Query("UPDATE series SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE series SET lastWatchedEpisodeId = 0")
    suspend fun clearWatchProgress()

    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber, episodeNumber")
    fun getBySeries(seriesId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNumber = :season ORDER BY episodeNumber")
    fun getBySeason(seriesId: Long, season: Int): Flow<List<EpisodeEntity>>

    @Query("SELECT DISTINCT seasonNumber FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber")
    fun getSeasons(seriesId: Long): Flow<List<Int>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getById(id: Long): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE seriesId IN (SELECT id FROM series WHERE playlistId = :playlistId)")
    suspend fun getByPlaylistSnapshot(playlistId: Long): List<EpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET lastPosition = :position, totalDuration = :total, lastWatched = :time WHERE id = :id")
    suspend fun updateProgress(id: Long, position: Long, total: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE episodes SET lastPosition = 0, totalDuration = 0, lastWatched = 0")
    suspend fun clearWatchProgress()

    @Query("DELETE FROM episodes WHERE seriesId = :seriesId")
    suspend fun deleteBySeries(seriesId: Long)

    @Query("DELETE FROM episodes WHERE seriesId IN (SELECT id FROM series WHERE playlistId = :playlistId)")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND seasonNumber = :season AND episodeNumber = :episode LIMIT 1")
    suspend fun getEpisode(seriesId: Long, season: Int, episode: Int): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND lastPosition > 0 ORDER BY lastWatched DESC LIMIT 1")
    suspend fun getLastWatched(seriesId: Long): EpisodeEntity?
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND contentType = :type ORDER BY name")
    fun getByType(playlistId: Long, type: String): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId AND contentType = :contentType")
    suspend fun deleteByPlaylistAndContentType(playlistId: Long, contentType: String)
}

@Dao
interface EpgDao {
    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND endTime > :now ORDER BY startTime LIMIT 10")
    fun getPrograms(channelId: String, now: Long = System.currentTimeMillis()): Flow<List<EpgProgramEntity>>

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime <= :now AND endTime > :now LIMIT 1")
    suspend fun getCurrentProgram(channelId: String, now: Long = System.currentTimeMillis()): EpgProgramEntity?

    @Query("SELECT * FROM epg_programs WHERE channelId IN (:channelIds) AND startTime <= :now AND endTime > :now ORDER BY startTime")
    suspend fun getCurrentProgramsForIds(channelIds: List<String>, now: Long = System.currentTimeMillis()): List<EpgProgramEntity>

    @Query("SELECT * FROM epg_programs WHERE channelId IN (:channelIds) AND endTime > :now ORDER BY startTime LIMIT :limit")
    suspend fun getUpcomingProgramsForIds(
        channelIds: List<String>,
        now: Long = System.currentTimeMillis(),
        limit: Int = 10
    ): List<EpgProgramEntity>

    @Query("SELECT * FROM epg_programs WHERE channelId IN (:channelIds) AND startTime < :windowEnd AND endTime > :windowStart ORDER BY startTime")
    suspend fun getProgramsInWindowForIds(
        channelIds: List<String>,
        windowStart: Long,
        windowEnd: Long
    ): List<EpgProgramEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE endTime < :time")
    suspend fun deleteOld(time: Long = System.currentTimeMillis())
}

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE contentType = :type ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentByType(type: String, limit: Int = 50): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE progress < 0.95 AND progress > 0.02 ORDER BY timestamp DESC LIMIT :limit")
    fun getContinueWatching(limit: Int = 20): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE contentId = :contentId AND contentType = :type LIMIT 1")
    suspend fun getByContent(contentId: Long, type: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WatchHistoryEntity): Long

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM watch_history")
    suspend fun deleteAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    fun getAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE contentType = :type ORDER BY timestamp DESC")
    fun getByType(type: String): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE contentId = :contentId AND contentType = :type)")
    fun isFavorite(contentId: Long, type: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE contentId = :contentId AND contentType = :type")
    suspend fun delete(contentId: Long, type: String)
}

@Dao
interface MetadataCacheDao {
    @Query("SELECT * FROM metadata_cache WHERE title = :title AND (year = :year OR year = 0 OR :year = 0) AND language = :language AND contentType = :contentType ORDER BY cachedAt DESC LIMIT 1")
    suspend fun find(title: String, year: Int = 0, language: String = "", contentType: String = ""): MetadataCacheEntity?

    @Query("SELECT * FROM metadata_cache WHERE tmdbId = :tmdbId AND language = :language AND contentType = :contentType ORDER BY cachedAt DESC LIMIT 1")
    suspend fun findByTmdbId(tmdbId: Int, language: String = "", contentType: String = ""): MetadataCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: MetadataCacheEntity)

    @Query("DELETE FROM metadata_cache WHERE cachedAt < :before")
    suspend fun deleteOld(before: Long)
}
