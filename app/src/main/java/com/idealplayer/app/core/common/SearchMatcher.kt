package com.idealplayer.app.core.common

import java.util.Locale

object SearchMatcher {
    const val DEFAULT_RESULT_LIMIT = 80

    private const val SHORT_QUERY_THRESHOLD = 0.80
    private const val MULTI_TERM_THRESHOLD = 0.62
    private const val DEFAULT_THRESHOLD = 0.58

    data class Match<T>(
        val item: T,
        val score: Double
    )

    fun score(
        query: String,
        primary: String,
        secondary: List<String> = emptyList(),
        year: Int = 0
    ): Double {
        val normalizedQuery = normalizeSearchText(query)
        if (normalizedQuery.isBlank()) return 0.0

        return scoreNormalized(
            normalizedQuery = normalizedQuery,
            queryTerms = terms(normalizedQuery),
            primary = primary,
            secondary = secondary,
            year = year
        )
    }

    fun <T> rank(
        query: String,
        items: Iterable<T>,
        limit: Int = DEFAULT_RESULT_LIMIT,
        primary: (T) -> String,
        secondary: (T) -> List<String> = { emptyList() },
        year: (T) -> Int = { 0 }
    ): List<T> = rankWithScores(
        query = query,
        items = items,
        limit = limit,
        primary = primary,
        secondary = secondary,
        year = year
    ).map { it.item }

    fun <T> rankWithScores(
        query: String,
        items: Iterable<T>,
        limit: Int = DEFAULT_RESULT_LIMIT,
        primary: (T) -> String,
        secondary: (T) -> List<String> = { emptyList() },
        year: (T) -> Int = { 0 }
    ): List<Match<T>> {
        val normalizedQuery = normalizeSearchText(query)
        if (normalizedQuery.isBlank()) return emptyList()

        val queryTerms = terms(normalizedQuery)
        val threshold = thresholdFor(normalizedQuery, queryTerms)

        return items.asSequence()
            .map { item ->
                Match(
                    item = item,
                    score = scoreNormalized(
                        normalizedQuery = normalizedQuery,
                        queryTerms = queryTerms,
                        primary = primary(item),
                        secondary = secondary(item),
                        year = year(item)
                    )
                )
            }
            .filter { it.score >= threshold }
            .sortedWith(
                compareByDescending<Match<T>> { it.score }
                    .thenBy { primary(it.item).lowercase(Locale.ENGLISH) }
            )
            .take(limit)
            .toList()
    }

    private fun scoreNormalized(
        normalizedQuery: String,
        queryTerms: List<String>,
        primary: String,
        secondary: List<String>,
        year: Int
    ): Double {
        val primaryForms = normalizedPrimaryForms(primary, year)
        val primaryScore = primaryForms.maxOfOrNull { textScore(normalizedQuery, queryTerms, it) } ?: 0.0
        val secondaryScore = secondary.asSequence()
            .map { normalizeSearchText(it) }
            .filter { it.isNotBlank() }
            .map { textScore(normalizedQuery, queryTerms, it) * 0.84 }
            .maxOrNull() ?: 0.0
        val yearScore = if (year > 0 && queryTerms.size == 1 && queryTerms.first() == year.toString()) 0.88 else 0.0

        return maxOf(primaryScore, secondaryScore, yearScore).coerceIn(0.0, 1.0)
    }

    private fun normalizedPrimaryForms(primary: String, year: Int): List<String> {
        val normalized = normalizeSearchText(primary)
        val cleaned = normalizeSearchText(StringUtils.cleanTitleForSearch(primary))
        val forms = if (cleaned.isNotBlank() && cleaned != normalized) {
            listOf(normalized, cleaned)
        } else {
            listOf(normalized)
        }
        return if (year > 0) forms + forms.map { "$it $year".trim() } else forms
    }

    private fun textScore(
        normalizedQuery: String,
        queryTerms: List<String>,
        normalizedTarget: String
    ): Double {
        if (normalizedTarget.isBlank()) return 0.0
        if (normalizedTarget == normalizedQuery) return 1.0

        val lengthRatio = lengthRatio(normalizedQuery, normalizedTarget)
        if (normalizedTarget.startsWith(normalizedQuery)) return 0.94 + lengthRatio * 0.04
        if (normalizedTarget.contains(normalizedQuery)) return 0.88 + lengthRatio * 0.08

        val compactQuery = normalizedQuery.withoutSpaces()
        val compactTarget = normalizedTarget.withoutSpaces()
        if (compactQuery.length >= 2) {
            if (compactTarget == compactQuery) return 0.98
            if (compactTarget.contains(compactQuery)) {
                return 0.90 + lengthRatio(compactQuery, compactTarget) * 0.06
            }
        }

        val targetTerms = terms(normalizedTarget)
        val acronym = targetTerms.joinToString("") { it.first().toString() }
        if (compactQuery.length >= 2 && acronym.startsWith(compactQuery)) {
            return 0.89 + lengthRatio(compactQuery, acronym) * 0.07
        }

        val fuzzyScore = if (normalizedQuery.length >= 4 || queryTerms.size > 1) {
            StringUtils.fuzzyMatchScore(normalizedQuery, normalizedTarget) * 0.96
        } else {
            0.0
        }

        return maxOf(
            tokenCoverageScore(queryTerms, targetTerms, normalizedTarget),
            fuzzyScore
        )
    }

    private fun tokenCoverageScore(
        queryTerms: List<String>,
        targetTerms: List<String>,
        normalizedTarget: String
    ): Double {
        if (queryTerms.isEmpty() || targetTerms.isEmpty()) return 0.0

        val scores = queryTerms.map { queryTerm ->
            targetTerms.maxOfOrNull { targetTerm -> tokenScore(queryTerm, targetTerm) } ?: 0.0
        }
        val matchedCount = scores.countIndexed { index, score -> score >= minimumTokenScore(queryTerms[index]) }
        if (matchedCount == 0) return 0.0

        val coverage = matchedCount.toDouble() / queryTerms.size
        val averageScore = scores.average()
        val orderedTerms = queryTerms.joinToString(" ")
        val orderBoost = if (queryTerms.size > 1 && normalizedTarget.contains(orderedTerms)) 0.04 else 0.0

        return if (coverage == 1.0) {
            (0.72 + averageScore * 0.22 + orderBoost).coerceAtMost(0.97)
        } else {
            (0.48 + averageScore * 0.20) * coverage
        }
    }

    private fun tokenScore(queryTerm: String, targetTerm: String): Double {
        if (queryTerm == targetTerm) return 1.0
        if (targetTerm.startsWith(queryTerm)) {
            return 0.86 + lengthRatio(queryTerm, targetTerm) * 0.10
        }
        if (queryTerm.length >= 3 && targetTerm.contains(queryTerm)) {
            return 0.74 + lengthRatio(queryTerm, targetTerm) * 0.10
        }
        if (queryTerm.length >= 3 && targetTerm.length >= 3) {
            val fuzzy = StringUtils.fuzzyMatchScore(queryTerm, targetTerm)
            if (fuzzy >= 0.80) {
                return (0.64 + (fuzzy - 0.80) * 1.5).coerceAtMost(0.88)
            }
        }
        return 0.0
    }

    private fun minimumTokenScore(term: String): Double = if (term.length <= 2) 0.84 else 0.58

    private fun thresholdFor(normalizedQuery: String, queryTerms: List<String>): Double = when {
        normalizedQuery.length <= 2 -> SHORT_QUERY_THRESHOLD
        queryTerms.size > 1 -> MULTI_TERM_THRESHOLD
        else -> DEFAULT_THRESHOLD
    }

    private fun normalizeSearchText(value: String): String = StringUtils.normalizeTitle(value)

    private fun terms(value: String): List<String> = value
        .split(" ")
        .filter { it.isNotBlank() && it !in noiseTerms }

    private fun lengthRatio(first: String, second: String): Double =
        minOf(first.length, second.length).toDouble() / maxOf(first.length, second.length).coerceAtLeast(1)

    private fun String.withoutSpaces(): String = replace(" ", "")

    private inline fun <T> Iterable<T>.countIndexed(predicate: (Int, T) -> Boolean): Int {
        var count = 0
        forEachIndexed { index, item ->
            if (predicate(index, item)) count++
        }
        return count
    }

    private val noiseTerms = setOf(
        "hd",
        "fhd",
        "uhd",
        "sd",
        "4k",
        "8k",
        "vod",
        "live",
        "tv"
    )
}
