package com.idealplayer.app.data.parser

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.model.Channel
import kotlinx.serialization.json.Json
import org.junit.Test

class XtreamEpgParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parse xtream epg listings with base64 text`() {
        val response = json.parseToJsonElement(
            """
            {
              "epg_listings": [
                {
                  "title": "SGFiZXJsZXI=",
                  "description": "R3VuZGVt",
                  "start_timestamp": "1717430400",
                  "stop_timestamp": "1717434000"
                }
              ]
            }
            """.trimIndent()
        )

        val programs = parseXtreamEpgPrograms(
            response = response,
            channel = Channel(
                id = 1L,
                playlistId = 1L,
                streamId = 12345,
                name = "TR • TRT 1 FHD",
                streamUrl = "https://provider.example/live/user/pass/12345.ts",
                epgChannelId = "trt.1"
            )
        )

        assertThat(programs).hasSize(1)
        assertThat(programs[0].channelId).isEqualTo("trt.1")
        assertThat(programs[0].title).isEqualTo("Haberler")
        assertThat(programs[0].description).isEqualTo("Gundem")
        assertThat(programs[0].startTime).isEqualTo(1_717_430_400_000L)
        assertThat(programs[0].endTime).isEqualTo(1_717_434_000_000L)
    }

    @Test
    fun `plain xtream epg text is preserved`() {
        assertThat("Canli Yayin".decodeXtreamText()).isEqualTo("Canli Yayin")
    }
}
