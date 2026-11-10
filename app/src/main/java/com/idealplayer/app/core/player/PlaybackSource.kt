package com.idealplayer.app.core.player

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

private const val DEFAULT_PLAYBACK_USER_AGENT = "IdealPlayer/Android"
private const val MAX_PLAYBACK_HEADERS = 32

data class PlaybackSource(
    val url: String,
    val headers: Map<String, String> = emptyMap()
) {
    val userAgent: String
        get() = headers["User-Agent"] ?: DEFAULT_PLAYBACK_USER_AGENT

    val requestProperties: Map<String, String>
        get() = headers - "User-Agent"
}

/**
 * Parses the common IPTV `url|Header=Value&Header2=Value` convention without
 * allowing line breaks or invalid header names into a network request.
 */
fun parsePlaybackSource(rawValue: String): PlaybackSource {
    val trimmed = rawValue.trim().removeSuffix(".")
    val separatorIndex = trimmed.indexOf('|')
    if (separatorIndex <= 0 || separatorIndex == trimmed.lastIndex) {
        return PlaybackSource(url = trimmed)
    }

    val encodedHeaders = trimmed.substring(separatorIndex + 1)
    if (!encodedHeaders.contains('=')) {
        return PlaybackSource(url = trimmed)
    }

    val headers = encodedHeaders
        .split('&')
        .asSequence()
        .mapNotNull(::parseHeaderEntry)
        .take(MAX_PLAYBACK_HEADERS)
        .toMap(LinkedHashMap())

    return if (headers.isEmpty()) {
        PlaybackSource(url = trimmed)
    } else {
        PlaybackSource(
            url = trimmed.substring(0, separatorIndex).trim(),
            headers = headers
        )
    }
}

fun withPlaybackHeaders(rawUrl: String, additionalHeaders: Map<String, String>): String {
    val source = parsePlaybackSource(rawUrl)
    val mergedHeaders = LinkedHashMap(source.headers)
    additionalHeaders.forEach { (name, value) ->
        sanitizeHeader(name, value)?.let { (safeName, safeValue) ->
            mergedHeaders[safeName] = safeValue
        }
    }
    if (mergedHeaders.isEmpty()) return source.url

    val encoded = mergedHeaders.entries.joinToString("&") { (name, value) ->
        "${encodeHeaderComponent(name)}=${encodeHeaderComponent(value)}"
    }
    return "${source.url}|$encoded"
}

fun playbackProfileKey(rawUrl: String): String {
    val normalizedUrl = parsePlaybackSource(rawUrl).url.trim()
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalizedUrl.toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

fun playbackSourceSummary(rawUrl: String): Pair<String, String> {
    val sourceUrl = parsePlaybackSource(rawUrl).url
    val schemeSeparator = sourceUrl.indexOf("://")
    if (schemeSeparator <= 0) return "unknown" to "local/unknown"

    val scheme = sourceUrl.substring(0, schemeSeparator).lowercase(Locale.ROOT)
    val authority = sourceUrl.substring(schemeSeparator + 3).substringBefore('/')
    val host = authority.substringAfterLast('@').substringBefore(':').ifBlank { "unknown" }
    val hostFingerprint = MessageDigest.getInstance("SHA-256")
        .digest(host.toByteArray(StandardCharsets.UTF_8))
        .take(6)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
    return scheme to hostFingerprint
}

private fun parseHeaderEntry(entry: String): Pair<String, String>? {
    val separatorIndex = entry.indexOf('=')
    if (separatorIndex <= 0) return null
    val name = decodeHeaderComponent(entry.substring(0, separatorIndex))
    val value = decodeHeaderComponent(entry.substring(separatorIndex + 1))
    return sanitizeHeader(name, value)
}

private fun sanitizeHeader(name: String, value: String): Pair<String, String>? {
    val normalizedName = canonicalHeaderName(name.trim()) ?: return null
    val normalizedValue = value.trim()
    if (normalizedValue.isBlank() || normalizedValue.any { it == '\r' || it == '\n' }) return null
    return normalizedName to normalizedValue
}

private fun canonicalHeaderName(name: String): String? {
    if (!name.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) return null
    return when (name.lowercase(Locale.ROOT)) {
        "user-agent", "http-user-agent" -> "User-Agent"
        "referer", "referrer", "http-referrer" -> "Referer"
        "origin", "http-origin" -> "Origin"
        "cookie", "http-cookie" -> "Cookie"
        else -> name.split('-').joinToString("-") { part ->
            part.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercaseChar)
        }
    }
}

private fun decodeHeaderComponent(value: String): String = runCatching {
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}.getOrDefault(value)

private fun encodeHeaderComponent(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
