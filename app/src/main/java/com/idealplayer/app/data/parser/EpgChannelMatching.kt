package com.idealplayer.app.data.parser

import com.idealplayer.app.core.model.Channel
import java.text.Normalizer
import java.util.Locale

private val combiningMarksRegex = Regex("\\p{Mn}+")
private val separatorRegex = Regex("[^a-z0-9]+")
private val repeatedDashRegex = Regex("-+")
private val repeatedUnderscoreRegex = Regex("_+")
private val leadingRegionTokens = setOf("tr", "turkey", "turkiye")
private val trailingRegionTokens = setOf("tr", "turkey", "turkiye")
private val trailingDescriptorTokens = setOf(
    "raw",
    "hd",
    "fhd",
    "fullhd",
    "uhd",
    "sd",
    "hevc",
    "h264",
    "h265",
    "x264",
    "x265",
    "vip",
    "backup",
    "yedek",
    "tv"
)

fun buildEpgChannelIdMap(channels: List<Channel>): Map<String, String> {
    val map = linkedMapOf<String, String>()
    channels.forEach { channel ->
        val storageId = epgStorageChannelId(channel)
        if (storageId.isBlank()) return@forEach

        epgLookupKeysForChannel(channel).forEach { key ->
            if (key !in map) {
                map[key] = storageId
            }
        }
    }
    return map
}

fun epgLookupKeysForChannel(channel: Channel): List<String> = buildList {
    addEpgLookupKeys(channel.epgChannelId)
    addEpgLookupKeys(channel.name)
    if (channel.streamId > 0) {
        addEpgLookupKeys(channel.streamId.toString())
    }
}.distinct()

fun canonicalEpgChannelId(channel: Channel): String =
    channel.epgChannelId.trim()
        .ifBlank { channel.name.trim() }
        .ifBlank { channel.streamId.takeIf { it > 0 }?.toString().orEmpty() }

private fun epgStorageChannelId(channel: Channel): String {
    val source = channel.epgChannelId.trim()
        .ifBlank { channel.name.trim() }
        .ifBlank { channel.streamId.takeIf { it > 0 }?.toString().orEmpty() }

    return reducedTokenLookupKey(source)
        .ifBlank { canonicalEpgChannelId(channel) }
}

internal fun resolveEpgChannelId(
    xmlChannelId: String,
    displayNames: List<String>,
    channelIdMap: Map<String, String>?
): String {
    if (channelIdMap.isNullOrEmpty()) return xmlChannelId

    val candidates = buildList {
        addEpgLookupKeys(xmlChannelId)
        displayNames.forEach(::addEpgLookupKeys)
    }

    candidates.forEach { candidate ->
        channelIdMap[candidate]?.let { return it }
    }
    return xmlChannelId
}

internal fun epgLookupKeys(value: String): List<String> = buildList {
    addEpgLookupKeys(value)
}.distinct()

private fun MutableList<String>.addEpgLookupKeys(value: String) {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return

    addUnique(trimmed)
    addUnique(trimmed.lowercase(Locale.ROOT))

    val normalized = normalizeForEpgLookup(trimmed)
    if (normalized.isBlank()) return

    addUnique(normalized)
    addUnique(normalized.replace(separatorRegex, "-").replace(repeatedDashRegex, "-").trim('-'))
    addUnique(normalized.replace(separatorRegex, "_").replace(repeatedUnderscoreRegex, "_").trim('_'))
    addUnique(normalized.filter { it in 'a'..'z' || it in '0'..'9' })

    val tokens = normalized.split(separatorRegex)
        .filter(String::isNotBlank)
    addTokenLookupVariants(tokens)
}

private fun MutableList<String>.addTokenLookupVariants(tokens: List<String>) {
    if (tokens.isEmpty()) return

    val variants = linkedSetOf<List<String>>()
    val pending = ArrayDeque<List<String>>()

    fun enqueue(variant: List<String>) {
        if (variant.isNotEmpty() && variants.add(variant)) {
            pending.addLast(variant)
        }
    }

    enqueue(tokens)

    while (pending.isNotEmpty()) {
        val variant = pending.removeFirst()

        enqueue(variant.dropWhile { it in leadingRegionTokens })
        if (variant.lastOrNull() in trailingDescriptorTokens) {
            enqueue(variant.dropLast(1))
        }
        enqueue(variant.dropLastWhile { it in trailingDescriptorTokens })
        if (variant.lastOrNull() in trailingRegionTokens) {
            enqueue(variant.dropLast(1))
        }
        enqueue(variant.dropLastWhile { it in trailingRegionTokens })
    }

    variants.forEach { variant ->
        addUnique(variant.joinToString("-"))
        addUnique(variant.joinToString("_"))
        addUnique(variant.joinToString(""))
    }
}

private fun MutableList<String>.addUnique(value: String) {
    if (value.isNotBlank() && value !in this) {
        add(value)
    }
}

private fun reducedTokenLookupKey(value: String): String {
    val tokens = normalizeForEpgLookup(value)
        .split(separatorRegex)
        .filter(String::isNotBlank)
        .toMutableList()
    if (tokens.isEmpty()) return ""

    while (tokens.isNotEmpty() && tokens.first() in leadingRegionTokens) {
        tokens.removeAt(0)
    }
    while (
        tokens.isNotEmpty() &&
        (tokens.last() in trailingDescriptorTokens || tokens.last() in trailingRegionTokens)
    ) {
        tokens.removeAt(tokens.lastIndex)
    }

    return tokens.joinToString("-")
}

private fun normalizeForEpgLookup(value: String): String {
    val turkishSafe = value
        .replace('ı', 'i')
        .replace('İ', 'I')
        .replace('ğ', 'g')
        .replace('Ğ', 'G')
        .replace('ü', 'u')
        .replace('Ü', 'U')
        .replace('ş', 's')
        .replace('Ş', 'S')
        .replace('ö', 'o')
        .replace('Ö', 'O')
        .replace('ç', 'c')
        .replace('Ç', 'C')

    return Normalizer.normalize(turkishSafe, Normalizer.Form.NFD)
        .replace(combiningMarksRegex, "")
        .lowercase(Locale.ROOT)
}
