package com.idealplayer.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.idealplayer.app.R
import com.idealplayer.app.core.common.StringUtils
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.core.model.Channel
import com.idealplayer.app.core.model.ContentType
import com.idealplayer.app.core.model.Movie
import com.idealplayer.app.core.model.Series
import com.idealplayer.app.core.model.WatchHistoryItem
import com.idealplayer.app.data.repository.ContentRepository
import com.idealplayer.app.data.repository.PlaylistRepository
import com.idealplayer.app.ui.components.EmptyScreen
import com.idealplayer.app.ui.components.ArtworkFallbackStyle
import com.idealplayer.app.ui.components.IdealPlayerDrawer
import com.idealplayer.app.ui.components.LoadingScreen
import com.idealplayer.app.ui.components.PosterImage
import com.idealplayer.app.ui.components.a2.A2ActionButton
import com.idealplayer.app.ui.components.a2.A2ActionVariant
import com.idealplayer.app.ui.components.a2.A2BadgeTone
import com.idealplayer.app.ui.components.a2.A2ContentCard
import com.idealplayer.app.ui.components.a2.A2ContentCardKind
import com.idealplayer.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TvContinueWatchingState(
    val items: List<WatchHistoryItem> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class TvContinueWatchingViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(TvContinueWatchingState())
    val state: StateFlow<TvContinueWatchingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            contentRepository.getContinueWatching().collectLatest { items ->
                _state.value = TvContinueWatchingState(
                    items = items,
                    isLoading = false
                )
            }
        }
    }

    fun clearWatchHistory() = viewModelScope.launch {
        contentRepository.clearWatchHistory()
    }
}

data class TvFavoritesState(
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class TvFavoritesViewModel @Inject constructor(
    playlistRepository: PlaylistRepository,
    contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(TvFavoritesState())
    val state: StateFlow<TvFavoritesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.getActivePlaylist().collectLatest { playlist ->
                if (playlist == null) {
                    _state.value = TvFavoritesState(isLoading = false)
                } else {
                    launch {
                        contentRepository.getFavoriteMovies(playlist.id).collectLatest { movies ->
                            _state.value = _state.value.copy(
                                movies = movies,
                                isLoading = false
                            )
                        }
                    }
                    launch {
                        contentRepository.getFavoriteSeries(playlist.id).collectLatest { series ->
                            _state.value = _state.value.copy(
                                series = series,
                                isLoading = false
                            )
                        }
                    }
                    launch {
                        contentRepository.getFavoriteChannels(playlist.id).collectLatest { channels ->
                            _state.value = _state.value.copy(
                                channels = channels,
                                isLoading = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvContinueWatchingScreen(
    onNavigate: (String) -> Unit,
    onResume: (WatchHistoryItem) -> Unit,
    onOpenDetail: (Long, String) -> Unit,
    viewModel: TvContinueWatchingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isDrawerExpanded by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    val clearHistoryFocusRequester = remember { FocusRequester() }
    val clearHistoryDialogCancelRequester = remember { FocusRequester() }
    var wasClearHistoryDialogVisible by remember { mutableStateOf(false) }
    var focusDrawerAfterClearingHistory by remember { mutableStateOf(false) }

    LaunchedEffect(showClearHistoryDialog, focusDrawerAfterClearingHistory) {
        when {
            showClearHistoryDialog -> {
                wasClearHistoryDialogVisible = true
                withFrameNanos { }
                clearHistoryDialogCancelRequester.requestFocusSafely()
            }

            wasClearHistoryDialogVisible -> {
                // Clearing the final history item removes this requester from composition.
                // Let the empty state recover focus through the drawer instead of asking a
                // detached target for focus on the next frame.
                if (!focusDrawerAfterClearingHistory) {
                    withFrameNanos { }
                    clearHistoryFocusRequester.requestFocusSafely()
                }
                wasClearHistoryDialogVisible = false
            }
        }
    }

    LaunchedEffect(state.isLoading, state.items.isEmpty(), focusDrawerAfterClearingHistory) {
        if (
            focusDrawerAfterClearingHistory &&
            !state.isLoading &&
            state.items.isEmpty()
        ) {
            // There is no remaining history card to own focus. Open the existing navigation
            // shell, whose selected item is a stable fallback focus target.
            isDrawerExpanded = true
            focusDrawerAfterClearingHistory = false
        }
    }

    IdealPlayerDrawer(
        isExpanded = isDrawerExpanded,
        selectedRoute = Routes.CONTINUE_WATCHING,
        isTv = true,
        onToggle = { isDrawerExpanded = !isDrawerExpanded },
        onNavigate = onNavigate
    ) {
        when {
            state.isLoading -> LoadingScreen()
            state.items.isEmpty() -> EmptyScreen(message = stringResource(R.string.no_content))
            else -> TvContinueWatchingContent(
                state = state,
                onResume = onResume,
                onOpenDetail = onOpenDetail,
                onClearHistory = { showClearHistoryDialog = true },
                clearHistoryFocusRequester = clearHistoryFocusRequester
            )
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.action_clear_watch_history)) },
            text = {
                Text(
                    text = stringResource(R.string.clear_watch_history_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IdealPlayerColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryDialog = false
                        focusDrawerAfterClearingHistory = true
                        viewModel.clearWatchHistory()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = IdealPlayerColors.Error)
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearHistoryDialog = false },
                    modifier = Modifier.focusRequester(clearHistoryDialogCancelRequester)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = IdealPlayerColors.Surface,
            titleContentColor = IdealPlayerColors.TextPrimary,
            textContentColor = IdealPlayerColors.TextSecondary
        )
    }
}

@Composable
private fun TvContinueWatchingContent(
    state: TvContinueWatchingState,
    onResume: (WatchHistoryItem) -> Unit,
    onOpenDetail: (Long, String) -> Unit,
    onClearHistory: () -> Unit,
    clearHistoryFocusRequester: FocusRequester
) {
    val dimens = LocalIdealPlayerDimens.current
    val featured = state.items.firstOrNull()
    val movies = state.items.filter { it.contentType == ContentType.MOVIE }
    val series = state.items.filter { it.contentType == ContentType.SERIES }
    val live = state.items.filter { it.contentType == ContentType.LIVE }
    val resumeFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    LaunchedEffect(featured?.id) {
        if (featured != null && !hasRequestedInitialFocus) {
            withFrameNanos { }
            resumeFocusRequester.requestFocusSafely()
            hasRequestedInitialFocus = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = dimens.screenPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.nav_continue_watching),
                style = MaterialTheme.typography.headlineMedium,
                color = IdealPlayerColors.TextPrimary
            )
            A2ActionButton(
                text = stringResource(R.string.action_clear_watch_history),
                icon = Icons.Filled.Delete,
                variant = A2ActionVariant.Secondary,
                modifier = Modifier.focusRequester(clearHistoryFocusRequester),
                onClick = onClearHistory
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        if (featured != null) {
            TvContinueWatchingHero(
                item = featured,
                onResume = { onResume(featured) },
                primaryFocusRequester = resumeFocusRequester,
                onOpenDetail = {
                    if (featured.contentType != ContentType.LIVE) {
                        onOpenDetail(featured.contentId, featured.contentType.name)
                    } else {
                        onResume(featured)
                    }
                }
            )
            Spacer(modifier = Modifier.height(28.dp))
        }

        TvHistoryRail(
            title = stringResource(R.string.nav_series),
            items = series,
            onResume = onResume
        )
        TvHistoryRail(
            title = stringResource(R.string.nav_movies),
            items = movies,
            onResume = onResume
        )
        TvHistoryRail(
            title = stringResource(R.string.nav_live_tv),
            items = live,
            onResume = onResume
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TvContinueWatchingHero(
    item: WatchHistoryItem,
    onResume: () -> Unit,
    onOpenDetail: () -> Unit,
    primaryFocusRequester: FocusRequester
) {
    val dimens = LocalIdealPlayerDimens.current
    val title = item.seriesName.ifBlank { item.title }
    val subtitle = continueWatchingSubtitle(item)
    val progress = item.progress.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .padding(horizontal = dimens.screenPadding)
            .clip(RoundedCornerShape(28.dp))
            .background(IdealPlayerColors.Surface)
    ) {
        PosterImage(
            url = item.posterUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            IdealPlayerColors.Background.copy(alpha = 0.94f),
                            IdealPlayerColors.Background.copy(alpha = 0.62f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 40.dp, vertical = 32.dp)
                .width(500.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = IdealPlayerColors.Primary.copy(alpha = 0.16f)
            ) {
                Text(
                    text = stringResource(R.string.section_continue_watching),
                    style = MaterialTheme.typography.labelLarge,
                    color = IdealPlayerColors.Primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = IdealPlayerColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = IdealPlayerColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = continueWatchingProgressLabel(item),
                style = MaterialTheme.typography.bodyLarge,
                color = IdealPlayerColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(IdealPlayerColors.SurfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxSize()
                        .background(IdealPlayerColors.Primary)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                A2ActionButton(
                    text = stringResource(R.string.action_resume),
                    icon = Icons.Filled.PlayArrow,
                    onClick = onResume,
                    modifier = Modifier.focusRequester(primaryFocusRequester)
                )
                if (item.contentType != ContentType.LIVE) {
                    A2ActionButton(
                        text = stringResource(R.string.action_details),
                        onClick = onOpenDetail,
                        variant = A2ActionVariant.Secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun TvHistoryRail(
    title: String,
    items: List<WatchHistoryItem>,
    onResume: (WatchHistoryItem) -> Unit
) {
    if (items.isEmpty()) return

    val dimens = LocalIdealPlayerDimens.current

    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = IdealPlayerColors.TextPrimary,
        modifier = Modifier.padding(horizontal = dimens.screenPadding)
    )
    Spacer(modifier = Modifier.height(12.dp))
    LazyRow(
        modifier = Modifier.focusGroup(),
        contentPadding = PaddingValues(horizontal = dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
    ) {
        items(items, key = { "${it.contentType.name}-${it.id}" }) { item ->
            TvHistoryCard(
                item = item,
                onResume = { onResume(item) }
            )
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun TvHistoryCard(
    item: WatchHistoryItem,
    onResume: () -> Unit
) {
    val progress = item.progress.coerceIn(0f, 1f)
    val title = item.seriesName.ifBlank { item.title }
    val badge = when (item.contentType) {
        ContentType.LIVE -> stringResource(R.string.live_tv)
        ContentType.MOVIE -> stringResource(R.string.movies)
        ContentType.SERIES -> stringResource(R.string.series)
    }

    A2ContentCard(
        kind = A2ContentCardKind.ContinueWatching,
        title = title,
        subtitle = continueWatchingSubtitle(item).takeIf { it.isNotBlank() },
        metadata = continueWatchingProgressLabel(item).takeIf { it.isNotBlank() },
        progress = progress,
        badgeText = badge,
        badgeTone = A2BadgeTone.Primary,
        stateDescription = continueWatchingProgressLabel(item).takeIf { it.isNotBlank() },
        onClick = onResume,
        modifier = Modifier
            .width(272.dp)
            .height(192.dp),
        artwork = {
            PosterImage(
                url = item.posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    )
}

@Composable
fun TvFavoritesScreen(
    onNavigate: (String) -> Unit,
    onMovieClick: (Long) -> Unit,
    onSeriesClick: (Long) -> Unit,
    onPlayChannel: (String, String, Long, String?) -> Unit,
    viewModel: TvFavoritesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isDrawerExpanded by remember { mutableStateOf(false) }

    IdealPlayerDrawer(
        isExpanded = isDrawerExpanded,
        selectedRoute = Routes.FAVORITES,
        isTv = true,
        onToggle = { isDrawerExpanded = !isDrawerExpanded },
        onNavigate = onNavigate
    ) {
        when {
            state.isLoading -> LoadingScreen()
            state.movies.isEmpty() && state.series.isEmpty() && state.channels.isEmpty() -> {
                EmptyScreen(message = stringResource(R.string.no_content))
            }

            else -> TvFavoritesContent(
                state = state,
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick,
                onPlayChannel = onPlayChannel
            )
        }
    }
}

@Composable
private fun TvFavoritesContent(
    state: TvFavoritesState,
    onMovieClick: (Long) -> Unit,
    onSeriesClick: (Long) -> Unit,
    onPlayChannel: (String, String, Long, String?) -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val initialFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    val initialFocusKey = remember(state.movies, state.series, state.channels) {
        when {
            state.movies.isNotEmpty() -> "movie-${state.movies.first().id}"
            state.series.isNotEmpty() -> "series-${state.series.first().id}"
            state.channels.isNotEmpty() -> "channel-${state.channels.first().id}"
            else -> null
        }
    }

    LaunchedEffect(initialFocusKey) {
        if (initialFocusKey != null && !hasRequestedInitialFocus) {
            withFrameNanos { }
            initialFocusRequester.requestFocusSafely()
            hasRequestedInitialFocus = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = dimens.screenPadding)
    ) {
        TvFavoritesHeader(state = state)
        Spacer(modifier = Modifier.height(28.dp))

        if (state.movies.isNotEmpty()) {
            TvMovieRail(
                title = stringResource(R.string.nav_movies),
                movies = state.movies,
                onMovieClick = onMovieClick,
                firstItemFocusRequester = if (initialFocusKey == "movie-${state.movies.first().id}") {
                    initialFocusRequester
                } else {
                    null
                }
            )
        }

        if (state.series.isNotEmpty()) {
            TvSeriesRail(
                title = stringResource(R.string.nav_series),
                series = state.series,
                onSeriesClick = onSeriesClick,
                firstItemFocusRequester = if (initialFocusKey == "series-${state.series.first().id}") {
                    initialFocusRequester
                } else {
                    null
                }
            )
        }

        if (state.channels.isNotEmpty()) {
            Text(
                text = stringResource(R.string.nav_live_tv),
                style = MaterialTheme.typography.headlineMedium,
                color = IdealPlayerColors.TextPrimary,
                modifier = Modifier.padding(horizontal = dimens.screenPadding)
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                modifier = Modifier.focusGroup(),
                contentPadding = PaddingValues(horizontal = dimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
            ) {
                items(state.channels, key = { it.id }) { channel ->
                    TvFavoriteChannelCard(
                        channel = channel,
                        modifier = if (initialFocusKey == "channel-${channel.id}") {
                            Modifier.focusRequester(initialFocusRequester)
                        } else {
                            Modifier
                        },
                        onClick = {
                            onPlayChannel(
                                channel.streamUrl,
                                channel.name,
                                channel.id,
                                channel.groupTitle
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TvFavoritesHeader(
    state: TvFavoritesState
) {
    val dimens = LocalIdealPlayerDimens.current
    val total = state.movies.size + state.series.size + state.channels.size
    val moviesLabel = stringResource(R.string.nav_movies)
    val seriesLabel = stringResource(R.string.nav_series)
    val liveLabel = stringResource(R.string.nav_live_tv)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenPadding)
    ) {
        Text(
            text = stringResource(R.string.favorites),
            style = MaterialTheme.typography.headlineLarge,
            color = IdealPlayerColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "$total • $moviesLabel ${state.movies.size} • " +
                "$seriesLabel ${state.series.size} • $liveLabel ${state.channels.size}",
            style = MaterialTheme.typography.bodyLarge,
            color = IdealPlayerColors.TextSecondary
        )
    }
}

@Composable
private fun TvMovieRail(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Long) -> Unit,
    firstItemFocusRequester: FocusRequester? = null
) {
    val dimens = LocalIdealPlayerDimens.current

    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = IdealPlayerColors.TextPrimary,
        modifier = Modifier.padding(horizontal = dimens.screenPadding)
    )
    Spacer(modifier = Modifier.height(12.dp))
    LazyRow(
        modifier = Modifier.focusGroup(),
        contentPadding = PaddingValues(horizontal = dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
    ) {
        items(movies, key = { it.id }) { movie ->
            val metadata = remember(movie.year, movie.rating) {
                favoriteMetadata(movie.year, movie.rating)
            }
            A2ContentCard(
                kind = A2ContentCardKind.Landscape,
                title = movie.name,
                metadata = metadata,
                badgeText = title,
                badgeTone = A2BadgeTone.Primary,
                modifier = if (movie.id == movies.firstOrNull()?.id && firstItemFocusRequester != null) {
                    Modifier
                        .focusRequester(firstItemFocusRequester)
                        .width(272.dp)
                        .height(192.dp)
                } else {
                    Modifier
                        .width(272.dp)
                        .height(192.dp)
                },
                onClick = { onMovieClick(movie.id) },
                artwork = {
                    PosterImage(
                        url = movie.posterUrl,
                        contentDescription = movie.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            )
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun TvSeriesRail(
    title: String,
    series: List<Series>,
    onSeriesClick: (Long) -> Unit,
    firstItemFocusRequester: FocusRequester? = null
) {
    val dimens = LocalIdealPlayerDimens.current

    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = IdealPlayerColors.TextPrimary,
        modifier = Modifier.padding(horizontal = dimens.screenPadding)
    )
    Spacer(modifier = Modifier.height(12.dp))
    LazyRow(
        modifier = Modifier.focusGroup(),
        contentPadding = PaddingValues(horizontal = dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
    ) {
        items(series, key = { it.id }) { item ->
            val metadata = remember(item.year, item.rating) {
                favoriteMetadata(item.year, item.rating)
            }
            A2ContentCard(
                kind = A2ContentCardKind.Landscape,
                title = item.name,
                metadata = metadata,
                badgeText = title,
                badgeTone = A2BadgeTone.Primary,
                modifier = if (item.id == series.firstOrNull()?.id && firstItemFocusRequester != null) {
                    Modifier
                        .focusRequester(firstItemFocusRequester)
                        .width(272.dp)
                        .height(192.dp)
                } else {
                    Modifier
                        .width(272.dp)
                        .height(192.dp)
                },
                onClick = { onSeriesClick(item.id) },
                artwork = {
                    PosterImage(
                        url = item.posterUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            )
        }
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun TvFavoriteChannelCard(
    channel: Channel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    A2ContentCard(
        kind = A2ContentCardKind.Landscape,
        title = channel.name,
        subtitle = channel.groupTitle.takeIf { it.isNotBlank() },
        badgeText = stringResource(R.string.live_tv),
        badgeTone = A2BadgeTone.Primary,
        contentDescription = channel.name,
        onClick = onClick,
        modifier = modifier
            .width(272.dp)
            .height(192.dp),
        artwork = {
            PosterImage(
                url = channel.logoUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                fallbackStyle = ArtworkFallbackStyle.Channel,
                modifier = Modifier
                    .matchParentSize()
                    .padding(16.dp)
            )
        }
    )
}

private fun continueWatchingSubtitle(item: WatchHistoryItem): String {
    return when {
        item.contentType == ContentType.SERIES && item.seasonNumber > 0 -> {
            val episodeLabel = "S${item.seasonNumber}:E${item.episodeNumber}"
            if (item.title.isNotBlank() && item.title != item.seriesName) {
                "$episodeLabel  •  ${item.title}"
            } else {
                episodeLabel
            }
        }

        item.title.isNotBlank() -> item.title
        else -> ""
    }
}

private fun continueWatchingProgressLabel(item: WatchHistoryItem): String {
    return if (item.totalDuration > 0L) {
        "${StringUtils.formatDuration(item.position)} / ${StringUtils.formatDuration(item.totalDuration)}"
    } else if (item.position > 0L) {
        StringUtils.formatDuration(item.position)
    } else {
        ""
    }
}

private fun favoriteMetadata(year: Int, rating: Double): String? = buildList {
    if (year > 0) add(year.toString())
    if (rating > 0.0) add(String.format(java.util.Locale.US, "%.1f", rating))
}.joinToString(" • ").takeIf { it.isNotBlank() }

private fun FocusRequester.requestFocusSafely() {
    runCatching { requestFocus() }
}
