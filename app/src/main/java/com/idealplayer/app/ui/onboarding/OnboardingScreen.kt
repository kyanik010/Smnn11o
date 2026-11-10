package com.idealplayer.app.ui.onboarding

import android.graphics.Bitmap
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.idealplayer.app.core.common.Resource
import com.idealplayer.app.core.common.rethrowIfCancellation
import com.idealplayer.app.R
import com.idealplayer.app.core.designsystem.theme.A2Shape
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.core.model.Playlist
import com.idealplayer.app.core.model.PlaylistType
import com.idealplayer.app.data.repository.ConnectedSetupRepository
import com.idealplayer.app.data.repository.ConnectedSetupRepository.Companion.PAIRING_POLL_INTERVAL_MS
import com.idealplayer.app.data.repository.ConnectedSetupRepository.Companion.PAIRING_STATUS_COMPLETED
import com.idealplayer.app.data.repository.ConnectedSetupRepository.Companion.PAIRING_STATUS_ERROR
import com.idealplayer.app.data.repository.ConnectedSetupRepository.Companion.PAIRING_STATUS_EXPIRED
import com.idealplayer.app.data.repository.ConnectedSetupRepository.Companion.PAIRING_STATUS_PENDING
import com.idealplayer.app.data.repository.PlaylistRepository
import com.idealplayer.app.ui.components.rememberTvFocusVisualState
import com.idealplayer.app.ui.components.a2.A2ActionButton
import com.idealplayer.app.ui.components.a2.A2ActionVariant
import com.idealplayer.app.ui.components.a2.A2Badge
import com.idealplayer.app.ui.components.a2.A2BadgeTone
import com.idealplayer.app.ui.components.a2.A2DialogShell
import com.idealplayer.app.ui.components.a2.A2IconButton
import com.idealplayer.app.ui.components.a2.A2ProgressIndicator
import com.idealplayer.app.ui.components.a2.A2StatusSurface
import com.idealplayer.app.ui.components.a2.A2StatusType
import com.idealplayer.app.ui.components.a2.A2TextField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

private const val TV_ONBOARDING_LOG_TAG = "TvOnboarding"

data class OnboardingState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = true,
    val showWelcome: Boolean = true,
    val showAddDialog: Boolean = false,
    val isSyncing: Boolean = false,
    val syncMessage: String = "",
    val syncError: String? = null,
    val pairingCode: String? = null,
    val pairingUrl: String? = null,
    val pairingWebBaseUrl: String? = null,
    val pairingExpiresAtMs: Long = 0L,
    val pairingStatus: String? = null,
    val pairingErrorMessage: String? = null,
    val isRemoteSetupAvailable: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val connectedSetupRepository: ConnectedSetupRepository
) : ViewModel() {
    private val _state = MutableStateFlow(
        OnboardingState(isRemoteSetupAvailable = connectedSetupRepository.isEnabled)
    )
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private var pairingJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                Timber.d("Starting playlist collection")
                playlistRepository.getAllPlaylists().collect { playlists ->
                    Timber.d("Collected playlists: count=%d", playlists.size)
                    _state.update {
                        it.copy(
                            playlists = playlists,
                            isLoading = false,
                            showWelcome = it.showWelcome && playlists.isEmpty()
                        )
                    }
                }
            } catch(e: Exception) {
                Timber.e(e, "Error collecting playlists")
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun startQrPairing(onPlaylistSelected: () -> Unit) {
        if (!connectedSetupRepository.isEnabled) {
            _state.update {
                it.copy(pairingErrorMessage = "Connected IdealPlayer bu build için yapılandırılmamış.")
            }
            return
        }
        pairingJob?.cancel()

        val session = runCatching { connectedSetupRepository.createPairingSession() }
            .getOrElse { error ->
                error.rethrowIfCancellation()
                Timber.w("Connected IdealPlayer pairing session could not be created: %s", error.javaClass.simpleName)
                _state.update {
                    it.copy(
                        pairingStatus = PAIRING_STATUS_ERROR,
                        pairingErrorMessage = "Connected IdealPlayer bağlantı adresleri doğrulanamadı."
                    )
                }
                return
            }

        _state.update {
            it.copy(
                pairingCode = session.code,
                pairingUrl = session.url,
                pairingWebBaseUrl = session.webBaseUrl,
                pairingExpiresAtMs = session.expiresAtMs,
                pairingStatus = PAIRING_STATUS_PENDING,
                pairingErrorMessage = null
            )
        }

        pairingJob = viewModelScope.launch {
            if (!connectedSetupRepository.openPairing(session.code)) {
                _state.update {
                    it.copy(
                        pairingStatus = PAIRING_STATUS_ERROR,
                        pairingErrorMessage = "Connected IdealPlayer sunucusuna bağlanılamadı."
                    )
                }
                return@launch
            }

            var isCompleted = false
            while (System.currentTimeMillis() < session.expiresAtMs) {
                delay(PAIRING_POLL_INTERVAL_MS)
                val response = connectedSetupRepository.pollPairing(session.code) ?: continue

                when (response.status) {
                    PAIRING_STATUS_COMPLETED -> {
                        isCompleted = true
                        val payload = response.payload
                        if (payload == null) {
                            _state.update {
                                it.copy(
                                    pairingStatus = PAIRING_STATUS_ERROR,
                                    pairingErrorMessage = "Sunucudan boş Connected IdealPlayer paketi döndü."
                                )
                            }
                            break
                        }

                        _state.update { it.copy(pairingStatus = PAIRING_STATUS_COMPLETED) }
                        try {
                            val playlist = connectedSetupRepository.saveConnectedPlaylist(session.code, payload)
                            _state.update {
                                it.copy(
                                    pairingCode = null,
                                    pairingUrl = null,
                                    pairingWebBaseUrl = null,
                                    pairingExpiresAtMs = 0L,
                                    pairingStatus = PAIRING_STATUS_COMPLETED,
                                    pairingErrorMessage = null
                                )
                            }
                            selectPlaylist(playlist, onPlaylistSelected)
                        } catch (error: Exception) {
                            error.rethrowIfCancellation()
                            Timber.e("Failed to save Connected IdealPlayer playlist: %s", error.javaClass.simpleName)
                            _state.update {
                                it.copy(
                                    pairingStatus = PAIRING_STATUS_ERROR,
                                    pairingErrorMessage = "Playlist kaydedilemedi: ${error.localizedMessage ?: "Geçersiz paket."}"
                                )
                            }
                        }
                        break
                    }

                    PAIRING_STATUS_ERROR, PAIRING_STATUS_EXPIRED -> {
                        isCompleted = true
                        _state.update {
                            it.copy(
                                pairingStatus = PAIRING_STATUS_ERROR,
                                pairingErrorMessage = if (response.status == PAIRING_STATUS_EXPIRED) {
                                    "Eşleşme süresi doldu."
                                } else {
                                    "Connected IdealPlayer oturumu hata döndürdü."
                                }
                            )
                        }
                        break
                    }
                }
            }

            if (!isCompleted && _state.value.pairingStatus == PAIRING_STATUS_PENDING) {
                _state.update {
                    it.copy(
                        pairingStatus = PAIRING_STATUS_ERROR,
                        pairingErrorMessage = "Eşleşme süresi doldu."
                    )
                }
            }
        }
    }

    fun cancelPairing() {
        pairingJob?.cancel()
        pairingJob = null
        _state.update {
            it.copy(
                pairingCode = null,
                pairingUrl = null,
                pairingWebBaseUrl = null,
                pairingExpiresAtMs = 0L,
                pairingStatus = "idle",
                pairingErrorMessage = null
            )
        }
    }

    fun showAddDialog() = _state.update { it.copy(showAddDialog = true, syncError = null) }
    fun hideAddDialog() = _state.update { it.copy(showAddDialog = false) }

    fun addPlaylist(
        name: String,
        type: PlaylistType,
        url: String,
        server: String,
        username: String,
        password: String,
        epgUrl: String,
        onSuccess: () -> Unit
    ) {
        Timber.d("Adding playlist")
        viewModelScope.launch {
            try {
                val playlist = Playlist(
                    name = name,
                    type = type,
                    url = if (type == PlaylistType.M3U_URL) url else "",
                    filePath = if (type == PlaylistType.M3U_FILE) url else "",
                    epgUrl = epgUrl,
                    serverUrl = if (type == PlaylistType.XTREAM_CODES) server else "",
                    username = if (type == PlaylistType.XTREAM_CODES) username else "",
                    password = if (type == PlaylistType.XTREAM_CODES) password else ""
                )
                val id = playlistRepository.savePlaylist(playlist)
                val savedPlaylist = playlist.copy(id = id)
                _state.update { it.copy(showAddDialog = false) }
                selectPlaylist(savedPlaylist, onSuccess)
            } catch (e: Exception) {
                Timber.e(e, "Failed to add playlist")
                _state.update { it.copy(syncError = e.localizedMessage ?: "Failed to add playlist") }
            }
        }
    }

    fun selectPlaylist(playlist: Playlist, onSelected: () -> Unit) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSyncing = true,
                    syncMessage = "Syncing playlist...",
                    syncError = null
                )
            }
            try {
                when (val syncResult = playlistRepository.syncPlaylist(playlist)) {
                    is Resource.Success -> {
                        playlistRepository.activatePlaylist(playlist.id)
                        _state.update { it.copy(isSyncing = false, syncMessage = "") }
                        onSelected()
                    }

                    is Resource.Error -> {
                        Timber.w(syncResult.throwable, "Playlist sync failed, checking existing content")
                        if (playlistRepository.hasSyncedContent(playlist.id)) {
                            playlistRepository.activatePlaylist(playlist.id)
                            _state.update { it.copy(isSyncing = false, syncMessage = "") }
                            onSelected()
                        } else {
                            _state.update {
                                it.copy(
                                    isSyncing = false,
                                    syncMessage = "",
                                    syncError = syncResult.message
                                )
                            }
                        }
                    }

                    Resource.Loading -> Unit
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync selected playlist")
                _state.update {
                    it.copy(
                        isSyncing = false,
                        syncMessage = "",
                        syncError = e.localizedMessage ?: "Playlist sync failed"
                    )
                }
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist)
        }
    }

    fun dismissWelcome() {
        _state.update { it.copy(showWelcome = false) }
    }

    fun editPlaylist(
        playlist: Playlist,
        name: String,
        type: PlaylistType,
        url: String,
        server: String,
        username: String,
        password: String,
        epgUrl: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                playlistRepository.updatePlaylist(
                    playlist.copy(
                        name = name,
                        type = type,
                        url = if (type == PlaylistType.M3U_URL) url else "",
                        filePath = if (type == PlaylistType.M3U_FILE) url else "",
                        epgUrl = epgUrl,
                        serverUrl = if (type == PlaylistType.XTREAM_CODES) server else "",
                        username = if (type == PlaylistType.XTREAM_CODES) username else "",
                        password = if (type == PlaylistType.XTREAM_CODES) password else "",
                        lastUpdated = System.currentTimeMillis()
                    )
                )
                onSuccess()
            } catch (e: Exception) {
                Timber.e(e, "Failed to edit playlist")
                _state.update { it.copy(syncError = e.localizedMessage ?: "Failed to edit playlist") }
            }
        }
    }

    fun testDraftConnection(
        name: String,
        type: PlaylistType,
        url: String,
        server: String,
        username: String,
        password: String,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            val draft = Playlist(
                name = name.ifBlank { "Test Playlist" },
                type = type,
                url = url,
                serverUrl = server,
                username = username,
                password = password
            )
            val result = playlistRepository.testConnection(draft)
            onResult(result.fold({ it }, { it.message ?: "Connection failed" }))
        }
    }
}

@Composable
fun OnboardingScreen(
    isTv: Boolean,
    onPlaylistSelected: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (isTv) {
        TvOnboardingContent(
            state = state,
            viewModel = viewModel,
            onPlaylistSelected = onPlaylistSelected
        )
    } else if (!state.isLoading && state.playlists.isEmpty() && state.showWelcome) {
        MobileWelcomeContent(
            onStart = {
                viewModel.dismissWelcome()
                viewModel.showAddDialog()
            }
        )
    } else {
        MobileOnboardingContent(
            state = state,
            viewModel = viewModel,
            onPlaylistSelected = onPlaylistSelected
        )
    }
}

@Composable
private fun MobileWelcomeContent(onStart: () -> Unit) {
    var showHowItWorks by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdealPlayerColors.Background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.idealplayer_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.width(132.dp).height(88.dp)
        )
        Text(
            text = stringResource(R.string.a2_welcome_title),
            style = MaterialTheme.typography.displayLarge,
            color = IdealPlayerColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.a2_welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = IdealPlayerColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Image(
            painter = painterResource(R.drawable.a2_artwork_coast),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp)
                .clip(A2Shape.medium)
        )
        Surface(
            modifier = Modifier.widthIn(max = 290.dp),
            shape = A2Shape.medium,
            color = IdealPlayerColors.CardBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, IdealPlayerColors.CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.a2_welcome_notice_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = IdealPlayerColors.TextPrimary
                )
                Text(
                    text = stringResource(R.string.a2_welcome_notice_message),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    ),
                    color = IdealPlayerColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        A2ActionButton(
            text = stringResource(R.string.a2_welcome_start),
            onClick = onStart,
            modifier = Modifier.width(164.dp)
        )
        A2ActionButton(
            text = stringResource(R.string.a2_welcome_how),
            onClick = { showHowItWorks = true },
            variant = A2ActionVariant.Secondary,
            modifier = Modifier.width(164.dp)
        )
    }

    if (showHowItWorks) {
        Dialog(
            onDismissRequest = { showHowItWorks = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            A2DialogShell(
                title = stringResource(R.string.a2_welcome_how_title),
                message = stringResource(R.string.a2_welcome_how_message),
                icon = Icons.Filled.Info,
                modifier = Modifier.padding(16.dp),
                actions = {
                    A2ActionButton(
                        text = stringResource(R.string.a2_action_close),
                        onClick = { showHowItWorks = false },
                        modifier = Modifier.width(164.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun MobileOnboardingContent(
    state: OnboardingState,
    viewModel: OnboardingViewModel,
    onPlaylistSelected: () -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IdealPlayerColors.Background)
    ) {
        SharedOnboardingBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.screenPadding),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(A2Spacing.sm))

            MobilePlaylistTopBar(
                playlistCount = state.playlists.size,
                onAdd = { viewModel.showAddDialog() }
            )

            Spacer(modifier = Modifier.height(A2Spacing.lg))

            if (state.isSyncing) {
                A2StatusSurface(
                    type = A2StatusType.Syncing,
                    title = stringResource(R.string.a2_playlist_preparing_title),
                    message = state.syncMessage,
                    stateDescription = state.syncMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else if (state.isLoading) {
                A2StatusSurface(
                    type = A2StatusType.Loading,
                    title = stringResource(R.string.loading),
                    message = stringResource(R.string.a2_playlists_loading_message),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                state.syncError?.let { error ->
                    SyncErrorPanel(message = error)
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (state.playlists.isEmpty() && !state.isLoading) {
                    EmptyMobilePlaylistState(
                        onAdd = { viewModel.showAddDialog() },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        stringResource(R.string.a2_my_playlists_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = IdealPlayerColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Aktif kaynak, içerik sayıları ve hızlı işlemler tek ekranda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IdealPlayerColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(state.playlists, key = { it.id }) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                isTv = false,
                                onSelect = { viewModel.selectPlaylist(playlist, onPlaylistSelected) },
                                onEdit = { editingPlaylist = playlist },
                                onDelete = { viewModel.deletePlaylist(playlist) }
                            )
                        }
                    }
                }
            }
        }

        if (state.showAddDialog) {
            AddPlaylistDialog(
                isTv = false,
                onDismiss = { viewModel.hideAddDialog() },
                onAdd = { name, type, url, server, user, pass, epgUrl ->
                    viewModel.addPlaylist(name, type, url, server, user, pass, epgUrl, onPlaylistSelected)
                },
                onTest = { name, type, url, server, user, pass, onResult ->
                    viewModel.testDraftConnection(name, type, url, server, user, pass, onResult)
                }
            )
        }

        editingPlaylist?.let { playlist ->
            MobileEditPlaylistDialog(
                playlist = playlist,
                onDismiss = { editingPlaylist = null },
                onSave = { name, type, url, server, user, pass, epgUrl ->
                    viewModel.editPlaylist(
                        playlist = playlist,
                        name = name,
                        type = type,
                        url = url,
                        server = server,
                        username = user,
                        password = pass,
                        epgUrl = epgUrl,
                        onSuccess = { editingPlaylist = null }
                    )
                },
                onTest = { name, type, url, server, user, pass, onResult ->
                    viewModel.testDraftConnection(name, type, url, server, user, pass, onResult)
                }
            )
        }
    }
}

@Composable
private fun TvOnboardingContent(
    state: OnboardingState,
    viewModel: OnboardingViewModel,
    onPlaylistSelected: () -> Unit
) {
    val addPlaylistFocusRequester = remember { FocusRequester() }
    val playlistIds = remember(state.playlists) { state.playlists.map(Playlist::id) }
    val playlistFocusRequesters = remember(playlistIds) {
        playlistIds.associateWith { FocusRequester() }
    }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var hasRequestedEntryFocus by remember { mutableStateOf(false) }
    var waitingToRestoreFocus by remember { mutableStateOf(false) }
    var restorePlaylistFocusId by remember { mutableStateOf<Long?>(null) }
    var deletedPlaylistIdAwaitingFocus by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(
        state.showAddDialog,
        editingPlaylist?.id,
        state.isSyncing,
        state.isLoading,
        state.pairingCode,
        playlistIds,
        waitingToRestoreFocus,
        restorePlaylistFocusId
    ) {
        val modalOrBlockingState =
            state.showAddDialog || editingPlaylist != null || state.isSyncing || state.pairingCode != null

        if (modalOrBlockingState) {
            waitingToRestoreFocus = true
        } else if (!state.isLoading && (!hasRequestedEntryFocus || waitingToRestoreFocus)) {
            // Playlist/EPG flows continue to update after entry. Focusing the first card on
            // each update used to pull focus away from Edit/Delete controls, so only enter once
            // and restore once after a modal or blocking sync state has gone away.
            withFrameNanos { }
            if (state.playlists.isNotEmpty()) {
                val targetId = restorePlaylistFocusId?.takeIf { it in playlistFocusRequesters }
                    ?: state.playlists.first().id
                playlistFocusRequesters[targetId]
                    ?.requestFocusSafely("TV onboarding playlist $targetId")
            } else {
                addPlaylistFocusRequester.requestFocusSafely("TV onboarding add source card")
            }
            hasRequestedEntryFocus = true
            waitingToRestoreFocus = false
            restorePlaylistFocusId = null
        }
    }

    LaunchedEffect(deletedPlaylistIdAwaitingFocus, playlistIds) {
        val deletedPlaylistId = deletedPlaylistIdAwaitingFocus ?: return@LaunchedEffect
        if (deletedPlaylistId !in playlistFocusRequesters) {
            // The focused delete action is removed with its row. Wait until the replacement
            // row/add card has entered composition, then move focus to a live target.
            withFrameNanos { }
            val replacementId = state.playlists.firstOrNull()?.id
            if (replacementId != null) {
                playlistFocusRequesters[replacementId]
                    ?.requestFocusSafely("TV onboarding playlist after delete $replacementId")
            } else {
                addPlaylistFocusRequester.requestFocusSafely("TV onboarding add source after delete")
            }
            deletedPlaylistIdAwaitingFocus = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IdealPlayerColors.Background)
    ) {
        SharedOnboardingBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 40.dp)
        ) {
            TvPlaylistHeader(
                playlists = state.playlists,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 72.dp),
                onAddPlaylist = { viewModel.showAddDialog() }
            )
            Spacer(modifier = Modifier.height(24.dp))

            state.syncError?.let { error ->
                Box(modifier = Modifier.padding(horizontal = 72.dp)) {
                    SyncErrorPanel(message = error)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (state.isLoading) {
                A2StatusSurface(
                    type = A2StatusType.Loading,
                    title = stringResource(R.string.loading),
                    message = stringResource(R.string.a2_playlists_loading_message),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 72.dp)
                )
            } else if (state.playlists.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 72.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (state.isRemoteSetupAvailable) {
                        TvQrPairingCard(
                            modifier = Modifier
                                .weight(1.2f)
                                .focusRequester(addPlaylistFocusRequester),
                            onClick = { viewModel.startQrPairing(onPlaylistSelected) }
                        )
                    }
                    TvAddPlaylistCard(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (state.isRemoteSetupAvailable) Modifier
                                else Modifier.focusRequester(addPlaylistFocusRequester)
                            ),
                        onClick = { viewModel.showAddDialog() }
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 72.dp)
                ) {
                    Text(
                        "Listelerim",
                        style = MaterialTheme.typography.headlineMedium,
                        color = IdealPlayerColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Kumandayla liste seç, bilgileri düzenle veya yeni kaynak ekle.",
                        style = MaterialTheme.typography.titleMedium,
                        color = IdealPlayerColors.TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(start = 72.dp, end = 72.dp, bottom = 24.dp)
                ) {
                    items(state.playlists, key = { it.id }) { playlist ->
                        val playlistModifier = playlistFocusRequesters[playlist.id]
                            ?.let { requester -> Modifier.focusRequester(requester) }
                            ?: Modifier
                        TvPlaylistRow(
                            playlist = playlist,
                            modifier = playlistModifier,
                            onSelect = { viewModel.selectPlaylist(playlist, onPlaylistSelected) },
                            onEdit = {
                                restorePlaylistFocusId = playlist.id
                                editingPlaylist = playlist
                            },
                            onDelete = {
                                deletedPlaylistIdAwaitingFocus = playlist.id
                                viewModel.deletePlaylist(playlist)
                            }
                        )
                    }
                }
            }
        }

        if (state.isSyncing) {
            TvSyncingOverlay(message = state.syncMessage)
        }

        if (state.showAddDialog) {
            AddPlaylistDialog(
                isTv = true,
                onDismiss = { viewModel.hideAddDialog() },
                onAdd = { name, type, url, server, user, pass, epgUrl ->
                    viewModel.addPlaylist(name, type, url, server, user, pass, epgUrl, onPlaylistSelected)
                },
                onTest = { name, type, url, server, user, pass, onResult ->
                    viewModel.testDraftConnection(name, type, url, server, user, pass, onResult)
                },
                onQrClick = if (state.isRemoteSetupAvailable) {
                    {
                        viewModel.hideAddDialog()
                        viewModel.startQrPairing(onPlaylistSelected)
                    }
                } else null
            )
        }

        if (state.pairingCode != null && state.pairingUrl != null && state.pairingWebBaseUrl != null) {
            TvQrPairingDialog(
                pairingCode = state.pairingCode,
                pairingUrl = state.pairingUrl,
                pairingWebBaseUrl = state.pairingWebBaseUrl,
                pairingStatus = state.pairingStatus ?: "pending",
                errorMessage = state.pairingErrorMessage,
                onDismiss = { viewModel.cancelPairing() }
            )
        }

        editingPlaylist?.let { playlist ->
            EditPlaylistDialog(
                playlist = playlist,
                onDismiss = { editingPlaylist = null },
                onSave = { name, type, url, server, user, pass, epgUrl ->
                    viewModel.editPlaylist(
                        playlist = playlist,
                        name = name,
                        type = type,
                        url = url,
                        server = server,
                        username = user,
                        password = pass,
                        epgUrl = epgUrl,
                        onSuccess = { editingPlaylist = null }
                    )
                },
                onTest = { name, type, url, server, user, pass, onResult ->
                    viewModel.testDraftConnection(name, type, url, server, user, pass, onResult)
                }
            )
        }
    }
}

@Composable
private fun SyncErrorPanel(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = IdealPlayerColors.Error.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = IdealPlayerColors.Error.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = IdealPlayerColors.Error,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = IdealPlayerColors.Error
            )
        }
    }
}

@Composable
private fun SharedOnboardingBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        IdealPlayerColors.Primary.copy(alpha = 0.05f),
                        IdealPlayerColors.Background,
                        IdealPlayerColors.Secondary.copy(alpha = 0.03f)
                    )
                )
            )
    )
}

@Composable
private fun MobilePlaylistTopBar(
    playlistCount: Int,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (playlistCount > 0) {
                    pluralStringResource(
                        R.plurals.a2_playlist_sources_ready,
                        playlistCount,
                        playlistCount
                    )
                } else {
                    stringResource(R.string.a2_add_own_playlists)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = IdealPlayerColors.TextTertiary
            )
        }

        A2ActionButton(
            text = stringResource(R.string.a2_action_new),
            icon = Icons.Filled.Add,
            onClick = onAdd,
            modifier = Modifier
                .widthIn(min = 96.dp)
        )
    }
}

@Composable
private fun EmptyMobilePlaylistState(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        A2StatusSurface(
            type = A2StatusType.Empty,
            title = stringResource(R.string.a2_no_playlists_title),
            message = stringResource(R.string.a2_no_playlists_message),
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            actionLabel = stringResource(R.string.a2_add_new_playlist),
            onAction = onAdd,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    isTv: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    if (isTv) {
        TvPlaylistRow(
            playlist = playlist,
            onSelect = onSelect,
            onDelete = onDelete
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 176.dp)
            .clip(A2Shape.large)
            .background(if (playlist.isActive) IdealPlayerColors.SurfaceSelected else IdealPlayerColors.CardBackground)
            .border(
                width = if (playlist.isActive) 2.dp else 1.dp,
                color = if (playlist.isActive) IdealPlayerColors.SelectedBorder else IdealPlayerColors.CardBorder,
                shape = A2Shape.large
            )
            .clickable(onClick = onSelect)
            .padding(A2Spacing.lg)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = A2Shape.medium,
                    color = IdealPlayerColors.SurfaceElevated,
                    modifier = Modifier.size(58.dp)
                ) {
                    Icon(
                        imageVector = playlistTypeIcon(playlist.type),
                        contentDescription = null,
                        tint = IdealPlayerColors.Secondary,
                        modifier = Modifier.padding(15.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = IdealPlayerColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        playlistTypeLabel(playlist.type),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IdealPlayerColors.TextSecondary
                    )
                }

                if (playlist.isActive) {
                    A2Badge(
                        text = stringResource(R.string.a2_state_active),
                        tone = A2BadgeTone.Selected,
                        leadingIcon = Icons.Filled.CheckCircle,
                        stateDescription = stringResource(R.string.a2_selected_playlist_state)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PlaylistStatPill(
                    label = stringResource(R.string.a2_channel_singular),
                    value = playlist.channelCount,
                    modifier = Modifier.weight(1f)
                )
                PlaylistStatPill(
                    label = stringResource(R.string.a2_movie_singular),
                    value = playlist.movieCount,
                    modifier = Modifier.weight(1f)
                )
                PlaylistStatPill(
                    label = stringResource(R.string.a2_series_singular),
                    value = playlist.seriesCount,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                A2ActionButton(
                    text = stringResource(R.string.a2_action_open),
                    icon = Icons.Filled.PlayArrow,
                    onClick = onSelect,
                    modifier = Modifier.weight(1f)
                )

                if (onEdit != null) {
                    PlaylistIconAction(
                        icon = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.edit),
                        onClick = onEdit
                    )
                }

                PlaylistIconAction(
                    icon = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                    isDestructive = true,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun PlaylistStatPill(
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = IdealPlayerColors.Background.copy(alpha = 0.38f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = IdealPlayerColors.TextPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = IdealPlayerColors.TextTertiary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PlaylistIconAction(
    icon: ImageVector,
    contentDescription: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    A2IconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        variant = if (isDestructive) {
            A2ActionVariant.Destructive
        } else {
            A2ActionVariant.Ghost
        },
        size = 48.dp
    )
}

@Composable
private fun TvPlaylistHeader(
    playlists: List<Playlist>,
    modifier: Modifier = Modifier,
    addButtonModifier: Modifier = Modifier,
    onAddPlaylist: () -> Unit
) {
    val totalChannels = playlists.sumOf { it.channelCount }
    val totalMovies = playlists.sumOf { it.movieCount }
    val totalSeries = playlists.sumOf { it.seriesCount }
    val activePlaylist = playlists.firstOrNull { it.isActive }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = IdealPlayerColors.CardBorder.copy(alpha = 0.75f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            IdealPlayerColors.SurfaceVariant.copy(alpha = 0.95f),
                            IdealPlayerColors.CardBackground.copy(alpha = 0.98f)
                        )
                    )
                )
                .padding(horizontal = 32.dp, vertical = 22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.a2_brand_prefix),
                                style = MaterialTheme.typography.displaySmall,
                                color = IdealPlayerColors.Primary
                            )
                            Text(
                                stringResource(R.string.a2_brand_suffix),
                                style = MaterialTheme.typography.displaySmall,
                                color = IdealPlayerColors.Secondary
                            )
                        }
                        Text(
                            text = if (playlists.isEmpty()) {
                                stringResource(R.string.a2_tv_add_media_sources)
                            } else {
                                stringResource(R.string.a2_tv_playlist_center)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = IdealPlayerColors.TextPrimary
                        )
                        Text(
                            text = activePlaylist?.let {
                                stringResource(R.string.a2_active_source_format, it.name)
                            } ?: stringResource(R.string.a2_add_source_remote_hint),
                            style = MaterialTheme.typography.titleMedium,
                            color = IdealPlayerColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    TvActionButton(
                        text = stringResource(R.string.a2_new_playlist),
                        icon = Icons.Filled.Add,
                        onClick = onAddPlaylist,
                        isSecondary = true,
                        modifier = addButtonModifier.width(198.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TvDashboardMetric(
                        value = playlists.size.toString(),
                        label = stringResource(R.string.a2_source_singular),
                        modifier = Modifier.weight(1f)
                    )
                    TvDashboardMetric(
                        value = totalChannels.toString(),
                        label = stringResource(R.string.a2_channel_singular),
                        modifier = Modifier.weight(1f)
                    )
                    TvDashboardMetric(
                        value = totalMovies.toString(),
                        label = stringResource(R.string.a2_movie_singular),
                        modifier = Modifier.weight(1f)
                    )
                    TvDashboardMetric(
                        value = totalSeries.toString(),
                        label = stringResource(R.string.a2_series_singular),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TvDashboardMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = IdealPlayerColors.Background.copy(alpha = 0.42f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = IdealPlayerColors.CardBorder.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = IdealPlayerColors.TextPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = IdealPlayerColors.TextTertiary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TvPlaylistRow(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TvFocusableCard(
            modifier = modifier.weight(1f),
            isSelected = playlist.isActive,
            onClick = onSelect
        ) { isFocused ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(IdealPlayerColors.Primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = playlistTypeIcon(playlist.type),
                                contentDescription = null,
                                tint = IdealPlayerColors.Primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(22.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = IdealPlayerColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (playlist.isActive) {
                                    TvActivePill()
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = playlistTypeLabel(playlist.type),
                                style = MaterialTheme.typography.titleMedium,
                                color = IdealPlayerColors.TextSecondary
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TvPlaylistStat(
                            value = playlist.channelCount,
                            label = stringResource(R.string.a2_channel_singular),
                            modifier = Modifier.weight(1f)
                        )
                        TvPlaylistStat(
                            value = playlist.movieCount,
                            label = stringResource(R.string.a2_movie_singular),
                            modifier = Modifier.weight(1f)
                        )
                        TvPlaylistStat(
                            value = playlist.seriesCount,
                            label = stringResource(R.string.a2_series_singular),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (isFocused) {
                    Spacer(modifier = Modifier.width(16.dp))
                    TvInlineHint(stringResource(R.string.a2_hint_ok_open), isHighlighted = true)
                }
            }
        }

        // Right Actions Area (outside the card)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (onEdit != null) {
                TvPlaylistMiniAction(
                    icon = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.edit),
                    onClick = onEdit
                )
            }
            TvPlaylistMiniAction(
                icon = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.delete),
                isDestructive = true,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun TvPlaylistMiniAction(
    icon: ImageVector,
    contentDescription: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    A2IconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        variant = if (isDestructive) {
            A2ActionVariant.Destructive
        } else {
            A2ActionVariant.Ghost
        },
        size = 56.dp
    )
}

@Composable
private fun TvActivePill() {
    A2Badge(
        text = stringResource(R.string.a2_state_active),
        tone = A2BadgeTone.Selected,
        leadingIcon = Icons.Filled.CheckCircle,
        stateDescription = stringResource(R.string.a2_selected_playlist_state)
    )
}

@Composable
private fun TvPlaylistStat(
    value: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = IdealPlayerColors.Background.copy(alpha = 0.38f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = IdealPlayerColors.TextPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = IdealPlayerColors.TextTertiary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TvAddPlaylistCard(
    modifier: Modifier,
    onClick: () -> Unit
) {
    TvFocusableCard(
        modifier = modifier,
        onClick = onClick
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(IdealPlayerColors.Primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = null,
                    tint = IdealPlayerColors.Primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.a2_add_new_playlist),
                    style = MaterialTheme.typography.headlineMedium,
                    color = IdealPlayerColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.a2_tv_add_playlist_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = IdealPlayerColors.TextSecondary
                )
            }
            TvInlineHint(stringResource(R.string.a2_hint_ok_add), isHighlighted = isFocused)
        }
    }
}

@Composable
private fun AddPlaylistDialog(
    isTv: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String, PlaylistType, String, String, String, String, String) -> Unit,
    onTest: (String, PlaylistType, String, String, String, String, (String) -> Unit) -> Unit,
    onQrClick: (() -> Unit)? = null
) {
    if (isTv) {
        TvAddPlaylistDialog(
            onDismiss = onDismiss,
            onAdd = onAdd,
            onTest = onTest,
            onQrClick = onQrClick
        )
    } else {
        MobileAddPlaylistDialog(
            onDismiss = onDismiss,
            onAdd = onAdd,
            onTest = onTest
        )
    }
}

@Composable
private fun EditPlaylistDialog(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onSave: (String, PlaylistType, String, String, String, String, String) -> Unit,
    onTest: (String, PlaylistType, String, String, String, String, (String) -> Unit) -> Unit
) {
    TvAddPlaylistDialog(
        initialPlaylist = playlist,
        title = stringResource(R.string.a2_edit_playlist_title),
        subtitle = stringResource(R.string.a2_edit_playlist_tv_description),
        primaryActionText = stringResource(R.string.a2_action_save_changes),
        onDismiss = onDismiss,
        onAdd = onSave,
        onTest = onTest
    )
}

@Composable
private fun MobileAddPlaylistDialog(
    initialPlaylist: Playlist? = null,
    title: String? = null,
    primaryActionText: String? = null,
    onDismiss: () -> Unit,
    onAdd: (String, PlaylistType, String, String, String, String, String) -> Unit,
    onTest: (String, PlaylistType, String, String, String, String, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val dialogTitle = title ?: stringResource(R.string.add_playlist)
    val dialogPrimaryActionText = primaryActionText ?: stringResource(R.string.a2_action_save_continue)
    var selectedType by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.type ?: PlaylistType.M3U_URL)
    }
    var name by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.name.orEmpty())
    }
    var url by remember(initialPlaylist?.id) {
        mutableStateOf(
            when (initialPlaylist?.type) {
                PlaylistType.M3U_FILE -> initialPlaylist.filePath
                else -> initialPlaylist?.url.orEmpty()
            }
        )
    }
    var server by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.serverUrl.orEmpty())
    }
    var username by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.username.orEmpty())
    }
    // Existing credentials are retained for an unchanged edit, but never re-rendered.
    val existingPassword = remember(initialPlaylist?.id) { initialPlaylist?.password.orEmpty() }
    var password by remember(initialPlaylist?.id) { mutableStateOf("") }
    var epgUrl by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.epgUrl.orEmpty())
    }
    var isTesting by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            url = uri.toString()
            testMessage = null
        }
    }

    val effectivePassword = if (
        selectedType == PlaylistType.XTREAM_CODES &&
        password.isBlank() &&
        initialPlaylist?.type == PlaylistType.XTREAM_CODES
    ) {
        existingPassword
    } else {
        password
    }
    val canSave = remember(selectedType, name, url, server, username, effectivePassword) {
        isDraftValid(
            name = name,
            type = selectedType,
            url = url,
            server = server,
            username = username,
            password = effectivePassword
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        A2DialogShell(
            title = dialogTitle,
            stateDescription = dialogTitle,
            modifier = Modifier.padding(A2Spacing.md),
            content = {
                Spacer(Modifier.height(A2Spacing.md))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(A2Spacing.sm)
                ) {
                    Text(
                        stringResource(R.string.a2_playlist_type_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = IdealPlayerColors.TextSecondary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(A2Spacing.xs)) {
                        PlaylistType.entries.forEach { type ->
                            A2ActionButton(
                                text = playlistTypeLabel(type),
                                icon = playlistTypeIcon(type),
                                iconContentDescription = null,
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                modifier = Modifier.fillMaxWidth(),
                                variant = A2ActionVariant.Secondary
                            )
                        }
                    }

                    DialogTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(R.string.playlist_name),
                        imeAction = ImeAction.Next
                    )

                    when (selectedType) {
                        PlaylistType.M3U_URL -> {
                            DialogTextField(
                                value = url,
                                onValueChange = { url = it },
                                label = stringResource(R.string.m3u_url),
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            )
                        }

                        PlaylistType.M3U_FILE -> {
                            A2ActionButton(
                                text = if (url.isBlank()) {
                                    stringResource(R.string.a2_action_choose_playlist_file)
                                } else {
                                    stringResource(R.string.a2_action_change_playlist_file)
                                },
                                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                                iconContentDescription = null,
                                onClick = {
                                    filePicker.launch(
                                        arrayOf(
                                            "application/vnd.apple.mpegurl",
                                            "audio/x-mpegurl",
                                            "text/plain",
                                            "application/octet-stream"
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                variant = A2ActionVariant.Secondary
                            )
                            if (url.isNotBlank()) {
                                A2Badge(
                                    text = stringResource(R.string.a2_file_selected),
                                    tone = A2BadgeTone.Success
                                )
                            }
                        }

                        PlaylistType.XTREAM_CODES -> {
                            DialogTextField(
                                value = server,
                                onValueChange = { server = it },
                                label = stringResource(R.string.server_url),
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            )
                            DialogTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = stringResource(R.string.username),
                                imeAction = ImeAction.Next
                            )
                            DialogTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = stringResource(R.string.password),
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next,
                                visualTransformation = PasswordVisualTransformation()
                            )
                        }
                    }

                    DialogTextField(
                        value = epgUrl,
                        onValueChange = { epgUrl = it },
                        label = stringResource(R.string.a2_epg_url_optional),
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    )

                    testMessage?.let { message ->
                        Surface(
                            shape = A2Shape.medium,
                            color = if (isPositiveTestMessage(message)) {
                                IdealPlayerColors.Success.copy(alpha = 0.12f)
                            } else {
                                IdealPlayerColors.Error.copy(alpha = 0.12f)
                            }
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isPositiveTestMessage(message)) {
                                    IdealPlayerColors.Success
                                } else {
                                    IdealPlayerColors.Error
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            },
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm)
                ) {
                    A2ActionButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        variant = A2ActionVariant.Ghost
                    )
                    A2ActionButton(
                        text = if (isTesting) {
                            stringResource(R.string.a2_action_testing)
                        } else {
                            stringResource(R.string.test_connection)
                        },
                        enabled = canSave && !isTesting,
                        onClick = {
                            isTesting = true
                            onTest(name, selectedType, url, server, username, effectivePassword) { result ->
                                testMessage = result
                                isTesting = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        variant = A2ActionVariant.Secondary,
                        loading = isTesting
                    )
                }
                Spacer(Modifier.height(A2Spacing.sm))
                A2ActionButton(
                    text = dialogPrimaryActionText,
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (canSave) {
                            onAdd(name, selectedType, url, server, username, effectivePassword, epgUrl)
                        }
                    }
                )
            }
        )
    }
}

@Composable
private fun MobileEditPlaylistDialog(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onSave: (String, PlaylistType, String, String, String, String, String) -> Unit,
    onTest: (String, PlaylistType, String, String, String, String, (String) -> Unit) -> Unit
) {
    MobileAddPlaylistDialog(
        initialPlaylist = playlist,
        title = stringResource(R.string.a2_edit_playlist_title),
        primaryActionText = stringResource(R.string.save),
        onDismiss = onDismiss,
        onAdd = onSave,
        onTest = onTest
    )
}

@Composable
private fun TvAddPlaylistDialog(
    initialPlaylist: Playlist? = null,
    title: String? = null,
    subtitle: String? = null,
    primaryActionText: String? = null,
    onDismiss: () -> Unit,
    onAdd: (String, PlaylistType, String, String, String, String, String) -> Unit,
    onTest: (String, PlaylistType, String, String, String, String, (String) -> Unit) -> Unit,
    onQrClick: (() -> Unit)? = null
) {
    val dialogTitle = title ?: stringResource(R.string.a2_add_playlist_tv_title)
    val dialogSubtitle = subtitle ?: stringResource(R.string.a2_add_playlist_tv_description)
    val dialogPrimaryActionText = primaryActionText ?: stringResource(R.string.a2_action_save_continue)
    var selectedType by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.type ?: PlaylistType.M3U_URL)
    }
    var name by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.name.orEmpty())
    }
    var url by remember(initialPlaylist?.id) {
        mutableStateOf(
            when (initialPlaylist?.type) {
                PlaylistType.M3U_FILE -> initialPlaylist.filePath
                else -> initialPlaylist?.url.orEmpty()
            }
        )
    }
    var server by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.serverUrl.orEmpty())
    }
    var username by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.username.orEmpty())
    }
    // Do not reveal a stored credential when editing. Blank means "leave unchanged".
    val existingPassword = remember(initialPlaylist?.id) { initialPlaylist?.password.orEmpty() }
    var password by remember(initialPlaylist?.id) { mutableStateOf("") }
    var epgUrl by remember(initialPlaylist?.id) {
        mutableStateOf(initialPlaylist?.epgUrl.orEmpty())
    }
    var isTesting by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var shouldMoveFocusToForm by remember { mutableStateOf(false) }

    val typeFocusRequester = remember { FocusRequester() }
    val nameFocusRequester = remember { FocusRequester() }

    val effectivePassword = if (
        selectedType == PlaylistType.XTREAM_CODES &&
        password.isBlank() &&
        initialPlaylist?.type == PlaylistType.XTREAM_CODES
    ) {
        existingPassword
    } else {
        password
    }
    val canSave = remember(selectedType, name, url, server, username, effectivePassword) {
        isDraftValid(
            name = name,
            type = selectedType,
            url = url,
            server = server,
            username = username,
            password = effectivePassword
        )
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        typeFocusRequester.requestFocusSafely("TV add playlist dialog provider type")
    }

    LaunchedEffect(selectedType, shouldMoveFocusToForm) {
        if (shouldMoveFocusToForm) {
            withFrameNanos { }
            nameFocusRequester.requestFocusSafely("TV add playlist dialog form")
            shouldMoveFocusToForm = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 1180.dp),
            shape = RoundedCornerShape(32.dp),
            color = IdealPlayerColors.Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            dialogTitle,
                            style = MaterialTheme.typography.displaySmall,
                            color = IdealPlayerColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            dialogSubtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = IdealPlayerColors.TextSecondary
                        )
                    }
                    TvInlineHint(stringResource(R.string.a2_dialog_focus_locked))
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.a2_provider_type_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = IdealPlayerColors.TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        PlaylistType.entries.forEachIndexed { index, type ->
                            TvTypeCard(
                                modifier = if (index == 0) {
                                    Modifier
                                        .weight(1f)
                                        .focusRequester(typeFocusRequester)
                                } else {
                                    Modifier.weight(1f)
                                },
                                type = type,
                                isSelected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    shouldMoveFocusToForm = true
                                    testMessage = null
                                }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        stringResource(R.string.a2_playlist_details_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = IdealPlayerColors.TextPrimary
                    )
                    TvDialogTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            testMessage = null
                        },
                        label = stringResource(R.string.playlist_name),
                        placeholder = stringResource(R.string.a2_playlist_name_placeholder),
                        focusRequester = nameFocusRequester
                    )

                    when (selectedType) {
                        PlaylistType.M3U_URL -> {
                            TvDialogTextField(
                                value = url,
                                onValueChange = {
                                    url = it
                                    testMessage = null
                                },
                                label = stringResource(R.string.m3u_url),
                                placeholder = stringResource(R.string.a2_m3u_url_placeholder),
                                keyboardType = KeyboardType.Uri
                            )
                        }

                        PlaylistType.M3U_FILE -> {
                            TvDialogTextField(
                                value = url,
                                onValueChange = {
                                    url = it
                                    testMessage = null
                                },
                                label = stringResource(R.string.a2_local_file_path_label),
                                placeholder = stringResource(R.string.a2_local_file_path_placeholder)
                            )
                        }

                        PlaylistType.XTREAM_CODES -> {
                            TvDialogTextField(
                                value = server,
                                onValueChange = {
                                    server = it
                                    testMessage = null
                                },
                                label = stringResource(R.string.server_url),
                                placeholder = stringResource(R.string.a2_server_url_placeholder),
                                keyboardType = KeyboardType.Uri
                            )
                            TvDialogTextField(
                                value = username,
                                onValueChange = {
                                    username = it
                                    testMessage = null
                                },
                                label = stringResource(R.string.username),
                                placeholder = stringResource(R.string.a2_username_placeholder)
                            )
                            TvDialogTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    testMessage = null
                                },
                                label = stringResource(R.string.password),
                                placeholder = stringResource(R.string.a2_password_placeholder),
                                keyboardType = KeyboardType.Password,
                                visualTransformation = PasswordVisualTransformation()
                            )
                        }
                    }

                    TvDialogTextField(
                        value = epgUrl,
                        onValueChange = {
                            epgUrl = it
                            testMessage = null
                        },
                        label = stringResource(R.string.a2_epg_url_optional),
                        placeholder = stringResource(R.string.a2_epg_url_placeholder),
                        keyboardType = KeyboardType.Uri
                    )
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = IdealPlayerColors.Background.copy(alpha = 0.42f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = providerHelperTitle(selectedType),
                            style = MaterialTheme.typography.titleLarge,
                            color = IdealPlayerColors.TextPrimary
                        )
                        Text(
                            text = providerHelperText(selectedType),
                            style = MaterialTheme.typography.bodyMedium,
                            color = IdealPlayerColors.TextSecondary
                        )
                    }
                }

                if (testMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isPositiveTestMessage(testMessage)) {
                            IdealPlayerColors.Success.copy(alpha = 0.12f)
                        } else {
                            IdealPlayerColors.Error.copy(alpha = 0.12f)
                        }
                    ) {
                        Text(
                            text = testMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isPositiveTestMessage(testMessage)) {
                                IdealPlayerColors.Success
                            } else {
                                IdealPlayerColors.Error
                            },
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (onQrClick != null && initialPlaylist == null) {
                        TvActionButton(
                            text = stringResource(R.string.a2_action_remote_add),
                            onClick = onQrClick,
                            modifier = Modifier.weight(1f),
                            isSecondary = true
                        )
                    }
                    TvActionButton(
                        text = if (isTesting) {
                            stringResource(R.string.a2_action_testing)
                        } else {
                            stringResource(R.string.test_connection)
                        },
                        onClick = {
                            isTesting = true
                            onTest(name, selectedType, url, server, username, effectivePassword) { result ->
                                testMessage = result
                                isTesting = false
                            }
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f)
                    )
                    TvActionButton(
                        text = dialogPrimaryActionText,
                        onClick = {
                            onAdd(name, selectedType, url, server, username, effectivePassword, epgUrl)
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f)
                    )
                    TvActionButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        isSecondary = true
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    A2TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction)
    )
}

@Composable
private fun TvDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    focusRequester: FocusRequester? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    A2TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next)
    )
}

@Composable
private fun TvTypeCard(
    modifier: Modifier = Modifier,
    type: PlaylistType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }

    val icon = playlistTypeIcon(type)
    val title = playlistTypeLabel(type)
    val subtitle = when (type) {
        PlaylistType.M3U_URL -> stringResource(R.string.a2_type_m3u_url_summary)
        PlaylistType.XTREAM_CODES -> stringResource(R.string.a2_type_xtream_summary)
        PlaylistType.M3U_FILE -> stringResource(R.string.a2_type_local_file_summary)
    }

    val focusState = rememberTvFocusVisualState(
        isFocused = isFocused,
        isSelected = isSelected,
        defaultSurface = IdealPlayerColors.CardBackground,
        selectedSurface = IdealPlayerColors.SurfaceSelected,
        focusedSurface = IdealPlayerColors.SurfaceFocus
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = focusState.scale
                scaleY = focusState.scale
                shadowElevation = focusState.shadowElevation.toPx()
                spotShadowColor = focusState.glowColor
                ambientShadowColor = focusState.glowColor
            }
            .clip(A2Shape.large)
            .background(focusState.backgroundColor)
            .border(focusState.borderWidth, focusState.borderColor, A2Shape.large)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(IdealPlayerColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = focusState.accentColor.takeUnless { it == Color.Transparent }
                        ?: IdealPlayerColors.TextSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = focusState.contentColor,
                    fontWeight = if (isFocused || isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = focusState.secondaryContentColor
                )
            }
        }
    }
}

@Composable
private fun TvFocusableCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    content: @Composable (isFocused: Boolean) -> Unit
) {
    var hasFocus by remember { mutableStateOf(false) }
    val focusState = rememberTvFocusVisualState(
        isFocused = hasFocus,
        isSelected = isSelected,
        defaultSurface = IdealPlayerColors.CardBackground,
        selectedSurface = IdealPlayerColors.SurfaceSelected,
        focusedSurface = IdealPlayerColors.SurfaceFocus
    )

    Box(
        modifier = modifier
            .onFocusChanged { hasFocus = it.hasFocus }
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .graphicsLayer {
                scaleX = focusState.scale
                scaleY = focusState.scale
                shadowElevation = focusState.shadowElevation.toPx()
                spotShadowColor = focusState.glowColor
                ambientShadowColor = focusState.glowColor
            }
            .clip(A2Shape.large)
            .background(focusState.backgroundColor)
            .border(
                width = if (!hasFocus && !isSelected) 1.dp else focusState.borderWidth,
                color = if (!hasFocus && !isSelected) IdealPlayerColors.CardBorder else focusState.borderColor,
                shape = A2Shape.large
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (hasFocus || isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(4.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (hasFocus) IdealPlayerColors.FocusBorder else IdealPlayerColors.Secondary)
                )
            }
        }
        content(hasFocus)
    }
}

@Composable
private fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isSecondary: Boolean = false,
    isDestructive: Boolean = false
) {
    A2ActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        variant = when {
            isDestructive -> A2ActionVariant.Destructive
            isSecondary -> A2ActionVariant.Secondary
            else -> A2ActionVariant.Primary
        }
    )
}

@Composable
private fun TvInlineHint(
    text: String,
    isHighlighted: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (isHighlighted) IdealPlayerColors.Primary.copy(alpha = 0.24f) else IdealPlayerColors.Secondary.copy(alpha = 0.15f),
        border = if (isHighlighted) androidx.compose.foundation.BorderStroke(1.dp, IdealPlayerColors.Primary) else null
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (isHighlighted) IdealPlayerColors.Primary else IdealPlayerColors.Secondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun TvSyncingOverlay(message: String) {
    val overlayFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        overlayFocusRequester.requestFocusSafely("TV playlist sync overlay")
    }

    // A real dialog places the blocking state in its own focus window. Keeping this as an
    // in-tree overlay allowed the drawer's preview handler to see Menu before the overlay,
    // which could open navigation behind an active playlist sync.
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .focusRequester(overlayFocusRequester)
                .focusable()
                // Sync has no actionable controls. Consume both key phases so no remote key
                // leaks to the obscured drawer/content while the source is being updated.
                .onPreviewKeyEvent { true },
            contentAlignment = Alignment.Center
        ) {
            A2StatusSurface(
                type = A2StatusType.Syncing,
                title = stringResource(R.string.a2_playlist_preparing_title),
                message = message,
                modifier = Modifier
                    .padding(A2Spacing.xl)
                    .widthIn(min = 320.dp, max = 560.dp)
            )
        }
    }
}

private fun playlistTypeIcon(type: PlaylistType): ImageVector {
    return when (type) {
        PlaylistType.M3U_URL -> Icons.Filled.Link
        PlaylistType.M3U_FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
        PlaylistType.XTREAM_CODES -> Icons.Filled.Dns
    }
}

@Composable
private fun playlistTypeLabel(type: PlaylistType): String {
    return when (type) {
        PlaylistType.M3U_URL -> stringResource(R.string.a2_playlist_type_m3u_url)
        PlaylistType.M3U_FILE -> stringResource(R.string.a2_playlist_type_local_file)
        PlaylistType.XTREAM_CODES -> stringResource(R.string.a2_playlist_type_xtream)
    }
}

private fun playlistStats(playlist: Playlist): String {
    val stats = buildList {
        if (playlist.channelCount > 0) add("${playlist.channelCount} channels")
        if (playlist.movieCount > 0) add("${playlist.movieCount} movies")
        if (playlist.seriesCount > 0) add("${playlist.seriesCount} series")
    }
    return stats.ifEmpty { listOf("No synced content yet") }.joinToString("  •  ")
}

@Composable
private fun providerHelperTitle(type: PlaylistType): String {
    return when (type) {
        PlaylistType.M3U_URL -> stringResource(R.string.a2_helper_m3u_title)
        PlaylistType.XTREAM_CODES -> stringResource(R.string.a2_helper_xtream_title)
        PlaylistType.M3U_FILE -> stringResource(R.string.a2_helper_file_title)
    }
}

@Composable
private fun providerHelperText(type: PlaylistType): String {
    return when (type) {
        PlaylistType.M3U_URL -> stringResource(R.string.a2_helper_m3u_text)
        PlaylistType.XTREAM_CODES -> stringResource(R.string.a2_helper_xtream_text)
        PlaylistType.M3U_FILE -> stringResource(R.string.a2_helper_file_text)
    }
}

private fun isDraftValid(
    name: String,
    type: PlaylistType,
    url: String,
    server: String,
    username: String,
    password: String
): Boolean {
    if (name.isBlank()) return false

    return when (type) {
        PlaylistType.M3U_URL,
        PlaylistType.M3U_FILE -> url.isNotBlank()

        PlaylistType.XTREAM_CODES -> server.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    }
}

private fun isPositiveTestMessage(message: String?): Boolean {
    if (message == null) return false
    val normalized = message.lowercase()
    return "successful" in normalized || "ready" in normalized || "active" in normalized
}

private fun FocusRequester.requestFocusSafely(reason: String) {
    runCatching { requestFocus() }
        .onFailure { error ->
            Timber.tag(TV_ONBOARDING_LOG_TAG).w(error, "Unable to request focus for %s", reason)
        }
}

@Composable
private fun TvQrPairingCard(
    modifier: Modifier,
    onClick: () -> Unit
) {
    TvFocusableCard(
        modifier = modifier,
        onClick = onClick
    ) { isFocused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(IdealPlayerColors.Primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Link,
                    contentDescription = null,
                    tint = IdealPlayerColors.Primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.a2_qr_remote_add_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = IdealPlayerColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.a2_qr_remote_add_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = IdealPlayerColors.TextSecondary
                )
            }
            TvInlineHint(stringResource(R.string.a2_hint_ok_start), isHighlighted = isFocused)
        }
    }
}

@Composable
private fun TvQrPairingDialog(
    pairingCode: String,
    pairingUrl: String,
    pairingWebBaseUrl: String,
    pairingStatus: String,
    errorMessage: String?,
    onDismiss: () -> Unit
) {
    val qrCode = remember(pairingUrl) { createPairingQrCode(pairingUrl) }

    val closeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        closeFocusRequester.requestFocusSafely("TV QR pairing dialog close button")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .widthIn(max = 880.dp),
            shape = RoundedCornerShape(32.dp),
            color = IdealPlayerColors.Surface,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = IdealPlayerColors.CardBorder.copy(alpha = 0.75f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Header
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.a2_connected_idealplayer_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = IdealPlayerColors.TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.a2_connected_idealplayer_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = IdealPlayerColors.TextSecondary
                    )
                }

                // Row containing QR and code details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: QR Code Box
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(IdealPlayerColors.SurfaceVariant)
                            .border(1.dp, IdealPlayerColors.CardBorder, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        qrCode?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.a2_connected_idealplayer_qr_description),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        }
                    }

                    // Right: Code displays and status
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.a2_tv_pairing_code),
                                style = MaterialTheme.typography.titleMedium,
                                color = IdealPlayerColors.TextTertiary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pairingCode,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 42.sp,
                                    letterSpacing = 4.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                                ),
                                color = IdealPlayerColors.Primary
                            )
                        }

                        Column {
                            Text(
                                text = stringResource(R.string.a2_address),
                                style = MaterialTheme.typography.titleMedium,
                                color = IdealPlayerColors.TextTertiary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = pairingWebBaseUrl,
                                style = MaterialTheme.typography.bodyLarge,
                                color = IdealPlayerColors.TextSecondary
                            )
                        }

                        // Connection Status panel
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = when (pairingStatus) {
                                "completed" -> IdealPlayerColors.Success.copy(alpha = 0.12f)
                                "error" -> IdealPlayerColors.Error.copy(alpha = 0.12f)
                                else -> IdealPlayerColors.Secondary.copy(alpha = 0.12f)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = when (pairingStatus) {
                                    "completed" -> IdealPlayerColors.Success.copy(alpha = 0.4f)
                                    "error" -> IdealPlayerColors.Error.copy(alpha = 0.4f)
                                    else -> IdealPlayerColors.Secondary.copy(alpha = 0.4f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (pairingStatus == "pending" || pairingStatus == "completed") {
                                    CircularProgressIndicator(
                                        color = if (pairingStatus == "completed") IdealPlayerColors.Success else IdealPlayerColors.Secondary,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                Text(
                                    text = when (pairingStatus) {
                                        "completed" -> stringResource(R.string.a2_pairing_completed)
                                        "error" -> errorMessage ?: stringResource(R.string.a2_pairing_error)
                                        else -> stringResource(R.string.a2_pairing_pending)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when (pairingStatus) {
                                        "completed" -> IdealPlayerColors.Success
                                        "error" -> IdealPlayerColors.Error
                                        else -> IdealPlayerColors.TextPrimary
                                    }
                                )
                            }
                        }
                    }
                }

                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TvActionButton(
                        text = stringResource(R.string.a2_action_cancel_close),
                        onClick = onDismiss,
                        modifier = Modifier
                            .width(220.dp)
                            .focusRequester(closeFocusRequester),
                        isSecondary = true
                    )
                }
            }
        }
    }
}

private fun createPairingQrCode(content: String, size: Int = 400): Bitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
    )
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}.getOrNull()
