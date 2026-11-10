package com.idealplayer.app.data.parser

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

/**
 * Guards XMLTV time parsing (K-5). A naive (no-offset) timestamp must be interpreted in the
 * local zone — matching XtreamEpgParser and XMLTV convention — not as UTC, otherwise
 * "now playing" is shifted by the device's UTC offset.
 */
class XmltvTimeParsingTest {

    @Test
    fun `offset timestamp parses to the correct instant`() {
        val expected = OffsetDateTime.of(2024, 4, 19, 12, 0, 0, 0, ZoneOffset.ofHours(3))
            .toInstant().toEpochMilli()
        assertThat(parseXmltvTimeToEpochMillis("20240419120000 +0300")).isEqualTo(expected)
    }

    @Test
    fun `offset without a space is normalized`() {
        val expected = OffsetDateTime.of(2024, 4, 19, 12, 0, 0, 0, ZoneOffset.ofHours(3))
            .toInstant().toEpochMilli()
        assertThat(parseXmltvTimeToEpochMillis("20240419120000+0300")).isEqualTo(expected)
    }

    @Test
    fun `naive timestamp is interpreted in the provided fallback zone`() {
        val zone = ZoneId.of("Europe/Istanbul") // +03:00, no DST
        val expected = LocalDateTime.of(2024, 4, 19, 12, 0, 0)
            .atZone(zone).toInstant().toEpochMilli()
        assertThat(parseXmltvTimeToEpochMillis("20240419120000", zone)).isEqualTo(expected)
    }

    @Test
    fun `naive timestamp is not treated as UTC`() {
        val utc = parseXmltvTimeToEpochMillis("20240419120000", ZoneId.of("UTC"))
        val istanbul = parseXmltvTimeToEpochMillis("20240419120000", ZoneId.of("Europe/Istanbul"))
        // The same wall clock in a +03:00 zone is an earlier absolute instant by 3 hours.
        assertThat(utc - istanbul).isEqualTo(3 * 60 * 60 * 1000L)
    }

    @Test
    fun `blank and unparseable input return -1`() {
        assertThat(parseXmltvTimeToEpochMillis("")).isEqualTo(-1L)
        assertThat(parseXmltvTimeToEpochMillis("   ")).isEqualTo(-1L)
        assertThat(parseXmltvTimeToEpochMillis("not-a-time")).isEqualTo(-1L)
    }
}
