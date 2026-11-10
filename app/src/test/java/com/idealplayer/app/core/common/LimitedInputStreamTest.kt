package com.idealplayer.app.core.common

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import org.junit.Assert.assertThrows
import org.junit.Test

class LimitedInputStreamTest {

    @Test
    fun `reads an input exactly at the limit`() {
        val content = "12345".byteInputStream()

        val result = content.limitedTo(5).readBytes().decodeToString()

        assertThat(result).isEqualTo("12345")
    }

    @Test
    fun `throws when a stream exceeds its byte limit`() {
        val content = ByteArrayInputStream("123456".encodeToByteArray())

        assertThrows(InputSizeLimitExceededException::class.java) {
            content.limitedTo(5).readBytes()
        }
    }

    @Test
    fun `skip cannot bypass the byte limit`() {
        val input = ByteArrayInputStream("123456".encodeToByteArray()).limitedTo(5)

        assertThrows(InputSizeLimitExceededException::class.java) {
            input.skip(6)
        }
    }
}
