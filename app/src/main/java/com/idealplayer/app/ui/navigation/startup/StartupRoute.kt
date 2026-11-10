package com.idealplayer.app.ui.navigation.startup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.idealplayer.app.core.datastore.AppSettings
import com.idealplayer.app.core.datastore.SettingsDataStore
import com.idealplayer.app.core.model.Playlist
import com.idealplayer.app.core.model.PlaylistType
import com.idealplayer.app.data.repository.PlaylistRepository
import com.idealplayer.app.ui.components.LoadingScreen
import com.idealplayer.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class StartupState(
    val isLoading: Boolean = true,
    val targetRoute: String? = null
)

internal data class StartupDecision(
    val targetRoute: String,
    val playlistToActivate: Long? = null
)

internal fun resolveStartupDecision(
    playlists: List<Playlist>,
    settings: AppSettings
): StartupDecision {
    val usablePlaylists = playlists.filter(Playlist::isUsableForStartup)
    if (usablePlaylists.isEmpty()) {
        return StartupDecision(targetRoute = Routes.PLAYLISTS)
    }

    val preferredPlaylist = if (settings.openLastPlaylist && settings.lastPlaylistId > 0L) {
        usablePlaylists.firstOrNull { it.id == settings.lastPlaylistId }
    } else {
        null
    }
    val activePlaylist = usablePlaylists.firstOrNull { it.isActive }
    val fallbackPlaylist = usablePlaylists.first()
    val resolvedPlaylist = preferredPlaylist ?: activePlaylist ?: fallbackPlaylist

    return StartupDecision(
        targetRoute = Routes.HOME,
        playlistToActivate = resolvedPlaylist.takeIf { !it.isActive }?.id
    )
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(StartupState())
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val playlists = playlistRepository.getAllPlaylists().first()
            val settings = settingsDataStore.settings.first()
            val decision = resolveStartupDecision(playlists, settings)

            decision.playlistToActivate?.let { playlistId ->
                playlistRepository.activatePlaylist(playlistId)
            }

            _state.value = StartupState(
                isLoading = false,
                targetRoute = decision.targetRoute
            )
        }
    }
}

@Composable
fun StartupRoute(
    onReady: (String) -> Unit,
    viewModel: StartupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var hasNavigated by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(state.isLoading, state.targetRoute, hasNavigated) {
        val targetRoute = state.targetRoute ?: return@LaunchedEffect
        if (!state.isLoading && !hasNavigated) {
            hasNavigated = true
            onReady(targetRoute)
        }
    }

    LoadingScreen()
}

private fun Playlist.isUsableForStartup(): Boolean {
    val hasSource = when (type) {
        PlaylistType.M3U_URL -> url.isNotBlank()
        PlaylistType.M3U_FILE -> filePath.isNotBlank()
        PlaylistType.XTREAM_CODES ->
            serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }

    val hasPlayableCatalog = channelCount > 0 || movieCount > 0 || seriesCount > 0
    return hasSource && hasPlayableCatalog
}
