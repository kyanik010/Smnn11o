package com.idealplayer.app.ui.player

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.model.Channel
import org.junit.Test

class LiveChannelNavigationTest {

    private val channels = listOf(1L, 2L, 3L).map(::channel)

    @Test
    fun `repeated next advances from latest requested channel`() {
        val second = adjacentLiveChannel(channels, anchorChannelId = 1L, offset = 1)
        val third = adjacentLiveChannel(channels, anchorChannelId = second?.id, offset = 1)

        assertThat(second?.id).isEqualTo(2L)
        assertThat(third?.id).isEqualTo(3L)
    }

    @Test
    fun `navigation does not wrap or guess an unknown anchor`() {
        assertThat(adjacentLiveChannel(channels, anchorChannelId = 3L, offset = 1)).isNull()
        assertThat(adjacentLiveChannel(channels, anchorChannelId = 99L, offset = 1)).isNull()
    }

    @Test
    fun `latest request wins over switching target and current channel`() {
        assertThat(
            liveNavigationAnchor(
                currentChannelId = 1L,
                switchingTargetChannelId = 2L,
                latestRequestedChannelId = 3L
            )
        ).isEqualTo(3L)
        assertThat(
            liveNavigationAnchor(
                currentChannelId = 1L,
                switchingTargetChannelId = 2L,
                latestRequestedChannelId = null
            )
        ).isEqualTo(2L)
    }

    @Test
    fun `channel selected from another category retains that category for navigation`() {
        val oldCategory = listOf(channel(1L), channel(2L))
        val selectedCategory = listOf(channel(10L), channel(11L), channel(12L))

        val navigationChannels = liveNavigationChannelsForTarget(
            targetChannel = selectedCategory[1],
            browserChannels = selectedCategory,
            retainedChannels = emptyList(),
            sessionChannels = oldCategory
        )

        assertThat(adjacentLiveChannel(navigationChannels, 11L, -1)?.id).isEqualTo(10L)
        assertThat(adjacentLiveChannel(navigationChannels, 11L, 1)?.id).isEqualTo(12L)
    }

    private fun channel(id: Long) = Channel(
        id = id,
        playlistId = 1L,
        name = "Synthetic $id",
        streamUrl = "https://example.invalid/live/$id.ts"
    )
}
