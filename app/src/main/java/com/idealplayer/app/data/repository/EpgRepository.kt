package com.idealplayer.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.idealplayer.app.core.common.SensitiveLog
import com.idealplayer.app.core.common.isUsableArtworkUrl
import com.idealplayer.app.core.common.rethrowIfCancellation
import com.idealplayer.app.core.database.ChannelDao
import com.idealplayer.app.core.database.EpgDao
import com.idealplayer.app.core.database.EpgProgramEntity
import com.idealplayer.app.core.database.IdealPlayerDatabase
import com.idealplayer.app.core.database.PlaylistDao
import com.idealplayer.app.core.database.toModel
import com.idealplayer.app.core.model.Channel
import com.idealplayer.app.core.worker.EpgSyncWorker
import com.idealplayer.app.data.parser.EpgProgram
import com.idealplayer.app.data.parser.XmltvParser
import com.idealplayer.app.data.parser.asXmltvInputStream
import com.idealplayer.app.data.parser.buildEpgChannelIdMap
import com.idealplayer.app.data.parser.epgLookupKeys
import com.idealplayer.app.data.parser.epgLookupKeysForChannel
import com.idealplayer.app.data.parser.toUiModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpgRepository @Inject constructor(
    private val database: IdealPlayerDatabase,
    private val epgDao: EpgDao,
    private val channelDao: ChannelDao,
    private val playlistDao: PlaylistDao,
    private val xmltvParser: XmltvParser,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    private companion object {
        private const val MAX_SQL_BIND_ARGS = 800
        private const val EPG_HISTORY_RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
    }

    /**
     * Returns a live flow of current and upcoming programs for [channelId].
     * Automatically refreshed when DB changes.
     */
    fun getProgramsForChannel(channelId: String): Flow<List<EpgProgram>> =
        epgDao.getPrograms(channelId, System.currentTimeMillis())
            .map { list -> list.map { it.toUiModel() } }

    /**
     * Returns the currently-airing program for [channelId], or null.
     */
    suspend fun getCurrentProgram(channelId: String): EpgProgram? =
        epgDao.getCurrentProgram(channelId, System.currentTimeMillis())?.toUiModel()

    suspend fun getCurrentProgram(channel: Channel): EpgProgram? =
        withContext(Dispatchers.IO) {
            val candidates = epgLookupKeysForChannel(channel)
            queryCurrentProgramForCandidates(candidates, System.currentTimeMillis())?.toUiModel()
        }

    /**
     * Returns the next program after [nowMs] for [channelId], or null.
     */
    suspend fun getNextProgram(channelId: String, nowMs: Long = System.currentTimeMillis()): EpgProgram? =
        withContext(Dispatchers.IO) {
            epgDao.getPrograms(channelId, nowMs).first()
                .firstOrNull { it.startTime > nowMs }
                ?.toUiModel()
        }

    suspend fun getNextProgram(channel: Channel, nowMs: Long = System.currentTimeMillis()): EpgProgram? =
        withContext(Dispatchers.IO) {
            val candidates = epgLookupKeysForChannel(channel)
            queryUpcomingProgramsForCandidates(candidates, nowMs)
                .filter { it.startTime > nowMs }
                .firstProgramForCandidates(candidates)
                ?.toUiModel()
        }

    suspend fun getProgramsForChannel(
        channel: Channel,
        nowMs: Long = System.currentTimeMillis(),
        limit: Int = 24
    ): List<EpgProgram> = withContext(Dispatchers.IO) {
        val candidates = epgLookupKeysForChannel(channel)
        val programsByLookupId = queryUpcomingProgramsForCandidates(candidates, nowMs, limit)
            .groupBy(EpgProgramEntity::channelId)

        candidates
            .firstNotNullOfOrNull { candidate ->
                programsByLookupId[candidate]?.takeIf { it.isNotEmpty() }
            }
            .orEmpty()
            .distinctBy { program -> "${program.channelId}|${program.startTime}|${program.title}" }
            .sortedBy(EpgProgramEntity::startTime)
            .take(limit)
            .map(EpgProgramEntity::toUiModel)
    }

    /**
     * Fetch and parse XMLTV from [url], insert into DB.
     * Called manually or from [EpgSyncWorker].
     */
    suspend fun fetchAndSave(
        url: String,
        channelIdMap: Map<String, String>? = null,
        expectedActivePlaylistId: Long? = null
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                Timber.d("EpgRepository: fetching %s", SensitiveLog.redactUrl(url))
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "IdealPlayer/Android")
                    .build()
                val parsed = okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}"))
                    }
                    val body = response.body
                        ?: return@withContext Result.failure(Exception("Empty body"))
                    xmltvParser.parseDocument(
                        body.byteStream().asXmltvInputStream(url, body.contentType()?.toString()),
                        channelIdMap
                    )
                }
                val savedCount = persistPrograms(parsed.programs, expectedActivePlaylistId)
                val repairedChannelLogos = persistChannelArtwork(
                    channelIcons = parsed.channelIcons,
                    expectedActivePlaylistId = expectedActivePlaylistId
                )
                if (parsed.programs.isNotEmpty() && savedCount == 0 && expectedActivePlaylistId != null) {
                    Timber.d("EpgRepository: active playlist changed before EPG persistence")
                } else {
                    Timber.d(
                        "EpgRepository: saved %d programs and repaired %d channel logos",
                        savedCount,
                        repairedChannelLogos
                    )
                }
                Result.success(savedCount)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e("EpgRepository fetch failed: %s", e.javaClass.simpleName)
                Result.failure(e)
            }
        }

    suspend fun savePrograms(
        programs: List<EpgProgramEntity>,
        expectedActivePlaylistId: Long? = null
    ): Int =
        withContext(Dispatchers.IO) {
            val savedCount = persistPrograms(programs, expectedActivePlaylistId)
            Timber.d("EpgRepository: saved $savedCount Xtream EPG programs")
            savedCount
        }

    /**
     * EPG rows are global, so persistence must be serialized with playlist activation. Checking
     * the expected active owner inside the same Room transaction prevents an in-flight source
     * from writing channel mappings after the user switches playlists.
     */
    private suspend fun persistPrograms(
        programs: List<EpgProgramEntity>,
        expectedActivePlaylistId: Long?
    ): Int {
        if (programs.isEmpty()) return 0
        return database.withTransaction {
            if (
                expectedActivePlaylistId != null &&
                playlistDao.getActive()?.id != expectedActivePlaylistId
            ) {
                return@withTransaction 0
            }
            epgDao.deleteOld(System.currentTimeMillis() - EPG_HISTORY_RETENTION_MS)
            programs.chunked(500).forEach { epgDao.insertAll(it) }
            programs.size
        }
    }

    /**
     * Uses only artwork declared by the user's XMLTV source, then shares it with quality
     * aliases of the same station. Existing usable provider logos are never overwritten.
     */
    private suspend fun persistChannelArtwork(
        channelIcons: Map<String, String>,
        expectedActivePlaylistId: Long?
    ): Int {
        if (channelIcons.isEmpty()) return 0
        return database.withTransaction {
            val activePlaylistId = playlistDao.getActive()?.id ?: return@withTransaction 0
            if (
                expectedActivePlaylistId != null &&
                activePlaylistId != expectedActivePlaylistId
            ) {
                return@withTransaction 0
            }

            val iconByLookupKey = linkedMapOf<String, String>()
            channelIcons.forEach { (channelId, iconUrl) ->
                if (isUsableArtworkUrl(iconUrl)) {
                    epgLookupKeys(channelId).forEach { key ->
                        iconByLookupKey.putIfAbsent(key, iconUrl)
                    }
                }
            }
            if (iconByLookupKey.isEmpty()) return@withTransaction 0

            val current = channelDao.getByPlaylistSnapshot(activePlaylistId)
            val withDirectEpgArtwork = current.map { entity ->
                if (isUsableArtworkUrl(entity.logoUrl)) {
                    entity
                } else {
                    val inherited = epgLookupKeysForChannel(entity.toModel())
                        .firstNotNullOfOrNull(iconByLookupKey::get)
                    if (inherited == null) entity else entity.copy(logoUrl = inherited)
                }
            }
            val resolved = withDirectEpgArtwork.withInheritedChannelArtwork()
            val changed = resolved.zip(current)
                .mapNotNull { (updated, previous) -> updated.takeIf { it.logoUrl != previous.logoUrl } }
            if (changed.isNotEmpty()) {
                for (chunk in changed.chunked(500)) {
                    channelDao.insertAll(chunk)
                }
            }
            changed.size
        }
    }

    /**
     * Schedule daily background EPG sync via WorkManager.
     */
    fun scheduleDailySync(epgUrl: String, ownerPlaylistId: Long) {
        EpgSyncWorker.enqueue(context, epgUrl, ownerPlaylistId)
    }

    fun cancelScheduledSync(epgUrl: String) {
        EpgSyncWorker.cancel(context, epgUrl)
    }

    /**
     * Trigger an immediate EPG refresh.
     */
    fun syncNow(epgUrl: String) {
        EpgSyncWorker.runNow(context, epgUrl)
    }

    /**
     * Delete EPG data older than the catch-up/guide history window.
     */
    suspend fun pruneOld() {
        epgDao.deleteOld(System.currentTimeMillis() - EPG_HISTORY_RETENTION_MS)
    }

    /**
     * Returns a map of app channel id to the currently-airing program.
     * The lookup accepts tvg-id, XMLTV id variants, channel names, and stream ids.
     */
    suspend fun getCurrentProgramsForChannels(
        channels: List<Channel>
    ): Map<Long, EpgProgram> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val candidatesByChannelId = channels
            .distinctBy(Channel::id)
            .associate { channel -> channel.id to epgLookupKeysForChannel(channel) }
            .filterValues { it.isNotEmpty() }

        if (candidatesByChannelId.isEmpty()) {
            return@withContext emptyMap()
        }

        val programsByLookupId = candidatesByChannelId.values
            .flatten()
            .distinct()
            .chunked(MAX_SQL_BIND_ARGS)
            .flatMap { ids -> epgDao.getCurrentProgramsForIds(ids, now) }
            .groupBy(EpgProgramEntity::channelId)

        candidatesByChannelId.mapNotNull { (channelId, candidates) ->
            val program = candidates
                .firstNotNullOfOrNull { candidate -> programsByLookupId[candidate]?.firstOrNull() }
                ?.toUiModel()
            program?.let { channelId to it }
        }.toMap()
    }

    suspend fun getGuideProgramsForChannels(
        channels: List<Channel>,
        windowStart: Long,
        windowEnd: Long
    ): Map<Long, List<EpgProgram>> = withContext(Dispatchers.IO) {
        if (channels.isEmpty() || windowEnd <= windowStart) return@withContext emptyMap()

        val candidatesByChannelId = channels
            .distinctBy(Channel::id)
            .associate { channel -> channel.id to epgLookupKeysForChannel(channel) }
            .filterValues(List<String>::isNotEmpty)
        if (candidatesByChannelId.isEmpty()) return@withContext emptyMap()

        val programsByLookupId = candidatesByChannelId.values
            .flatten()
            .distinct()
            .chunked(MAX_SQL_BIND_ARGS)
            .flatMap { ids ->
                epgDao.getProgramsInWindowForIds(ids, windowStart, windowEnd)
            }
            .groupBy(EpgProgramEntity::channelId)

        candidatesByChannelId.mapValues { (_, candidates) ->
            selectGuideProgramsForCandidates(programsByLookupId, candidates)
                .map(EpgProgramEntity::toUiModel)
        }
    }

    /**
     * M3U/Xtream channel id'lerini XMLTV channel id'leriyle eşleştiren map oluşturur.
     * Önce tam eşleşme, sonra normalize edilmiş fuzzy eşleşme dener.
     */
    suspend fun buildChannelIdMap(channels: List<Channel>): Map<String, String> {
        return buildEpgChannelIdMap(channels)
    }

    private suspend fun queryCurrentProgramForCandidates(
        candidates: List<String>,
        nowMs: Long
    ): EpgProgramEntity? {
        if (candidates.isEmpty()) return null
        return candidates
            .chunked(MAX_SQL_BIND_ARGS)
            .flatMap { ids -> epgDao.getCurrentProgramsForIds(ids, nowMs) }
            .firstProgramForCandidates(candidates)
    }

    private suspend fun queryUpcomingProgramsForCandidates(
        candidates: List<String>,
        nowMs: Long,
        limit: Int = 10
    ): List<EpgProgramEntity> {
        if (candidates.isEmpty()) return emptyList()
        return candidates
            .chunked(MAX_SQL_BIND_ARGS)
            .flatMap { ids -> epgDao.getUpcomingProgramsForIds(ids, nowMs, limit) }
    }
}

internal fun selectGuideProgramsForCandidates(
    programsByLookupId: Map<String, List<EpgProgramEntity>>,
    candidates: List<String>
): List<EpgProgramEntity> = candidates
    .firstNotNullOfOrNull { candidate -> programsByLookupId[candidate]?.takeIf(List<EpgProgramEntity>::isNotEmpty) }
    .orEmpty()
    .distinctBy { program -> "${program.channelId}|${program.startTime}|${program.title}" }
    .sortedBy(EpgProgramEntity::startTime)

private fun List<EpgProgramEntity>.firstProgramForCandidates(
    candidates: List<String>
): EpgProgramEntity? {
    if (isEmpty()) return null

    val programsByLookupId = groupBy(EpgProgramEntity::channelId)
    candidates.forEach { candidate ->
        programsByLookupId[candidate]?.firstOrNull()?.let { return it }
    }
    return null
}
