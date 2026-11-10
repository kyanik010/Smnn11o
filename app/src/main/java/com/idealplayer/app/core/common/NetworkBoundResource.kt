package com.idealplayer.app.core.common

import kotlinx.coroutines.flow.*
import timber.log.Timber

/**
 * Offline-first network resource pattern.
 *
 * Flow:
 * 1. Emit [Resource.Loading]
 * 2. Emit current DB result immediately (cache)
 * 3. Try network fetch
 * 4. On success → save to DB → DB emits fresh data
 * 5. On failure → emit [Resource.Error] with last DB value (cache fallback)
 *
 * Usage:
 * ```
 * fun getChannels(playlistId: Long): Flow<Resource<List<Channel>>> =
 *     networkBoundResource(
 *         query = { channelDao.getByPlaylist(playlistId) },
 *         fetch  = { api.getChannels() },
 *         saveFetchResult = { channelDao.insertAll(it) },
 *         shouldFetch = { cached -> cached.isEmpty() }
 *     )
 * ```
 */
inline fun <DB, Network> networkBoundResource(
    crossinline query: () -> Flow<DB>,
    crossinline fetch: suspend () -> Network,
    crossinline saveFetchResult: suspend (Network) -> Unit,
    crossinline shouldFetch: (DB) -> Boolean = { true },
    crossinline onFetchFailed: (Throwable) -> Unit = { e ->
        Timber.e(e, "networkBoundResource fetch failed")
    }
): Flow<Resource<DB>> = flow {
    emit(Resource.Loading)

    val cachedData = query().first()
    emit(Resource.Success(cachedData))

    if (shouldFetch(cachedData)) {
        try {
            val networkResult = fetch()
            saveFetchResult(networkResult)
            emitAll(query().map { Resource.Success(it) })
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            onFetchFailed(e)
            val errorMsg = when {
                e.message?.contains("Unable to resolve host") == true ||
                    e.message?.contains("network") == true ->
                    "İnternet bağlantısı yok. Önbellek gösteriliyor."
                else ->
                    "Güncelleme başarısız. Önbellek gösteriliyor: ${e.localizedMessage}"
            }
            emitAll(query().map { Resource.Error(errorMsg, e) })
        }
    } else {
        emitAll(query().map { Resource.Success(it) })
    }
}
