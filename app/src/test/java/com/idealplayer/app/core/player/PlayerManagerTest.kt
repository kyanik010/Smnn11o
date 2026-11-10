package com.idealplayer.app.core.player

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.model.PlayerEngineType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class PlayerManagerTest {

    @Test
    fun `playback profiles distinguish live and vod`() {
        assertThat(PlaybackProfile.entries).containsExactly(
            PlaybackProfile.LIVE,
            PlaybackProfile.VOD
        ).inOrder()
    }

    @Test
    fun `playback states are defined correctly`() {
        assertThat(PlaybackState.values()).hasLength(7)
        assertThat(PlaybackState.IDLE.name).isEqualTo("IDLE")
        assertThat(PlaybackState.PLAYING.name).isEqualTo("PLAYING")
        assertThat(PlaybackState.ERROR.name).isEqualTo("ERROR")
    }

    @Test
    fun `aspect ratio modes are available`() {
        assertThat(AspectRatioMode.values()).hasLength(8)
        assertThat(AspectRatioMode.FIT.name).isEqualTo("FIT")
    }

    @Test
    fun `initial player state starts unconfirmed`() {
        val state = PlayerState()

        assertThat(state.playbackState).isEqualTo(PlaybackState.IDLE)
        assertThat(state.isPlaying).isFalse()
        assertThat(state.currentPosition).isEqualTo(0L)
        assertThat(state.duration).isEqualTo(0L)
        assertThat(state.isSeekable).isFalse()
        assertThat(state.playbackSpeed).isEqualTo(1f)
        assertThat(state.audioTracks).isEmpty()
        assertThat(state.subtitleTracks).isEmpty()
        assertThat(state.isPlaybackConfirmed).isFalse()
        assertThat(state.isSurfaceReady).isFalse()
        assertThat(state.hasRenderedFirstFrame).isFalse()
    }

    @Test
    fun `engine switch replays active buffering paused and error requests`() {
        assertThat(shouldReplayAfterEngineSwitch(PlaybackState.BUFFERING)).isTrue()
        assertThat(shouldReplayAfterEngineSwitch(PlaybackState.PLAYING)).isTrue()
        assertThat(shouldReplayAfterEngineSwitch(PlaybackState.PAUSED)).isTrue()
        assertThat(shouldReplayAfterEngineSwitch(PlaybackState.ERROR)).isTrue()
        assertThat(shouldReplayAfterEngineSwitch(PlaybackState.IDLE)).isFalse()
        assertThat(shouldReplayAfterEngineSwitch(PlaybackState.STOPPED)).isFalse()
        assertThat(shouldReplayAfterEngineSwitch(PlaybackState.ENDED)).isFalse()
    }

    @Test
    fun `engine switch resets live position and preserves nonnegative vod position`() {
        assertThat(engineSwitchStartPosition(PlaybackProfile.LIVE, 45_000L)).isEqualTo(0L)
        assertThat(engineSwitchStartPosition(PlaybackProfile.VOD, 45_000L)).isEqualTo(45_000L)
        assertThat(engineSwitchStartPosition(PlaybackProfile.VOD, -1L)).isEqualTo(0L)
    }

    @Test
    fun `playback request rebuilds a missing or mismatched engine once`() {
        assertThat(
            shouldPreparePlaybackEngine(
                activeEngineType = null,
                preferredEngineType = PlayerEngineType.EXOPLAYER,
                engineSwitchAttempted = false
            )
        ).isTrue()
        assertThat(
            shouldPreparePlaybackEngine(
                activeEngineType = PlayerEngineType.VLC,
                preferredEngineType = PlayerEngineType.EXOPLAYER,
                engineSwitchAttempted = false
            )
        ).isTrue()
        assertThat(
            shouldPreparePlaybackEngine(
                activeEngineType = PlayerEngineType.EXOPLAYER,
                preferredEngineType = PlayerEngineType.EXOPLAYER,
                engineSwitchAttempted = false
            )
        ).isFalse()
        assertThat(
            shouldPreparePlaybackEngine(
                activeEngineType = null,
                preferredEngineType = PlayerEngineType.EXOPLAYER,
                engineSwitchAttempted = true
            )
        ).isFalse()
    }

    @Test
    fun `automatic fallback only handles unconfirmed ExoPlayer errors once`() {
        val failedState = PlayerState(
            playbackState = PlaybackState.ERROR,
            errorMessage = "Decoder failed",
            isPlaybackConfirmed = false
        )

        assertThat(
            shouldAttemptAutomaticFallback(
                enabled = true,
                activeEngineType = PlayerEngineType.EXOPLAYER,
                state = failedState,
                hasPreviouslyConfirmed = false,
                alreadyAttempted = false,
                hasPlaybackRequest = true
            )
        ).isTrue()
        assertThat(
            shouldAttemptAutomaticFallback(
                enabled = true,
                activeEngineType = PlayerEngineType.VLC,
                state = failedState,
                hasPreviouslyConfirmed = false,
                alreadyAttempted = false,
                hasPlaybackRequest = true
            )
        ).isFalse()
        assertThat(
            shouldAttemptAutomaticFallback(
                enabled = true,
                activeEngineType = PlayerEngineType.EXOPLAYER,
                state = failedState,
                hasPreviouslyConfirmed = false,
                alreadyAttempted = true,
                hasPlaybackRequest = true
            )
        ).isFalse()
        assertThat(
            shouldAttemptAutomaticFallback(
                enabled = false,
                activeEngineType = PlayerEngineType.EXOPLAYER,
                state = failedState,
                hasPreviouslyConfirmed = false,
                alreadyAttempted = false,
                hasPlaybackRequest = true
            )
        ).isFalse()
    }

    @Test
    fun `confirmed playback errors do not trigger automatic fallback`() {
        val state = PlayerState(
            playbackState = PlaybackState.ERROR,
            isPlaybackConfirmed = true
        )

        assertThat(
            shouldAttemptAutomaticFallback(
                enabled = true,
                activeEngineType = PlayerEngineType.EXOPLAYER,
                state = state,
                hasPreviouslyConfirmed = true,
                alreadyAttempted = false,
                hasPlaybackRequest = true
            )
        ).isFalse()
    }

    @Test
    fun `playback errors are reduced to privacy safe categories`() {
        assertThat(classifyPlaybackError("HTTP 403 for a private URL")).isEqualTo("authorization")
        assertThat(classifyPlaybackError("Decoder failed for video HEVC")).isEqualTo("decoder")
        assertThat(classifyPlaybackError(null)).isEqualTo("unknown")
    }

    @Test
    fun `player engine contract exposes single profile aware play method`() {
        val engine = object : PlayerEngine {
            override val state = MutableStateFlow(PlayerState())
            override val engineName: String = "TEST"

            override fun initialize() {}
            override fun release() {}
            override fun play(url: String, startPosition: Long, profile: PlaybackProfile) {}
            override fun pause() {}
            override fun resume() {}
            override fun stop() {}
            override fun seekTo(position: Long) {}
            override fun seekForward(ms: Long) {}
            override fun seekBackward(ms: Long) {}
            override fun setPlaybackSpeed(speed: Float) {}
            override fun selectAudioTrack(index: Int) {}
            override fun selectSubtitleTrack(index: Int) {}
            override fun disableSubtitles() {}
            override fun setAspectRatio(mode: AspectRatioMode) {}
        }

        engine.play("https://example.com/stream.ts", 0L, PlaybackProfile.LIVE)
        assertThat(engine.state.value.playbackState).isEqualTo(PlaybackState.IDLE)
    }
}
