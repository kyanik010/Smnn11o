package com.idealplayer.app.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtworkUrlsTest {

    @Test
    fun `invalid provider artwork values are rejected`() {
        listOf("", "null", " NULL ", "n/a", "undefined", "0").forEach { value ->
            assertThat(isUsableArtworkUrl(value)).isFalse()
        }
    }

    @Test
    fun `relative artwork resolves against provider server`() {
        assertThat(
            normalizeRemoteArtworkUrl("https://provider.example/player_api.php", "/images/poster.jpg")
        ).isEqualTo("https://provider.example/images/poster.jpg")
    }

    @Test
    fun `protocol relative artwork inherits provider scheme`() {
        assertThat(
            normalizeRemoteArtworkUrl("https://provider.example", "//cdn.example/poster.jpg")
        ).isEqualTo("https://cdn.example/poster.jpg")
    }
}
