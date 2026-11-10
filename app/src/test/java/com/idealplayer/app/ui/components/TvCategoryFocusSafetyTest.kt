package com.idealplayer.app.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TvCategoryFocusSafetyTest {

    @Test
    fun `provider category cannot collide with synthetic all key`() {
        assertThat(tvCategoryLazyKey("live", null))
            .isNotEqualTo(tvCategoryLazyKey("live", "__tv_all__"))
        assertThat(tvCategoryLazyKey("movies", null))
            .isNotEqualTo(tvCategoryLazyKey("movies", "__all_movies__"))
        assertThat(tvCategoryLazyKey("series", null))
            .isNotEqualTo(tvCategoryLazyKey("series", "__all_series__"))
    }

    @Test
    fun `category keys are stable and scoped per screen`() {
        assertThat(tvCategoryLazyKey("movies", "Drama"))
            .isEqualTo(tvCategoryLazyKey("movies", "Drama"))
        assertThat(tvCategoryLazyKey("movies", "Drama"))
            .isNotEqualTo(tvCategoryLazyKey("series", "Drama"))
    }
}
