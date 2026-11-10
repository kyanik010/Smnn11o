package com.idealplayer.app.ui.details

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DetailPresentationSafetyTest {

    @Test
    fun `provider rating is constrained to renderable range`() {
        assertThat(normalizeContentRating(-5.0)).isEqualTo(0.0)
        assertThat(normalizeContentRating(7.5)).isEqualTo(7.5)
        assertThat(normalizeContentRating(42.0)).isEqualTo(10.0)
    }

    @Test
    fun `non finite provider rating cannot reach star repeat counts`() {
        assertThat(normalizeContentRating(Double.NaN)).isEqualTo(0.0)
        assertThat(normalizeContentRating(Double.POSITIVE_INFINITY)).isEqualTo(0.0)
        assertThat(normalizeContentRating(Double.NEGATIVE_INFINITY)).isEqualTo(0.0)
    }
}
