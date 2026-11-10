package com.idealplayer.app.data.parser

import com.idealplayer.app.core.database.EpgProgramEntity
import com.idealplayer.app.core.model.Channel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Base64

internal fun parseXtreamEpgPrograms(
    response: JsonElement,
    channel: Channel
): List<EpgProgramEntity> {
    val channelId = canonicalEpgChannelId(channel)
    if (channelId.isBlank()) return emptyList()

    return response.xtreamListingObjects().mapNotNull { listing ->
        val title = listing.textValue("title", "name")
            .decodeXtreamText()
            .ifBlank { return@mapNotNull null }
        val description = listing.textValue("description", "desc", "plot")
            .decodeXtreamText()

        val startMs = listing.timestampMs(
            timestampKeys = arrayOf("start_timestamp", "startTimestamp", "start_time", "startTime"),
            dateKeys = arrayOf("start", "start_date")
        )
        val endMs = listing.timestampMs(
            timestampKeys = arrayOf("stop_timestamp", "end_timestamp", "stopTimestamp", "endTimestamp", "stop_time", "end_time"),
            dateKeys = arrayOf("end", "stop", "end_date")
        )

        if (startMs <= 0L || endMs <= startMs) return@mapNotNull null

        EpgProgramEntity(
            channelId = channelId,
            title = title,
            description = description,
            startTime = startMs,
            endTime = endMs
        )
    }
}

private fun JsonElement.xtreamListingObjects(): List<JsonObject> {
    return when (this) {
        is JsonArray -> mapNotNull { it as? JsonObject }
        is JsonObject -> {
            val listElement = this["epg_listings"]
                ?: this["epgListings"]
                ?: this["listings"]
                ?: this["data"]

            when (listElement) {
                is JsonArray -> listElement.mapNotNull { it as? JsonObject }
                is JsonObject -> listElement.values.mapNotNull { it as? JsonObject }
                else -> emptyList()
            }
        }
        else -> emptyList()
    }
}

private fun JsonObject.textValue(vararg keys: String): String {
    keys.forEach { key ->
        val value = (this[key] as? JsonPrimitive)?.contentOrNullCompat()?.trim()
        if (!value.isNullOrBlank()) return value
    }
    return ""
}

private fun JsonObject.timestampMs(
    timestampKeys: Array<String>,
    dateKeys: Array<String>
): Long {
    timestampKeys.forEach { key ->
        val raw = this[key] ?: return@forEach
        val timestamp = when (raw) {
            is JsonPrimitive -> raw.longOrNull ?: raw.contentOrNullCompat()?.toLongOrNull()
            else -> null
        } ?: return@forEach

        return if (timestamp < 10_000_000_000L) timestamp * 1000L else timestamp
    }

    dateKeys.forEach { key ->
        val parsed = (this[key] as? JsonPrimitive)
            ?.contentOrNullCompat()
            ?.parseXtreamDateMs()
            ?: return@forEach
        return parsed
    }

    return -1L
}

private fun JsonPrimitive.contentOrNullCompat(): String? =
    runCatching { content }.getOrNull()

private fun String.parseXtreamDateMs(): Long {
    val cleaned = trim()
    if (cleaned.isBlank()) return -1L

    runCatching { return Instant.parse(cleaned).toEpochMilli() }
    runCatching { return OffsetDateTime.parse(cleaned).toInstant().toEpochMilli() }

    val localPatterns = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy/MM/dd HH:mm:ss"
    )

    for (pattern in localPatterns) {
        try {
            return LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern(pattern))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
    }

    return -1L
}

internal fun String.decodeXtreamText(): String {
    val cleaned = trim()
    if (cleaned.isBlank()) return ""

    val padded = cleaned + "=".repeat((4 - cleaned.length % 4) % 4)
    val decoded = sequenceOf(
        runCatching { Base64.getDecoder().decode(padded) }.getOrNull(),
        runCatching { Base64.getUrlDecoder().decode(padded) }.getOrNull()
    )
        .filterNotNull()
        .map { bytes -> String(bytes, StandardCharsets.UTF_8).trim() }
        .firstOrNull { it.isLikelyHumanReadable() }

    return decoded ?: cleaned
}

private fun String.isLikelyHumanReadable(): Boolean {
    if (isBlank()) return false
    val printable = count { char ->
        !char.isISOControl() || char == '\n' || char == '\r' || char == '\t'
    }
    return printable >= length * 0.9
}
