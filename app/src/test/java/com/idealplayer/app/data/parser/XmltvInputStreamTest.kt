package com.idealplayer.app.data.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class XmltvInputStreamTest {

    @Test
    fun `asXmltvInputStream decompresses gzip xmltv`() {
        val xml = """<?xml version="1.0"?><tv></tv>"""
        val gzipped = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { gzip ->
                gzip.write(xml.toByteArray())
            }
            output.toByteArray()
        }

        val decoded = gzipped.inputStream()
            .asXmltvInputStream("https://example.test/epg.xml.gz")
            .bufferedReader()
            .readText()

        assertThat(decoded).isEqualTo(xml)
    }
}
