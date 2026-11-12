package com.idealplayer.app.ui.tv

import android.app.Activity
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idealplayer.app.R
import com.idealplayer.app.core.common.StringUtils
import com.idealplayer.app.core.designsystem.theme.A2Shape
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.model.Channel
import com.idealplayer.app.core.model.ContentType
import com.idealplayer.app.core.model.Episode
import com.idealplayer.app.core.model.Movie
import com.idealplayer.app.core.player.AspectRatioMode
import com.idealplayer.app.core.player.PlaybackState
import com.idealplayer.app.core.player.PlaybackDiagnostics
import com.idealplayer.app.core.player.PlayerState
import com.idealplayer.app.core.player.VideoQualityMode
import com.idealplayer.app.core.player.formatNetworkSpeed
import com.idealplayer.app.core.player.resolveRelativeSeekPosition
import com.idealplayer.app.core.player.videoResolutionBadge

import com.idealplayer.app.ui.components.rememberTvFocusVisualState
import com.idealplayer.app.ui.player.PlayerShellMode
import com.idealplayer.app.ui.player.PlayerSurfaceHost
import com.idealplayer.app.ui.player.PlayerSurfaceHostState
import com.idealplayer.app.ui.player.PlayerViewModel
import kotlin.math.roundToInt
import timber.log.Timber

internal enum class TvPlayerPanel {
    NONE,
    CHANNELS,
    CATEGORIES,
    EPG,
    AUDIO,
    SUBTITLE,
    SCREEN_MODE,
    QUALITY,
    SPEED,
    SLEEP_TIMER,
    SETTINGS,
    STREAM_INFO
}

private enum class TvPanelSide {
    START,
    END
}

internal enum class TvOverlayAction {
    BACK,
    MAIN_PREVIOUS,
    SEEK_BACKWARD,
    PLAY_PAUSE,
    SEEK,
    GO_LIVE,
    SEEK_FORWARD,
    MAIN_NEXT,
    CHANNELS,
    EPG,
    PREVIOUS_EPISODE,
    NEXT_EPISODE,
    AUDIO,
    SUBTITLE,
    SCREEN_MODE,
    SETTINGS
}

internal enum class TvPlaybackKind {
    MOVIE,
    SERIES,
    LIVE
}

private const val TV_PLAYER_LOG_TAG = "TvPlayerScreen"
internal const val TV_PLAYER_SEEK_BAR_TEST_TAG = "tv_player_seek_bar"

internal fun resolveTvPlaybackKind(
    contentType: String,
    hasLiveChannel: Boolean,
    hasEpisode: Boolean
): TvPlaybackKind = when {
    hasLiveChannel || contentType.equals(ContentType.LIVE.name, ignoreCase = true) ->
        TvPlaybackKind.LIVE

    hasEpisode || contentType.equals(ContentType.SERIES.name, ignoreCase = true) ->
        TvPlaybackKind.SERIES

    else -> TvPlaybackKind.MOVIE
}

internal fun tvPlayerUtilityActions(
    playbackKind: TvPlaybackKind,
    hasChannels: Boolean,
    hasPreviousEpisode: Boolean,
    hasNextEpisode: Boolean,
    hasAudioTracks: Boolean,
    hasSubtitleTracks: Boolean
): List<TvOverlayAction> = buildList {
    if (playbackKind == TvPlaybackKind.LIVE) {
        if (hasChannels) add(TvOverlayAction.CHANNELS)
        add(TvOverlayAction.EPG)
    }
    if (playbackKind == TvPlaybackKind.SERIES && hasPreviousEpisode) {
        add(TvOverlayAction.PREVIOUS_EPISODE)
    }
    if (playbackKind == TvPlaybackKind.SERIES && hasNextEpisode) {
        add(TvOverlayAction.NEXT_EPISODE)
    }
    if (hasAudioTracks) add(TvOverlayAction.AUDIO)
    if (hasSubtitleTracks) add(TvOverlayAction.SUBTITLE)
    add(TvOverlayAction.SCREEN_MODE)
    add(TvOverlayAction.SETTINGS)
}

/**
 * The player owns only these horizontal panel transitions. The action stays explicit even
 * when its destination is empty, so an edge key cannot fall through to a disappearing Lazy
 * item or the player root while a panel is refreshing.
 */
internal enum class TvPlayerPanelDirectionalAction {
    NONE,
    OPEN_CATEGORIES,
    OPEN_CHANNELS
}

internal enum class TvPlayerPanelDirection {
    LEFT,
    RIGHT
}

internal enum class TvPlayerHiddenDirectionalAction {
    NONE,
    SEEK_BACKWARD,
    SEEK_FORWARD,
    OPEN_CHANNELS,
    OPEN_EPG
}

internal fun tvPlayerHiddenDirectionalAction(
    isLivePlayback: Boolean,
    isSeekable: Boolean,
    direction: TvPlayerPanelDirection
): TvPlayerHiddenDirectionalAction = when {
    isLivePlayback && direction == TvPlayerPanelDirection.LEFT ->
        TvPlayerHiddenDirectionalAction.OPEN_CHANNELS

    isLivePlayback && direction == TvPlayerPanelDirection.RIGHT ->
        TvPlayerHiddenDirectionalAction.OPEN_EPG

    !isLivePlayback && isSeekable && direction == TvPlayerPanelDirection.LEFT ->
        TvPlayerHiddenDirectionalAction.SEEK_BACKWARD

    !isLivePlayback && isSeekable && direction == TvPlayerPanelDirection.RIGHT ->
        TvPlayerHiddenDirectionalAction.SEEK_FORWARD

    else -> TvPlayerHiddenDirectionalAction.NONE
}

internal fun tvSeekStepSeconds(stepMs: Long): Long =
    (((stepMs.coerceAtLeast(1L) - 1L) / 1_000L) + 1L).coerceAtLeast(1L)

internal fun tvSeekStepForRepeat(baseStepMs: Long, repeatCount: Int): Long {
    val multiplier = when {
        repeatCount >= 12 -> 4L
        repeatCount >= 5 -> 2L
        else -> 1L
    }
    val safeBase = baseStepMs.coerceAtLeast(0L)
    return if (safeBase > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else safeBase * multiplier
}

internal fun tvResolvedScrubPosition(
    enginePosition: Long,
    localPosition: Long,
    duration: Long,
    pendingAgeMs: Long,
    pendingTimeoutMs: Long = 1_500L,
    catchUpToleranceMs: Long = 1_500L
): Long {
    val boundedEngine = enginePosition.coerceIn(0L, duration.coerceAtLeast(0L))
    val boundedLocal = localPosition.coerceIn(0L, duration.coerceAtLeast(0L))
    val shouldKeepPendingTarget = pendingAgeMs in 0L..pendingTimeoutMs &&
        kotlin.math.abs(boundedEngine - boundedLocal) > catchUpToleranceMs
    return if (shouldKeepPendingTarget) boundedLocal else boundedEngine
}

internal fun tvMoviePlaybackSubtitle(movie: Movie?): String = movie
    ?.let { metadata ->
        listOfNotNull(
            metadata.year.takeIf { it > 0 }?.toString(),
            metadata.genre.takeIf { it.isNotBlank() }
        ).joinToString(" • ")
    }
    .orEmpty()

internal fun tvSupportsQualitySelection(
    engineName: String,
    isAdaptiveStream: Boolean,
    availableQualityCount: Int
): Boolean = engineName.equals("EXOPLAYER", ignoreCase = true) &&
    (isAdaptiveStream || availableQualityCount > 1)

internal fun tvPlayerPanelDirectionalAction(
    isLivePlayback: Boolean,
    activePanel: TvPlayerPanel,
    direction: TvPlayerPanelDirection
): TvPlayerPanelDirectionalAction = when {
    isLivePlayback && activePanel == TvPlayerPanel.CHANNELS &&
        direction == TvPlayerPanelDirection.LEFT -> TvPlayerPanelDirectionalAction.OPEN_CATEGORIES

    isLivePlayback && activePanel == TvPlayerPanel.CATEGORIES &&
        direction == TvPlayerPanelDirection.RIGHT -> TvPlayerPanelDirectionalAction.OPEN_CHANNELS

    else -> TvPlayerPanelDirectionalAction.NONE
}

internal fun tvPlayerPanelBackTarget(
    activePanel: TvPlayerPanel,
    parentPanel: TvPlayerPanel
): TvPlayerPanel = when {
    activePanel == TvPlayerPanel.CATEGORIES -> TvPlayerPanel.CHANNELS
    parentPanel != TvPlayerPanel.NONE -> parentPanel
    else -> TvPlayerPanel.NONE
}

/**
 * Lazy layouts need unique, stable keys. IPTV metadata can contain duplicate labels, so callers
 * provide a semantic id and duplicate ids are disambiguated deterministically.
 */
internal fun tvStablePlayerPanelKeys(rawKeys: List<String>): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return rawKeys.mapIndexed { index, rawKey ->
        val baseKey = rawKey.ifBlank { "option:$index" }
        val occurrence = occurrences.getOrDefault(baseKey, 0)
        occurrences[baseKey] = occurrence + 1
        "$baseKey#$occurrence"
    }
}

internal fun tvInitialPlayerPanelFocusIndex(
    selected: List<Boolean>,
    enabled: List<Boolean>
): Int? {
    if (selected.size != enabled.size) return null
    return selected.indices.firstOrNull { selected[it] && enabled[it] }
        ?: enabled.indices.firstOrNull { enabled[it] }
}

private data class TvPanelOption(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val progressFraction: Float? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

private fun TvPanelSide.edgeShape(cornerRadius: Dp): RoundedCornerShape {
    return when (this) {
        TvPanelSide.START -> RoundedCornerShape(
            topStart = 0.dp,
            topEnd = cornerRadius,
            bottomEnd = cornerRadius,
            bottomStart = 0.dp
        )

        TvPanelSide.END -> RoundedCornerShape(
            topStart = cornerRadius,
            topEnd = 0.dp,
            bottomEnd = 0.dp,
            bottomStart = cornerRadius
        )
    }
}


@Composable
private fun tvAspectRatioLabel(mode: AspectRatioMode): String = stringResource(
    when (mode) {
        AspectRatioMode.AUTO -> R.string.player_aspect_auto
        AspectRatioMode.FIT -> R.string.player_aspect_fit
        AspectRatioMode.FILL -> R.string.player_aspect_fill
        AspectRatioMode.ZOOM -> R.string.player_aspect_zoom
        AspectRatioMode.STRETCH -> R.string.player_aspect_stretch
        AspectRatioMode.ORIGINAL -> R.string.player_aspect_original
        AspectRatioMode.FORCE_16_9 -> R.string.player_aspect_16_9
        AspectRatioMode.FORCE_4_3 -> R.string.player_aspect_4_3
    }
)

@Composable
private fun tvQualityModeLabel(mode: VideoQualityMode): String = stringResource(
    when (mode) {
        VideoQualityMode.AUTO -> R.string.player_quality_auto
        VideoQualityMode.BEST -> R.string.player_quality_best
        VideoQualityMode.BALANCED -> R.string.player_quality_balanced
        VideoQualityMode.DATA_SAVER -> R.string.player_quality_data_saver
    }
)

@Composable
private fun tvPlaybackStateLabel(state: PlaybackState): String = stringResource(
    when (state) {
        PlaybackState.IDLE -> R.string.player_state_idle
        PlaybackState.BUFFERING -> R.string.player_state_buffering
        PlaybackState.PLAYING -> R.string.player_state_playing
        PlaybackState.PAUSED -> R.string.player_state_paused
        PlaybackState.ENDED -> R.string.player_state_ended
        PlaybackState.ERROR -> R.string.player_state_error
        PlaybackState.STOPPED -> R.string.player_state_stopped
    }
)

private fun tvChannelPanelSubtitle(
    epgProgram: com.idealplayer.app.data.parser.EpgProgram?
): String? {
    if (epgProgram == null) {
        return null
    }

    val timeFormatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    val startTime = timeFormatter.format(java.util.Date(epgProgram.startTime))
    val endTime = timeFormatter.format(java.util.Date(epgProgram.endTime))
    val progressPercent = (epgProgram.progressFraction * 100f).roundToInt().coerceIn(0, 100)
    return "${epgProgram.title} • $startTime - $endTime • $progressPercent%"
}

private fun tvEpgPanelSubtitle(
    epgProgram: com.idealplayer.app.data.parser.EpgProgram
): String {
    val timeFormatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    val startTime = timeFormatter.format(java.util.Date(epgProgram.startTime))
    val endTime = timeFormatter.format(java.util.Date(epgProgram.endTime))
    return buildString {
        append(startTime)
        append(" - ")
        append(endTime)
        if (epgProgram.genre.isNotBlank()) {
            append(" • ")
            append(epgProgram.genre)
        }
    }
}

private fun KeyEvent.isTvBackKey(): Boolean {
    return key == Key.Back ||
        key == Key.Escape ||
        nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK
}

private fun KeyEvent.isTvPlayPauseToggleKey(): Boolean {
    return key == Key.MediaPlayPause ||
        key == Key.Spacebar ||
        nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
}

private fun KeyEvent.isTvPlayKey(): Boolean {
    return nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_MEDIA_PLAY
}

private fun KeyEvent.isTvPauseKey(): Boolean {
    return nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_MEDIA_PAUSE ||
        nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_MEDIA_STOP
}

private fun KeyEvent.isTvPreviousShortcutKey(): Boolean {
    return when (nativeKeyEvent.keyCode) {
        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
        AndroidKeyEvent.KEYCODE_PAGE_DOWN,
        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
        AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> true
        else -> false
    }
}

private fun KeyEvent.isTvNextShortcutKey(): Boolean {
    return when (nativeKeyEvent.keyCode) {
        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
        AndroidKeyEvent.KEYCODE_PAGE_UP,
        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> true
        else -> false
    }
}

private fun KeyEvent.isTvMenuKey(): Boolean {
    return nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_MENU
}

private fun KeyEvent.isTvInfoKey(): Boolean {
    return nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_INFO
}

private fun KeyEvent.isTvCaptionsKey(): Boolean {
    return nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_CAPTIONS
}

private fun KeyEvent.isTvAudioTrackKey(): Boolean {
    return nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_MEDIA_AUDIO_TRACK
}

private fun KeyEvent.tvRemoteDigit(): String? {
    return when (nativeKeyEvent.keyCode) {
        AndroidKeyEvent.KEYCODE_0,
        AndroidKeyEvent.KEYCODE_NUMPAD_0 -> "0"
        AndroidKeyEvent.KEYCODE_1,
        AndroidKeyEvent.KEYCODE_NUMPAD_1 -> "1"
        AndroidKeyEvent.KEYCODE_2,
        AndroidKeyEvent.KEYCODE_NUMPAD_2 -> "2"
        AndroidKeyEvent.KEYCODE_3,
        AndroidKeyEvent.KEYCODE_NUMPAD_3 -> "3"
        AndroidKeyEvent.KEYCODE_4,
        AndroidKeyEvent.KEYCODE_NUMPAD_4 -> "4"
        AndroidKeyEvent.KEYCODE_5,
        AndroidKeyEvent.KEYCODE_NUMPAD_5 -> "5"
        AndroidKeyEvent.KEYCODE_6,
        AndroidKeyEvent.KEYCODE_NUMPAD_6 -> "6"
        AndroidKeyEvent.KEYCODE_7,
        AndroidKeyEvent.KEYCODE_NUMPAD_7 -> "7"
        AndroidKeyEvent.KEYCODE_8,
        AndroidKeyEvent.KEYCODE_NUMPAD_8 -> "8"
        AndroidKeyEvent.KEYCODE_9,
        AndroidKeyEvent.KEYCODE_NUMPAD_9 -> "9"
        else -> null
    }
}

@Composable
fun TvPlayerScreen(
    url: String,
    title: String,
    contentId: Long,
    contentType: String,
    startPosition: Long,
    groupContext: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.state.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val playerReady by viewModel.playerReady.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val liveChannelSwitch by viewModel.liveChannelSwitch.collectAsStateWithLifecycle()
    val currentEpgProgram by viewModel.currentEpgProgram.collectAsStateWithLifecycle()
    val nextEpgProgram by viewModel.nextEpgProgram.collectAsStateWithLifecycle()
    val channelListEpgPrograms by viewModel.channelListEpgPrograms.collectAsStateWithLifecycle()
    val currentChannelEpgPrograms by viewModel.currentChannelEpgPrograms.collectAsStateWithLifecycle()
    val channelBrowserChannels by viewModel.channelBrowserChannels.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val activity = context as? Activity
    val insetsController = remember(activity) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
        }
    }

    val playbackKind = resolveTvPlaybackKind(
        contentType = contentType,
        hasLiveChannel = session.currentChannel != null,
        hasEpisode = session.currentEpisode != null
    )
    val isLivePlayback = playbackKind == TvPlaybackKind.LIVE
    val isSeriesPlayback = playbackKind == TvPlaybackKind.SERIES
    val isChannelSwitching = liveChannelSwitch.isSwitching
    val liveChannelAnchorId = liveChannelSwitch.targetChannelId ?: session.currentChannel?.id ?: contentId
    val previousLiveChannel = remember(session.availableChannels, liveChannelAnchorId) {
        com.idealplayer.app.ui.player.adjacentLiveChannel(
            session.availableChannels,
            liveChannelAnchorId,
            -1
        )
    }
    val nextLiveChannel = remember(session.availableChannels, liveChannelAnchorId) {
        com.idealplayer.app.ui.player.adjacentLiveChannel(
            session.availableChannels,
            liveChannelAnchorId,
            1
        )
    }
    val showSwitchingOverlay = isChannelSwitching && !playerState.isPlaybackConfirmed
    val showBlockingPlaybackOverlay = !playerReady ||
        (playerState.playbackState == PlaybackState.BUFFERING && !playerState.isPlaybackConfirmed) ||
        showSwitchingOverlay
    val displayTitle = when (playbackKind) {
        TvPlaybackKind.SERIES -> session.series?.name?.takeIf(String::isNotBlank)
            ?: session.title.ifBlank { title }
        else -> session.title.ifBlank { title }
    }
    val displaySubtitle = when (playbackKind) {
        TvPlaybackKind.LIVE -> tvPlaybackSubtitle(
            isLivePlayback = true,
            isSeriesPlayback = false,
            sessionEpisode = null,
            currentChannel = session.currentChannel,
            liveGroup = session.liveGroup
        )
        TvPlaybackKind.SERIES -> tvPlaybackSubtitle(
            isLivePlayback = false,
            isSeriesPlayback = true,
            sessionEpisode = session.currentEpisode,
            currentChannel = null,
            liveGroup = ""
        )
        TvPlaybackKind.MOVIE -> tvMoviePlaybackSubtitle(session.movie)
    }
    val playbackActive = playerState.playbackState == PlaybackState.PLAYING ||
        playerState.playbackState == PlaybackState.BUFFERING ||
        isChannelSwitching
    val utilityActions = remember(
        playbackKind,
        session.availableChannels.isNotEmpty(),
        session.previousEpisode?.id,
        session.nextEpisode?.id,
        playerState.audioTracks.isNotEmpty(),
        playerState.subtitleTracks.isNotEmpty()
    ) {
        tvPlayerUtilityActions(
            playbackKind = playbackKind,
            hasChannels = session.availableChannels.isNotEmpty(),
            hasPreviousEpisode = session.previousEpisode != null,
            hasNextEpisode = session.nextEpisode != null,
            hasAudioTracks = playerState.audioTracks.isNotEmpty(),
            hasSubtitleTracks = playerState.subtitleTracks.isNotEmpty()
        )
    }
    val availableOverlayActions = remember(
        playbackKind,
        playerState.isSeekable,
        previousLiveChannel?.id,
        nextLiveChannel?.id,
        session.previousEpisode?.id,
        session.nextEpisode?.id,
        utilityActions
    ) {
        buildSet {
            add(TvOverlayAction.BACK)
            add(TvOverlayAction.PLAY_PAUSE)
            if (playbackKind == TvPlaybackKind.LIVE) {
                add(TvOverlayAction.GO_LIVE)
                if (previousLiveChannel != null) add(TvOverlayAction.MAIN_PREVIOUS)
                if (nextLiveChannel != null) add(TvOverlayAction.MAIN_NEXT)
                if (playerState.isSeekable) {
                    add(TvOverlayAction.SEEK_BACKWARD)
                    add(TvOverlayAction.SEEK_FORWARD)
                    add(TvOverlayAction.SEEK)
                }
            } else {
                if (playerState.isSeekable) {
                    add(TvOverlayAction.SEEK_BACKWARD)
                    add(TvOverlayAction.SEEK_FORWARD)
                    add(TvOverlayAction.SEEK)
                }
                if (playbackKind == TvPlaybackKind.SERIES) {
                    if (session.previousEpisode != null) add(TvOverlayAction.MAIN_PREVIOUS)
                    if (session.nextEpisode != null) add(TvOverlayAction.MAIN_NEXT)
                }
            }
            addAll(
                utilityActions.filterNot {
                    it == TvOverlayAction.PREVIOUS_EPISODE ||
                        it == TvOverlayAction.NEXT_EPISODE
                }
            )
        }
    }

    val rootFocusRequester = remember { FocusRequester() }
    val overlayActionRequesters = remember {
        TvOverlayAction.entries.associateWith { FocusRequester() }
    }

    var overlayVisible by rememberSaveable { mutableStateOf(true) }
    var activePanel by rememberSaveable { mutableStateOf(TvPlayerPanel.NONE) }
    var panelReturnTarget by rememberSaveable { mutableStateOf(TvPlayerPanel.NONE) }
    var panelOriginOverlayVisible by rememberSaveable { mutableStateOf(true) }
    var settingsPreferredFocusKey by rememberSaveable { mutableStateOf<String?>(null) }
    var channelPanelGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val sessionPanelGroup = session.liveGroup
        .takeIf { it.isNotBlank() }
        ?: session.currentChannel?.groupTitle?.takeIf { it.isNotBlank() }
    val channelPanelChannels = channelBrowserChannels.ifEmpty {
        if (channelPanelGroup == sessionPanelGroup) {
            session.availableChannels
        } else {
            emptyList()
        }
    }
    val defaultOverlayAction = if (
        isLivePlayback && TvOverlayAction.CHANNELS in availableOverlayActions
    ) {
        TvOverlayAction.CHANNELS
    } else {
        TvOverlayAction.PLAY_PAUSE
    }
    var lastOverlayAction by rememberSaveable { mutableStateOf(defaultOverlayAction) }
    var requestedOverlayAction by rememberSaveable { mutableStateOf(defaultOverlayAction) }
    var currentOverlayAction by remember { mutableStateOf<TvOverlayAction?>(null) }
    var interactionVersion by remember { mutableIntStateOf(0) }
    var isExiting by rememberSaveable { mutableStateOf(false) }
    var channelNumberInput by remember { mutableStateOf("") }
    var transientZapAction by rememberSaveable { mutableStateOf<TvOverlayAction?>(null) }
    var transientZapTitle by rememberSaveable { mutableStateOf("") }
    var transientZapVersion by remember { mutableIntStateOf(0) }
    var transientSeekPosition by remember { mutableStateOf<Long?>(null) }
    var transientPlaybackWillPlay by remember { mutableStateOf<Boolean?>(null) }
    // Some TV remotes report OK as ENTER. The KeyDown opens the overlay and moves focus to
    // its default action before the matching KeyUp arrives. If that release is allowed to
    // continue into the newly focused button, the same physical press also clicks Channels.
    // Keep ownership of the whole press until its release so OK only reveals the chrome.
    var consumeOverlayOpeningConfirmRelease by remember { mutableStateOf(false) }
    val showPlaybackError =
        (playerState.playbackState == PlaybackState.ERROR || !liveChannelSwitch.errorMessage.isNullOrBlank()) &&
            !isChannelSwitching &&
            !(
                isLivePlayback &&
                    activePanel in listOf(TvPlayerPanel.CHANNELS, TvPlayerPanel.CATEGORIES) &&
                    session.availableChannels.isNotEmpty()
                )

    fun registerInteraction() {
        interactionVersion += 1
    }

    fun releaseFocusBeforeStructureChange(reason: String) {
        runCatching { focusManager.clearFocus(force = true) }
            .onFailure { error ->
                Timber.tag(TV_PLAYER_LOG_TAG).w(
                    error,
                    "Unable to clear focus before %s",
                    reason
                )
            }
    }

    LaunchedEffect(url, contentId, isLivePlayback, settings.startFullscreenLive) {
        releaseFocusBeforeStructureChange("new playback session")
        overlayVisible = !(isLivePlayback && settings.startFullscreenLive)
        val defaultAction = if (
            isLivePlayback && TvOverlayAction.CHANNELS in availableOverlayActions
        ) {
            TvOverlayAction.CHANNELS
        } else {
            TvOverlayAction.PLAY_PAUSE
        }
        lastOverlayAction = defaultAction
        requestedOverlayAction = defaultAction
        currentOverlayAction = null
        panelReturnTarget = TvPlayerPanel.NONE
        panelOriginOverlayVisible = overlayVisible
        settingsPreferredFocusKey = null
        if (!overlayVisible) {
            activePanel = TvPlayerPanel.NONE
        }
        channelPanelGroup = groupContext.takeIf { it.isNotBlank() }
        transientZapAction = null
        transientZapTitle = ""
        transientSeekPosition = null
        transientPlaybackWillPlay = null
    }

    fun showOverlay(action: TvOverlayAction = lastOverlayAction) {
        Timber.tag(TV_PLAYER_LOG_TAG).d(
            "showOverlay action=%s panel=%s visible=%s",
            action,
            activePanel,
            overlayVisible
        )
        if (!overlayVisible && activePanel == TvPlayerPanel.NONE) {
            releaseFocusBeforeStructureChange("opening player overlay")
        }
        requestedOverlayAction = action
        overlayVisible = true
        registerInteraction()
    }

    fun hideOverlay() {
        Timber.tag(TV_PLAYER_LOG_TAG).d("hideOverlay panel=%s", activePanel)
        releaseFocusBeforeStructureChange("hiding player overlay")
        overlayVisible = false
        activePanel = TvPlayerPanel.NONE
        panelReturnTarget = TvPlayerPanel.NONE
        registerInteraction()
    }

    fun closePanel() {
        releaseFocusBeforeStructureChange("closing player panel $activePanel")
        val backTarget = tvPlayerPanelBackTarget(activePanel, panelReturnTarget)
        if (backTarget != TvPlayerPanel.NONE) {
            activePanel = backTarget
            if (backTarget == panelReturnTarget) {
                panelReturnTarget = TvPlayerPanel.NONE
            }
            overlayVisible = true
            registerInteraction()
        } else {
            activePanel = TvPlayerPanel.NONE
            if (panelOriginOverlayVisible) {
                showOverlay(lastOverlayAction)
            } else {
                overlayVisible = false
                registerInteraction()
            }
        }
    }

    fun showTransientZapFeedback(action: TvOverlayAction, title: String) {
        transientZapAction = action
        transientZapTitle = title
        transientZapVersion += 1
        Timber.tag(TV_PLAYER_LOG_TAG).d(
            "showTransientZapFeedback action=%s title=%s",
            action,
            title
        )
    }

    fun clearTransientZapFeedback() {
        transientZapAction = null
        transientZapTitle = ""
        transientSeekPosition = null
        transientPlaybackWillPlay = null
    }

    fun resolvedOverlayAction(action: TvOverlayAction): TvOverlayAction {
        return when {
            action in availableOverlayActions -> action
            lastOverlayAction in availableOverlayActions -> lastOverlayAction
            isLivePlayback && TvOverlayAction.CHANNELS in availableOverlayActions -> TvOverlayAction.CHANNELS
            else -> TvOverlayAction.PLAY_PAUSE
        }
    }

    fun exitPlayer() {
        if (isExiting) return
        isExiting = true
        hideOverlay()
        viewModel.exitPlayer(onBack)
    }

    fun handleBack(): Boolean {
        return when {
            channelNumberInput.isNotBlank() -> {
                channelNumberInput = ""
                true
            }

            showPlaybackError -> {
                // The error surface is modal. Hiding an underlying overlay leaves the error in
                // place and makes Back feel ignored; match its visible Cancel action instead.
                exitPlayer()
                true
            }

            activePanel != TvPlayerPanel.NONE -> {
                closePanel()
                true
            }

            overlayVisible -> {
                hideOverlay()
                true
            }

            else -> {
                exitPlayer()
                true
            }
        }
    }

    DisposableEffect(activity, playbackActive) {
        if (playbackActive) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(activity) {
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        insetsController?.let { controller ->
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(insetsController, overlayVisible, activePanel, playbackActive) {
        if (playbackActive) {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.saveProgress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(viewModel) {
        viewModel.configureTvPlayback(true)
        onDispose {
            viewModel.configureTvPlayback(false)
        }
    }

    LaunchedEffect(url, title, contentId, contentType, startPosition, groupContext) {
        viewModel.init(url, title, startPosition, contentId, contentType, groupContext)
    }

    val currentChannel = session.currentChannel
    LaunchedEffect(currentChannel?.id, currentChannel?.epgChannelId, currentChannel?.name) {
        viewModel.loadEpgForChannel(currentChannel)
    }

    LaunchedEffect(activePanel, session.liveGroup, currentChannel?.groupTitle) {
        if (activePanel != TvPlayerPanel.CHANNELS && activePanel != TvPlayerPanel.CATEGORIES) {
            channelPanelGroup = session.liveGroup
                .takeIf { it.isNotBlank() }
                ?: currentChannel?.groupTitle?.takeIf { it.isNotBlank() }
        }
    }

    LaunchedEffect(availableOverlayActions, currentOverlayAction) {
        val targetAction = resolvedOverlayAction(requestedOverlayAction)
        if (requestedOverlayAction != targetAction) {
            requestedOverlayAction = targetAction
        }
        if (lastOverlayAction !in availableOverlayActions) {
            lastOverlayAction = targetAction
        }
        if (
            overlayVisible &&
            activePanel == TvPlayerPanel.NONE &&
            currentOverlayAction != null &&
            currentOverlayAction !in availableOverlayActions
        ) {
            currentOverlayAction = null
            requestedOverlayAction = targetAction
        }
    }

    LaunchedEffect(
        showPlaybackError,
        showBlockingPlaybackOverlay,
        overlayVisible,
        activePanel,
        requestedOverlayAction
    ) {
        val targetAction = resolvedOverlayAction(requestedOverlayAction)
        when {
            showPlaybackError -> Unit
            // Each panel owns and restores its own initial focus. Giving the blocking spinner
            // root focus first can steal the first D-pad event while a channel is still opening.
            activePanel != TvPlayerPanel.NONE -> Unit
            showBlockingPlaybackOverlay -> {
                rootFocusRequester.requestFocusWhenReady("player blocking surface")
            }
            overlayVisible -> {
                Timber.tag(TV_PLAYER_LOG_TAG).d(
                    "requestOverlayFocus action=%s panel=%s",
                    targetAction,
                    activePanel
                )
                val focused = overlayActionRequesters
                    .getValue(targetAction)
                    .requestFocusWhenReady("player overlay action $targetAction")
                if (!focused) {
                    val fallbackAction = defaultOverlayAction
                    if (fallbackAction != targetAction && fallbackAction in availableOverlayActions) {
                        requestedOverlayAction = fallbackAction
                    }
                }
            }
            else -> {
                rootFocusRequester.requestFocusWhenReady("player root surface")
            }
        }
    }

    LaunchedEffect(
        overlayVisible,
        activePanel,
        interactionVersion,
        playerState.isPlaying,
        settings.controllerAutoHideMs
    ) {
        if (overlayVisible && activePanel == TvPlayerPanel.NONE && playerState.isPlaying) {
            kotlinx.coroutines.delay(settings.controllerAutoHideMs.coerceAtLeast(1_000L))
            if (overlayVisible && activePanel == TvPlayerPanel.NONE && playerState.isPlaying) {
                hideOverlay()
            }
        }
    }

    LaunchedEffect(playerState.playbackState, isChannelSwitching) {
        if (
            playerState.playbackState == PlaybackState.ERROR &&
            !isChannelSwitching
        ) {
            showOverlay(TvOverlayAction.PLAY_PAUSE)
        }
    }

    LaunchedEffect(liveChannelSwitch.errorMessage) {
        if (!liveChannelSwitch.errorMessage.isNullOrBlank()) {
            if (isLivePlayback && session.availableChannels.isNotEmpty()) {
                clearTransientZapFeedback()
                releaseFocusBeforeStructureChange("opening failed-channel browser")
                requestedOverlayAction = TvOverlayAction.CHANNELS
                overlayVisible = true
                activePanel = TvPlayerPanel.CHANNELS
                channelPanelGroup = session.liveGroup
                    .takeIf { it.isNotBlank() }
                    ?: session.currentChannel?.groupTitle?.takeIf { it.isNotBlank() }
                viewModel.loadChannelBrowser(channelPanelGroup)
                registerInteraction()
            } else {
                showOverlay(defaultOverlayAction)
            }
        }
    }

    LaunchedEffect(activePanel, session.availableChannels.map(Channel::id), channelBrowserChannels.map(Channel::id)) {
        if (activePanel == TvPlayerPanel.CHANNELS) {
            if (channelPanelChannels.isNotEmpty()) {
                viewModel.loadEpgForChannels(channelPanelChannels)
            }
        }
    }

    LaunchedEffect(activePanel, currentChannel?.id, currentChannel?.epgChannelId, currentChannel?.name) {
        if (activePanel == TvPlayerPanel.EPG) {
            viewModel.loadEpgProgramsForChannel(currentChannel)
        }
    }

    LaunchedEffect(transientZapVersion) {
        if (transientZapAction == null) return@LaunchedEffect
        val version = transientZapVersion
        kotlinx.coroutines.delay(if (transientSeekPosition != null) 1_600L else 900L)
        if (transientZapVersion == version) {
            clearTransientZapFeedback()
        }
    }

    BackHandler(enabled = !isExiting, onBack = ::handleBack)

    // Channel number input timeout — navigate after 2 seconds
    LaunchedEffect(channelNumberInput) {
        if (channelNumberInput.isNotBlank() && isLivePlayback) {
            kotlinx.coroutines.delay(2000)
            val targetNumber = channelNumberInput.toIntOrNull()
            channelNumberInput = ""
            if (targetNumber != null && targetNumber > 0) {
                val channels = session.availableChannels
                val targetIndex = targetNumber - 1
                if (targetIndex in channels.indices) {
                    val targetChannel = channels[targetIndex]
                    viewModel.playChannel(targetChannel)
                    hideOverlay()
                    showTransientZapFeedback(TvOverlayAction.MAIN_NEXT, targetChannel.name)
                }
            }
        }
    }

    fun openRemotePanel(
        panel: TvPlayerPanel,
        action: TvOverlayAction = TvOverlayAction.SETTINGS,
        returnTarget: TvPlayerPanel = TvPlayerPanel.NONE,
        preferredSettingsKey: String? = null
    ): Boolean {
        clearTransientZapFeedback()
        channelNumberInput = ""
        if (activePanel != panel) {
            releaseFocusBeforeStructureChange("opening player panel $panel")
        }
        if (activePanel == TvPlayerPanel.NONE) {
            panelOriginOverlayVisible = overlayVisible
        }
        panelReturnTarget = returnTarget
        settingsPreferredFocusKey = preferredSettingsKey
        requestedOverlayAction = action
        overlayVisible = true
        activePanel = panel
        registerInteraction()
        return true
    }

    fun selectedLiveChannelGroup(): String? {
        return channelPanelGroup
            ?: session.liveGroup.takeIf { it.isNotBlank() }
            ?: session.currentChannel?.groupTitle?.takeIf { it.isNotBlank() }
    }

    fun openLiveChannelPanel(group: String? = selectedLiveChannelGroup()): Boolean {
        if (!isLivePlayback || session.availableChannels.isEmpty()) return false
        channelPanelGroup = group?.takeIf { it.isNotBlank() }
        viewModel.loadChannelBrowser(channelPanelGroup)
        return openRemotePanel(TvPlayerPanel.CHANNELS, TvOverlayAction.CHANNELS)
    }

    fun openLiveCategoryPanel(): Boolean {
        if (!isLivePlayback || session.liveGroups.isEmpty()) return false
        return openRemotePanel(TvPlayerPanel.CATEGORIES, TvOverlayAction.CHANNELS)
    }

    fun selectLiveChannelGroup(group: String?) {
        releaseFocusBeforeStructureChange("switching channel category")
        channelPanelGroup = group?.takeIf { it.isNotBlank() }
        viewModel.loadChannelBrowser(channelPanelGroup)
        requestedOverlayAction = TvOverlayAction.CHANNELS
        overlayVisible = true
        activePanel = TvPlayerPanel.CHANNELS
        panelReturnTarget = TvPlayerPanel.NONE
        registerInteraction()
    }

    fun openLiveEpgPanel(): Boolean {
        if (!isLivePlayback) return false
        viewModel.loadEpgProgramsForChannel(session.currentChannel)
        return openRemotePanel(TvPlayerPanel.EPG, TvOverlayAction.EPG)
    }

    fun handleRemotePlayPause(forcePlay: Boolean? = null): Boolean {
        registerInteraction()
        clearTransientZapFeedback()
        val willPlay = forcePlay ?: !playerState.isPlaying
        val shouldToggle = willPlay != playerState.isPlaying
        if (shouldToggle) {
            viewModel.togglePlayPause()
        }
        if (overlayVisible) {
            showOverlay(TvOverlayAction.PLAY_PAUSE)
        } else {
            transientPlaybackWillPlay = willPlay
            showTransientZapFeedback(TvOverlayAction.PLAY_PAUSE, displayTitle)
        }
        return true
    }

    fun seekVodWithoutInterruptingPlayback(
        deltaMs: Long,
        action: TvOverlayAction,
        revealOverlay: Boolean
    ): Boolean {
        val duration = playerState.duration
        val basePosition = if (revealOverlay) {
            playerState.currentPosition
        } else {
            transientSeekPosition ?: playerState.currentPosition
        }
        val target = resolveRelativeSeekPosition(
            currentPosition = basePosition,
            deltaMs = deltaMs,
            duration = duration,
            isSeekable = playerState.isSeekable
        )
        if (target == null) {
            Timber.tag(TV_PLAYER_LOG_TAG).d("Ignoring TV seek without a finite seek window")
            return true
        }

        viewModel.seekTo(target)
        if (revealOverlay) {
            clearTransientZapFeedback()
            showOverlay(action)
        } else {
            transientSeekPosition = target
            showTransientZapFeedback(action, StringUtils.formatDuration(target))
        }
        return true
    }

    fun handleRemotePrevious(
        revealOverlayForVodSeek: Boolean = overlayVisible,
        seekStepMs: Long = settings.seekBackwardMs
    ): Boolean {
        if (activePanel != TvPlayerPanel.NONE) return false

        registerInteraction()
        if (isLivePlayback) {
            val targetChannel = previousLiveChannel
            Timber.tag(TV_PLAYER_LOG_TAG).d(
                "remote previous live current=%s target=%s switching=%s",
                session.currentChannel?.name,
                targetChannel?.name,
                isChannelSwitching
            )
            if (targetChannel != null) {
                showTransientZapFeedback(TvOverlayAction.MAIN_PREVIOUS, targetChannel.name)
                viewModel.playPreviousChannel()
            } else if (overlayVisible) {
                clearTransientZapFeedback()
                showOverlay(TvOverlayAction.MAIN_PREVIOUS)
            }
        } else {
            seekVodWithoutInterruptingPlayback(
                deltaMs = -seekStepMs,
                action = TvOverlayAction.MAIN_PREVIOUS,
                revealOverlay = revealOverlayForVodSeek
            )
        }
        return true
    }

    fun handleRemoteNext(
        revealOverlayForVodSeek: Boolean = overlayVisible,
        seekStepMs: Long = settings.seekForwardMs
    ): Boolean {
        if (activePanel != TvPlayerPanel.NONE) return false

        registerInteraction()
        if (isLivePlayback) {
            val targetChannel = nextLiveChannel
            Timber.tag(TV_PLAYER_LOG_TAG).d(
                "remote next live current=%s target=%s switching=%s",
                session.currentChannel?.name,
                targetChannel?.name,
                isChannelSwitching
            )
            if (targetChannel != null) {
                showTransientZapFeedback(TvOverlayAction.MAIN_NEXT, targetChannel.name)
                viewModel.playNextChannel()
            } else if (overlayVisible) {
                clearTransientZapFeedback()
                showOverlay(TvOverlayAction.MAIN_NEXT)
            }
        } else {
            seekVodWithoutInterruptingPlayback(
                deltaMs = seekStepMs,
                action = TvOverlayAction.MAIN_NEXT,
                revealOverlay = revealOverlayForVodSeek
            )
        }
        return true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            // Keep this focus target attached for the entire playback session. Toggling the
            // focusable node off while an overlay/panel child was focused left Compose with an
            // ActiveParent and no child, which caused the reported fatal D-pad crash.
            .focusable()
            .onPreviewKeyEvent { event ->
                if (isExiting) {
                    return@onPreviewKeyEvent true
                }
                val isConfirmKey =
                    event.key == Key.DirectionCenter || event.key == Key.Enter
                if (isConfirmKey && consumeOverlayOpeningConfirmRelease) {
                    if (event.type == KeyEventType.KeyUp) {
                        consumeOverlayOpeningConfirmRelease = false
                    }
                    // Also consume repeat KeyDown events while OK/Enter remains held.
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                val isRepeatedKeyDown = event.nativeKeyEvent.repeatCount > 0

                when {
                    // Back and media/menu shortcuts are actions, not continuous navigation.
                    // Consume Android auto-repeat so holding one key cannot chain hide/exit or
                    // toggle playback twice. D-pad channel/seek navigation remains repeatable.
                    event.isTvBackKey() && isRepeatedKeyDown -> true

                    event.isTvBackKey() -> handleBack()

                    event.isTvPlayPauseToggleKey() && isRepeatedKeyDown -> true

                    event.isTvPlayKey() && isRepeatedKeyDown -> true

                    event.isTvPauseKey() && isRepeatedKeyDown -> true

                    (event.isTvMenuKey() || event.isTvInfoKey() || event.isTvCaptionsKey() ||
                        event.isTvAudioTrackKey()) && isRepeatedKeyDown -> true

                    // A visible error owns the interaction. Keep remote media shortcuts from
                    // changing the hidden player state while its Retry/Cancel actions own focus.
                    showPlaybackError && (
                        event.isTvPlayPauseToggleKey() ||
                            event.isTvPlayKey() ||
                            event.isTvPauseKey() ||
                            event.isTvPreviousShortcutKey() ||
                            event.isTvNextShortcutKey() ||
                            event.isTvMenuKey() ||
                            event.isTvInfoKey() ||
                            event.isTvCaptionsKey() ||
                            event.isTvAudioTrackKey()
                        ) -> true

                    // Let the error surface's focused Retry/Cancel controls receive D-pad and
                    // confirm events, but never run the player-root shortcuts behind it.
                    showPlaybackError && event.key in listOf(
                        Key.DirectionUp,
                        Key.DirectionDown,
                        Key.DirectionLeft,
                        Key.DirectionRight,
                        Key.DirectionCenter,
                        Key.Enter
                    ) -> false

                    // A live channel list or EPG may be opened while the current stream is still
                    // buffering. Only actions that have no visible target remain blocked here;
                    // left/right continue below and open their respective panels immediately.
                    showBlockingPlaybackOverlay &&
                        activePanel == TvPlayerPanel.NONE &&
                        event.key in listOf(
                        Key.DirectionUp,
                        Key.DirectionDown,
                        Key.DirectionCenter,
                        Key.Enter
                    ) -> true

                    event.isTvPlayPauseToggleKey() -> handleRemotePlayPause()

                    event.isTvPlayKey() -> handleRemotePlayPause(forcePlay = true)

                    event.isTvPauseKey() -> handleRemotePlayPause(forcePlay = false)

                    event.isTvPreviousShortcutKey() -> handleRemotePrevious(
                        seekStepMs = tvSeekStepForRepeat(
                            settings.seekBackwardMs,
                            event.nativeKeyEvent.repeatCount
                        )
                    )

                    event.isTvNextShortcutKey() -> handleRemoteNext(
                        seekStepMs = tvSeekStepForRepeat(
                            settings.seekForwardMs,
                            event.nativeKeyEvent.repeatCount
                        )
                    )

                    event.isTvMenuKey() && activePanel == TvPlayerPanel.NONE -> {
                        openRemotePanel(TvPlayerPanel.SETTINGS, TvOverlayAction.SETTINGS)
                    }

                    event.isTvInfoKey() -> {
                        openRemotePanel(TvPlayerPanel.STREAM_INFO, TvOverlayAction.SETTINGS)
                    }

                    event.isTvCaptionsKey() -> {
                        openRemotePanel(TvPlayerPanel.SUBTITLE, TvOverlayAction.SUBTITLE)
                    }

                    event.isTvAudioTrackKey() -> {
                        openRemotePanel(TvPlayerPanel.AUDIO, TvOverlayAction.AUDIO)
                    }

                    event.key == Key.DirectionCenter || event.key == Key.Enter -> {
                        if (!overlayVisible && activePanel == TvPlayerPanel.NONE) {
                            consumeOverlayOpeningConfirmRelease = true
                            clearTransientZapFeedback()
                            Timber.tag(TV_PLAYER_LOG_TAG).d("remote center opens overlay")
                            showOverlay(defaultOverlayAction)
                            true
                        } else {
                            false
                        }
                    }

                    event.key == Key.DirectionUp || event.key == Key.DirectionDown -> {
                        if (!overlayVisible && activePanel == TvPlayerPanel.NONE) {
                            clearTransientZapFeedback()
                            if (isLivePlayback) {
                                Timber.tag(TV_PLAYER_LOG_TAG).d("remote %s switches live channel", event.key)
                                if (event.key == Key.DirectionUp) {
                                    handleRemotePrevious()
                                } else {
                                    handleRemoteNext()
                                }
                            } else {
                                Timber.tag(TV_PLAYER_LOG_TAG).d("remote %s opens overlay", event.key)
                                showOverlay(TvOverlayAction.PLAY_PAUSE)
                                true
                            }
                        } else {
                            false
                        }
                    }

                    event.key == Key.DirectionLeft -> {
                        if (
                            tvPlayerPanelDirectionalAction(
                                isLivePlayback = isLivePlayback,
                                activePanel = activePanel,
                                direction = TvPlayerPanelDirection.LEFT
                            ) == TvPlayerPanelDirectionalAction.OPEN_CATEGORIES
                        ) {
                            // Consume the edge key even if the category list is temporarily
                            // empty. Otherwise Compose can try to move focus out of a panel that
                            // is being replaced by the channel LazyColumn.
                            openLiveCategoryPanel()
                            true
                        } else if (
                            (!overlayVisible || showBlockingPlaybackOverlay) &&
                            activePanel == TvPlayerPanel.NONE
                        ) {
                            when (
                                tvPlayerHiddenDirectionalAction(
                                    isLivePlayback = isLivePlayback,
                                    isSeekable = playerState.isSeekable,
                                    direction = TvPlayerPanelDirection.LEFT
                                )
                            ) {
                                TvPlayerHiddenDirectionalAction.OPEN_CHANNELS -> openLiveChannelPanel()
                                TvPlayerHiddenDirectionalAction.SEEK_BACKWARD ->
                                    handleRemotePrevious(
                                        revealOverlayForVodSeek = false,
                                        seekStepMs = tvSeekStepForRepeat(
                                            settings.seekBackwardMs,
                                            event.nativeKeyEvent.repeatCount
                                        )
                                    )
                                else -> true
                            }
                        } else {
                            false
                        }
                    }

                    event.key == Key.DirectionRight -> {
                        if (
                            tvPlayerPanelDirectionalAction(
                                isLivePlayback = isLivePlayback,
                                activePanel = activePanel,
                                direction = TvPlayerPanelDirection.RIGHT
                            ) == TvPlayerPanelDirectionalAction.OPEN_CHANNELS
                        ) {
                            // See the matching LEFT branch: this is the safety boundary for the
                            // reported right-D-pad escape/crash path.
                            openLiveChannelPanel(channelPanelGroup)
                            true
                        } else if (
                            (!overlayVisible || showBlockingPlaybackOverlay) &&
                            activePanel == TvPlayerPanel.NONE
                        ) {
                            when (
                                tvPlayerHiddenDirectionalAction(
                                    isLivePlayback = isLivePlayback,
                                    isSeekable = playerState.isSeekable,
                                    direction = TvPlayerPanelDirection.RIGHT
                                )
                            ) {
                                TvPlayerHiddenDirectionalAction.OPEN_EPG -> openLiveEpgPanel()
                                TvPlayerHiddenDirectionalAction.SEEK_FORWARD ->
                                    handleRemoteNext(
                                        revealOverlayForVodSeek = false,
                                        seekStepMs = tvSeekStepForRepeat(
                                            settings.seekForwardMs,
                                            event.nativeKeyEvent.repeatCount
                                        )
                                    )
                                else -> true
                            }
                        } else {
                            false
                        }
                    }

                    else -> {
                        // Digit keys for channel number input (TV remote)
                        if (isLivePlayback && activePanel == TvPlayerPanel.NONE && event.nativeKeyEvent.repeatCount == 0) {
                            val digit = event.tvRemoteDigit()
                            if (digit != null && channelNumberInput.length < 4) {
                                clearTransientZapFeedback()
                                channelNumberInput += digit
                                registerInteraction()
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
                }
            }
    ) {
        PlayerSurfaceHost(
            modifier = Modifier.fillMaxSize(),
            playerEngine = viewModel.playerManager.getEngine(),
            playerState = playerState,
            surfaceState = PlayerSurfaceHostState(
                playerReady = playerReady,
                playbackActive = playbackActive,
                shellMode = PlayerShellMode.TV
            )
        )

        if (showBlockingPlaybackOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (showSwitchingOverlay || playerState.playbackState == PlaybackState.BUFFERING) {
                            Color.Black.copy(alpha = 0.18f)
                        } else {
                            Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(54.dp),
                        color = IdealPlayerColors.Primary
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = when {
                            showSwitchingOverlay -> liveChannelSwitch.targetTitle.ifBlank {
                                stringResource(R.string.channel_list)
                            }

                            !playerReady -> stringResource(R.string.loading)
                            else -> stringResource(R.string.buffering)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }

        if (showPlaybackError) {
            TvErrorState(
                message = liveChannelSwitch.errorMessage
                    ?: playerState.errorMessage
                    ?: stringResource(R.string.playback_error),
                onRetry = {
                    showOverlay(TvOverlayAction.PLAY_PAUSE)
                    viewModel.clearLiveChannelSwitchError()
                    viewModel.retryCurrent()
                },
                onExit = ::exitPlayer
            )
        }

        AnimatedVisibility(
            visible = !showPlaybackError &&
                !showBlockingPlaybackOverlay &&
                overlayVisible &&
                activePanel == TvPlayerPanel.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            TvPlaybackOverlay(
                title = displayTitle,
                subtitle = displaySubtitle,
                playbackKind = playbackKind,
                utilityActions = utilityActions,
                playerState = playerState,
                isLivePlayback = isLivePlayback,
                isChannelSwitching = isChannelSwitching,
                currentEpgProgram = currentEpgProgram,
                nextEpgProgram = nextEpgProgram,
                hasPreviousContent = when (playbackKind) {
                    TvPlaybackKind.LIVE -> previousLiveChannel != null
                    TvPlaybackKind.SERIES -> session.previousEpisode != null
                    TvPlaybackKind.MOVIE -> false
                },
                hasNextContent = when (playbackKind) {
                    TvPlaybackKind.LIVE -> nextLiveChannel != null
                    TvPlaybackKind.SERIES -> session.nextEpisode != null
                    TvPlaybackKind.MOVIE -> false
                },
                sleepTimerRemainingMs = sleepTimerState.remainingMs.takeIf { sleepTimerState.isActive },
                overlayActionRequesters = overlayActionRequesters,
                onActionFocused = { action ->
                    currentOverlayAction = action
                    lastOverlayAction = action
                    requestedOverlayAction = action
                    registerInteraction()
                },
                seekBackwardMs = settings.seekBackwardMs,
                seekForwardMs = settings.seekForwardMs,
                onBack = ::exitPlayer,
                onPreviousContent = {
                    registerInteraction()
                    when (playbackKind) {
                        TvPlaybackKind.LIVE -> viewModel.playPreviousChannel()
                        TvPlaybackKind.SERIES -> viewModel.playPreviousEpisode()
                        TvPlaybackKind.MOVIE -> Unit
                    }
                },
                onSeekBackward = {
                    registerInteraction()
                    viewModel.seekBackward()
                },
                onPlayPause = {
                    registerInteraction()
                    viewModel.togglePlayPause()
                },
                onSeekTo = { position ->
                    registerInteraction()
                    viewModel.seekTo(position)
                },
                onGoLive = {
                    registerInteraction()
                    if (playerState.duration > 0L && playerState.isSeekable) {
                        viewModel.seekTo(playerState.duration)
                    } else {
                        viewModel.retryCurrent()
                    }
                },
                onSeekForward = {
                    registerInteraction()
                    viewModel.seekForward()
                },
                onNextContent = {
                    registerInteraction()
                    when (playbackKind) {
                        TvPlaybackKind.LIVE -> viewModel.playNextChannel()
                        TvPlaybackKind.SERIES -> viewModel.playNextEpisode()
                        TvPlaybackKind.MOVIE -> Unit
                    }
                },
                onOpenChannels = {
                    registerInteraction()
                    openLiveChannelPanel()
                },
                onOpenEpg = {
                    registerInteraction()
                    openLiveEpgPanel()
                },
                onOpenAudio = {
                    registerInteraction()
                    openRemotePanel(TvPlayerPanel.AUDIO, TvOverlayAction.AUDIO)
                },
                onOpenSubtitle = {
                    registerInteraction()
                    openRemotePanel(TvPlayerPanel.SUBTITLE, TvOverlayAction.SUBTITLE)
                },
                onOpenScreenMode = {
                    registerInteraction()
                    openRemotePanel(TvPlayerPanel.SCREEN_MODE, TvOverlayAction.SCREEN_MODE)
                },
                onOpenQuality = {
                    registerInteraction()
                    openRemotePanel(TvPlayerPanel.QUALITY, TvOverlayAction.SCREEN_MODE)
                },
                onOpenSettings = {
                    registerInteraction()
                    settingsPreferredFocusKey = null
                    openRemotePanel(TvPlayerPanel.SETTINGS, TvOverlayAction.SETTINGS)
                },
                onPreviousEpisode = {
                    registerInteraction()
                    viewModel.playPreviousEpisode()
                },
                onNextEpisode = {
                    registerInteraction()
                    viewModel.playNextEpisode()
                }
            )
        }

        if (
            !showPlaybackError &&
            activePanel != TvPlayerPanel.NONE
        ) {
            TvPlayerPanelHost(
                modifier = Modifier.fillMaxSize(),
                activePanel = activePanel,
                playbackKind = playbackKind,
                hasParentPanel = panelReturnTarget != TvPlayerPanel.NONE,
                preferredSettingsKey = settingsPreferredFocusKey,
                playerState = playerState,
                diagnostics = diagnostics,
                supportsQualitySelection = tvSupportsQualitySelection(
                    engineName = diagnostics.engineName,
                    isAdaptiveStream = playerState.isAdaptiveStream,
                    availableQualityCount = playerState.availableQualities.count { it.index >= 0 }
                ),
                channels = channelPanelChannels,
                channelGroups = session.liveGroups,
                selectedChannelGroup = channelPanelGroup,
                currentChannelId = liveChannelSwitch.targetChannelId ?: session.currentChannel?.id ?: contentId,
                epgPrograms = channelListEpgPrograms,
                currentChannelTitle = session.currentChannel?.name ?: displayTitle,
                currentChannelEpgPrograms = currentChannelEpgPrograms.ifEmpty {
                    listOfNotNull(currentEpgProgram, nextEpgProgram)
                },
                sleepTimerActive = sleepTimerState.isActive,
                sleepTimerSelectedMinutes = sleepTimerState.selectedMinutes,
                sleepTimerRemainingMs = sleepTimerState.remainingMs,
                sleepTimerOptions = viewModel.sleepTimerOptions,
                onDismiss = ::closePanel,
                onSelectChannel = { channel ->
                    viewModel.playChannel(channel)
                    hideOverlay()
                    showTransientZapFeedback(TvOverlayAction.MAIN_NEXT, channel.name)
                },
                onSelectChannelGroup = ::selectLiveChannelGroup,
                onSelectAudio = {
                    if (playerState.selectedAudioTrack != it) {
                        viewModel.selectAudio(it)
                    }
                },
                onSelectSubtitle = {
                    if (playerState.selectedSubtitleTrack != it) {
                        viewModel.selectSubtitle(it)
                    }
                },
                onDisableSubtitles = {
                    viewModel.disableSubtitles()
                },
                onSelectAspectRatio = {
                    if (playerState.aspectRatioMode != it) {
                        viewModel.setAspectRatio(it)
                    }
                    closePanel()
                },
                onSelectSpeed = {
                    viewModel.setSpeed(it)
                    closePanel()
                },
                onSelectQualityMode = {
                    viewModel.setVideoQualityMode(it)
                    closePanel()
                },
                onSelectVideoTrack = {
                    viewModel.selectVideoTrack(it)
                    closePanel()
                },
                onSelectSleepTimer = {
                    if (it == null) viewModel.cancelSleepTimer() else viewModel.setSleepTimer(it)
                    closePanel()
                },
                onOpenScreenModePanel = {
                    openRemotePanel(
                        TvPlayerPanel.SCREEN_MODE,
                        returnTarget = TvPlayerPanel.SETTINGS,
                        preferredSettingsKey = "settings:aspect-ratio"
                    )
                },
                onOpenQualityPanel = {
                    openRemotePanel(
                        TvPlayerPanel.QUALITY,
                        returnTarget = TvPlayerPanel.SETTINGS,
                        preferredSettingsKey = "settings:quality"
                    )
                },
                onOpenAudioPanel = {
                    openRemotePanel(
                        TvPlayerPanel.AUDIO,
                        returnTarget = TvPlayerPanel.SETTINGS,
                        preferredSettingsKey = "settings:audio"
                    )
                },
                onOpenSubtitlePanel = {
                    openRemotePanel(
                        TvPlayerPanel.SUBTITLE,
                        returnTarget = TvPlayerPanel.SETTINGS,
                        preferredSettingsKey = "settings:subtitle"
                    )
                },
                onOpenSpeedPanel = {
                    openRemotePanel(
                        TvPlayerPanel.SPEED,
                        returnTarget = TvPlayerPanel.SETTINGS,
                        preferredSettingsKey = "settings:speed"
                    )
                },
                onOpenSleepTimerPanel = {
                    openRemotePanel(
                        TvPlayerPanel.SLEEP_TIMER,
                        returnTarget = TvPlayerPanel.SETTINGS,
                        preferredSettingsKey = "settings:sleep-timer"
                    )
                },
                onOpenStreamInfoPanel = {
                    openRemotePanel(
                        TvPlayerPanel.STREAM_INFO,
                        returnTarget = TvPlayerPanel.SETTINGS,
                        preferredSettingsKey = "settings:stream-info"
                    )
                },
                onRetry = {
                    viewModel.retryCurrent()
                    closePanel()
                },
                onCopyDiagnostics = {
                    clipboardManager.setText(AnnotatedString(viewModel.buildDiagnosticsReport()))
                    Toast.makeText(
                        context,
                        context.getString(R.string.playback_diagnostics_copied),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        // Channel number overlay
        AnimatedVisibility(
            visible = channelNumberInput.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(40.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(2.dp, IdealPlayerColors.Primary)
            ) {
                Text(
                    text = channelNumberInput,
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = transientZapAction != null && !overlayVisible && activePanel == TvPlayerPanel.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.82f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, IdealPlayerColors.Primary.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            transientZapAction == TvOverlayAction.PLAY_PAUSE &&
                                transientPlaybackWillPlay == true -> Icons.Filled.PlayArrow
                            transientZapAction == TvOverlayAction.PLAY_PAUSE -> Icons.Filled.Pause
                            isLivePlayback && transientZapAction == TvOverlayAction.MAIN_PREVIOUS ->
                                Icons.Filled.SkipPrevious
                            isLivePlayback -> Icons.Filled.SkipNext
                            transientZapAction == TvOverlayAction.MAIN_PREVIOUS -> Icons.Filled.Replay10
                            else -> Icons.Filled.Forward10
                        },
                        contentDescription = null,
                        tint = IdealPlayerColors.Primary
                    )
                    Column {
                        Text(
                            text = when {
                                transientZapAction == TvOverlayAction.PLAY_PAUSE &&
                                    transientPlaybackWillPlay == true ->
                                    stringResource(R.string.player_playing_feedback)
                                transientZapAction == TvOverlayAction.PLAY_PAUSE ->
                                    stringResource(R.string.player_paused_feedback)
                                isLivePlayback && transientZapAction == TvOverlayAction.MAIN_PREVIOUS ->
                                    stringResource(R.string.previous_channel)
                                isLivePlayback -> stringResource(R.string.next_channel)
                                transientZapAction == TvOverlayAction.MAIN_PREVIOUS ->
                                    stringResource(
                                        R.string.seek_backward_feedback,
                                        tvSeekStepSeconds(settings.seekBackwardMs)
                                    )
                                else -> stringResource(
                                    R.string.seek_forward_feedback,
                                    tvSeekStepSeconds(settings.seekForwardMs)
                                )
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = IdealPlayerColors.TextSecondary
                        )
                        Text(
                            text = transientZapTitle.ifBlank { displayTitle },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** A2 TV player chrome from Figma node 96:2193. */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvPlaybackOverlay(
    title: String,
    subtitle: String,
    playbackKind: TvPlaybackKind,
    utilityActions: List<TvOverlayAction>,
    playerState: PlayerState,
    isLivePlayback: Boolean,
    isChannelSwitching: Boolean,
    currentEpgProgram: com.idealplayer.app.data.parser.EpgProgram?,
    nextEpgProgram: com.idealplayer.app.data.parser.EpgProgram?,
    hasPreviousContent: Boolean,
    hasNextContent: Boolean,
    sleepTimerRemainingMs: Long?,
    overlayActionRequesters: Map<TvOverlayAction, FocusRequester>,
    onActionFocused: (TvOverlayAction) -> Unit,
    seekBackwardMs: Long,
    seekForwardMs: Long,
    onBack: () -> Unit,
    onPreviousContent: () -> Unit,
    onSeekBackward: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onGoLive: () -> Unit,
    onSeekForward: () -> Unit,
    onNextContent: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenEpg: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onOpenScreenMode: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenSettings: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit
) {
    val shouldShowVodTimeline = !isLivePlayback &&
        playerState.duration > 0L && playerState.isSeekable
    val timelineFocusAction = when {
        shouldShowVodTimeline -> TvOverlayAction.SEEK
        isLivePlayback -> TvOverlayAction.GO_LIVE
        else -> TvOverlayAction.BACK
    }
    val timelineFocusRequester = overlayActionRequesters.getValue(timelineFocusAction)
    val firstTransportAction = when {
        hasPreviousContent -> TvOverlayAction.MAIN_PREVIOUS
        playerState.isSeekable -> TvOverlayAction.SEEK_BACKWARD
        else -> TvOverlayAction.PLAY_PAUSE
    }
    val lastTransportAction = when {
        hasNextContent -> TvOverlayAction.MAIN_NEXT
        playerState.isSeekable -> TvOverlayAction.SEEK_FORWARD
        else -> TvOverlayAction.PLAY_PAUSE
    }
    val leftActions = listOf(
        TvOverlayAction.CHANNELS,
        TvOverlayAction.AUDIO,
        TvOverlayAction.SUBTITLE,
        TvOverlayAction.EPG
    ).filter(utilityActions::contains)
    val rightActions = listOf(
        TvOverlayAction.SCREEN_MODE,
        TvOverlayAction.SETTINGS
    ).filter(utilityActions::contains)
    val resolutionBadge = videoResolutionBadge(playerState.videoWidth, playerState.videoHeight)
        .ifBlank { playerState.currentVideoResolution }
    val metadata = listOfNotNull(
        subtitle.takeIf { it.isNotBlank() },
        stringResource(R.string.player_content_live).takeIf { isLivePlayback },
        resolutionBadge.takeIf { it.isNotBlank() }
    ).distinct().joinToString(" • ")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        IdealPlayerColors.Background.copy(alpha = 0.94f),
                        Color.Transparent,
                        Color.Transparent,
                        IdealPlayerColors.Background.copy(alpha = 0.98f)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvPlayerIconAction(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.player_back),
                    focusRequester = overlayActionRequesters.getValue(TvOverlayAction.BACK),
                    downFocusRequester = timelineFocusRequester,
                    onFocused = { onActionFocused(TvOverlayAction.BACK) },
                    onClick = onBack
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.widthIn(max = 840.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displaySmall,
                        color = IdealPlayerColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (metadata.isNotBlank()) {
                        Text(
                            text = metadata,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isLivePlayback) IdealPlayerColors.Primary else
                                IdealPlayerColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sleepTimerRemainingMs?.let { remainingMs ->
                    TvStatusBadge(
                        label = stringResource(
                            R.string.player_sleep_timer_badge,
                            StringUtils.formatDuration(remainingMs)
                        ),
                        compact = true
                    )
                }
                if (isChannelSwitching || playerState.playbackState == PlaybackState.BUFFERING) {
                    TvStatusBadge(
                        label = stringResource(R.string.buffering),
                        background = IdealPlayerColors.SurfaceElevated,
                        contentColor = IdealPlayerColors.Primary,
                        compact = true
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 300.dp)
                .background(IdealPlayerColors.Background.copy(alpha = 0.98f))
                .navigationBarsPadding()
                .padding(horizontal = 64.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (shouldShowVodTimeline) {
                TvSeekBar(
                    currentPosition = playerState.currentPosition,
                    duration = playerState.duration,
                    bufferedPosition = playerState.bufferedPosition,
                    backwardStepMs = seekBackwardMs,
                    forwardStepMs = seekForwardMs,
                    focusRequester = overlayActionRequesters.getValue(TvOverlayAction.SEEK),
                    upFocusRequester = overlayActionRequesters.getValue(TvOverlayAction.BACK),
                    downFocusRequester = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE),
                    onSeekTo = onSeekTo,
                    onFocused = { onActionFocused(TvOverlayAction.SEEK) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (isLivePlayback) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentEpgProgram != null) {
                        TvEpgStrip(
                            currentProgram = currentEpgProgram,
                            nextProgram = nextEpgProgram,
                            compact = true,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    TvActionChip(
                        icon = Icons.Filled.KeyboardDoubleArrowRight,
                        label = stringResource(R.string.player_go_live),
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.GO_LIVE),
                        leftFocusRequester = FocusRequester.Cancel,
                        rightFocusRequester = FocusRequester.Cancel,
                        upFocusRequester = overlayActionRequesters.getValue(TvOverlayAction.BACK),
                        downFocusRequester = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE),
                        onFocused = { onActionFocused(TvOverlayAction.GO_LIVE) },
                        onClick = onGoLive
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            ) {
                TvA2UtilityRow(
                    actions = leftActions,
                    modifier = Modifier.align(Alignment.CenterStart),
                    overlayActionRequesters = overlayActionRequesters,
                    leftEdgeRequester = FocusRequester.Cancel,
                    rightEdgeRequester = overlayActionRequesters.getValue(firstTransportAction),
                    upFocusRequester = timelineFocusRequester,
                    onActionFocused = onActionFocused,
                    onOpenChannels = onOpenChannels,
                    onOpenEpg = onOpenEpg,
                    onOpenAudio = onOpenAudio,
                    onOpenSubtitle = onOpenSubtitle,
                    onOpenQuality = onOpenQuality,
                    onOpenSettings = onOpenSettings,
                    onPreviousEpisode = onPreviousEpisode,
                    onNextEpisode = onNextEpisode
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvPlayerControlButton(
                        icon = Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(
                            if (isLivePlayback) R.string.previous_channel else
                                R.string.previous_episode
                        ),
                        enabled = hasPreviousContent,
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.MAIN_PREVIOUS),
                        leftFocusRequester = leftActions.lastOrNull()?.let(overlayActionRequesters::getValue)
                            ?: FocusRequester.Cancel,
                        rightFocusRequester = if (playerState.isSeekable) {
                            overlayActionRequesters.getValue(TvOverlayAction.SEEK_BACKWARD)
                        } else {
                            overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE)
                        },
                        upFocusRequester = timelineFocusRequester,
                        onFocused = { onActionFocused(TvOverlayAction.MAIN_PREVIOUS) },
                        onClick = onPreviousContent
                    )
                    TvPlayerControlButton(
                        icon = Icons.Filled.Replay10,
                        contentDescription = stringResource(
                            R.string.seek_backward_short,
                            tvSeekStepSeconds(seekBackwardMs)
                        ),
                        enabled = playerState.isSeekable,
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.SEEK_BACKWARD),
                        leftFocusRequester = if (hasPreviousContent) {
                            overlayActionRequesters.getValue(TvOverlayAction.MAIN_PREVIOUS)
                        } else {
                            leftActions.lastOrNull()?.let(overlayActionRequesters::getValue)
                                ?: FocusRequester.Cancel
                        },
                        rightFocusRequester = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE),
                        upFocusRequester = timelineFocusRequester,
                        onFocused = { onActionFocused(TvOverlayAction.SEEK_BACKWARD) },
                        onClick = onSeekBackward
                    )
                    TvPlayerControlButton(
                        icon = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (playerState.isPlaying) R.string.action_pause else R.string.action_play
                        ),
                        enabled = true,
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE),
                        leftFocusRequester = if (playerState.isSeekable) {
                            overlayActionRequesters.getValue(TvOverlayAction.SEEK_BACKWARD)
                        } else if (hasPreviousContent) {
                            overlayActionRequesters.getValue(TvOverlayAction.MAIN_PREVIOUS)
                        } else {
                            FocusRequester.Cancel
                        },
                        rightFocusRequester = if (playerState.isSeekable) {
                            overlayActionRequesters.getValue(TvOverlayAction.SEEK_FORWARD)
                        } else if (hasNextContent) {
                            overlayActionRequesters.getValue(TvOverlayAction.MAIN_NEXT)
                        } else {
                            FocusRequester.Cancel
                        },
                        upFocusRequester = timelineFocusRequester,
                        onFocused = { onActionFocused(TvOverlayAction.PLAY_PAUSE) },
                        onClick = onPlayPause
                    )
                    TvPlayerControlButton(
                        icon = Icons.Filled.Forward10,
                        contentDescription = stringResource(
                            R.string.seek_forward_short,
                            tvSeekStepSeconds(seekForwardMs)
                        ),
                        enabled = playerState.isSeekable,
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.SEEK_FORWARD),
                        leftFocusRequester = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE),
                        rightFocusRequester = if (hasNextContent) {
                            overlayActionRequesters.getValue(TvOverlayAction.MAIN_NEXT)
                        } else {
                            rightActions.firstOrNull()?.let(overlayActionRequesters::getValue)
                                ?: FocusRequester.Cancel
                        },
                        upFocusRequester = timelineFocusRequester,
                        onFocused = { onActionFocused(TvOverlayAction.SEEK_FORWARD) },
                        onClick = onSeekForward
                    )
                    TvPlayerControlButton(
                        icon = Icons.Filled.SkipNext,
                        contentDescription = stringResource(
                            if (isLivePlayback) R.string.next_channel else R.string.next_episode
                        ),
                        enabled = hasNextContent,
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.MAIN_NEXT),
                        leftFocusRequester = if (playerState.isSeekable) {
                            overlayActionRequesters.getValue(TvOverlayAction.SEEK_FORWARD)
                        } else {
                            overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE)
                        },
                        rightFocusRequester = rightActions.firstOrNull()?.let(overlayActionRequesters::getValue)
                            ?: FocusRequester.Cancel,
                        upFocusRequester = timelineFocusRequester,
                        onFocused = { onActionFocused(TvOverlayAction.MAIN_NEXT) },
                        onClick = onNextContent
                    )
                }

                TvA2UtilityRow(
                    actions = rightActions,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    overlayActionRequesters = overlayActionRequesters,
                    leftEdgeRequester = overlayActionRequesters.getValue(lastTransportAction),
                    rightEdgeRequester = FocusRequester.Cancel,
                    upFocusRequester = timelineFocusRequester,
                    onActionFocused = onActionFocused,
                    onOpenChannels = onOpenChannels,
                    onOpenEpg = onOpenEpg,
                    onOpenAudio = onOpenAudio,
                    onOpenSubtitle = onOpenSubtitle,
                    onOpenQuality = onOpenQuality,
                    onOpenSettings = onOpenSettings,
                    onPreviousEpisode = onPreviousEpisode,
                    onNextEpisode = onNextEpisode
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvA2UtilityRow(
    actions: List<TvOverlayAction>,
    modifier: Modifier = Modifier,
    overlayActionRequesters: Map<TvOverlayAction, FocusRequester>,
    leftEdgeRequester: FocusRequester,
    rightEdgeRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    onActionFocused: (TvOverlayAction) -> Unit,
    onOpenChannels: () -> Unit,
    onOpenEpg: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenSettings: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit
) {
    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEachIndexed { index, action ->
            val icon = when (action) {
                TvOverlayAction.CHANNELS -> Icons.AutoMirrored.Filled.List
                TvOverlayAction.EPG -> Icons.Filled.Info
                TvOverlayAction.PREVIOUS_EPISODE -> Icons.Filled.SkipPrevious
                TvOverlayAction.NEXT_EPISODE -> Icons.Filled.SkipNext
                TvOverlayAction.AUDIO -> Icons.Filled.Audiotrack
                TvOverlayAction.SUBTITLE -> Icons.Filled.Subtitles
                TvOverlayAction.SCREEN_MODE -> Icons.Filled.HighQuality
                else -> Icons.Filled.Tune
            }
            val label = when (action) {
                TvOverlayAction.CHANNELS -> stringResource(R.string.channels)
                TvOverlayAction.EPG -> stringResource(R.string.epg)
                TvOverlayAction.PREVIOUS_EPISODE -> stringResource(R.string.previous_episode)
                TvOverlayAction.NEXT_EPISODE -> stringResource(R.string.next_episode)
                TvOverlayAction.AUDIO -> stringResource(R.string.setting_audio_track)
                TvOverlayAction.SUBTITLE -> stringResource(R.string.setting_subtitles)
                TvOverlayAction.SCREEN_MODE -> stringResource(R.string.setting_video_quality)
                else -> stringResource(R.string.player_options)
            }
            val onClick = when (action) {
                TvOverlayAction.CHANNELS -> onOpenChannels
                TvOverlayAction.EPG -> onOpenEpg
                TvOverlayAction.PREVIOUS_EPISODE -> onPreviousEpisode
                TvOverlayAction.NEXT_EPISODE -> onNextEpisode
                TvOverlayAction.AUDIO -> onOpenAudio
                TvOverlayAction.SUBTITLE -> onOpenSubtitle
                TvOverlayAction.SCREEN_MODE -> onOpenQuality
                else -> onOpenSettings
            }
            TvActionChip(
                icon = icon,
                label = label,
                focusRequester = overlayActionRequesters.getValue(action),
                leftFocusRequester = if (index == 0) leftEdgeRequester else null,
                rightFocusRequester = if (index == actions.lastIndex) rightEdgeRequester else null,
                upFocusRequester = upFocusRequester,
                downFocusRequester = FocusRequester.Cancel,
                onFocused = { onActionFocused(action) },
                onClick = onClick
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvPlayerIconAction(
    icon: ImageVector,
    contentDescription: String,
    focusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusState = rememberTvFocusVisualState(
        isFocused = focused,
        defaultSurface = Color.Transparent,
        focusedSurface = IdealPlayerColors.SurfaceFocus
    )
    Surface(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = focusState.scale
                scaleY = focusState.scale
                shadowElevation = focusState.shadowElevation.toPx()
            }
            .focusRequester(focusRequester)
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                up = FocusRequester.Cancel
                down = downFocusRequester
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = focusState.backgroundColor,
        border = BorderStroke(focusState.borderWidth, focusState.borderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = focusState.contentColor,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvPlayerControlButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    focusRequester: FocusRequester,
    leftFocusRequester: FocusRequester,
    rightFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusState = rememberTvFocusVisualState(
        isFocused = focused,
        defaultSurface = IdealPlayerColors.CardBackground,
        focusedSurface = IdealPlayerColors.SurfaceFocus,
        defaultContentColor = IdealPlayerColors.TextPrimary,
        focusedContentColor = IdealPlayerColors.TextPrimary
    )
    Surface(
        modifier = Modifier
            .size(72.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer {
                scaleX = focusState.scale
                scaleY = focusState.scale
                shadowElevation = focusState.shadowElevation.toPx()
                ambientShadowColor = IdealPlayerColors.FocusGlow
                spotShadowColor = IdealPlayerColors.FocusGlow
            }
            .focusRequester(focusRequester)
            .focusProperties {
                canFocus = enabled
                left = leftFocusRequester
                right = rightFocusRequester
                up = upFocusRequester
                down = FocusRequester.Cancel
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = focusState.backgroundColor,
        border = BorderStroke(
            width = focusState.borderWidth.coerceAtLeast(1.dp),
            color = if (focusState.borderWidth > 0.dp) {
                focusState.borderColor
            } else {
                IdealPlayerColors.CardBorder
            }
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = focusState.contentColor,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun LegacyTvPlaybackOverlay(
    title: String,
    subtitle: String,
    playbackKind: TvPlaybackKind,
    utilityActions: List<TvOverlayAction>,
    playerState: PlayerState,
    isLivePlayback: Boolean,
    isChannelSwitching: Boolean,
    currentEpgProgram: com.idealplayer.app.data.parser.EpgProgram?,
    nextEpgProgram: com.idealplayer.app.data.parser.EpgProgram?,
    hasPreviousChannel: Boolean,
    hasNextChannel: Boolean,
    sleepTimerRemainingMs: Long?,
    overlayActionRequesters: Map<TvOverlayAction, FocusRequester>,
    onActionFocused: (TvOverlayAction) -> Unit,
    seekBackwardMs: Long,
    seekForwardMs: Long,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onNext: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenEpg: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitle: () -> Unit,
    onOpenScreenMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit
) {
    val shouldShowProgress = !isLivePlayback && playerState.duration > 0L && playerState.isSeekable
    val primaryUpRequester = overlayActionRequesters.getValue(
        if (shouldShowProgress) TvOverlayAction.SEEK else TvOverlayAction.BACK
    )
    val headerDownRequester = overlayActionRequesters.getValue(
        if (shouldShowProgress) TvOverlayAction.SEEK else TvOverlayAction.PLAY_PAUSE
    )
    val previousEnabled = if (isLivePlayback) hasPreviousChannel else playerState.isSeekable
    val nextEnabled = if (isLivePlayback) hasNextChannel else playerState.isSeekable
    val utilityListState = rememberLazyListState()
    var focusedUtilityAction by remember { mutableStateOf<TvOverlayAction?>(null) }
    val resolutionBadge = remember(
        playerState.videoWidth,
        playerState.videoHeight,
        playerState.currentVideoResolution
    ) {
        videoResolutionBadge(playerState.videoWidth, playerState.videoHeight)
            .ifBlank { playerState.currentVideoResolution }
    }
    val fpsBadge = remember(playerState.currentVideoFps) {
        playerState.currentVideoFps.replace("fps", "FPS", ignoreCase = true)
    }
    val networkSpeedBadge = remember(playerState.networkSpeedKbps) {
        formatNetworkSpeed(playerState.networkSpeedKbps)
    }

    LaunchedEffect(focusedUtilityAction, utilityActions) {
        val focusedIndex = utilityActions.indexOf(focusedUtilityAction)
        if (focusedIndex in utilityActions.indices) {
            utilityListState.animateScrollToItem(focusedIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        IdealPlayerColors.Background.copy(alpha = 0.92f),
                        Color.Transparent,
                        Color.Transparent,
                        IdealPlayerColors.Background.copy(alpha = 0.92f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 54.dp, vertical = 38.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvActionChip(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = stringResource(R.string.player_exit),
                        focusRequester = overlayActionRequesters.getValue(TvOverlayAction.BACK),
                        downFocusRequester = headerDownRequester,
                        trapDirectionalFocus = true,
                        onFocused = { onActionFocused(TvOverlayAction.BACK) },
                        onClick = onBack
                    )
                    Spacer(modifier = Modifier.width(18.dp))
                    TvContentBadge(playbackKind)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.widthIn(max = 760.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = IdealPlayerColors.TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sleepTimerRemainingMs?.let { remainingMs ->
                        TvStatusBadge(
                            label = stringResource(
                                R.string.player_sleep_timer_badge,
                                StringUtils.formatDuration(remainingMs)
                            ),
                            compact = true
                        )
                    }
                    if (resolutionBadge.isNotBlank()) {
                        TvStatusBadge(label = resolutionBadge, compact = true)
                    }
                    if (fpsBadge.isNotBlank()) {
                        TvStatusBadge(label = fpsBadge, compact = true)
                    }
                    if (networkSpeedBadge.isNotBlank()) {
                        TvStatusBadge(
                            label = stringResource(
                                R.string.player_network_speed_badge,
                                networkSpeedBadge
                            ),
                            compact = true
                        )
                    }
                    if (isChannelSwitching) {
                        TvStatusBadge(
                            label = stringResource(R.string.buffering),
                            background = IdealPlayerColors.Warning.copy(alpha = 0.22f),
                            contentColor = IdealPlayerColors.Warning
                        )
                    } else if (playerState.playbackState == PlaybackState.BUFFERING) {
                        TvStatusBadge(
                            label = stringResource(R.string.buffering),
                            background = IdealPlayerColors.Secondary.copy(alpha = 0.22f),
                            contentColor = IdealPlayerColors.Secondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(if (isLivePlayback) 22.dp else 28.dp),
                color = Color.Black.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (isLivePlayback) 18.dp else 22.dp,
                        vertical = if (isLivePlayback) 12.dp else 18.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(
                        if (isLivePlayback) 10.dp else 18.dp
                    )
                ) {
                    if (isLivePlayback && currentEpgProgram != null) {
                        TvEpgStrip(
                            currentProgram = currentEpgProgram,
                            nextProgram = nextEpgProgram,
                            compact = true
                        )
                    }

                    if (shouldShowProgress) {
                        TvSeekBar(
                            currentPosition = playerState.currentPosition,
                            duration = playerState.duration,
                            bufferedPosition = playerState.bufferedPosition,
                            backwardStepMs = seekBackwardMs,
                            forwardStepMs = seekForwardMs,
                            focusRequester = overlayActionRequesters.getValue(TvOverlayAction.SEEK),
                            upFocusRequester = overlayActionRequesters.getValue(TvOverlayAction.BACK),
                            downFocusRequester = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE),
                            onSeekTo = onSeekTo,
                            onFocused = { onActionFocused(TvOverlayAction.SEEK) }
                        )
                    }

                    Row(
                        modifier = Modifier.focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(
                            if (isLivePlayback) 12.dp else 18.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvActionButton(
                            icon = if (isLivePlayback) Icons.Filled.SkipPrevious else Icons.Filled.Replay10,
                            label = if (isLivePlayback) stringResource(R.string.previous_channel) else
                                stringResource(R.string.seek_backward_short, tvSeekStepSeconds(seekBackwardMs)),
                            enabled = previousEnabled,
                            focusRequester = overlayActionRequesters.getValue(TvOverlayAction.MAIN_PREVIOUS),
                            leftFocusRequester = FocusRequester.Cancel,
                            rightFocusRequester = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE),
                            upFocusRequester = primaryUpRequester,
                            onFocused = { onActionFocused(TvOverlayAction.MAIN_PREVIOUS) },
                            compact = isLivePlayback,
                            onClick = onPrevious
                        )
                        TvActionButton(
                            icon = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            label = if (playerState.isPlaying) stringResource(R.string.action_pause) else
                                stringResource(R.string.action_play),
                            enabled = true,
                            primary = true,
                            focusRequester = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE),
                            leftFocusRequester = overlayActionRequesters
                                .getValue(TvOverlayAction.MAIN_PREVIOUS)
                                .takeIf { previousEnabled } ?: FocusRequester.Cancel,
                            rightFocusRequester = overlayActionRequesters
                                .getValue(TvOverlayAction.MAIN_NEXT)
                                .takeIf { nextEnabled } ?: FocusRequester.Cancel,
                            upFocusRequester = primaryUpRequester,
                            onFocused = { onActionFocused(TvOverlayAction.PLAY_PAUSE) },
                            compact = isLivePlayback,
                            onClick = onPlayPause
                        )
                        TvActionButton(
                            icon = if (isLivePlayback) Icons.Filled.SkipNext else Icons.Filled.Forward10,
                            label = if (isLivePlayback) stringResource(R.string.next_channel) else
                                stringResource(R.string.seek_forward_short, tvSeekStepSeconds(seekForwardMs)),
                            enabled = nextEnabled,
                            focusRequester = overlayActionRequesters.getValue(TvOverlayAction.MAIN_NEXT),
                            leftFocusRequester = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE),
                            rightFocusRequester = FocusRequester.Cancel,
                            upFocusRequester = primaryUpRequester,
                            onFocused = { onActionFocused(TvOverlayAction.MAIN_NEXT) },
                            compact = isLivePlayback,
                            onClick = onNext
                        )
                    }

                    LazyRow(
                        state = utilityListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(
                            if (isLivePlayback) 10.dp else 14.dp
                        ),
                        contentPadding = PaddingValues(
                            horizontal = 2.dp,
                            vertical = if (isLivePlayback) 1.dp else 2.dp
                        )
                    ) {
                        itemsIndexed(
                            items = utilityActions,
                            key = { _, action -> action.name }
                        ) { index, action ->
                            val icon = when (action) {
                                TvOverlayAction.CHANNELS -> Icons.AutoMirrored.Filled.List
                                TvOverlayAction.EPG -> Icons.Filled.Info
                                TvOverlayAction.PREVIOUS_EPISODE -> Icons.Filled.SkipPrevious
                                TvOverlayAction.NEXT_EPISODE -> Icons.Filled.SkipNext
                                TvOverlayAction.AUDIO -> Icons.Filled.Audiotrack
                                TvOverlayAction.SUBTITLE -> Icons.Filled.Subtitles
                                TvOverlayAction.SCREEN_MODE -> Icons.Filled.AspectRatio
                                else -> Icons.Filled.Settings
                            }
                            val label = when (action) {
                                TvOverlayAction.CHANNELS -> stringResource(R.string.channel_list)
                                TvOverlayAction.EPG -> stringResource(R.string.epg)
                                TvOverlayAction.PREVIOUS_EPISODE -> stringResource(R.string.previous_episode)
                                TvOverlayAction.NEXT_EPISODE -> stringResource(R.string.next_episode)
                                TvOverlayAction.AUDIO -> stringResource(R.string.setting_audio_track)
                                TvOverlayAction.SUBTITLE -> stringResource(R.string.setting_subtitles)
                                TvOverlayAction.SCREEN_MODE -> stringResource(R.string.setting_display_mode)
                                else -> stringResource(R.string.nav_settings)
                            }
                            val clickAction = when (action) {
                                TvOverlayAction.CHANNELS -> onOpenChannels
                                TvOverlayAction.EPG -> onOpenEpg
                                TvOverlayAction.PREVIOUS_EPISODE -> onPreviousEpisode
                                TvOverlayAction.NEXT_EPISODE -> onNextEpisode
                                TvOverlayAction.AUDIO -> onOpenAudio
                                TvOverlayAction.SUBTITLE -> onOpenSubtitle
                                TvOverlayAction.SCREEN_MODE -> onOpenScreenMode
                                else -> onOpenSettings
                            }
                            TvActionChip(
                                icon = icon,
                                label = label,
                                focusRequester = overlayActionRequesters.getValue(action),
                                // Interior neighbors are intentionally left to LazyRow's
                                // beyond-bounds focus search so an off-screen item is composed and
                                // scrolled into view before focus moves. Only viewport edges trap.
                                leftFocusRequester = FocusRequester.Cancel.takeIf { index == 0 },
                                rightFocusRequester = FocusRequester.Cancel.takeIf {
                                    index == utilityActions.lastIndex
                                },
                                upFocusRequester = overlayActionRequesters.getValue(TvOverlayAction.PLAY_PAUSE),
                                downFocusRequester = FocusRequester.Cancel,
                                onFocused = {
                                    focusedUtilityAction = action
                                    onActionFocused(action)
                                },
                                compact = isLivePlayback,
                                onClick = clickAction
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvEpgStrip(
    currentProgram: com.idealplayer.app.data.parser.EpgProgram,
    nextProgram: com.idealplayer.app.data.parser.EpgProgram?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val nowMs = System.currentTimeMillis()
    val duration = (currentProgram.endTime - currentProgram.startTime).coerceAtLeast(1L)
    val progress = ((nowMs - currentProgram.startTime).toFloat() / duration.toFloat())
        .coerceIn(0f, 1f)
    val timeFormatter = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val startTime = remember(currentProgram.startTime) {
        timeFormatter.format(java.util.Date(currentProgram.startTime))
    }
    val endTime = remember(currentProgram.endTime) {
        timeFormatter.format(java.util.Date(currentProgram.endTime))
    }

    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.48f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 14.dp else 18.dp,
                    vertical = if (compact) 9.dp else 14.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = IdealPlayerColors.Primary.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = stringResource(R.string.guide_now),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(
                            horizontal = if (compact) 6.dp else 8.dp,
                            vertical = if (compact) 2.dp else 3.dp
                        )
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = currentProgram.title,
                    style = if (compact) MaterialTheme.typography.titleSmall else
                        MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "$startTime - $endTime",
                    style = if (compact) MaterialTheme.typography.bodySmall else
                        MaterialTheme.typography.bodyMedium,
                    color = IdealPlayerColors.TextSecondary
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 2.dp else 3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = IdealPlayerColors.Primary,
                trackColor = Color.White.copy(alpha = 0.2f)
            )

            nextProgram?.let { next ->
                Text(
                    text = stringResource(R.string.player_next_program, next.title),
                    style = if (compact) MaterialTheme.typography.bodySmall else
                        MaterialTheme.typography.bodyMedium,
                    color = IdealPlayerColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TvPlayerPanelHost(
    modifier: Modifier,
    activePanel: TvPlayerPanel,
    playbackKind: TvPlaybackKind,
    hasParentPanel: Boolean,
    preferredSettingsKey: String?,
    playerState: PlayerState,
    diagnostics: PlaybackDiagnostics,
    supportsQualitySelection: Boolean,
    channels: List<Channel>,
    channelGroups: List<String>,
    selectedChannelGroup: String?,
    currentChannelId: Long,
    epgPrograms: Map<Long, com.idealplayer.app.data.parser.EpgProgram>,
    currentChannelTitle: String,
    currentChannelEpgPrograms: List<com.idealplayer.app.data.parser.EpgProgram>,
    sleepTimerActive: Boolean,
    sleepTimerSelectedMinutes: Int,
    sleepTimerRemainingMs: Long,
    sleepTimerOptions: List<Int>,
    onDismiss: () -> Unit,
    onSelectChannel: (Channel) -> Unit,
    onSelectChannelGroup: (String?) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onDisableSubtitles: () -> Unit,
    onSelectAspectRatio: (AspectRatioMode) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectQualityMode: (VideoQualityMode) -> Unit,
    onSelectVideoTrack: (Int) -> Unit,
    onSelectSleepTimer: (Int?) -> Unit,
    onOpenScreenModePanel: () -> Unit,
    onOpenQualityPanel: () -> Unit,
    onOpenAudioPanel: () -> Unit,
    onOpenSubtitlePanel: () -> Unit,
    onOpenSpeedPanel: () -> Unit,
    onOpenSleepTimerPanel: () -> Unit,
    onOpenStreamInfoPanel: () -> Unit,
    onRetry: () -> Unit,
    onCopyDiagnostics: () -> Unit
) {
    val panelDismissLabel = stringResource(
        if (hasParentPanel || activePanel == TvPlayerPanel.CATEGORIES) {
            R.string.player_back
        } else {
            R.string.player_close
        }
    )
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.44f))
            .focusGroup(),
        contentAlignment = when (activePanel) {
            TvPlayerPanel.CHANNELS,
            TvPlayerPanel.CATEGORIES -> Alignment.CenterStart
            else -> Alignment.CenterEnd
        }
    ) {
        when (activePanel) {
            TvPlayerPanel.NONE -> Unit

            TvPlayerPanel.CHANNELS -> {
                val panelTitle = selectedChannelGroup
                    ?.takeIf { it.isNotBlank() }
                    ?.let { group -> "${stringResource(R.string.channels)} • $group" }
                    ?: stringResource(R.string.channel_list)
                TvOptionPanel(
                    title = panelTitle,
                    onDismiss = onDismiss,
                    dismissLabel = panelDismissLabel,
                    edgeToEdge = true,
                    panelSide = TvPanelSide.START,
                    width = 420.dp,
                    items = channels.map { channel ->
                        val epgProgram = epgPrograms[channel.id]
                        val isCurrentChannel = channel.id == currentChannelId
                        TvPanelOption(
                            key = "channel:${channel.id}",
                            title = channel.name,
                            subtitle = tvChannelPanelSubtitle(epgProgram),
                            progressFraction = epgProgram?.progressFraction,
                            selected = isCurrentChannel,
                            onClick = {
                                if (isCurrentChannel) onDismiss() else onSelectChannel(channel)
                            }
                        )
                    }
                )
            }

            TvPlayerPanel.CATEGORIES -> {
                val categoryItems = buildList {
                    add(
                        TvPanelOption(
                            key = "channel-group:all",
                            title = stringResource(R.string.category_all),
                            selected = selectedChannelGroup.isNullOrBlank(),
                            onClick = { onSelectChannelGroup(null) }
                        )
                    )
                    addAll(
                        channelGroups.map { group ->
                            TvPanelOption(
                                key = "channel-group:$group",
                                title = group,
                                selected = group == selectedChannelGroup,
                                onClick = { onSelectChannelGroup(group) }
                            )
                        }
                    )
                }

                TvOptionPanel(
                    title = stringResource(R.string.categories),
                    onDismiss = onDismiss,
                    dismissLabel = panelDismissLabel,
                    edgeToEdge = true,
                    panelSide = TvPanelSide.START,
                    width = 380.dp,
                    items = if (categoryItems.isNotEmpty()) {
                        categoryItems
                    } else {
                        listOf(
                            TvPanelOption(
                                key = "channel-group:empty",
                                title = stringResource(R.string.no_content),
                                enabled = false,
                                onClick = {}
                            )
                        )
                    }
                )
            }

            TvPlayerPanel.EPG -> {
                val nowMs = System.currentTimeMillis()
                TvOptionPanel(
                    title = "${stringResource(R.string.epg)} • $currentChannelTitle",
                    onDismiss = onDismiss,
                    dismissLabel = panelDismissLabel,
                    edgeToEdge = true,
                    panelSide = TvPanelSide.END,
                    width = 520.dp,
                    items = if (currentChannelEpgPrograms.isNotEmpty()) {
                        currentChannelEpgPrograms.map { program ->
                            val isCurrent = program.startTime <= nowMs && program.endTime > nowMs
                            TvPanelOption(
                                key = "epg:${program.startTime}:${program.endTime}:${program.title}",
                                title = if (isCurrent) {
                                    stringResource(R.string.player_now_program, program.title)
                                } else {
                                    program.title
                                },
                                subtitle = tvEpgPanelSubtitle(program),
                                progressFraction = program.progressFraction.takeIf { isCurrent },
                                selected = isCurrent,
                                onClick = {}
                            )
                        }
                    } else {
                        listOf(
                            TvPanelOption(
                                key = "epg:empty",
                                title = stringResource(R.string.epg_no_data),
                                enabled = false,
                                onClick = {}
                            )
                        )
                    }
                )
            }

            TvPlayerPanel.AUDIO -> {
                val options = if (playerState.audioTracks.isNotEmpty()) {
                    playerState.audioTracks.map { track ->
                        TvPanelOption(
                            key = "audio:${track.index}",
                            title = track.name,
                            subtitle = track.language.ifBlank { null },
                            selected = track.isSelected,
                            onClick = { onSelectAudio(track.index) }
                        )
                    }
                } else {
                    listOf(
                        TvPanelOption(
                            key = "audio:empty",
                            title = stringResource(R.string.no_content),
                            enabled = false,
                            onClick = {}
                        )
                    )
                }

                TvOptionPanel(
                    title = stringResource(R.string.setting_audio_track),
                    onDismiss = onDismiss,
                    dismissLabel = panelDismissLabel,
                    items = options
                )
            }

            TvPlayerPanel.SUBTITLE -> {
                val options = buildList {
                    add(
                        TvPanelOption(
                            key = "subtitle:disabled",
                            title = stringResource(R.string.language_off),
                            selected = playerState.selectedSubtitleTrack == -1,
                            onClick = onDisableSubtitles
                        )
                    )
                    addAll(
                        playerState.subtitleTracks.map { track ->
                            TvPanelOption(
                                key = "subtitle:${track.index}",
                                title = track.name,
                                subtitle = track.language.ifBlank { null },
                                selected = track.isSelected,
                                onClick = { onSelectSubtitle(track.index) }
                            )
                        }
                    )
                }

                TvOptionPanel(
                    title = stringResource(R.string.setting_subtitles),
                    onDismiss = onDismiss,
                    dismissLabel = panelDismissLabel,
                    items = options
                )
            }

            TvPlayerPanel.SCREEN_MODE -> {
                TvOptionPanel(
                    title = stringResource(R.string.setting_display_mode),
                    onDismiss = onDismiss,
                    dismissLabel = panelDismissLabel,
                    items = AspectRatioMode.entries.map { mode ->
                        TvPanelOption(
                            key = "aspect-ratio:${mode.name}",
                            title = tvAspectRatioLabel(mode),
                            selected = playerState.aspectRatioMode == mode,
                            onClick = { onSelectAspectRatio(mode) }
                        )
                    }
                )
            }

            TvPlayerPanel.QUALITY -> {
                val qualityItems = buildList {
                    addAll(
                        VideoQualityMode.entries.map { mode ->
                            TvPanelOption(
                                key = "quality-mode:${mode.name}",
                                title = tvQualityModeLabel(mode),
                                subtitle = stringResource(R.string.player_quality_policy),
                                selected = playerState.videoQualityMode == mode,
                                enabled = supportsQualitySelection,
                                onClick = { onSelectQualityMode(mode) }
                            )
                        }
                    )
                    addAll(
                        playerState.availableQualities
                            .filter { it.index >= 0 }
                            .map { quality ->
                                TvPanelOption(
                                    key = "quality-track:${quality.index}",
                                    title = quality.label,
                                    subtitle = quality.bitrate.takeIf { it > 0 }
                                        ?.let { bitrate -> "${bitrate / 1_000} kbps" },
                                    selected = quality.isSelected,
                                    enabled = supportsQualitySelection,
                                    onClick = { onSelectVideoTrack(quality.index) }
                                )
                            }
                    )
                }
                TvOptionPanel(
                    title = stringResource(R.string.setting_video_quality),
                    onDismiss = onDismiss,
                    dismissLabel = panelDismissLabel,
                    items = qualityItems
                )
            }

            TvPlayerPanel.SPEED -> {
                TvOptionPanel(
                    title = stringResource(R.string.setting_playback_speed),
                    onDismiss = onDismiss,
                    dismissLabel = panelDismissLabel,
                    items = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map { speed ->
                        TvPanelOption(
                            key = "speed:$speed",
                            title = "${speed}x",
                            selected = playerState.playbackSpeed == speed,
                            onClick = { onSelectSpeed(speed) }
                        )
                    }
                )
            }

            TvPlayerPanel.SLEEP_TIMER -> {
                TvOptionPanel(
                    title = stringResource(R.string.player_sleep_timer),
                    onDismiss = onDismiss,
                    dismissLabel = panelDismissLabel,
                    items = buildList {
                        add(
                            TvPanelOption(
                                key = "sleep-timer:off",
                                title = stringResource(R.string.language_off),
                                selected = !sleepTimerActive,
                                onClick = { onSelectSleepTimer(null) }
                            )
                        )
                        addAll(
                            sleepTimerOptions.map { minutes ->
                                TvPanelOption(
                                    key = "sleep-timer:$minutes",
                                    title = stringResource(R.string.player_minutes, minutes),
                                    subtitle = if (
                                        sleepTimerActive && sleepTimerSelectedMinutes == minutes
                                    ) {
                                        stringResource(
                                            R.string.player_sleep_timer_remaining,
                                            StringUtils.formatDuration(sleepTimerRemainingMs)
                                        )
                                    } else {
                                        null
                                    },
                                    selected = sleepTimerActive && sleepTimerSelectedMinutes == minutes,
                                    onClick = { onSelectSleepTimer(minutes) }
                                )
                            }
                        )
                    }
                )
            }

            TvPlayerPanel.SETTINGS -> {
                TvOptionPanel(
                    title = stringResource(R.string.nav_settings),
                    onDismiss = onDismiss,
                    dismissLabel = panelDismissLabel,
                    preferredFocusKey = preferredSettingsKey,
                    items = buildList {
                        add(
                            TvPanelOption(
                                key = "settings:aspect-ratio",
                                title = stringResource(R.string.setting_display_mode),
                                subtitle = tvAspectRatioLabel(playerState.aspectRatioMode),
                                onClick = onOpenScreenModePanel
                            )
                        )
                        add(
                            TvPanelOption(
                                key = "settings:quality",
                                title = stringResource(R.string.setting_video_quality),
                                subtitle = if (supportsQualitySelection) {
                                    tvQualityModeLabel(playerState.videoQualityMode)
                                } else {
                                    stringResource(R.string.player_not_available)
                                },
                                enabled = supportsQualitySelection,
                                onClick = onOpenQualityPanel
                            )
                        )
                        if (playbackKind != TvPlaybackKind.LIVE) {
                            add(
                                TvPanelOption(
                                    key = "settings:speed",
                                    title = stringResource(R.string.setting_playback_speed),
                                    subtitle = "${playerState.playbackSpeed}x",
                                    onClick = onOpenSpeedPanel
                                )
                            )
                        }
                        add(
                            TvPanelOption(
                                key = "settings:audio",
                                title = stringResource(R.string.setting_audio_track),
                                subtitle = playerState.audioTracks.firstOrNull { it.isSelected }?.name
                                    ?: stringResource(R.string.player_not_available),
                                enabled = playerState.audioTracks.isNotEmpty(),
                                onClick = onOpenAudioPanel
                            )
                        )
                        add(
                            TvPanelOption(
                                key = "settings:subtitle",
                                title = stringResource(R.string.setting_subtitles),
                                subtitle = playerState.subtitleTracks.firstOrNull { it.isSelected }?.name
                                    ?: stringResource(R.string.language_off),
                                onClick = onOpenSubtitlePanel
                            )
                        )
                        add(
                            TvPanelOption(
                                key = "settings:sleep-timer",
                                title = stringResource(R.string.player_sleep_timer),
                                subtitle = if (sleepTimerActive) {
                                    stringResource(
                                        R.string.player_sleep_timer_remaining,
                                        StringUtils.formatDuration(sleepTimerRemainingMs)
                                    )
                                } else {
                                    stringResource(R.string.language_off)
                                },
                                onClick = onOpenSleepTimerPanel
                            )
                        )
                        add(
                            TvPanelOption(
                                key = "settings:stream-info",
                                title = stringResource(R.string.setting_stream_info),
                                subtitle = videoResolutionBadge(
                                    playerState.videoWidth,
                                    playerState.videoHeight
                                ).ifBlank { playerState.currentVideoResolution.ifBlank { "—" } },
                                onClick = onOpenStreamInfoPanel
                            )
                        )
                        add(
                            TvPanelOption(
                                key = "settings:retry",
                                title = stringResource(R.string.retry),
                                subtitle = stringResource(R.string.player_retry_description),
                                onClick = onRetry
                            )
                        )
                    }
                )
            }

            TvPlayerPanel.STREAM_INFO -> {
                TvInfoPanel(
                    title = stringResource(R.string.setting_stream_info),
                    closeLabel = panelDismissLabel,
                    rows = listOf(
                        stringResource(R.string.player_info_engine) to diagnostics.engineName,
                        stringResource(R.string.player_info_playback) to
                            tvPlaybackStateLabel(playerState.playbackState),
                        stringResource(R.string.player_info_confirmed) to stringResource(
                            if (playerState.isPlaybackConfirmed) R.string.yes else R.string.no
                        ),
                        stringResource(R.string.player_info_resolution) to
                            videoResolutionBadge(
                                playerState.videoWidth,
                                playerState.videoHeight
                            ).ifBlank { playerState.currentVideoResolution.ifBlank { "—" } },
                        stringResource(R.string.player_info_bitrate) to
                            playerState.currentVideoBitrate.ifBlank { "—" },
                        stringResource(R.string.player_info_codec) to
                            playerState.currentVideoCodec.ifBlank { "—" },
                        stringResource(R.string.player_info_fps) to
                            playerState.currentVideoFps.ifBlank { "—" },
                        stringResource(R.string.player_info_network_speed) to
                            formatNetworkSpeed(playerState.networkSpeedKbps).ifBlank { "—" },
                        stringResource(R.string.player_info_buffer) to stringResource(
                            R.string.player_seconds_value,
                            (playerState.bufferedPosition - playerState.currentPosition)
                                .coerceAtLeast(0L) / 1_000f
                        ),
                        stringResource(R.string.player_info_source) to
                            "${diagnostics.streamProtocol}:${diagnostics.streamHost}",
                        stringResource(R.string.player_info_recovery) to when {
                            diagnostics.recovery.isRecovering ->
                                stringResource(R.string.player_recovery_in_progress)
                            diagnostics.recovery.automaticFallbackUsed ->
                                stringResource(R.string.player_recovery_vlc_used)
                            else -> stringResource(R.string.player_recovery_not_used)
                        }
                    ),
                    onCopy = onCopyDiagnostics,
                    onClose = onDismiss
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvOptionPanel(
    title: String,
    items: List<TvPanelOption>,
    onDismiss: () -> Unit,
    dismissLabel: String,
    preferredFocusKey: String? = null,
    modifier: Modifier = Modifier,
    edgeToEdge: Boolean = false,
    panelSide: TvPanelSide = TvPanelSide.END,
    width: Dp = 460.dp
) {
    val optionKeys = tvStablePlayerPanelKeys(items.map(TvPanelOption::key))
    val rememberedRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val optionRequesters = optionKeys.associateWith { key ->
        rememberedRequesters.getOrPut(key) { FocusRequester() }
    }
    val closeRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val preferredFocusIndex = items.indexOfFirst { item ->
        item.enabled && item.key == preferredFocusKey
    }.takeIf { it >= 0 }
    val initialFocusIndex = preferredFocusIndex ?: tvInitialPlayerPanelFocusIndex(
        selected = items.map(TvPanelOption::selected),
        enabled = items.map(TvPanelOption::enabled)
    )
    val firstEnabledIndex = items.indexOfFirst { it.enabled }.takeIf { it >= 0 }
    val lastEnabledIndex = items.indexOfLast { it.enabled }.takeIf { it >= 0 }
    val closeFocusKey = "__panel_close__"

    var visualFocusIndex by remember(title) {
        mutableStateOf(initialFocusIndex)
    }
    var focusedOptionKey by remember(title) { mutableStateOf<String?>(null) }

    LaunchedEffect(optionKeys, initialFocusIndex) {
        val preservedIndex = focusedOptionKey?.let(optionKeys::indexOf)?.takeIf { it >= 0 }
        when {
            focusedOptionKey == closeFocusKey -> visualFocusIndex = null
            preservedIndex != null && items.getOrNull(preservedIndex)?.enabled == true -> {
                visualFocusIndex = preservedIndex
            }
            initialFocusIndex != null -> {
                focusedOptionKey = null
            // The selected item can be outside the composed LazyColumn viewport. Scroll first,
            // then wait for a frame so requestFocus never targets an unattached requester.
                listState.scrollToItem(initialFocusIndex)
                val focused = optionRequesters
                    .getValue(optionKeys[initialFocusIndex])
                    .requestFocusWhenReady("player option panel $title")
                visualFocusIndex = initialFocusIndex.takeIf { focused }
            }
            else -> {
                // Empty and fully disabled panels still need an obvious, safe focus target.
                visualFocusIndex = null
                closeRequester.requestFocusWhenReady("player option panel close $title")
            }
        }
    }

    val panelModifier = if (edgeToEdge) {
        modifier
            .fillMaxHeight()
            .width(width)
    } else {
        modifier
            .padding(horizontal = 54.dp, vertical = 44.dp)
            .width(width)
            .fillMaxHeight(0.78f)
    }

    Surface(
        modifier = panelModifier,
        shape = if (edgeToEdge) panelSide.edgeShape(28.dp) else RoundedCornerShape(26.dp),
        color = IdealPlayerColors.Surface.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, IdealPlayerColors.CardBorder)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (edgeToEdge) 32.dp else 24.dp,
                        end = if (edgeToEdge) 24.dp else 18.dp,
                        top = if (edgeToEdge) 20.dp else 14.dp,
                        bottom = if (edgeToEdge) 20.dp else 14.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TvActionChip(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = dismissLabel,
                    focusRequester = closeRequester,
                    // The options list is lazy and can be positioned far from the initially
                    // selected item. Keep the fixed header edges trapped but let DOWN choose a
                    // currently composed option through spatial focus search.
                    leftFocusRequester = FocusRequester.Cancel,
                    rightFocusRequester = FocusRequester.Cancel,
                    upFocusRequester = FocusRequester.Cancel,
                    onFocused = {
                        visualFocusIndex = null
                        if (firstEnabledIndex != null) focusedOptionKey = closeFocusKey
                    },
                    onClick = onDismiss
                )
            }
            HorizontalDivider(color = IdealPlayerColors.DividerColor)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = if (edgeToEdge) 14.dp else 10.dp,
                    bottom = if (edgeToEdge) 30.dp else 10.dp
                )
            ) {
                if (items.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.no_content),
                            style = MaterialTheme.typography.bodyLarge,
                            color = IdealPlayerColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)
                        )
                    }
                } else {
                    itemsIndexed(
                        items = items,
                        key = { index, item -> optionKeys[index] }
                    ) { index, item ->
                        TvPanelOptionRow(
                            title = item.title,
                            subtitle = item.subtitle,
                            progressFraction = item.progressFraction,
                            selected = item.selected,
                            enabled = item.enabled,
                            visuallyFocused = visualFocusIndex == index,
                            onClick = item.onClick,
                            focusRequester = optionRequesters.getValue(optionKeys[index]),
                            isFirstFocusable = index == firstEnabledIndex,
                            isLastFocusable = index == lastEnabledIndex,
                            closeRequester = closeRequester,
                            onFocused = {
                                visualFocusIndex = index
                                focusedOptionKey = optionKeys[index]
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvInfoPanel(
    title: String,
    closeLabel: String,
    rows: List<Pair<String, String>>,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    val closeRequester = remember { FocusRequester() }
    val copyRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        closeRequester.requestFocusWhenReady("player info panel close action")
    }

    Surface(
        modifier = Modifier
            .padding(horizontal = 54.dp, vertical = 44.dp)
            .width(460.dp)
            .fillMaxHeight(0.82f)
            .focusGroup(),
        shape = RoundedCornerShape(26.dp),
        color = IdealPlayerColors.Surface.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, IdealPlayerColors.CardBorder)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )
            HorizontalDivider(color = IdealPlayerColors.DividerColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TvActionChip(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = closeLabel,
                    focusRequester = closeRequester,
                    leftFocusRequester = FocusRequester.Cancel,
                    rightFocusRequester = copyRequester,
                    upFocusRequester = FocusRequester.Cancel,
                    downFocusRequester = FocusRequester.Cancel,
                    onFocused = {},
                    onClick = onClose
                )
                TvActionChip(
                    icon = Icons.Filled.ContentCopy,
                    label = stringResource(R.string.copy_playback_diagnostics),
                    focusRequester = copyRequester,
                    leftFocusRequester = closeRequester,
                    rightFocusRequester = FocusRequester.Cancel,
                    upFocusRequester = FocusRequester.Cancel,
                    downFocusRequester = FocusRequester.Cancel,
                    onFocused = {},
                    onClick = onCopy
                )
            }
            HorizontalDivider(color = IdealPlayerColors.DividerColor)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(rows, key = { it.first }) { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = IdealPlayerColors.TextSecondary
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvErrorState(
    message: String,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    val retryRequester = remember { FocusRequester() }
    val exitRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        retryRequester.requestFocusWhenReady("player error retry action")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusGroup(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 740.dp),
            shape = RoundedCornerShape(28.dp),
            color = IdealPlayerColors.Surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, IdealPlayerColors.Error.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = IdealPlayerColors.Error,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvActionChip(
                        icon = Icons.Filled.PlayArrow,
                        label = stringResource(R.string.retry),
                        focusRequester = retryRequester,
                        leftFocusRequester = FocusRequester.Cancel,
                        rightFocusRequester = exitRequester,
                        upFocusRequester = FocusRequester.Cancel,
                        downFocusRequester = FocusRequester.Cancel,
                        onFocused = {},
                        onClick = onRetry
                    )
                    TvActionChip(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = stringResource(R.string.player_exit),
                        focusRequester = exitRequester,
                        leftFocusRequester = retryRequester,
                        rightFocusRequester = FocusRequester.Cancel,
                        upFocusRequester = FocusRequester.Cancel,
                        downFocusRequester = FocusRequester.Cancel,
                        onFocused = {},
                        onClick = onExit
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    primary: Boolean = false,
    compact: Boolean = false
) {
    // Focus belongs to this stable control node; changing Play/Pause text must not erase the
    // visual state while the actual Compose focus remains on the same node.
    var isFocused by remember { mutableStateOf(false) }
    val focusState = rememberTvFocusVisualState(
        isFocused = isFocused,
        isSelected = false,
        defaultSurface = IdealPlayerColors.CardBackground,
        selectedSurface = IdealPlayerColors.SurfaceSelected,
        focusedSurface = IdealPlayerColors.SurfaceFocus,
        selectedFocusedSurface = IdealPlayerColors.SurfaceFocus,
        defaultContentColor = IdealPlayerColors.TextPrimary,
        defaultSecondaryContentColor = IdealPlayerColors.TextSecondary,
        selectedContentColor = IdealPlayerColors.TextPrimary,
        focusedContentColor = IdealPlayerColors.TextPrimary,
        selectedFocusedContentColor = IdealPlayerColors.TextPrimary,
        selectedBorderColor = IdealPlayerColors.SelectedBorder,
        focusedBorderColor = IdealPlayerColors.FocusBorder,
        selectedFocusedBorderColor = IdealPlayerColors.FocusBorder,
        selectedAccentColor = Color.Transparent,
        focusedAccentColor = Color.Transparent,
        selectedFocusedAccentColor = Color.Transparent
    )

    Surface(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.42f)
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                scaleX = focusState.scale
                scaleY = focusState.scale
                shadowElevation = focusState.shadowElevation.toPx()
            }
            .focusRequester(focusRequester)
            .focusProperties {
                canFocus = enabled
                leftFocusRequester?.let { left = it }
                rightFocusRequester?.let { right = it }
                upFocusRequester?.let { up = it }
                downFocusRequester?.let { down = it }
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(if (compact) 22.dp else 28.dp),
        color = focusState.backgroundColor,
        border = BorderStroke(
            width = focusState.borderWidth.coerceAtLeast(1.dp),
            color = if (focusState.borderWidth > 0.dp) {
                focusState.borderColor
            } else {
                IdealPlayerColors.CardBorder
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(
                    horizontal = if (compact) 18.dp else 24.dp,
                    vertical = if (compact) 12.dp else 18.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = focusState.contentColor,
                modifier = Modifier.size(
                    when {
                        compact && primary -> 34.dp
                        compact -> 28.dp
                        primary -> 42.dp
                        else -> 34.dp
                    }
                )
            )
            Text(
                text = label,
                style = when {
                    compact && primary -> MaterialTheme.typography.titleMedium
                    compact -> MaterialTheme.typography.titleSmall
                    primary -> MaterialTheme.typography.titleLarge
                    else -> MaterialTheme.typography.titleMedium
                },
                color = focusState.contentColor,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvActionChip(
    icon: ImageVector,
    label: String,
    focusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    trapDirectionalFocus: Boolean = false,
    compact: Boolean = false,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusState = rememberTvFocusVisualState(
        isFocused = isFocused,
        defaultSurface = IdealPlayerColors.SurfaceElevated,
        selectedSurface = IdealPlayerColors.SurfaceSelected,
        focusedSurface = IdealPlayerColors.SurfaceFocus,
        selectedFocusedSurface = IdealPlayerColors.SurfaceFocus,
        defaultContentColor = IdealPlayerColors.TextPrimary,
        defaultSecondaryContentColor = IdealPlayerColors.TextSecondary,
        selectedContentColor = IdealPlayerColors.TextPrimary,
        focusedContentColor = IdealPlayerColors.TextPrimary,
        selectedFocusedContentColor = IdealPlayerColors.TextPrimary,
        selectedBorderColor = IdealPlayerColors.SelectedBorder,
        focusedBorderColor = IdealPlayerColors.FocusBorder,
        selectedFocusedBorderColor = IdealPlayerColors.FocusBorder,
        selectedAccentColor = Color.Transparent,
        focusedAccentColor = Color.Transparent,
        selectedFocusedAccentColor = Color.Transparent
    )

    Surface(
        modifier = Modifier
            .defaultMinSize(minHeight = 56.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (
                    trapDirectionalFocus || leftFocusRequester != null ||
                    rightFocusRequester != null || upFocusRequester != null ||
                    downFocusRequester != null
                ) {
                    Modifier.focusProperties {
                        if (leftFocusRequester != null) left = leftFocusRequester
                        else if (trapDirectionalFocus) left = FocusRequester.Cancel
                        if (rightFocusRequester != null) right = rightFocusRequester
                        else if (trapDirectionalFocus) right = FocusRequester.Cancel
                        if (upFocusRequester != null) up = upFocusRequester
                        else if (trapDirectionalFocus) up = FocusRequester.Cancel
                        if (downFocusRequester != null) down = downFocusRequester
                        else if (trapDirectionalFocus) down = FocusRequester.Cancel
                    }
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                scaleX = focusState.scale
                scaleY = focusState.scale
                shadowElevation = focusState.shadowElevation.toPx()
            }
            .zIndex(if (isFocused) 1f else 0f)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick),
        shape = A2Shape.large,
        color = focusState.backgroundColor,
        border = BorderStroke(
            width = focusState.borderWidth.coerceAtLeast(1.dp),
            color = if (focusState.borderWidth > 0.dp) {
                focusState.borderColor
            } else {
                IdealPlayerColors.CardBorder
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = focusState.contentColor,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = focusState.contentColor
            )
        }
    }
}

private fun FocusRequester.requestFocusOnce(reason: String): Boolean {
    return runCatching {
        requestFocus()
        true
    }
        .getOrElse { error ->
            Timber.tag(TV_PLAYER_LOG_TAG).w(error, "Unable to request focus for %s", reason)
            false
        }
}

private suspend fun FocusRequester.requestFocusWhenReady(
    reason: String,
    maximumFrames: Int = 3
): Boolean {
    repeat(maximumFrames.coerceAtLeast(1)) {
        withFrameNanos { }
        if (requestFocusOnce(reason)) return true
    }
    Timber.tag(TV_PLAYER_LOG_TAG).w("Focus target was not attached after composition: %s", reason)
    return false
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvPanelOptionRow(
    title: String,
    subtitle: String?,
    progressFraction: Float?,
    selected: Boolean,
    enabled: Boolean,
    visuallyFocused: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    isFirstFocusable: Boolean,
    isLastFocusable: Boolean,
    closeRequester: FocusRequester,
    onFocused: () -> Unit = {}
) {
    // The Lazy item key identifies this control. Async track/EPG selection updates must not
    // erase its visual focus while Compose focus remains on the same node.
    var hasRealFocus by remember { mutableStateOf(false) }

    val isVisuallyFocused = hasRealFocus || visuallyFocused

    val focusState = rememberTvFocusVisualState(
        isFocused = isVisuallyFocused,
        isSelected = selected,
        defaultSurface = Color.Transparent,
        selectedSurface = IdealPlayerColors.SurfaceSelected,
        focusedSurface = IdealPlayerColors.SurfaceFocus,
        selectedFocusedSurface = IdealPlayerColors.SurfaceFocus,
        defaultContentColor = IdealPlayerColors.TextPrimary,
        defaultSecondaryContentColor = IdealPlayerColors.TextSecondary,
        selectedContentColor = IdealPlayerColors.TextPrimary,
        focusedContentColor = IdealPlayerColors.TextPrimary,
        selectedFocusedContentColor = IdealPlayerColors.TextPrimary,
        selectedBorderColor = IdealPlayerColors.SelectedBorder,
        focusedBorderColor = IdealPlayerColors.FocusBorder,
        selectedFocusedBorderColor = IdealPlayerColors.FocusBorder
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.52f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                // The panel header exposes an explicit close affordance. At list boundaries
                // keep focus inside the panel instead of letting a D-pad event reach the player
                // root or a removed Lazy item.
                if (isFirstFocusable) up = closeRequester
                if (isLastFocusable) down = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .graphicsLayer {
                scaleX = focusState.scale
                scaleY = focusState.scale
                shadowElevation = focusState.shadowElevation.toPx()
            }
            .onFocusChanged {
                hasRealFocus = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = focusState.backgroundColor,
        border = BorderStroke(
            width = focusState.borderWidth,
            color = focusState.borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(if (isVisuallyFocused) 8.dp else if (selected) 4.dp else 0.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        when {
                            isVisuallyFocused -> IdealPlayerColors.Primary
                            selected -> IdealPlayerColors.Secondary
                            else -> Color.Transparent
                        }
                    )
            )

            if (isVisuallyFocused || selected) {
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = focusState.contentColor
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = focusState.secondaryContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                progressFraction?.let { progress ->
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth(0.76f)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = IdealPlayerColors.Primary,
                        trackColor = Color.White.copy(alpha = 0.14f)
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = IdealPlayerColors.Secondary
                )
            }
        }
    }
}

@Composable
private fun TvContentBadge(playbackKind: TvPlaybackKind) {
    val label = stringResource(
        when (playbackKind) {
            TvPlaybackKind.LIVE -> R.string.player_content_live
            TvPlaybackKind.MOVIE -> R.string.player_content_movie
            TvPlaybackKind.SERIES -> R.string.player_content_series
        }
    )
    val accent = when (playbackKind) {
        TvPlaybackKind.LIVE -> IdealPlayerColors.Error
        TvPlaybackKind.MOVIE -> IdealPlayerColors.Primary
        TvPlaybackKind.SERIES -> IdealPlayerColors.Secondary
    }
    TvStatusBadge(
        label = label,
        background = accent.copy(alpha = 0.2f),
        contentColor = accent
    )
}

@Composable
private fun TvStatusBadge(
    label: String,
    background: Color = Color.White.copy(alpha = 0.08f),
    contentColor: Color = Color.White,
    compact: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = background,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelMedium else
                MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 5.dp else 8.dp
            )
        )
    }
}

private fun tvPlaybackSubtitle(
    isLivePlayback: Boolean,
    isSeriesPlayback: Boolean,
    sessionEpisode: Episode?,
    currentChannel: Channel?,
    liveGroup: String
): String {
    return when {
        isLivePlayback -> {
            listOfNotNull(
                currentChannel?.groupTitle?.takeIf { it.isNotBlank() },
                liveGroup.takeIf { it.isNotBlank() }
            ).distinct().joinToString(" • ")
        }

        isSeriesPlayback && sessionEpisode != null -> {
            buildString {
                append("S")
                append(sessionEpisode.seasonNumber)
                append("E")
                append(sessionEpisode.episodeNumber)
                if (sessionEpisode.name.isNotBlank()) {
                    append(" • ")
                    append(sessionEpisode.name)
                }
            }
        }

        else -> ""
    }
}


// ─── TV D-pad Seek Bar ─────────────────────────────────────────────────────────
// Intercepts Left / Right D-pad key presses to seek ±10s.
// Highlights in primary color when TV focus is on it.
@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun TvSeekBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    backwardStepMs: Long,
    forwardStepMs: Long,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    onSeekTo: (Long) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(currentPosition.coerceIn(0L, duration)) }
    var pendingSeekElapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(currentPosition, duration, isFocused) {
        if (!isFocused) {
            scrubPosition = currentPosition.coerceIn(0L, duration)
            pendingSeekElapsedMs = 0L
        } else {
            val pendingAgeMs = if (pendingSeekElapsedMs > 0L) {
                SystemClock.elapsedRealtime() - pendingSeekElapsedMs
            } else {
                Long.MAX_VALUE
            }
            val resolvedPosition = tvResolvedScrubPosition(
                enginePosition = currentPosition,
                localPosition = scrubPosition,
                duration = duration,
                pendingAgeMs = pendingAgeMs
            )
            if (resolvedPosition == currentPosition.coerceIn(0L, duration)) {
                pendingSeekElapsedMs = 0L
            }
            scrubPosition = resolvedPosition
        }
    }

    val progress = if (duration > 0L) {
        (scrubPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val bufferedProgress = if (duration > 0L) {
        (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(progress, 1f)
    } else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TV_PLAYER_SEEK_BAR_TEST_TAG)
            .focusRequester(focusRequester)
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                up = upFocusRequester
                down = downFocusRequester
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                    scrubPosition = currentPosition.coerceIn(0L, duration)
                    pendingSeekElapsedMs = 0L
                }
            }
            .onPreviewKeyEvent { event ->
                if (!isFocused) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        val stepMs = tvSeekStepForRepeat(
                            backwardStepMs,
                            event.nativeKeyEvent.repeatCount
                        )
                        scrubPosition = resolveRelativeSeekPosition(
                            currentPosition = scrubPosition,
                            deltaMs = -stepMs,
                            duration = duration,
                            isSeekable = true
                        ) ?: scrubPosition
                        pendingSeekElapsedMs = SystemClock.elapsedRealtime()
                        onSeekTo(scrubPosition)
                        true
                    }
                    Key.DirectionRight -> {
                        val stepMs = tvSeekStepForRepeat(
                            forwardStepMs,
                            event.nativeKeyEvent.repeatCount
                        )
                        scrubPosition = resolveRelativeSeekPosition(
                            currentPosition = scrubPosition,
                            deltaMs = stepMs,
                            duration = duration,
                            isSeekable = true
                        ) ?: scrubPosition
                        pendingSeekElapsedMs = SystemClock.elapsedRealtime()
                        onSeekTo(scrubPosition)
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        onSeekTo(scrubPosition)
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFocused) 16.dp else 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            LinearProgressIndicator(
                progress = { bufferedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isFocused) 8.dp else 5.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = Color.White.copy(alpha = 0.3f),
                trackColor = Color.White.copy(alpha = 0.14f)
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isFocused) 8.dp else 5.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = if (isFocused) IdealPlayerColors.Primary else Color.White.copy(alpha = 0.88f),
                trackColor = Color.Transparent
            )
            val thumbSize = if (isFocused) 16.dp else 10.dp
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - thumbSize) * progress)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(if (isFocused) IdealPlayerColors.Primary else Color.White)
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = CircleShape
                    )
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = StringUtils.formatDuration(scrubPosition),
                style = MaterialTheme.typography.labelLarge,
                color = if (isFocused) IdealPlayerColors.Primary else Color.White
            )
            if (isFocused) {
                Text(
                    text = stringResource(
                        R.string.tv_seek_controls_hint,
                        tvSeekStepSeconds(backwardStepMs),
                        tvSeekStepSeconds(forwardStepMs)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = IdealPlayerColors.Primary.copy(alpha = 0.8f)
                )
            }
            Text(
                text = stringResource(
                    R.string.player_duration_remaining,
                    StringUtils.formatDuration(duration),
                    StringUtils.formatDuration((duration - scrubPosition).coerceAtLeast(0L))
                ),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
    }
}
