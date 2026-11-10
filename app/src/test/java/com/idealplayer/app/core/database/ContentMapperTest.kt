package com.idealplayer.app.core.database

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentMapperTest {

    @Test
    fun `movie mapper preserves latest ordering fields`() {
        val entity = MovieEntity(
            playlistId = 1L,
            name = "Synthetic Movie",
            streamUrl = "http://stream.example.com/movie/synthetic.mkv",
            addedAt = 1234L,
            sourceOrder = 7
        )

        val model = entity.toModel()
        val restored = model.toEntity()

        assertThat(model.addedAt).isEqualTo(1234L)
        assertThat(model.sourceOrder).isEqualTo(7)
        assertThat(restored.addedAt).isEqualTo(1234L)
        assertThat(restored.sourceOrder).isEqualTo(7)
    }

    @Test
    fun `series mapper preserves latest ordering fields`() {
        val entity = SeriesEntity(
            playlistId = 1L,
            name = "Synthetic Series",
            addedAt = 5678L,
            sourceOrder = 3
        )

        val model = entity.toModel()
        val restored = model.toEntity()

        assertThat(model.addedAt).isEqualTo(5678L)
        assertThat(model.sourceOrder).isEqualTo(3)
        assertThat(restored.addedAt).isEqualTo(5678L)
        assertThat(restored.sourceOrder).isEqualTo(3)
    }

    @Test
    fun `legacy and malformed content types do not crash Flow mappers`() {
        val legacyCategory = CategoryEntity(
            playlistId = 1L,
            categoryId = 1,
            name = "Legacy VOD",
            contentType = "vod"
        )
        val malformedHistory = WatchHistoryEntity(
            contentId = 9L,
            contentType = "future_type",
            title = "Synthetic",
            streamUrl = "https://stream.example.test/item"
        )

        assertThat(legacyCategory.toModel().contentType).isEqualTo(com.idealplayer.app.core.model.ContentType.MOVIE)
        assertThat(malformedHistory.toModel().contentType).isEqualTo(com.idealplayer.app.core.model.ContentType.LIVE)
    }
}
