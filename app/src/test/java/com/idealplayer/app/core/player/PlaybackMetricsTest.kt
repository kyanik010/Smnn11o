package com.idealplayer.app.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackMetricsTest {

    @Test
    fun `resolution tiers use channel dimensions`() {
        assertThat(videoResolutionTier(3_840, 2_160)).isEqualTo(VideoResolutionTier.UHD_4K)
        assertThat(videoResolutionTier(1_920, 1_080)).isEqualTo(VideoResolutionTier.FULL_HD)
        assertThat(videoResolutionTier(1_280, 720)).isEqualTo(VideoResolutionTier.HD)
        assertThat(videoResolutionTier(720, 576)).isEqualTo(VideoResolutionTier.SD)
        assertThat(videoResolutionTier(0, 0)).isNull()
        assertThat(videoResolutionBadge(1_920, 1_080)).isEqualTo("FHD • 1920×1080")
    }

    @Test
    fun `frame rate keeps broadcast fractional rates readable`() {
        assertThat(formatFrameRate(50.0)).isEqualTo("50 FPS")
        assertThat(formatFrameRate(59.94)).isEqualTo("59.94 FPS")
        assertThat(formatFrameRate(0.0)).isEmpty()
        assertThat(formatFrameRate(500.0)).isEmpty()
    }

    @Test
    fun `network byte delta is converted to decimal kilobits per second`() {
        assertThat(calculateNetworkSpeedKbps(125_000L, 1_000L)).isEqualTo(1_000L)
        assertThat(calculateNetworkSpeedKbps(0L, 1_000L)).isEqualTo(0L)
        assertThat(formatNetworkSpeed(850L)).isEqualTo("850 Kbps")
    }

    @Test
    fun `network sampler throttles measurements and can reset between streams`() {
        var bytes = 100_000L
        var timeMs = 1_000L
        val sampler = NetworkThroughputSampler(
            receivedBytes = { bytes },
            elapsedRealtimeMs = { timeMs }
        )

        assertThat(sampler.sample()).isEqualTo(0L)

        bytes = 225_000L
        timeMs = 2_000L
        assertThat(sampler.sample()).isEqualTo(1_000L)

        bytes = 250_000L
        timeMs = 2_500L
        assertThat(sampler.sample()).isEqualTo(1_000L)

        sampler.reset()
        assertThat(sampler.sample()).isEqualTo(0L)
    }
}
