package com.idealplayer.app.metadata

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.network.dto.TmdbDetailResponse
import org.junit.Test

class MetadataIdentityMatchTest {

    @Test
    fun `direct id accepts matching localized or original identity`() {
        assertThat(
            isPlausibleDirectMetadataMatch(
                requestedTitle = "The Matrix (1999)",
                requestedYear = 1999,
                detail = TmdbDetailResponse(
                    id = 603,
                    title = "Matrix",
                    originalTitle = "The Matrix",
                    releaseDate = "1999-03-30"
                )
            )
        ).isTrue()
    }

    @Test
    fun `direct id rejects unrelated title even when year matches`() {
        assertThat(
            isPlausibleDirectMetadataMatch(
                requestedTitle = "Synthetic Space Journey (2020)",
                requestedYear = 2020,
                detail = TmdbDetailResponse(
                    id = 44,
                    title = "Unrelated Family Comedy",
                    releaseDate = "2020-05-10"
                )
            )
        ).isFalse()
    }

    @Test
    fun `direct id rejects a distant remake year with the same title`() {
        assertThat(
            isPlausibleDirectMetadataMatch(
                requestedTitle = "Synthetic Story (1990)",
                requestedYear = 1990,
                detail = TmdbDetailResponse(
                    id = 91,
                    title = "Synthetic Story",
                    releaseDate = "2020-01-01"
                )
            )
        ).isFalse()
    }

    @Test
    fun `bilingual provider title can match either title half`() {
        assertThat(
            metadataTitleSimilarity(
                requestedTitle = "Sentetik Yol + Synthetic Road (2024)",
                candidateTitle = "Synthetic Road",
                candidateOriginalTitle = null
            )
        ).isEqualTo(1.0)
    }
}
