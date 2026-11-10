package com.idealplayer.app.ui.components.a2

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class A2ArtworkTest {

    @Test
    fun `fallback artwork selection is stable and spans all approved scenes`() {
        val seeds = listOf("coast", "city", "documentary", "movie", "series", "channel")
        val firstPass = seeds.map(::a2ArtworkResource)

        assertThat(seeds.map(::a2ArtworkResource)).containsExactlyElementsIn(firstPass).inOrder()
        assertThat(firstPass.distinct()).hasSize(3)
    }
}
