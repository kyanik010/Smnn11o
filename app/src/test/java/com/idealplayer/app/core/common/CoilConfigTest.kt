package com.idealplayer.app.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoilConfigTest {

    @Test
    fun imageMemoryCachePercent_usesCompactCacheForConstrainedDevices() {
        assertThat(imageMemoryCachePercent(isLowRamDevice = true, memoryClassMb = 512))
            .isEqualTo(0.12)
        assertThat(imageMemoryCachePercent(isLowRamDevice = false, memoryClassMb = 256))
            .isEqualTo(0.12)
    }

    @Test
    fun imageMemoryCachePercent_keepsBalancedCacheForLargerDevices() {
        assertThat(imageMemoryCachePercent(isLowRamDevice = false, memoryClassMb = 384))
            .isEqualTo(0.20)
    }
}
