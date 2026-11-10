package com.idealplayer.app.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idealplayer.app.R
import com.idealplayer.app.core.common.isUsableArtworkUrl
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.IdealPlayerTheme
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.core.model.Channel
import com.idealplayer.app.core.model.Movie
import com.idealplayer.app.core.model.Series
import com.idealplayer.app.ui.components.PosterImage
import com.idealplayer.app.ui.components.ArtworkFallbackStyle
import com.idealplayer.app.ui.components.a2.A2Badge
import com.idealplayer.app.ui.components.a2.A2BadgeTone
import com.idealplayer.app.ui.components.a2.A2ContentCard
import com.idealplayer.app.ui.components.a2.A2ContentCardKind
import com.idealplayer.app.ui.components.a2.A2StatusSurface
import com.idealplayer.app.ui.components.a2.A2StatusType
import com.idealplayer.app.ui.tv.TvFavoritesState
import com.idealplayer.app.ui.tv.TvFavoritesViewModel

/**
 * Touch-first Favorites destination for both the mobile bottom-navigation shell and the
 * tablet navigation-rail shell. Favorites remain sourced by [TvFavoritesViewModel] so the
 * mobile, tablet, and TV surfaces observe the same repository flows.
 */
@Composable
fun FavoritesScreen(
    onMovieClick: (Long) -> Unit,
    onSeriesClick: (Long) -> Unit,
    onPlayChannel: (String, String, Long, String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvFavoritesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    FavoritesContent(
        state = state,
        onMovieClick = onMovieClick,
        onSeriesClick = onSeriesClick,
        onPlayChannel = onPlayChannel,
        modifier = modifier
    )
}

/** Stateless content split kept independently renderable for previews and Compose tests. */
@Composable
internal fun FavoritesContent(
    state: TvFavoritesState,
    onMovieClick: (Long) -> Unit,
    onSeriesClick: (Long) -> Unit,
    onPlayChannel: (String, String, Long, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalIdealPlayerDimens.current
    val title = stringResource(R.string.favorites)
    val movieTitle = stringResource(R.string.nav_movies)
    val seriesTitle = stringResource(R.string.nav_series)
    val channelTitle = stringResource(R.string.channels)
    val totalCount = state.movies.size + state.series.size + state.channels.size

    when {
        state.isLoading -> FavoritesStatusContent(
            title = title,
            totalCount = totalCount,
            statusTitle = stringResource(R.string.loading),
            statusMessage = stringResource(R.string.loading),
            statusType = A2StatusType.Loading,
            modifier = modifier
        )

        totalCount == 0 -> FavoritesStatusContent(
            title = title,
            totalCount = totalCount,
            statusTitle = title,
            statusMessage = stringResource(R.string.no_content),
            statusType = A2StatusType.Empty,
            modifier = modifier
        )

        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(dimens.gridColumns),
            modifier = modifier
                .fillMaxSize()
                .background(IdealPlayerColors.Background),
            contentPadding = PaddingValues(
                start = dimens.screenPadding,
                top = dimens.screenPadding,
                end = dimens.screenPadding,
                bottom = dimens.screenPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(dimens.sectionSpacing)
        ) {
            item(
                key = "favorites_header",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                FavoritesHeader(title = title, count = totalCount)
            }

            if (state.movies.isNotEmpty()) {
                item(
                    key = "favorites_movies_header",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    FavoritesSectionHeader(title = movieTitle, count = state.movies.size)
                }
                items(
                    items = state.movies,
                    key = { movie -> "favorite_movie_${movie.id}_${movie.streamId}" }
                ) { movie ->
                    FavoriteMovieCard(
                        movie = movie,
                        onClick = { onMovieClick(movie.id) }
                    )
                }
            }

            if (state.series.isNotEmpty()) {
                item(
                    key = "favorites_series_header",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    FavoritesSectionHeader(title = seriesTitle, count = state.series.size)
                }
                items(
                    items = state.series,
                    key = { series -> "favorite_series_${series.id}_${series.seriesId}" }
                ) { series ->
                    FavoriteSeriesCard(
                        series = series,
                        onClick = { onSeriesClick(series.id) }
                    )
                }
            }

            if (state.channels.isNotEmpty()) {
                item(
                    key = "favorites_channels_header",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    FavoritesSectionHeader(title = channelTitle, count = state.channels.size)
                }
                items(
                    items = state.channels,
                    key = { channel -> "favorite_channel_${channel.id}_${channel.streamId}" },
                    span = {
                        GridItemSpan(
                            if (dimens.gridColumns >= 4) 2 else maxLineSpan
                        )
                    }
                ) { channel ->
                    FavoriteChannelCard(
                        channel = channel,
                        onClick = {
                            onPlayChannel(
                                channel.streamUrl,
                                channel.name,
                                channel.id,
                                channel.groupTitle.takeIf(String::isNotBlank)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesStatusContent(
    title: String,
    totalCount: Int,
    statusTitle: String,
    statusMessage: String,
    statusType: A2StatusType,
    modifier: Modifier = Modifier
) {
    val dimens = LocalIdealPlayerDimens.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IdealPlayerColors.Background)
            .padding(dimens.screenPadding)
    ) {
        FavoritesHeader(title = title, count = totalCount)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            A2StatusSurface(
                type = statusType,
                title = statusTitle,
                message = statusMessage,
                stateDescription = statusMessage,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FavoritesHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = IdealPlayerColors.TextPrimary,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        Spacer(Modifier.width(A2Spacing.md))
        A2Badge(
            text = count.toString(),
            tone = A2BadgeTone.Selected,
            contentDescription = "$title: $count"
        )
    }
}

@Composable
private fun FavoritesSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = IdealPlayerColors.TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        Spacer(Modifier.width(A2Spacing.sm))
        A2Badge(
            text = count.toString(),
            contentDescription = "$title: $count"
        )
    }
}

@Composable
private fun FavoriteMovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentDescription = "${stringResource(R.string.action_details)}: ${movie.name}"

    A2ContentCard(
        kind = A2ContentCardKind.Movie,
        title = movie.name,
        subtitle = movie.categoryName.ifBlank { movie.genre }.takeIf(String::isNotBlank),
        metadata = movie.year.takeIf { it > 0 }?.toString(),
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        artwork = if (isUsableArtworkUrl(movie.posterUrl)) {
            {
                PosterImage(
                    url = movie.posterUrl,
                    contentDescription = movie.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clearAndSetSemantics { }
                )
            }
        } else {
            null
        }
    )
}

@Composable
private fun FavoriteSeriesCard(
    series: Series,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentDescription = "${stringResource(R.string.action_details)}: ${series.name}"

    A2ContentCard(
        kind = A2ContentCardKind.Series,
        title = series.name,
        subtitle = series.categoryName.ifBlank { series.genre }.takeIf(String::isNotBlank),
        metadata = series.year.takeIf { it > 0 }?.toString(),
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        artwork = if (isUsableArtworkUrl(series.posterUrl)) {
            {
                PosterImage(
                    url = series.posterUrl,
                    contentDescription = series.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clearAndSetSemantics { }
                )
            }
        } else {
            null
        }
    )
}

@Composable
private fun FavoriteChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentDescription = "${stringResource(R.string.action_play)}: ${channel.name}"

    A2ContentCard(
        kind = A2ContentCardKind.Channel,
        title = channel.name,
        subtitle = channel.groupTitle.takeIf(String::isNotBlank),
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        artwork = {
            PosterImage(
                url = channel.logoUrl,
                contentDescription = channel.name,
                fallbackStyle = ArtworkFallbackStyle.Channel,
                modifier = Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics { }
            )
        }
    )
}

@Preview(name = "Favorites phone", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun FavoritesPhonePreview() {
    IdealPlayerTheme {
        FavoritesContent(
            state = previewFavoritesState(),
            onMovieClick = {},
            onSeriesClick = {},
            onPlayChannel = { _, _, _, _ -> }
        )
    }
}

@Preview(name = "Favorites tablet", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun FavoritesTabletPreview() {
    IdealPlayerTheme {
        FavoritesContent(
            state = previewFavoritesState(),
            onMovieClick = {},
            onSeriesClick = {},
            onPlayChannel = { _, _, _, _ -> }
        )
    }
}

private fun previewFavoritesState() = TvFavoritesState(
    movies = listOf(
        Movie(
            id = 1,
            playlistId = 1,
            name = "The Long Night",
            streamUrl = "",
            genre = "Drama",
            year = 2026,
            isFavorite = true
        ),
        Movie(
            id = 2,
            playlistId = 1,
            name = "Northern Lights",
            streamUrl = "",
            genre = "Adventure",
            year = 2025,
            isFavorite = true
        )
    ),
    series = listOf(
        Series(
            id = 3,
            playlistId = 1,
            name = "City Stories",
            genre = "Drama",
            year = 2026,
            isFavorite = true
        ),
        Series(
            id = 4,
            playlistId = 1,
            name = "Deep Space",
            genre = "Science Fiction",
            year = 2024,
            isFavorite = true
        )
    ),
    channels = listOf(
        Channel(
            id = 5,
            playlistId = 1,
            name = "City News",
            groupTitle = "News",
            streamUrl = "",
            isFavorite = true
        ),
        Channel(
            id = 6,
            playlistId = 1,
            name = "Arena Sports",
            groupTitle = "Sports",
            streamUrl = "",
            isFavorite = true
        )
    ),
    isLoading = false
)
