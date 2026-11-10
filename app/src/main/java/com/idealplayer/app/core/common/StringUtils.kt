package com.idealplayer.app.core.common

import java.text.Normalizer
import java.util.Locale

object StringUtils {
    fun normalizeTitle(title: String): String {
        return Normalizer.normalize(transliterateSpecialLetters(title), Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .trim()
            .lowercase(Locale.ENGLISH)
            .replace(Regex("\\s+"), " ")
    }

    private fun transliterateSpecialLetters(value: String): String {
        return value
            .replace('ı', 'i')
            .replace('İ', 'I')
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .replace('ø', 'o')
            .replace('Ø', 'O')
            .replace('æ', 'a')
            .replace('Æ', 'A')
            .replace('œ', 'o')
            .replace('Œ', 'O')
    }

    /**
     * Clean a title for TMDB search by removing quality tags, codec info,
     * provider names, episode markers etc.
     *
     * For very short titles (≤ 3 chars), we skip aggressive cleaning
     * to avoid destroying the search query entirely.
     */
    fun cleanTitleForSearch(title: String): String {
        // For very short titles, only do minimal cleanup.
        // Keep numeric titles such as "10", but remove wrapper quotes/separators.
        if (title.trim().length <= 3) {
            return title.trim()
                .replace(Regex("[\"“”]"), "")
                .replace(Regex("[-_.:|/\\\\]+"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
        }

        var clean = title

        // Provider catalogs commonly prepend locale/content buckets. Remove only tokens that
        // are followed by an explicit separator so real titles such as "Tron" stay intact.
        clean = clean.replace(
            Regex("(?i)^\\s*(?:(?:TR|EN|ENG|DE|FR|ES|IT|AR|NL|VOD|F[İI]LM|MOVIE|S[İI]NEMA)\\s*[:|/\\-]\\s*)+"),
            ""
        )

        // 1. Remove common file extensions and tags
        clean = clean.replace(Regex("(?i)\\.(mkv|mp4|avi|srt|sub)\\b"), "")

        // 2. Remove quality and codec tags (more comprehensive)
        clean = clean.replace(Regex("(?i)\\b(1080p|720p|4k|8k|2160p|1440p|480p|360p|uhd|fhd|hd|x264|x265|hevc|h264|h265|av1|xvid|divx|web-dl|webrip|web|hdrip|bluray|bdrip|brrip|dvdrip|cam|ts|tc|scr|screener)\\b"), "")

        // 3. Remove HDR, 10bit, 60fps, etc.
        clean = clean.replace(Regex("(?i)\\b(hdr|hdr10|10bit|8bit|60fps)\\b"), "")

        // 4. Remove S01E01, S01, E01 style tags
        clean = clean.replace(Regex("(?i)\\b(S\\d{1,2}E\\d{1,2}|S\\d{1,2}|E\\d{1,2})\\b"), "")

        // 5. Remove language, dubbing, sub tags
        clean = clean.replace(Regex("(?i)\\b(tur|turkce|türkçe|ingilizce|eng|altyazil?i?|dublajl?i?|multi|dual)\\b"), "")
        clean = clean.replace(Regex("(?i)(\\([^)]*(dublaj|altyaz)[^)]*\\))|\\[(?:[^]]*(dublaj|altyaz)[^]]*)\\]"), "")

        // 6. Remove year in parentheses or brackets (but save for later use)
        clean = clean.replace(Regex("\\(\\d{4}\\)|\\[\\d{4}\\]"), "")
        clean = clean.replace(Regex("\\b(19|20)\\d{2}\\b"), "")

        // 7. Remove content commonly inside brackets or parentheses that is noise
        // e.g. [TR-EN], (ProviderName) — but be careful not to remove meaningful parenthetical names
        clean = clean.replace(Regex("\\[[^\\]]*\\]"), "")
        // Only remove parenthetical content if it looks like noise (short, has numbers/special chars)
        clean = clean.replace(Regex("\\([^)]{1,15}\\)"), "")

        // 8. Normalize quote/separator punctuation so provider titles still match TMDB titles.
        // Example: "Doctor Who" Joy to the World -> Doctor Who Joy to the World
        clean = clean.replace(Regex("[\"“”]"), "")
        clean = clean.replace(Regex("[-+_.:|/\\\\]+"), " ")

        // 9. Remove multiple spaces and trim
        val result = clean.trim().replace(Regex("\\s+"), " ")

        // Safety: if cleaning destroyed the title, fall back to original with minimal cleanup
        return if (result.isBlank() || result.length < 2) {
            title.trim()
                .replace(Regex("[\"“”]"), "")
                .replace(Regex("[-_.:|/\\\\]+"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")
        } else {
            result
        }
    }

    /**
     * Builds conservative TMDB query alternatives without discarding the complete title.
     * Provider catalogs often join a localized and an original title with a spaced separator;
     * searching the two halves as fallbacks improves coverage while the full title remains the
     * first and safest query.
     */
    fun metadataSearchTitleVariants(title: String): List<String> {
        val titleWithoutYear = extractYearFromTitle(title).first
        val splitParts = titleWithoutYear.split(Regex("\\s+(?:\\+|/|\\||-|–|—)\\s+"))
        val noiseTokens = setOf("tr", "en", "eng", "vod", "film", "movie", "sinema")

        return buildList {
            add(cleanTitleForSearch(title))
            splitParts.forEach { part -> add(cleanTitleForSearch(part)) }
        }
            .map(String::trim)
            .filter { candidate ->
                val normalized = normalizeTitle(candidate)
                normalized.length >= 2 && normalized !in noiseTokens
            }
            .distinctBy(::normalizeTitle)
    }

    /**
     * Extract year from a title string and return both the cleaned title and the year.
     * E.g. "The Matrix (1999)" -> Pair("The Matrix", 1999)
     * E.g. "Inception 2010 1080p" -> Pair("Inception 2010 1080p", 2010) (year from trailing position)
     */
    fun extractYearFromTitle(title: String): Pair<String, Int?> {
        // Try parenthesized year first
        val parenMatch = Regex("\\((19|20)\\d{2}\\)").find(title)
        if (parenMatch != null) {
            val year = parenMatch.value.trim('(', ')').toIntOrNull()
            val cleaned = title.replace(parenMatch.value, "").trim()
            return Pair(cleaned, year)
        }

        // Try bracketed year
        val bracketMatch = Regex("\\[(19|20)\\d{2}\\]").find(title)
        if (bracketMatch != null) {
            val year = bracketMatch.value.trim('[', ']').toIntOrNull()
            val cleaned = title.replace(bracketMatch.value, "").trim()
            return Pair(cleaned, year)
        }

        // Try trailing standalone year
        val trailingMatch = Regex("\\b(19|20)\\d{2}\\b").findAll(title).lastOrNull()
        if (trailingMatch != null) {
            val year = trailingMatch.value.toIntOrNull()
            return Pair(title, year)
        }

        return Pair(title, null)
    }

    fun fuzzyMatch(query: String, target: String, threshold: Double = 0.85): Boolean {
        val normalizedQuery = normalizeTitle(query)
        val normalizedTarget = normalizeTitle(target)
        if (normalizedQuery == normalizedTarget) return true
        if (normalizedTarget.contains(normalizedQuery) || normalizedQuery.contains(normalizedTarget)) return true

        // Very short strings need exact match
        if (normalizedQuery.length < 3 || normalizedTarget.length < 3) return false

        val similarity = jaroWinklerSimilarity(normalizedQuery, normalizedTarget)
        return similarity >= threshold
    }

    fun fuzzyMatchScore(query: String, target: String): Double {
        if (target.isBlank()) return 0.0
        val normalizedQuery = normalizeTitle(query)
        val normalizedTarget = normalizeTitle(target)
        if (normalizedQuery.isEmpty() || normalizedTarget.isEmpty()) return 0.0
        if (normalizedQuery == normalizedTarget) return 1.0
        if (normalizedTarget.contains(normalizedQuery) || normalizedQuery.contains(normalizedTarget)) {
            // Boost containment score by how close the lengths are
            val lengthRatio = minOf(normalizedQuery.length, normalizedTarget.length).toDouble() /
                              maxOf(normalizedQuery.length, normalizedTarget.length).toDouble()
            return 0.85 + (lengthRatio * 0.10) // Range: 0.85 - 0.95
        }

        if (normalizedQuery.length < 2 || normalizedTarget.length < 2) return 0.0

        return jaroWinklerSimilarity(normalizedQuery, normalizedTarget)
    }

    private fun jaroWinklerSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val matchDistance = (maxOf(s1.length, s2.length) / 2) - 1
        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)

        var matches = 0
        var transpositions = 0

        for (i in s1.indices) {
            val start = maxOf(0, i - matchDistance)
            val end = minOf(i + matchDistance + 1, s2.length)
            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val jaro = (matches.toDouble() / s1.length +
                matches.toDouble() / s2.length +
                (matches - transpositions / 2.0) / matches) / 3.0

        var prefix = 0
        for (i in 0 until minOf(4, minOf(s1.length, s2.length))) {
            if (s1[i] == s2[i]) prefix++ else break
        }

        return jaro + prefix * 0.1 * (1 - jaro)
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatDurationMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun extractYear(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val match = Regex("(19|20)\\d{2}").find(text)
        return match?.value?.toIntOrNull()
    }
}
