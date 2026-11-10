package com.idealplayer.app.data.parser

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class XtreamVodMetadataTest {

    @Test
    fun `parses provider poster and backdrop from flexible vod info`() {
        val element = Json.parseToJsonElement(
            """
            {
              "info": {
                "movie_image": "/images/movie.jpg",
                "backdrop_path": ["//cdn.example.test/backdrop.jpg"],
                "plot": "Synthetic plot",
                "duration": "01:42:00",
                "rating": "7.4",
                "tmdb_id": "123"
              },
              "movie_data": {"stream_id": 42}
            }
            """.trimIndent()
        )

        val metadata = parseXtreamVodMetadata(element, "https://provider.example/player_api.php")

        assertThat(metadata?.posterUrl).isEqualTo("https://provider.example/images/movie.jpg")
        assertThat(metadata?.backdropUrl).isEqualTo("https://cdn.example.test/backdrop.jpg")
        assertThat(metadata?.duration).isEqualTo(102)
        assertThat(metadata?.rating).isEqualTo(7.4)
        assertThat(metadata?.tmdbId).isEqualTo(123)
    }

    @Test
    fun `falls back to movie data and ignores null artwork`() {
        val element = Json.parseToJsonElement(
            """{"info":{"movie_image":"null"},"movie_data":{"stream_icon":"https://img.example.test/poster.jpg"}}"""
        )

        val metadata = parseXtreamVodMetadata(element, "https://provider.example")

        assertThat(metadata?.posterUrl).isEqualTo("https://img.example.test/poster.jpg")
    }
}
