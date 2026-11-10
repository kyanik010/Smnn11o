package com.idealplayer.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import com.idealplayer.app.R
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idealplayer.app.core.common.StringUtils
import com.idealplayer.app.core.designsystem.theme.A2Motion
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.core.model.*
import com.idealplayer.app.ui.components.*
import com.idealplayer.app.ui.components.a2.A2ContentCard
import com.idealplayer.app.ui.components.a2.A2ContentCardKind
import com.idealplayer.app.ui.components.a2.A2BadgeTone
import com.idealplayer.app.ui.components.a2.A2ActionButton
import com.idealplayer.app.ui.components.a2.A2ActionVariant
import com.idealplayer.app.ui.navigation.Routes

@Composable
fun HomeScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String, Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (isTv) {
        IdealPlayerDrawer(
            isExpanded = false,
            selectedRoute = Routes.HOME,
            isTv = true,
            onToggle = {},
            onNavigate = onNavigate
        ) {
            TvHomeContent(
                state = state,
                isLoading = state.isLoading,
                onNavigate = onNavigate,
                onContentClick = onContentClick,
                onPlayContent = onPlayContent,
                onMoviePosterError = viewModel::repairMovieArtwork
            ) { viewModel.selectContent(it) }
        }
    } else {
        if (state.isLoading) {
            LoadingScreen()
        } else {
            MobileHomeContent(
                state = state,
                onNavigate = onNavigate,
                onContentClick = onContentClick,
                onPlayContent = onPlayContent,
                onMoviePosterError = viewModel::repairMovieArtwork
            ) { viewModel.selectContent(it) }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TV Home — rail-based landscape layout
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun TvHomeContent(
    state: HomeState,
    isLoading: Boolean,
    onNavigate: (String) -> Unit,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String, Long) -> Unit,
    onMoviePosterError: (Long) -> Unit,
    onFocusChange: (Any?) -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val latestMovies = remember(state.latestMovies, state.allMovies) {
        state.latestMovies
            .ifEmpty { state.allMovies.asReversed() }
            .take(18)
    }
    val latestSeries = remember(state.latestSeries, state.allSeries) {
        state.latestSeries
            .ifEmpty { state.allSeries.asReversed() }
            .take(18)
    }
    val favoriteMovies = remember(state.favoriteMovies) { state.favoriteMovies.take(18) }
    val recentChannels = remember(state.recentChannels) { state.recentChannels.take(16) }
    val initialContentFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialContentFocus by remember { mutableStateOf(false) }
    val initialContentFocusKey = remember(
        state.continueWatching,
        latestMovies,
        latestSeries,
        recentChannels,
        favoriteMovies
    ) {
        when {
            latestMovies.isNotEmpty() -> "hero"
            state.continueWatching.isNotEmpty() -> {
                val item = state.continueWatching.first()
                "continue-${item.contentType.name}-${item.id}"
            }

            latestSeries.isNotEmpty() -> "series-${latestSeries.first().id}"
            recentChannels.isNotEmpty() -> "channel-${recentChannels.first().id}"
            favoriteMovies.isNotEmpty() -> "favorite-${favoriteMovies.first().id}"
            else -> null
        }
    }
    val hasVisibleContent = remember(
        state.continueWatching,
        latestMovies,
        latestSeries,
        recentChannels,
        favoriteMovies
    ) {
        state.continueWatching.isNotEmpty() ||
            latestMovies.isNotEmpty() ||
            latestSeries.isNotEmpty() ||
            recentChannels.isNotEmpty() ||
            favoriteMovies.isNotEmpty()
    }
    // The collapsed drawer deliberately does not take entry focus. Give each populated TV
    // home state one stable content target, but never re-request it for later flow updates.
    LaunchedEffect(initialContentFocusKey) {
        if (initialContentFocusKey != null && !hasRequestedInitialContentFocus) {
            repeat(6) {
                withFrameNanos { }
                val focused = runCatching { initialContentFocusRequester.requestFocus() }.isSuccess
                if (focused) {
                    hasRequestedInitialContentFocus = true
                    return@LaunchedEffect
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(IdealPlayerColors.Background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading && !hasVisibleContent) {
                item(key = "tv-home-loading") {
                    TvHomeLoadingState()
                }
            }

            if (!isLoading || hasVisibleContent) {
                item(key = "tv-home-featured") {
                    TvHeroBanner(
                        state = state,
                        initialFocusRequester = if (initialContentFocusKey == "hero") {
                            initialContentFocusRequester
                        } else {
                            null
                        },
                        onSearch = { onNavigate(Routes.SEARCH) },
                        onPlay = onPlayContent,
                        onDetail = onContentClick
                    )
                }
            }

            if (state.continueWatching.isNotEmpty()) {
                item(key = "tv-home-continue-watching") {
                    ContentRail(stringResource(R.string.section_continue_watching), dimens.screenPadding) {
                        items(
                            items = state.continueWatching,
                            key = { "${it.contentType.name}-${it.id}" }
                        ) { item ->
                            ContinueWatchingCard(
                                item = item,
                                isTv = true,
                                modifier = if (initialContentFocusKey == "continue-${item.contentType.name}-${item.id}") {
                                    Modifier.focusRequester(initialContentFocusRequester)
                                } else {
                                    Modifier
                                },
                                onClick = { onPlayContent(item.streamUrl, item.title, item.contentId, item.contentType.name, item.position) },
                                onFocus = { onFocusChange(item) })
                        }
                    }
                }
            }
            if (latestMovies.isNotEmpty()) {
                item(key = "tv-home-movies") {
                    ContentRail(stringResource(R.string.section_latest_movies), dimens.screenPadding) {
                        items(latestMovies, key = { it.id }) { movie ->
                            HomeLandscapeMediaCard(
                                title = movie.name,
                                artworkUrl = movie.backdropUrl.ifBlank { movie.posterUrl },
                                year = movie.year,
                                genre = movie.genre,
                                isSeries = false,
                                isTv = true,
                                modifier = if (initialContentFocusKey == "movie-${movie.id}") {
                                    Modifier.focusRequester(initialContentFocusRequester)
                                } else {
                                    Modifier
                                },
                                onArtworkError = { onMoviePosterError(movie.id) },
                                onClick = { onContentClick(movie.id, "MOVIE") })
                        }
                    }
                }
            }
            if (latestSeries.isNotEmpty()) {
                item(key = "tv-home-series") {
                    ContentRail(stringResource(R.string.section_latest_series), dimens.screenPadding) {
                        items(latestSeries, key = { it.id }) { series ->
                            HomeLandscapeMediaCard(
                                title = series.name,
                                artworkUrl = series.backdropUrl.ifBlank { series.posterUrl },
                                year = series.year,
                                genre = series.genre,
                                isSeries = true,
                                isTv = true,
                                modifier = if (initialContentFocusKey == "series-${series.id}") {
                                    Modifier.focusRequester(initialContentFocusRequester)
                                } else {
                                    Modifier
                                },
                                onClick = { onContentClick(series.id, "SERIES") })
                        }
                    }
                }
            }
            if (recentChannels.isNotEmpty()) {
                item(key = "tv-home-recent-channels") {
                    ContentRail(stringResource(R.string.section_recent_channels), dimens.screenPadding) {
                        items(recentChannels, key = { it.id }) { ch ->
                            ChannelCard(
                                channel = ch,
                                isTv = true,
                                modifier = if (initialContentFocusKey == "channel-${ch.id}") {
                                    Modifier.focusRequester(initialContentFocusRequester)
                                } else {
                                    Modifier
                                },
                                onClick = { onPlayContent(ch.streamUrl, ch.name, ch.id, "LIVE", 0) },
                                onFocus = { onFocusChange(ch) })
                        }
                    }
                }
            }
            if (favoriteMovies.isNotEmpty()) {
                item(key = "tv-home-favorites") {
                    ContentRail(stringResource(R.string.section_favorites), dimens.screenPadding) {
                        items(favoriteMovies, key = { it.id }) { movie ->
                            HomeLandscapeMediaCard(
                                title = movie.name,
                                artworkUrl = movie.backdropUrl.ifBlank { movie.posterUrl },
                                year = movie.year,
                                genre = movie.genre,
                                isSeries = false,
                                isTv = true,
                                modifier = if (initialContentFocusKey == "favorite-${movie.id}") {
                                    Modifier.focusRequester(initialContentFocusRequester)
                                } else {
                                    Modifier
                                },
                                onArtworkError = { onMoviePosterError(movie.id) },
                                onClick = { onContentClick(movie.id, "MOVIE") })
                        }
                    }
                }
            }
            item(key = "tv-home-footer-space") {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun TvHomeLoadingState() {
    val dimens = LocalIdealPlayerDimens.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenPadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = stringResource(R.string.nav_home),
            style = MaterialTheme.typography.headlineMedium,
            color = IdealPlayerColors.TextPrimary
        )
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = IdealPlayerColors.Primary,
            trackColor = IdealPlayerColors.SurfaceVariant
        )
        repeat(2) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(22.dp))
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Mobile Home — portrait-first vertical layout
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun MobileHomeContent(
    state: HomeState,
    onNavigate: (String) -> Unit,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String, Long) -> Unit,
    onMoviePosterError: (Long) -> Unit,
    onFocusChange: (Any?) -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val config = LocalConfiguration.current
    val isTablet = config.smallestScreenWidthDp >= 600
    val isLandscape = config.screenWidthDp > config.screenHeightDp
    val latestMovies = remember(state.latestMovies, state.allMovies) {
        state.latestMovies.ifEmpty { state.allMovies.asReversed() }
    }
    val latestSeries = remember(state.latestSeries, state.allSeries) {
        state.latestSeries.ifEmpty { state.allSeries.asReversed() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdealPlayerColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = dimens.contentPadding)
    ) {
        MobileHomeTopBar(
            isTablet = isTablet,
            onSearch = { onNavigate(Routes.SEARCH) },
            onProfile = { onNavigate(Routes.SETTINGS) }
        )

        AdaptiveHomeHero(
            state = state,
            isTablet = isTablet,
            isLandscape = isLandscape,
            onPlay = onPlayContent,
            onDetail = onContentClick
        )
        Spacer(modifier = Modifier.height(dimens.sectionSpacing))

        if (state.continueWatching.isNotEmpty()) {
            ContentRail(stringResource(R.string.section_continue_watching), dimens.screenPadding) {
                items(
                    items = state.continueWatching,
                    key = { "${it.contentType.name}-${it.id}" }
                ) { item ->
                    ContinueWatchingCard(
                        item = item,
                        isTv = false,
                        onClick = {
                            onPlayContent(
                                item.streamUrl,
                                item.title,
                                item.contentId,
                                item.contentType.name,
                                item.position
                            )
                        },
                        onFocus = { onFocusChange(item) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(dimens.sectionSpacing))
        }

        if (state.recentChannels.isNotEmpty()) {
            ContentRail(stringResource(R.string.section_recent_channels), dimens.screenPadding) {
                items(state.recentChannels, key = Channel::id) { channel ->
                    ChannelCard(
                        channel = channel,
                        isTv = false,
                        onClick = {
                            onPlayContent(channel.streamUrl, channel.name, channel.id, "LIVE", 0)
                        },
                        onFocus = { onFocusChange(channel) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(dimens.sectionSpacing))
        }

        HomeBrowseShortcuts(
            onMovies = { onNavigate(Routes.MOVIES) },
            onSeries = { onNavigate(Routes.SERIES) },
            onFavorites = { onNavigate(Routes.FAVORITES) }
        )
        Spacer(modifier = Modifier.height(dimens.sectionSpacing))

        if (latestMovies.isNotEmpty()) {
            ContentRail(stringResource(R.string.section_latest_movies), dimens.screenPadding) {
                items(latestMovies, key = Movie::id) { movie ->
                    HomeLandscapeMediaCard(
                        title = movie.name,
                        artworkUrl = movie.backdropUrl.ifBlank { movie.posterUrl },
                        year = movie.year,
                        genre = movie.genre,
                        isSeries = false,
                        isTv = false,
                        onArtworkError = { onMoviePosterError(movie.id) },
                        onClick = { onContentClick(movie.id, "MOVIE") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(dimens.sectionSpacing))
        }
        if (state.favoriteMovies.isNotEmpty()) {
            ContentRail(stringResource(R.string.section_favorites), dimens.screenPadding) {
                items(state.favoriteMovies, key = Movie::id) { movie ->
                    HomeLandscapeMediaCard(
                        title = movie.name,
                        artworkUrl = movie.backdropUrl.ifBlank { movie.posterUrl },
                        year = movie.year,
                        genre = movie.genre,
                        isSeries = false,
                        isTv = false,
                        onArtworkError = { onMoviePosterError(movie.id) },
                        onClick = { onContentClick(movie.id, "MOVIE") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(dimens.sectionSpacing))
        }
        if (latestSeries.isNotEmpty()) {
            ContentRail(stringResource(R.string.section_latest_series), dimens.screenPadding) {
                items(latestSeries, key = Series::id) { series ->
                    HomeLandscapeMediaCard(
                        title = series.name,
                        artworkUrl = series.backdropUrl.ifBlank { series.posterUrl },
                        year = series.year,
                        genre = series.genre,
                        isSeries = true,
                        isTv = false,
                        onClick = { onContentClick(series.id, "SERIES") }
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileHomeTopBar(
    isTablet: Boolean,
    onSearch: () -> Unit,
    onProfile: () -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = dimens.screenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.size(if (isTablet) 56.dp else 48.dp)
        )
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onSearch,
            modifier = Modifier
                .width(if (isTablet) 360.dp else 128.dp)
                .height(48.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = IdealPlayerColors.TextSecondary),
            border = BorderStroke(1.dp, IdealPlayerColors.CardBorder),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isTablet) stringResource(R.string.search_hint) else stringResource(R.string.search),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            onClick = onProfile,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(22.dp),
            color = IdealPlayerColors.SurfaceElevated,
            border = BorderStroke(2.dp, IdealPlayerColors.SelectedBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.AccountCircle,
                    contentDescription = stringResource(R.string.settings),
                    tint = IdealPlayerColors.TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeBrowseShortcuts(
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onFavorites: () -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    SectionHeader(
        title = stringResource(R.string.categories),
        modifier = Modifier.padding(horizontal = dimens.screenPadding)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HomeBrowseShortcut(
            title = stringResource(R.string.movies),
            icon = Icons.Filled.Movie,
            onClick = onMovies,
            modifier = Modifier.weight(1f)
        )
        HomeBrowseShortcut(
            title = stringResource(R.string.series),
            icon = Icons.Filled.Tv,
            onClick = onSeries,
            modifier = Modifier.weight(1f),
            selected = true
        )
        HomeBrowseShortcut(
            title = stringResource(R.string.favorites),
            icon = Icons.Filled.Favorite,
            onClick = onFavorites,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HomeBrowseShortcut(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) IdealPlayerColors.SurfaceSelected else IdealPlayerColors.CardBackground,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) IdealPlayerColors.SelectedBorder else IdealPlayerColors.CardBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) IdealPlayerColors.Secondary else IdealPlayerColors.Primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = IdealPlayerColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TV Hero Banner — horizontal Row layout (landscape-first)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun TvHeroBanner(
    state: HomeState,
    initialFocusRequester: FocusRequester?,
    onSearch: () -> Unit,
    onPlay: (String, String, Long, String, Long) -> Unit,
    onDetail: (Long, String) -> Unit
) {
    val featured = state.latestMovies.firstOrNull() ?: state.allMovies.firstOrNull() ?: return
    val dimens = LocalIdealPlayerDimens.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(284.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(IdealPlayerColors.CardBackground)
        ) {
            PosterImage(
                url = featured.backdropUrl.ifBlank { featured.posterUrl },
                contentDescription = featured.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to IdealPlayerColors.Background.copy(alpha = 0.98f),
                            0.58f to IdealPlayerColors.Background.copy(alpha = 0.78f),
                            1f to Color.Transparent
                        )
                    )
            )
            HeroCopy(
                featured = featured,
                initialFocusRequester = initialFocusRequester,
                onPlay = onPlay,
                onDetail = onDetail,
                isTv = true,
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .padding(start = dimens.screenPadding, top = 28.dp, bottom = 14.dp)
            )

            A2ActionButton(
                text = stringResource(R.string.nav_search),
                onClick = onSearch,
                icon = Icons.Filled.Search,
                variant = A2ActionVariant.Secondary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 28.dp, end = 160.dp)
                    .width(292.dp)
            )
        }
    }
}

@Composable
private fun AdaptiveHomeHero(
    state: HomeState,
    isTablet: Boolean,
    isLandscape: Boolean,
    onPlay: (String, String, Long, String, Long) -> Unit,
    onDetail: (Long, String) -> Unit
) {
    val featured = state.latestMovies.firstOrNull() ?: state.allMovies.firstOrNull() ?: return
    val dimens = LocalIdealPlayerDimens.current
    val expandedLayout = isTablet || isLandscape

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenPadding)
            .height(if (isTablet) 288.dp else if (expandedLayout) 232.dp else 196.dp),
        shape = RoundedCornerShape(16.dp),
        color = IdealPlayerColors.CardBackground,
        border = BorderStroke(1.dp, IdealPlayerColors.CardBorder)
    ) {
        if (expandedLayout) {
            Row(modifier = Modifier.fillMaxSize()) {
                HeroCopy(
                    featured = featured,
                    onPlay = onPlay,
                    onDetail = onDetail,
                    isTv = false,
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        .padding(if (isTablet) 24.dp else 16.dp)
                )
                Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
                    PosterImage(
                        url = featured.backdropUrl.ifBlank { featured.posterUrl },
                        contentDescription = featured.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(IdealPlayerColors.CardBackground, Color.Transparent)
                                )
                            )
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                PosterImage(
                    url = featured.backdropUrl.ifBlank { featured.posterUrl },
                    contentDescription = featured.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to IdealPlayerColors.Background.copy(alpha = 0.96f),
                                0.7f to IdealPlayerColors.Background.copy(alpha = 0.46f),
                                1f to Color.Transparent
                            )
                        )
                )
                HeroCopy(
                    featured = featured,
                    onPlay = onPlay,
                    onDetail = onDetail,
                    isTv = false,
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.76f)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun HeroCopy(
    featured: Movie,
    onPlay: (String, String, Long, String, Long) -> Unit,
    onDetail: (Long, String) -> Unit,
    isTv: Boolean,
    initialFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = IdealPlayerColors.Primary,
            shape = RoundedCornerShape(if (isTv) 10.dp else 8.dp)
        ) {
            Text(
                text = stringResource(R.string.a2_featured),
                style = if (isTv) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                color = IdealPlayerColors.TextOnPrimary,
                modifier = Modifier.padding(
                    horizontal = if (isTv) 12.dp else 8.dp,
                    vertical = if (isTv) 6.dp else 3.dp
                )
            )
        }
        Spacer(modifier = Modifier.height(if (isTv) 8.dp else 4.dp))
        Text(
            text = featured.name,
            style = if (isTv) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
            color = IdealPlayerColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (featured.genre.isNotBlank()) {
                Text(
                    featured.genre.take(30),
                    style = MaterialTheme.typography.bodySmall,
                    color = IdealPlayerColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (featured.year > 0) {
                Text(
                    featured.year.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IdealPlayerColors.TextSecondary
                )
            }
        }
        if (featured.plot.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                featured.plot,
                style = MaterialTheme.typography.bodySmall,
                color = IdealPlayerColors.TextSecondary,
                maxLines = if (isTv) 2 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(if (isTv) 16.dp else 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton(
                text = stringResource(R.string.action_play),
                icon = Icons.Filled.PlayArrow,
                isTv = isTv,
                focusRequester = initialFocusRequester,
                onClick = {
                    onPlay(featured.streamUrl, featured.name, featured.id, "MOVIE", featured.lastPosition)
                }
            )
            IdealPlayerOutlinedButton(
                text = stringResource(R.string.action_details),
                icon = Icons.Filled.Info,
                isTv = isTv,
                onClick = { onDetail(featured.id, "MOVIE") }
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Shared composables
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun HomeLandscapeMediaCard(
    title: String,
    artworkUrl: String,
    year: Int,
    genre: String,
    isSeries: Boolean,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onArtworkError: () -> Unit = {},
    onClick: () -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val width = when {
        isTv -> 272.dp
        dimens.cardWidth >= 200.dp -> 220.dp
        else -> 160.dp
    }
    A2ContentCard(
        kind = A2ContentCardKind.Landscape,
        title = title,
        subtitle = genre.takeIf(String::isNotBlank),
        metadata = year.takeIf { it > 0 }?.toString(),
        badgeText = stringResource(
            if (isSeries) R.string.player_content_series else R.string.player_content_movie
        ),
        modifier = modifier.width(width),
        onClick = onClick,
        artworkContentDescription = title,
        artwork = {
            PosterImage(
                url = artworkUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                onError = onArtworkError,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}

@Composable
private fun ContentRail(
    title: String,
    horizontalPad: androidx.compose.ui.unit.Dp,
    itemContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    SectionHeader(title = title, modifier = Modifier.padding(horizontal = horizontalPad))
    LazyRow(
        modifier = Modifier.focusGroup(),
        contentPadding = PaddingValues(horizontal = horizontalPad),
        horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing),
        content = { itemContent() }
    )
}

@Composable
private fun ContinueWatchingCard(
    item: WatchHistoryItem,
    isTv: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalIdealPlayerDimens.current
    val interactionSource = remember(item.id, item.contentId, item.contentType) { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused, item.id, item.contentId, item.contentType) {
        if (isFocused) onFocus()
    }
    val width = when {
        isTv -> 272.dp
        dimens.cardWidth >= 200.dp -> 220.dp
        else -> 160.dp
    }
    val title = remember(item) { item.seriesName.ifBlank { item.title } }
    val subtitle = remember(item) { continueWatchingSubtitle(item) }
    val progressLabel = remember(item) { continueWatchingProgressLabel(item) }
    val progress = item.progress.coerceIn(0f, 1f)
    
    val tvFocusState = if (isTv) {
        rememberTvFocusVisualState(
            isFocused = isFocused,
            defaultSurface = IdealPlayerColors.CardBackground,
            selectedSurface = IdealPlayerColors.CardBackground,
            focusedSurface = IdealPlayerColors.SurfaceFocus,
            selectedFocusedSurface = IdealPlayerColors.SurfaceFocus
        )
    } else null

    val scale by animateFloatAsState(
        targetValue = tvFocusState?.scale ?: if (isFocused) A2Motion.FocusScale else 1f,
        animationSpec = tween(A2Motion.StandardMillis),
        label = "continueWatchingScale"
    )
    val borderWidth by animateDpAsState(
        targetValue = tvFocusState?.borderWidth ?: if (isFocused) dimens.focusBorderWidth else 0.dp,
        animationSpec = tween(A2Motion.StandardMillis),
        label = "continueWatchingBorder"
    )
    val backgroundColor by animateColorAsState(
        targetValue = tvFocusState?.backgroundColor ?: if (isFocused) IdealPlayerColors.SurfaceVariant else IdealPlayerColors.CardBackground,
        animationSpec = tween(A2Motion.StandardMillis),
        label = "continueWatchingBackground"
    )
    val borderColor = tvFocusState?.borderColor ?: IdealPlayerColors.FocusBorder

    Column(
        modifier = modifier
            .width(width)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(dimens.borderRadius))
            .background(backgroundColor)
            .then(
                if (isTv && isFocused && tvFocusState != null) {
                    Modifier.shadow(
                        elevation = tvFocusState.shadowElevation,
                        shape = RoundedCornerShape(dimens.borderRadius),
                        spotColor = tvFocusState.glowColor
                    )
                } else {
                    Modifier
                }
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(dimens.borderRadius)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = if (isTv) null else LocalIndication.current,
                onClick = onClick
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(if (isTv) 104.dp else 80.dp)) {
            PosterImage(url = item.posterUrl, contentDescription = item.title, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )
            if (item.contentType == ContentType.SERIES && item.seasonNumber > 0) {
                Surface(
                    modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = "S${item.seasonNumber}:E${item.episodeNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Surface(
                modifier = Modifier.padding(8.dp).align(Alignment.BottomStart),
                shape = RoundedCornerShape(999.dp),
                color = IdealPlayerColors.Primary.copy(alpha = 0.92f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_resume),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp)
                    .align(Alignment.BottomCenter).background(IdealPlayerColors.SurfaceVariant)
            ) {
                Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(IdealPlayerColors.Primary))
            }
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = IdealPlayerColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = IdealPlayerColors.TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = IdealPlayerColors.TextSecondary
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = IdealPlayerColors.Primary
                )
            }
        }
    }
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

@Composable
private fun ChannelCard(
    channel: Channel,
    isTv: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalIdealPlayerDimens.current

    if (isTv) {
        A2ContentCard(
            kind = A2ContentCardKind.Landscape,
            title = channel.name,
            subtitle = channel.groupTitle.takeIf(String::isNotBlank),
            badgeText = stringResource(R.string.player_content_live),
            badgeTone = A2BadgeTone.Primary,
            onClick = onClick,
            artworkContentDescription = channel.name,
            artwork = {
                PosterImage(
                    url = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    fallbackStyle = ArtworkFallbackStyle.Channel,
                    modifier = Modifier.fillMaxSize().padding(18.dp)
                )
            },
            modifier = modifier
                .width(272.dp)
                .onFocusChanged { if (it.isFocused) onFocus() }
        )
        return
    }

    val interactionSource = remember(channel.id) { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused, channel.id) {
        if (isFocused) onFocus()
    }

    val tvFocusState = if (isTv) {
        rememberTvFocusVisualState(
            isFocused = isFocused,
            defaultSurface = IdealPlayerColors.CardBackground,
            selectedSurface = IdealPlayerColors.CardBackground,
            focusedSurface = IdealPlayerColors.SurfaceFocus,
            selectedFocusedSurface = IdealPlayerColors.SurfaceFocus
        )
    } else null

    val scale by animateFloatAsState(
        targetValue = tvFocusState?.scale ?: if (isFocused) A2Motion.FocusScale else 1f,
        animationSpec = tween(A2Motion.StandardMillis),
        label = "channelScale"
    )
    val borderWidth by animateDpAsState(
        targetValue = tvFocusState?.borderWidth ?: if (isFocused) dimens.focusBorderWidth else 0.dp,
        animationSpec = tween(A2Motion.StandardMillis),
        label = "channelBorder"
    )
    val backgroundColor by animateColorAsState(
        targetValue = tvFocusState?.backgroundColor ?: if (isFocused) IdealPlayerColors.SurfaceVariant else IdealPlayerColors.CardBackground,
        animationSpec = tween(A2Motion.StandardMillis),
        label = "channelBackground"
    )
    val borderColor = tvFocusState?.borderColor ?: IdealPlayerColors.FocusBorder
    val width = when {
        isTv -> 300.dp
        dimens.cardWidth >= 200.dp -> 360.dp
        else -> 328.dp
    }

    Box(
        modifier = modifier
            .width(width)
            .height(112.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(dimens.borderRadius))
                .background(backgroundColor)
                .then(
                    if (isTv && isFocused && tvFocusState != null) {
                        Modifier.shadow(
                            elevation = tvFocusState.shadowElevation,
                            shape = RoundedCornerShape(dimens.borderRadius),
                            ambientColor = tvFocusState.glowColor,
                            spotColor = tvFocusState.glowColor
                        )
                    } else {
                        Modifier
                    }
                )
                .border(
                    width = if (isTv && isFocused) 4.dp else borderWidth,
                    color = if (isTv && isFocused) IdealPlayerColors.Primary else borderColor,
                    shape = RoundedCornerShape(dimens.borderRadius)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = if (isTv) null else LocalIndication.current,
                    onClick = onClick
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .fillMaxHeight()
                    .background(IdealPlayerColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                PosterImage(
                    url = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    fallbackStyle = ArtworkFallbackStyle.Channel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = IdealPlayerColors.Primary
                ) {
                    Text(
                        text = stringResource(R.string.player_content_live),
                        style = MaterialTheme.typography.labelSmall,
                        color = IdealPlayerColors.TextOnPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = channel.name,
                    style = if (isTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    color = IdealPlayerColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.groupTitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = channel.groupTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = IdealPlayerColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(IdealPlayerColors.CardBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.38f)
                            .fillMaxHeight()
                            .background(IdealPlayerColors.Primary)
                    )
                }
            }
        }

        if (isTv && isFocused) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp)
                    .width(6.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(IdealPlayerColors.Primary)
            )
        }
    }
}
