package com.idealplayer.app.core.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class SleepTimerState(
    val isActive: Boolean = false,
    val selectedMinutes: Int = 0,     // chosen option
    val remainingMs: Long = 0L,       // countdown in ms
    val isLastMinute: Boolean = false  // triggers fade-out warning UI
) {
    val remainingFormatted: String get() {
        if (!isActive || remainingMs <= 0) return ""
        val totalSecs = (remainingMs / 1000).toInt()
        val m = totalSecs / 60
        val s = totalSecs % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}

/**
 * Sleep Timer — counts down and fires [onTimerExpired] when time is up.
 *
 * Options: 15, 30, 45, 60, 90 minutes.
 * Last minute: [SleepTimerState.isLastMinute] = true so UI can show fade-out.
 */
@Singleton
class SleepTimerManager internal constructor(
    private val dispatcher: CoroutineDispatcher
) {
    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    @Inject
    constructor() : this(Dispatchers.Main.immediate)

    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private var timerJob: Job? = null

    val availableOptions = listOf(15, 30, 45, 60, 90) // minutes

    /**
     * Start a sleep timer for [minutes] minutes.
     * Cancels any previously running timer.
     */
    fun start(minutes: Int, onTimerExpired: () -> Unit) {
        timerJob?.cancel()
        val durationMs = minutes * 60_000L
        Timber.d("SleepTimer: started for $minutes minutes")

        _state.value = SleepTimerState(
            isActive = true,
            selectedMinutes = minutes,
            remainingMs = durationMs,
            isLastMinute = minutes <= 1
        )

        timerJob = scope.launch {
            var remaining = durationMs
            while (remaining > 0 && coroutineContext.isActive && _state.value.isActive) {
                delay(1_000L)
                remaining -= 1_000L
                val isLastMin = remaining <= 60_000L
                _state.value = _state.value.copy(
                    remainingMs = maxOf(remaining, 0L),
                    isLastMinute = isLastMin
                )
            }
            if (coroutineContext.isActive && _state.value.isActive) {
                Timber.d("SleepTimer: expired")
                _state.value = SleepTimerState()
                onTimerExpired()
            }
        }
    }

    /**
     * Cancel the active timer.
     */
    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _state.value = SleepTimerState()
        Timber.d("SleepTimer: cancelled")
    }

    /**
     * Extend the current timer by [minutes] minutes.
     */
    fun extend(minutes: Int) {
        if (!_state.value.isActive) return
        val addedMs = minutes * 60_000L
        val newRemaining = _state.value.remainingMs + addedMs
        _state.value = _state.value.copy(
            remainingMs = newRemaining,
            isLastMinute = newRemaining <= 60_000L
        )
    }

    fun release() {
        scope.cancel()
    }
}
