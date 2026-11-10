package com.idealplayer.app.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class CategoryOrderingTest {

    @Test
    fun `orderCategoryNames preserves first source order`() {
        val categories = listOf(
            "Sports 10",
            "Movies",
            "sports 2",
            "News",
            "Sports 1"
        )

        val result = orderCategoryNames(categories, Locale.US)

        assertThat(result).containsExactly(
            "Sports 10",
            "Movies",
            "sports 2",
            "News",
            "Sports 1"
        ).inOrder()
    }

    @Test
    fun `orderCategoryNames removes blank and exact duplicate names`() {
        val result = orderCategoryNames(
            listOf("News", " ", "Movies", "News", ""),
            Locale.US
        )

        assertThat(result).containsExactly("News", "Movies").inOrder()
    }
}
