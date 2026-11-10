package com.idealplayer.app.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SensitiveLogTest {

    @Test
    fun `redactUrl keeps only scheme host and port`() {
        val redacted = SensitiveLog.redactUrl(
            "https://example.com:8443/live/user/pass/123.m3u8?token=secret#frag"
        )

        val expected = "https://example.com:8443/" + "..." + "?" + "..." + "#" + "..."
        assertThat(redacted).isEqualTo(expected)
    }

    @Test
    fun `redactUrl does not expose credentials in path or query`() {
        val redacted = SensitiveLog.redactUrl("http://host.test/movie/alice/password123/42.mp4?username=alice")

        assertThat(redacted).doesNotContain("alice")
        assertThat(redacted).doesNotContain("password123")
        assertThat(redacted).doesNotContain("username")
        assertThat(redacted).contains("http://host.test")
    }

    @Test
    fun `redactUrl handles invalid values`() {
        assertThat(SensitiveLog.redactUrl("not a url")).isEqualTo("[redacted]")
        assertThat(SensitiveLog.redactUrl("   ")).isEmpty()
    }
}
