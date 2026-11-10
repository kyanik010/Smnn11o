package com.idealplayer.app.data.parser

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.Locale
import java.util.zip.GZIPInputStream

fun InputStream.asXmltvInputStream(
    sourceUrl: String,
    contentType: String? = null
): InputStream {
    val pushback = PushbackInputStream(BufferedInputStream(this), 2)
    val first = pushback.read()
    val second = pushback.read()
    if (second >= 0) pushback.unread(second)
    if (first >= 0) pushback.unread(first)

    val hasGzipMagic = first == 0x1f && second == 0x8b
    val lowerUrl = sourceUrl.lowercase(Locale.ROOT)
    val lowerContentType = contentType.orEmpty().lowercase(Locale.ROOT)
    val looksGzipped = hasGzipMagic ||
        lowerUrl.endsWith(".gz") ||
        lowerUrl.contains(".xml.gz") ||
        lowerContentType.contains("gzip") ||
        lowerContentType.contains("application/x-gzip")

    return if (looksGzipped) GZIPInputStream(pushback) else pushback
}
