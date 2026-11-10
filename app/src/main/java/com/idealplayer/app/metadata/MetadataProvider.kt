package com.idealplayer.app.metadata

import com.idealplayer.app.BuildConfig
import com.idealplayer.app.core.common.Constants
import com.idealplayer.app.core.common.StringUtils
import com.idealplayer.app.core.common.rethrowIfCancellation
import com.idealplayer.app.core.database.MetadataCacheDao
import com.idealplayer.app.core.database.MetadataCacheEntity
import com.idealplayer.app.core.model.ContentType
import com.idealplayer.app.core.network.TmdbApi
import com.idealplayer.app.core.network.dto.TmdbDetailResponse
import com.idealplayer.app.core.network.dto.TmdbSearchResult
import com.idealplayer.app.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class MetadataResult(
    val tmdbId: Int = 0,
    val imdbId: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val overview: String = "",
    val tagline: String = "",
    val genre: String = "",
    val cast: String = "",
    val director: String = "",
    val runtime: Int = 0,
    val rating: Double = 0.0,
    val year: Int = 0,
    val trailerUrl: String = "",
    val confidence: Double = 0.0
)

internal fun metadataTitleSimilarity(
    requestedTitle: String,
    candidateTitle: String?,
    candidateOriginalTitle: String?
): Double {
    val queries = StringUtils.metadataSearchTitleVariants(requestedTitle)
    val candidates = listOfNotNull(candidateTitle, candidateOriginalTitle)
        .filter(String::isNotBlank)
    return queries.flatMap { query ->
        candidates.map { candidate -> StringUtils.fuzzyMatchScore(query, candidate) }
    }.maxOrNull() ?: 0.0
}

internal fun isPlausibleDirectMetadataMatch(
    requestedTitle: String,
    requestedYear: Int,
    detail: TmdbDetailResponse
): Boolean {
    val similarity = metadataTitleSimilarity(
        requestedTitle = requestedTitle,
        candidateTitle = detail.title ?: detail.name,
        candidateOriginalTitle = detail.originalTitle ?: detail.originalName
    )
    val candidateYear = StringUtils.extractYear(detail.releaseDate ?: detail.firstAirDate)
    val yearDifference = if (requestedYear > 0 && candidateYear != null) {
        kotlin.math.abs(requestedYear - candidateYear)
    } else {
        null
    }

    return when {
        yearDifference != null && yearDifference > 5 -> false
        similarity >= 0.97 && (yearDifference == null || yearDifference <= 3) -> true
        similarity >= 0.88 && (yearDifference == null || yearDifference <= 2) -> true
        similarity >= 0.72 && yearDifference != null && yearDifference <= 1 -> true
        else -> false
    }
}

@Singleton
class MetadataProvider @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val cacheDao: MetadataCacheDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val apiKey: String = BuildConfig.TMDB_API_KEY

    private data class SearchCandidate(
        val result: TmdbSearchResult,
        val detailType: ContentType
    )

    // Minimum confidence to accept a TMDB match
    companion object {
        private const val MIN_CONFIDENCE_SCORE = 55.0
        private const val MIN_TITLE_SIMILARITY_WITH_YEAR = 0.72
        private const val MIN_TITLE_SIMILARITY_WITHOUT_YEAR = 0.82
        private const val SHORT_TITLE_THRESHOLD = 3
        private const val SHORT_TITLE_MIN_CONFIDENCE = 50.0
        private const val CACHE_TTL_MS = 7 * 24 * 3600 * 1000L
        private const val CACHE_MATCH_VERSION = 2
        private const val TURKISH_METADATA_LANGUAGE = "tr-TR"
        private const val ENGLISH_FALLBACK_LANGUAGE = "en-US"
    }

    suspend fun getCachedMetadata(
        title: String,
        year: Int = 0,
        contentType: ContentType = ContentType.MOVIE
    ): MetadataResult? {
        if (contentType == ContentType.LIVE) return null

        val normalizedTitle = StringUtils.normalizeTitle(title)
        if (normalizedSearchLength(normalizedTitle) <= SHORT_TITLE_THRESHOLD) return null

        val languageTag = currentLanguageTag()
        val cached = cacheDao.find(
            normalizedTitle,
            year,
            languageTag,
            metadataCacheType(contentType)
        ) ?: return null

        return if (isFreshUsableCache(cached)) {
            cached.toResult()
        } else {
            null
        }
    }

    suspend fun fetchMetadata(
        title: String,
        year: Int = 0,
        contentType: ContentType = ContentType.MOVIE,
        tmdbId: Int = 0
    ): MetadataResult? {
        if (apiKey.isBlank()) {
            Timber.w("TMDB API key not configured")
            return null
        }

        try {
            val cleanTitle = StringUtils.cleanTitleForSearch(title)
            val normalizedTitle = StringUtils.normalizeTitle(title)

            // Extract year from title if not provided
            val (_, titleYear) = StringUtils.extractYearFromTitle(title)
            val effectiveYear = if (year > 0) year else (titleYear ?: 0)

            val languageTag = currentLanguageTag()
            val canUseTitleCache = normalizedSearchLength(cleanTitle) > SHORT_TITLE_THRESHOLD
            val cacheType = metadataCacheType(contentType)

            // Check locale-aware cache. Skip partial caches so blank detail pages can be repaired.
            // Title ownership is mandatory: a provider can attach the same stale TMDB id to
            // unrelated rows, so an id-only cache lookup can leak one title into another.
            val cached = if (canUseTitleCache) {
                cacheDao.find(normalizedTitle, effectiveYear, languageTag, cacheType)
            } else {
                null
            }
            if (cached != null && isFreshUsableCache(cached)) {
                Timber.d("Cache hit for '$normalizedTitle' (lang=$languageTag)")
                return cached.toResult()
            }

            if (tmdbId > 0) {
                val localizedDetail = runCatching {
                    fetchDetailByTmdbId(tmdbId, contentType, languageTag)
                }.onFailure { error ->
                    error.rethrowIfCancellation()
                    Timber.w(
                        "Provider TMDB id lookup failed for id=%d: %s; retrying by title",
                        tmdbId,
                        error.javaClass.simpleName
                    )
                }.getOrNull()
                if (
                    localizedDetail != null &&
                    isPlausibleDirectMetadataMatch(title, effectiveYear, localizedDetail)
                ) {
                    val fallbackDetail = fetchFallbackDetailIfNeeded(localizedDetail, contentType, tmdbId, languageTag)
                    val result = buildResult(
                        detail = localizedDetail,
                        fallbackDetail = fallbackDetail,
                        bestMatch = null,
                        effectiveYear = effectiveYear,
                        confidence = 100.0,
                        languageTag = languageTag
                    )
                    cacheResult(normalizedTitle, result, languageTag, contentType)
                    return result.takeIf { it.hasUsableDetails() }
                }
                Timber.w(
                    "Provider TMDB id=%d does not match title='%s' year=%d; retrying by title",
                    tmdbId,
                    cleanTitle,
                    effectiveYear
                )
            }

            val searchResults = searchMetadataCandidates(cleanTitle, title, effectiveYear, contentType, languageTag)

            if (searchResults.isEmpty()) {
                Timber.d("No TMDB results for '$cleanTitle'")
                return null
            }

            // Smart scoring to establish best match with confidence
            val scoredResults = searchResults
                .asSequence()
                .filter { candidate -> candidate.detailType == contentType }
                .distinctBy { "${it.detailType.name}:${it.result.id}" }
                .map { candidate ->
                    val score = calculateMatchScore(
                        cleanTitle,
                        title,
                        effectiveYear,
                        candidate.result,
                        contentType
                    )
                    Pair(candidate, score)
                }
                .sortedByDescending { it.second }
                .toList()

            val bestPair = scoredResults.firstOrNull() ?: return null
            val bestMatch = bestPair.first
            val bestScore = bestPair.second

            // Confidence threshold — NEVER accept low-confidence matches
            val isShortTitle = normalizedSearchLength(cleanTitle) <= SHORT_TITLE_THRESHOLD
            val requiredConfidence = if (isShortTitle) SHORT_TITLE_MIN_CONFIDENCE else MIN_CONFIDENCE_SCORE

            if (bestScore < requiredConfidence) {
                Timber.w("Best match for '$cleanTitle' scored $bestScore (threshold=$requiredConfidence) — rejecting to prevent wrong metadata")
                return null
            }

            val bestTitleSim = getBestTitleSimilarity(cleanTitle, title, bestMatch.result)
            val requiredTitleSimilarity = if (effectiveYear > 0) {
                MIN_TITLE_SIMILARITY_WITH_YEAR
            } else {
                MIN_TITLE_SIMILARITY_WITHOUT_YEAR
            }
            if (bestTitleSim < requiredTitleSimilarity) {
                Timber.w("Title similarity too low ($bestTitleSim, threshold=$requiredTitleSimilarity) for '$cleanTitle' vs '${bestMatch.result.title ?: bestMatch.result.name}' — rejecting")
                return null
            }

            Timber.d("TMDB match: '${bestMatch.result.title ?: bestMatch.result.name}' type=${bestMatch.detailType} score=$bestScore sim=$bestTitleSim for query='$cleanTitle'")

            // Fetch full details
            val detail = when (bestMatch.detailType) {
                ContentType.MOVIE -> tmdbApi.getMovieDetails(bestMatch.result.id, apiKey, language = languageTag)
                ContentType.SERIES -> tmdbApi.getTvDetails(bestMatch.result.id, apiKey, language = languageTag)
                ContentType.LIVE -> return null
            }
            val fallbackDetail = fetchFallbackDetailIfNeeded(detail, bestMatch.detailType, bestMatch.result.id, languageTag)

            val result = buildResult(
                detail = detail,
                fallbackDetail = fallbackDetail,
                bestMatch = bestMatch.result,
                effectiveYear = effectiveYear,
                confidence = bestScore,
                languageTag = languageTag
            )

            cacheResult(normalizedTitle, result, languageTag, contentType)

            return result.takeIf { it.hasUsableDetails() }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "Failed to fetch metadata for: $title")
            return null
        }
    }

    private suspend fun currentLanguageTag(): String {
        settingsDataStore.settings.first()
        return TURKISH_METADATA_LANGUAGE
    }

    private suspend fun fetchDetailByTmdbId(
        tmdbId: Int,
        contentType: ContentType,
        languageTag: String
    ): TmdbDetailResponse? {
        return when (contentType) {
            ContentType.MOVIE -> tmdbApi.getMovieDetails(tmdbId, apiKey, language = languageTag)
            ContentType.SERIES -> tmdbApi.getTvDetails(tmdbId, apiKey, language = languageTag)
            ContentType.LIVE -> null
        }
    }

    private suspend fun fetchFallbackDetailIfNeeded(
        localizedDetail: TmdbDetailResponse,
        contentType: ContentType,
        tmdbId: Int,
        languageTag: String
    ): TmdbDetailResponse? {
        if (languageTag.equals(ENGLISH_FALLBACK_LANGUAGE, ignoreCase = true)) return null
        if (!localizedDetail.needsFallbackDetail()) return null

        return runCatching {
            fetchDetailByTmdbId(tmdbId, contentType, ENGLISH_FALLBACK_LANGUAGE)
        }.onFailure { error ->
            error.rethrowIfCancellation()
            Timber.w(error, "Failed to fetch English fallback metadata for TMDB id=$tmdbId")
        }.getOrNull()
    }

    private suspend fun searchMetadataCandidates(
        cleanTitle: String,
        originalTitle: String,
        effectiveYear: Int,
        contentType: ContentType,
        languageTag: String
    ): List<SearchCandidate> {
        val queries = buildList {
            addAll(StringUtils.metadataSearchTitleVariants(originalTitle))
            add(cleanTitle)
            val extractedTitle = StringUtils.extractYearFromTitle(originalTitle).first
            add(StringUtils.cleanTitleForSearch(extractedTitle))
            add(originalTitle)
        }.map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

        val languages = listOf(languageTag, ENGLISH_FALLBACK_LANGUAGE).distinct()

        val candidates = mutableListOf<SearchCandidate>()

        suspend fun searchExpectedType(query: String, year: Int?, language: String) {
            val result = runCatching {
                when (contentType) {
                    ContentType.MOVIE -> tmdbApi.searchMovie(apiKey, query, year, 1, language).results
                        .map { SearchCandidate(it, ContentType.MOVIE) }
                    ContentType.SERIES -> tmdbApi.searchTv(apiKey, query, year, 1, language).results
                        .map { SearchCandidate(it, ContentType.SERIES) }
                    ContentType.LIVE -> emptyList()
                }
            }.getOrElse { error ->
                error.rethrowIfCancellation()
                Timber.w("TMDB typed search failed: %s", error.javaClass.simpleName)
                emptyList()
            }
            candidates += result
        }

        suspend fun searchMulti(query: String, year: Int?, language: String) {
            val result = runCatching {
                tmdbApi.searchMulti(apiKey, query, year, 1, language).results
                    .mapNotNull { item ->
                        when (item.mediaType.lowercase()) {
                            "movie" -> SearchCandidate(item, ContentType.MOVIE)
                            "tv" -> SearchCandidate(item, ContentType.SERIES)
                            else -> null
                        }
                    }
            }.getOrElse { error ->
                error.rethrowIfCancellation()
                Timber.w("TMDB multi search failed: %s", error.javaClass.simpleName)
                emptyList()
            }
            candidates += result
        }

        val requestedYear = effectiveYear.takeIf { it > 0 }
        for (query in queries) {
            for (language in languages) {
                searchExpectedType(query, requestedYear, language)
            }
            val hasStrongTypedCandidate = candidates.any { candidate ->
                candidate.detailType == contentType &&
                    metadataTitleSimilarity(
                        requestedTitle = query,
                        candidateTitle = candidate.result.title ?: candidate.result.name,
                        candidateOriginalTitle = candidate.result.originalTitle ?: candidate.result.originalName
                    ) >= 0.88
            }
            if (hasStrongTypedCandidate) break
        }

        // Some providers place TV specials in a movie bucket (and vice versa). If the typed
        // endpoint only produced fuzzy containment matches, include multi-search so an exact
        // cross-media title can beat a visually plausible but wrong poster.
        val primaryQuery = queries.firstOrNull().orEmpty()
        val normalizedPrimary = StringUtils.normalizeTitle(primaryQuery)
        val hasExactCandidate = candidates.any { candidate ->
            val title = candidate.result.title ?: candidate.result.name.orEmpty()
            val original = candidate.result.originalTitle ?: candidate.result.originalName.orEmpty()
            normalizedPrimary.isNotBlank() && (
                normalizedPrimary == StringUtils.normalizeTitle(title) ||
                    normalizedPrimary == StringUtils.normalizeTitle(original)
                )
        }
        if (candidates.isNotEmpty() && !hasExactCandidate && primaryQuery.isNotBlank()) {
            for (language in languages) {
                searchMulti(primaryQuery, requestedYear, language)
            }
        }

        // Provider years are frequently wrong. Retry the cleanest title without the year
        // before falling back to a cross-media search.
        if (candidates.isEmpty() && requestedYear != null) {
            if (primaryQuery.isNotBlank()) {
                for (language in languages) {
                    searchExpectedType(primaryQuery, null, language)
                }
            }
        }

        if (candidates.isEmpty()) {
            for (query in queries) {
                for (language in languages) {
                    searchMulti(query, requestedYear, language)
                }
                if (candidates.isNotEmpty()) break
            }
        }

        val normalizedClean = StringUtils.normalizeTitle(cleanTitle)
        return if (normalizedSearchLength(normalizedClean) <= SHORT_TITLE_THRESHOLD) {
            candidates.filter { candidate ->
                val resultTitle = candidate.result.title ?: candidate.result.name ?: ""
                val resultOriginalTitle = candidate.result.originalTitle ?: candidate.result.originalName ?: ""
                normalizedClean == StringUtils.normalizeTitle(resultTitle) ||
                    normalizedClean == StringUtils.normalizeTitle(resultOriginalTitle)
            }
        } else {
            candidates
        }
    }

    private fun buildResult(
        detail: TmdbDetailResponse,
        fallbackDetail: TmdbDetailResponse?,
        bestMatch: TmdbSearchResult?,
        effectiveYear: Int,
        confidence: Double,
        languageTag: String
    ): MetadataResult {
        val translations = detail.translations?.translations ?: fallbackDetail?.translations?.translations ?: emptyList()
        val translated = resolveTranslation(translations, languageTag, detail.originalLanguage)
        val finalOverview = translated?.overview?.takeIf { it.isNotBlank() }
            ?: detail.overview.ifBlank { fallbackDetail?.overview.orEmpty() }
        val finalTagline = translated?.tagline?.takeIf { it.isNotBlank() }
            ?: detail.tagline.orEmpty().ifBlank { fallbackDetail?.tagline.orEmpty() }

        val director = detail.credits?.crew?.find { it.job == "Director" }?.name
            ?: fallbackDetail?.credits?.crew?.find { it.job == "Director" }?.name
            ?: detail.createdBy?.firstOrNull()?.name
            ?: fallbackDetail?.createdBy?.firstOrNull()?.name
            ?: ""

        val runtime = detail.runtime
            ?: fallbackDetail?.runtime
            ?: detail.episodeRunTime?.firstOrNull()
            ?: fallbackDetail?.episodeRunTime?.firstOrNull()
            ?: 0

        val videoResults = detail.videos?.results.orEmpty().ifEmpty { fallbackDetail?.videos?.results.orEmpty() }
        val trailerUrl = videoResults
            .filter { it.site.equals("YouTube", ignoreCase = true) && it.type.equals("Trailer", ignoreCase = true) }
            .maxByOrNull { if (it.official) 1 else 0 }
            ?.let { "https://www.youtube.com/watch?v=${it.key}" }
            ?: videoResults
                .firstOrNull { it.site.equals("YouTube", ignoreCase = true) }
                ?.let { "https://www.youtube.com/watch?v=${it.key}" }
            ?: ""

        val genres = detail.genres.ifEmpty { fallbackDetail?.genres.orEmpty() }
        val cast = detail.credits?.cast.orEmpty().ifEmpty { fallbackDetail?.credits?.cast.orEmpty() }

        return MetadataResult(
            tmdbId = detail.id.takeIf { it > 0 } ?: fallbackDetail?.id ?: 0,
            imdbId = detail.imdbId ?: detail.externalIds?.imdbId ?: fallbackDetail?.imdbId ?: fallbackDetail?.externalIds?.imdbId ?: "",
            posterUrl = detail.posterPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_POSTER_SIZE}$it" }
                ?: fallbackDetail?.posterPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_POSTER_SIZE}$it" }
                ?: bestMatch?.posterPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_POSTER_SIZE}$it" }
                ?: "",
            backdropUrl = detail.backdropPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_BACKDROP_SIZE}$it" }
                ?: fallbackDetail?.backdropPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_BACKDROP_SIZE}$it" }
                ?: bestMatch?.backdropPath?.let { "${Constants.TMDB_IMAGE_BASE_URL}${Constants.TMDB_BACKDROP_SIZE}$it" }
                ?: "",
            overview = finalOverview,
            tagline = finalTagline,
            genre = genres.joinToString(", ") { it.name },
            cast = cast.take(15).joinToString(", ") { it.name },
            director = director,
            runtime = runtime,
            rating = when {
                detail.voteAverage > 0 -> detail.voteAverage
                fallbackDetail?.voteAverage != null && fallbackDetail.voteAverage > 0 -> fallbackDetail.voteAverage
                else -> bestMatch?.voteAverage ?: 0.0
            },
            year = StringUtils.extractYear(detail.releaseDate ?: detail.firstAirDate)
                ?: StringUtils.extractYear(fallbackDetail?.releaseDate ?: fallbackDetail?.firstAirDate)
                ?: StringUtils.extractYear(bestMatch?.releaseDate ?: bestMatch?.firstAirDate)
                ?: effectiveYear,
            trailerUrl = trailerUrl,
            confidence = confidence
        )
    }

    private suspend fun cacheResult(
        normalizedTitle: String,
        result: MetadataResult,
        languageTag: String,
        contentType: ContentType
    ) {
        cacheDao.insert(
            MetadataCacheEntity(
                title = normalizedTitle,
                year = result.year,
                language = languageTag,
                tmdbId = result.tmdbId,
                imdbId = result.imdbId,
                posterUrl = result.posterUrl,
                backdropUrl = result.backdropUrl,
                overview = result.overview,
                tagline = result.tagline,
                genre = result.genre,
                cast = result.cast,
                director = result.director,
                runtime = result.runtime,
                rating = result.rating,
                trailerUrl = result.trailerUrl,
                contentType = metadataCacheType(contentType)
            )
        )
    }

    /**
     * Calculate a multi-signal match score for a TMDB search result.
     * Returns a score from 0-100 where higher is better.
     */
    private fun calculateMatchScore(
        cleanTitle: String,
        originalTitle: String,
        year: Int,
        result: TmdbSearchResult,
        expectedType: ContentType
    ): Double {
        var score = 0.0
        val resultTitle = result.title ?: result.name ?: ""
        val resultOriginalTitle = result.originalTitle ?: result.originalName ?: ""
        val resultYear = StringUtils.extractYear(result.releaseDate ?: result.firstAirDate)
        val normalizedClean = StringUtils.normalizeTitle(cleanTitle)
        val normalizedResult = StringUtils.normalizeTitle(resultTitle)
        val normalizedOriginal = StringUtils.normalizeTitle(resultOriginalTitle)

        // ─── 1. Title similarity (max 50 points) ───
        val bestTitleSim = getBestTitleSimilarity(cleanTitle, originalTitle, result)
        score += bestTitleSim * 50
        if (normalizedClean.isNotBlank() && (normalizedClean == normalizedResult || normalizedClean == normalizedOriginal)) {
            score += 28.0
        }

        // ─── 2. Year match (max 25 points, min -15) ───
        if (year > 0 && resultYear != null) {
            val yearDiff = kotlin.math.abs(year - resultYear)
            score += when {
                yearDiff == 0 -> 25.0
                yearDiff == 1 -> 15.0
                yearDiff == 2 -> 5.0
                yearDiff <= 5 -> -10.0
                else -> -40.0
            }
        } else if (year > 0 && resultYear == null) {
            score -= 3.0 // Small penalty if we have year but result doesn't
        }

        // ─── 3. Popularity as minor tiebreaker (max 5 points) ───
        // Use log scale to prevent runaway scores
        val popBonus = minOf(5.0, kotlin.math.ln(1.0 + (result.popularity / 10.0)))
        score += popBonus

        // ─── 4. Original language compatibility (max 5 points, min -10) ───
        val resultLang = result.originalLanguage ?: ""
        if (resultLang.isNotBlank()) {
            // Penalty for unexpected languages when title is clearly Latin-script
            val titleIsLatin = cleanTitle.all { it.isLetterOrDigit() || it.isWhitespace() || it in "-':!,." }
            val langIsUnexpected = resultLang in listOf("ko", "ja", "zh", "ar", "hi", "th")
            if (titleIsLatin && langIsUnexpected && bestTitleSim < 0.90) {
                score -= 10.0
            }
            // Small bonus for Turkish or English content when matching Latin titles
            if (resultLang in listOf("tr", "en", "de", "fr", "es", "it", "pt", "hr", "nl", "sv", "no", "da")) {
                score += 3.0
            }
        }

        // ─── 5. Vote average signal (max 3 points) ───
        if (result.voteAverage > 0) {
            score += minOf(3.0, result.voteAverage * 0.3)
        }

        // ─── 6. Short title extra caution ───
        if (normalizedSearchLength(normalizedClean) <= SHORT_TITLE_THRESHOLD) {
            // For very short titles, require near-exact match
            if (normalizedClean != normalizedResult && normalizedClean != normalizedOriginal) {
                score -= 80.0 // Heavy penalty for non-exact short title matches
            }
        }

        val resultType = when (result.mediaType.lowercase()) {
            "movie" -> ContentType.MOVIE
            "tv" -> ContentType.SERIES
            else -> expectedType
        }
        if (resultType != expectedType) score -= 40.0

        return score
    }

    /**
     * Get the best title similarity across all available title variants.
     */
    private fun getBestTitleSimilarity(
        cleanTitle: String,
        originalTitle: String,
        result: TmdbSearchResult
    ): Double {
        val resultTitle = result.title ?: result.name ?: ""
        val resultOriginalTitle = result.originalTitle ?: result.originalName ?: ""

        val queryVariants = buildList {
            add(cleanTitle)
            addAll(StringUtils.metadataSearchTitleVariants(originalTitle))
        }.distinctBy(StringUtils::normalizeTitle)
        val resultVariants = listOf(resultTitle, resultOriginalTitle).filter(String::isNotBlank)
        val similarities = queryVariants.flatMap { query ->
            resultVariants.map { candidate -> StringUtils.fuzzyMatchScore(query, candidate) }
        }

        return similarities.maxOrNull() ?: 0.0
    }

    /**
     * Resolve the best translation following priority:
     * 1. Requested locale (e.g. Turkish)
     * 2. Original Turkish language data, if the content itself is Turkish
     * 3. English fallback, so detail pages are not left blank when Turkish metadata is unavailable.
     */
    private fun resolveTranslation(
        translations: List<com.idealplayer.app.core.network.dto.TmdbTranslation>,
        requestedLanguageTag: String,
        originalLanguage: String?
    ): com.idealplayer.app.core.network.dto.TmdbTranslationData? {
        if (translations.isEmpty()) return null

        val requestedLang = requestedLanguageTag.split("-").firstOrNull()?.lowercase() ?: ""

        // 1. Try requested language (e.g. "tr")
        val requested = translations.find {
            it.language.lowercase() == requestedLang && !it.data?.overview.isNullOrBlank()
        }?.data
        if (requested != null) return requested

        // 2. Try original language only when it is Turkish.
        if (originalLanguage.equals("tr", ignoreCase = true)) {
            val original = translations.find {
                it.language.equals("tr", ignoreCase = true) && !it.data?.overview.isNullOrBlank()
            }?.data
            if (original != null) return original
        }

        // 3. English fallback. This is preferable to a blank detail screen.
        return translations.find {
            it.language.equals("en", ignoreCase = true) && !it.data?.overview.isNullOrBlank()
        }?.data
    }

    private fun isFreshUsableCache(cached: MetadataCacheEntity): Boolean {
        return System.currentTimeMillis() - cached.cachedAt < CACHE_TTL_MS && cached.toResult().hasUsableDetails()
    }

    private fun TmdbDetailResponse.needsFallbackDetail(): Boolean {
        return overview.isBlank() ||
            genres.isEmpty() ||
            credits?.cast.isNullOrEmpty() ||
            (posterPath.isNullOrBlank() && backdropPath.isNullOrBlank())
    }

    private fun normalizedSearchLength(value: String): Int =
        StringUtils.normalizeTitle(value).count(Char::isLetterOrDigit)

    private fun metadataCacheType(contentType: ContentType): String =
        "${contentType.name}:v$CACHE_MATCH_VERSION"

    private fun MetadataResult.hasUsableDetails(): Boolean {
        return overview.isNotBlank() ||
            genre.isNotBlank() ||
            cast.isNotBlank() ||
            director.isNotBlank() ||
            posterUrl.isNotBlank() ||
            backdropUrl.isNotBlank() ||
            rating > 0.0 ||
            year > 0
    }

    private fun MetadataCacheEntity.toResult() = MetadataResult(
        tmdbId = tmdbId,
        imdbId = imdbId,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        overview = overview,
        tagline = tagline,
        genre = genre,
        cast = cast,
        director = director,
        runtime = runtime,
        rating = rating,
        year = year,
        trailerUrl = trailerUrl,
        confidence = 100.0 // Cached results were already validated
    )
}
