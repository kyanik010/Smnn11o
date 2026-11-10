package com.idealplayer.app.data.repository

import com.idealplayer.app.core.common.SearchMatcher
import com.idealplayer.app.core.common.StringUtils
import com.idealplayer.app.core.common.isUsableArtworkUrl
import com.idealplayer.app.core.common.orderCategoryNames
import com.idealplayer.app.core.database.*
import com.idealplayer.app.core.common.rethrowIfCancellation
import com.idealplayer.app.core.model.*
import com.idealplayer.app.data.parser.XtreamClient
import com.idealplayer.app.data.parser.XtreamVodMetadata
import com.idealplayer.app.metadata.MetadataProvider
import com.idealplayer.app.metadata.MetadataResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

private const val SEARCH_CANDIDATE_LIMIT = 360
private const val SEARCH_FULL_SCAN_FALLBACK_MIN_LENGTH = 3

private data class SearchQueryParts(
    val sqlQuery: String,
    val token: String,
    val normalizedQuery: String
)

@Singleton
class ContentRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val favoriteDao: FavoriteDao,
    private val playlistDao: PlaylistDao,
    private val xtreamClient: XtreamClient,
    private val metadataProvider: MetadataProvider
) {
    private val movieArtworkRepairMutex = Mutex()
    private val movieArtworkRepairIds = ConcurrentHashMap.newKeySet<Long>()
    private val seriesArtworkRepairMutex = Mutex()
    private val seriesArtworkRepairIds = ConcurrentHashMap.newKeySet<Long>()

    // Channels
    fun getChannels(playlistId: Long): Flow<List<Channel>> =
        channelDao.getByPlaylist(playlistId).map { list -> list.map { it.toModel() } }

    fun getRecentlyWatchedChannels(playlistId: Long): Flow<List<Channel>> =
        channelDao.getRecentlyWatched(playlistId).map { list -> list.map { it.toModel() } }

    fun getChannelsByGroup(playlistId: Long, group: String): Flow<List<Channel>> =
        channelDao.getByGroup(playlistId, group).map { list -> list.map { it.toModel() } }

    fun getChannelGroups(playlistId: Long): Flow<List<String>> =
        channelDao.getGroups(playlistId).map { orderCategoryNames(it) }

    fun getFavoriteChannels(playlistId: Long): Flow<List<Channel>> =
        channelDao.getFavorites(playlistId).map { list -> list.map { it.toModel() } }

    suspend fun getChannel(id: Long): Channel? = channelDao.getById(id)?.toModel()

    suspend fun toggleChannelFavorite(id: Long, favorite: Boolean) =
        channelDao.setFavorite(id, favorite)

    suspend fun updateChannelLastWatched(id: Long) =
        channelDao.updateLastWatched(id)

    suspend fun clearRecentlyWatchedChannels() = channelDao.clearWatchState()

    // Movies
    fun getMovies(playlistId: Long): Flow<List<Movie>> =
        movieDao.getByPlaylist(playlistId).map { list -> list.map { it.toModel() } }

    fun getMoviesByCategory(playlistId: Long, category: String): Flow<List<Movie>> =
        movieDao.getByCategory(playlistId, category).map { list -> list.map { it.toModel() } }

    fun getMovieCategories(playlistId: Long): Flow<List<String>> =
        movieDao.getCategories(playlistId).map { orderCategoryNames(it) }

    fun getFavoriteMovies(playlistId: Long): Flow<List<Movie>> =
        movieDao.getFavorites(playlistId).map { list -> list.map { it.toModel() } }

    fun getRecentMovies(playlistId: Long): Flow<List<Movie>> =
        movieDao.getRecentlyWatched(playlistId).map { list -> list.map { it.toModel() } }

    fun getLatestAddedMovies(playlistId: Long, limit: Int = 30): Flow<List<Movie>> =
        movieDao.getLatestAdded(playlistId, limit).map { list -> list.map { it.toModel() } }

    fun getContinueWatchingMovies(playlistId: Long): Flow<List<Movie>> =
        movieDao.getContinueWatching(playlistId).map { list -> list.map { it.toModel() } }

    fun getSimilarMovies(playlistId: Long, category: String, excludeId: Long): Flow<List<Movie>> =
        movieDao.getSimilar(playlistId, category, excludeId).map { list -> list.map { it.toModel() } }

    suspend fun getMovie(id: Long): Movie? = movieDao.getById(id)?.toModel()

    suspend fun toggleMovieFavorite(id: Long, favorite: Boolean) =
        movieDao.setFavorite(id, favorite)

    suspend fun updateMovieProgress(id: Long, position: Long, total: Long) =
        movieDao.updateProgress(id, position, total)

    suspend fun backfillMissingMovieArtwork(
        playlistId: Long,
        limit: Int = 24
    ): Int = withContext(Dispatchers.IO) {
        if (!movieArtworkRepairMutex.tryLock()) return@withContext 0
        try {
            val playlist = playlistDao.getById(playlistId)?.toModel() ?: return@withContext 0
            val candidates = movieDao.getByPlaylistSnapshot(playlistId)
                .asSequence()
                .filterNot { movie -> isUsableArtworkUrl(movie.posterUrl) }
                .take(limit.coerceIn(1, 60))
                .toList()
            if (candidates.isEmpty()) return@withContext 0

            val semaphore = Semaphore(3)
            coroutineScope {
                candidates.map { movie ->
                    async {
                        semaphore.withPermit {
                            try {
                                repairMovieArtwork(movie, playlist, replaceExistingArtwork = false)
                            } catch (error: Exception) {
                                error.rethrowIfCancellation()
                                Timber.w("Movie artwork repair failed for id=%d: %s", movie.id, error.javaClass.simpleName)
                                false
                            }
                        }
                    }
                }.awaitAll().count { repaired -> repaired }
            }
        } finally {
            movieArtworkRepairMutex.unlock()
        }
    }

    suspend fun repairMovieArtwork(movieId: Long): Boolean = withContext(Dispatchers.IO) {
        val entity = movieDao.getById(movieId) ?: return@withContext false
        val playlist = playlistDao.getById(entity.playlistId)?.toModel() ?: return@withContext false
        repairMovieArtwork(entity, playlist, replaceExistingArtwork = true)
    }

    private suspend fun repairMovieArtwork(
        entity: MovieEntity,
        playlist: Playlist,
        replaceExistingArtwork: Boolean
    ): Boolean {
        if (!movieArtworkRepairIds.add(entity.id)) return false
        try {
            val movie = entity.toModel()
            val cached = metadataProvider.getCachedMetadata(movie.name, movie.year, ContentType.MOVIE)
            if (cached != null && cached.improves(movie, replaceExistingArtwork)) {
                updateMovieWithMetadata(movie.id, cached, replaceExistingArtwork)
                return true
            }

            var providerMetadataResult: MetadataResult? = null
            if (playlist.type == PlaylistType.XTREAM_CODES && movie.streamId > 0) {
                val providerMetadata = xtreamClient.loadVodMetadata(
                    serverUrl = playlist.serverUrl,
                    username = playlist.username,
                    password = playlist.password,
                    vodId = movie.streamId
                )
                if (providerMetadata != null) {
                    val providerResult = providerMetadata.toMetadataResult()
                    providerMetadataResult = providerResult
                    if (providerResult.improves(movie, replaceExistingArtwork)) {
                        updateMovieWithMetadata(movie.id, providerResult, replaceExistingArtwork)
                    }
                    if (providerResult.isCompleteCatalogMetadata()) return true
                }
            }

            val requestedTmdbId = providerMetadataResult?.tmdbId?.takeIf { it > 0 } ?: movie.tmdbId
            val fetched = metadataProvider.fetchMetadata(
                title = movie.name,
                year = movie.year,
                contentType = ContentType.MOVIE,
                tmdbId = requestedTmdbId
            )
            val identityCorrected = fetched != null && requestedTmdbId > 0 &&
                fetched.tmdbId > 0 && fetched.tmdbId != requestedTmdbId
            if (fetched != null && (fetched.improves(movie, replaceExistingArtwork) || identityCorrected)) {
                updateMovieWithMetadata(
                    movieId = movie.id,
                    metadata = fetched,
                    replaceExistingArtwork = replaceExistingArtwork || identityCorrected,
                    replaceExistingIdentity = identityCorrected
                )
                return true
            }
            return providerMetadataResult?.improves(movie, replaceExistingArtwork) == true
        } finally {
            movieArtworkRepairIds.remove(entity.id)
        }
    }

    // Series
    fun getSeries(playlistId: Long): Flow<List<Series>> =
        seriesDao.getByPlaylist(playlistId).map { list -> list.map { it.toModel() } }

    fun getSeriesByCategory(playlistId: Long, category: String): Flow<List<Series>> =
        seriesDao.getByCategory(playlistId, category).map { list -> list.map { it.toModel() } }

    fun getSeriesCategories(playlistId: Long): Flow<List<String>> =
        seriesDao.getCategories(playlistId).map { orderCategoryNames(it) }

    fun getFavoriteSeries(playlistId: Long): Flow<List<Series>> =
        seriesDao.getFavorites(playlistId).map { list -> list.map { it.toModel() } }

    fun getLatestAddedSeries(playlistId: Long, limit: Int = 30): Flow<List<Series>> =
        seriesDao.getLatestAdded(playlistId, limit).map { list -> list.map { it.toModel() } }

    suspend fun getSeriesById(id: Long): Series? = seriesDao.getById(id)?.toModel()

    suspend fun toggleSeriesFavorite(id: Long, favorite: Boolean) =
        seriesDao.setFavorite(id, favorite)

    suspend fun backfillMissingSeriesArtwork(
        playlistId: Long,
        limit: Int = 24
    ): Int = withContext(Dispatchers.IO) {
        if (!seriesArtworkRepairMutex.tryLock()) return@withContext 0
        try {
            val playlist = playlistDao.getById(playlistId)?.toModel() ?: return@withContext 0
            val candidates = seriesDao.getByPlaylistSnapshot(playlistId)
                .asSequence()
                .filterNot { series -> isUsableArtworkUrl(series.posterUrl) }
                .take(limit.coerceIn(1, 60))
                .toList()
            if (candidates.isEmpty()) return@withContext 0

            val semaphore = Semaphore(3)
            coroutineScope {
                candidates.map { series ->
                    async {
                        semaphore.withPermit {
                            repairSeriesMetadata(series, playlist, replaceExistingArtwork = false)
                        }
                    }
                }.awaitAll().count { repaired -> repaired }
            }
        } finally {
            seriesArtworkRepairMutex.unlock()
        }
    }

    suspend fun repairSeriesArtwork(seriesId: Long): Boolean = withContext(Dispatchers.IO) {
        val entity = seriesDao.getById(seriesId) ?: return@withContext false
        val playlist = playlistDao.getById(entity.playlistId)?.toModel() ?: return@withContext false
        repairSeriesMetadata(entity, playlist, replaceExistingArtwork = true)
    }

    /**
     * Incrementally enriches the whole catalog without flooding provider/TMDB endpoints.
     * Callers persist the returned offsets so failed titles cannot permanently block later
     * rows in a large IPTV catalog.
     */
    suspend fun enrichIncompleteCatalogMetadata(
        playlistId: Long,
        movieOffset: Int,
        seriesOffset: Int,
        movieLimit: Int = 60,
        seriesLimit: Int = 40
    ): CatalogMetadataRefresh = withContext(Dispatchers.IO) {
        val playlist = playlistDao.getById(playlistId)?.toModel()
            ?: return@withContext CatalogMetadataRefresh()
        // Missing posters are immediately visible in every catalog grid, so rotate through
        // those rows before spending the foreground budget on secondary plot/cast metadata.
        // The cursor still advances inside the priority group, preventing an unmatchable title
        // from permanently blocking later artwork repairs.
        val movieCandidates = movieDao.getByPlaylistSnapshot(playlistId)
            .filter(MovieEntity::needsCatalogMetadata)
            .sortedBy { movie -> isUsableArtworkUrl(movie.posterUrl) }
        val seriesCandidates = seriesDao.getByPlaylistSnapshot(playlistId)
            .filter(SeriesEntity::needsCatalogMetadata)
            .sortedBy { series -> isUsableArtworkUrl(series.posterUrl) }
        val selectedMovies = movieCandidates.prioritizedCircularSlice(
            offset = movieOffset,
            limit = movieLimit.coerceIn(1, 60),
            isPriority = { movie -> !isUsableArtworkUrl(movie.posterUrl) }
        )
        val selectedSeries = seriesCandidates.prioritizedCircularSlice(
            offset = seriesOffset,
            limit = seriesLimit.coerceIn(1, 40),
            isPriority = { series -> !isUsableArtworkUrl(series.posterUrl) }
        )
        val semaphore = Semaphore(2)

        val movieUpdates = coroutineScope {
            selectedMovies.map { movie ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            repairMovieArtwork(movie, playlist, replaceExistingArtwork = false)
                        }.getOrElse { error ->
                            error.rethrowIfCancellation()
                            Timber.w(
                                "Movie metadata enrichment failed for id=%d: %s",
                                movie.id,
                                error.javaClass.simpleName
                            )
                            false
                        }
                    }
                }
            }.awaitAll().count { it }
        }
        val seriesUpdates = coroutineScope {
            selectedSeries.map { series ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            repairSeriesMetadata(series, playlist, replaceExistingArtwork = false)
                        }.getOrElse { error ->
                            error.rethrowIfCancellation()
                            Timber.w(
                                "Series metadata enrichment failed for id=%d: %s",
                                series.id,
                                error.javaClass.simpleName
                            )
                            false
                        }
                    }
                }
            }.awaitAll().count { it }
        }

        CatalogMetadataRefresh(
            movieProcessed = selectedMovies.size,
            seriesProcessed = selectedSeries.size,
            updated = movieUpdates + seriesUpdates,
            movieRemaining = movieCandidates.size,
            seriesRemaining = seriesCandidates.size,
            nextMovieOffset = nextCircularOffset(movieOffset, selectedMovies.size, movieCandidates.size),
            nextSeriesOffset = nextCircularOffset(seriesOffset, selectedSeries.size, seriesCandidates.size)
        )
    }

    private suspend fun repairSeriesMetadata(
        entity: SeriesEntity,
        playlist: Playlist,
        replaceExistingArtwork: Boolean
    ): Boolean {
        if (!seriesArtworkRepairIds.add(entity.id)) return false
        try {
            val series = entity.toModel()
            val cached = metadataProvider.getCachedMetadata(
                series.name,
                series.year,
                ContentType.SERIES
            )
            if (cached != null && cached.improves(series, replaceExistingArtwork)) {
                updateSeriesWithMetadata(series.id, cached, replaceExistingArtwork)
                return true
            }

            var providerMetadataResult: MetadataResult? = null
            if (
                playlist.type == PlaylistType.XTREAM_CODES &&
                series.seriesId > 0 &&
                (!isUsableArtworkUrl(series.posterUrl) || !isUsableArtworkUrl(series.backdropUrl))
            ) {
                val providerMetadata = xtreamClient.loadSeriesMetadata(
                    serverUrl = playlist.serverUrl,
                    username = playlist.username,
                    password = playlist.password,
                    seriesId = series.seriesId
                )
                if (providerMetadata != null) {
                    val providerResult = providerMetadata.toMetadataResult()
                    providerMetadataResult = providerResult
                    if (providerResult.improves(series, replaceExistingArtwork)) {
                        updateSeriesWithMetadata(series.id, providerResult, replaceExistingArtwork)
                    }
                    if (providerResult.isCompleteCatalogMetadata()) return true
                }
            }
            val requestedTmdbId = providerMetadataResult?.tmdbId?.takeIf { it > 0 } ?: series.tmdbId
            val fetched = metadataProvider.fetchMetadata(
                title = series.name,
                year = series.year,
                contentType = ContentType.SERIES,
                tmdbId = requestedTmdbId
            ) ?: return providerMetadataResult?.improves(series, replaceExistingArtwork) == true
            val identityCorrected = requestedTmdbId > 0 && fetched.tmdbId > 0 &&
                fetched.tmdbId != requestedTmdbId
            if (!fetched.improves(series, replaceExistingArtwork) && !identityCorrected) return false
            updateSeriesWithMetadata(
                seriesId = series.id,
                metadata = fetched,
                replaceExistingArtwork = replaceExistingArtwork || identityCorrected,
                replaceExistingIdentity = identityCorrected
            )
            return true
        } finally {
            seriesArtworkRepairIds.remove(entity.id)
        }
    }

    // Episodes
    fun getEpisodes(seriesId: Long): Flow<List<Episode>> =
        episodeDao.getBySeries(seriesId).map { list -> list.map { it.toModel() } }

    fun getEpisodesBySeason(seriesId: Long, season: Int): Flow<List<Episode>> =
        episodeDao.getBySeason(seriesId, season).map { list -> list.map { it.toModel() } }

    fun getSeasons(seriesId: Long): Flow<List<Int>> =
        episodeDao.getSeasons(seriesId)

    suspend fun getAllEpisodes(seriesId: Long): List<Episode> =
        episodeDao.getBySeries(seriesId).first().map { it.toModel() }

    suspend fun getEpisodesForSeason(seriesId: Long, season: Int): List<Episode> =
        episodeDao.getBySeason(seriesId, season).first().map { it.toModel() }

    suspend fun getEpisode(id: Long): Episode? = episodeDao.getById(id)?.toModel()

    suspend fun updateEpisodeProgress(id: Long, position: Long, total: Long) =
        episodeDao.updateProgress(id, position, total)

    suspend fun getLastWatchedEpisode(seriesId: Long): Episode? =
        episodeDao.getLastWatched(seriesId)?.toModel()

    suspend fun getNextEpisode(seriesId: Long, season: Int, episode: Int): Episode? {
        val next = episodeDao.getEpisode(seriesId, season, episode + 1)
        if (next != null) return next.toModel()
        return episodeDao.getEpisode(seriesId, season + 1, 1)?.toModel()
    }

    suspend fun getSeriesResumeEpisode(seriesId: Long): Episode? {
        val series = seriesDao.getById(seriesId)
        val lastWatchedEpisodeId = series?.lastWatchedEpisodeId ?: 0L
        if (lastWatchedEpisodeId > 0) {
            episodeDao.getById(lastWatchedEpisodeId)?.toModel()?.let { return it }
        }

        episodeDao.getLastWatched(seriesId)?.toModel()?.let { return it }
        return getAllEpisodes(seriesId).firstOrNull()
    }

    suspend fun updateSeriesLastWatchedEpisode(seriesId: Long, episodeId: Long) {
        val series = seriesDao.getById(seriesId) ?: return
        if (series.lastWatchedEpisodeId == episodeId) return
        seriesDao.insertAll(listOf(series.copy(lastWatchedEpisodeId = episodeId)))
    }

    /**
     * Fetch episodes from Xtream API if not already in DB.
     */
    suspend fun syncSeriesEpisodes(series: Series): Boolean = withContext(Dispatchers.IO) {
        try {
            val existingCount = episodeDao.getBySeries(series.id).first().size
            if (existingCount > 0) {
                Timber.d("Episodes already cached for series ${series.id} (${series.name}), count=$existingCount")
                return@withContext false
            }

            val playlist = playlistDao.getById(series.playlistId)?.toModel()
            if (playlist == null || playlist.type != PlaylistType.XTREAM_CODES) {
                Timber.w("Cannot fetch episodes: playlist ${series.playlistId} not found or not Xtream")
                return@withContext false
            }

            if (series.seriesId == 0) {
                Timber.w("Cannot fetch episodes: series ${series.id} has no Xtream seriesId")
                return@withContext false
            }

            Timber.d("Fetching episodes from Xtream API for series ${series.name} (seriesId=${series.seriesId})")
            val episodes = xtreamClient.loadSeriesEpisodes(
                serverUrl = playlist.serverUrl,
                username = playlist.username,
                password = playlist.password,
                seriesId = series.seriesId,
                dbSeriesId = series.id
            )

            if (episodes.isNotEmpty()) {
                episodeDao.insertAll(episodes)
                Timber.d("Inserted ${episodes.size} episodes for series ${series.name}")
                return@withContext true
            }
            false
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "Failed to sync episodes for series ${series.name}")
            false
        }
    }

    /**
     * Fetch metadata from TMDB for a movie or series with missing details.
     * Returns the MetadataResult if successful and confidence is sufficient, null otherwise.
     */
    suspend fun enrichMetadata(title: String, year: Int, contentType: ContentType, tmdbId: Int = 0): MetadataResult? {
        return withContext(Dispatchers.IO) {
            try {
                val result = metadataProvider.fetchMetadata(title, year, contentType, tmdbId)
                // MetadataProvider already handles confidence thresholds
                // A null return means no confident match was found
                result
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Metadata enrichment failed for: $title")
                null
            }
        }
    }

    suspend fun getCachedMetadata(title: String, year: Int, contentType: ContentType): MetadataResult? {
        return withContext(Dispatchers.IO) {
            try {
                metadataProvider.getCachedMetadata(title, year, contentType)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "Metadata cache lookup failed for: $title")
                null
            }
        }
    }

    /**
     * Update a movie entity with enriched metadata from TMDB.
     * Prefers TMDB data when provider data is empty/generic.
     */
    suspend fun updateMovieWithMetadata(
        movieId: Long,
        metadata: MetadataResult,
        replaceExistingArtwork: Boolean = false,
        replaceExistingIdentity: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            val entity = movieDao.getById(movieId) ?: return@withContext
            val updated = entity.copy(
                plot = metadata.overview.ifBlank { entity.plot },
                cast = metadata.cast.ifBlank { entity.cast },
                director = metadata.director.ifBlank { entity.director },
                genre = metadata.genre.ifBlank { entity.genre },
                rating = if ((replaceExistingIdentity || entity.rating == 0.0) && metadata.rating > 0) metadata.rating else entity.rating,
                year = if ((replaceExistingIdentity || entity.year == 0) && metadata.year > 0) metadata.year else entity.year,
                posterUrl = if (
                    isUsableArtworkUrl(metadata.posterUrl) &&
                    (replaceExistingArtwork || !isUsableArtworkUrl(entity.posterUrl))
                ) metadata.posterUrl else entity.posterUrl,
                backdropUrl = if (
                    isUsableArtworkUrl(metadata.backdropUrl) &&
                    (replaceExistingArtwork || !isUsableArtworkUrl(entity.backdropUrl))
                ) metadata.backdropUrl else entity.backdropUrl,
                tmdbId = if ((replaceExistingIdentity || entity.tmdbId == 0) && metadata.tmdbId > 0) metadata.tmdbId else entity.tmdbId,
                imdbId = if (replaceExistingIdentity) {
                    metadata.imdbId
                } else if (entity.imdbId.isBlank() && metadata.imdbId.isNotBlank()) {
                    metadata.imdbId
                } else {
                    entity.imdbId
                },
                duration = if ((replaceExistingIdentity || entity.duration == 0) && metadata.runtime > 0) metadata.runtime else entity.duration
            )
            movieDao.insertAll(listOf(updated))
        }
    }

    /**
     * Update a series entity with enriched metadata from TMDB.
     */
    suspend fun updateSeriesWithMetadata(
        seriesId: Long,
        metadata: MetadataResult,
        replaceExistingArtwork: Boolean = false,
        replaceExistingIdentity: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            val entity = seriesDao.getById(seriesId) ?: return@withContext
            val updated = entity.copy(
                plot = metadata.overview.ifBlank { entity.plot },
                cast = metadata.cast.ifBlank { entity.cast },
                director = metadata.director.ifBlank { entity.director },
                genre = metadata.genre.ifBlank { entity.genre },
                rating = if ((replaceExistingIdentity || entity.rating == 0.0) && metadata.rating > 0) metadata.rating else entity.rating,
                year = if ((replaceExistingIdentity || entity.year == 0) && metadata.year > 0) metadata.year else entity.year,
                posterUrl = if (
                    isUsableArtworkUrl(metadata.posterUrl) &&
                    (replaceExistingArtwork || !isUsableArtworkUrl(entity.posterUrl))
                ) metadata.posterUrl else entity.posterUrl,
                backdropUrl = if (
                    isUsableArtworkUrl(metadata.backdropUrl) &&
                    (replaceExistingArtwork || !isUsableArtworkUrl(entity.backdropUrl))
                ) metadata.backdropUrl else entity.backdropUrl,
                tmdbId = if ((replaceExistingIdentity || entity.tmdbId == 0) && metadata.tmdbId > 0) metadata.tmdbId else entity.tmdbId,
                imdbId = if (replaceExistingIdentity) {
                    metadata.imdbId
                } else if (entity.imdbId.isBlank() && metadata.imdbId.isNotBlank()) {
                    metadata.imdbId
                } else {
                    entity.imdbId
                }
            )
            seriesDao.insertAll(listOf(updated))
        }
    }

    // Watch History
    fun getWatchHistory(): Flow<List<WatchHistoryItem>> =
        watchHistoryDao.getRecent().map { list -> list.map { it.toModel() } }

    fun getContinueWatching(): Flow<List<WatchHistoryItem>> =
        watchHistoryDao.getContinueWatching().map { list ->
            dedupeContinueWatching(list.map { it.toModel() })
        }

    suspend fun addWatchHistory(item: WatchHistoryItem) {
        val existing = watchHistoryDao.getByContent(item.contentId, item.contentType.name)
        val entity = item.toEntity().copy(id = existing?.id ?: 0)
        watchHistoryDao.insert(entity)
    }

    suspend fun clearWatchHistory() {
        withContext(Dispatchers.IO) {
            watchHistoryDao.deleteAll()
            movieDao.clearWatchProgress()
            episodeDao.clearWatchProgress()
            seriesDao.clearWatchProgress()
            channelDao.clearWatchState()
        }
    }

    suspend fun clearContinueWatchingHistory() {
        withContext(Dispatchers.IO) {
            watchHistoryDao.deleteAll()
        }
    }

    // Favorites
    fun getAllFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.getAll()

    fun isFavorite(contentId: Long, type: ContentType): Flow<Boolean> =
        favoriteDao.isFavorite(contentId, type.name)

    suspend fun addFavorite(contentId: Long, type: ContentType, title: String, posterUrl: String, streamUrl: String) {
        favoriteDao.insert(FavoriteEntity(contentId = contentId, contentType = type.name, title = title, posterUrl = posterUrl, streamUrl = streamUrl))
    }

    suspend fun removeFavorite(contentId: Long, type: ContentType) {
        favoriteDao.delete(contentId, type.name)
    }

    // Search
    fun searchChannels(playlistId: Long, query: String): Flow<List<Channel>> =
        channelDao.getByPlaylist(playlistId)
            .map { list ->
                SearchMatcher.rank(
                    query = query,
                    items = list,
                    primary = { it.name },
                    secondary = { listOf(it.groupTitle, it.epgChannelId) }
                ).map { it.toModel() }
            }
            .flowOn(Dispatchers.Default)

    suspend fun searchChannelsNow(
        playlistId: Long,
        query: String,
        allowFullScanFallback: Boolean = true
    ): List<Channel> =
        rankCandidateSearch(
            query = query,
            allowFullScanFallback = allowFullScanFallback,
            loadCandidates = { parts ->
                channelDao.searchCandidates(
                    playlistId = playlistId,
                    query = parts.sqlQuery,
                    token = parts.token,
                    limit = SEARCH_CANDIDATE_LIMIT
                )
            },
            loadFallback = { channelDao.getByPlaylistSnapshot(playlistId) },
            primary = { it.name },
            secondary = { listOf(it.groupTitle, it.epgChannelId, it.streamId.takeIf { id -> id > 0 }?.toString().orEmpty()) },
            year = { 0 },
            mapper = { it.toModel() }
        )

    fun searchMovies(playlistId: Long, query: String): Flow<List<Movie>> =
        movieDao.getByPlaylist(playlistId)
            .map { list ->
                SearchMatcher.rank(
                    query = query,
                    items = list,
                    primary = { it.name },
                    secondary = { listOf(it.categoryName, it.genre, it.releaseDate) },
                    year = { it.year }
                ).map { it.toModel() }
            }
            .flowOn(Dispatchers.Default)

    suspend fun searchMoviesNow(
        playlistId: Long,
        query: String,
        allowFullScanFallback: Boolean = true
    ): List<Movie> =
        rankCandidateSearch(
            query = query,
            allowFullScanFallback = allowFullScanFallback,
            loadCandidates = { parts ->
                movieDao.searchCandidates(
                    playlistId = playlistId,
                    query = parts.sqlQuery,
                    token = parts.token,
                    limit = SEARCH_CANDIDATE_LIMIT
                )
            },
            loadFallback = { movieDao.getByPlaylistSnapshot(playlistId) },
            primary = { it.name },
            secondary = {
                listOf(
                    it.categoryName,
                    it.genre,
                    it.releaseDate,
                    it.streamId.takeIf { id -> id > 0 }?.toString().orEmpty()
                )
            },
            year = { it.year },
            mapper = { it.toModel() }
        )

    fun searchSeries(playlistId: Long, query: String): Flow<List<Series>> =
        seriesDao.getByPlaylist(playlistId)
            .map { list ->
                SearchMatcher.rank(
                    query = query,
                    items = list,
                    primary = { it.name },
                    secondary = { listOf(it.categoryName, it.genre, it.releaseDate) },
                    year = { it.year }
                ).map { it.toModel() }
            }
            .flowOn(Dispatchers.Default)

    suspend fun searchSeriesNow(
        playlistId: Long,
        query: String,
        allowFullScanFallback: Boolean = true
    ): List<Series> =
        rankCandidateSearch(
            query = query,
            allowFullScanFallback = allowFullScanFallback,
            loadCandidates = { parts ->
                seriesDao.searchCandidates(
                    playlistId = playlistId,
                    query = parts.sqlQuery,
                    token = parts.token,
                    limit = SEARCH_CANDIDATE_LIMIT
                )
            },
            loadFallback = { seriesDao.getByPlaylistSnapshot(playlistId) },
            primary = { it.name },
            secondary = {
                listOf(
                    it.categoryName,
                    it.genre,
                    it.releaseDate,
                    it.seriesId.takeIf { id -> id > 0 }?.toString().orEmpty()
                )
            },
            year = { it.year },
            mapper = { it.toModel() }
        )

    private fun dedupeContinueWatching(items: List<WatchHistoryItem>): List<WatchHistoryItem> {
        val seenKeys = LinkedHashSet<String>()

        return items.filter { item ->
            val key = when (item.contentType) {
                ContentType.SERIES -> {
                    val seriesKey = item.seriesName.ifBlank { item.title }
                    "SERIES:${seriesKey.lowercase()}"
                }
                ContentType.MOVIE -> "MOVIE:${item.contentId}"
                ContentType.LIVE -> "LIVE:${item.contentId}"
            }

            seenKeys.add(key)
        }
    }

    private suspend fun <Entity, Model> rankCandidateSearch(
        query: String,
        allowFullScanFallback: Boolean,
        loadCandidates: suspend (SearchQueryParts) -> List<Entity>,
        loadFallback: suspend () -> List<Entity>,
        primary: (Entity) -> String,
        secondary: (Entity) -> List<String>,
        year: (Entity) -> Int,
        mapper: (Entity) -> Model
    ): List<Model> {
        val parts = query.toSearchQueryParts() ?: return emptyList()
        val candidates = withContext(Dispatchers.IO) { loadCandidates(parts) }
        val rankedCandidates = rankSearchItems(
            items = candidates,
            query = query,
            primary = primary,
            secondary = secondary,
            year = year,
            mapper = mapper
        )

        if (
            rankedCandidates.isNotEmpty() ||
            !allowFullScanFallback ||
            parts.normalizedQuery.length < SEARCH_FULL_SCAN_FALLBACK_MIN_LENGTH
        ) {
            return rankedCandidates
        }

        val fallbackItems = withContext(Dispatchers.IO) { loadFallback() }
        return rankSearchItems(
            items = fallbackItems,
            query = query,
            primary = primary,
            secondary = secondary,
            year = year,
            mapper = mapper
        )
    }

    private suspend fun <Entity, Model> rankSearchItems(
        items: List<Entity>,
        query: String,
        primary: (Entity) -> String,
        secondary: (Entity) -> List<String>,
        year: (Entity) -> Int,
        mapper: (Entity) -> Model
    ): List<Model> {
        return withContext(Dispatchers.Default) {
            SearchMatcher.rank(
                query = query,
                items = items,
                primary = primary,
                secondary = secondary,
                year = year
            ).map(mapper)
        }
    }

    private fun String.toSearchQueryParts(): SearchQueryParts? {
        val trimmed = trim()
        val normalized = StringUtils.normalizeTitle(trimmed)
        if (normalized.isBlank()) return null

        val terms = normalized
            .split(" ")
            .filter { it.length >= 2 }
        val token = terms.maxByOrNull { it.length } ?: normalized.takeIf { it.length >= 2 }.orEmpty()
        val sqlQuery = trimmed
            .replace('%', ' ')
            .replace('_', ' ')
            .trim()
            .replace(Regex("\\s+"), " ")
            .ifBlank { normalized }

        return SearchQueryParts(
            sqlQuery = sqlQuery,
            token = token,
            normalizedQuery = normalized
        )
    }
}

private fun XtreamVodMetadata.toMetadataResult(): MetadataResult = MetadataResult(
    tmdbId = tmdbId,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    overview = plot,
    genre = genre,
    cast = cast,
    director = director,
    runtime = duration,
    rating = rating,
    year = StringUtils.extractYear(releaseDate) ?: 0,
    confidence = 100.0
)

data class CatalogMetadataRefresh(
    val movieProcessed: Int = 0,
    val seriesProcessed: Int = 0,
    val updated: Int = 0,
    val movieRemaining: Int = 0,
    val seriesRemaining: Int = 0,
    val nextMovieOffset: Int = 0,
    val nextSeriesOffset: Int = 0
)

internal fun MovieEntity.needsCatalogMetadata(): Boolean =
    !isUsableArtworkUrl(posterUrl) ||
        !isUsableArtworkUrl(backdropUrl) ||
        plot.isBlank() ||
        genre.isBlank() ||
        year <= 0 ||
        tmdbId <= 0

internal fun SeriesEntity.needsCatalogMetadata(): Boolean =
    !isUsableArtworkUrl(posterUrl) ||
        !isUsableArtworkUrl(backdropUrl) ||
        plot.isBlank() ||
        genre.isBlank() ||
        year <= 0 ||
        tmdbId <= 0

internal fun nextCircularOffset(currentOffset: Int, processed: Int, total: Int): Int {
    if (total <= 0 || processed <= 0) return 0
    return (currentOffset.mod(total) + processed).mod(total)
}

private fun <T> List<T>.circularSlice(offset: Int, limit: Int): List<T> {
    if (isEmpty() || limit <= 0) return emptyList()
    val start = offset.mod(size)
    return List(minOf(limit, size)) { index -> this[(start + index).mod(size)] }
}

private fun <T> List<T>.prioritizedCircularSlice(
    offset: Int,
    limit: Int,
    isPriority: (T) -> Boolean
): List<T> {
    if (isEmpty() || limit <= 0) return emptyList()
    val priority = filter(isPriority)
    val selectedPriority = priority.circularSlice(offset, limit)
    if (selectedPriority.size >= limit) return selectedPriority

    val fallback = filterNot(isPriority)
    return selectedPriority + fallback.circularSlice(offset, limit - selectedPriority.size)
}

private fun MetadataResult.hasReplacementArtwork(
    currentPoster: String,
    currentBackdrop: String,
    replaceExistingArtwork: Boolean
): Boolean {
    fun isReplacement(candidateUrl: String, currentUrl: String): Boolean =
        isUsableArtworkUrl(candidateUrl) &&
            (!isUsableArtworkUrl(currentUrl) ||
                (replaceExistingArtwork && !candidateUrl.equals(currentUrl, ignoreCase = true)))

    return isReplacement(posterUrl, currentPoster) ||
        isReplacement(backdropUrl, currentBackdrop)
}

private fun MetadataResult.improves(movie: Movie, replaceExistingArtwork: Boolean): Boolean =
    hasReplacementArtwork(movie.posterUrl, movie.backdropUrl, replaceExistingArtwork) ||
        (movie.plot.isBlank() && overview.isNotBlank()) ||
        (movie.genre.isBlank() && genre.isNotBlank()) ||
        (movie.cast.isBlank() && cast.isNotBlank()) ||
        (movie.director.isBlank() && director.isNotBlank()) ||
        (movie.rating <= 0.0 && rating > 0.0) ||
        (movie.year <= 0 && year > 0) ||
        (movie.duration <= 0 && runtime > 0) ||
        (movie.tmdbId <= 0 && tmdbId > 0) ||
        (movie.imdbId.isBlank() && imdbId.isNotBlank())

private fun MetadataResult.improves(series: Series, replaceExistingArtwork: Boolean): Boolean =
    hasReplacementArtwork(series.posterUrl, series.backdropUrl, replaceExistingArtwork) ||
        (series.plot.isBlank() && overview.isNotBlank()) ||
        (series.genre.isBlank() && genre.isNotBlank()) ||
        (series.cast.isBlank() && cast.isNotBlank()) ||
        (series.director.isBlank() && director.isNotBlank()) ||
        (series.rating <= 0.0 && rating > 0.0) ||
        (series.year <= 0 && year > 0) ||
        (series.tmdbId <= 0 && tmdbId > 0) ||
        (series.imdbId.isBlank() && imdbId.isNotBlank())

private fun MetadataResult.isCompleteCatalogMetadata(): Boolean =
    isUsableArtworkUrl(posterUrl) &&
        isUsableArtworkUrl(backdropUrl) &&
        overview.isNotBlank() &&
        genre.isNotBlank() &&
        year > 0 &&
        tmdbId > 0
