package com.idealplayer.app.core.common

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Signals that a streamed input exceeded the caller's explicit byte budget. */
class InputSizeLimitExceededException(
    maxBytes: Long
) : IOException("Input exceeds the ${maxBytes / (1024 * 1024)} MiB limit")

/**
 * An [InputStream] wrapper that fails closed once more than [maxBytes] has been consumed.
 *
 * It deliberately throws rather than returning a partial EOF. Callers that replace persisted
 * data from a stream must distinguish a complete source from a truncated one.
 */
class LimitedInputStream(
    input: InputStream,
    private val maxBytes: Long
) : FilterInputStream(input) {
    private var consumedBytes = 0L

    init {
        require(maxBytes >= 0L) { "maxBytes must not be negative" }
    }

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) recordBytes(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = super.read(buffer, offset, length)
        if (read > 0) recordBytes(read.toLong())
        return read
    }

    override fun skip(byteCount: Long): Long {
        val skipped = super.skip(byteCount)
        if (skipped > 0) recordBytes(skipped)
        return skipped
    }

    private fun recordBytes(byteCount: Long) {
        if (byteCount > maxBytes - consumedBytes) {
            throw InputSizeLimitExceededException(maxBytes)
        }
        consumedBytes += byteCount
    }
}

fun InputStream.limitedTo(maxBytes: Long): InputStream = LimitedInputStream(this, maxBytes)
