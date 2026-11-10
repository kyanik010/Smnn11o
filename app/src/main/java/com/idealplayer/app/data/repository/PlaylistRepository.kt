package com.idealplayer.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.idealplayer.app.core.common.Resource
import com.idealplayer.app.core.common.limitedTo
import com.idealplayer.app.core.common.rethrowIfCancellation
import com.idealplayer.app.core.database.*
import com.idealplayer.app.core.datastore.SettingsDataStore
import com.idealplayer.app.core.model.*
import com.idealplayer.app.core.player.parsePlaybackSource
import com.idealplayer.app.data.parser.M3uParser
import com.idealplayer.app.data.parser.XtreamClient
import com.idealplayer.app.data.parser.XtreamContentResult
import com.idealplayer.app.data.parser.XtreamContentSection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val categoryDao: CategoryDao,
    private val database: IdealPlayerDatabase,
    private val m3uParser: M3uParser,
    private val xtreamClient: XtreamClient,
    private val epgRepository: EpgRepository,
    private val okHttpClient: OkHttpClient,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) {
    private val epgDiscoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val epgDiscoveryJobs = ConcurrentHashMap<Long, Job>()
    private val activeEpgSyncJobs = ConcurrentHashMap<Long, Job>()
    // EPG rows/settings are app-global even though playlists are not. Serialize ownership
    // transitions with source/schedule commits so a late A result cannot overwrite B.
    private val epgSourceMutex = Mutex()

    fun getAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.getAll().map { list -> list.map { it.toModel() } }

    fun getActivePlaylist(): Flow<Playlist?> =
        playlistDao.getActiveFlow().map { it?.toModel() }

    suspend fun getPlaylistById(id: Long): Playlist? =
        playlistDao.getById(id)?.toModel()

    suspend fun savePlaylist(playlist: Playlist): Long {
        val entity = playlist.toEntity()
        return playlistDao.insert(entity)
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        playlistDao.update(playlist.toEntity())
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        epgSourceMutex.withLock {
            // EPG rows are app-wide and only the active playlist is scheduled. Do not cancel
            // the active source when deleting an unrelated inactive playlist that happens to
            // declare a matching URL; cancel only when the scheduled owner is being removed.
            if (playlistDao.getActive()?.id == playlist.id) {
                val configuredEpgUrl = settingsDataStore.settings.first().epgUrl
                setOf(playlist.epgUrl, configuredEpgUrl)
                    .asSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .forEach(epgRepository::cancelScheduledSync)
            }
            epgDiscoveryJobs.remove(playlist.id)?.cancel()
            activeEpgSyncJobs.remove(playlist.id)?.cancel()

            database.withTransaction {
                replacePlaylistContent(playlist.id)
                playlistDao.delete(playlist.toEntity())
            }
        }
    }

    suspend fun activatePlaylist(id: Long) {
        val activatedPlaylist = epgSourceMutex.withLock {
            val previousActiveId = playlistDao.getActive()?.id
            // The EPG table is app-wide. Stop and cancel the previous owner's work before
            // switching, then make the active flag transition one Room transaction so source
            // commits and persistence guards observe a single, unambiguous owner.
            if (previousActiveId != id) {
                settingsDataStore.settings.first().epgUrl
                    .trim()
                    .takeIf(String::isNotBlank)
                    ?.let(epgRepository::cancelScheduledSync)
                previousActiveId?.let { previousId ->
                    epgDiscoveryJobs.remove(previousId)?.cancel()
                    activeEpgSyncJobs.remove(previousId)?.cancel()
                }
            }
            val playlist = database.withTransaction {
                playlistDao.deactivateAll()
                playlistDao.activate(id)
                playlistDao.getById(id)?.toModel()
            }
            settingsDataStore.updateLastPlaylistId(id)
            playlist
        }

        // New playlists are synced before they become active during onboarding. Start EPG
        // discovery only after this ownership transition, avoiding an inactive playlist that
        // could otherwise replace the app-global EPG source or cancel the active schedule.
        activatedPlaylist?.let(::syncActivePlaylistEpgInBackground)
    }

    suspend fun hasSyncedContent(playlistId: Long): Boolean =
        channelDao.countByPlaylist(playlistId) > 0 ||
            movieDao.countByPlaylist(playlistId) > 0 ||
            seriesDao.countByPlaylist(playlistId) > 0

    suspend fun testConnection(playlist: Playlist): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (playlist.type) {
                PlaylistType.M3U_URL -> testM3uUrlConnection(playlist.url)
                PlaylistType.XTREAM_CODES -> {
                    val result = xtreamClient.authenticate(playlist.serverUrl, playlist.username, playlist.password)
                    result.map { "Connection successful - ${it.userInfo?.status}" }
                }
                PlaylistType.M3U_FILE -> {
                    openLocalPlaylist(playlist.filePath).use { }
                    Result.success("Local file ready")
                }
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e("Connection test failed: %s", e.javaClass.simpleName)
            Result.failure(e)
        }
    }

    suspend fun syncPlaylist(playlist: Playlist): Resource<Unit> = withContext(Dispatchers.IO) {
        try {
            val normalizedPlaylist = playlist.normalizedForSync()
            val content = when (normalizedPlaylist.type) {
                PlaylistType.M3U_URL -> PlaylistSyncContent.M3u(loadM3uUrl(normalizedPlaylist))
                PlaylistType.M3U_FILE -> PlaylistSyncContent.M3u(loadM3uFile(normalizedPlaylist))
                PlaylistType.XTREAM_CODES -> PlaylistSyncContent.Xtream(loadXtream(normalizedPlaylist))
            }
            content.requireNonEmpty()
            val epgDiscoverySeed = content.toEpgDiscoverySeed()
            val syncTime = System.currentTimeMillis()

            database.withTransaction {
                val existingContent = loadExistingContentState(playlist.id)

                when (content) {
                    is PlaylistSyncContent.M3u -> {
                        replacePlaylistContent(playlist.id)
                        saveParseResult(content.result, existingContent, syncTime)
                    }
                    is PlaylistSyncContent.Xtream -> {
                        saveXtreamResult(playlist.id, content.result, existingContent, syncTime)
                    }
                }

                val channelCount = channelDao.countByPlaylist(playlist.id)
                val movieCount = movieDao.countByPlaylist(playlist.id)
                val seriesCount = seriesDao.countByPlaylist(playlist.id)
                playlistDao.updateCounts(playlist.id, channelCount, movieCount, seriesCount)
            }

            syncDiscoveredEpgInBackground(normalizedPlaylist, epgDiscoverySeed)

            Resource.Success(Unit)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e("Sync failed for playlist %d: %s", playlist.id, e.javaClass.simpleName)
            Resource.Error(e.message ?: "Sync failed", e)
        }
    }

    suspend fun ensureEpgSynced(
        playlist: Playlist,
        forceRefresh: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isActivePlaylist(playlist.id)) {
            Timber.d("Skipping EPG discovery for inactive playlist %d", playlist.id)
            return@withContext false
        }
        epgDiscoveryJobs[playlist.id]
            ?.takeIf(Job::isActive)
            ?.let { runningJob ->
                runningJob.join()
                val channels = channelDao.getByPlaylistSnapshot(playlist.id).map { it.toModel() }
                return@withContext channels.isNotEmpty() &&
                    epgRepository.getCurrentProgramsForChannels(channels).isNotEmpty()
            }

        val settings = settingsDataStore.settings.first()
        if (!forceRefresh && settings.epgLastSync > 0L) {
            val channels = channelDao.getByPlaylistSnapshot(playlist.id).map { it.toModel() }
            val hasCurrentPrograms = channels.isNotEmpty() &&
                epgRepository.getCurrentProgramsForChannels(channels).isNotEmpty()
            if (hasCurrentPrograms) {
                return@withContext false
            }
            Timber.d("EPG URL exists but no current programs matched; rediscovering for playlist ${playlist.id}")
        }

        runCatching {
            val normalizedPlaylist = playlist.normalizedForSync()
            val content = when (normalizedPlaylist.type) {
                PlaylistType.M3U_URL -> PlaylistSyncContent.M3u(loadM3uUrl(normalizedPlaylist))
                PlaylistType.M3U_FILE -> PlaylistSyncContent.M3u(loadM3uFile(normalizedPlaylist))
                PlaylistType.XTREAM_CODES -> {
                    val channels = channelDao.getByPlaylistSnapshot(normalizedPlaylist.id)
                    PlaylistSyncContent.Xtream(
                        XtreamContentResult(
                            channels = channels,
                            movies = emptyList(),
                            series = emptyList(),
                            categories = emptyList()
                        )
                    )
                }
            }

            syncDiscoveredEpg(normalizedPlaylist, content.toEpgDiscoverySeed())
        }.getOrElse { error ->
            error.rethrowIfCancellation()
            Timber.w(
                "EPG auto discovery failed for playlist %d: %s",
                playlist.id,
                error.javaClass.simpleName
            )
            false
        }
    }

    private suspend fun loadM3uUrl(playlist: Playlist): com.idealplayer.app.data.parser.M3uParseResult {
        val request = Request.Builder().url(playlist.url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Playlist request failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw Exception("Empty response")
            require(body.contentLength() <= MAX_PLAYLIST_BYTES || body.contentLength() < 0L) {
                "Playlist is larger than the ${MAX_PLAYLIST_BYTES / MEBIBYTE} MiB limit"
            }
            return m3uParser.parse(body.byteStream().limitedTo(MAX_PLAYLIST_BYTES), playlist.id)
        }
    }

    private suspend fun loadM3uFile(playlist: Playlist): com.idealplayer.app.data.parser.M3uParseResult {
        return openLocalPlaylist(playlist.filePath).use { input ->
            m3uParser.parse(input.limitedTo(MAX_PLAYLIST_BYTES), playlist.id)
        }
    }

    /**
     * IPTV servers often reject HEAD while accepting the GET used by the actual sync. Try the
     * inexpensive probe first, then a tiny ranged GET before reporting a false failure.
     */
    private fun testM3uUrlConnection(url: String): Result<String> {
        val headSucceeded = runCatching {
            okHttpClient.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
                response.isSuccessful
            }
        }.getOrDefault(false)
        if (headSucceeded) return Result.success("Connection successful")

        return runCatching {
            okHttpClient.newCall(
                Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-1023")
                    .get()
                    .build()
            ).execute().use { response ->
                if (response.isSuccessful) {
                    "Connection successful"
                } else {
                    throw IllegalStateException("HTTP ${response.code}")
                }
            }
        }
    }

    private fun openLocalPlaylist(location: String): java.io.InputStream {
        val trimmed = location.trim()
        require(trimmed.isNotBlank()) { "Playlist file is not selected" }

        if (trimmed.startsWith("content://", ignoreCase = true)) {
            return context.contentResolver.openInputStream(Uri.parse(trimmed))
                ?: throw IllegalStateException("Selected playlist file cannot be opened")
        }

        val file = java.io.File(trimmed)
        if (!file.isFile) throw IllegalStateException("Selected playlist file cannot be opened")
        return file.inputStream()
    }

    private suspend fun loadXtream(playlist: Playlist): XtreamContentResult =
        xtreamClient.loadContent(
            playlist.serverUrl, playlist.username, playlist.password, playlist.id
        )

    private suspend fun replacePlaylistContent(playlistId: Long) {
        channelDao.deleteByPlaylist(playlistId)
        movieDao.deleteByPlaylist(playlistId)
        episodeDao.deleteByPlaylist(playlistId)
        seriesDao.deleteByPlaylist(playlistId)
        categoryDao.deleteByPlaylist(playlistId)
    }

    private suspend fun saveXtreamResult(
        playlistId: Long,
        result: XtreamContentResult,
        existingContent: ExistingContentState,
        syncTime: Long
    ) {
        // Xtream panels may be live-only. A section omitted from [loadedSections] was rejected
        // as unsupported, so leave its previous snapshot untouched rather than deleting a
        // user's existing VOD/series catalog during an otherwise valid live refresh.
        if (XtreamContentSection.LIVE in result.loadedSections) {
            channelDao.deleteByPlaylist(playlistId)
            categoryDao.deleteByPlaylistAndContentType(playlistId, ContentType.LIVE.name)
            insertChunked(result.channels.withPreservedChannelState(existingContent.channelByKey)) {
                channelDao.insertAll(it)
            }
            insertChunked(result.categories.filter { it.contentType == ContentType.LIVE.name }) {
                categoryDao.insertAll(it)
            }
        }

        if (XtreamContentSection.MOVIES in result.loadedSections) {
            movieDao.deleteByPlaylist(playlistId)
            categoryDao.deleteByPlaylistAndContentType(playlistId, ContentType.MOVIE.name)
            insertChunked(result.movies.withPreservedMovieState(existingContent.movieByKey, syncTime)) {
                movieDao.insertAll(it)
            }
            insertChunked(result.categories.filter { it.contentType == ContentType.MOVIE.name }) {
                categoryDao.insertAll(it)
            }
        }

        if (XtreamContentSection.SERIES in result.loadedSections) {
            // Episodes are loaded on demand. Preserve rows belonging to series that survive
            // this successful series snapshot so progress and favorites retain their ids.
            episodeDao.deleteByPlaylist(playlistId)
            seriesDao.deleteByPlaylist(playlistId)
            categoryDao.deleteByPlaylistAndContentType(playlistId, ContentType.SERIES.name)

            val series = result.series.withPreservedSeriesState(existingContent.seriesByKey, syncTime)
            insertChunked(series) { seriesDao.insertAll(it) }
            insertChunked(result.categories.filter { it.contentType == ContentType.SERIES.name }) {
                categoryDao.insertAll(it)
            }

            val preservedSeriesIds = series.mapNotNull { it.id.takeIf { id -> id > 0L } }.toSet()
            val preservedEpisodes = existingContent.episodes.filter { it.seriesId in preservedSeriesIds }
            if (preservedEpisodes.isNotEmpty()) {
                insertChunked(preservedEpisodes) { episodeDao.insertAll(it) }
            }
        }
    }

    private suspend fun syncDiscoveredEpg(
        playlist: Playlist,
        seed: EpgDiscoverySeed
    ): Boolean {
        if (!isActivePlaylist(playlist.id)) {
            Timber.d("Skipping EPG source discovery for inactive playlist %d", playlist.id)
            return false
        }
        val storedChannels = channelDao.getByPlaylistSnapshot(playlist.id).map { it.toModel() }
        val channels = storedChannels.ifEmpty { seed.channels }
        if (channels.isEmpty()) return false

        val streamUrls = seed.streamUrls.ifEmpty { channels.map { it.streamUrl } }
        val discoveredEpgUrls = when (seed.type) {
            EpgDiscoveryType.M3U -> resolveM3uEpgUrls(
                epgUrls = seed.epgUrls,
                playlistUrl = playlist.url,
                streamUrls = streamUrls
            )
            EpgDiscoveryType.XTREAM -> listOf(buildXtreamEpgUrl(playlist))
        }
            .plus(playlist.epgUrl.splitEpgSourceUrls())
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

        if (discoveredEpgUrls.isNotEmpty() && syncXmltvEpgUrls(playlist, channels, discoveredEpgUrls)) {
            return true
        }

        if (discoveredEpgUrls.isEmpty()) {
            Timber.d("No EPG URL discovered for playlist ${playlist.id}")
        }

        if (syncXtreamApiEpg(playlist, channels)) {
            return true
        }

        // Guide data must come from the user's playlist, explicitly configured XMLTV URL, or
        // the playlist's own Xtream credentials. Do not silently fetch bundled public sources.
        return false
    }

    private fun syncDiscoveredEpgInBackground(
        playlist: Playlist,
        seed: EpgDiscoverySeed
    ) {
        if (seed.channels.isEmpty()) return
        if (epgDiscoveryJobs[playlist.id]?.isActive == true) {
            Timber.d("EPG discovery already running for playlist ${playlist.id}")
            return
        }

        epgDiscoveryJobs[playlist.id] = epgDiscoveryScope.launch {
            try {
                if (isActivePlaylist(playlist.id)) {
                    syncDiscoveredEpg(playlist, seed)
                } else {
                    Timber.d("Deferred EPG discovery until playlist %d is active", playlist.id)
                }
            } catch (error: Exception) {
                error.rethrowIfCancellation()
                Timber.w(
                    "Background EPG discovery failed for playlist %d: %s",
                    playlist.id,
                    error.javaClass.simpleName
                )
            } finally {
                epgDiscoveryJobs.remove(playlist.id)
            }
        }
    }

    private fun syncActivePlaylistEpgInBackground(playlist: Playlist) {
        if (activeEpgSyncJobs[playlist.id]?.isActive == true) return

        activeEpgSyncJobs[playlist.id] = epgDiscoveryScope.launch {
            try {
                ensureEpgSynced(playlist, forceRefresh = true)
            } catch (error: Exception) {
                error.rethrowIfCancellation()
                Timber.w(
                    "Active playlist EPG discovery failed for playlist %d: %s",
                    playlist.id,
                    error.javaClass.simpleName
                )
            } finally {
                activeEpgSyncJobs.remove(playlist.id)
            }
        }
    }

    private suspend fun isActivePlaylist(playlistId: Long): Boolean =
        playlistDao.getActive()?.id == playlistId

    private suspend fun syncXmltvEpgUrls(
        playlist: Playlist,
        channels: List<Channel>,
        epgUrls: List<String>
    ): Boolean {
        if (!isActivePlaylist(playlist.id)) return false
        val channelIdMap = epgRepository.buildChannelIdMap(channels)

        for (discoveredEpgUrl in epgUrls) {
            val result = epgRepository.fetchAndSave(
                url = discoveredEpgUrl,
                channelIdMap = channelIdMap,
                expectedActivePlaylistId = playlist.id
            )
            val savedProgramCount = result.getOrNull() ?: 0
            val error = result.exceptionOrNull()
            if (error != null) {
                Timber.w(
                    "Auto EPG sync failed for playlist %d: %s",
                    playlist.id,
                    error.javaClass.simpleName
                )
                continue
            }

            if (savedProgramCount <= 0) {
                Timber.w("Auto EPG sync parsed no programs for playlist ${playlist.id}")
                continue
            }

            if (!hasCurrentEpgMatches(channels)) {
                Timber.w("Auto EPG sync parsed programs but matched no current channels for playlist ${playlist.id}")
                continue
            }

            if (!commitXmltvEpgSource(playlist.id, discoveredEpgUrl)) return false
            Timber.d("Auto EPG sync completed for playlist ${playlist.id}: $savedProgramCount programs")
            return true
        }

        return false
    }

    private suspend fun syncXtreamApiEpg(
        playlist: Playlist,
        channels: List<Channel>
    ): Boolean {
        if (!isActivePlaylist(playlist.id)) return false
        val credentials = resolveXtreamCredentials(
            playlist = playlist,
            streamUrls = channels.map { it.streamUrl }
        ) ?: return false

        val targetChannels = channels
            .filter { it.streamId > 0 }
            .take(XTREAM_SHORT_EPG_CHANNEL_LIMIT)
        if (targetChannels.isEmpty()) return false

        val programs = xtreamClient.loadLiveEpgPrograms(
            serverUrl = credentials.serverUrl,
            username = credentials.username,
            password = credentials.password,
            channels = targetChannels
        )
        val savedProgramCount = epgRepository.savePrograms(
            programs = programs,
            expectedActivePlaylistId = playlist.id
        )
        if (savedProgramCount <= 0) {
            Timber.w("Xtream API EPG sync parsed no programs for playlist ${playlist.id}")
            return false
        }

        if (!commitXtreamEpgSource(playlist.id)) return false
        Timber.d("Xtream API EPG sync completed for playlist ${playlist.id}: $savedProgramCount programs")
        return true
    }

    /** Atomically commits the global XMLTV source only while [playlistId] still owns it. */
    private suspend fun commitXmltvEpgSource(playlistId: Long, epgUrl: String): Boolean =
        epgSourceMutex.withLock {
            if (!isActivePlaylist(playlistId)) return@withLock false

            val freshSettings = settingsDataStore.settings.first()
            if (freshSettings.epgUrl != epgUrl) {
                freshSettings.epgUrl
                    .trim()
                    .takeIf(String::isNotBlank)
                    ?.let(epgRepository::cancelScheduledSync)
                settingsDataStore.updateEpgUrl(epgUrl)
            }
            settingsDataStore.updateEpgLastSync(System.currentTimeMillis())
            if (freshSettings.epgAutoSync) {
                epgRepository.scheduleDailySync(epgUrl, playlistId)
            }
            true
        }

    /** Xtream API EPG has no durable XMLTV URL, so clear any previous XMLTV schedule safely. */
    private suspend fun commitXtreamEpgSource(playlistId: Long): Boolean =
        epgSourceMutex.withLock {
            if (!isActivePlaylist(playlistId)) return@withLock false

            val freshSettings = settingsDataStore.settings.first()
            freshSettings.epgUrl
                .trim()
                .takeIf(String::isNotBlank)
                ?.let(epgRepository::cancelScheduledSync)
            if (freshSettings.epgUrl.isNotBlank()) {
                settingsDataStore.updateEpgUrl("")
            }
            settingsDataStore.updateEpgLastSync(System.currentTimeMillis())
            true
        }

    private suspend fun hasCurrentEpgMatches(channels: List<Channel>): Boolean =
        epgRepository.getCurrentProgramsForChannels(channels).isNotEmpty()

    private suspend fun saveParseResult(
        result: com.idealplayer.app.data.parser.M3uParseResult,
        existingContent: ExistingContentState,
        syncTime: Long
    ) {
        insertChunked(result.channels.withPreservedChannelState(existingContent.channelByKey)) { channelDao.insertAll(it) }
        insertChunked(result.movies.withPreservedMovieState(existingContent.movieByKey, syncTime)) { movieDao.insertAll(it) }

        val series = result.series.withPreservedSeriesState(existingContent.seriesByKey, syncTime)
        if (series.isEmpty()) return

        insertChunked(series) { seriesDao.insertAll(it) }

        val playlistId = result.series.firstOrNull()?.playlistId
            ?: result.channels.firstOrNull()?.playlistId
            ?: result.movies.firstOrNull()?.playlistId
            ?: return

        val storedSeries = seriesDao.getByPlaylistSnapshot(playlistId).associateBy { it.name }
        val episodeEntities = buildM3uEpisodes(result.seriesEpisodes, storedSeries)
            .withPreservedEpisodeState(existingContent.episodeByKey)

        if (episodeEntities.isNotEmpty()) {
            insertChunked(episodeEntities) { episodeDao.insertAll(it) }

            val countsBySeriesId = episodeEntities.groupBy { it.seriesId }
            val updatedSeries = storedSeries.values.mapNotNull { series ->
                val episodes = countsBySeriesId[series.id] ?: return@mapNotNull null
                val seasonCount = episodes.map { it.seasonNumber }.distinct().count()
                series.copy(
                    seasonCount = seasonCount,
                    episodeCount = episodes.size
                )
            }

            if (updatedSeries.isNotEmpty()) {
                insertChunked(updatedSeries) { seriesDao.insertAll(it) }
            }
        }
    }

    private suspend fun loadExistingContentState(playlistId: Long): ExistingContentState {
        val channels = channelDao.getByPlaylistSnapshot(playlistId)
        val movies = movieDao.getByPlaylistSnapshot(playlistId)
        val series = seriesDao.getByPlaylistSnapshot(playlistId)
        val episodes = episodeDao.getByPlaylistSnapshot(playlistId)

        return ExistingContentState(
            channelByKey = channels.associateBy { it.stableContentKey() },
            movieByKey = movies.associateBy { it.stableContentKey() },
            seriesByKey = series.associateBy { it.stableContentKey() },
            episodeByKey = episodes.associateBy { it.stableContentKey() },
            episodes = episodes
        )
    }

    private suspend fun <T> insertChunked(items: List<T>, insert: suspend (List<T>) -> Unit) {
        items.chunked(INSERT_CHUNK_SIZE).forEach { chunk ->
            insert(chunk)
        }
    }

    private data class ExistingContentState(
        val channelByKey: Map<String, ChannelEntity>,
        val movieByKey: Map<String, MovieEntity>,
        val seriesByKey: Map<String, SeriesEntity>,
        val episodeByKey: Map<String, EpisodeEntity>,
        val episodes: List<EpisodeEntity>
    )

    private fun buildM3uEpisodes(
        seriesEpisodes: Map<String, List<com.idealplayer.app.data.parser.M3uEntry>>,
        storedSeries: Map<String, SeriesEntity>
    ): List<EpisodeEntity> {
        return buildList {
            seriesEpisodes.forEach { (seriesName, entries) ->
                val seriesEntity = storedSeries[seriesName] ?: return@forEach

                entries.forEachIndexed { index, entry ->
                    val parsedEpisode = parseEpisode(entry.name, seriesName, index)
                    add(
                        EpisodeEntity(
                            seriesId = seriesEntity.id,
                            seasonNumber = parsedEpisode.seasonNumber,
                            episodeNumber = parsedEpisode.episodeNumber,
                            name = parsedEpisode.displayName,
                            posterUrl = entry.logoUrl,
                            streamUrl = entry.url
                        )
                    )
                }
            }
        }
    }

    private fun parseEpisode(
        rawTitle: String,
        seriesName: String,
        index: Int
    ): ParsedEpisode {
        val normalizedTitle = rawTitle.trim()
        val patterns = listOf(
            Regex("""(?i)\bS(\d{1,2})\s*E(\d{1,3})\b"""),
            Regex("""(?i)\b(\d{1,2})x(\d{1,3})\b"""),
            Regex("""(?i)\bseason\s*(\d{1,2})\D+episode\s*(\d{1,3})\b""")
        )

        patterns.forEach { pattern ->
            val match = pattern.find(normalizedTitle) ?: return@forEach
            val seasonNumber = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            val episodeNumber = match.groupValues.getOrNull(2)?.toIntOrNull() ?: (index + 1)
            val cleanedTitle = normalizedTitle
                .replace(seriesName, "", ignoreCase = true)
                .replace(pattern, "")
                .replace("""^[\s\-_:|]+|[\s\-_:|]+$""".toRegex(), "")
                .ifBlank { "Episode $episodeNumber" }

            return ParsedEpisode(
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                displayName = cleanedTitle
            )
        }

        return ParsedEpisode(
            seasonNumber = 1,
            episodeNumber = index + 1,
            displayName = normalizedTitle
                .replace(seriesName, "", ignoreCase = true)
                .replace("""^[\s\-_:|]+|[\s\-_:|]+$""".toRegex(), "")
                .ifBlank { "Episode ${index + 1}" }
        )
    }

    private data class ParsedEpisode(
        val seasonNumber: Int,
        val episodeNumber: Int,
        val displayName: String
    )

    private companion object {
        const val INSERT_CHUNK_SIZE = 500
        const val XTREAM_SHORT_EPG_CHANNEL_LIMIT = 160
        const val MEBIBYTE = 1024L * 1024L
        // Direct URL/file imports can be larger than a Connected setup payload, but parsing
        // retains content entities in memory, so imports still need a clear upper bound.
        const val MAX_PLAYLIST_BYTES = 10L * MEBIBYTE
    }
}

internal fun resolveM3uEpgUrl(epgUrl: String, playlistUrl: String): String {
    val trimmed = epgUrl.trim()
    if (trimmed.isBlank()) {
        return buildXtreamEpgUrlFromM3uPlaylistUrl(playlistUrl)
    }
    return resolveExplicitM3uEpgUrl(trimmed, playlistUrl)
}

internal fun resolveM3uEpgUrls(
    epgUrls: List<String>,
    playlistUrl: String,
    streamUrls: List<String>
): List<String> =
    buildList {
        epgUrls.forEach { epgUrl ->
            add(resolveExplicitM3uEpgUrl(epgUrl, playlistUrl))
        }
        add(buildXtreamEpgUrlFromM3uPlaylistUrl(playlistUrl))
        streamUrls.forEach { streamUrl ->
            add(buildXtreamEpgUrlFromStreamUrl(streamUrl))
        }
    }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

private fun String.splitEpgSourceUrls(): List<String> =
    lineSequence()
        .flatMap { line -> line.split(',', ';').asSequence() }
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()

private fun resolveExplicitM3uEpgUrl(epgUrl: String, playlistUrl: String): String {
    val trimmed = epgUrl.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return trimmed
    }

    return playlistUrl
        .toHttpUrlOrNull()
        ?.resolve(trimmed)
        ?.toString()
        ?: trimmed
}

internal fun buildXtreamEpgUrlFromM3uPlaylistUrl(playlistUrl: String): String {
    val httpUrl = playlistUrl.toHttpUrlOrNull() ?: return ""
    val username = httpUrl.queryParameter("username")?.takeIf(String::isNotBlank)
        ?: httpUrl.queryParameter("user")?.takeIf(String::isNotBlank) ?: return ""
    val password = httpUrl.queryParameter("password")?.takeIf(String::isNotBlank)
        ?: httpUrl.queryParameter("pass")?.takeIf(String::isNotBlank) ?: return ""

    return httpUrl.newBuilder()
        .encodedPath("/xmltv.php")
        .query(null)
        .addQueryParameter("username", username)
        .addQueryParameter("password", password)
        .build()
        .toString()
}

internal fun buildXtreamEpgUrlFromStreamUrl(streamUrl: String): String {
    val httpUrl = parsePlaybackSource(streamUrl).url.toHttpUrlOrNull() ?: return ""
    var username = httpUrl.queryParameter("username")?.takeIf(String::isNotBlank)
        ?: httpUrl.queryParameter("user")?.takeIf(String::isNotBlank)
    var password = httpUrl.queryParameter("password")?.takeIf(String::isNotBlank)
        ?: httpUrl.queryParameter("pass")?.takeIf(String::isNotBlank)

    if (username.isNullOrBlank() || password.isNullOrBlank()) {
        val pathSegments = httpUrl.pathSegments
        val credentialsStartIndex = pathSegments.indexOfFirst { segment ->
            segment.equals("live", ignoreCase = true) ||
                segment.equals("movie", ignoreCase = true) ||
                segment.equals("series", ignoreCase = true)
        } + 1
        if (credentialsStartIndex <= 0) return ""

        username = pathSegments.getOrNull(credentialsStartIndex)?.takeIf(String::isNotBlank) ?: return ""
        password = pathSegments.getOrNull(credentialsStartIndex + 1)?.takeIf(String::isNotBlank) ?: return ""
    }

    return httpUrl.newBuilder()
        .encodedPath("/xmltv.php")
        .query(null)
        .addQueryParameter("username", username)
        .addQueryParameter("password", password)
        .build()
        .toString()
}

internal data class XtreamCredentials(
    val serverUrl: String,
    val username: String,
    val password: String
)

internal fun resolveXtreamCredentials(
    playlist: Playlist,
    streamUrls: List<String>
): XtreamCredentials? {
    if (
        playlist.type == PlaylistType.XTREAM_CODES &&
        playlist.serverUrl.isNotBlank() &&
        playlist.username.isNotBlank() &&
        playlist.password.isNotBlank()
    ) {
        return XtreamCredentials(
            serverUrl = normalizeXtreamServerUrl(playlist.serverUrl),
            username = playlist.username,
            password = playlist.password
        )
    }

    buildXtreamCredentialsFromM3uPlaylistUrl(playlist.url)?.let { return it }
    return streamUrls.asSequence()
        .mapNotNull(::buildXtreamCredentialsFromStreamUrl)
        .firstOrNull()
}

internal fun buildXtreamCredentialsFromM3uPlaylistUrl(playlistUrl: String): XtreamCredentials? {
    val httpUrl = playlistUrl.toHttpUrlOrNull() ?: return null
    val username = httpUrl.queryParameter("username")?.takeIf(String::isNotBlank)
        ?: httpUrl.queryParameter("user")?.takeIf(String::isNotBlank) ?: return null
    val password = httpUrl.queryParameter("password")?.takeIf(String::isNotBlank)
        ?: httpUrl.queryParameter("pass")?.takeIf(String::isNotBlank) ?: return null

    return XtreamCredentials(
        serverUrl = httpUrl.toXtreamServerUrl(),
        username = username,
        password = password
    )
}

internal fun buildXtreamCredentialsFromStreamUrl(streamUrl: String): XtreamCredentials? {
    val httpUrl = parsePlaybackSource(streamUrl).url.toHttpUrlOrNull() ?: return null
    var username = httpUrl.queryParameter("username")?.takeIf(String::isNotBlank)
        ?: httpUrl.queryParameter("user")?.takeIf(String::isNotBlank)
    var password = httpUrl.queryParameter("password")?.takeIf(String::isNotBlank)
        ?: httpUrl.queryParameter("pass")?.takeIf(String::isNotBlank)

    if (username.isNullOrBlank() || password.isNullOrBlank()) {
        val pathSegments = httpUrl.pathSegments
        val credentialsStartIndex = pathSegments.indexOfFirst { segment ->
            segment.equals("live", ignoreCase = true) ||
                segment.equals("movie", ignoreCase = true) ||
                segment.equals("series", ignoreCase = true)
        } + 1
        if (credentialsStartIndex <= 0) return null

        username = pathSegments.getOrNull(credentialsStartIndex)?.takeIf(String::isNotBlank) ?: return null
        password = pathSegments.getOrNull(credentialsStartIndex + 1)?.takeIf(String::isNotBlank) ?: return null
    }

    return XtreamCredentials(
        serverUrl = httpUrl.toXtreamServerUrl(),
        username = username,
        password = password
    )
}

private fun buildXtreamEpgUrl(playlist: Playlist): String {
    if (playlist.serverUrl.isBlank() || playlist.username.isBlank() || playlist.password.isBlank()) {
        return ""
    }

    val base = playlist.serverUrl.trimEnd('/')
    return base.toHttpUrlOrNull()
        ?.newBuilder()
        ?.addPathSegment("xmltv.php")
        ?.addQueryParameter("username", playlist.username)
        ?.addQueryParameter("password", playlist.password)
        ?.build()
        ?.toString()
        ?: "$base/xmltv.php?username=${playlist.username}&password=${playlist.password}"
}

private sealed interface PlaylistSyncContent {
    data class M3u(val result: com.idealplayer.app.data.parser.M3uParseResult) : PlaylistSyncContent
    data class Xtream(val result: XtreamContentResult) : PlaylistSyncContent
}

private enum class EpgDiscoveryType {
    M3U,
    XTREAM
}

private data class EpgDiscoverySeed(
    val type: EpgDiscoveryType,
    val channels: List<Channel>,
    val epgUrls: List<String>,
    val streamUrls: List<String>
)

private fun PlaylistSyncContent.toEpgDiscoverySeed(): EpgDiscoverySeed {
    return when (this) {
        is PlaylistSyncContent.M3u -> EpgDiscoverySeed(
            type = EpgDiscoveryType.M3U,
            channels = result.channels.map { it.toModel() },
            epgUrls = result.epgUrls,
            streamUrls = result.channels.map { it.streamUrl }
        )

        is PlaylistSyncContent.Xtream -> EpgDiscoverySeed(
            type = EpgDiscoveryType.XTREAM,
            channels = result.channels.map { it.toModel() },
            epgUrls = emptyList(),
            streamUrls = result.channels.map { it.streamUrl }
        )
    }
}

private fun PlaylistSyncContent.requireNonEmpty() {
    val hasContent = when (this) {
        is PlaylistSyncContent.M3u ->
            result.channels.isNotEmpty() || result.movies.isNotEmpty() || result.series.isNotEmpty()
        is PlaylistSyncContent.Xtream ->
            result.channels.isNotEmpty() || result.movies.isNotEmpty() || result.series.isNotEmpty()
    }
    if (!hasContent) {
        throw IllegalStateException("Playlist sync returned no playable content")
    }
}

private fun Playlist.normalizedForSync(): Playlist {
    return when (type) {
        PlaylistType.M3U_URL -> copy(url = normalizeHttpUrl(url))
        PlaylistType.XTREAM_CODES -> copy(serverUrl = normalizeXtreamServerUrl(serverUrl))
        PlaylistType.M3U_FILE -> this
    }
}

private fun normalizeHttpUrl(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return trimmed
    return if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "http://$trimmed"
    }
}

private fun normalizeXtreamServerUrl(value: String): String {
    val normalized = normalizeHttpUrl(value).trimEnd('/')
    return if (normalized.endsWith("/player_api.php", ignoreCase = true)) {
        normalized.dropLast("/player_api.php".length)
    } else {
        normalized
    }
}

private fun okhttp3.HttpUrl.toXtreamServerUrl(): String =
    newBuilder()
        .encodedPath("/")
        .query(null)
        .fragment(null)
        .build()
        .toString()
        .trimEnd('/')
