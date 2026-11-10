package com.idealplayer.app.core.database

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.model.PlaylistType
import org.junit.Test

class PlaylistMapperTest {

    @Test
    fun `unknown playlist type with server credentials maps to Xtream`() {
        val entity = PlaylistEntity(
            name = "Portal",
            type = "portal",
            serverUrl = "https://example.invalid",
            username = "user",
            password = "pass"
        )

        assertThat(entity.toModel().type).isEqualTo(PlaylistType.XTREAM_CODES)
    }

    @Test
    fun `unknown playlist type with file path maps to M3U file`() {
        val entity = PlaylistEntity(
            name = "File",
            type = "legacy_file",
            filePath = "/storage/emulated/0/playlist.m3u"
        )

        assertThat(entity.toModel().type).isEqualTo(PlaylistType.M3U_FILE)
    }

    @Test
    fun `blank playlist type falls back to M3U URL`() {
        val entity = PlaylistEntity(
            name = "URL",
            type = "",
            url = "https://example.invalid/list.m3u"
        )

        assertThat(entity.toModel().type).isEqualTo(PlaylistType.M3U_URL)
    }

    @Test
    fun `legacy file alias maps to M3U file without relying on file path`() {
        val entity = PlaylistEntity(
            name = "File alias",
            type = "file"
        )

        assertThat(entity.toModel().type).isEqualTo(PlaylistType.M3U_FILE)
    }

    @Test
    fun `legacy xc alias maps to Xtream without relying on credentials`() {
        val entity = PlaylistEntity(
            name = "XC alias",
            type = "xc"
        )

        assertThat(entity.toModel().type).isEqualTo(PlaylistType.XTREAM_CODES)
    }
}
