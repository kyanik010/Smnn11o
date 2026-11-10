package com.idealplayer.app.ui.player

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.player.parsePlaybackSource
import org.junit.Test

class SeriesPlaybackCandidatesTest {

    @Test
    fun `extensionless xtream series url gets bounded fallbacks`() {
        assertThat(
            seriesPlaybackCandidates(
                "https://provider.example/series/user/pass/12345?token=synthetic"
            )
        ).containsExactly(
            "https://provider.example/series/user/pass/12345?token=synthetic",
            "https://provider.example/series/user/pass/12345.mkv?token=synthetic",
            "https://provider.example/series/user/pass/12345.mp4?token=synthetic",
            "https://provider.example/series/user/pass/12345.ts?token=synthetic"
        ).inOrder()
    }

    @Test
    fun `series url with extension is not rewritten`() {
        val url = "https://provider.example/series/user/pass/12345.mkv"

        assertThat(seriesPlaybackCandidates(url)).containsExactly(url)
    }

    @Test
    fun `non xtream url is not rewritten`() {
        val url = "https://cdn.example/synthetic/episode"

        assertThat(seriesPlaybackCandidates(url)).containsExactly(url)
    }

    @Test
    fun `request headers are preserved on extension fallback candidates`() {
        val candidates = seriesPlaybackCandidates(
            "https://provider.example/series/user/pass/42|User-Agent=Example%20TV&Referer=https%3A%2F%2Fportal.example%2F"
        )

        assertThat(candidates).hasSize(4)
        candidates.forEach { candidate ->
            assertThat(parsePlaybackSource(candidate).headers).containsExactly(
                "User-Agent", "Example TV",
                "Referer", "https://portal.example/"
            )
        }
    }

    @Test
    fun `resume setting controls requested start position`() {
        assertThat(playbackStartPosition(42_000L, autoResumeEnabled = true)).isEqualTo(42_000L)
        assertThat(playbackStartPosition(42_000L, autoResumeEnabled = false)).isEqualTo(0L)
        assertThat(playbackStartPosition(-1L, autoResumeEnabled = true)).isEqualTo(0L)
    }
}
