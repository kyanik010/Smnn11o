package com.idealplayer.app.core.player

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerManagerTest {

    private lateinit var sleepTimer: SleepTimerManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sleepTimer = SleepTimerManager(testDispatcher)
    }

    @After
    fun tearDown() {
        sleepTimer.cancel()
        sleepTimer.release()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is inactive`() {
        val state = sleepTimer.state.value
        assertThat(state.isActive).isFalse()
        assertThat(state.remainingMs).isEqualTo(0L)
        assertThat(state.selectedMinutes).isEqualTo(0)
    }

    @Test
    fun `start sets active state`() {
        var expired = false
        sleepTimer.start(30) { expired = true }

        val state = sleepTimer.state.value
        assertThat(state.isActive).isTrue()
        assertThat(state.selectedMinutes).isEqualTo(30)
        assertThat(state.remainingMs).isEqualTo(30 * 60_000L)
        assertThat(expired).isFalse()
    }

    @Test
    fun `cancel resets state`() {
        sleepTimer.start(15) {}
        assertThat(sleepTimer.state.value.isActive).isTrue()

        sleepTimer.cancel()
        val state = sleepTimer.state.value
        assertThat(state.isActive).isFalse()
        assertThat(state.remainingMs).isEqualTo(0L)
    }

    @Test
    fun `extend adds time to remaining`() {
        sleepTimer.start(15) {}
        val before = sleepTimer.state.value.remainingMs

        sleepTimer.extend(5)
        val after = sleepTimer.state.value.remainingMs

        assertThat(after).isEqualTo(before + 5 * 60_000L)
    }

    @Test
    fun `extend on inactive timer does nothing`() {
        sleepTimer.extend(5)
        assertThat(sleepTimer.state.value.remainingMs).isEqualTo(0L)
    }

    @Test
    fun `isLastMinute is true when less than 60s remain`() {
        sleepTimer.start(1) {} // 1 minute
        // After start, 1 min = exactly 60000ms → isLastMinute depends on <= 60000
        assertThat(sleepTimer.state.value.isLastMinute).isTrue()
    }

    @Test
    fun `isLastMinute is false for longer durations`() {
        sleepTimer.start(30) {}
        assertThat(sleepTimer.state.value.isLastMinute).isFalse()
    }

    @Test
    fun `available options contains expected minutes`() {
        assertThat(sleepTimer.availableOptions).containsExactly(15, 30, 45, 60, 90).inOrder()
    }

    @Test
    fun `remainingFormatted is empty when inactive`() {
        assertThat(sleepTimer.state.value.remainingFormatted).isEmpty()
    }

    @Test
    fun `remainingFormatted shows seconds when less than 1 min`() {
        sleepTimer.start(1) {}
        val formatted = sleepTimer.state.value.remainingFormatted
        assertThat(formatted).isNotEmpty()
        assertThat(formatted).isEqualTo("1m 0s")
    }

    @Test
    fun `starting new timer cancels previous`() {
        var firstExpired = false
        var secondExpired = false

        sleepTimer.start(30) { firstExpired = true }
        sleepTimer.start(15) { secondExpired = true }

        val state = sleepTimer.state.value
        assertThat(state.selectedMinutes).isEqualTo(15)
        assertThat(firstExpired).isFalse()
    }
}
