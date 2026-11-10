package com.idealplayer.app.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerTrackAndSeekTest {

    @Test
    fun `absolute seek is clamped to finite duration`() {
        assertThat(resolveSeekPosition(-5_000L, 60_000L, isSeekable = true)).isEqualTo(0L)
        assertThat(resolveSeekPosition(25_000L, 60_000L, isSeekable = true)).isEqualTo(25_000L)
        assertThat(resolveSeekPosition(80_000L, 60_000L, isSeekable = true)).isEqualTo(60_000L)
    }

    @Test
    fun `relative forward and backward seek stay inside media bounds`() {
        assertThat(resolveRelativeSeekPosition(55_000L, 10_000L, 60_000L, true))
            .isEqualTo(60_000L)
        assertThat(resolveRelativeSeekPosition(4_000L, -10_000L, 60_000L, true))
            .isEqualTo(0L)
    }

    @Test
    fun `non seekable live window rejects seek`() {
        assertThat(resolveSeekPosition(10_000L, 0L, isSeekable = false)).isNull()
        assertThat(resolveRelativeSeekPosition(0L, 10_000L, 0L, isSeekable = false)).isNull()
    }

    @Test
    fun `missing track metadata produces visible fallback titles`() {
        assertThat(
            buildTrackTitle(TrackKind.AUDIO, 2, label = "", language = "")
        ).isEqualTo("Audio 2")
        assertThat(
            buildTrackTitle(TrackKind.SUBTITLE, 3, label = null, language = "und")
        ).isEqualTo("Subtitle 3")
    }

    @Test
    fun `default forced and hearing impaired subtitle flags remain visible`() {
        assertThat(
            buildTrackTitle(
                kind = TrackKind.SUBTITLE,
                ordinal = 1,
                label = "English",
                language = "eng",
                isDefault = true,
                isForced = true,
                isHearingImpaired = true
            )
        ).isEqualTo("English (Default, Forced, CC)")
    }

    @Test
    fun `dub role is included in audio title`() {
        assertThat(
            buildTrackTitle(
                kind = TrackKind.AUDIO,
                ordinal = 1,
                label = "Turkish",
                language = "tur",
                role = "Dub"
            )
        ).isEqualTo("Turkish (Dub)")
    }

    @Test
    fun `duplicate audio tracks are distinguished by codec and channels`() {
        val tracks = disambiguateTrackNames(
            listOf(
                TrackInfo(index = 10, name = "English", codec = "aac", channelCount = 2),
                TrackInfo(index = 11, name = "English", codec = "ac3", channelCount = 6)
            )
        )

        assertThat(tracks.map(TrackInfo::name)).containsExactly(
            "English • AAC • 2ch",
            "English • AC3 • 6ch"
        ).inOrder()
    }

    @Test
    fun `duplicate unknown subtitles are retained and numbered`() {
        val tracks = disambiguateTrackNames(
            listOf(
                TrackInfo(index = 20, name = "Subtitle"),
                TrackInfo(index = 21, name = "Subtitle")
            )
        )

        assertThat(tracks.map(TrackInfo::name)).containsExactly(
            "Subtitle • #1",
            "Subtitle • #2"
        ).inOrder()
    }

    @Test
    fun `libvlc selection resolves native id and rejects disabled entry`() {
        val selectableIds = listOf(7, 42)

        assertThat(resolveSelectableTrackId(selectableIds, 42)).isEqualTo(42)
        assertThat(resolveSelectableTrackId(selectableIds, -1)).isNull()
        assertThat(resolveSelectableTrackId(selectableIds, 1)).isNull()
    }

    @Test
    fun `libvlc rapid seek uses pending target until native time catches up`() {
        assertThat(
            vlcSeekBasePosition(
                nativePosition = 10_000L,
                pendingPosition = 30_000L,
                pendingAgeMs = 250L
            )
        ).isEqualTo(30_000L)
        assertThat(
            vlcSeekBasePosition(
                nativePosition = 31_000L,
                pendingPosition = 30_000L,
                pendingAgeMs = 3_500L
            )
        ).isEqualTo(31_000L)
    }
}
