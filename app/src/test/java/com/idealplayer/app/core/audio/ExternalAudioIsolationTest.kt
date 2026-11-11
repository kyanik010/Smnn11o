package com.idealplayer.app.core.audio

import com.idealplayer.app.core.model.Channel
import com.idealplayer.app.core.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAudioIsolationTest {

    @Test
    fun externalAudioCandidateDiscovery_filtersOutCurrentPlayingChannel() {
        val ch1 = Channel(id = 101, playlistId = 1, streamId = 501, name = "beIN SPORTS 1 HD", groupTitle = "SPORTS", streamUrl = "http://srv/live/u/p/501.ts")
        val ch2 = Channel(id = 102, playlistId = 1, streamId = 502, name = "beIN SPORTS 2 HD", groupTitle = "SPORTS", streamUrl = "http://srv/live/u/p/502.ts")
        val ch3 = Channel(id = 103, playlistId = 1, streamId = 503, name = "MBC 1", groupTitle = "ARABIC", streamUrl = "http://srv/live/u/p/503.ts")

        val channels = listOf(ch1, ch2, ch3)
        val currentPlayingChannelId = 101L

        val candidates = channels.filter { ch ->
            if (ch.id == currentPlayingChannelId) {
                false
            } else {
                val group = AudioSourceClassifier.classify(ch.name, ch.groupTitle)
                group != AudioChannelGroup.OTHER && ch.streamId > 0
            }
        }

        // ch1 is excluded because it's the video channel currently playing
        // ch3 is excluded because it is not beIN or ALWAN
        // Only ch2 remains
        assertEquals(1, candidates.size)
        assertEquals(102L, candidates[0].id)
    }

    @Test
    fun externalAudioSyncOffset_preservesBounds() {
        assertEquals(10000L, 12000L.coerceIn(-10000L, 10000L))
        assertEquals(-10000L, (-15000L).coerceIn(-10000L, 10000L))
        assertEquals(500L, 500L.coerceIn(-10000L, 10000L))
    }

    @Test
    fun retryDelayCalculation_returnsExpectedIntervals() {
        assertEquals(1000L, externalAudioRetryDelayMs(1))
        assertEquals(2000L, externalAudioRetryDelayMs(2))
        assertEquals(4000L, externalAudioRetryDelayMs(3))
        assertEquals(8000L, externalAudioRetryDelayMs(4))
        assertEquals(16000L, externalAudioRetryDelayMs(5))
        assertEquals(30000L, externalAudioRetryDelayMs(6))
    }
}
