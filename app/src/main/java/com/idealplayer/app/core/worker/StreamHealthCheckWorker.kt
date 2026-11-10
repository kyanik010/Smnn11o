package com.idealplayer.app.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.idealplayer.app.core.common.SensitiveLog
import com.idealplayer.app.core.player.parsePlaybackSource
import com.idealplayer.app.core.common.rethrowIfCancellation
import com.idealplayer.app.core.database.ChannelDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Health classification for a single stream probe.
 *
 * Only [ALIVE] and [DEAD] are acted upon; [UNKNOWN] (transient network errors, timeouts,
 * ambiguous 4xx/5xx) intentionally leaves the channel's stored `isOnline` state untouched,
 * so a flaky probe never hides a working channel.
 */
internal enum class StreamHealth { ALIVE, DEAD, UNKNOWN }

/**
 * Map an HTTP status code to a [StreamHealth] verdict. Conservative by design:
 * a stream is only marked dead on an unambiguous "gone" status. Auth/method/rate-limit
 * responses prove the stream exists, and 5xx / other 4xx are treated as unknown because
 * IPTV edges routinely return them transiently.
 */
internal fun classifyStreamHealth(code: Int): StreamHealth = when {
    code in 200..399 -> StreamHealth.ALIVE
    code == 401 || code == 403 || code == 405 || code == 429 -> StreamHealth.ALIVE
    code == 404 || code == 410 -> StreamHealth.DEAD
    else -> StreamHealth.UNKNOWN
}

/** Returns the next page offset when a bounded health-check run has more work to do. */
internal fun nextStreamHealthOffset(
    startOffset: Int,
    checkedChannels: Int,
    totalChannels: Int
): Int? {
    if (startOffset < 0 || checkedChannels <= 0 || totalChannels <= 0) return null
    val nextOffset = (startOffset + checkedChannels).coerceAtMost(totalChannels)
    return nextOffset.takeIf { it < totalChannels }
}

/**
 * Checks stream health for the channels in a playlist.
 *
 * - Runs once after playlist sync (currently not wired; see [runForPlaylist]).
 * - Marks unreachable streams as [com.idealplayer.app.core.database.ChannelEntity.isOnline] = false,
 *   and revives recovered streams, but only on a definitive verdict (see [classifyStreamHealth]).
 * - Uses a ranged GET (many IPTV servers reject HEAD) with a short timeout.
 * - Channels are probed in small concurrent batches with an inter-batch delay, capped at
 *   [MAX_CHANNELS_PER_RUN] to bound runtime/battery on very large playlists.
 */
@HiltWorker
class StreamHealthCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val channelDao: ChannelDao,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(appContext, params) {

    // Short-timeout client for health checks.
    private val pingClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun doWork(): Result {
        val playlistId = inputData.getLong(KEY_PLAYLIST_ID, -1L)
        if (playlistId < 0) return Result.success()
        val startOffset = inputData.getInt(KEY_START_OFFSET, 0).coerceAtLeast(0)

        return try {
            val totalChannels = channelDao.countByPlaylist(playlistId)
            Timber.d(
                "StreamHealthCheckWorker: checking up to %d of %d channels for playlist %d from offset %d",
                MAX_CHANNELS_PER_RUN,
                totalChannels,
                playlistId,
                startOffset
            )

            var deadCount = 0
            var aliveCount = 0
            var unknownCount = 0
            var checkedChannels = 0
            var offset = startOffset
            var capped = false

            while (offset < totalChannels && !capped) {
                val page = channelDao.getByPlaylistPaged(playlistId, PAGE_SIZE, offset)
                if (page.isEmpty()) break

                for (batch in page.chunked(BATCH_SIZE)) {
                    val results = coroutineScope {
                        batch.map { channel ->
                            async(Dispatchers.IO) { channel to pingStream(channel.streamUrl) }
                        }.map { it.await() }
                    }

                    for ((channel, health) in results) {
                        when (health) {
                            StreamHealth.ALIVE -> {
                                aliveCount++
                                if (!channel.isOnline) channelDao.setStreamOnline(channel.id, isOnline = true)
                            }
                            StreamHealth.DEAD -> {
                                deadCount++
                                if (channel.isOnline) channelDao.setStreamOnline(channel.id, isOnline = false)
                            }
                            // UNKNOWN: leave the stored isOnline state untouched.
                            StreamHealth.UNKNOWN -> unknownCount++
                        }
                    }

                    checkedChannels += batch.size
                    if (checkedChannels >= MAX_CHANNELS_PER_RUN) {
                        capped = true
                        break
                    }
                    // Small delay between batches to avoid rate limiting.
                    delay(BATCH_DELAY_MS)
                }

                offset += PAGE_SIZE
            }

            val nextOffset = nextStreamHealthOffset(startOffset, checkedChannels, totalChannels)
            if (capped && nextOffset != null) {
                Timber.w(
                    "StreamHealthCheckWorker: reached per-run cap; checked %d channels for playlist %d and will continue at offset %d",
                    checkedChannels,
                    playlistId,
                    nextOffset
                )
                enqueueForPlaylist(applicationContext, playlistId, nextOffset, append = true)
            }
            Timber.d("StreamHealthCheckWorker: done. alive=%d dead=%d unknown=%d", aliveCount, deadCount, unknownCount)
            Result.success(
                workDataOf(
                    KEY_ALIVE_COUNT to aliveCount,
                    KEY_DEAD_COUNT to deadCount,
                    KEY_UNKNOWN_COUNT to unknownCount,
                    KEY_NEXT_OFFSET to (nextOffset ?: NO_NEXT_OFFSET)
                )
            )
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "StreamHealthCheckWorker failed")
            Result.failure()
        }
    }

    private fun pingStream(url: String): StreamHealth {
        if (url.isBlank()) return StreamHealth.UNKNOWN
        val source = parsePlaybackSource(url)
        return try {
            val requestBuilder = Request.Builder()
                .url(source.url)
                // Ranged GET: HEAD is widely rejected by IPTV servers; ask for one byte and
                // close immediately so we never download the stream body.
                .header("Range", "bytes=0-1")
                .header("User-Agent", source.userAgent)
                .get()
            source.requestProperties.forEach(requestBuilder::header)
            val request = requestBuilder.build()
            val code = pingClient.newCall(request).execute().use { it.code }
            classifyStreamHealth(code)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            // Transient/network failure — do NOT mark a working channel offline.
            Timber.v("pingStream unknown: %s - %s", SensitiveLog.redactUrl(source.url), e.javaClass.simpleName)
            StreamHealth.UNKNOWN
        }
    }

    companion object {
        const val KEY_PLAYLIST_ID = "playlist_id"
        const val KEY_START_OFFSET = "start_offset"
        const val KEY_ALIVE_COUNT = "alive_count"
        const val KEY_DEAD_COUNT = "dead_count"
        const val KEY_UNKNOWN_COUNT = "unknown_count"
        const val KEY_NEXT_OFFSET = "next_offset"
        const val DEAD_STREAM_SENTINEL = -1
        const val NO_NEXT_OFFSET = -1
        private const val WORK_TAG = "stream_health_check"
        private const val PAGE_SIZE = 100
        private const val BATCH_SIZE = 10
        private const val BATCH_DELAY_MS = 200L

        /**
         * At an 8 second per-request timeout and ten concurrent probes, 500 channels fit inside
         * a conservative WorkManager execution window even on an entirely unresponsive source.
         */
        const val MAX_CHANNELS_PER_RUN = 500

        fun runForPlaylist(context: Context, playlistId: Long) {
            enqueueForPlaylist(context, playlistId, startOffset = 0, append = false)
        }

        private fun enqueueForPlaylist(
            context: Context,
            playlistId: Long,
            startOffset: Int,
            append: Boolean
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<StreamHealthCheckWorker>()
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        KEY_PLAYLIST_ID to playlistId,
                        KEY_START_OFFSET to startOffset
                    )
                )
                .addTag(WORK_TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()

            val policy = if (append) {
                ExistingWorkPolicy.APPEND_OR_REPLACE
            } else {
                ExistingWorkPolicy.KEEP
            }
            WorkManager.getInstance(context).enqueueUniqueWork(
                workNameFor(playlistId),
                policy,
                request
            )
            Timber.d(
                "StreamHealthCheckWorker: enqueued for playlist %d from offset %d",
                playlistId,
                startOffset
            )
        }

        internal fun workNameFor(playlistId: Long): String = "${WORK_TAG}_$playlistId"
    }
}
