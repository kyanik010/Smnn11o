package com.idealplayer.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.idealplayer.app.BuildConfig
import com.idealplayer.app.R
import com.idealplayer.app.core.datastore.AppSettings
import com.idealplayer.app.core.datastore.SettingsDataStore
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.core.designsystem.theme.A2Shape
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.core.model.PlayerEngineType

import com.idealplayer.app.core.player.AspectRatioMode
import com.idealplayer.app.core.player.LiveLatencyMode
import com.idealplayer.app.core.player.PlayerManager
import com.idealplayer.app.core.player.VideoQualityMode
import com.idealplayer.app.data.repository.ContentRepository
import com.idealplayer.app.data.repository.PlaylistRepository
import com.idealplayer.app.ui.components.*
import com.idealplayer.app.ui.components.a2.A2ActionButton
import com.idealplayer.app.ui.components.a2.A2ActionVariant
import com.idealplayer.app.ui.components.a2.A2BooleanSettingRow
import com.idealplayer.app.ui.components.a2.A2DialogShell
import com.idealplayer.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PRIVACY_POLICY_URL = "https://idealplayer.netlify.app/privacy"

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// ViewModel
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository,
    private val playerManager: PlayerManager,
    val parentalControlManager: com.idealplayer.app.core.security.ParentalControlManager
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())


    val activePlaylist = playlistRepository.getActivePlaylist()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)



    // ━━━ App General ━━━
    fun updateAppLanguage(lang: String) = viewModelScope.launch { settingsDataStore.updateAppLanguage(lang) }

    // ━━━ Player ━━━
    fun updatePlayerEngine(engine: String) = viewModelScope.launch {
        playerManager.updatePreferredEngine(PlayerEngineType.fromStoredValue(engine))
    }
    fun updateDisplayMode(mode: String) = viewModelScope.launch { settingsDataStore.updateDefaultDisplayMode(mode) }
    fun updateDefaultSpeed(speed: Float) = viewModelScope.launch { settingsDataStore.updateDefaultPlaybackSpeed(speed) }
    fun updateSeekForward(ms: Long) = viewModelScope.launch { settingsDataStore.updateSeekForward(ms) }
    fun updateSeekBackward(ms: Long) = viewModelScope.launch { settingsDataStore.updateSeekBackward(ms) }
    fun updateAutoResume(v: Boolean) = viewModelScope.launch { settingsDataStore.updateAutoResume(v) }
    fun updateContinueWatching(v: Boolean) = viewModelScope.launch {
        settingsDataStore.updateContinueWatching(v)
        if (!v) contentRepository.clearContinueWatchingHistory()
    }
    fun updateAutoPlayNext(v: Boolean) = viewModelScope.launch { settingsDataStore.updateAutoPlayNext(v) }
    fun clearWatchHistory() = viewModelScope.launch { contentRepository.clearWatchHistory() }

    // ━━━ Audio & Subtitle ━━━
    fun updateSubtitleLang(lang: String) = viewModelScope.launch { settingsDataStore.updateSubtitleLanguage(lang) }
    fun updateAudioLang(lang: String) = viewModelScope.launch { settingsDataStore.updateAudioLanguage(lang) }
    fun updateAutoEnableSubtitles(v: Boolean) = viewModelScope.launch { settingsDataStore.updateAutoEnableSubtitles(v) }
    fun updateSubtitleSize(size: Int) = viewModelScope.launch { settingsDataStore.updateSubtitleSize(size) }
    fun updateRememberTrack(v: Boolean) = viewModelScope.launch { settingsDataStore.updateRememberTrackPerContent(v) }

    // ━━━ Quality ━━━
    fun updateQualityMode(mode: String) = viewModelScope.launch { settingsDataStore.updateVideoQualityMode(mode) }
    fun updatePreferHw(v: Boolean) = viewModelScope.launch { settingsDataStore.updatePreferHwDecoding(v) }
    fun updateAllowFallback(v: Boolean) = viewModelScope.launch { settingsDataStore.updateAllowQualityFallback(v) }
    fun updateAutoPlayerFallback(v: Boolean) = viewModelScope.launch { settingsDataStore.updateAutoPlayerFallback(v) }

    // ━━━ Live TV ━━━
    fun updateLiveMode(mode: String) = viewModelScope.launch { settingsDataStore.updateLiveLatencyMode(mode) }
    fun updateLiveReconnect(v: Boolean) = viewModelScope.launch { settingsDataStore.updateLiveReconnect(v) }
    fun updateRememberChannel(v: Boolean) = viewModelScope.launch {
        settingsDataStore.updateRememberLastChannel(v)
        if (!v) contentRepository.clearRecentlyWatchedChannels()
    }
    fun updateStartFullscreen(v: Boolean) = viewModelScope.launch { settingsDataStore.updateStartFullscreenLive(v) }
    fun updateOpenLastPlaylist(v: Boolean) = viewModelScope.launch { settingsDataStore.updateOpenLastPlaylist(v) }

    // ━━━ Playlist / Account ━━━
    fun exitPlaylist() = viewModelScope.launch {
        val active = activePlaylist.value ?: return@launch
        playlistRepository.deletePlaylist(active)
    }

    fun refreshPlaylist() = viewModelScope.launch {
        val active = activePlaylist.value ?: return@launch
        playlistRepository.syncPlaylist(active)
    }

}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Screen entry point
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun SettingsScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onBackToOnboarding: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var isDrawerExpanded by remember { mutableStateOf(false) }

    if (isTv) {
        IdealPlayerDrawer(
            isExpanded = isDrawerExpanded,
            selectedRoute = Routes.SETTINGS,
            isTv = true,
            onToggle = { isDrawerExpanded = !isDrawerExpanded },
            onNavigate = { route ->
                if (route == "exit") onBackToOnboarding()
                else onNavigate(route)
            }
        ) {
            SettingsContent(
                settings = settings,
                viewModel = viewModel,
                isTv = true,
                onBackToOnboarding = onBackToOnboarding
            )
        }
    } else {
        SettingsContent(
            settings = settings,
            viewModel = viewModel,
            isTv = false,
            onBackToOnboarding = onBackToOnboarding
        )
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Language helpers
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

private data class LanguageOption(val code: String, val labelResId: Int)

private val appLanguages = listOf(
    LanguageOption("system", R.string.language_system),
    LanguageOption("tr", R.string.language_turkish),
    LanguageOption("en", R.string.language_english),
    LanguageOption("ar", R.string.language_arabic)
)

@Composable
private fun langLabel(code: String, options: List<LanguageOption>): String {
    val resId = options.find { it.code == code }?.labelResId ?: return code
    return stringResource(resId)
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Main content
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun SettingsContent(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    isTv: Boolean,
    onBackToOnboarding: () -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()
    val activePlaylist by viewModel.activePlaylist.collectAsStateWithLifecycle()
    var showExitDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    val initialFocusRequester = remember { FocusRequester() }
    val clearHistoryFocusRequester = remember { FocusRequester() }
    val exitPlaylistFocusRequester = remember { FocusRequester() }
    val clearDialogCancelFocusRequester = remember { FocusRequester() }
    val exitDialogCancelFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    var wasClearHistoryDialogVisible by remember { mutableStateOf(false) }
    var wasExitDialogVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isTv) {
        if (isTv && !hasRequestedInitialFocus) {
            withFrameNanos { }
            runCatching { initialFocusRequester.requestFocus() }
            hasRequestedInitialFocus = true
        }
    }

    LaunchedEffect(isTv, showClearHistoryDialog) {
        if (!isTv) return@LaunchedEffect
        when {
            showClearHistoryDialog -> {
                wasClearHistoryDialogVisible = true
                withFrameNanos { }
                runCatching { clearDialogCancelFocusRequester.requestFocus() }
            }

            wasClearHistoryDialogVisible -> {
                withFrameNanos { }
                runCatching { clearHistoryFocusRequester.requestFocus() }
                wasClearHistoryDialogVisible = false
            }
        }
    }

    LaunchedEffect(isTv, showExitDialog) {
        if (!isTv) return@LaunchedEffect
        when {
            showExitDialog -> {
                wasExitDialogVisible = true
                withFrameNanos { }
                runCatching { exitDialogCancelFocusRequester.requestFocus() }
            }

            wasExitDialogVisible -> {
                withFrameNanos { }
                runCatching { exitPlaylistFocusRequester.requestFocus() }
                wasExitDialogVisible = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = dimens.screenPadding, vertical = dimens.screenPadding)
    ) {
        if (!isTv) {
            Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium, color = IdealPlayerColors.TextPrimary)
            Spacer(modifier = Modifier.height(20.dp))
        }


        // ══════════════════════════════════════════════
        // A) PLAYER
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.Filled.PlayCircle,
            title = stringResource(R.string.settings_player),
            isTv = isTv
        ) {
            // Player Engine
            SettingsDropdown(
                label = stringResource(R.string.setting_player_engine),
                value = if (settings.playerEngine.uppercase() == "EXOPLAYER") "Media3 (ExoPlayer)" else "VLC Player",
                options = listOf("Media3 (ExoPlayer)", "VLC Player"),
                isTv = isTv,
                focusRequester = if (isTv) initialFocusRequester else null,
                onSelect = { label ->
                    val engine = if (label.contains("ExoPlayer")) "EXOPLAYER" else "VLC"
                    viewModel.updatePlayerEngine(engine)
                }
            )

            // Display mode
            val displayModes = AspectRatioMode.entries.map { it.name to it.label }
            SettingsDropdown(
                label = stringResource(R.string.setting_display_mode),
                value = displayModes.find { it.first.equals(settings.defaultDisplayMode, ignoreCase = true) }?.second ?: "Fit to Screen",
                options = displayModes.map { it.second },
                isTv = isTv,
                onSelect = { label ->
                    val mode = displayModes.find { it.second == label }?.first ?: "FIT"
                    viewModel.updateDisplayMode(mode)
                }
            )

            // Playback speed
            val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
            SettingsDropdown(
                label = stringResource(R.string.setting_playback_speed),
                value = "${settings.defaultPlaybackSpeed}x",
                options = speeds.map { "${it}x" },
                isTv = isTv,
                onSelect = { label ->
                    val speed = label.removeSuffix("x").toFloatOrNull() ?: 1f
                    viewModel.updateDefaultSpeed(speed)
                }
            )

            SettingsSwitch(
                label = stringResource(R.string.settings_auto_resume),
                checked = settings.autoResumePlayback,
                isTv = isTv,
                onCheckedChange = { viewModel.updateAutoResume(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_continue_watching),
                checked = settings.continueWatching,
                isTv = isTv,
                onCheckedChange = { viewModel.updateContinueWatching(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_auto_play_next),
                checked = settings.autoPlayNextEpisode,
                isTv = isTv,
                onCheckedChange = { viewModel.updateAutoPlayNext(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsActionButton(
                icon = Icons.Filled.Delete,
                label = stringResource(R.string.action_clear_watch_history),
                isDanger = true,
                isTv = isTv,
                focusRequester = if (isTv) clearHistoryFocusRequester else null,
                onClick = { showClearHistoryDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════
        // B) AUDIO & SUBTITLES
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.Filled.Subtitles,
            title = stringResource(R.string.settings_audio_subtitle),
            isTv = isTv
        ) {
            val subtitleLangs = listOf("off", "tr", "en", "de", "fr", "ar", "system")
            SettingsDropdown(
                label = stringResource(R.string.setting_subtitles),
                value = settings.defaultSubtitleLanguage,
                options = subtitleLangs,
                isTv = isTv,
                onSelect = { viewModel.updateSubtitleLang(it) }
            )

            val audioLangs = listOf("system", "tr", "en", "de", "fr", "ar")
            SettingsDropdown(
                label = stringResource(R.string.setting_audio_track),
                value = settings.defaultAudioLanguage,
                options = audioLangs,
                isTv = isTv,
                onSelect = { viewModel.updateAudioLang(it) }
            )

            SettingsSwitch(
                label = stringResource(R.string.settings_auto_enable_subtitles),
                checked = settings.autoEnableSubtitles,
                isTv = isTv,
                onCheckedChange = { viewModel.updateAutoEnableSubtitles(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════
        // C) VIDEO QUALITY
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.Filled.HighQuality,
            title = stringResource(R.string.settings_video_quality),
            isTv = isTv
        ) {
            val qualityModes = VideoQualityMode.entries.map { it.name to it.label }
            SettingsDropdown(
                label = stringResource(R.string.setting_video_quality),
                value = qualityModes.find { it.first.equals(settings.videoQualityMode, ignoreCase = true) }?.second ?: "Auto",
                options = qualityModes.map { it.second },
                isTv = isTv,
                onSelect = { label ->
                    val mode = qualityModes.find { it.second == label }?.first ?: "AUTO"
                    viewModel.updateQualityMode(mode)
                }
            )

            SettingsSwitch(
                label = stringResource(R.string.settings_prefer_hw_decoding),
                checked = settings.preferHwDecoding,
                isTv = isTv,
                onCheckedChange = { viewModel.updatePreferHw(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_allow_quality_fallback),
                checked = settings.allowQualityFallback,
                isTv = isTv,
                onCheckedChange = { viewModel.updateAllowFallback(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_auto_player_fallback),
                checked = settings.autoPlayerFallback,
                isTv = isTv,
                onCheckedChange = { viewModel.updateAutoPlayerFallback(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════
        // D) LIVE TV
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.Filled.LiveTv,
            title = stringResource(R.string.settings_live_tv),
            isTv = isTv
        ) {
            val latencyModes = LiveLatencyMode.entries.map { it.name to it.label }
            SettingsDropdown(
                label = stringResource(R.string.settings_live_latency),
                value = latencyModes.find { it.first.equals(settings.liveLatencyMode, ignoreCase = true) }?.second ?: "Balanced",
                options = latencyModes.map { it.second },
                isTv = isTv,
                onSelect = { label ->
                    val mode = latencyModes.find { it.second == label }?.first ?: "BALANCED"
                    viewModel.updateLiveMode(mode)
                }
            )

            SettingsSwitch(
                label = stringResource(R.string.settings_live_reconnect),
                checked = settings.liveReconnectOnFailure,
                isTv = isTv,
                onCheckedChange = { viewModel.updateLiveReconnect(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_remember_channel),
                checked = settings.rememberLastChannel,
                isTv = isTv,
                onCheckedChange = { viewModel.updateRememberChannel(it) }
            )
            SettingsSwitch(
                label = stringResource(R.string.settings_start_fullscreen),
                checked = settings.startFullscreenLive,
                isTv = isTv,
                onCheckedChange = { viewModel.updateStartFullscreen(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════
        // E) PARENTAL CONTROL
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.Filled.FamilyRestroom,
            title = stringResource(R.string.settings_parental_control),
            isTv = isTv
        ) {
            ParentalControlSection(
                parentalControlManager = viewModel.parentalControlManager,
                isTv = isTv
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════
        // F) PLAYLIST / ACCOUNT
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            title = stringResource(R.string.settings_playlist_account),
            isTv = isTv
        ) {
            // Current playlist info
            activePlaylist?.let { playlist ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(IdealPlayerColors.Primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = null,
                            tint = IdealPlayerColors.Primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(playlist.name, style = MaterialTheme.typography.bodyLarge, color = IdealPlayerColors.TextPrimary)
                        Text(
                            when (playlist.type) {
                                com.idealplayer.app.core.model.PlaylistType.XTREAM_CODES -> "Xtream Codes"
                                com.idealplayer.app.core.model.PlaylistType.M3U_URL -> "M3U URL"
                                com.idealplayer.app.core.model.PlaylistType.M3U_FILE -> "M3U File"
                            },
                            style = MaterialTheme.typography.bodySmall, color = IdealPlayerColors.TextTertiary
                        )
                    }
                }
                HorizontalDivider(color = IdealPlayerColors.DividerColor, modifier = Modifier.padding(vertical = 4.dp))
            }

            SettingsActionButton(
                icon = Icons.Filled.Refresh,
                label = stringResource(R.string.action_refresh_playlist),
                isTv = isTv,
                onClick = { viewModel.refreshPlaylist() }
            )

            SettingsActionButton(
                icon = Icons.Filled.SwapHoriz,
                label = stringResource(R.string.action_switch_playlist),
                isTv = isTv,
                onClick = onBackToOnboarding
            )

            SettingsSwitch(
                label = stringResource(R.string.open_last_playlist),
                checked = settings.openLastPlaylist,
                isTv = isTv,
                onCheckedChange = { viewModel.updateOpenLastPlaylist(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsActionButton(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = stringResource(R.string.nav_exit_playlist),
                isDanger = true,
                isTv = isTv,
                focusRequester = if (isTv) exitPlaylistFocusRequester else null,
                onClick = { showExitDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════
        // F) APP / GENERAL
        // ══════════════════════════════════════════════
        SettingsSection(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.settings_app_general),
            isTv = isTv
        ) {
            val localizedAppLangs = appLanguages.map { it.code to stringResource(it.labelResId) }
            SettingsDropdown(
                label = stringResource(R.string.settings_app_language),
                value = langLabel(settings.appLanguage, appLanguages),
                options = localizedAppLangs.map { it.second },
                isTv = isTv,
                onSelect = { label ->
                    val langCode = localizedAppLangs.find { it.second == label }?.first ?: "system"
                    viewModel.updateAppLanguage(langCode)
                    
                    val localeList = if (langCode == "system") {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(langCode)
                    }
                    AppCompatDelegate.setApplicationLocales(localeList)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsInfoRow(stringResource(R.string.app_version), "IdealPlayer v${BuildConfig.VERSION_NAME}")

            Spacer(modifier = Modifier.height(8.dp))
            SettingsActionButton(
                icon = Icons.Filled.PrivacyTip,
                label = stringResource(R.string.privacy_policy),
                isTv = isTv,
                onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.labelSmall, color = IdealPlayerColors.TextTertiary)
            Text(stringResource(R.string.disclaimer_text),
                style = MaterialTheme.typography.bodySmall, color = IdealPlayerColors.TextTertiary,
                modifier = Modifier.padding(top = 2.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Exit playlist confirm dialog
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    SettingsConfirmationDialog(
        visible = showExitDialog,
        title = stringResource(R.string.nav_exit_playlist),
        message = stringResource(R.string.settings_exit_playlist_confirm_message),
        confirmLabel = stringResource(R.string.confirm),
        dismissLabel = stringResource(R.string.cancel),
        dismissFocusRequester = exitDialogCancelFocusRequester,
        onDismiss = { showExitDialog = false },
        onConfirm = {
            showExitDialog = false
            viewModel.exitPlaylist()
            onBackToOnboarding()
        }
    )

    SettingsConfirmationDialog(
        visible = showClearHistoryDialog,
        title = stringResource(R.string.action_clear_watch_history),
        message = stringResource(R.string.clear_watch_history_confirm_message),
        confirmLabel = stringResource(R.string.confirm),
        dismissLabel = stringResource(R.string.cancel),
        dismissFocusRequester = clearDialogCancelFocusRequester,
        onDismiss = { showClearHistoryDialog = false },
        onConfirm = {
            showClearHistoryDialog = false
            viewModel.clearWatchHistory()
        }
    )
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Reusable settings composables
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    isTv: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IdealPlayerColors.Surface, A2Shape.large)
            .border(1.dp, IdealPlayerColors.CardBorder, A2Shape.large)
            .padding(if (isTv) A2Spacing.xl else A2Spacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = IdealPlayerColors.Secondary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(A2Spacing.sm))
            Text(title, style = MaterialTheme.typography.titleLarge, color = IdealPlayerColors.TextPrimary)
        }
        Spacer(modifier = Modifier.height(A2Spacing.md))
        content()
    }
}

@Composable
private fun SettingsDropdown(
    label: String,
    value: String,
    options: List<String>,
    isTv: Boolean,
    focusRequester: FocusRequester? = null,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val localFocusRequester = remember { FocusRequester() }
    val triggerFocusRequester = focusRequester ?: localFocusRequester
    val optionFocusRequesters = remember(options) {
        options.associateWith { FocusRequester() }
    }
    var wasExpanded by remember { mutableStateOf(false) }
    val dimens = LocalIdealPlayerDimens.current
    val scale = if (isTv && isFocused) 1.035f else 1f

    LaunchedEffect(isTv, expanded) {
        if (!isTv) return@LaunchedEffect
        when {
            expanded -> {
                wasExpanded = true
                withFrameNanos { }
                val selectedRequester = optionFocusRequesters[value]
                    ?: options.firstOrNull()?.let(optionFocusRequesters::get)
                runCatching { selectedRequester?.requestFocus() }
            }

            wasExpanded -> {
                withFrameNanos { }
                runCatching { triggerFocusRequester.requestFocus() }
                wasExpanded = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = dimens.touchTargetMin)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .then(if (isTv) Modifier.focusRequester(triggerFocusRequester) else Modifier)
                    .onFocusChanged { isFocused = it.isFocused }
                    .then(
                        if (isTv && isFocused) {
                            Modifier.shadow(14.dp, A2Shape.medium, spotColor = IdealPlayerColors.FocusGlow)
                        } else {
                            Modifier
                        }
                    ),
                border = BorderStroke(
                    width = if (isFocused) dimens.focusBorderWidth else if (expanded) 2.dp else 1.dp,
                    color = when {
                        isFocused -> IdealPlayerColors.FocusBorder
                        expanded -> IdealPlayerColors.SelectedBorder
                        else -> IdealPlayerColors.CardBorder
                    }
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = IdealPlayerColors.TextPrimary,
                    containerColor = if (expanded) IdealPlayerColors.SurfaceSelected else IdealPlayerColors.SurfaceVariant
                ),
                shape = A2Shape.medium
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = IdealPlayerColors.TextSecondary)
                    Text(value, style = MaterialTheme.typography.bodyLarge, color = IdealPlayerColors.TextPrimary)
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(dimens.iconSize))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(IdealPlayerColors.SurfaceElevated)
            ) {
                options.forEach { option ->
                    A2ActionButton(
                        text = option,
                        onClick = { onSelect(option); expanded = false },
                        selected = option == value,
                        variant = A2ActionVariant.Ghost,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isTv) {
                                    optionFocusRequesters[option]
                                        ?.let { Modifier.focusRequester(it) }
                                        ?: Modifier
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }
    }
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    isTv: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    A2BooleanSettingRow(
        title = label,
        checked = checked,
        onCheckedChange = onCheckedChange,
        stateDescription = if (checked) "Açık" else "Kapalı",
        modifier = Modifier.padding(vertical = A2Spacing.xs)
    )
}

@Composable
private fun SettingsActionButton(
    icon: ImageVector,
    label: String,
    isDanger: Boolean = false,
    isTv: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    A2ActionButton(
        text = label,
        onClick = onClick,
        icon = icon,
        variant = if (isDanger) A2ActionVariant.Destructive else A2ActionVariant.Ghost,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .padding(vertical = A2Spacing.xs)
    )
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(IdealPlayerColors.CardBackground, A2Shape.medium)
            .border(1.dp, IdealPlayerColors.CardBorder, A2Shape.medium)
            .padding(horizontal = A2Spacing.md, vertical = A2Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = IdealPlayerColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = IdealPlayerColors.TextPrimary)
    }
}

@Composable
private fun SettingsConfirmationDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    dismissFocusRequester: FocusRequester,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        A2DialogShell(
            title = title,
            message = message,
            stateDescription = title,
            modifier = Modifier.padding(A2Spacing.md),
            actions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm)
                ) {
                    A2ActionButton(
                        text = dismissLabel,
                        onClick = onDismiss,
                        variant = A2ActionVariant.Ghost,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(dismissFocusRequester)
                    )
                    A2ActionButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        variant = A2ActionVariant.Destructive,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        )
    }
}
