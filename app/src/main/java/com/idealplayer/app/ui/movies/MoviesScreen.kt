package com.idealplayer.app.ui.movies

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.core.model.Movie
import com.idealplayer.app.data.repository.ContentRepository
import com.idealplayer.app.data.repository.PlaylistRepository
import com.idealplayer.app.ui.components.*
import com.idealplayer.app.ui.components.a2.A2ContentCard
import com.idealplayer.app.ui.components.a2.A2ContentCardKind
import com.idealplayer.app.ui.components.a2.A2Badge
import com.idealplayer.app.ui.components.a2.A2BadgeTone
import com.idealplayer.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import androidx.compose.ui.res.stringResource
import com.idealplayer.app.R

data class MoviesState(
    val movies: List<Movie> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(MoviesState())
    val state: StateFlow<MoviesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.getActivePlaylist().collectLatest { playlist ->
                if (playlist != null) {
                    launch(Dispatchers.IO) {
                        contentRepository.backfillMissingMovieArtwork(playlist.id)
                    }
                    launch {
                        contentRepository.getMovieCategories(playlist.id).collect { cats ->
                            _state.update { it.copy(categories = cats) }
                        }
                    }
                    launch {
                        contentRepository.getMovies(playlist.id).collect { movies ->
                            _state.update { it.copy(movies = movies, isLoading = false) }
                        }
                    }
                }
            }
        }
    }

    fun selectCategory(category: String?) {
        _state.update { it.copy(selectedCategory = category) }
    }

    fun repairArtwork(movieId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            contentRepository.repairMovieArtwork(movieId)
        }
    }
}

@Composable
fun MoviesScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onMovieClick: (Long) -> Unit,
    viewModel: MoviesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isDrawerExpanded by remember { mutableStateOf(false) }

    if (isTv) {
        IdealPlayerDrawer(
            isExpanded = isDrawerExpanded,
            selectedRoute = Routes.MOVIES,
            isTv = true,
            onToggle = { isDrawerExpanded = !isDrawerExpanded },
            onNavigate = onNavigate
        ) {
            if (state.isLoading) LoadingScreen()
            else TvMoviesContent(state, viewModel, onMovieClick)
        }
    } else {
        if (state.isLoading) LoadingScreen()
        else MobileMoviesContent(
            state = state,
            viewModel = viewModel,
            onMovieClick = onMovieClick,
            onSearch = { onNavigate(Routes.SEARCH) }
        )
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TV: Side category panel + grid
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvMoviesContent(
    state: MoviesState,
    viewModel: MoviesViewModel,
    onMovieClick: (Long) -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val allCategoryFocusRequester = remember { FocusRequester() }

    val displayMovies = if (state.selectedCategory != null)
        state.movies.filter { it.categoryName == state.selectedCategory }
    else state.movies
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    // Category changes are initiated from the focused category item. Requesting focus again
    // here used to pull focus back to "All" every time a user chose a category or the grid
    // refreshed. Only establish the entry focus once, after the first composed frame.
    LaunchedEffect(state.isLoading) {
        if (hasRequestedInitialFocus || state.isLoading) return@LaunchedEffect
        withFrameNanos { }
        allCategoryFocusRequester.requestFocusSafely()
        hasRequestedInitialFocus = true
    }

    Row(modifier = Modifier.fillMaxSize()) {
        TvCategoryPanel {
            item(key = tvCategoryLazyKey("movies", null)) {
                TvRailCategoryItem(
                    name = stringResource(R.string.category_all),
                    isSelected = state.selectedCategory == null,
                    modifier = Modifier
                        .focusRequester(allCategoryFocusRequester)
                        .focusProperties {
                            // Let spatial focus search choose a currently composed grid card.
                            // The grid may retain a non-zero scroll position after filtering,
                            // so its first item is not necessarily attached.
                            if (displayMovies.isEmpty()) right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                        },
                    onClick = { viewModel.selectCategory(null) }
                )
            }
            itemsIndexed(
                items = state.categories,
                key = { _, cat -> tvCategoryLazyKey("movies", cat) }
            ) { index, cat ->
                TvRailCategoryItem(
                    name = cat,
                    isSelected = state.selectedCategory == cat,
                    modifier = Modifier.focusProperties {
                        if (displayMovies.isEmpty()) right = FocusRequester.Cancel
                        if (index == state.categories.lastIndex) {
                            down = FocusRequester.Cancel
                        }
                    },
                    onClick = { viewModel.selectCategory(cat) }
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 32.dp, top = 36.dp, end = 48.dp)
        ) {
            Text(
                text = stringResource(R.string.movies),
                style = MaterialTheme.typography.headlineLarge,
                color = IdealPlayerColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(36.dp))

            if (displayMovies.isEmpty()) {
                EmptyScreen(message = stringResource(R.string.no_content))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(dimens.gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(displayMovies, key = { it.id }) { movie ->
                        MoviePosterCard(
                            movie = movie,
                            modifier = Modifier
                                .width(250.dp)
                                .height(403.dp),
                            onPosterError = { viewModel.repairArtwork(movie.id) },
                            onClick = { onMovieClick(movie.id) }
                        )
                    }
                }
            }
        }
    }
}

private fun FocusRequester.requestFocusSafely() {
    runCatching { requestFocus() }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Mobile: Quick category bar + bottom sheet + grid
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun MobileMoviesContent(
    state: MoviesState,
    viewModel: MoviesViewModel,
    onMovieClick: (Long) -> Unit,
    onSearch: () -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    var showCategorySheet by remember { mutableStateOf(false) }
    var recentCategories by remember { mutableStateOf(listOf<String>()) }
    var pinnedCategories by remember { mutableStateOf(listOf<String>()) }

    // Build category counts
    val categoryCounts = remember(state.movies) {
        state.movies.groupBy { it.categoryName }.mapValues { it.value.size }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = dimens.screenPadding)) {
        MobileCatalogHeader(
            title = stringResource(R.string.movies),
            count = state.movies.count {
                state.selectedCategory == null || it.categoryName == state.selectedCategory
            },
            selectedCategory = state.selectedCategory,
            onSearch = onSearch,
            modifier = Modifier.padding(horizontal = dimens.screenPadding)
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Quick category bar with "Browse All" button
        QuickCategoryBar(
            categories = state.categories,
            selectedCategory = state.selectedCategory,
            recentCategories = recentCategories,
            onCategorySelected = { cat ->
                viewModel.selectCategory(cat)
                if (cat != null && cat !in recentCategories) {
                    recentCategories = (listOf(cat) + recentCategories).take(10)
                }
            },
            onBrowseAll = { showCategorySheet = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        val displayMovies = if (state.selectedCategory != null)
            state.movies.filter { it.categoryName == state.selectedCategory }
        else state.movies

        if (displayMovies.isEmpty()) {
            EmptyScreen(message = stringResource(R.string.no_content))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(dimens.gridColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = dimens.screenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
            ) {
                items(displayMovies, key = { it.id }) { movie ->
                    MoviePosterCard(
                        movie = movie,
                        modifier = Modifier.fillMaxWidth(),
                        onPosterError = { viewModel.repairArtwork(movie.id) },
                        onClick = { onMovieClick(movie.id) }
                    )
                }
            }
        }
    }

    // Category bottom sheet
    CategoryBottomSheet(
        isVisible = showCategorySheet,
        categories = state.categories,
        categoryCounts = categoryCounts,
        selectedCategory = state.selectedCategory,
        recentCategories = recentCategories,
        pinnedCategories = pinnedCategories,
        onCategorySelected = { cat ->
            viewModel.selectCategory(cat)
            if (cat != null && cat !in recentCategories) {
                recentCategories = (listOf(cat) + recentCategories).take(10)
            }
        },
        onDismiss = { showCategorySheet = false },
        onTogglePin = { cat ->
            pinnedCategories = if (cat in pinnedCategories) pinnedCategories - cat else pinnedCategories + cat
        }
    )
}

@Composable
private fun MobileCatalogHeader(
    title: String,
    count: Int,
    selectedCategory: String?,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = IdealPlayerColors.TextPrimary
            )
            Text(
                text = selectedCategory?.let { "$it • ${stringResource(R.string.catalog_item_count, count)}" }
                    ?: stringResource(R.string.catalog_item_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = IdealPlayerColors.TextSecondary,
                maxLines = 1
            )
        }
        A2Badge(
            text = count.toString(),
            tone = A2BadgeTone.Selected,
            contentDescription = stringResource(R.string.catalog_item_count, count)
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(
            onClick = onSearch,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = IdealPlayerColors.SurfaceElevated,
                contentColor = IdealPlayerColors.TextPrimary
            )
        ) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
        }
    }
}

@Composable
private fun MoviePosterCard(
    movie: Movie,
    modifier: Modifier = Modifier,
    onPosterError: () -> Unit,
    onClick: () -> Unit
) {
    val metadata = remember(movie.year, movie.rating) {
        buildList {
            if (movie.year > 0) add(movie.year.toString())
            if (movie.rating > 0.0) add(String.format(java.util.Locale.US, "%.1f", movie.rating))
        }.joinToString(" • ").takeIf { it.isNotBlank() }
    }

    A2ContentCard(
        kind = A2ContentCardKind.Movie,
        title = movie.name,
        metadata = metadata,
        contentDescription = movie.name,
        onClick = onClick,
        modifier = modifier,
        artwork = {
            PosterImage(
                url = movie.posterUrl,
                contentDescription = movie.name,
                onError = onPosterError,
                modifier = Modifier.matchParentSize()
            )
        }
    )
}
