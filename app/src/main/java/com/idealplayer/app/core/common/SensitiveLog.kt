package com.idealplayer.app.core.common

import java.net.URI

object SensitiveLog {
    private const val REDACTED = "[redacted]"

    fun redactUrl(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return ""

        return runCatching {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase().orEmpty()
            val host = uri.host.orEmpty()

            when {
                scheme.isBlank() -> REDACTED
                host.isBlank() -> "$scheme://$REDACTED"
                else -> buildString {
                    append(scheme)
                    append("://")
                    append(host)
                    if (uri.port != -1) append(":").append(uri.port)
                    if (!uri.rawPath.isNullOrBlank() && uri.rawPath != "/") append("/...")
                    if (!uri.rawQuery.isNullOrBlank()) append("?...")
                    if (!uri.rawFragment.isNullOrBlank()) append("#...")
                }
            }
        }.getOrDefault(REDACTED)
    }
}
