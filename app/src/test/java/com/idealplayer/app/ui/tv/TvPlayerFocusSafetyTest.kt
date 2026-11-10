package com.idealplayer.app.ui.tv

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.model.Movie
import org.junit.Test

class TvPlayerFocusSafetyTest {

    @Test
    fun `playback kind is case insensitive and session context wins`() {
        assertThat(resolveTvPlaybackKind("live", hasLiveChannel = false, hasEpisode = false))
            .isEqualTo(TvPlaybackKind.LIVE)
        assertThat(resolveTvPlaybackKind("movie", hasLiveChannel = false, hasEpisode = true))
            .isEqualTo(TvPlaybackKind.SERIES)
        assertThat(resolveTvPlaybackKind("unknown", hasLiveChannel = true, hasEpisode = false))
            .isEqualTo(TvPlaybackKind.LIVE)
    }

    @Test
    fun `utility actions adapt to movie series and live capabilities`() {
        assertThat(
            tvPlayerUtilityActions(
                playbackKind = TvPlaybackKind.MOVIE,
                hasChannels = false,
                hasPreviousEpisode = false,
                hasNextEpisode = false,
                hasAudioTracks = false,
                hasSubtitleTracks = false
            )
        ).containsExactly(TvOverlayAction.SCREEN_MODE, TvOverlayAction.SETTINGS).inOrder()

        assertThat(
            tvPlayerUtilityActions(
                playbackKind = TvPlaybackKind.SERIES,
                hasChannels = false,
                hasPreviousEpisode = true,
                hasNextEpisode = true,
                hasAudioTracks = true,
                hasSubtitleTracks = true
            )
        ).containsExactly(
            TvOverlayAction.PREVIOUS_EPISODE,
            TvOverlayAction.NEXT_EPISODE,
            TvOverlayAction.AUDIO,
            TvOverlayAction.SUBTITLE,
            TvOverlayAction.SCREEN_MODE,
            TvOverlayAction.SETTINGS
        ).inOrder()

        assertThat(
            tvPlayerUtilityActions(
                playbackKind = TvPlaybackKind.LIVE,
                hasChannels = false,
                hasPreviousEpisode = false,
                hasNextEpisode = false,
                hasAudioTracks = false,
                hasSubtitleTracks = false
            )
        ).containsExactly(
            TvOverlayAction.EPG,
            TvOverlayAction.SCREEN_MODE,
            TvOverlayAction.SETTINGS
        ).inOrder()
    }

    @Test
    fun `focused seek target follows engine after pending window`() {
        assertThat(
            tvResolvedScrubPosition(
                enginePosition = 35_000L,
                localPosition = 30_000L,
                duration = 120_000L,
                pendingAgeMs = 2_000L
            )
        ).isEqualTo(35_000L)
        assertThat(
            tvResolvedScrubPosition(
                enginePosition = 30_000L,
                localPosition = 45_000L,
                duration = 120_000L,
                pendingAgeMs = 250L
            )
        ).isEqualTo(45_000L)
    }

    @Test
    fun `panel back returns through category and settings hierarchy`() {
        assertThat(
            tvPlayerPanelBackTarget(TvPlayerPanel.CATEGORIES, TvPlayerPanel.NONE)
        ).isEqualTo(TvPlayerPanel.CHANNELS)
        assertThat(
            tvPlayerPanelBackTarget(TvPlayerPanel.QUALITY, TvPlayerPanel.SETTINGS)
        ).isEqualTo(TvPlayerPanel.SETTINGS)
        assertThat(
            tvPlayerPanelBackTarget(TvPlayerPanel.SETTINGS, TvPlayerPanel.NONE)
        ).isEqualTo(TvPlayerPanel.NONE)
    }

    @Test
    fun `movie subtitle uses only available metadata`() {
        assertThat(
            tvMoviePlaybackSubtitle(
                Movie(
                    playlistId = 1L,
                    name = "Synthetic Movie",
                    streamUrl = "https://example.test/movie.mp4",
                    year = 2026,
                    genre = "Drama"
                )
            )
        ).isEqualTo("2026 • Drama")
        assertThat(tvMoviePlaybackSubtitle(null)).isEmpty()
    }

    @Test
    fun `quality controls require an adaptive exoplayer source`() {
        assertThat(tvSupportsQualitySelection("VLC", true, 4)).isFalse()
        assertThat(tvSupportsQualitySelection("EXOPLAYER", false, 1)).isFalse()
        assertThat(tvSupportsQualitySelection("ExoPlayer", true, 1)).isTrue()
        assertThat(tvSupportsQualitySelection("EXOPLAYER", false, 3)).isTrue()
    }

    @Test
    fun `hidden vod direction is owned by seek without opening overlay navigation`() {
        assertThat(
            tvPlayerHiddenDirectionalAction(
                isLivePlayback = false,
                isSeekable = true,
                direction = TvPlayerPanelDirection.LEFT
            )
        ).isEqualTo(TvPlayerHiddenDirectionalAction.SEEK_BACKWARD)
        assertThat(
            tvPlayerHiddenDirectionalAction(
                isLivePlayback = false,
                isSeekable = true,
                direction = TvPlayerPanelDirection.RIGHT
            )
        ).isEqualTo(TvPlayerHiddenDirectionalAction.SEEK_FORWARD)
    }

    @Test
    fun `hidden live directions preserve channel and epg navigation`() {
        assertThat(
            tvPlayerHiddenDirectionalAction(
                isLivePlayback = true,
                isSeekable = false,
                direction = TvPlayerPanelDirection.LEFT
            )
        ).isEqualTo(TvPlayerHiddenDirectionalAction.OPEN_CHANNELS)
        assertThat(
            tvPlayerHiddenDirectionalAction(
                isLivePlayback = true,
                isSeekable = false,
                direction = TvPlayerPanelDirection.RIGHT
            )
        ).isEqualTo(TvPlayerHiddenDirectionalAction.OPEN_EPG)
    }

    @Test
    fun `hidden non seekable vod consumes direction without a seek action`() {
        assertThat(
            tvPlayerHiddenDirectionalAction(
                isLivePlayback = false,
                isSeekable = false,
                direction = TvPlayerPanelDirection.RIGHT
            )
        ).isEqualTo(TvPlayerHiddenDirectionalAction.NONE)
    }

    @Test
    fun `tv seek step label rounds partial seconds up`() {
        assertThat(tvSeekStepSeconds(10_000L)).isEqualTo(10L)
        assertThat(tvSeekStepSeconds(10_001L)).isEqualTo(11L)
        assertThat(tvSeekStepSeconds(0L)).isEqualTo(1L)
    }

    @Test
    fun `held seek accelerates without changing rapid individual press size`() {
        assertThat(tvSeekStepForRepeat(10_000L, repeatCount = 0)).isEqualTo(10_000L)
        assertThat(tvSeekStepForRepeat(10_000L, repeatCount = 4)).isEqualTo(10_000L)
        assertThat(tvSeekStepForRepeat(10_000L, repeatCount = 5)).isEqualTo(20_000L)
        assertThat(tvSeekStepForRepeat(10_000L, repeatCount = 12)).isEqualTo(40_000L)
    }

    @Test
    fun `right from the live categories panel is always owned by the panel transition`() {
        assertThat(
            tvPlayerPanelDirectionalAction(
                isLivePlayback = true,
                activePanel = TvPlayerPanel.CATEGORIES,
                direction = TvPlayerPanelDirection.RIGHT
            )
        ).isEqualTo(TvPlayerPanelDirectionalAction.OPEN_CHANNELS)
    }

    @Test
    fun `panel direction only switches between live channel and category panels`() {
        assertThat(
            tvPlayerPanelDirectionalAction(
                isLivePlayback = true,
                activePanel = TvPlayerPanel.CHANNELS,
                direction = TvPlayerPanelDirection.LEFT
            )
        ).isEqualTo(TvPlayerPanelDirectionalAction.OPEN_CATEGORIES)

        assertThat(
            tvPlayerPanelDirectionalAction(
                isLivePlayback = false,
                activePanel = TvPlayerPanel.CATEGORIES,
                direction = TvPlayerPanelDirection.RIGHT
            )
        ).isEqualTo(TvPlayerPanelDirectionalAction.NONE)
    }

    @Test
    fun `panel keys remain unique when provider metadata repeats labels`() {
        assertThat(tvStablePlayerPanelKeys(listOf("audio:1", "audio:1", "", "")))
            .containsExactly("audio:1#0", "audio:1#1", "option:2#0", "option:3#0")
            .inOrder()
    }

    @Test
    fun `track panel initially focuses selected enabled option`() {
        assertThat(
            tvInitialPlayerPanelFocusIndex(
                selected = listOf(false, true, false),
                enabled = listOf(true, true, true)
            )
        ).isEqualTo(1)
    }

    @Test
    fun `track panel focuses first enabled option when selection is absent`() {
        assertThat(
            tvInitialPlayerPanelFocusIndex(
                selected = listOf(false, false, false),
                enabled = listOf(false, true, true)
            )
        ).isEqualTo(1)
    }

    @Test
    fun `track panel can recover focus after async list replaces empty state`() {
        val beforeTracks = tvInitialPlayerPanelFocusIndex(
            selected = listOf(false),
            enabled = listOf(false)
        )
        val afterTracks = tvInitialPlayerPanelFocusIndex(
            selected = listOf(false, true),
            enabled = listOf(true, true)
        )

        assertThat(beforeTracks).isNull()
        assertThat(afterTracks).isEqualTo(1)
    }
}
