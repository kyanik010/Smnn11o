package com.idealplayer.app.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExoPlaybackGateTest {

    @Test
    fun `ready surface does not consume playback request before tracks are known`() {
        assertThat(
            shouldOpenPlaybackGate(
                hasVideoTrack = false,
                hasAudioTrack = false,
                surfaceReady = true
            )
        ).isFalse()
    }

    @Test
    fun `video playback waits for its surface`() {
        assertThat(
            shouldOpenPlaybackGate(
                hasVideoTrack = true,
                hasAudioTrack = false,
                surfaceReady = false
            )
        ).isFalse()
        assertThat(
            shouldOpenPlaybackGate(
                hasVideoTrack = true,
                hasAudioTrack = false,
                surfaceReady = true
            )
        ).isTrue()
    }

    @Test
    fun `audio only playback does not wait for a video surface`() {
        assertThat(
            shouldOpenPlaybackGate(
                hasVideoTrack = false,
                hasAudioTrack = true,
                surfaceReady = false
            )
        ).isTrue()
    }
}
