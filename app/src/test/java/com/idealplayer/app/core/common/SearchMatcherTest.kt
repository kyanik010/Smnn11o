package com.idealplayer.app.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchMatcherTest {

    @Test
    fun `rank matches titles without exact punctuation or spacing`() {
        val titles = listOf("Spider-Man: No Way Home", "The Matrix", "Inception")

        val results = SearchMatcher.rank(
            query = "spiderman",
            items = titles,
            primary = { it }
        )

        assertThat(results).containsExactly("Spider-Man: No Way Home")
    }

    @Test
    fun `rank matches Turkish characters from ascii query`() {
        val titles = listOf("Kurtlar Vadisi", "Çağrı", "İçerde")

        val results = SearchMatcher.rank(
            query = "cagri",
            items = titles,
            primary = { it }
        )

        assertThat(results).containsExactly("Çağrı")
    }

    @Test
    fun `rank matches partial multi word prefixes`() {
        val titles = listOf("Harry Potter and the Sorcerer's Stone", "The Matrix", "Inception")

        val results = SearchMatcher.rank(
            query = "har pot",
            items = titles,
            primary = { it }
        )

        assertThat(results.first()).isEqualTo("Harry Potter and the Sorcerer's Stone")
    }

    @Test
    fun `rank matches words out of order`() {
        val titles = listOf("Harry Potter and the Chamber of Secrets", "Potter's Field", "The Matrix")

        val results = SearchMatcher.rank(
            query = "potter harry",
            items = titles,
            primary = { it }
        )

        assertThat(results.first()).isEqualTo("Harry Potter and the Chamber of Secrets")
    }

    @Test
    fun `rank tolerates small typos`() {
        val titles = listOf("Green National HD", "Great Movies", "News International")

        val results = SearchMatcher.rank(
            query = "gren neshnal",
            items = titles,
            primary = { it }
        )

        assertThat(results.first()).isEqualTo("Green National HD")
    }

    @Test
    fun `rank matches acronyms`() {
        val titles = listOf("Game of Thrones", "Lord of the Rings", "The Matrix")

        val results = SearchMatcher.rank(
            query = "got",
            items = titles,
            primary = { it }
        )

        assertThat(results).containsExactly("Game of Thrones")
    }

    @Test
    fun `rank supports single character prefixes`() {
        val titles = listOf("ATV", "Show TV", "TRT 1")

        val results = SearchMatcher.rank(
            query = "a",
            items = titles,
            primary = { it }
        )

        assertThat(results).containsExactly("ATV")
    }

    @Test
    fun `rank matches iptv channel names with country prefixes and quality suffixes`() {
        val titles = listOf("TR • TRT 1 FHD", "TR : ATV HD", "TR • Show FHD")

        val trtResults = SearchMatcher.rank(
            query = "trt",
            items = titles,
            primary = { it }
        )
        val atvResults = SearchMatcher.rank(
            query = "atv",
            items = titles,
            primary = { it }
        )

        assertThat(trtResults).containsExactly("TR • TRT 1 FHD")
        assertThat(atvResults).containsExactly("TR : ATV HD")
    }

    @Test
    fun `rank uses category and year as secondary search fields`() {
        data class Item(val title: String, val category: String, val year: Int)
        val items = listOf(
            Item("Morning News", "Live TV", 0),
            Item("The Matrix", "Sci-Fi", 1999),
            Item("The Matrix Reloaded", "Sci-Fi", 2003)
        )

        val results = SearchMatcher.rank(
            query = "1999",
            items = items,
            primary = { it.title },
            secondary = { listOf(it.category) },
            year = { it.year }
        )

        assertThat(results).containsExactly(Item("The Matrix", "Sci-Fi", 1999))
    }

    @Test
    fun `rank combines title and year terms`() {
        data class Item(val title: String, val year: Int)
        val items = listOf(
            Item("Different Movie", 1999),
            Item("The Matrix", 1999),
            Item("The Matrix Reloaded", 2003)
        )

        val results = SearchMatcher.rank(
            query = "matrix 1999",
            items = items,
            primary = { it.title },
            year = { it.year }
        )

        assertThat(results.first()).isEqualTo(Item("The Matrix", 1999))
    }

    @Test
    fun `rank does not include unrelated results`() {
        val titles = listOf("The Matrix", "Inception", "Interstellar")

        val results = SearchMatcher.rank(
            query = "football",
            items = titles,
            primary = { it }
        )

        assertThat(results).isEmpty()
    }
}
