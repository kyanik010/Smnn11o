package com.idealplayer.app.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StringUtilsTest {

    @Test
    fun `normalizeTitle removes special characters and normalizes whitespace`() {
        assertThat(StringUtils.normalizeTitle("The Matrix (1999)")).isEqualTo("the matrix 1999")
        assertThat(StringUtils.normalizeTitle("  Hello  World  ")).isEqualTo("hello world")
        assertThat(StringUtils.normalizeTitle("Café")).isEqualTo("cafe")
        assertThat(StringUtils.normalizeTitle("Işık İçerde Çağrı")).isEqualTo("isik icerde cagri")
    }

    @Test
    fun `fuzzyMatch returns true for exact matches`() {
        assertThat(StringUtils.fuzzyMatch("The Matrix", "The Matrix")).isTrue()
    }

    @Test
    fun `fuzzyMatch returns true for close matches`() {
        assertThat(StringUtils.fuzzyMatch("The Matrix", "the matrix")).isTrue()
        assertThat(StringUtils.fuzzyMatch("Matrix", "The Matrix")).isTrue()
    }

    @Test
    fun `fuzzyMatch returns false for unrelated strings`() {
        assertThat(StringUtils.fuzzyMatch("The Matrix", "Inception")).isFalse()
    }

    @Test
    fun `formatDuration formats correctly`() {
        assertThat(StringUtils.formatDuration(0)).isEqualTo("00:00")
        assertThat(StringUtils.formatDuration(61000)).isEqualTo("01:01")
        assertThat(StringUtils.formatDuration(3661000)).isEqualTo("1:01:01")
    }

    @Test
    fun `formatDurationMinutes formats correctly`() {
        assertThat(StringUtils.formatDurationMinutes(90)).isEqualTo("1h 30m")
        assertThat(StringUtils.formatDurationMinutes(45)).isEqualTo("45m")
    }

    @Test
    fun `extractYear extracts year from string`() {
        assertThat(StringUtils.extractYear("2023-01-15")).isEqualTo(2023)
        assertThat(StringUtils.extractYear("Released in 1999")).isEqualTo(1999)
        assertThat(StringUtils.extractYear("No year here")).isNull()
        assertThat(StringUtils.extractYear(null)).isNull()
    }

    @Test
    fun `cleanTitleForSearch removes provider schedule and bucket noise`() {
        assertThat(StringUtils.cleanTitleForSearch("TR | FİLM: Annemin Yarası (2016) [PAZAR]"))
            .isEqualTo("Annemin Yarası")
        assertThat(StringUtils.cleanTitleForSearch("VOD / The Matrix [4K]"))
            .isEqualTo("The Matrix")
    }

    @Test
    fun `cleanTitleForSearch keeps meaningful title prefixes`() {
        assertThat(StringUtils.cleanTitleForSearch("Tron: Legacy (2010)"))
            .isEqualTo("Tron Legacy")
    }

    @Test
    fun `cleanTitleForSearch removes standalone lookup year and plus separators`() {
        assertThat(StringUtils.cleanTitleForSearch("Savas Yolu + Warpath 2020 1080p"))
            .isEqualTo("Savas Yolu Warpath")
    }

    @Test
    fun `metadata search variants preserve full title and bilingual alternatives`() {
        assertThat(
            StringUtils.metadataSearchTitleVariants(
                "TR | Sentetik Yol + Synthetic Road (2024) [4K]"
            )
        ).containsExactly(
            "Sentetik Yol Synthetic Road",
            "Sentetik Yol",
            "Synthetic Road"
        ).inOrder()
    }
}
