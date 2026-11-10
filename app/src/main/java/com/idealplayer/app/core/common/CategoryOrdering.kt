package com.idealplayer.app.core.common

import java.util.Locale

fun orderCategoryNames(
    categories: Iterable<String>,
    @Suppress("UNUSED_PARAMETER")
    locale: Locale = Locale.getDefault()
): List<String> {
    return categories
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}
