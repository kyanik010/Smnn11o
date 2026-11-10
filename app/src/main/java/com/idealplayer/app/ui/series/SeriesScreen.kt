package com.idealplayer.app.ui.series

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.core.model.Series
import com.idealplayer.app.data.repository.ContentRepository
import com.idealplayer.app.data.repository.PlaylistRepository
import com.idealplayer.app.ui.components.*
import com.idealplayer.app.ui.components.a2.A2ContentCard
import com.idealplayer.app.ui.components.a2.A2ContentCardKind
import com.idealplayer.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import androidx.compose.ui.res.stringResource
import com.idealplayer.app.R

data class SeriesState(
    val series: List<Series> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SeriesState())
    val state: StateFlow<SeriesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.getActivePlaylist().collectLatest { playlist ->
                if (playlist != null) {
                    launch(Dispatchers.IO) {
                        contentRepository.backfillMissingSeriesArtwork(playlist.id)
                    }
                    launch {
                        contentRepository.getSeriesCategories(playlist.id).collect { cats ->
                            _state.update { it.copy(categories = cats) }
                        }
                    }
                    launch {
                        contentRepository.getSeries(playlist.id).collect { series ->
                            _state.update { it.copy(series = series, isLoading = false) }
                        }
                    }
                }
            }
        }
    }

    fun selectCategory(cat: String?) = _state.update { it.copy(selectedCategory = cat) }

    fun repairArtwork(seriesId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            contentRepository.repairSeriesArtwork(seriesId)
        }
    }
}

@Composable
fun SeriesScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onSeriesClick: (Long) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isDrawerExpanded by remember { mutableStateOf(false) }

    if (isTv) {
        IdealPlayerDrawer(
            isExpanded = isDrawerExpanded,
            selectedRoute = Routes.SERIES,
            isTv = true,
            onToggle = { isDrawerExpanded = !isDrawerExpanded },
            onNavigate = onNavigate
        ) {
            if (state.isLoading) LoadingScreen()
            else TvSeriesContent(state, viewModel, onSeriesClick)
        }
    } else {
        if (state.isLoading) LoadingScreen()
        else MobileSeriesContent(
            state = state,
            viewModel = viewModel,
            onSeriesClick = onSeriesClick,
            onSearch = { onNavigate(Routes.SEARCH) }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvSeriesContent(
    state: SeriesState,
    viewModel: SeriesViewModel,
    onSeriesClick: (Long) -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val allCategoryFocusRequester = remember { FocusRequester() }
    val display = if (state.selectedCategory != null) {
        state.series.filter { it.categoryName == state.selectedCategory }
    } else {
        state.series
    }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    // Preserve the current category/grid focus across filtering and data refreshes. The
    // category panel itself is always composed, so it is a safe initial target once loading
    // finishes without continuously stealing focus from the remote user.
    LaunchedEffect(state.isLoading) {
        if (hasRequestedInitialFocus || state.isLoading) return@LaunchedEffect
        withFrameNanos { }
        allCategoryFocusRequester.requestFocusSafely()
        hasRequestedInitialFocus = true
    }

    Row(modifier = Modifier.fillMaxSize()) {
        TvCategoryPanel {
            item(key = tvCategoryLazyKey("series", null)) {
                TvRailCategoryItem(
                    name = stringResource(R.string.category_all),
                    isSelected = state.selectedCategory == null,
                    modifier = Modifier
                        .focusRequester(allCategoryFocusRequester)
                        .focusProperties {
                            // LazyGrid can preserve a scrolled viewport across filtering. Use
                            // spatial traversal so RIGHT never targets a detached index-zero row.
                            if (display.isEmpty()) right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                        },
                    onClick = { viewModel.selectCategory(null) }
                )
            }
            itemsIndexed(
                items = state.categories,
                key = { _, cat -> tvCategoryLazyKey("series", cat) }
            ) { index, cat ->
                TvRailCategoryItem(
                    name = cat,
                    isSelected = state.selectedCategory == cat,
                    modifier = Modifier.focusProperties {
                        if (display.isEmpty()) right = FocusRequester.Cancel
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
                text = stringResource(R.string.series),
                style = MaterialTheme.typography.headlineLarge,
                color = IdealPlayerColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(36.dp))

            if (display.isEmpty()) {
                EmptyScreen(message = stringResource(R.string.no_content))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(dimens.gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(display, key = { it.id }) { item ->
                        SeriesPosterCard(
                            series = item,
                            modifier = Modifier
                                .width(250.dp)
                                .height(403.dp),
                            onPosterError = { viewModel.repairArtwork(item.id) },
                            onClick = { onSeriesClick(item.id) }
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

@Composable
private fun MobileSeriesContent(
    state: SeriesState,
    viewModel: SeriesViewModel,
    onSeriesClick: (Long) -> Unit,
    onSearch: () -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    var showCategorySheet by remember { mutableStateOf(false) }
    var recentCategories by remember { mutableStateOf(listOf<String>()) }
    var pinnedCategories by remember { mutableStateOf(listOf<String>()) }

    val categoryCounts = remember(state.series) {
        state.series.groupBy { it.categoryName }.mapValues { it.value.size }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = dimens.screenPadding)) {
        MobileSeriesCatalogHeader(
            title = stringResource(R.string.series),
            count = state.series.count {
                state.selectedCategory == null || it.categoryName == state.selectedCategory
            },
            selectedCategory = state.selectedCategory,
            onSearch = onSearch,
            modifier = Modifier.padding(horizontal = dimens.screenPadding)
        )
        Spacer(modifier = Modifier.height(12.dp))

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

        val display = if (state.selectedCategory != null) state.series.filter { it.categoryName == state.selectedCategory } else state.series

        if (display.isEmpty()) {
            EmptyScreen(message = stringResource(R.string.no_content))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(dimens.gridColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = dimens.screenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
            ) {
                items(display, key = { it.id }) { item ->
                    SeriesPosterCard(
                        series = item,
                        modifier = Modifier.fillMaxWidth(),
                        onPosterError = { viewModel.repairArtwork(item.id) },
                        onClick = { onSeriesClick(item.id) }
                    )
                }
            }
        }
    }

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
private fun SeriesPosterCard(
    series: Series,
    modifier: Modifier = Modifier,
    onPosterError: () -> Unit,
    onClick: () -> Unit
) {
    val metadata = remember(series.year, series.rating) {
        buildList {
            if (series.year > 0) add(series.year.toString())
            if (series.rating > 0.0) add(String.format(java.util.Locale.US, "%.1f", series.rating))
        }.joinToString(" • ").takeIf { it.isNotBlank() }
    }

    A2ContentCard(
        kind = A2ContentCardKind.Series,
        title = series.name,
        metadata = metadata,
        contentDescription = series.name,
        onClick = onClick,
        modifier = modifier,
        artwork = {
            PosterImage(
                url = series.posterUrl,
                contentDescription = series.name,
                onError = onPosterError,
                modifier = Modifier.matchParentSize()
            )
        }
    )
}

@Composable
private fun MobileSeriesCatalogHeader(
    title: String,
    count: Int,
    selectedCategory: String?,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = IdealPlayerColors.TextPrimary)
            Text(
                text = selectedCategory?.let { "$it • ${stringResource(R.string.catalog_item_count, count)}" }
                    ?: stringResource(R.string.catalog_item_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = IdealPlayerColors.TextSecondary,
                maxLines = 1
            )
        }
        com.idealplayer.app.ui.components.a2.A2Badge(
            text = count.toString(),
            tone = com.idealplayer.app.ui.components.a2.A2BadgeTone.Selected,
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
