package com.idealplayer.app.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.idealplayer.app.core.database.ChannelDao
import com.idealplayer.app.core.common.SensitiveLog
import com.idealplayer.app.core.common.rethrowIfCancellation
import com.idealplayer.app.core.database.PlaylistDao
import com.idealplayer.app.core.database.toModel
import com.idealplayer.app.core.datastore.SettingsDataStore
import com.idealplayer.app.data.parser.buildEpgChannelIdMap
import com.idealplayer.app.data.repository.EpgRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** A periodic EPG request is valid only for the app-wide, currently active source. */
internal fun shouldRunScheduledEpgSync(
    requestedUrl: String,
    configuredUrl: String,
    autoSyncEnabled: Boolean,
    activePlaylistId: Long?,
    ownerPlaylistId: Long
): Boolean = autoSyncEnabled &&
    activePlaylistId != null &&
    activePlaylistId == ownerPlaylistId &&
    requestedUrl.trim().isNotBlank() &&
    requestedUrl.trim() == configuredUrl.trim()

/** Old periodic requests lacked an owner; never reinterpret them as a current manual refresh. */
internal fun isLegacyOwnerlessScheduledEpgWork(
    isManualRun: Boolean,
    requiresActiveSource: Boolean,
    ownerPlaylistId: Long
): Boolean = !isManualRun &&
    (!requiresActiveSource || ownerPlaylistId == EpgSyncWorker.NO_PLAYLIST_ID)

/**
 * WorkManager Worker that downloads and parses the EPG (XMLTV) file.
 *
 * - Runs once a day (PeriodicWorkRequest, 24h interval)
 * - Requires network
 * - On success: deletes old programs, inserts fresh data
 * - Input: EPG_URL (String)
 */
@HiltWorker
class EpgSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val settingsDataStore: SettingsDataStore,
    private val epgRepository: EpgRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val epgUrl = inputData.getString(KEY_EPG_URL)
        if (epgUrl.isNullOrBlank()) {
            Timber.w("EpgSyncWorker: no EPG URL provided, skipping")
            return Result.success()
        }
        val requireActiveSource = inputData.getBoolean(KEY_REQUIRE_ACTIVE_SOURCE, false)
        val isManualRun = inputData.getBoolean(KEY_MANUAL_RUN, false)
        val ownerPlaylistId = inputData.getLong(KEY_OWNER_PLAYLIST_ID, NO_PLAYLIST_ID)
        if (isLegacyOwnerlessScheduledEpgWork(isManualRun, requireActiveSource, ownerPlaylistId)) {
            // Pre-owner releases scheduled periodic work with URL-only input. Treat it as stale
            // on upgrade; otherwise it could map an old source into whichever playlist is now
            // active. New one-time refreshes set KEY_MANUAL_RUN explicitly.
            cancel(applicationContext, epgUrl)
            Timber.d("EpgSyncWorker: cancelled legacy ownerless schedule")
            return Result.success()
        }
        val activePlaylist = playlistDao.getActive()
        if (activePlaylist == null) {
            if (requireActiveSource) cancel(applicationContext, epgUrl)
            Timber.d("EpgSyncWorker: no active playlist, skipping")
            return Result.success()
        }
        if (requireActiveSource) {
            val settings = settingsDataStore.settings.first()
            if (
                !shouldRunScheduledEpgSync(
                    requestedUrl = epgUrl,
                    configuredUrl = settings.epgUrl,
                    autoSyncEnabled = settings.epgAutoSync,
                    activePlaylistId = activePlaylist.id,
                    ownerPlaylistId = ownerPlaylistId
                )
            ) {
                // A previous playlist/source was deactivated or removed after this periodic
                // request was queued. Cancel its known work names so it cannot wake again.
                cancel(applicationContext, epgUrl)
                Timber.d("EpgSyncWorker: cancelled stale scheduled source")
                return Result.success()
            }
        }
        return try {
            // Snapshot the owner before networking. EpgRepository checks this id again inside
            // its Room transaction, so an activation that happens while downloading cannot
            // persist A's channel mapping into newly-active playlist B.
            val channelIdMap = buildChannelIdMap(activePlaylist.id)
            val savedProgramCount = epgRepository.fetchAndSave(
                url = epgUrl,
                channelIdMap = channelIdMap.takeIf { it.isNotEmpty() },
                expectedActivePlaylistId = activePlaylist.id
            ).getOrElse { error -> throw error }
            if (savedProgramCount <= 0) {
                if (playlistDao.getActive()?.id != activePlaylist.id) {
                    Timber.d("EpgSyncWorker: playlist changed before EPG persistence")
                } else {
                    Timber.w("EpgSyncWorker: no programs parsed")
                }
                return Result.success()
            }
            Timber.d("EpgSyncWorker: inserted $savedProgramCount programs")
            Result.success()
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            // Network/parser exceptions can retain the user-provided EPG URL.
            Timber.e("EpgSyncWorker failed: %s", e.javaClass.simpleName)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private suspend fun buildChannelIdMap(playlistId: Long): Map<String, String> {
        val channels = channelDao.getByPlaylistSnapshot(playlistId).map { it.toModel() }
        return buildEpgChannelIdMap(channels)
    }

    companion object {
        const val KEY_EPG_URL = "epg_url"
        const val KEY_REQUIRE_ACTIVE_SOURCE = "require_active_source"
        const val KEY_MANUAL_RUN = "manual_run"
        const val KEY_OWNER_PLAYLIST_ID = "owner_playlist_id"
        const val WORK_NAME_PREFIX = "epg_sync_"
        const val NO_PLAYLIST_ID = -1L

        /**
         * Enqueue a daily EPG sync for the given [epgUrl].
         * Uses unique work name so only one sync runs per URL.
         */
        fun enqueue(context: Context, epgUrl: String, ownerPlaylistId: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<EpgSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        KEY_EPG_URL to epgUrl,
                        KEY_REQUIRE_ACTIVE_SOURCE to true,
                        KEY_OWNER_PLAYLIST_ID to ownerPlaylistId
                    )
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.MINUTES
                )
                .build()

            val workManager = WorkManager.getInstance(context)
            // Older releases used String.hashCode(), which can collide for different URLs.
            // Clear that legacy name once while moving to an opaque cryptographic identifier.
            workManager.cancelLegacyWork(epgUrl)
            workManager.enqueueUniquePeriodicWork(
                workNameFor(epgUrl),
                // The same XMLTV URL can legitimately become owned by a different playlist.
                // UPDATE refreshes the owner input instead of retaining a stale A-owned request.
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Timber.d("EpgSyncWorker: enqueued daily sync for %s", SensitiveLog.redactUrl(epgUrl))
        }

        /**
         * Run an immediate one-time EPG sync (e.g. on first playlist load).
         */
        fun runNow(context: Context, epgUrl: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<EpgSyncWorker>()
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        KEY_EPG_URL to epgUrl,
                        KEY_MANUAL_RUN to true
                    )
                )
                .build()

            WorkManager.getInstance(context).enqueue(request)
            Timber.d("EpgSyncWorker: one-time sync enqueued for %s", SensitiveLog.redactUrl(epgUrl))
        }

        fun cancel(context: Context, epgUrl: String) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(workNameFor(epgUrl))
                cancelLegacyWork(epgUrl)
            }
        }

        /** Stable, opaque WorkManager name that cannot collide like String.hashCode(). */
        internal fun workNameFor(epgUrl: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(epgUrl.trim().toByteArray(StandardCharsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
            return "$WORK_NAME_PREFIX$digest"
        }

        private fun WorkManager.cancelLegacyWork(epgUrl: String) {
            cancelUniqueWork(legacyWorkNameFor(epgUrl))
        }

        private fun legacyWorkNameFor(epgUrl: String): String =
            "$WORK_NAME_PREFIX${epgUrl.hashCode()}"

    }
}
