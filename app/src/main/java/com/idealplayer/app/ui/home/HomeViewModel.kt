package com.idealplayer.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idealplayer.app.core.model.*
import com.idealplayer.app.core.datastore.SettingsDataStore
import com.idealplayer.app.data.repository.ContentRepository
import com.idealplayer.app.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val playlist: Playlist? = null,
    val recentMovies: List<Movie> = emptyList(),
    val continueWatching: List<WatchHistoryItem> = emptyList(),
    val recentChannels: List<Channel> = emptyList(),
    val favoriteMovies: List<Movie> = emptyList(),
    val allMovies: List<Movie> = emptyList(),
    val allSeries: List<Series> = emptyList(),
    val latestMovies: List<Movie> = emptyList(),
    val latestSeries: List<Series> = emptyList(),
    val selectedContent: Any? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.getActivePlaylist().collectLatest { playlist ->
                _state.update { it.copy(playlist = playlist, isLoading = playlist == null) }
                if (playlist != null) loadContent(playlist.id)
            }
        }
    }

    private fun loadContent(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            contentRepository.backfillMissingMovieArtwork(playlistId, limit = 12)
        }
        viewModelScope.launch {
            contentRepository.getRecentMovies(playlistId).collect { movies ->
                _state.update { it.copy(recentMovies = movies) }
            }
        }
        viewModelScope.launch {
            combine(
                contentRepository.getContinueWatching(),
                settingsDataStore.settings
            ) { items, settings ->
                if (settings.continueWatching) items else emptyList()
            }.collect { items ->
                _state.update { it.copy(continueWatching = items) }
            }
        }
        viewModelScope.launch {
            contentRepository.getFavoriteMovies(playlistId).collect { movies ->
                _state.update { it.copy(favoriteMovies = movies) }
            }
        }
        viewModelScope.launch {
            contentRepository.getLatestAddedMovies(playlistId).collect { movies ->
                _state.update { it.copy(latestMovies = movies) }
            }
        }
        viewModelScope.launch {
            contentRepository.getLatestAddedSeries(playlistId).collect { series ->
                _state.update { it.copy(latestSeries = series) }
            }
        }
        viewModelScope.launch {
            contentRepository.getMovies(playlistId).collect { movies ->
                _state.update { it.copy(allMovies = movies.take(50), isLoading = false) }
            }
        }
        viewModelScope.launch {
            contentRepository.getSeries(playlistId).collect { series ->
                _state.update { it.copy(allSeries = series.take(50)) }
            }
        }
        viewModelScope.launch {
            combine(
                contentRepository.getRecentlyWatchedChannels(playlistId),
                settingsDataStore.settings
            ) { channels, settings ->
                if (settings.rememberLastChannel) channels else emptyList()
            }.collect { channels ->
                _state.update { it.copy(recentChannels = channels.take(20)) }
            }
        }
    }

    fun selectContent(content: Any?) = _state.update { it.copy(selectedContent = content) }

    fun repairMovieArtwork(movieId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            contentRepository.repairMovieArtwork(movieId)
        }
    }
}
