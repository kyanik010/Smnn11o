package com.idealplayer.app.core.worker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards the conservative health policy (K-3): a working stream must never be classified
 * as DEAD just because a probe returned an ambiguous or transient status.
 */
class StreamHealthTest {

    @Test
    fun `2xx and 3xx are alive`() {
        listOf(200, 204, 206, 301, 302, 307, 399).forEach { code ->
            assertThat(classifyStreamHealth(code)).isEqualTo(StreamHealth.ALIVE)
        }
    }

    @Test
    fun `auth method and rate-limit responses prove the stream exists`() {
        listOf(401, 403, 405, 429).forEach { code ->
            assertThat(classifyStreamHealth(code)).isEqualTo(StreamHealth.ALIVE)
        }
    }

    @Test
    fun `only not-found and gone are dead`() {
        assertThat(classifyStreamHealth(404)).isEqualTo(StreamHealth.DEAD)
        assertThat(classifyStreamHealth(410)).isEqualTo(StreamHealth.DEAD)
    }

    @Test
    fun `server errors and other 4xx are unknown and do not flip state`() {
        listOf(400, 408, 409, 451, 500, 502, 503, 504).forEach { code ->
            assertThat(classifyStreamHealth(code)).isEqualTo(StreamHealth.UNKNOWN)
        }
    }

    @Test
    fun `per run probe cap stays within a practical worker budget`() {
        assertThat(StreamHealthCheckWorker.MAX_CHANNELS_PER_RUN).isAtMost(500)
    }

    @Test
    fun `large playlists continue from the next unchecked offset`() {
        assertThat(
            nextStreamHealthOffset(
                startOffset = 0,
                checkedChannels = StreamHealthCheckWorker.MAX_CHANNELS_PER_RUN,
                totalChannels = 1_200
            )
        ).isEqualTo(StreamHealthCheckWorker.MAX_CHANNELS_PER_RUN)
        assertThat(
            nextStreamHealthOffset(
                startOffset = 1_000,
                checkedChannels = 200,
                totalChannels = 1_200
            )
        ).isNull()
    }
}
