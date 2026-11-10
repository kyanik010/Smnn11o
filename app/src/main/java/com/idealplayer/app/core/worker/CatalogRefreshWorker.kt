package com.idealplayer.app.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.idealplayer.app.core.common.Resource
import com.idealplayer.app.core.common.DeviceUtils
import com.idealplayer.app.core.common.rethrowIfCancellation
import com.idealplayer.app.core.database.PlaylistDao
import com.idealplayer.app.core.database.toModel
import com.idealplayer.app.data.repository.ContentRepository
import com.idealplayer.app.data.repository.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Refreshes the active user-provided catalog whenever the app returns to the foreground, then
 * enriches a rotating metadata batch. The cursor is persisted separately from catalog rows so
 * an unmatchable title never prevents later movies or series from being processed.
 */
@HiltWorker
class CatalogRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val playlistDao: PlaylistDao,
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val active = playlistDao.getActive()?.toModel() ?: return Result.success()
        return try {
            val syncResult = playlistRepository.syncPlaylist(active)
            val hasContent = playlistRepository.hasSyncedContent(active.id)
            if (syncResult is Resource.Error && !hasContent) {
                Timber.w(
                    "Catalog refresh failed with no cached content for playlist %d: %s",
                    active.id,
                    syncResult.throwable?.javaClass?.simpleName ?: "Unknown"
                )
                return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
            }

            val preferences = applicationContext.getSharedPreferences(
                CURSOR_PREFERENCES,
                Context.MODE_PRIVATE
            )
            val movieKey = "$MOVIE_CURSOR_PREFIX${active.id}"
            val seriesKey = "$SERIES_CURSOR_PREFIX${active.id}"
            val refresh = contentRepository.enrichIncompleteCatalogMetadata(
                playlistId = active.id,
                movieOffset = preferences.getInt(movieKey, 0),
                seriesOffset = preferences.getInt(seriesKey, 0)
            )
            preferences.edit()
                .putInt(movieKey, refresh.nextMovieOffset)
                .putInt(seriesKey, refresh.nextSeriesOffset)
                .apply()

            Timber.i(
                "Catalog refreshed: movies=%d series=%d metadataUpdated=%d",
                refresh.movieProcessed,
                refresh.seriesProcessed,
                refresh.updated
            )
            Result.success()
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            Timber.w(
                "Catalog foreground refresh failed for playlist %d: %s",
                active.id,
                error.javaClass.simpleName
            )
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "active_catalog_foreground_refresh"
        private const val CURSOR_PREFERENCES = "catalog_metadata_refresh"
        private const val MOVIE_CURSOR_PREFIX = "movie_cursor_"
        private const val SERIES_CURSOR_PREFIX = "series_cursor_"
        private const val MAX_RETRIES = 2

        fun enqueueOnAppEntry(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = OneTimeWorkRequestBuilder<CatalogRefreshWorker>()
                .setConstraints(constraints)
                .setInitialDelay(
                    catalogRefreshInitialDelaySeconds(
                        isTv = DeviceUtils.resolveUiMode(context).isTv
                    ),
                    TimeUnit.SECONDS
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}

internal fun catalogRefreshInitialDelaySeconds(isTv: Boolean): Long =
    if (isTv) 90L else 10L
