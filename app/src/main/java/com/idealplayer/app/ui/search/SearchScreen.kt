package com.idealplayer.app.ui.search

import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.idealplayer.app.core.common.Constants
import com.idealplayer.app.core.common.SearchMatcher
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.core.model.*
import com.idealplayer.app.data.repository.ContentRepository
import com.idealplayer.app.data.repository.PlaylistRepository
import com.idealplayer.app.ui.components.*
import com.idealplayer.app.ui.components.a2.A2ActionButton
import com.idealplayer.app.ui.components.a2.A2ActionVariant
import com.idealplayer.app.ui.components.a2.A2BadgeTone
import com.idealplayer.app.ui.components.a2.A2ContentCard
import com.idealplayer.app.ui.components.a2.A2ContentCardKind
import com.idealplayer.app.ui.components.a2.A2SearchField
import com.idealplayer.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import androidx.compose.ui.res.stringResource
import com.idealplayer.app.R

private const val MIN_SEARCH_QUERY_LENGTH = 1
private const val MAX_COMBINED_SEARCH_RESULTS = 120

private data class RankedSearchResult(
    val result: SearchResult,
    val score: Double
)

private data class SearchRequest(
    val query: String,
    val filter: ContentType?,
    val playlistId: Long?
)

data class SearchState(
    val query: String = "",
    val contentTypeFilter: ContentType? = null,
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _queryFlow = MutableStateFlow("")
    private val _filterFlow = MutableStateFlow<ContentType?>(null)
    private val activePlaylist = playlistRepository.getActivePlaylist()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            combine(
                _queryFlow
                    .debounce(Constants.SEARCH_DEBOUNCE_MS)
                    .map { it.trim() }
                    .distinctUntilChanged(),
                _filterFlow,
                activePlaylist
            ) { query, filter, playlist ->
                SearchRequest(query = query, filter = filter, playlistId = playlist?.id)
            }
                .collectLatest { request ->
                    if (request.query.length >= MIN_SEARCH_QUERY_LENGTH && request.playlistId != null) {
                        performSearch(request.query, request.filter, request.playlistId)
                    } else {
                        _state.update { it.copy(results = emptyList(), isSearching = false) }
                    }
                }
        }
    }

    fun updateQuery(query: String) {
        val isSearchable = query.trim().length >= MIN_SEARCH_QUERY_LENGTH
        _state.update {
            it.copy(
                query = query,
                results = if (isSearchable) it.results else emptyList(),
                isSearching = isSearchable
            )
        }
        _queryFlow.value = query
    }

    fun setFilter(type: ContentType?) {
        if (_state.value.contentTypeFilter == type) return
        _filterFlow.value = type
        _state.update {
            it.copy(
                contentTypeFilter = type,
                isSearching = it.query.trim().length >= MIN_SEARCH_QUERY_LENGTH
            )
        }
    }

    private suspend fun performSearch(query: String, filter: ContentType?, playlistId: Long) = coroutineScope {
        val startedAt = SystemClock.elapsedRealtime()
        val results = mutableListOf<RankedSearchResult>()
        val allowFullScanFallback = filter != null

        if (filter == null || filter == ContentType.LIVE) {
            addChannelResults(
                results = results,
                channels = contentRepository.searchChannelsNow(
                    playlistId = playlistId,
                    query = query,
                    allowFullScanFallback = allowFullScanFallback
                ),
                query = query
            )
            if (filter == null && results.isNotEmpty()) {
                publishSearchResults(results, isSearching = true)
            }
        }

        val movies = if (filter == null || filter == ContentType.MOVIE) {
            async {
                contentRepository.searchMoviesNow(
                    playlistId = playlistId,
                    query = query,
                    allowFullScanFallback = allowFullScanFallback
                )
            }
        } else {
            null
        }
        val series = if (filter == null || filter == ContentType.SERIES) {
            async {
                contentRepository.searchSeriesNow(
                    playlistId = playlistId,
                    query = query,
                    allowFullScanFallback = allowFullScanFallback
                )
            }
        } else {
            null
        }

        movies?.await()?.let { addMovieResults(results, it, query) }
        series?.await()?.let { addSeriesResults(results, it, query) }

        val sortedResults = publishSearchResults(results, isSearching = false)
        Timber.d(
            "Search completed length=${query.length}, filter=$filter, results=${sortedResults.size}, elapsedMs=${SystemClock.elapsedRealtime() - startedAt}"
        )
    }

    private fun publishSearchResults(
        results: List<RankedSearchResult>,
        isSearching: Boolean
    ): List<SearchResult> {
        val sortedResults = results
            .sortedWith(
                compareByDescending<RankedSearchResult> { it.score }
                    .thenBy { it.result.title.lowercase(Locale.ENGLISH) }
            )
            .take(MAX_COMBINED_SEARCH_RESULTS)
            .map { it.result }

        _state.update { it.copy(results = sortedResults, isSearching = isSearching) }
        return sortedResults
    }

    private fun addChannelResults(
        results: MutableList<RankedSearchResult>,
        channels: List<Channel>,
        query: String
    ) {
        channels.mapTo(results) {
            RankedSearchResult(
                result = SearchResult(it.id, it.name, it.logoUrl, ContentType.LIVE, it.groupTitle, streamUrl = it.streamUrl),
                score = SearchMatcher.score(query, it.name, listOf(it.groupTitle, it.epgChannelId))
            )
        }
    }

    private fun addMovieResults(
        results: MutableList<RankedSearchResult>,
        movies: List<Movie>,
        query: String
    ) {
        movies.mapTo(results) {
            RankedSearchResult(
                result = SearchResult(it.id, it.name, it.posterUrl, ContentType.MOVIE, it.genre, it.year, it.rating, it.streamUrl),
                score = SearchMatcher.score(query, it.name, listOf(it.categoryName, it.genre, it.releaseDate), it.year)
            )
        }
    }

    private fun addSeriesResults(
        results: MutableList<RankedSearchResult>,
        series: List<Series>,
        query: String
    ) {
        series.mapTo(results) {
            RankedSearchResult(
                result = SearchResult(it.id, it.name, it.posterUrl, ContentType.SERIES, it.genre, it.year, it.rating),
                score = SearchMatcher.score(query, it.name, listOf(it.categoryName, it.genre, it.releaseDate), it.year)
            )
        }
    }
}

@Composable
fun SearchScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isDrawerExpanded by remember { mutableStateOf(false) }

    if (isTv) {
        IdealPlayerDrawer(
            isExpanded = isDrawerExpanded,
            selectedRoute = Routes.SEARCH,
            isTv = true,
            onToggle = { isDrawerExpanded = !isDrawerExpanded },
            onNavigate = onNavigate
        ) {
            TvSearchContent(state, viewModel, onContentClick, onPlayContent)
        }
    } else {
        MobileSearchContent(state, viewModel, onContentClick, onPlayContent)
    }
}

@Composable
private fun TvSearchContent(
    state: SearchState,
    viewModel: SearchViewModel,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String) -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { searchFocusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = dimens.screenPadding, top = 40.dp, end = dimens.screenPadding)
    ) {
        Text(
            text = stringResource(R.string.search),
            style = MaterialTheme.typography.headlineLarge,
            color = IdealPlayerColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(16.dp))
        A2SearchField(
            query = state.query,
            onQueryChange = viewModel::updateQuery,
            label = stringResource(R.string.search),
            placeholder = stringResource(R.string.search_hint),
            clearContentDescription = stringResource(R.string.action_clear_search),
            onSearch = {},
            modifier = Modifier
                .width(900.dp)
                .heightIn(min = dimens.touchTargetMin)
                .focusRequester(searchFocusRequester)
        )
        Spacer(modifier = Modifier.height(16.dp))
        SearchFilterChips(state, viewModel)
        Spacer(modifier = Modifier.height(20.dp))
        SearchResults(state, dimens.gridColumns, true, onContentClick, onPlayContent)
    }
}

@Composable
private fun MobileSearchContent(
    state: SearchState,
    viewModel: SearchViewModel,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String) -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val config = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = dimens.screenPadding)) {
        Text(stringResource(R.string.search), style = MaterialTheme.typography.headlineMedium, color = IdealPlayerColors.TextPrimary,
            modifier = Modifier.padding(horizontal = dimens.screenPadding))
        Spacer(modifier = Modifier.height(12.dp))

        A2SearchField(
            query = state.query,
            onQueryChange = viewModel::updateQuery,
            label = stringResource(R.string.search),
            placeholder = stringResource(R.string.search_hint),
            clearContentDescription = stringResource(R.string.action_clear_search),
            onSearch = { focusManager.clearFocus() },
            modifier = Modifier
                .padding(horizontal = dimens.screenPadding)
                .then(
                    if (config.smallestScreenWidthDp >= 600) Modifier.width(872.dp)
                    else Modifier.fillMaxWidth()
                )
                .heightIn(min = dimens.touchTargetMin)
                .focusRequester(searchFocusRequester),
        )
        Spacer(modifier = Modifier.height(10.dp))
        SearchFilterChips(state, viewModel, Modifier.padding(horizontal = dimens.screenPadding))
        Spacer(modifier = Modifier.height(8.dp))
        SearchResults(state, dimens.gridColumns, false, onContentClick, onPlayContent)
    }
}

@Composable
private fun SearchFilterChips(
    state: SearchState,
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            A2ActionButton(
                text = stringResource(R.string.category_all),
                onClick = { viewModel.setFilter(null) },
                variant = A2ActionVariant.Ghost,
                selected = state.contentTypeFilter == null
            )
        }
        item {
            A2ActionButton(
                text = stringResource(R.string.live_tv),
                onClick = { viewModel.setFilter(ContentType.LIVE) },
                variant = A2ActionVariant.Ghost,
                selected = state.contentTypeFilter == ContentType.LIVE
            )
        }
        item {
            A2ActionButton(
                text = stringResource(R.string.movies),
                onClick = { viewModel.setFilter(ContentType.MOVIE) },
                variant = A2ActionVariant.Ghost,
                selected = state.contentTypeFilter == ContentType.MOVIE
            )
        }
        item {
            A2ActionButton(
                text = stringResource(R.string.series),
                onClick = { viewModel.setFilter(ContentType.SERIES) },
                variant = A2ActionVariant.Ghost,
                selected = state.contentTypeFilter == ContentType.SERIES
            )
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchState,
    columns: Int,
    isTv: Boolean,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String) -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.isSearching && state.results.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isTv) 0.dp else dimens.screenPadding),
                color = IdealPlayerColors.Primary,
                trackColor = IdealPlayerColors.SurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        when {
            state.isSearching && state.results.isEmpty() -> LoadingScreen()
            state.query.isNotBlank() && state.results.isEmpty() -> EmptyScreen(stringResource(R.string.no_results))
            state.query.isBlank() -> EmptyScreen(stringResource(R.string.search_hint))
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = if (isTv) 0.dp else dimens.screenPadding,
                        vertical = 8.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(dimens.cardSpacing),
                    verticalArrangement = Arrangement.spacedBy(dimens.cardSpacing)
                ) {
                    items(state.results, key = { "${it.contentType.name}-${it.id}" }) { result ->
                        val compactLandscape = !isTv && columns == 2
                        SearchResultCard(
                            result = result,
                            compactLandscape = compactLandscape,
                            modifier = when {
                                isTv -> Modifier
                                    .width(216.dp)
                                    .height(348.dp)
                                columns >= 4 -> Modifier
                                    .width(206.dp)
                                    .height(264.dp)
                                else -> Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            },
                            onClick = {
                                when (result.contentType) {
                                    ContentType.LIVE -> onPlayContent(result.streamUrl, result.title, result.id, "LIVE")
                                    else -> onContentClick(result.id, result.contentType.name)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    compactLandscape: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val metadata = remember(result.year, result.rating) {
        buildList {
            if (result.year > 0) add(result.year.toString())
            if (result.rating > 0.0) add(String.format(Locale.US, "%.1f", result.rating))
        }.joinToString(" • ").takeIf { it.isNotBlank() }
    }
    val kind = when {
        compactLandscape -> A2ContentCardKind.Landscape
        result.contentType == ContentType.SERIES -> A2ContentCardKind.Series
        else -> A2ContentCardKind.Movie
    }

    A2ContentCard(
        kind = kind,
        title = result.title,
        subtitle = result.genre.takeIf { it.isNotBlank() },
        metadata = metadata,
        badgeText = if (result.contentType == ContentType.LIVE) stringResource(R.string.live_tv) else null,
        badgeTone = A2BadgeTone.Primary,
        contentDescription = result.title,
        onClick = onClick,
        modifier = modifier,
        artwork = {
            PosterImage(
                url = result.posterUrl,
                contentDescription = result.title,
                modifier = Modifier.matchParentSize()
            )
        }
    )
}
