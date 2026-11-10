package com.idealplayer.app.data.parser

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.core.player.parsePlaybackSource
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class M3uParserTest {

    private lateinit var parser: M3uParser

    @Before
    fun setup() {
        parser = M3uParser()
    }

    @Test
    fun `parse valid M3U with live channels`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="ch1" tvg-logo="http://logo.png" group-title="News",CNN
            http://stream.example.com/live/cnn.m3u8
            #EXTINF:-1 tvg-id="ch2" tvg-logo="http://logo2.png" group-title="Sports",ESPN
            http://stream.example.com/live/espn.m3u8
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)
        assertThat(result.channels).hasSize(2)
        assertThat(result.channels[0].name).isEqualTo("CNN")
        assertThat(result.channels[0].groupTitle).isEqualTo("News")
        assertThat(result.channels[0].logoUrl).isEqualTo("http://logo.png")
        assertThat(result.channels[0].epgChannelId).isEqualTo("ch1")
        assertThat(result.channels[1].name).isEqualTo("ESPN")
    }

    @Test
    fun `parse M3U header epg url`() {
        val m3u = """
            #EXTM3U x-tvg-url="https://epg.example.test/xmltv.xml.gz"
            #EXTINF:-1 tvg-id="show.tv" group-title="TR",TR • Show Tv RAW
            http://stream.example.com/live/show.m3u8
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)

        assertThat(result.epgUrl).isEqualTo("https://epg.example.test/xmltv.xml.gz")
        assertThat(result.channels).hasSize(1)
    }

    @Test
    fun `parse M3U header epg url from url tvg alias with single quotes`() {
        val m3u = """
            #EXTM3U url-tvg='https://epg.example.test/xmltv.xml'
            #EXTINF:-1 tvg-id="show.tv" group-title="TR",TR • Show Tv RAW
            http://stream.example.com/live/show.m3u8
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)

        assertThat(result.epgUrl).isEqualTo("https://epg.example.test/xmltv.xml")
    }

    @Test
    fun `parse multiple M3U header epg urls`() {
        val m3u = """
            #EXTM3U url-tvg="https://epg.example.test/primary.xml.gz, epg/fallback.xml"
            #EXTINF:-1 tvg-id="show.tv" group-title="TR",TR • Show Tv RAW
            http://stream.example.com/live/show.m3u8
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)

        assertThat(result.epgUrls).containsExactly(
            "https://epg.example.test/primary.xml.gz",
            "epg/fallback.xml"
        ).inOrder()
        assertThat(result.epgUrl).isEqualTo("https://epg.example.test/primary.xml.gz")
    }

    @Test
    fun `parse xtream stream id from m3u live url`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-name="TRT 1" group-title="TR",TR • TRT 1 FHD
            https://provider.example/live/user1/pass1/12345.ts
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)

        assertThat(result.channels).hasSize(1)
        assertThat(result.channels[0].streamId).isEqualTo(12345)
    }

    @Test
    fun `parse xtream stream id from query parameter live url`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-name="TRT 1" group-title="TR",TR • TRT 1 FHD
            https://provider.example/live?username=user1&password=pass1&stream_id=67890
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)

        assertThat(result.channels).hasSize(1)
        assertThat(result.channels[0].streamId).isEqualTo(67890)
    }

    @Test
    fun `parse M3U with movies`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Movies",The Matrix
            http://stream.example.com/movie/123.mkv
            #EXTINF:-1 group-title="Film",Inception
            http://stream.example.com/movie/456.mp4
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)
        assertThat(result.movies).hasSize(2)
        assertThat(result.movies[0].name).isEqualTo("The Matrix")
        assertThat(result.movies[0].categoryName).isEqualTo("Movies")
    }

    @Test
    fun `progressive video URLs are VOD while preserving the playlist category`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Legal Demo" tvg-name="Open Movie",Open Movie
            https://media.example.test/open-movies/demo.mp4
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)

        assertThat(result.channels).isEmpty()
        assertThat(result.movies).hasSize(1)
        assertThat(result.movies.single().categoryName).isEqualTo("Legal Demo")
    }

    @Test
    fun `parse M3U with mixed content`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="News",BBC News
            http://stream.example.com/live/bbc.m3u8
            #EXTINF:-1 group-title="Movies",Interstellar
            http://stream.example.com/movie/789.mkv
            #EXTINF:-1 group-title="Series",Breaking Bad S01E01
            http://stream.example.com/series/101.mp4
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)
        assertThat(result.channels).hasSize(1)
        assertThat(result.movies).hasSize(1)
        assertThat(result.series).hasSize(1)
    }

    @Test
    fun `parse empty M3U`() {
        val m3u = "#EXTM3U\n"
        val result = parser.parseText(m3u, 1L)
        assertThat(result.channels).isEmpty()
        assertThat(result.movies).isEmpty()
        assertThat(result.series).isEmpty()
    }

    @Test
    fun `read failure is propagated instead of returning a partial playlist`() {
        val truncatedInput = object : InputStream() {
            private val prefix = "#EXTM3U\n#EXTINF:-1,First\nhttps://stream.example.test/1\n".byteInputStream()

            override fun read(): Int {
                val next = prefix.read()
                if (next >= 0) return next
                throw IOException("Synthetic truncated response")
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val read = prefix.read(buffer, offset, length)
                if (read >= 0) return read
                throw IOException("Synthetic truncated response")
            }
        }

        assertThrows(IOException::class.java) {
            parser.parse(truncatedInput, 1L)
        }
    }

    @Test
    fun `parse M3U without EXTM3U header`() {
        val m3u = """
            #EXTINF:-1,Test Channel
            http://stream.example.com/test.m3u8
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)
        assertThat(result.channels).hasSize(1)
    }

    @Test
    fun `parse attributes correctly`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="test.id" tvg-name="Test Name" tvg-logo="http://logo.png" group-title="Group" catchup-source="http://catchup",Display Name
            http://stream.example.com/test
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)
        assertThat(result.channels).hasSize(1)
        assertThat(result.channels[0].name).isEqualTo("Display Name")
        assertThat(result.channels[0].epgChannelId).isEqualTo("test.id")
        assertThat(result.channels[0].logoUrl).isEqualTo("http://logo.png")
        assertThat(result.channels[0].catchupSource).isEqualTo("http://catchup")
    }

    @Test
    fun `parse EXTVLCOPT request headers for the next stream`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="News",Synthetic News
            #EXTVLCOPT:http-user-agent=Example TV Client
            #EXTVLCOPT:http-referrer=https://portal.example.test/
            https://media.example.test/live/1.m3u8
            #EXTINF:-1 group-title="News",Second Channel
            https://media.example.test/live/2.m3u8
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)
        val firstSource = parsePlaybackSource(result.channels[0].streamUrl)
        val secondSource = parsePlaybackSource(result.channels[1].streamUrl)

        assertThat(firstSource.headers).containsExactly(
            "User-Agent", "Example TV Client",
            "Referer", "https://portal.example.test/"
        )
        assertThat(secondSource.headers).isEmpty()
    }

    @Test
    fun `parse request headers from EXTINF attributes and preserve pipe headers`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 http-user-agent="Attribute Agent" http-origin="https://portal.example.test" group-title="Film",Synthetic Film
            https://media.example.test/movie/1.mkv|Referer=https%3A%2F%2Fref.example.test%2F
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)
        val source = parsePlaybackSource(result.movies.single().streamUrl)

        assertThat(source.headers).containsExactly(
            "User-Agent", "Attribute Agent",
            "Origin", "https://portal.example.test",
            "Referer", "https://ref.example.test/"
        )
    }

    @Test
    fun `parse KODIPROP and EXTHTTP request headers`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="News",Kodi Synthetic
            #KODIPROP:inputstream.adaptive.stream_headers=User-Agent=Kodi%20Agent&Referer=https%3A%2F%2Fportal.example.test%2F
            https://media.example.test/live/1.m3u8
            #EXTINF:-1 group-title="News",JSON Synthetic
            #EXTHTTP:{"User-Agent":"JSON Agent","Origin":"https://portal.example.test"}
            https://media.example.test/live/2.m3u8
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)

        assertThat(parsePlaybackSource(result.channels[0].streamUrl).headers).containsExactly(
            "User-Agent", "Kodi Agent",
            "Referer", "https://portal.example.test/"
        )
        assertThat(parsePlaybackSource(result.channels[1].streamUrl).headers).containsExactly(
            "User-Agent", "JSON Agent",
            "Origin", "https://portal.example.test"
        )
    }

    @Test
    fun `series name extraction`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Series",Breaking Bad S01E01
            http://stream.example.com/series/1.mp4
            #EXTINF:-1 group-title="Series",Breaking Bad S01E02
            http://stream.example.com/series/2.mp4
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)
        assertThat(result.series).hasSize(1)
        assertThat(result.series[0].name).isEqualTo("Breaking Bad")
    }

    @Test
    fun `movies keep playlist source order`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="Movies",Newest Synthetic Movie
            http://stream.example.com/movie/newest.mkv
            #EXTINF:-1 group-title="Movies",Older Synthetic Movie
            http://stream.example.com/movie/older.mkv
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)

        assertThat(result.movies.map { it.sourceOrder }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `series source order uses first episode position`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 group-title="News",Synthetic News
            http://stream.example.com/live/news.m3u8
            #EXTINF:-1 group-title="Series",Example Show S01E01
            http://stream.example.com/series/example-s01e01.mp4
            #EXTINF:-1 group-title="Series",Example Show S01E02
            http://stream.example.com/series/example-s01e02.mp4
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)

        assertThat(result.series).hasSize(1)
        assertThat(result.series[0].sourceOrder).isEqualTo(1)
    }

    @Test
    fun `parse EPG URL from separate lines`() {
        val m3u = """
            #EXTM3U
            #url-tvg:http://example.com/epg.xml.gz
            #EXTINF:-1 tvg-id="show.tv" group-title="TR",TR • Show Tv RAW
            http://stream.example.com/live/show.m3u8
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)

        assertThat(result.epgUrl).isEqualTo("http://example.com/epg.xml.gz")
    }

    @Test
    fun `parse EPG URL from separate lines with spaces and equals sign`() {
        val m3u = """
            #EXTM3U
            #x-tvg-url = http://example.com/epg2.xml
            #EXTINF:-1 tvg-id="show.tv" group-title="TR",TR • Show Tv RAW
            http://stream.example.com/live/show.m3u8
        """.trimIndent()

        val result = parser.parseText(m3u, 1L)

        assertThat(result.epgUrl).isEqualTo("http://example.com/epg2.xml")
    }
}
