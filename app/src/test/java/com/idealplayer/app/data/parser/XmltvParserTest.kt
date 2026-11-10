package com.idealplayer.app.data.parser

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class XmltvParserTest {

    private lateinit var parser: XmltvParser

    @Before
    fun setup() {
        parser = XmltvParser()
    }

    private fun xmlStream(xml: String) = ByteArrayInputStream(xml.trimIndent().toByteArray())

    // ─── Minimal valid XMLTV ────────────────────────────────────────────────

    private val minimalXmltv = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE tv SYSTEM "xmltv.dtd">
        <tv generator-info-name="test">
            <channel id="ch1.example">
                <display-name>Test Channel</display-name>
            </channel>
            <programme start="20260419120000 +0300" stop="20260419130000 +0300" channel="ch1.example">
                <title lang="tr">Haber Bülteni</title>
                <desc lang="tr">Günün haberleri.</desc>
                <category lang="tr">Haber</category>
            </programme>
        </tv>
    """.trimIndent()

    @Test
    fun `parse minimal XMLTV produces one program`() = runTest {
        val programs = parser.parse(xmlStream(minimalXmltv))
        assertThat(programs).hasSize(1)
    }

    @Test
    fun `parsed program has correct channel id`() = runTest {
        val programs = parser.parse(xmlStream(minimalXmltv))
        assertThat(programs[0].channelId).isEqualTo("ch1.example")
    }

    @Test
    fun `parsed program has correct title`() = runTest {
        val programs = parser.parse(xmlStream(minimalXmltv))
        assertThat(programs[0].title).isEqualTo("Haber Bülteni")
    }

    @Test
    fun `parsed program has correct description`() = runTest {
        val programs = parser.parse(xmlStream(minimalXmltv))
        assertThat(programs[0].description).isEqualTo("Günün haberleri.")
    }

    @Test
    fun `parsed program has correct genre`() = runTest {
        val programs = parser.parse(xmlStream(minimalXmltv))
        assertThat(programs[0].genre).isEqualTo("Haber")
    }

    @Test
    fun `start time is normalized to UTC millis`() = runTest {
        val programs = parser.parse(xmlStream(minimalXmltv))
        // 2026-04-19 12:00:00 +0300 = 2026-04-19 09:00:00 UTC
        // UTC epoch ms: 1776589200000
        val expectedUtc = 1776589200000L
        assertThat(programs[0].startTime).isEqualTo(expectedUtc)
    }

    @Test
    fun `end time is after start time`() = runTest {
        val programs = parser.parse(xmlStream(minimalXmltv))
        assertThat(programs[0].endTime).isGreaterThan(programs[0].startTime)
    }

    @Test
    fun `duration is exactly one hour`() = runTest {
        val programs = parser.parse(xmlStream(minimalXmltv))
        val durationMs = programs[0].endTime - programs[0].startTime
        assertThat(durationMs).isEqualTo(3_600_000L)
    }

    // ─── Multiple programs ───────────────────────────────────────────────────

    private val multiProgram = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
            <programme start="20260419090000 +0000" stop="20260419100000 +0000" channel="bbc.one">
                <title>Morning News</title>
            </programme>
            <programme start="20260419100000 +0000" stop="20260419110000 +0000" channel="bbc.one">
                <title>Documentary</title>
            </programme>
            <programme start="20260419090000 +0000" stop="20260419095000 +0000" channel="ch2">
                <title>Sport</title>
            </programme>
        </tv>
    """.trimIndent()

    @Test
    fun `parse multiple programs from multiple channels`() = runTest {
        val programs = parser.parse(xmlStream(multiProgram))
        assertThat(programs).hasSize(3)
    }

    @Test
    fun `programs are grouped by channel`() = runTest {
        val programs = parser.parse(xmlStream(multiProgram))
        val bbcPrograms = programs.filter { it.channelId == "bbc.one" }
        assertThat(bbcPrograms).hasSize(2)
    }

    // ─── Channel ID remapping ────────────────────────────────────────────────

    @Test
    fun `channel id map is applied`() = runTest {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
                <programme start="20260419090000 +0000" stop="20260419100000 +0000" channel="trt.1">
                    <title>Test</title>
                </programme>
            </tv>
        """.trimIndent()
        val idMap = mapOf("trt.1" to "internal-channel-42")
        val programs = parser.parse(xmlStream(xml), idMap)
        assertThat(programs[0].channelId).isEqualTo("internal-channel-42")
    }

    @Test
    fun `channel display name map is applied when XMLTV id differs`() = runTest {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
                <channel id="xml-random-id">
                    <display-name>TRT 1 HD</display-name>
                </channel>
                <programme start="20260419090000 +0000" stop="20260419100000 +0000" channel="xml-random-id">
                    <title>Test</title>
                </programme>
            </tv>
        """.trimIndent()
        val idMap = mapOf("trt-1-hd" to "trt.1")
        val programs = parser.parse(xmlStream(xml), idMap)
        assertThat(programs[0].channelId).isEqualTo("trt.1")
    }

    // ─── Edge cases ──────────────────────────────────────────────────────────

    @Test
    fun `empty XMLTV returns empty list`() = runTest {
        val xml = """<?xml version="1.0"?><tv></tv>"""
        val programs = parser.parse(xmlStream(xml))
        assertThat(programs).isEmpty()
    }

    @Test
    fun `program without title is skipped`() = runTest {
        val xml = """
            <?xml version="1.0"?>
            <tv>
                <programme start="20260419090000 +0000" stop="20260419100000 +0000" channel="ch1">
                    <desc>No title here</desc>
                </programme>
            </tv>
        """.trimIndent()
        val programs = parser.parse(xmlStream(xml))
        assertThat(programs).isEmpty()
    }

    @Test
    fun `malformed time string is skipped`() = runTest {
        val xml = """
            <?xml version="1.0"?>
            <tv>
                <programme start="NOT_A_DATE" stop="ALSO_NOT_A_DATE" channel="ch1">
                    <title>Bad Time</title>
                </programme>
            </tv>
        """.trimIndent()
        val programs = parser.parse(xmlStream(xml))
        assertThat(programs).isEmpty()
    }

    @Test
    fun `icon url is captured`() = runTest {
        val xml = """
            <?xml version="1.0"?>
            <tv>
                <programme start="20260419090000 +0000" stop="20260419100000 +0000" channel="ch1">
                    <title>Show</title>
                    <icon src="http://example.com/poster.jpg"/>
                </programme>
            </tv>
        """.trimIndent()
        val programs = parser.parse(xmlStream(xml))
        assertThat(programs).hasSize(1)
        assertThat(programs[0].posterUrl).isEqualTo("http://example.com/poster.jpg")
    }

    @Test
    fun `channel icon is captured and resolved through display name mapping`() = runTest {
        val xml = """
            <?xml version="1.0"?>
            <tv>
                <channel id="provider-random-id">
                    <display-name>TR • Tele 1 HD</display-name>
                    <icon src="https://images.example/tele1.png"/>
                </channel>
                <programme start="20260419090000 +0000" stop="20260419100000 +0000" channel="provider-random-id">
                    <title>Morning Show</title>
                </programme>
            </tv>
        """.trimIndent()

        val result = parser.parseDocument(
            xmlStream(xml),
            channelIdMap = mapOf("tele-1" to "tele-1")
        )

        assertThat(result.channelIcons)
            .containsEntry("tele-1", "https://images.example/tele1.png")
        assertThat(result.programs).hasSize(1)
    }

    // ─── EpgProgram UI model ─────────────────────────────────────────────────

    @Test
    fun `EpgProgram progressFraction is between 0 and 1`() {
        val now = System.currentTimeMillis()
        val program = EpgProgram(
            channelId = "ch1",
            title = "Test",
            startTime = now - 1_800_000L,  // started 30min ago
            endTime = now + 1_800_000L     // ends in 30min
        )
        assertThat(program.isCurrentlyAiring).isTrue()
        assertThat(program.progressFraction).isGreaterThan(0f)
        assertThat(program.progressFraction).isLessThan(1f)
        // Should be ~0.5 (halfway through)
        assertThat(program.progressFraction).isWithin(0.05f).of(0.5f)
    }

    @Test
    fun `EpgProgram not airing has zero progress`() {
        val now = System.currentTimeMillis()
        val program = EpgProgram(
            channelId = "ch1",
            title = "Past Show",
            startTime = now - 7_200_000L,  // 2h ago
            endTime = now - 3_600_000L     // ended 1h ago
        )
        assertThat(program.isCurrentlyAiring).isFalse()
        assertThat(program.progressFraction).isEqualTo(0f)
    }

    @Test
    fun `EpgProgram durationMs is correct`() {
        val start = System.currentTimeMillis()
        val end = start + 3_600_000L
        val program = EpgProgram("ch1", "Show", startTime = start, endTime = end)
        assertThat(program.durationMs).isEqualTo(3_600_000L)
    }
}
