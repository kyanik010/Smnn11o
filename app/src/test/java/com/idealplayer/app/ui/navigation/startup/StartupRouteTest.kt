package com.idealplayer.app.ui.navigation.startup

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.datastore.AppSettings
import com.idealplayer.app.core.model.Playlist
import com.idealplayer.app.core.model.PlaylistType
import com.idealplayer.app.ui.navigation.Routes
import org.junit.Test

class StartupRouteTest {

    @Test
    fun `returns playlists when there is no usable playlist`() {
        val playlists = listOf(
            Playlist(
                id = 1,
                name = "Broken",
                type = PlaylistType.M3U_URL,
                url = "",
                channelCount = 10
            )
        )

        val decision = resolveStartupDecision(playlists, AppSettings())

        assertThat(decision.targetRoute).isEqualTo(Routes.PLAYLISTS)
        assertThat(decision.playlistToActivate).isNull()
    }

    @Test
    fun `prefers last playlist when enabled and usable`() {
        val first = playablePlaylist(id = 1, active = true)
        val last = playablePlaylist(id = 2, active = false)

        val decision = resolveStartupDecision(
            playlists = listOf(first, last),
            settings = AppSettings(openLastPlaylist = true, lastPlaylistId = 2)
        )

        assertThat(decision.targetRoute).isEqualTo(Routes.HOME)
        assertThat(decision.playlistToActivate).isEqualTo(2L)
    }

    @Test
    fun `falls back to active playlist when last playlist is disabled`() {
        val active = playablePlaylist(id = 1, active = true)
        val other = playablePlaylist(id = 2, active = false)

        val decision = resolveStartupDecision(
            playlists = listOf(active, other),
            settings = AppSettings(openLastPlaylist = false, lastPlaylistId = 2)
        )

        assertThat(decision.targetRoute).isEqualTo(Routes.HOME)
        assertThat(decision.playlistToActivate).isNull()
    }

    private fun playablePlaylist(id: Long, active: Boolean): Playlist {
        return Playlist(
            id = id,
            name = "Playlist $id",
            type = PlaylistType.M3U_URL,
            url = "https://example.com/$id.m3u",
            isActive = active,
            channelCount = 12
        )
    }
}
