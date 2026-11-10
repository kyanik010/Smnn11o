package com.idealplayer.app.core.common

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

private val INVALID_ARTWORK_VALUES = setOf("", "null", "n/a", "na", "none", "undefined", "0", "-")

fun isUsableArtworkUrl(value: String): Boolean {
    val normalized = value.trim()
    if (normalized.lowercase(Locale.ROOT) in INVALID_ARTWORK_VALUES) return false
    return normalized.startsWith("https://", ignoreCase = true) ||
        normalized.startsWith("http://", ignoreCase = true) ||
        normalized.startsWith("content://", ignoreCase = true) ||
        normalized.startsWith("file://", ignoreCase = true) ||
        normalized.startsWith("android.resource://", ignoreCase = true)
}

fun normalizeRemoteArtworkUrl(serverUrl: String, value: String): String {
    val normalized = value.trim().replace("\\/", "/")
    if (normalized.lowercase(Locale.ROOT) in INVALID_ARTWORK_VALUES) return ""
    if (isUsableArtworkUrl(normalized)) return normalized

    val base = serverUrl.trim().toHttpUrlOrNull() ?: return ""
    if (normalized.startsWith("//")) return "${base.scheme}:$normalized"
    return base.resolve(normalized)?.toString().orEmpty()
}
