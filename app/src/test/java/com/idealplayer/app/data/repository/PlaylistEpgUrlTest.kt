package com.idealplayer.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.model.Playlist
import com.idealplayer.app.core.model.PlaylistType
import org.junit.Test

class PlaylistEpgUrlTest {

    @Test
    fun `m3u get php url derives xtream xmltv url when header epg is missing`() {
        val epgUrl = resolveM3uEpgUrl(
            epgUrl = "",
            playlistUrl = "https://provider.example/get.php?username=user1&password=pass1&type=m3u_plus&output=ts"
        )

        assertThat(epgUrl).isEqualTo("https://provider.example/xmltv.php?username=user1&password=pass1")
    }

    @Test
    fun `explicit relative m3u epg url resolves against playlist url`() {
        val epgUrl = resolveM3uEpgUrl(
            epgUrl = "epg/xmltv.xml.gz",
            playlistUrl = "https://provider.example/lists/playlist.m3u"
        )

        assertThat(epgUrl).isEqualTo("https://provider.example/lists/epg/xmltv.xml.gz")
    }

    @Test
    fun `stream live url derives xtream xmltv url`() {
        val epgUrl = buildXtreamEpgUrlFromStreamUrl(
            "https://provider.example/live/user1/pass1/12345.ts"
        )

        assertThat(epgUrl).isEqualTo("https://provider.example/xmltv.php?username=user1&password=pass1")
    }

    @Test
    fun `stream headers do not block xtream xmltv discovery`() {
        val epgUrl = buildXtreamEpgUrlFromStreamUrl(
            "https://provider.example/live/user1/pass1/12345.ts|User-Agent=Example%20TV"
        )

        assertThat(epgUrl).isEqualTo("https://provider.example/xmltv.php?username=user1&password=pass1")
    }

    @Test
    fun `m3u epg urls include header playlist and stream candidates`() {
        val epgUrls = resolveM3uEpgUrls(
            epgUrls = listOf("epg/header.xml.gz"),
            playlistUrl = "https://provider.example/get.php?username=user1&password=pass1&type=m3u_plus",
            streamUrls = listOf("https://backup.example/live/user2/pass2/12345.m3u8")
        )

        assertThat(epgUrls).containsExactly(
            "https://provider.example/epg/header.xml.gz",
            "https://provider.example/xmltv.php?username=user1&password=pass1",
            "https://backup.example/xmltv.php?username=user2&password=pass2"
        ).inOrder()
    }

    @Test
    fun `m3u get php url derives xtream credentials`() {
        val credentials = buildXtreamCredentialsFromM3uPlaylistUrl(
            "https://provider.example/get.php?username=user1&password=pass1&type=m3u_plus&output=ts"
        )

        assertThat(credentials?.serverUrl).isEqualTo("https://provider.example")
        assertThat(credentials?.username).isEqualTo("user1")
        assertThat(credentials?.password).isEqualTo("pass1")
    }

    @Test
    fun `stream live url derives xtream credentials`() {
        val credentials = buildXtreamCredentialsFromStreamUrl(
            "https://provider.example/live/user1/pass1/12345.ts"
        )

        assertThat(credentials?.serverUrl).isEqualTo("https://provider.example")
        assertThat(credentials?.username).isEqualTo("user1")
        assertThat(credentials?.password).isEqualTo("pass1")
    }

    @Test
    fun `stream headers do not block xtream credential discovery`() {
        val credentials = buildXtreamCredentialsFromStreamUrl(
            "https://provider.example/live/user1/pass1/12345.ts|Referer=https%3A%2F%2Fportal.example%2F"
        )

        assertThat(credentials?.serverUrl).isEqualTo("https://provider.example")
        assertThat(credentials?.username).isEqualTo("user1")
        assertThat(credentials?.password).isEqualTo("pass1")
    }

    @Test
    fun `xtream playlist keeps explicit credentials`() {
        val credentials = resolveXtreamCredentials(
            playlist = Playlist(
                id = 1L,
                name = "Test",
                type = PlaylistType.XTREAM_CODES,
                serverUrl = "https://provider.example/player_api.php",
                username = "user1",
                password = "pass1"
            ),
            streamUrls = emptyList()
        )

        assertThat(credentials?.serverUrl).isEqualTo("https://provider.example")
        assertThat(credentials?.username).isEqualTo("user1")
        assertThat(credentials?.password).isEqualTo("pass1")
    }

}
