package com.idealplayer.app.core.worker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CatalogRefreshWorkerTest {

    @Test
    fun `foreground catalog refresh waits for the first UI render`() {
        assertThat(catalogRefreshInitialDelaySeconds(isTv = false)).isAtLeast(10L)
        assertThat(catalogRefreshInitialDelaySeconds(isTv = true)).isAtLeast(90L)
        assertThat(catalogRefreshInitialDelaySeconds(isTv = true))
            .isGreaterThan(catalogRefreshInitialDelaySeconds(isTv = false))
    }
}
