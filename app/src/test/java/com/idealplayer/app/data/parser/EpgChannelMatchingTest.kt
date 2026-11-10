package com.idealplayer.app.data.parser

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.model.Channel
import org.junit.Test

class EpgChannelMatchingTest {

    @Test
    fun `lookup keys include epg id name and stream id variants`() {
        val channel = Channel(
            playlistId = 1L,
            streamId = 101,
            name = "TRT 1 HD",
            streamUrl = "http://example.test/live/101",
            epgChannelId = "trt.1"
        )

        val keys = epgLookupKeysForChannel(channel)

        assertThat(keys).containsAtLeast(
            "trt.1",
            "trt-1",
            "trt1",
            "TRT 1 HD",
            "trt-1-hd",
            "trt1hd",
            "101"
        )
    }

    @Test
    fun `lookup keys strip playlist region and raw suffix from channel names`() {
        val channel = Channel(
            playlistId = 1L,
            name = "TR • Show Tv RAW",
            groupTitle = "TR ✨ Raw",
            streamUrl = "http://example.test/live/show"
        )

        val keys = epgLookupKeysForChannel(channel)

        assertThat(keys).containsAtLeast(
            "tr-show-tv-raw",
            "show-tv",
            "showtv",
            "show"
        )
    }

    @Test
    fun `lookup keys treat tv suffix as optional after region suffix is stripped`() {
        val keys = epgLookupKeys("SHOW TV.tr")

        assertThat(keys).containsAtLeast(
            "show-tv",
            "showtv",
            "show"
        )
    }

    @Test
    fun `channel id map points aliases to shared lookup id`() {
        val channel = Channel(
            playlistId = 1L,
            streamId = 101,
            name = "TRT 1 HD",
            streamUrl = "http://example.test/live/101",
            epgChannelId = "trt.1"
        )

        val map = buildEpgChannelIdMap(listOf(channel))

        assertThat(map["trt-1"]).isEqualTo("trt-1")
        assertThat(map["trt1hd"]).isEqualTo("trt-1")
        assertThat(map["101"]).isEqualTo("trt-1")
    }

    @Test
    fun `resolve channel id can match XMLTV display name`() {
        val resolved = resolveEpgChannelId(
            xmlChannelId = "xml-random-id",
            displayNames = listOf("TRT 1 HD"),
            channelIdMap = mapOf("trt-1-hd" to "trt.1")
        )

        assertThat(resolved).isEqualTo("trt.1")
    }

    @Test
    fun `resolve channel id matches playlist channel without tv suffix to open epg id`() {
        val channel = Channel(
            playlistId = 1L,
            name = "TR • Show FHD",
            groupTitle = "TR ✨ Ulusal",
            streamUrl = "http://example.test/live/show"
        )

        val resolved = resolveEpgChannelId(
            xmlChannelId = "SHOW TV.tr",
            displayNames = listOf("SHOW TV"),
            channelIdMap = buildEpgChannelIdMap(listOf(channel))
        )

        assertThat(resolved).isEqualTo("show")
    }

    @Test
    fun `resolve channel id matches playlist channel without tv suffix to epg share id`() {
        val channel = Channel(
            playlistId = 1L,
            name = "TR • Star HD",
            groupTitle = "TR ✨ Ulusal",
            streamUrl = "http://example.test/live/star"
        )

        val resolved = resolveEpgChannelId(
            xmlChannelId = "STAR.TV.HD.tr",
            displayNames = listOf("STAR TV HD"),
            channelIdMap = buildEpgChannelIdMap(listOf(channel))
        )

        assertThat(resolved).isEqualTo("star")
    }

    @Test
    fun `duplicate channel variants share the same xmltv program id`() {
        val channels = listOf(
            Channel(
                playlistId = 1L,
                name = "TR • Star FHD",
                groupTitle = "TR ✨ Ulusal",
                streamUrl = "http://example.test/live/star-fhd"
            ),
            Channel(
                playlistId = 1L,
                name = "TR • Star Tv FHD",
                groupTitle = "TR ✨ Ulusal",
                streamUrl = "http://example.test/live/star-tv-fhd"
            )
        )

        val resolved = resolveEpgChannelId(
            xmlChannelId = "STAR.TV.HD.tr",
            displayNames = listOf("STAR TV HD"),
            channelIdMap = buildEpgChannelIdMap(channels)
        )

        assertThat(resolved).isEqualTo("star")
        channels.forEach { channel ->
            assertThat(epgLookupKeysForChannel(channel)).contains("star")
        }
    }
}
