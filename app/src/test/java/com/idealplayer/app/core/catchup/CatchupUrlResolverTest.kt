package com.idealplayer.app.core.catchup

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.model.Channel
import org.junit.Test
import java.time.Instant

class CatchupUrlResolverTest {

    private val resolver = CatchupUrlResolver()
    private val channel = Channel(
        playlistId = 1L,
        streamId = 42,
        name = "Synthetic Channel",
        streamUrl = "https://media.example.test/live/42.ts",
        catchupSource = "{utc}:{duration}:{start}"
    )

    @Test
    fun `template uses UTC timestamps`() {
        val start = Instant.parse("2026-06-29T12:34:00Z").toEpochMilli()

        val result = resolver.resolve(channel, start, 60)

        assertThat(result).isEqualTo("1782736440:60:2026-06-29:12-34")
    }

    @Test
    fun `xtream catchup URL uses UTC start and duration`() {
        val start = Instant.parse("2026-06-29T12:34:00Z").toEpochMilli()
        val xtreamChannel = channel.copy(catchupSource = "")

        val result = resolver.resolve(
            channel = xtreamChannel,
            startTimeMs = start,
            durationMins = 45,
            serverUrl = "https://provider.example/",
            username = "user",
            password = "pass"
        )

        assertThat(result).isEqualTo(
            "https://provider.example/timeshift/user/pass/45/2026-06-29:12-34/42.ts"
        )
    }
}
