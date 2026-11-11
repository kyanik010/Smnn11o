package com.idealplayer.app.core.audio

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.idealplayer.app.core.model.Channel
import com.idealplayer.app.core.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 34])
class ExternalAudioManagerTest {

    private lateinit var context: Context
    private lateinit var manager: ExternalAudioManager
    private var muteCalls = 0
    private var unmuteCalls = 0

    private val testChannel = Channel(
        id = 10,
        playlistId = 1,
        streamId = 100,
        name = "beIN SPORTS 1 HD",
        groupTitle = "SPORTS",
        streamUrl = "http://provider.tv/live/user/pass/100.ts"
    )

    private val testPlaylist = Playlist(
        id = 1,
        name = "Test Playlist",
        type = com.idealplayer.app.core.model.PlaylistType.XTREAM_CODES,
        serverUrl = "http://provider.tv",
        username = "user",
        password = "pass"
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        manager = ExternalAudioManager(context)
        muteCalls = 0
        unmuteCalls = 0
        manager.muteVideoAudioAction = { muteCalls++ }
        manager.restoreVideoAudioAction = { unmuteCalls++ }
    }

    @Test
    fun initialState_isIdleAndUnmuted() {
        val state = manager.state.value
        assertFalse(state.isActive)
        assertEquals(AudioExtractionState.IDLE, state.extractionState)
        assertFalse(manager.isVideoMuted())
        assertEquals(0, manager.getRetryAttempt())
    }

    @Test
    fun readyState_doesNotMuteVideoAndDoesNotResetRetryAttempt() {
        manager.startExternalAudio(testChannel, testPlaylist)
        shadowOf(Looper.getMainLooper()).idle()

        // Simulate reaching STATE_READY without playing yet
        manager.handleAudioPlayerReady()
        shadowOf(Looper.getMainLooper()).idle()

        // Video must NOT be muted merely on READY
        assertFalse("Video audio must NOT be muted on STATE_READY", manager.isVideoMuted())
        assertEquals(0, muteCalls)
        assertEquals(AudioExtractionState.RESOURCE_READY, manager.state.value.extractionState)
    }

    @Test
    fun playingState_mutesVideoAudioOnce() {
        manager.startExternalAudio(testChannel, testPlaylist)
        shadowOf(Looper.getMainLooper()).idle()

        manager.handleAudioPlayerPlaying()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("Video audio MUST be muted when external audio starts genuinely playing", manager.isVideoMuted())
        assertEquals(1, muteCalls)
        assertEquals(AudioExtractionState.PLAYING, manager.state.value.extractionState)

        // Duplicate onPlaying callbacks must not invoke mute again
        manager.handleAudioPlayerPlaying()
        assertEquals("Duplicate playing callbacks must not invoke mute multiple times", 1, muteCalls)
    }

    @Test
    fun unstablePlayback_doesNotResetRetryAttemptBeforeThreshold() {
        manager.startExternalAudio(testChannel, testPlaylist)
        shadowOf(Looper.getMainLooper()).idle()

        // Force an attempt count
        manager.handleAudioPlayerError("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", "Network drop")
        assertEquals(1, manager.getRetryAttempt())

        // Reconnect and start playing
        manager.handleAudioPlayerPlaying()
        shadowOf(Looper.getMainLooper()).idle()

        // Advance looper by 2 seconds (less than STABLE_PLAYBACK_THRESHOLD_MS of 5s)
        shadowOf(Looper.getMainLooper()).idleFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("Retry attempt must NOT be reset on brief unstable playback", 1, manager.getRetryAttempt())

        // Stream drops again before 5s
        manager.handleAudioPlayerError("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", "Network drop again")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Retry attempt must have incremented on subsequent failure", 2, manager.getRetryAttempt())
    }

    @Test
    fun stablePlayback_resetsRetryAttemptAfterFiveSeconds() {
        manager.startExternalAudio(testChannel, testPlaylist)
        shadowOf(Looper.getMainLooper()).idle()

        // Force a retry count
        manager.handleAudioPlayerError("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", "Transient drop")
        assertEquals(1, manager.getRetryAttempt())

        // Plays stably
        manager.handleAudioPlayerPlaying()
        shadowOf(Looper.getMainLooper()).idle()

        // Advance looper past STABLE_PLAYBACK_THRESHOLD_MS (5000ms)
        shadowOf(Looper.getMainLooper()).idleFor(5100, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertEquals("Retry attempt must reset to 0 after stable playback exceeding threshold", 0, manager.getRetryAttempt())
    }

    @Test
    fun persistentFailure_restoresVideoAudioAndCeasesRetries() {
        manager.startExternalAudio(testChannel, testPlaylist)
        shadowOf(Looper.getMainLooper()).idle()

        // First it plays, muting the video
        manager.handleAudioPlayerPlaying()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(manager.isVideoMuted())
        assertEquals(1, muteCalls)

        // Trigger failures up to MAX_EXTERNAL_AUDIO_RETRIES (5)
        for (i in 1..MAX_EXTERNAL_AUDIO_RETRIES) {
            manager.handleAudioPlayerError("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", "Fail #$i")
            shadowOf(Looper.getMainLooper()).idle()
        }

        // At attempt 5, it was retried. When attempt 5 fails:
        manager.handleAudioPlayerError("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", "Terminal failure")
        shadowOf(Looper.getMainLooper()).idle()

        // Must restore video audio
        assertFalse("Video audio MUST be restored after persistent failure", manager.isVideoMuted())
        assertTrue("Unmute callback MUST be called", unmuteCalls >= 1)
        assertEquals(AudioExtractionState.FAILED_GENERAL, manager.state.value.extractionState)
        assertNotNull(manager.state.value.errorMessage)
        assertEquals(AudioErrorCode.SOURCE_TIMEOUT, manager.state.value.errorCode)

        // Advance time - verify no further reconnect is scheduled
        shadowOf(Looper.getMainLooper()).idleFor(60000, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(AudioExtractionState.FAILED_GENERAL, manager.state.value.extractionState)
    }

    @Test
    fun transientError_keepsVideoMutedDuringGracePeriod() {
        manager.startExternalAudio(testChannel, testPlaylist)
        shadowOf(Looper.getMainLooper()).idle()

        manager.handleAudioPlayerPlaying()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(manager.isVideoMuted())

        // First transient error
        manager.handleAudioPlayerError("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", "Transient error")
        shadowOf(Looper.getMainLooper()).idle()

        // During grace period (attempt 1 <= 5), video remains muted to prevent audio flapping
        assertTrue("Video must remain muted during transient backoff grace period", manager.isVideoMuted())
        assertEquals(0, unmuteCalls)
        assertEquals(1, manager.getRetryAttempt())
    }

    @Test
    fun bufferingTimeout_triggersRetryWhenBufferingHangs() {
        manager.startExternalAudio(testChannel, testPlaylist)
        shadowOf(Looper.getMainLooper()).idle()

        manager.handleAudioPlayerPlaying()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(manager.isVideoMuted())

        // Enter buffering
        manager.handleAudioPlayerBuffering()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AudioExtractionState.STREAM_OPENING, manager.state.value.extractionState)
        assertEquals(0, manager.getRetryAttempt())

        // Brief buffering (< 10s) does NOT trigger failure
        shadowOf(Looper.getMainLooper()).idleFor(5000, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(0, manager.getRetryAttempt())

        // Sustained buffering past 10s timeout
        shadowOf(Looper.getMainLooper()).idleFor(5100, java.util.concurrent.TimeUnit.MILLISECONDS)

        // Timeout fires and schedules retry
        assertEquals("Buffering timeout must increment retry attempt", 1, manager.getRetryAttempt())
    }

    @Test
    fun bufferingResumingBeforeTimeout_cancelsBufferingTimeout() {
        manager.startExternalAudio(testChannel, testPlaylist)
        shadowOf(Looper.getMainLooper()).idle()

        manager.handleAudioPlayerPlaying()
        shadowOf(Looper.getMainLooper()).idle()

        // Buffering begins
        manager.handleAudioPlayerBuffering()
        shadowOf(Looper.getMainLooper()).idle()

        // Buffers for 4 seconds, then resumes playing
        shadowOf(Looper.getMainLooper()).idleFor(4000, java.util.concurrent.TimeUnit.MILLISECONDS)
        manager.handleAudioPlayerPlaying()
        shadowOf(Looper.getMainLooper()).idle()

        // Advance another 10 seconds - buffering timeout must NOT fire
        shadowOf(Looper.getMainLooper()).idleFor(10000, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("Retry attempt must remain 0 since buffering recovered cleanly", 0, manager.getRetryAttempt())
        assertEquals(AudioExtractionState.PLAYING, manager.state.value.extractionState)
    }

    @Test
    fun manualStop_restoresVideoAudioAndCancelsAllPendingTasks() {
        manager.startExternalAudio(testChannel, testPlaylist)
        shadowOf(Looper.getMainLooper()).idle()

        manager.handleAudioPlayerPlaying()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(manager.isVideoMuted())

        manager.stop()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("Video audio must be restored on stop()", manager.isVideoMuted())
        assertEquals(1, unmuteCalls)
        assertFalse(manager.state.value.isActive)
        assertEquals(AudioExtractionState.STOPPED, manager.state.value.extractionState)
        assertEquals(0, manager.getRetryAttempt())

        // Even after time passes, no retry fires
        shadowOf(Looper.getMainLooper()).idleFor(60000, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(AudioExtractionState.STOPPED, manager.state.value.extractionState)
    }

    @Test
    fun channelSwitch_cancelsOldCallbacksAndPreventsStaleExecution() {
        manager.startExternalAudio(testChannel, testPlaylist)
        shadowOf(Looper.getMainLooper()).idle()

        // Simulate pending retry on old channel
        manager.handleAudioPlayerError("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", "Drop")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, manager.getRetryAttempt())

        val channel2 = Channel(
            id = 20,
            playlistId = 1,
            streamId = 200,
            name = "beIN SPORTS 2 HD",
            groupTitle = "SPORTS",
            streamUrl = "http://provider.tv/live/user/pass/200.ts"
        )

        // User switches channel
        manager.startExternalAudio(channel2, testPlaylist)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("New channel start must reset retry attempt to 0", 0, manager.getRetryAttempt())
        assertEquals(channel2.id, manager.state.value.selectedChannel?.id)
    }

    @Test
    fun media3ErrorMapping_preservesRealCause() {
        manager.startExternalAudio(testChannel, testPlaylist)
        shadowOf(Looper.getMainLooper()).idle()

        manager.handleAudioPlayerError("ERROR_CODE_PARSER_CONTAINER_MALFORMED", "Bad TS packet")
        shadowOf(Looper.getMainLooper()).idle()

        val state = manager.state.value
        assertEquals(AudioErrorCode.TS_DEMUX_ERROR, state.errorCode)
        assertTrue(state.errorMessage?.contains("ERROR_CODE_PARSER_CONTAINER_MALFORMED") == true)
        assertTrue(state.errorMessage?.contains("Bad TS packet") == true)
    }

    @Test
    fun streamEnded_triggersFailureHandling() {
        manager.startExternalAudio(testChannel, testPlaylist)
        shadowOf(Looper.getMainLooper()).idle()

        manager.handleAudioPlayerPlaying()
        shadowOf(Looper.getMainLooper()).idle()

        manager.handleAudioPlayerEnded()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, manager.getRetryAttempt())
        assertEquals(AudioExtractionState.STREAM_OPENING, manager.state.value.extractionState)
    }

    @Test
    fun vodSync_boundsTargetAndAppliesOffset() {
        val target = ExternalAudioSyncPolicy.targetPositionMs(
            videoPositionMs = 20000L,
            offsetMs = 1500L,
            audioDurationMs = 60000L
        )
        assertEquals(18500L, target)
    }
}
