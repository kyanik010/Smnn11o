package com.idealplayer.app.ui.player

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.player.PlaybackState
import org.junit.Test

class AutoPlayNextEpisodeTest {

    @Test
    fun `ended series episode triggers auto play once`() {
        assertThat(
            shouldTriggerAutoPlayNextEpisode(
                isSeriesPlayback = true,
                autoPlayEnabled = true,
                transitionInProgress = false,
                contentId = 10L,
                alreadyTriggeredContentId = null,
                playbackState = PlaybackState.ENDED,
                currentPosition = 60_000L,
                duration = 60_000L,
                minimumDuration = 30_000L,
                endTolerance = 2_500L
            )
        ).isTrue()
    }

    @Test
    fun `stale end state cannot restart an active episode transition`() {
        assertThat(
            shouldTriggerAutoPlayNextEpisode(
                isSeriesPlayback = true,
                autoPlayEnabled = true,
                transitionInProgress = true,
                contentId = 11L,
                alreadyTriggeredContentId = null,
                playbackState = PlaybackState.ENDED,
                currentPosition = 60_000L,
                duration = 60_000L,
                minimumDuration = 30_000L,
                endTolerance = 2_500L
            )
        ).isFalse()
    }

    @Test
    fun `near end playback triggers while pause does not`() {
        val common = mapOf(
            PlaybackState.PLAYING to true,
            PlaybackState.BUFFERING to true,
            PlaybackState.PAUSED to false
        )

        common.forEach { (state, expected) ->
            assertThat(
                shouldTriggerAutoPlayNextEpisode(
                    isSeriesPlayback = true,
                    autoPlayEnabled = true,
                    transitionInProgress = false,
                    contentId = 10L,
                    alreadyTriggeredContentId = null,
                    playbackState = state,
                    currentPosition = 58_000L,
                    duration = 60_000L,
                    minimumDuration = 30_000L,
                    endTolerance = 2_500L
                )
            ).isEqualTo(expected)
        }
    }
}
