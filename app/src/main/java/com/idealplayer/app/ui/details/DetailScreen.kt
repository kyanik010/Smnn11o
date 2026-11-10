package com.idealplayer.app.ui.details

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.idealplayer.app.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.common.isUsableArtworkUrl
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.core.model.*
import com.idealplayer.app.data.repository.ContentRepository
import com.idealplayer.app.metadata.MetadataResult
import com.idealplayer.app.ui.components.*
import com.idealplayer.app.ui.components.a2.A2ActionButton
import com.idealplayer.app.ui.components.a2.A2ActionVariant
import com.idealplayer.app.ui.components.a2.A2IconButton
import com.idealplayer.app.ui.components.a2.A2StatusSurface
import com.idealplayer.app.ui.components.a2.A2StatusType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailState(
    val movie: Movie? = null,
    val series: Series? = null,
    val episodes: List<Episode> = emptyList(),
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int = 1,
    val resumeEpisode: Episode? = null,
    val nextEpisode: Episode? = null,
    val isLoading: Boolean = true,
    val isFavorite: Boolean = false,
    val isLoadingEpisodes: Boolean = false,
    val isEnrichingMetadata: Boolean = false,
    val metadataEnriched: Boolean = false,
    val episodeError: String? = null,
    // Enriched metadata fields (available after TMDB fetch)
    val tagline: String = "",
    val trailerUrl: String = ""
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    fun loadContent(id: Long, type: String) {
        viewModelScope.launch {
            _state.value = DetailState(isLoading = true)
            when (type) {
                "MOVIE" -> loadMovie(id)
                "SERIES" -> loadSeries(id)
                else -> _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadMovie(id: Long) {
        val movie = contentRepository.getMovie(id)
        if (movie == null) {
            _state.update { it.copy(isLoading = false) }
            return
        }

        val cachedMetadata = contentRepository.getCachedMetadata(movie.name, movie.year, ContentType.MOVIE)
        val hydratedMovie = movie.mergeWith(cachedMetadata)

        _state.value = DetailState(
            movie = hydratedMovie,
            isFavorite = hydratedMovie.isFavorite,
            isLoading = false,
            tagline = cachedMetadata?.tagline.orEmpty(),
            trailerUrl = cachedMetadata?.trailerUrl.orEmpty()
        )

        if (cachedMetadata == null || needsMetadataEnrichment(hydratedMovie)) {
            enrichMovieMetadata(movie)
        }
        if (!hasUsableMovieArtwork(hydratedMovie)) {
            repairMovieArtwork(movie.id)
        }
    }

    private suspend fun loadSeries(id: Long) {
        val series = contentRepository.getSeriesById(id)
        if (series == null) {
            _state.update { it.copy(isLoading = false) }
            return
        }

        val cachedMetadata = contentRepository.getCachedMetadata(series.name, series.year, ContentType.SERIES)
        val hydratedSeries = series.mergeWith(cachedMetadata)
        val allEpisodes = contentRepository.getAllEpisodes(series.id)
        val resumeEpisode = contentRepository.getSeriesResumeEpisode(series.id)
        val seasons = allEpisodes.map { it.seasonNumber }.distinct().sorted()
        val selectedSeason = resumeEpisode?.seasonNumber ?: seasons.firstOrNull() ?: 1
        val seasonEpisodes = allEpisodes.filter { it.seasonNumber == selectedSeason }

        _state.value = DetailState(
            series = hydratedSeries,
            episodes = seasonEpisodes,
            seasons = seasons,
            selectedSeason = selectedSeason,
            resumeEpisode = resumeEpisode,
            nextEpisode = findAdjacentEpisode(allEpisodes, resumeEpisode, direction = 1),
            isLoading = false,
            isFavorite = hydratedSeries.isFavorite,
            isLoadingEpisodes = allEpisodes.isEmpty(),
            tagline = cachedMetadata?.tagline.orEmpty(),
            trailerUrl = cachedMetadata?.trailerUrl.orEmpty()
        )

        if (cachedMetadata == null || needsSeriesMetadataEnrichment(hydratedSeries)) {
            enrichSeriesMetadata(series)
        }

        refreshSeriesEpisodes(series, showLoader = allEpisodes.isEmpty())
    }

    private fun needsMetadataEnrichment(movie: Movie): Boolean {
        return !isUsableArtworkUrl(movie.posterUrl) ||
            movie.plot.isBlank() ||
            movie.cast.isBlank() ||
            !isUsableArtworkUrl(movie.backdropUrl) ||
            movie.genre.isBlank() ||
            movie.duration == 0 ||
            movie.tmdbId == 0
    }

    private fun needsSeriesMetadataEnrichment(series: Series): Boolean {
        return !isUsableArtworkUrl(series.posterUrl) ||
            series.plot.isBlank() ||
            series.cast.isBlank() ||
            !isUsableArtworkUrl(series.backdropUrl) ||
            series.genre.isBlank() ||
            series.tmdbId == 0
    }

    private fun hasUsableMovieArtwork(movie: Movie): Boolean =
        isUsableArtworkUrl(movie.posterUrl) && isUsableArtworkUrl(movie.backdropUrl)

    private fun enrichMovieMetadata(movie: Movie) {
        viewModelScope.launch {
            _state.update { it.copy(isEnrichingMetadata = true) }
            val metadata = contentRepository.enrichMetadata(movie.name, movie.year, ContentType.MOVIE, movie.tmdbId)
            if (metadata != null) {
                val identityCorrected = movie.tmdbId > 0 && metadata.tmdbId > 0 &&
                    movie.tmdbId != metadata.tmdbId
                contentRepository.updateMovieWithMetadata(
                    movieId = movie.id,
                    metadata = metadata,
                    replaceExistingArtwork = identityCorrected,
                    replaceExistingIdentity = identityCorrected
                )
                val updated = contentRepository.getMovie(movie.id)?.mergeWith(metadata)
                _state.update { 
                    it.copy(
                        movie = updated, 
                        isEnrichingMetadata = false, 
                        metadataEnriched = true,
                        tagline = metadata.tagline,
                        trailerUrl = metadata.trailerUrl
                    ) 
                }
            } else {
                _state.update { it.copy(isEnrichingMetadata = false) }
            }
        }
    }

    fun repairMovieArtwork(movieId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isEnrichingMetadata = true) }
            val repaired = contentRepository.repairMovieArtwork(movieId)
            val updatedMovie = contentRepository.getMovie(movieId)
            if (updatedMovie == null) {
                _state.update { it.copy(isEnrichingMetadata = false) }
                return@launch
            }

            val cachedMetadata = contentRepository.getCachedMetadata(
                updatedMovie.name,
                updatedMovie.year,
                ContentType.MOVIE
            )
            _state.update {
                it.copy(
                    movie = updatedMovie.mergeWith(cachedMetadata),
                    isEnrichingMetadata = false,
                    metadataEnriched = it.metadataEnriched || repaired,
                    tagline = cachedMetadata?.tagline ?: it.tagline,
                    trailerUrl = cachedMetadata?.trailerUrl ?: it.trailerUrl
                )
            }
        }
    }

    private fun enrichSeriesMetadata(series: Series) {
        viewModelScope.launch {
            _state.update { it.copy(isEnrichingMetadata = true) }
            val metadata = contentRepository.enrichMetadata(series.name, series.year, ContentType.SERIES, series.tmdbId)
            if (metadata != null) {
                val identityCorrected = series.tmdbId > 0 && metadata.tmdbId > 0 &&
                    series.tmdbId != metadata.tmdbId
                contentRepository.updateSeriesWithMetadata(
                    seriesId = series.id,
                    metadata = metadata,
                    replaceExistingArtwork = identityCorrected,
                    replaceExistingIdentity = identityCorrected
                )
                val updated = contentRepository.getSeriesById(series.id)?.mergeWith(metadata)
                _state.update { 
                    it.copy(
                        series = updated, 
                        isEnrichingMetadata = false, 
                        metadataEnriched = true,
                        tagline = metadata.tagline,
                        trailerUrl = metadata.trailerUrl
                    ) 
                }
            } else {
                _state.update { it.copy(isEnrichingMetadata = false) }
            }
        }
    }

    private fun refreshSeriesEpisodes(series: Series, showLoader: Boolean) {
        viewModelScope.launch {
            if (showLoader) {
                _state.update { it.copy(isLoadingEpisodes = true, episodeError = null) }
            }
            try {
                contentRepository.syncSeriesEpisodes(series)
                val allEpisodes = contentRepository.getAllEpisodes(series.id)
                val resumeEpisode = contentRepository.getSeriesResumeEpisode(series.id)
                val seasons = allEpisodes.map { it.seasonNumber }.distinct().sorted()
                val selectedSeason = _state.value.selectedSeason
                    .takeIf { season -> allEpisodes.any { it.seasonNumber == season } }
                    ?: resumeEpisode?.seasonNumber
                    ?: seasons.firstOrNull()
                    ?: 1

                _state.update {
                    it.copy(
                        episodes = allEpisodes.filter { episode -> episode.seasonNumber == selectedSeason },
                        seasons = seasons,
                        selectedSeason = selectedSeason,
                        resumeEpisode = resumeEpisode,
                        nextEpisode = findAdjacentEpisode(allEpisodes, resumeEpisode, direction = 1),
                        isLoadingEpisodes = false,
                        episodeError = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingEpisodes = false, episodeError = "Failed to load episodes: ${e.localizedMessage}") }
            }
        }
    }

    fun retryEpisodes() {
        val series = _state.value.series ?: return
        refreshSeriesEpisodes(series, showLoader = true)
    }

    fun selectSeason(season: Int) {
        val seriesId = _state.value.series?.id ?: return
        viewModelScope.launch {
            val episodes = contentRepository.getEpisodesForSeason(seriesId, season)
            _state.update { it.copy(selectedSeason = season, episodes = episodes) }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val s = _state.value
            val newFav = !s.isFavorite
            _state.update { it.copy(isFavorite = newFav) }
            when {
                s.movie != null -> contentRepository.toggleMovieFavorite(s.movie.id, newFav)
                s.series != null -> contentRepository.toggleSeriesFavorite(s.series.id, newFav)
            }
        }
    }
}

@Composable
fun DetailScreen(
    contentId: Long,
    contentType: String,
    isTv: Boolean,
    onBack: () -> Unit,
    onPlay: (String, String, Long, String, Long) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(contentId, contentType) {
        viewModel.loadContent(contentId, contentType)
    }

    if (state.isLoading) {
        if (isTv) {
            TvDetailLoadingState(onBack = onBack)
        } else {
            LoadingScreen()
        }
        return
    }

    if (isTv) {
        if (state.movie == null && state.series == null) {
            TvDetailUnavailableState(onBack = onBack)
        } else {
            TvDetailContent(state, viewModel, onBack, onPlay)
        }
    } else {
        MobileDetailContent(state, viewModel, onBack, onPlay)
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TV Detail — horizontal layout (UNTOUCHED)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun TvDetailContent(
    state: DetailState,
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onPlay: (String, String, Long, String, Long) -> Unit
) {
    val movie = state.movie
    val series = state.series
    val title = movie?.name ?: series?.name ?: ""
    val posterUrl = movie?.posterUrl ?: series?.posterUrl ?: ""
    val backdropUrl = movie?.backdropUrl ?: series?.backdropUrl ?: ""
    val plot = movie?.plot ?: series?.plot ?: ""
    val year = movie?.year ?: series?.year ?: 0
    val rating = movie?.rating ?: series?.rating ?: 0.0
    val genre = movie?.genre ?: series?.genre ?: ""
    val cast = movie?.cast ?: series?.cast ?: ""
    val director = movie?.director ?: series?.director ?: ""
    val hasBackdrop = isUsableArtworkUrl(backdropUrl)
    val onArtworkError: () -> Unit = { movie?.id?.let(viewModel::repairMovieArtwork) }
    val dimens = LocalIdealPlayerDimens.current
    val scrollState = rememberScrollState()
    val backFocusRequester = remember { FocusRequester() }
    val hasPlayableAction = movie?.streamUrl?.isNotBlank() == true ||
        (state.resumeEpisode ?: state.episodes.firstOrNull())?.streamUrl?.isNotBlank() == true
    var hasRequestedBackFallback by remember(movie?.id, series?.id) { mutableStateOf(false) }

    LaunchedEffect(hasPlayableAction, movie?.id, series?.id) {
        if (!hasPlayableAction && !hasRequestedBackFallback) {
            withFrameNanos { }
            runCatching { backFocusRequester.requestFocus() }
            hasRequestedBackFallback = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(dimens.screenPadding)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (series != null) 550.dp else 650.dp),
            shape = RoundedCornerShape(16.dp),
            color = IdealPlayerColors.Surface,
            border = BorderStroke(1.dp, IdealPlayerColors.CardBorder)
        ) {
            Row(
                modifier = Modifier.padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(if (series != null) 720.dp else 700.dp)
                        .fillMaxHeight()
                ) {
                    TvDetailBackButton(
                        onBack = onBack,
                        focusRequester = backFocusRequester
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displaySmall,
                        color = IdealPlayerColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailMetaRow(
                        year = year,
                        rating = rating,
                        genre = genre,
                        runtime = movie?.duration ?: series?.episodeCount?.takeIf { it > 0 } ?: 0
                    )
                    if (plot.isNotBlank()) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = plot,
                            style = MaterialTheme.typography.bodyLarge,
                            color = IdealPlayerColors.TextSecondary,
                            maxLines = if (series != null) 4 else 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (state.isEnrichingMetadata) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = stringResource(R.string.loading_details),
                            style = MaterialTheme.typography.bodyMedium,
                            color = IdealPlayerColors.TextTertiary
                        )
                    }
                    MetadataLoadingIndicator(state.isEnrichingMetadata)
                    Spacer(modifier = Modifier.height(20.dp))
                    DetailActionButtons(state, viewModel, onPlay, isTv = true)
                    if (cast.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        DetailInfoSection(stringResource(R.string.cast), cast)
                    }
                    if (director.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        DetailInfoSection(stringResource(R.string.director), director)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .background(IdealPlayerColors.SurfaceElevated)
                ) {
                    PosterImage(
                        url = if (hasBackdrop) backdropUrl else posterUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        onError = onArtworkError,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        IdealPlayerColors.Surface.copy(alpha = 0.34f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        }

        if (series != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.episodes),
                style = MaterialTheme.typography.headlineMedium,
                color = IdealPlayerColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            when {
                state.isLoadingEpisodes -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = IdealPlayerColors.Primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.loading_episodes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = IdealPlayerColors.TextSecondary
                        )
                    }
                }

                state.seasons.isNotEmpty() -> {
                    SeasonSelector(
                        seasons = state.seasons,
                        selected = state.selectedSeason,
                        isTv = true
                    ) { viewModel.selectSeason(it) }
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(state.episodes, key = { it.id }) { episode ->
                            TvEpisodeRow(
                                episode = episode,
                                modifier = Modifier
                                    .width(520.dp)
                                    .heightIn(min = 184.dp),
                                onPlay = {
                                    onPlay(
                                        episode.streamUrl,
                                        "${series.name} S${episode.seasonNumber}E${episode.episodeNumber}",
                                        episode.id,
                                        "SERIES",
                                        episode.lastPosition
                                    )
                                }
                            )
                        }
                    }
                }

                else -> {
                    Text(
                        text = state.episodeError ?: stringResource(R.string.no_episodes_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IdealPlayerColors.TextTertiary
                    )
                    if (state.episodeError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        A2ActionButton(
                            text = stringResource(R.string.retry),
                            onClick = viewModel::retryEpisodes,
                            variant = A2ActionVariant.Secondary
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun TvDetailLoadingState(onBack: () -> Unit) {
    val backFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { backFocusRequester.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        TvDetailBackButton(
            onBack = onBack,
            focusRequester = backFocusRequester
        )
        A2StatusSurface(
            type = A2StatusType.Loading,
            title = stringResource(R.string.loading),
            message = stringResource(R.string.loading_details),
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 640.dp)
        )
    }
}

@Composable
private fun TvDetailUnavailableState(onBack: () -> Unit) {
    val backFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { backFocusRequester.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        TvDetailBackButton(
            onBack = onBack,
            focusRequester = backFocusRequester
        )
        A2StatusSurface(
            type = A2StatusType.Empty,
            title = stringResource(R.string.no_content),
            message = stringResource(R.string.no_content),
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 640.dp)
        )
    }
}

@Composable
private fun TvDetailBackButton(
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    A2IconButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(R.string.player_back),
        onClick = onBack,
        modifier = modifier
            .focusRequester(focusRequester),
        variant = A2ActionVariant.Secondary
    )
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Mobile Detail — ENHANCED stacked vertical layout
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun MobileDetailContent(
    state: DetailState,
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onPlay: (String, String, Long, String, Long) -> Unit
) {
    val movie = state.movie
    val series = state.series
    val title = movie?.name ?: series?.name ?: ""
    val posterUrl = movie?.posterUrl ?: series?.posterUrl ?: ""
    val backdropUrl = movie?.backdropUrl ?: series?.backdropUrl ?: ""
    val plot = movie?.plot ?: series?.plot ?: ""
    val year = movie?.year ?: series?.year ?: 0
    val rating = movie?.rating ?: series?.rating ?: 0.0
    val genre = movie?.genre ?: series?.genre ?: ""
    val cast = movie?.cast ?: series?.cast ?: ""
    val director = movie?.director ?: series?.director ?: ""
    val tagline = state.tagline
    val trailerUrl = state.trailerUrl
    val hasBackdrop = isUsableArtworkUrl(backdropUrl)
    val onArtworkError: () -> Unit = { movie?.id?.let(viewModel::repairMovieArtwork) }
    val dimens = LocalIdealPlayerDimens.current
    val scrollState = rememberScrollState()
    val config = LocalConfiguration.current
    val isLandscape = config.screenWidthDp > config.screenHeightDp

    Box(modifier = Modifier.fillMaxSize()) {
        // ─── Backdrop with multi-stop gradient ───
        if (hasBackdrop) {
            PosterImage(
                url = backdropUrl, 
                contentDescription = title, 
                contentScale = ContentScale.Crop,
                onError = onArtworkError,
                modifier = Modifier.fillMaxWidth().height(320.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.3f to IdealPlayerColors.Background.copy(alpha = 0.3f),
                            0.6f to IdealPlayerColors.Background.copy(alpha = 0.7f),
                            1.0f to IdealPlayerColors.Background
                        )
                    )
            )
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            // ─── Top bar ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                A2IconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.player_back),
                    onClick = onBack,
                    variant = A2ActionVariant.Ghost
                )
            }

            if (isLandscape) {
                // ─── Landscape: poster + info side by side ───
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.screenPadding)) {
                    PosterImage(
                        url = posterUrl,
                        contentDescription = title,
                        onError = onArtworkError,
                        modifier = Modifier.width(160.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineMedium, color = IdealPlayerColors.TextPrimary)
                        if (tagline.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(tagline, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), 
                                color = IdealPlayerColors.TextTertiary, maxLines = 2)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailMetaRow(year, rating, genre, movie?.duration ?: series?.episodeCount?.takeIf { it > 0 } ?: 0)
                        // Overview
                        if (plot.isNotBlank()) { 
                            Spacer(modifier = Modifier.height(8.dp))
                            ExpandableOverview(plot)
                        } else if (state.isEnrichingMetadata) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MetadataLoadingPlaceholder()
                        }
                        MetadataLoadingIndicator(state.isEnrichingMetadata)
                        Spacer(modifier = Modifier.height(12.dp))
                        DetailActionButtons(state, viewModel, onPlay, trailerUrl)
                    }
                }
            } else {
                // ─── Portrait: hero header ───
                Spacer(modifier = Modifier.height(if (hasBackdrop) 160.dp else 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.screenPadding),
                    verticalAlignment = Alignment.Bottom
                ) {
                    PosterImage(
                        url = posterUrl, 
                        contentDescription = title,
                        onError = onArtworkError,
                        modifier = Modifier
                            .width(120.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title, 
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), 
                            color = IdealPlayerColors.TextPrimary,
                            maxLines = 3, 
                            overflow = TextOverflow.Ellipsis
                        )
                        // Tagline
                        if (tagline.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                tagline,
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = IdealPlayerColors.TextTertiary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        DetailMetaRow(year, rating, genre, movie?.duration ?: series?.episodeCount?.takeIf { it > 0 } ?: 0)
                    }
                }

                // ─── Action Row ───
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.padding(horizontal = dimens.screenPadding)) {
                    DetailActionButtons(state, viewModel, onPlay, trailerUrl)
                }

                // ─── Overview / Description ───
                if (plot.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ExpandableOverview(
                        text = plot,
                        modifier = Modifier.padding(horizontal = dimens.screenPadding)
                    )
                } else if (state.isEnrichingMetadata) {
                    Spacer(modifier = Modifier.height(16.dp))
                    MetadataLoadingPlaceholder(Modifier.padding(horizontal = dimens.screenPadding))
                }
                MetadataLoadingIndicator(state.isEnrichingMetadata, Modifier.padding(horizontal = dimens.screenPadding))
            }

            // ─── Metadata info sections ───
            // Always show these sections when data is available, regardless of other fields
            val sectionPadding = Modifier.padding(horizontal = dimens.screenPadding)

            // Genre chips (if not already shown in meta row, show as chips for better visibility)
            if (genre.isNotBlank() && genre.contains(",")) {
                Spacer(modifier = Modifier.height(16.dp))
                GenreChipsRow(genre, sectionPadding)
            }

            // Cast
            if (cast.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                DetailInfoSection(
                    label = stringResource(R.string.cast),
                    value = cast,
                    modifier = sectionPadding
                )
            }

            // Director / Creator
            if (director.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                DetailInfoSection(
                    label = stringResource(R.string.director),
                    value = director,
                    modifier = sectionPadding
                )
            }

            // ─── Rating display (when available and substantial) ───
            if (rating > 0 && plot.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                RatingBar(rating, sectionPadding)
            }

            // ─── Episodes section for series ───
            if (series != null) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = IdealPlayerColors.DividerColor, modifier = sectionPadding)
                Spacer(modifier = Modifier.height(12.dp))

                if (state.isLoadingEpisodes) {
                    Row(
                        modifier = sectionPadding,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = IdealPlayerColors.Primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.loading_episodes), style = MaterialTheme.typography.bodyMedium, color = IdealPlayerColors.TextSecondary)
                    }
                } else if (state.seasons.isNotEmpty()) {
                    SeasonSelector(
                        seasons = state.seasons,
                        selected = state.selectedSeason,
                        isTv = false,
                        modifier = sectionPadding
                    ) { viewModel.selectSeason(it) }
                    Spacer(modifier = Modifier.height(8.dp))
                    state.episodes.forEach { ep ->
                        EpisodeRow(ep, false) { onPlay(ep.streamUrl, "${series.name} S${ep.seasonNumber}E${ep.episodeNumber}", ep.id, "SERIES", ep.lastPosition) }
                    }
                } else {
                    Column(modifier = sectionPadding) {
                        Text(state.episodeError ?: stringResource(R.string.no_episodes_available),
                            style = MaterialTheme.typography.bodyMedium, color = IdealPlayerColors.TextTertiary)
                        if (state.episodeError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            A2ActionButton(
                                text = stringResource(R.string.retry),
                                onClick = viewModel::retryEpisodes,
                                variant = A2ActionVariant.Secondary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Enhanced shared detail composables
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun ExpandableOverview(text: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val maxLines = if (expanded) Int.MAX_VALUE else 4
    
    Column(modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = IdealPlayerColors.TextSecondary,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        // Show expand/collapse only if text is likely longer than 4 lines
        if (text.length > 200) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (expanded) {
                    stringResource(R.string.a2_overview_collapse)
                } else {
                    stringResource(R.string.a2_overview_expand)
                },
                style = MaterialTheme.typography.labelMedium,
                color = IdealPlayerColors.Primary,
                modifier = Modifier.clickable { expanded = !expanded }
            )
        }
    }
}

@Composable
private fun MetadataLoadingPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (it == 2) 0.6f else 1f)
                    .height(14.dp)
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(IdealPlayerColors.SurfaceVariant.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MetadataLoadingIndicator(isLoading: Boolean, modifier: Modifier = Modifier) {
    if (isLoading) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = IdealPlayerColors.TextTertiary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.loading_details), style = MaterialTheme.typography.bodySmall, color = IdealPlayerColors.TextTertiary)
        }
    }
}

@Composable
private fun DetailMetaRow(year: Int, rating: Double, genre: String, runtime: Int = 0) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp), 
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        if (year > 0) {
            Surface(shape = RoundedCornerShape(6.dp), color = IdealPlayerColors.Primary.copy(alpha = 0.15f)) {
                Text(year.toString(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), 
                    color = IdealPlayerColors.Primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        if (rating > 0) {
            Surface(shape = RoundedCornerShape(6.dp), color = IdealPlayerColors.RatingStarColor.copy(alpha = 0.15f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        Icons.Filled.Star, 
                        contentDescription = null, 
                        tint = IdealPlayerColors.RatingStarColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        String.format(java.util.Locale.US, "%.1f", rating), 
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), 
                        color = IdealPlayerColors.RatingStarColor
                    )
                }
            }
        }
        if (runtime > 0) {
            Surface(shape = RoundedCornerShape(6.dp), color = IdealPlayerColors.SurfaceVariant) {
                Text(
                    com.idealplayer.app.core.common.StringUtils.formatDurationMinutes(runtime),
                    style = MaterialTheme.typography.labelMedium, 
                    color = IdealPlayerColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        if (genre.isNotBlank()) {
            val shortGenre = genre.split(",").take(2).joinToString(", ") { it.trim() }
            Text(
                shortGenre, 
                style = MaterialTheme.typography.bodySmall, 
                color = IdealPlayerColors.TextTertiary,
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GenreChipsRow(genre: String, modifier: Modifier = Modifier) {
    val genres = genre.split(",").map { it.trim() }.filter { it.isNotBlank() }
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(genres) { g ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = IdealPlayerColors.SurfaceVariant,
                border = BorderStroke(0.5.dp, IdealPlayerColors.DividerColor)
            ) {
                Text(
                    g,
                    style = MaterialTheme.typography.labelSmall,
                    color = IdealPlayerColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RatingBar(rating: Double, modifier: Modifier = Modifier) {
    val normalizedRating = normalizeContentRating(rating)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val starRating = normalizedRating / 2
        val filledStars = starRating.toInt().coerceIn(0, 5)
        val halfStar = filledStars < 5 && starRating - filledStars >= 0.5
        repeat(filledStars) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = IdealPlayerColors.RatingStarColor, modifier = Modifier.size(18.dp))
        }
        if (halfStar) {
            @Suppress("DEPRECATION")
            Icon(Icons.Filled.StarHalf, contentDescription = null, tint = IdealPlayerColors.RatingStarColor, modifier = Modifier.size(18.dp))
        }
        repeat((5 - filledStars - if (halfStar) 1 else 0).coerceAtLeast(0)) {
            Icon(Icons.Filled.StarBorder, contentDescription = null, tint = IdealPlayerColors.TextTertiary.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            String.format(java.util.Locale.US, "%.1f / 10", normalizedRating),
            style = MaterialTheme.typography.labelMedium,
            color = IdealPlayerColors.TextTertiary
        )
    }
}

internal fun normalizeContentRating(rating: Double): Double = when {
    !rating.isFinite() -> 0.0
    else -> rating.coerceIn(0.0, 10.0)
}

@Composable
private fun DetailActionButtons(
    state: DetailState,
    viewModel: DetailViewModel,
    onPlay: (String, String, Long, String, Long) -> Unit,
    trailerUrl: String = "",
    isTv: Boolean = false
) {
    val movie = state.movie
    val series = state.series
    val context = LocalContext.current
    val resumeEpisode = state.resumeEpisode ?: state.episodes.firstOrNull()
    val hasMoviePlayback = movie?.streamUrl?.isNotBlank() == true
    val hasSeriesPlayback = resumeEpisode?.streamUrl?.isNotBlank() == true
    val primaryActionFocusRequester = remember(isTv, movie?.id, series?.id) { FocusRequester() }
    var hasRequestedInitialTvFocus by remember(isTv, movie?.id, series?.id) {
        mutableStateOf(false)
    }

    val canFocusPrimaryAction = isTv && (hasMoviePlayback || hasSeriesPlayback)

    LaunchedEffect(canFocusPrimaryAction, movie?.id, series?.id, resumeEpisode?.id) {
        // Episode loading, season selection and metadata enrichment all update this state.
        // Re-requesting the play button on each update pulled focus away from a selected
        // season/episode. Request it only when the detail screen first becomes playable.
        if (canFocusPrimaryAction && !hasRequestedInitialTvFocus) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching {
                primaryActionFocusRequester.requestFocus()
            }
            hasRequestedInitialTvFocus = true
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (movie != null) {
            A2ActionButton(
                text = if (movie.lastPosition > 0) stringResource(R.string.action_resume) else stringResource(R.string.action_play),
                icon = Icons.Filled.PlayArrow,
                enabled = hasMoviePlayback,
                modifier = if (isTv) Modifier.focusRequester(primaryActionFocusRequester) else Modifier,
                onClick = { onPlay(movie.streamUrl, movie.name, movie.id, "MOVIE", movie.lastPosition) }
            )
        } else if (series != null) {
            A2ActionButton(
                text = when {
                    (resumeEpisode?.lastPosition ?: 0L) > 0L -> stringResource(
                        R.string.action_resume_episode,
                        resumeEpisode?.seasonNumber ?: state.selectedSeason,
                        resumeEpisode?.episodeNumber ?: 1
                    )
                    state.isLoadingEpisodes -> stringResource(R.string.loading_episodes)
                    resumeEpisode != null -> stringResource(
                        R.string.action_play_episode,
                        resumeEpisode.seasonNumber,
                        resumeEpisode.episodeNumber
                    )
                    else -> stringResource(R.string.action_play)
                },
                icon = Icons.Filled.PlayArrow,
                enabled = hasSeriesPlayback,
                loading = state.isLoadingEpisodes,
                modifier = if (isTv) Modifier.focusRequester(primaryActionFocusRequester) else Modifier,
                onClick = {
                    val playableEpisode = resumeEpisode ?: return@A2ActionButton
                    onPlay(
                        playableEpisode.streamUrl,
                        "${series.name} S${playableEpisode.seasonNumber}E${playableEpisode.episodeNumber}",
                        playableEpisode.id,
                        "SERIES",
                        playableEpisode.lastPosition
                    )
                }
            )

            val nextEpisode = state.nextEpisode
            if (nextEpisode != null) {
                A2ActionButton(
                    onClick = {
                        onPlay(
                            nextEpisode.streamUrl,
                            "${series.name} S${nextEpisode.seasonNumber}E${nextEpisode.episodeNumber}",
                            nextEpisode.id,
                            "SERIES",
                            nextEpisode.lastPosition
                        )
                    },
                    text = stringResource(R.string.next_episode),
                    icon = Icons.Filled.SkipNext,
                    variant = A2ActionVariant.Secondary
                )
            }
        }

        // Favorite button
        A2IconButton(
            icon = if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(
                if (state.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites
            ),
            selected = state.isFavorite,
            onClick = { viewModel.toggleFavorite() }
        )

        // Trailer button (when available)
        if (trailerUrl.isNotBlank()) {
            A2IconButton(
                icon = Icons.Filled.PlayCircle,
                contentDescription = stringResource(R.string.a2_trailer),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl))
                    context.startActivity(intent)
                },
                variant = A2ActionVariant.Ghost
            )
        }
    }
}

@Composable
private fun DetailInfoSection(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label, 
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), 
            color = IdealPlayerColors.TextTertiary,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value, 
            style = MaterialTheme.typography.bodyMedium, 
            color = IdealPlayerColors.TextSecondary,
            maxLines = 4, 
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SeasonSelector(
    seasons: List<Int>,
    selected: Int,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(seasons) { season ->
            if (isTv) {
                TvSeasonChip(
                    season = season,
                    isSelected = season == selected,
                    onClick = { onSelect(season) }
                )
            } else {
                A2ActionButton(
                    text = stringResource(R.string.season_format, season),
                    onClick = { onSelect(season) },
                    variant = A2ActionVariant.Ghost,
                    selected = season == selected
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    isTv: Boolean,
    onPlay: () -> Unit
) {
    if (isTv) {
        TvEpisodeRow(
            episode = episode,
            onPlay = onPlay
        )
        return
    }

    var isFocused by remember { mutableStateOf(false) }
    val dimens = LocalIdealPlayerDimens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenPadding, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) IdealPlayerColors.SurfaceVariant else IdealPlayerColors.CardBackground)
            .then(if (isFocused) Modifier.border(1.dp, IdealPlayerColors.FocusBorder, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onPlay)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.episode_format, episode.episodeNumber, episode.name),
                style = MaterialTheme.typography.titleSmall,
                color = IdealPlayerColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (episode.plot.isNotBlank()) {
                Text(episode.plot, style = MaterialTheme.typography.bodySmall, color = IdealPlayerColors.TextTertiary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (episode.duration > 0) {
                Text(com.idealplayer.app.core.common.StringUtils.formatDurationMinutes(episode.duration),
                    style = MaterialTheme.typography.labelSmall, color = IdealPlayerColors.TextTertiary)
            }
        }
        if (episode.lastPosition > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = IdealPlayerColors.SurfaceSelected,
                border = BorderStroke(1.dp, IdealPlayerColors.SelectedBorder)
            ) {
                Text(stringResource(R.string.action_resume), style = MaterialTheme.typography.labelSmall, color = IdealPlayerColors.Secondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(Icons.Filled.PlayCircle, stringResource(R.string.action_play), tint = IdealPlayerColors.Primary, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun TvSeasonChip(
    season: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    A2ActionButton(
        text = stringResource(R.string.season_format, season),
        onClick = onClick,
        variant = A2ActionVariant.Ghost,
        selected = isSelected
    )
}

@Composable
private fun TvEpisodeRow(
    episode: Episode,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit
) {
    val interactionSource = remember(episode.id, episode.episodeNumber, episode.lastPosition) { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val episodeShape = RoundedCornerShape(20.dp)
    val focusState = rememberTvFocusVisualState(
        isFocused = isFocused,
        isSelected = episode.lastPosition > 0L,
        defaultSurface = IdealPlayerColors.CardBackground,
        selectedSurface = IdealPlayerColors.SurfaceSelected,
        focusedSurface = IdealPlayerColors.SurfaceFocus,
        selectedFocusedSurface = IdealPlayerColors.SurfaceFocus
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .graphicsLayer {
                scaleX = focusState.scale
                scaleY = focusState.scale
                this.shape = episodeShape
                clip = false
                shadowElevation = focusState.shadowElevation.toPx()
            }
            .clip(episodeShape)
            .background(focusState.backgroundColor)
            .border(focusState.borderWidth, focusState.borderColor, episodeShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(focusState.accentWidth)
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(focusState.accentColor)
        )
        Spacer(modifier = Modifier.width(if (focusState.accentWidth > 0.dp) 16.dp else 10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.episode_format, episode.episodeNumber, episode.name),
                style = MaterialTheme.typography.titleMedium,
                color = focusState.contentColor,
                fontWeight = when {
                    isFocused && episode.lastPosition > 0L -> FontWeight.ExtraBold
                    isFocused -> FontWeight.Bold
                    else -> FontWeight.SemiBold
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (episode.plot.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = episode.plot,
                    style = MaterialTheme.typography.bodySmall,
                    color = focusState.secondaryContentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (episode.duration > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = com.idealplayer.app.core.common.StringUtils.formatDurationMinutes(episode.duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = focusState.secondaryContentColor
                )
            }
        }
        if (episode.lastPosition > 0) {
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = focusState.accentColor.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, focusState.accentColor.copy(alpha = 0.7f))
            ) {
                Text(
                    text = stringResource(R.string.action_resume),
                    style = MaterialTheme.typography.labelMedium,
                    color = focusState.contentColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(focusState.accentColor.copy(alpha = 0.18f))
                .border(1.5.dp, focusState.accentColor.copy(alpha = 0.7f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = stringResource(R.string.action_play),
                tint = focusState.contentColor,
                modifier = Modifier.size(26.dp)
            )
        }
        if (focusState.glowColor != Color.Transparent) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(focusState.glowColor)
            )
        }
    }
}

private fun Movie.mergeWith(metadata: MetadataResult?): Movie {
    metadata ?: return this

    return copy(
        posterUrl = if (!isUsableArtworkUrl(posterUrl) && isUsableArtworkUrl(metadata.posterUrl)) metadata.posterUrl else posterUrl,
        backdropUrl = if (!isUsableArtworkUrl(backdropUrl) && isUsableArtworkUrl(metadata.backdropUrl)) metadata.backdropUrl else backdropUrl,
        genre = metadata.genre.ifBlank { genre },
        plot = metadata.overview.ifBlank { plot },
        cast = metadata.cast.ifBlank { cast },
        director = metadata.director.ifBlank { director },
        year = if (year == 0 && metadata.year > 0) metadata.year else year,
        duration = if (duration == 0 && metadata.runtime > 0) metadata.runtime else duration,
        rating = if (rating == 0.0 && metadata.rating > 0) metadata.rating else rating,
        imdbId = if (imdbId.isBlank() && metadata.imdbId.isNotBlank()) metadata.imdbId else imdbId,
        tmdbId = if (tmdbId == 0 && metadata.tmdbId > 0) metadata.tmdbId else tmdbId
    )
}

private fun Series.mergeWith(metadata: MetadataResult?): Series {
    metadata ?: return this

    return copy(
        posterUrl = if (!isUsableArtworkUrl(posterUrl) && isUsableArtworkUrl(metadata.posterUrl)) metadata.posterUrl else posterUrl,
        backdropUrl = if (!isUsableArtworkUrl(backdropUrl) && isUsableArtworkUrl(metadata.backdropUrl)) metadata.backdropUrl else backdropUrl,
        genre = metadata.genre.ifBlank { genre },
        plot = metadata.overview.ifBlank { plot },
        cast = metadata.cast.ifBlank { cast },
        director = metadata.director.ifBlank { director },
        year = if (year == 0 && metadata.year > 0) metadata.year else year,
        rating = if (rating == 0.0 && metadata.rating > 0) metadata.rating else rating,
        imdbId = if (imdbId.isBlank() && metadata.imdbId.isNotBlank()) metadata.imdbId else imdbId,
        tmdbId = if (tmdbId == 0 && metadata.tmdbId > 0) metadata.tmdbId else tmdbId
    )
}

private fun findAdjacentEpisode(
    episodes: List<Episode>,
    currentEpisode: Episode?,
    direction: Int
): Episode? {
    if (currentEpisode == null || episodes.isEmpty()) return null

    val index = episodes.indexOfFirst { it.id == currentEpisode.id }
    if (index == -1) return null

    val targetIndex = index + direction
    return episodes.getOrNull(targetIndex)
}
