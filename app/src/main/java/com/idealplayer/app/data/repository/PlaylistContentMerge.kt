package com.idealplayer.app.data.repository

import com.idealplayer.app.core.database.ChannelEntity
import com.idealplayer.app.core.database.EpisodeEntity
import com.idealplayer.app.core.database.MovieEntity
import com.idealplayer.app.core.database.SeriesEntity
import com.idealplayer.app.core.common.StringUtils
import com.idealplayer.app.core.common.isUsableArtworkUrl
import com.idealplayer.app.core.player.parsePlaybackSource
import com.idealplayer.app.data.parser.epgLookupKeys
import java.util.Locale

/**
 * Stable natural keys and user-state preservation used when a playlist is refreshed.
 *
 * Content tables use auto-generated primary keys and the refresh path deletes then
 * re-inserts every row. Without preservation the re-inserted rows would receive brand
 * new ids, which (a) resets per-row user flags (isFavorite / watch progress) and
 * (b) orphans the [com.idealplayer.app.core.database.FavoriteEntity] and
 * [com.idealplayer.app.core.database.WatchHistoryEntity] rows that reference those ids by
 * `contentId`. These helpers carry the previous primary key and user-state forward so a
 * refresh keeps favorites and continue-watching intact.
 *
 * They are pure functions (no IO) so the merge behaviour can be unit tested directly.
 */

private fun String.normalizedKeyPart(): String = trim().lowercase(Locale.ROOT)

private fun normalizedUrlKey(url: String): String = parsePlaybackSource(url).url.trim()

internal fun ChannelEntity.stableContentKey(): String = when {
    streamId > 0 -> "stream:$streamId"
    streamUrl.isNotBlank() -> "url:${normalizedUrlKey(streamUrl)}"
    else -> "name:${name.normalizedKeyPart()}:${groupTitle.normalizedKeyPart()}"
}

internal fun MovieEntity.stableContentKey(): String = when {
    streamId > 0 -> "stream:$streamId"
    streamUrl.isNotBlank() -> "url:${normalizedUrlKey(streamUrl)}"
    else -> "name:${name.normalizedKeyPart()}:${categoryName.normalizedKeyPart()}"
}

internal fun SeriesEntity.stableContentKey(): String = when {
    seriesId > 0 -> "series:$seriesId"
    else -> "name:${name.normalizedKeyPart()}:${categoryName.normalizedKeyPart()}"
}

internal fun EpisodeEntity.stableContentKey(): String = when {
    streamUrl.isNotBlank() -> "url:${normalizedUrlKey(streamUrl)}"
    else -> "ep:$seriesId:s$seasonNumber:e$episodeNumber"
}

private fun ChannelEntity.artworkLookupKeys(): List<String> = buildList {
    sequenceOf(epgChannelId, name).forEach { value ->
        epgLookupKeys(value)
            // Avoid broad collisions such as a channel whose complete normalized name is
            // only "1". Real station identities remain available through the longer keys.
            .filter { key -> key.length >= 3 }
            .forEach { key -> if (key !in this) add(key) }
    }
}

/**
 * Reuses one valid station logo across provider quality aliases such as FHD/HD/RAW/TV.
 * The EPG matcher already understands those suffixes, locale prefixes and punctuation, so
 * artwork and guide lookup use exactly the same conservative channel identity rules.
 */
internal fun List<ChannelEntity>.withInheritedChannelArtwork(
    extraCandidates: Iterable<ChannelEntity> = emptyList()
): List<ChannelEntity> {
    val artworkByLookupKey = linkedMapOf<String, String>()
    sequence {
        yieldAll(this@withInheritedChannelArtwork)
        yieldAll(extraCandidates)
    }.forEach { candidate ->
        if (isUsableArtworkUrl(candidate.logoUrl)) {
            candidate.artworkLookupKeys().forEach { key ->
                artworkByLookupKey.putIfAbsent(key, candidate.logoUrl)
            }
        }
    }

    return map { channel ->
        if (isUsableArtworkUrl(channel.logoUrl)) {
            channel
        } else {
            val inherited = channel.artworkLookupKeys()
                .firstNotNullOfOrNull(artworkByLookupKey::get)
            if (inherited == null) channel else channel.copy(logoUrl = inherited)
        }
    }
}

private data class CatalogArtwork(val posterUrl: String = "", val backdropUrl: String = "")

private fun catalogArtworkKey(name: String, year: Int): String? {
    val (_, titleYear) = StringUtils.extractYearFromTitle(name)
    val normalizedTitle = StringUtils.normalizeTitle(StringUtils.cleanTitleForSearch(name))
    if (normalizedTitle.length < 2) return null
    return "$normalizedTitle|${year.takeIf { it > 0 } ?: titleYear ?: 0}"
}

private fun <T> inheritCatalogArtwork(
    rows: List<T>,
    extraCandidates: Iterable<T>,
    name: (T) -> String,
    year: (T) -> Int,
    poster: (T) -> String,
    backdrop: (T) -> String,
    copyWithArtwork: (T, String, String) -> T
): List<T> {
    val artworkByKey = linkedMapOf<String, CatalogArtwork>()
    sequence {
        yieldAll(rows)
        yieldAll(extraCandidates)
    }.forEach { candidate ->
        val key = catalogArtworkKey(name(candidate), year(candidate)) ?: return@forEach
        val current = artworkByKey[key] ?: CatalogArtwork()
        artworkByKey[key] = CatalogArtwork(
            posterUrl = current.posterUrl.takeIf(::isUsableArtworkUrl)
                ?: poster(candidate).takeIf(::isUsableArtworkUrl).orEmpty(),
            backdropUrl = current.backdropUrl.takeIf(::isUsableArtworkUrl)
                ?: backdrop(candidate).takeIf(::isUsableArtworkUrl).orEmpty()
        )
    }

    return rows.map { row ->
        val key = catalogArtworkKey(name(row), year(row)) ?: return@map row
        val inherited = artworkByKey[key] ?: return@map row
        val resolvedPoster = poster(row).takeIf(::isUsableArtworkUrl) ?: inherited.posterUrl
        val resolvedBackdrop = backdrop(row).takeIf(::isUsableArtworkUrl) ?: inherited.backdropUrl
        if (resolvedPoster == poster(row) && resolvedBackdrop == backdrop(row)) row
        else copyWithArtwork(row, resolvedPoster, resolvedBackdrop)
    }
}

internal fun List<MovieEntity>.withInheritedMovieArtwork(
    extraCandidates: Iterable<MovieEntity> = emptyList()
): List<MovieEntity> = inheritCatalogArtwork(
    rows = this,
    extraCandidates = extraCandidates,
    name = MovieEntity::name,
    year = MovieEntity::year,
    poster = MovieEntity::posterUrl,
    backdrop = MovieEntity::backdropUrl,
    copyWithArtwork = { row, posterUrl, backdropUrl ->
        row.copy(posterUrl = posterUrl, backdropUrl = backdropUrl)
    }
)

internal fun List<SeriesEntity>.withInheritedSeriesArtwork(
    extraCandidates: Iterable<SeriesEntity> = emptyList()
): List<SeriesEntity> = inheritCatalogArtwork(
    rows = this,
    extraCandidates = extraCandidates,
    name = SeriesEntity::name,
    year = SeriesEntity::year,
    poster = SeriesEntity::posterUrl,
    backdrop = SeriesEntity::backdropUrl,
    copyWithArtwork = { row, posterUrl, backdropUrl ->
        row.copy(posterUrl = posterUrl, backdropUrl = backdropUrl)
    }
)

/** Carry the previous id + favorite/online/last-watched state onto refreshed channels. */
internal fun List<ChannelEntity>.withPreservedChannelState(
    existing: Map<String, ChannelEntity>
): List<ChannelEntity> {
    val claimedIds = mutableSetOf<Long>()
    val merged = map { incoming ->
        val previous = existing[incoming.stableContentKey()]
            ?.takeIf { it.id > 0L && claimedIds.add(it.id) }
            ?: return@map incoming
        incoming.copy(
            id = previous.id,
            logoUrl = incoming.logoUrl.takeIf(::isUsableArtworkUrl) ?: previous.logoUrl,
            isFavorite = previous.isFavorite,
            lastWatched = previous.lastWatched,
            isOnline = previous.isOnline
        )
    }
    return merged.withInheritedChannelArtwork(existing.values)
}

/** Carry the previous id + favorite/progress state onto refreshed movies (also backfills addedAt). */
internal fun List<MovieEntity>.withPreservedMovieState(
    existing: Map<String, MovieEntity>,
    syncTime: Long
): List<MovieEntity> {
    val claimedIds = mutableSetOf<Long>()
    val merged = map { incoming ->
        val previous = existing[incoming.stableContentKey()]
            ?.takeIf { it.id > 0L && claimedIds.add(it.id) }
        if (previous == null) {
            incoming.copy(addedAt = incoming.addedAt.takeIf { it > 0L } ?: syncTime)
        } else {
            incoming.copy(
                id = previous.id,
                addedAt = previous.addedAt.takeIf { it > 0L } ?: syncTime,
                isFavorite = previous.isFavorite,
                lastPosition = previous.lastPosition,
                lastWatched = previous.lastWatched,
                totalDuration = previous.totalDuration,
                // Catalog snapshots are intentionally lightweight. Keep metadata that was
                // fetched from the provider detail endpoint or TMDB instead of erasing it on
                // every foreground refresh.
                posterUrl = incoming.posterUrl.takeIf(::isUsableArtworkUrl)
                    ?: previous.posterUrl,
                backdropUrl = incoming.backdropUrl.takeIf(::isUsableArtworkUrl)
                    ?: previous.backdropUrl,
                genre = incoming.genre.ifBlank { previous.genre },
                plot = incoming.plot.ifBlank { previous.plot },
                cast = incoming.cast.ifBlank { previous.cast },
                director = incoming.director.ifBlank { previous.director },
                releaseDate = incoming.releaseDate.ifBlank { previous.releaseDate },
                year = incoming.year.takeIf { it > 0 } ?: previous.year,
                duration = incoming.duration.takeIf { it > 0 } ?: previous.duration,
                rating = incoming.rating.takeIf { it > 0.0 } ?: previous.rating,
                imdbId = incoming.imdbId.ifBlank { previous.imdbId },
                tmdbId = incoming.tmdbId.takeIf { it > 0 } ?: previous.tmdbId
            )
        }
    }
    return merged.withInheritedMovieArtwork(existing.values)
}

/** Carry the previous id + favorite/last-watched-episode state onto refreshed series. */
internal fun List<SeriesEntity>.withPreservedSeriesState(
    existing: Map<String, SeriesEntity>,
    syncTime: Long
): List<SeriesEntity> {
    val claimedIds = mutableSetOf<Long>()
    val merged = map { incoming ->
        val previous = existing[incoming.stableContentKey()]
            ?.takeIf { it.id > 0L && claimedIds.add(it.id) }
        if (previous == null) {
            incoming.copy(addedAt = incoming.addedAt.takeIf { it > 0L } ?: syncTime)
        } else {
            incoming.copy(
                id = previous.id,
                addedAt = previous.addedAt.takeIf { it > 0L } ?: syncTime,
                isFavorite = previous.isFavorite,
                lastWatchedEpisodeId = previous.lastWatchedEpisodeId,
                posterUrl = incoming.posterUrl.takeIf(::isUsableArtworkUrl)
                    ?: previous.posterUrl,
                backdropUrl = incoming.backdropUrl.takeIf(::isUsableArtworkUrl)
                    ?: previous.backdropUrl,
                genre = incoming.genre.ifBlank { previous.genre },
                plot = incoming.plot.ifBlank { previous.plot },
                cast = incoming.cast.ifBlank { previous.cast },
                director = incoming.director.ifBlank { previous.director },
                releaseDate = incoming.releaseDate.ifBlank { previous.releaseDate },
                year = incoming.year.takeIf { it > 0 } ?: previous.year,
                rating = incoming.rating.takeIf { it > 0.0 } ?: previous.rating,
                imdbId = incoming.imdbId.ifBlank { previous.imdbId },
                tmdbId = incoming.tmdbId.takeIf { it > 0 } ?: previous.tmdbId,
                seasonCount = incoming.seasonCount.takeIf { it > 0 } ?: previous.seasonCount,
                episodeCount = incoming.episodeCount.takeIf { it > 0 } ?: previous.episodeCount
            )
        }
    }
    return merged.withInheritedSeriesArtwork(existing.values)
}

/** Carry the previous id + favorite/progress state onto refreshed episodes. */
internal fun List<EpisodeEntity>.withPreservedEpisodeState(
    existing: Map<String, EpisodeEntity>
): List<EpisodeEntity> {
    val claimedIds = mutableSetOf<Long>()
    return map { incoming ->
        val previous = existing[incoming.stableContentKey()]
            ?.takeIf { it.id > 0L && claimedIds.add(it.id) }
            ?: return@map incoming
        incoming.copy(
            id = previous.id,
            isFavorite = previous.isFavorite,
            lastPosition = previous.lastPosition,
            lastWatched = previous.lastWatched,
            totalDuration = previous.totalDuration
        )
    }
}
