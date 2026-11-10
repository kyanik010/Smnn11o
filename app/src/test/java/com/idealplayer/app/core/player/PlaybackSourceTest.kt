package com.idealplayer.app.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackSourceTest {

    @Test
    fun `plain URL uses the application user agent`() {
        val source = parsePlaybackSource(" https://media.example.test/live/1.m3u8. ")

        assertThat(source.url).isEqualTo("https://media.example.test/live/1.m3u8")
        assertThat(source.userAgent).isEqualTo("IdealPlayer/Android")
        assertThat(source.headers).isEmpty()
    }

    @Test
    fun `pipe headers are decoded and canonicalized`() {
        val source = parsePlaybackSource(
            "https://media.example.test/live/1.m3u8|user-agent=Example%20TV&referrer=https%3A%2F%2Fportal.example.test%2F"
        )

        assertThat(source.url).isEqualTo("https://media.example.test/live/1.m3u8")
        assertThat(source.headers).containsExactly(
            "User-Agent", "Example TV",
            "Referer", "https://portal.example.test/"
        )
        assertThat(source.requestProperties).doesNotContainKey("User-Agent")
    }

    @Test
    fun `header merge preserves existing values and overrides matching names`() {
        val value = withPlaybackHeaders(
            "https://media.example.test/movie/1.mkv|User-Agent=Old",
            mapOf("http-user-agent" to "New Agent", "Origin" to "https://portal.example.test")
        )

        val source = parsePlaybackSource(value)
        assertThat(source.headers).containsExactly(
            "User-Agent", "New Agent",
            "Origin", "https://portal.example.test"
        )
    }

    @Test
    fun `unsafe header values are ignored`() {
        val source = parsePlaybackSource(
            "https://media.example.test/live/1.ts|User-Agent=Safe&Injected=bad%0D%0AX-Test%3Ayes"
        )

        assertThat(source.headers).containsExactly("User-Agent", "Safe")
    }

    @Test
    fun `profile key never contains URL credentials`() {
        val rawUrl = "https://user:secret@media.example.test/live/user/secret/1.ts"
        val key = playbackProfileKey(rawUrl)

        assertThat(key).hasLength(64)
        assertThat(key).doesNotContain("user")
        assertThat(key).doesNotContain("secret")
    }

    @Test
    fun `source summary fingerprints host and omits credentials and path`() {
        val summary = playbackSourceSummary(
            "https://user:secret@media.example.test:8443/live/user/secret/1.ts"
        )

        assertThat(summary.first).isEqualTo("https")
        assertThat(summary.second).hasLength(12)
        assertThat(summary.second).doesNotContain("media")
        assertThat(summary.toString()).doesNotContain("secret")
    }
}
