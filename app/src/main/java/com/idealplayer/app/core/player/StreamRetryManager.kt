package com.idealplayer.app.core.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.idealplayer.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stream error categories for user-friendly messages.
 */
enum class StreamErrorType {
    NO_INTERNET,       // Device has no network connection at all
    STREAM_OFFLINE,    // Network OK, but the stream itself is unreachable
    PLAYBACK_ERROR,    // Generic playback error (codec, auth, etc.)
    UNKNOWN
}

data class RetryState(
    val isRetrying: Boolean = false,
    val attempt: Int = 0,
    val maxAttempts: Int = 3,
    val errorType: StreamErrorType = StreamErrorType.UNKNOWN,
    val userMessage: String = "",
    val nextRetryInSeconds: Int = 0
)

/**
 * Exponential Backoff Retry Manager for stream playback.
 *
 * Strategy:
 * - Up to 3 retry attempts
 * - Backoff delays: 2s → 4s → 8s
 * - Differentiates NO_INTERNET vs STREAM_OFFLINE
 * - Observes ConnectivityManager to detect reconnection
 * - Exposes [state] flow for UI binding
 */
@Singleton
class StreamRetryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(RetryState())
    val state: StateFlow<RetryState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var retryJob: Job? = null
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val backoffDelaysMs = listOf(2_000L, 4_000L, 8_000L)

    /**
     * Current network connectivity status.
     */
    val isNetworkAvailable: Boolean
        get() {
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

    /**
     * Classify the given [PlaybackState] / [errorMessage] into a [StreamErrorType].
     */
    fun classifyError(errorMessage: String?): StreamErrorType {
        if (!isNetworkAvailable) return StreamErrorType.NO_INTERNET

        val msg = errorMessage?.lowercase() ?: return StreamErrorType.STREAM_OFFLINE
        return when {
            msg.contains("connection") || msg.contains("network") ||
                msg.contains("timeout") || msg.contains("unreachable") -> StreamErrorType.STREAM_OFFLINE
            msg.contains("codec") || msg.contains("format") ||
                msg.contains("decode") -> StreamErrorType.PLAYBACK_ERROR
            else -> StreamErrorType.STREAM_OFFLINE
        }
    }

    /** Build a locale-aware retry message for the given error. */
    fun buildUserMessage(errorType: StreamErrorType, attempt: Int, maxAttempts: Int): String {
        return context.getString(retryAttemptMessageRes(errorType), attempt, maxAttempts)
    }

    /**
     * Start retry cycle. Calls [onRetry] at each attempt.
     * Calls [onExhausted] when all attempts are used.
     */
    fun startRetry(
        errorMessage: String?,
        onRetry: suspend () -> Unit,
        onExhausted: (StreamErrorType) -> Unit
    ) {
        retryJob?.cancel()
        val errorType = classifyError(errorMessage)
        val maxAttempts = backoffDelaysMs.size

        retryJob = scope.launch {
            for (attempt in 1..maxAttempts) {
                val delayMs = backoffDelaysMs.getOrElse(attempt - 1) { 8_000L }

                // Countdown display
                var secondsLeft = (delayMs / 1000).toInt()
                _state.value = RetryState(
                    isRetrying = true,
                    attempt = attempt,
                    maxAttempts = maxAttempts,
                    errorType = errorType,
                    userMessage = buildUserMessage(errorType, attempt, maxAttempts),
                    nextRetryInSeconds = secondsLeft
                )

                // Wait with countdown tick
                while (secondsLeft > 0) {
                    delay(1000L)
                    secondsLeft--
                    if (!isActive) return@launch
                    _state.value = _state.value.copy(nextRetryInSeconds = secondsLeft)
                }

                if (!isActive) return@launch

                Timber.d("StreamRetryManager: attempt $attempt/$maxAttempts for error=$errorType")

                // If still no internet, wait for connectivity
                if (!isNetworkAvailable) {
                    _state.value = _state.value.copy(
                        userMessage = context.getString(R.string.retry_waiting_for_network),
                        nextRetryInSeconds = 0
                    )
                    waitForNetwork()
                    if (!isActive) return@launch
                }

                onRetry()

                // Give playback a moment to settle before deciding to retry again
                delay(6_000L)

                if (!isActive) return@launch
            }

            // All attempts exhausted
            Timber.w("StreamRetryManager: all $maxAttempts attempts exhausted")
            _state.value = RetryState(
                isRetrying = false,
                attempt = maxAttempts,
                maxAttempts = maxAttempts,
                errorType = errorType,
                userMessage = context.getString(retryExhaustedMessageRes(errorType))
            )
            onExhausted(errorType)
        }
    }

    /**
     * Cancel any ongoing retry and reset state.
     */
    fun cancel() {
        retryJob?.cancel()
        retryJob = null
        _state.value = RetryState()
        unregisterConnectivityCallback()
    }

    /**
     * Mark retry as successful (e.g. playback resumed).
     */
    fun onPlaybackSuccess() {
        if (_state.value.isRetrying) {
            retryJob?.cancel()
            retryJob = null
            _state.value = RetryState()
            unregisterConnectivityCallback()
            Timber.d("StreamRetryManager: playback success, retry cancelled")
        }
    }

    /**
     * Suspend until network connectivity is available.
     */
    private suspend fun waitForNetwork() {
        if (isNetworkAvailable) return

        val completable = CompletableDeferred<Unit>()
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Timber.d("StreamRetryManager: network became available")
                completable.complete(Unit)
            }
        }
        connectivityCallback = callback

        try {
            connectivityManager.registerNetworkCallback(request, callback)
            withTimeoutOrNull(60_000L) { completable.await() }
        } finally {
            unregisterConnectivityCallback()
        }
    }

    private fun unregisterConnectivityCallback() {
        connectivityCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
            connectivityCallback = null
        }
    }

    fun release() {
        scope.cancel()
        unregisterConnectivityCallback()
    }
}

internal fun retryAttemptMessageRes(errorType: StreamErrorType): Int = when (errorType) {
    StreamErrorType.NO_INTERNET -> R.string.retry_no_internet_attempt
    StreamErrorType.STREAM_OFFLINE -> R.string.retry_stream_offline_attempt
    StreamErrorType.PLAYBACK_ERROR -> R.string.retry_playback_error_attempt
    StreamErrorType.UNKNOWN -> R.string.retry_unknown_attempt
}

internal fun retryExhaustedMessageRes(errorType: StreamErrorType): Int = when (errorType) {
    StreamErrorType.NO_INTERNET -> R.string.retry_no_internet_exhausted
    StreamErrorType.STREAM_OFFLINE -> R.string.retry_stream_offline_exhausted
    StreamErrorType.PLAYBACK_ERROR,
    StreamErrorType.UNKNOWN -> R.string.retry_playback_error_exhausted
}
