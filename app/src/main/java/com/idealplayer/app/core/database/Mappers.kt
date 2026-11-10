package com.idealplayer.app.core.database

import com.idealplayer.app.core.model.*
import java.util.Locale

fun PlaylistEntity.toModel() = Playlist(
    id = id, name = name, type = resolvePlaylistType(),
    url = url, serverUrl = serverUrl, username = username, password = password,
    filePath = filePath, epgUrl = epgUrl, lastUpdated = lastUpdated, lastUsed = lastUsed,
    isActive = isActive, channelCount = channelCount, movieCount = movieCount, seriesCount = seriesCount
)

fun Playlist.toEntity() = PlaylistEntity(
    id = id, name = name, type = type.name,
    url = url, serverUrl = serverUrl, username = username, password = password,
    filePath = filePath, epgUrl = epgUrl, lastUpdated = lastUpdated, lastUsed = lastUsed,
    isActive = isActive, channelCount = channelCount, movieCount = movieCount, seriesCount = seriesCount
)

private fun PlaylistEntity.resolvePlaylistType(): PlaylistType {
    val normalizedType = type.trim().uppercase(Locale.ROOT)
    return when (normalizedType) {
        PlaylistType.M3U_URL.name,
        "M3U",
        "URL" -> PlaylistType.M3U_URL

        PlaylistType.M3U_FILE.name,
        "FILE",
        "LOCAL_FILE" -> PlaylistType.M3U_FILE

        PlaylistType.XTREAM_CODES.name,
        "XTREAM",
        "XC",
        "PORTAL" -> PlaylistType.XTREAM_CODES

        else -> when {
            serverUrl.isNotBlank() || username.isNotBlank() || password.isNotBlank() -> PlaylistType.XTREAM_CODES
            filePath.isNotBlank() -> PlaylistType.M3U_FILE
            else -> PlaylistType.M3U_URL
        }
    }
}

fun ChannelEntity.toModel() = Channel(
    id = id, playlistId = playlistId, streamId = streamId, name = name,
    logoUrl = logoUrl, groupTitle = groupTitle, streamUrl = streamUrl,
    epgChannelId = epgChannelId, catchupSource = catchupSource,
    isFavorite = isFavorite, lastWatched = lastWatched, sortOrder = sortOrder
)

fun Channel.toEntity() = ChannelEntity(
    id = id, playlistId = playlistId, streamId = streamId, name = name,
    logoUrl = logoUrl, groupTitle = groupTitle, streamUrl = streamUrl,
    epgChannelId = epgChannelId, catchupSource = catchupSource,
    isFavorite = isFavorite, lastWatched = lastWatched, sortOrder = sortOrder
)

fun MovieEntity.toModel() = Movie(
    id = id, playlistId = playlistId, streamId = streamId, name = name,
    posterUrl = posterUrl, backdropUrl = backdropUrl, streamUrl = streamUrl,
    genre = genre, plot = plot, cast = cast, director = director,
    releaseDate = releaseDate, year = year, duration = duration, rating = rating,
    imdbId = imdbId, tmdbId = tmdbId, containerExtension = containerExtension,
    categoryId = categoryId, categoryName = categoryName,
    isFavorite = isFavorite, lastPosition = lastPosition,
    lastWatched = lastWatched, totalDuration = totalDuration,
    addedAt = addedAt, sourceOrder = sourceOrder
)

fun Movie.toEntity() = MovieEntity(
    id = id, playlistId = playlistId, streamId = streamId, name = name,
    posterUrl = posterUrl, backdropUrl = backdropUrl, streamUrl = streamUrl,
    genre = genre, plot = plot, cast = cast, director = director,
    releaseDate = releaseDate, year = year, duration = duration, rating = rating,
    imdbId = imdbId, tmdbId = tmdbId, containerExtension = containerExtension,
    categoryId = categoryId, categoryName = categoryName,
    isFavorite = isFavorite, lastPosition = lastPosition,
    lastWatched = lastWatched, totalDuration = totalDuration,
    addedAt = addedAt, sourceOrder = sourceOrder
)

fun SeriesEntity.toModel() = Series(
    id = id, playlistId = playlistId, seriesId = seriesId, name = name,
    posterUrl = posterUrl, backdropUrl = backdropUrl, genre = genre,
    plot = plot, cast = cast, director = director, releaseDate = releaseDate,
    year = year, rating = rating, imdbId = imdbId, tmdbId = tmdbId,
    categoryId = categoryId, categoryName = categoryName,
    isFavorite = isFavorite, lastWatchedEpisodeId = lastWatchedEpisodeId,
    seasonCount = seasonCount, episodeCount = episodeCount,
    addedAt = addedAt, sourceOrder = sourceOrder
)

fun Series.toEntity() = SeriesEntity(
    id = id, playlistId = playlistId, seriesId = seriesId, name = name,
    posterUrl = posterUrl, backdropUrl = backdropUrl, genre = genre,
    plot = plot, cast = cast, director = director, releaseDate = releaseDate,
    year = year, rating = rating, imdbId = imdbId, tmdbId = tmdbId,
    categoryId = categoryId, categoryName = categoryName,
    isFavorite = isFavorite, lastWatchedEpisodeId = lastWatchedEpisodeId,
    seasonCount = seasonCount, episodeCount = episodeCount,
    addedAt = addedAt, sourceOrder = sourceOrder
)

fun EpisodeEntity.toModel() = Episode(
    id = id, seriesId = seriesId, seasonNumber = seasonNumber,
    episodeNumber = episodeNumber, name = name, plot = plot,
    posterUrl = posterUrl, streamUrl = streamUrl, duration = duration,
    rating = rating, containerExtension = containerExtension,
    isFavorite = isFavorite, lastPosition = lastPosition,
    lastWatched = lastWatched, totalDuration = totalDuration
)

fun Episode.toEntity() = EpisodeEntity(
    id = id, seriesId = seriesId, seasonNumber = seasonNumber,
    episodeNumber = episodeNumber, name = name, plot = plot,
    posterUrl = posterUrl, streamUrl = streamUrl, duration = duration,
    rating = rating, containerExtension = containerExtension,
    isFavorite = isFavorite, lastPosition = lastPosition,
    lastWatched = lastWatched, totalDuration = totalDuration
)

fun CategoryEntity.toModel() = Category(
    id = id, playlistId = playlistId, categoryId = categoryId,
    name = name, contentType = contentType.toContentTypeOrDefault(),
    parentId = parentId, itemCount = itemCount
)

fun Category.toEntity() = CategoryEntity(
    id = id, playlistId = playlistId, categoryId = categoryId,
    name = name, contentType = contentType.name,
    parentId = parentId, itemCount = itemCount
)

fun EpgProgramEntity.toModel() = EpgProgram(
    id = id, channelId = channelId, title = title,
    description = description, startTime = startTime, endTime = endTime,
    posterUrl = posterUrl, genre = genre
)

fun WatchHistoryEntity.toModel() = WatchHistoryItem(
    id = id, contentId = contentId, contentType = contentType.toContentTypeOrDefault(),
    title = title, posterUrl = posterUrl, streamUrl = streamUrl,
    position = position, totalDuration = totalDuration, progress = progress,
    timestamp = timestamp, seasonNumber = seasonNumber,
    episodeNumber = episodeNumber, seriesName = seriesName
)

fun WatchHistoryItem.toEntity() = WatchHistoryEntity(
    id = id, contentId = contentId, contentType = contentType.name,
    title = title, posterUrl = posterUrl, streamUrl = streamUrl,
    position = position, totalDuration = totalDuration, progress = progress,
    timestamp = timestamp, seasonNumber = seasonNumber,
    episodeNumber = episodeNumber, seriesName = seriesName
)

/**
 * Provider/legacy database values are not trustworthy enough for valueOf(). Keep a malformed
 * row from terminating an entire Room Flow; LIVE is the least surprising fallback because it
 * has no resume-only semantics and remains playable from the library.
 */
private fun String.toContentTypeOrDefault(): ContentType = when (trim().uppercase(Locale.ROOT)) {
    ContentType.LIVE.name, "CHANNEL", "TV" -> ContentType.LIVE
    ContentType.MOVIE.name, "VOD", "FILM" -> ContentType.MOVIE
    ContentType.SERIES.name, "TV_SERIES" -> ContentType.SERIES
    else -> ContentType.LIVE
}
