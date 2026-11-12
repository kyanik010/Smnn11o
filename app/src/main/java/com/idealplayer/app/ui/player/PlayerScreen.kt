package com.idealplayer.app.ui.player

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.idealplayer.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.idealplayer.app.core.common.StringUtils
import com.idealplayer.app.core.designsystem.theme.A2Shape
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.core.model.*
import com.idealplayer.app.core.player.*
import com.idealplayer.app.ui.components.a2.A2ActionVariant
import com.idealplayer.app.ui.components.a2.A2IconButton
import com.idealplayer.app.ui.components.a2.A2PlayerControlButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    url: String,
    title: String,
    contentId: Long,
    contentType: String,
    startPosition: Long,
    groupContext: String,
    isTv: Boolean,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.state.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val playerReady by viewModel.playerReady.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val liveChannelSwitch by viewModel.liveChannelSwitch.collectAsStateWithLifecycle()
    val retryState by viewModel.retryState.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val currentEpgProgram by viewModel.currentEpgProgram.collectAsStateWithLifecycle()
    val nextEpgProgram by viewModel.nextEpgProgram.collectAsStateWithLifecycle()
    val channelListEpgPrograms by viewModel.channelListEpgPrograms.collectAsStateWithLifecycle()
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }
    var pendingSeekPosition by remember { mutableStateOf<Long?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var playerSettingsInitialSection by remember { mutableStateOf<String?>(null) }
    var showChannelSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    // Screen lock — mobile only
    var isScreenLocked by remember { mutableStateOf(false) }
    // Context and activity must be declared before PiP and AudioManager references
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val activity = context as? Activity
    // PiP — read from activity
    val isPipMode by (activity as? com.idealplayer.app.MainActivity)?.isPipMode?.collectAsStateWithLifecycle(false)
        ?: remember { mutableStateOf(false) }
    // Volume/brightness drag gesture state
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }
    var isDragging by remember { mutableStateOf(false) }
    var dragType by remember { mutableStateOf<String?>(null) } // "brightness" | "volume"
    var dragValue by remember { mutableFloatStateOf(0f) }  // 0..1 for display
    var dragContinuousValue by remember { mutableFloatStateOf(0f) }
    var lastAppliedVolume by remember { mutableIntStateOf(-1) }
    var showDragIndicator by remember { mutableStateOf(false) }
    val playPauseFocusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current

    val displayTitle = session.title.ifBlank { title }
    val isLivePlayback = contentType == ContentType.LIVE.name || session.currentChannel != null
    val isSeriesPlayback = contentType == ContentType.SERIES.name || session.currentEpisode != null
    val liveLabel = stringResource(R.string.player_content_live)
    val overlaySubtitle = when {
        isLivePlayback -> listOfNotNull(
            session.currentChannel?.groupTitle?.takeIf { it.isNotBlank() },
            session.liveGroup.takeIf { it.isNotBlank() },
            liveLabel
        ).distinct().joinToString(" • ")
        isSeriesPlayback && session.currentEpisode != null -> episodeLabel(session.currentEpisode!!)
        else -> listOfNotNull(
            videoResolutionBadge(playerState.videoWidth, playerState.videoHeight)
                .ifBlank { playerState.currentVideoResolution }
                .takeIf { it.isNotBlank() },
            playerState.currentVideoCodec.takeIf { it.isNotBlank() },
            playerState.currentVideoFps.takeIf { it.isNotBlank() }
        ).joinToString(" • ")
    }
    val isChannelSwitching = liveChannelSwitch.isSwitching
    val liveChannelAnchorId = liveChannelSwitch.targetChannelId ?: session.currentChannel?.id ?: contentId
    val previousLiveChannel = remember(session.availableChannels, liveChannelAnchorId) {
        adjacentLiveChannel(session.availableChannels, liveChannelAnchorId, -1)
    }
    val nextLiveChannel = remember(session.availableChannels, liveChannelAnchorId) {
        adjacentLiveChannel(session.availableChannels, liveChannelAnchorId, 1)
    }
    val showSwitchingOverlay = isChannelSwitching && !playerState.isPlaybackConfirmed
    val showBufferingOverlay = playerState.playbackState == PlaybackState.BUFFERING &&
        !playerState.isPlaybackConfirmed
    val showUpNext = remember(
        isTv,
        isSeriesPlayback,
        session.nextEpisode,
        playerState.currentPosition,
        playerState.duration,
        playerState.playbackState,
        showSettingsSheet
    ) {
        !isTv &&
            !showSettingsSheet &&
            isSeriesPlayback &&
            session.nextEpisode != null &&
            (
                playerState.playbackState == PlaybackState.ENDED ||
                    (
                        playerState.duration > 0L &&
                            playerState.currentPosition > 30_000L &&
                            (playerState.duration - playerState.currentPosition) <= 45_000L
                    )
            )
    }

    LaunchedEffect(url, contentId, isLivePlayback, settings.startFullscreenLive) {
        controlsVisible = !(isLivePlayback && settings.startFullscreenLive)
        isScrubbing = false
        scrubPosition = 0L
        pendingSeekPosition = null
    }

    LaunchedEffect(
        playerState.currentPosition,
        playerState.duration,
        isScrubbing,
        pendingSeekPosition
    ) {
        if (!isScrubbing) {
            val pending = pendingSeekPosition
            if (pending == null) {
                scrubPosition = playerState.currentPosition
            } else if (abs(playerState.currentPosition - pending) <= 1_500L) {
                pendingSeekPosition = null
                scrubPosition = playerState.currentPosition
            } else {
                // Hold the committed target until the engine confirms its discontinuity. This
                // prevents the progress poller from briefly painting the pre-seek position.
                scrubPosition = pending
            }
        }
    }

    // Register player screen as active so onUserLeaveHint() triggers PiP
    DisposableEffect(Unit) {
        (activity as? com.idealplayer.app.MainActivity)?.isPlayerScreenActive = true
        onDispose {
            (activity as? com.idealplayer.app.MainActivity)?.isPlayerScreenActive = false
        }
    }

    val playbackActive = remember(playerState.playbackState, isChannelSwitching) {
        playerState.playbackState == PlaybackState.PLAYING ||
            playerState.playbackState == PlaybackState.BUFFERING ||
            isChannelSwitching
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // KEEP SCREEN ON — prevents screen from turning off during playback
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
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

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // IMMERSIVE MODE — hides system bars during video playback
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    val insetsController = remember(activity) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
        }
    }

    // Enter immersive on first composition, exit on dispose
    DisposableEffect(activity) {
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(insetsController, playbackActive, controlsVisible, showSettingsSheet, showChannelSheet) {
        insetsController?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (playbackActive) {
                controller.hide(WindowInsetsCompat.Type.statusBars())
                if (controlsVisible || showSettingsSheet || showChannelSheet) {
                    controller.show(WindowInsetsCompat.Type.navigationBars())
                } else {
                    controller.hide(WindowInsetsCompat.Type.navigationBars())
                }
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Keep fullscreen sticky when playback state changes
    DisposableEffect(insetsController) {
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            insetsController?.let { controller ->
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
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

    // Initialize player
    LaunchedEffect(url, title, contentId, contentType, startPosition, groupContext) {
        viewModel.init(url, title, startPosition, contentId, contentType, groupContext)
    }

    // Load EPG when channel changes
    val currentChannel = session.currentChannel
    LaunchedEffect(currentChannel?.id, currentChannel?.epgChannelId, currentChannel?.name) {
        viewModel.loadEpgForChannel(currentChannel)
    }

    LaunchedEffect(liveChannelSwitch.errorMessage, isLivePlayback, session.availableChannels) {
        if (
            !liveChannelSwitch.errorMessage.isNullOrBlank() &&
            isLivePlayback &&
            session.availableChannels.isNotEmpty()
        ) {
            showChannelSheet = true
            controlsVisible = true
        }
    }

    LaunchedEffect(showChannelSheet, session.availableChannels) {
        if (showChannelSheet && session.availableChannels.isNotEmpty()) {
            viewModel.loadEpgForChannels(session.availableChannels)
        }
    }

    // Auto-hide controls
    LaunchedEffect(controlsVisible, playerState.isPlaying, isScrubbing) {
        if (controlsVisible && playerState.isPlaying && !isScrubbing) {
            delay(settings.controllerAutoHideMs)
            controlsVisible = false
        }
    }

    // Handle back — optimized: navigate FIRST, release async
    BackHandler {
        when {
            isScreenLocked && !isTv -> {
                isScreenLocked = false
                controlsVisible = true
            }
            showSettingsSheet -> showSettingsSheet = false
            showChannelSheet -> showChannelSheet = false
            else -> viewModel.exitPlayer(onBack)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (isTv) {
                    Modifier.onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionCenter, Key.Enter -> {
                                    if (!controlsVisible) { controlsVisible = true; true }
                                    else { viewModel.togglePlayPause(); true }
                                }
                                Key.DirectionRight -> { viewModel.seekForward(); controlsVisible = true; true }
                                Key.DirectionLeft -> { viewModel.seekBackward(); controlsVisible = true; true }
                                Key.DirectionUp -> {
                                    if (!controlsVisible && isLivePlayback) {
                                        viewModel.playNextChannel()
                                        true
                                    } else {
                                        controlsVisible = true; true
                                    }
                                }
                                Key.DirectionDown -> {
                                    if (!controlsVisible && isLivePlayback) {
                                        viewModel.playPreviousChannel()
                                        true
                                    } else {
                                        controlsVisible = true; true
                                    }
                                }
                                Key.Back, Key.Escape -> {
                                    if (showSettingsSheet) {
                                        showSettingsSheet = false; true
                                    } else if (showChannelSheet) {
                                        showChannelSheet = false; true
                                    } else if (controlsVisible) {
                                        controlsVisible = false; true
                                    } else {
                                        viewModel.exitPlayer(onBack); true
                                    }
                                }
                                Key.MediaPlayPause, Key.Spacebar -> { viewModel.togglePlayPause(); controlsVisible = true; true }
                                else -> false
                            }
                        } else false
                    }
                } else {
                    // Mobile: tap gestures + vertical drag for brightness/volume
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { if (!isDragging) controlsVisible = !controlsVisible },
                                onDoubleTap = { offset ->
                                    if (isDragging) return@detectTapGestures
                                    val screenWidth = size.width
                                    if (offset.x < screenWidth / 3) viewModel.seekBackward()
                                    else if (offset.x > screenWidth * 2 / 3) viewModel.seekForward()
                                    else viewModel.togglePlayPause()
                                    controlsVisible = true
                                }
                            )
                        }
                        .pointerInput(maxVolume) {
                            detectDragGestures(
                                onDragStart = { offset: androidx.compose.ui.geometry.Offset ->
                                    dragType = if (offset.x < size.width / 2f) "brightness" else "volume"
                                    isDragging = true
                                    showDragIndicator = true
                                    dragValue = when (dragType) {
                                        "brightness" -> {
                                            val lp = activity?.window?.attributes
                                            (lp?.screenBrightness ?: 0.5f).coerceIn(0f, 1f)
                                        }
                                        else -> audioManager.getStreamVolume(
                                            android.media.AudioManager.STREAM_MUSIC
                                        ).toFloat() / maxVolume.toFloat()
                                    }
                                    dragContinuousValue = dragValue
                                    lastAppliedVolume = audioManager.getStreamVolume(
                                        android.media.AudioManager.STREAM_MUSIC
                                    )
                                },
                                onDragEnd = {
                                    isDragging = false
                                    dragType = null
                                },
                                onDragCancel = {
                                    isDragging = false
                                    dragType = null
                                },
                                onDrag = { _: androidx.compose.ui.input.pointer.PointerInputChange,
                                           dragAmount: androidx.compose.ui.geometry.Offset ->
                                    val delta = -dragAmount.y / size.height.toFloat()
                                    when (dragType) {
                                        "brightness" -> {
                                            val win = activity?.window ?: return@detectDragGestures
                                            val lp = win.attributes
                                            val baseBrightness = lp.screenBrightness
                                                .takeIf { it >= 0f }
                                                ?: dragContinuousValue
                                                    .takeIf { it > 0f }
                                                    ?: 0.5f
                                            val newBrightness = (baseBrightness + delta).coerceIn(0.01f, 1f)
                                            lp.screenBrightness = newBrightness
                                            win.attributes = lp
                                            dragValue = newBrightness
                                            dragContinuousValue = newBrightness
                                        }
                                        "volume" -> {
                                            val newVolumeFraction = (dragContinuousValue + delta).coerceIn(0f, 1f)
                                            val newVolume = (newVolumeFraction * maxVolume.toFloat())
                                                .roundToInt()
                                                .coerceIn(0, maxVolume)
                                            if (newVolume != lastAppliedVolume) {
                                                audioManager.setStreamVolume(
                                                    android.media.AudioManager.STREAM_MUSIC, newVolume, 0
                                                )
                                                lastAppliedVolume = newVolume
                                            }
                                            dragContinuousValue = newVolumeFraction
                                            dragValue = newVolumeFraction
                                        }
                                    }
                                }
                            )
                        }


                }
            )
    ) {
        PlayerSurfaceHost(
            modifier = Modifier.fillMaxSize(),
            playerEngine = viewModel.playerManager.getEngine(),
            playerState = playerState,
            surfaceState = PlayerSurfaceHostState(
                playerReady = playerReady,
                playbackActive = playbackActive,
                shellMode = PlayerShellMode.MOBILE
            ),
            onSurfaceBoundsChanged = { bounds ->
                (activity as? com.idealplayer.app.MainActivity)?.updatePipSourceRect(bounds)
            },
            placeholder = {
                CircularProgressIndicator(
                    color = IdealPlayerColors.Primary,
                    modifier = Modifier.size(40.dp).align(Alignment.Center)
                )
            }
        )

        // Buffering indicator
        if (showBufferingOverlay) {
            CircularProgressIndicator(
                color = IdealPlayerColors.Primary,
                modifier = Modifier.size(48.dp).align(Alignment.Center)
            )
        }

        // ─── Brightness / Volume drag indicator ─────────────────────────────────
        if (!isTv && showDragIndicator && dragType != null) {
            LaunchedEffect(isDragging) {
                if (!isDragging) {
                    delay(1500)
                    showDragIndicator = false
                }
            }
            val isLeft = dragType == "brightness"
            Box(
                modifier = Modifier
                    .align(if (isLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 32.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isLeft) Icons.Filled.Brightness6 else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "${(dragValue * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                    LinearProgressIndicator(
                        progress = { dragValue },
                        modifier = Modifier.width(60.dp).height(3.dp),
                        color = IdealPlayerColors.Primary,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }
        }

        // ─── Sleep Timer badge (top-right, always visible when active) ──────────
        if (!isTv && sleepTimerState.isActive) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 72.dp, top = 8.dp)
                    .clickable { showSleepTimerSheet = true },
                shape = RoundedCornerShape(8.dp),
                color = if (sleepTimerState.isLastMinute)
                    Color(0xFFFFB300).copy(alpha = 0.92f)
                else
                    Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Filled.Bedtime,
                        contentDescription = stringResource(R.string.player_sleep_timer),
                        tint = if (sleepTimerState.isLastMinute) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = sleepTimerState.remainingFormatted,
                        color = if (sleepTimerState.isLastMinute) Color.Black else Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Error state — with StreamRetryManager auto-retry countdown
        if (
            playerState.playbackState == PlaybackState.ERROR &&
            !isChannelSwitching
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (retryState.isRetrying) {
                        // Auto-retry in progress
                        CircularProgressIndicator(
                            color = IdealPlayerColors.Primary,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = retryState.userMessage,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (retryState.nextRetryInSeconds > 0) {
                            Text(
                                text = "${retryState.nextRetryInSeconds}s",
                                color = IdealPlayerColors.Primary,
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        // Allow manual cancel of auto-retry
                        OutlinedButton(
                            onClick = {
                                viewModel.retryManager.cancel()
                                viewModel.retryCurrent()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, Color.White.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(stringResource(R.string.player_retry_now))
                        }
                    } else {
                        // Manual retry controls
                        Icon(
                            Icons.Filled.Error,
                            contentDescription = null,
                            tint = IdealPlayerColors.Error,
                            modifier = Modifier.size(44.dp)
                        )
                        val displayError = retryState.userMessage
                            .ifBlank {
                                playerState.errorMessage
                                    ?: stringResource(R.string.player_state_error)
                            }
                        Text(
                            text = displayError,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        OutlinedButton(
                            onClick = { viewModel.retryCurrent() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }



        AnimatedVisibility(
            visible = showSwitchingOverlay,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = IdealPlayerColors.Primary, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.player_switching_channel),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    if (liveChannelSwitch.targetTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = liveChannelSwitch.targetTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showUpNext,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 16.dp, vertical = 96.dp)
        ) {
            val nextEpisode = session.nextEpisode
            if (nextEpisode != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.up_next),
                            style = MaterialTheme.typography.labelMedium,
                            color = IdealPlayerColors.Primary
                        )
                        Text(
                            text = episodeLabel(nextEpisode),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    controlsVisible = false
                                    viewModel.playNextEpisode()
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = IdealPlayerColors.Primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Filled.SkipNext, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.next_episode))
                            }
                            OutlinedButton(
                                onClick = { controlsVisible = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }
            }
        }

        // A2 touch overlay — Mobile and Tablet share behavior but use native Figma geometry.
        AnimatedVisibility(
            visible = controlsVisible && !isTv && !isPipMode &&
                (!isChannelSwitching || isLivePlayback),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300))
        ) {
            val displayedPosition = if (isScrubbing || pendingSeekPosition != null) {
                scrubPosition
            } else {
                playerState.currentPosition
            }.coerceIn(0L, playerState.duration.coerceAtLeast(0L))

            A2TouchPlaybackOverlay(
                title = displayTitle,
                subtitle = overlaySubtitle,
                isLivePlayback = isLivePlayback,
                isSeriesPlayback = isSeriesPlayback,
                playerState = playerState,
                seekBackwardSeconds = (settings.seekBackwardMs / 1_000L).coerceAtLeast(1L),
                seekForwardSeconds = (settings.seekForwardMs / 1_000L).coerceAtLeast(1L),
                currentEpgProgram = currentEpgProgram,
                nextEpgProgram = nextEpgProgram,
                positionMillis = displayedPosition,
                previousContentEnabled = when {
                    isLivePlayback -> previousLiveChannel != null
                    isSeriesPlayback -> session.previousEpisode != null
                    else -> false
                },
                nextContentEnabled = when {
                    isLivePlayback -> nextLiveChannel != null
                    isSeriesPlayback -> session.nextEpisode != null
                    else -> false
                },
                onBack = { viewModel.exitPlayer(onBack) },
                onPreviousContent = {
                    when {
                        isLivePlayback -> viewModel.playPreviousChannel()
                        isSeriesPlayback -> viewModel.playPreviousEpisode()
                    }
                },
                onSeekBackward = { viewModel.seekBackward() },
                onPlayPause = { viewModel.togglePlayPause() },
                onSeekForward = { viewModel.seekForward() },
                onNextContent = {
                    when {
                        isLivePlayback -> viewModel.playNextChannel()
                        isSeriesPlayback -> viewModel.playNextEpisode()
                    }
                },
                onScrub = { target ->
                    isScrubbing = true
                    pendingSeekPosition = null
                    scrubPosition = target.coerceIn(0L, playerState.duration)
                },
                onScrubFinished = {
                    if (isScrubbing) {
                        val target = scrubPosition.coerceIn(0L, playerState.duration)
                        isScrubbing = false
                        pendingSeekPosition = target
                        viewModel.seekTo(target)
                    }
                },
                onGoLive = {
                    if (playerState.duration > 0L && playerState.isSeekable) {
                        viewModel.seekTo(playerState.duration)
                    }
                },
                onOpenAudio = {
                    playerSettingsInitialSection = "audio"
                    showSettingsSheet = true
                },
                onOpenSubtitles = {
                    playerSettingsInitialSection = "subtitle"
                    showSettingsSheet = true
                },
                onOpenQuality = {
                    playerSettingsInitialSection = "quality"
                    showSettingsSheet = true
                },
                onOpenAspectRatio = {
                    playerSettingsInitialSection = "aspect"
                    showSettingsSheet = true
                },
                onOpenSpeed = {
                    playerSettingsInitialSection = "speed"
                    showSettingsSheet = true
                },
                onOpenOptions = {
                    playerSettingsInitialSection = null
                    showSettingsSheet = true
                },
                onLock = {
                    isScreenLocked = true
                    controlsVisible = false
                }
            )
        }

        // Compatibility overlay for legacy callers that still route TV through PlayerScreen.
        AnimatedVisibility(
            visible = controlsVisible && isTv && !isPipMode &&
                (!isChannelSwitching || isLivePlayback),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(
                        colors = listOf(IdealPlayerColors.OverlayDark, Color.Transparent, Color.Transparent, IdealPlayerColors.OverlayDark),
                        startY = 0f,
                        endY = Float.MAX_VALUE
                    ))
            ) {
                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        .statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerControlButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.player_back),
                        onClick = { viewModel.exitPlayer(onBack) })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(displayTitle, style = MaterialTheme.typography.titleLarge, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(12.dp))
                    if (!isTv && isSeriesPlayback) {
                        PlayerControlButton(
                            icon = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.previous_episode),
                            size = 22.dp,
                            onClick = { viewModel.playPreviousEpisode() },
                            modifier = Modifier.alpha(if (session.previousEpisode != null) 1f else 0.45f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        PlayerControlButton(
                            icon = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.next_episode),
                            size = 22.dp,
                            onClick = { viewModel.playNextEpisode() },
                            modifier = Modifier.alpha(if (session.nextEpisode != null) 1f else 0.45f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (!isTv && isLivePlayback) {
                        PlayerControlButton(
                            icon = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.channel_list),
                            size = 22.dp,
                            onClick = { showChannelSheet = true }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                // Center play/pause controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 40.dp else 28.dp)
                ) {
                    if (isLivePlayback && !isTv) {
                        PlayerControlButton(
                            icon = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.previous_channel),
                            size = 40.dp,
                            onClick = { viewModel.playPreviousChannel() },
                            modifier = Modifier.alpha(
                                if (previousLiveChannel != null) 1f else 0.45f
                            )
                        )
                    } else {
                        PlayerControlButton(icon = Icons.Filled.Replay10, contentDescription = stringResource(
                            R.string.seek_backward_short,
                            (settings.seekBackwardMs / 1_000L).coerceAtLeast(1L)
                        ),
                            size = if (isTv) 48.dp else 40.dp,
                            onClick = { viewModel.seekBackward() })
                    }

                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier
                            .size(if (isTv) 72.dp else 56.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .focusRequester(playPauseFocusRequester)
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playerState.isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                            tint = Color.White,
                            modifier = Modifier.size(if (isTv) 48.dp else 40.dp)
                        )
                    }

                    if (isLivePlayback && !isTv) {
                        PlayerControlButton(
                            icon = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.next_channel),
                            size = 40.dp,
                            onClick = { viewModel.playNextChannel() },
                            modifier = Modifier.alpha(
                                if (nextLiveChannel != null) 1f else 0.45f
                            )
                        )
                    } else {
                        PlayerControlButton(icon = Icons.Filled.Forward10, contentDescription = stringResource(
                            R.string.seek_forward_short,
                            (settings.seekForwardMs / 1_000L).coerceAtLeast(1L)
                        ),
                            size = if (isTv) 48.dp else 40.dp,
                            onClick = { viewModel.seekForward() })
                    }
                }

                // Bottom controls
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // EPG mini strip — live TV only, when EPG data available
                    if (isLivePlayback && currentEpgProgram != null && !isPipMode) {
                        EpgMiniStrip(
                            currentProgram = currentEpgProgram,
                            nextProgram = nextEpgProgram
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    // Progress bar

                    if (!isLivePlayback && playerState.duration > 0L) {
                        val displayedPosition = if (isScrubbing || pendingSeekPosition != null) {
                            scrubPosition
                        } else {
                            playerState.currentPosition
                        }.coerceIn(0L, playerState.duration)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(StringUtils.formatDuration(displayedPosition),
                                style = MaterialTheme.typography.labelSmall, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                Slider(
                                    value = (displayedPosition.toDouble() / playerState.duration.toDouble())
                                        .toFloat()
                                        .coerceIn(0f, 1f),
                                    onValueChange = { fraction ->
                                        isScrubbing = true
                                        pendingSeekPosition = null
                                        scrubPosition = (fraction.coerceIn(0f, 1f) * playerState.duration)
                                            .toLong()
                                            .coerceIn(0L, playerState.duration)
                                    },
                                    onValueChangeFinished = {
                                        if (isScrubbing) {
                                            val target = scrubPosition.coerceIn(0L, playerState.duration)
                                            isScrubbing = false
                                            pendingSeekPosition = target
                                            viewModel.seekTo(target)
                                        }
                                    },
                                    enabled = playerState.isSeekable,
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = IdealPlayerColors.Primary,
                                        activeTrackColor = IdealPlayerColors.Primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.focusable()
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(StringUtils.formatDuration(playerState.duration),
                                style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }

                    // Bottom toolbar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Speed button
                        PlayerControlButton(
                            icon = Icons.Filled.Speed,
                            contentDescription = stringResource(R.string.setting_playback_speed),
                            label = "${playerState.playbackSpeed}x",
                            onClick = { /* handled in settings sheet */ showSettingsSheet = true }
                        )

                        if (isLivePlayback) {
                            Spacer(modifier = Modifier.width(4.dp))
                            PlayerControlButton(
                                icon = Icons.AutoMirrored.Filled.List,
                                contentDescription = stringResource(R.string.channel_list),
                                label = session.liveGroup.ifBlank { stringResource(R.string.channels) },
                                onClick = { showChannelSheet = true }
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Resolution indicator (if adaptive stream)
                        if (playerState.currentVideoResolution.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(4.dp), color = Color.White.copy(alpha = 0.15f)) {
                                Text(playerState.currentVideoResolution,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Sleep timer button — mobile only
                        if (!isTv) {
                            PlayerControlButton(
                                icon = Icons.Filled.Bedtime,
                                contentDescription = stringResource(R.string.player_sleep_timer),
                                label = if (sleepTimerState.isActive) sleepTimerState.remainingFormatted else null,
                                onClick = { showSleepTimerSheet = true }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Screen lock button — mobile only
                        if (!isTv) {
                            PlayerControlButton(
                                icon = Icons.Filled.Lock,
                                contentDescription = stringResource(R.string.player_lock_screen),
                                onClick = {
                                    isScreenLocked = true
                                    controlsVisible = false
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Settings button (opens bottom sheet)
                        PlayerControlButton(
                            icon = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.nav_settings),
                            onClick = { showSettingsSheet = true }
                        )
                    }
                }
            }
        }
    }

    // ─── Screen Lock Overlay ────────────────────────────────────────────────────
    // Sits above all content, blocks all touches when active
    if (isScreenLocked && !isTv) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            contentAlignment = Alignment.TopEnd
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            )

            // Persistent unlock button — only interactive element
            Surface(
                shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 0.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clickable { isScreenLocked = false; controlsVisible = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = stringResource(R.string.player_unlock_screen),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.player_unlock_screen),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
    // ─── Sleep Timer Bottom Sheet ────────────────────────────────────────────────
    if (showSleepTimerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSleepTimerSheet = false },
            containerColor = IdealPlayerColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Bedtime, contentDescription = null, tint = IdealPlayerColors.Primary)
                    Text(
                        stringResource(R.string.player_sleep_timer),
                        style = MaterialTheme.typography.titleMedium,
                        color = IdealPlayerColors.TextPrimary
                    )
                }
                Spacer(Modifier.height(16.dp))
                viewModel.sleepTimer.availableOptions.forEach { minutes ->
                    val isSelected = sleepTimerState.isActive && sleepTimerState.selectedMinutes == minutes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) IdealPlayerColors.SurfaceSelected
                                else Color.Transparent
                            )
                            .clickable {
                                viewModel.setSleepTimer(minutes)
                                showSleepTimerSheet = false
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.player_minutes, minutes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) IdealPlayerColors.Secondary else IdealPlayerColors.TextPrimary
                        )
                        if (isSelected) {
                            Text(
                                sleepTimerState.remainingFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                color = IdealPlayerColors.Secondary
                            )
                        }
                    }
                }
                if (sleepTimerState.isActive) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = IdealPlayerColors.DividerColor)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                viewModel.cancelSleepTimer()
                                showSleepTimerSheet = false
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.TimerOff, contentDescription = null, tint = IdealPlayerColors.Error, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.player_cancel_sleep_timer),
                            style = MaterialTheme.typography.bodyLarge,
                            color = IdealPlayerColors.Error
                        )
                    }
                }
            }
        }
    }

    if (showChannelSheet) {
        ChannelSwitchSheet(
            channels = session.availableChannels,
            currentChannelId = liveChannelSwitch.targetChannelId ?: session.currentChannel?.id ?: contentId,
            isTv = isTv,
            epgPrograms = channelListEpgPrograms,
            onDismiss = { showChannelSheet = false },
            onChannelSelected = { channel ->
                showChannelSheet = false
                controlsVisible = false
                viewModel.playChannel(channel)
            }
        )
    }

    if (showSettingsSheet) {
        PlayerSettingsSheet(
            playerState = playerState,
            diagnostics = diagnostics,
            isTv = isTv,
            initialSection = playerSettingsInitialSection,
            isLivePlayback = isLivePlayback,
            sleepTimerRemaining = sleepTimerState.remainingFormatted.takeIf {
                sleepTimerState.isActive
            },
            onDismiss = {
                showSettingsSheet = false
                playerSettingsInitialSection = null
            },
            onOpenChannels = {
                showSettingsSheet = false
                playerSettingsInitialSection = null
                showChannelSheet = true
            },
            onOpenSleepTimer = {
                showSettingsSheet = false
                playerSettingsInitialSection = null
                showSleepTimerSheet = true
            },
            onLockScreen = {
                showSettingsSheet = false
                playerSettingsInitialSection = null
                isScreenLocked = true
                controlsVisible = false
            },
            onSetAspectRatio = { viewModel.setAspectRatio(it) },
            onSetSpeed = { viewModel.setSpeed(it) },
            onSetQualityMode = { viewModel.setVideoQualityMode(it) },
            onSelectVideoTrack = { viewModel.selectVideoTrack(it) },
            onSelectAudio = { viewModel.selectAudio(it) },
            onSelectSubtitle = { viewModel.selectSubtitle(it) },
            onDisableSubtitles = { viewModel.disableSubtitles() },
            onCopyDiagnostics = {
                clipboardManager.setText(AnnotatedString(viewModel.buildDiagnosticsReport()))
                Toast.makeText(context, context.getString(R.string.playback_diagnostics_copied), Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/**
 * A2 touch player chrome shared by Mobile and Tablet. Playback state and commands stay owned by
 * [PlayerViewModel]; this composable only maps the approved Figma geometry to callbacks.
 */
@Composable
private fun A2TouchPlaybackOverlay(
    title: String,
    subtitle: String,
    isLivePlayback: Boolean,
    isSeriesPlayback: Boolean,
    playerState: PlayerState,
    seekBackwardSeconds: Long,
    seekForwardSeconds: Long,
    currentEpgProgram: com.idealplayer.app.data.parser.EpgProgram?,
    nextEpgProgram: com.idealplayer.app.data.parser.EpgProgram?,
    positionMillis: Long,
    previousContentEnabled: Boolean,
    nextContentEnabled: Boolean,
    onBack: () -> Unit,
    onPreviousContent: () -> Unit,
    onSeekBackward: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onNextContent: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    onGoLive: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenAspectRatio: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenOptions: () -> Unit,
    onLock: () -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
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
        val isTablet = maxWidth >= 600.dp
        val edgePadding = if (isTablet) 32.dp else 16.dp

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = edgePadding, end = edgePadding, top = if (isTablet) 16.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            A2IconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.player_back),
                onClick = onBack,
                variant = A2ActionVariant.Ghost,
                size = 48.dp,
                iconSize = if (isTablet) 32.dp else 28.dp
            )
            Spacer(Modifier.width(if (isTablet) 12.dp else 4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = IdealPlayerColors.TextPrimary,
                    style = if (isTablet) MaterialTheme.typography.headlineSmall else
                        MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = if (isLivePlayback) IdealPlayerColors.Primary else IdealPlayerColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    if (isTablet) Color.Transparent else IdealPlayerColors.Background.copy(alpha = 0.96f)
                )
                .navigationBarsPadding()
                .padding(
                    horizontal = edgePadding,
                    vertical = if (isTablet) 20.dp else 12.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (isTablet) 16.dp else 8.dp)
        ) {
            if (isLivePlayback && currentEpgProgram != null) {
                EpgMiniStrip(
                    currentProgram = currentEpgProgram,
                    nextProgram = nextEpgProgram
                )
            }

            val mediaDuration = playerState.duration.coerceAtLeast(0L)
            val epgDuration = currentEpgProgram?.durationMs?.coerceAtLeast(0L) ?: 0L
            val timelineDuration = mediaDuration.takeIf { it > 0L } ?: epgDuration
            val timelinePosition = if (mediaDuration > 0L) {
                positionMillis.coerceIn(0L, mediaDuration)
            } else {
                currentEpgProgram?.let { program ->
                    (System.currentTimeMillis() - program.startTime).coerceIn(0L, epgDuration)
                } ?: 0L
            }
            val canScrub = mediaDuration > 0L && playerState.isSeekable

            if (timelineDuration > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm)
                ) {
                    A2TouchPlayerTimeline(
                        positionMillis = timelinePosition,
                        durationMillis = timelineDuration,
                        bufferedPositionMillis = playerState.bufferedPosition,
                        enabled = canScrub,
                        tablet = isTablet,
                        onScrub = onScrub,
                        onScrubFinished = onScrubFinished,
                        modifier = Modifier.weight(1f)
                    )
                    if (isLivePlayback && canScrub) {
                        A2CompactPlayerAction(
                            icon = Icons.Filled.KeyboardDoubleArrowRight,
                            label = stringResource(R.string.player_go_live),
                            onClick = onGoLive,
                            tablet = isTablet
                        )
                    }
                }
            }

            if (isTablet) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    A2TouchTransportRow(
                        isLivePlayback = isLivePlayback,
                        isSeriesPlayback = isSeriesPlayback,
                        isPlaying = playerState.isPlaying,
                        seekable = playerState.isSeekable,
                        previousContentEnabled = previousContentEnabled,
                        nextContentEnabled = nextContentEnabled,
                        seekBackwardSeconds = seekBackwardSeconds,
                        seekForwardSeconds = seekForwardSeconds,
                        onPreviousContent = onPreviousContent,
                        onSeekBackward = onSeekBackward,
                        onPlayPause = onPlayPause,
                        onSeekForward = onSeekForward,
                        onNextContent = onNextContent
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm)) {
                        A2PlayerControlButton(
                            Icons.Filled.Audiotrack,
                            stringResource(R.string.setting_audio_track),
                            onOpenAudio
                        )
                        A2PlayerControlButton(
                            Icons.Filled.Subtitles,
                            stringResource(R.string.setting_subtitles),
                            onOpenSubtitles
                        )
                        A2PlayerControlButton(
                            Icons.Filled.HighQuality,
                            stringResource(R.string.setting_video_quality),
                            onOpenQuality
                        )
                        A2PlayerControlButton(
                            Icons.Filled.AspectRatio,
                            stringResource(R.string.setting_display_mode),
                            onOpenAspectRatio
                        )
                        A2PlayerControlButton(
                            Icons.Filled.Speed,
                            stringResource(R.string.setting_playback_speed),
                            onOpenSpeed
                        )
                        A2PlayerControlButton(
                            Icons.Filled.Lock,
                            stringResource(R.string.player_lock_screen),
                            onLock
                        )
                    }
                }
            } else {
                A2TouchTransportRow(
                    isLivePlayback = isLivePlayback,
                    isSeriesPlayback = isSeriesPlayback,
                    isPlaying = playerState.isPlaying,
                    seekable = playerState.isSeekable,
                    previousContentEnabled = previousContentEnabled,
                    nextContentEnabled = nextContentEnabled,
                    seekBackwardSeconds = seekBackwardSeconds,
                    seekForwardSeconds = seekForwardSeconds,
                    onPreviousContent = onPreviousContent,
                    onSeekBackward = onSeekBackward,
                    onPlayPause = onPlayPause,
                    onSeekForward = onSeekForward,
                    onNextContent = onNextContent
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        A2Spacing.sm,
                        Alignment.CenterHorizontally
                    )
                ) {
                    A2PlayerControlButton(
                        Icons.Filled.Audiotrack,
                        stringResource(R.string.setting_audio_track),
                        onOpenAudio
                    )
                    A2PlayerControlButton(
                        Icons.Filled.Subtitles,
                        stringResource(R.string.setting_subtitles),
                        onOpenSubtitles
                    )
                    A2PlayerControlButton(
                        Icons.Filled.HighQuality,
                        stringResource(R.string.setting_video_quality),
                        onOpenQuality
                    )
                    A2PlayerControlButton(
                        Icons.Filled.Tune,
                        stringResource(R.string.player_options),
                        onOpenOptions
                    )
                }
            }
        }
    }
}

@Composable
private fun A2TouchTransportRow(
    isLivePlayback: Boolean,
    isSeriesPlayback: Boolean,
    isPlaying: Boolean,
    seekable: Boolean,
    previousContentEnabled: Boolean,
    nextContentEnabled: Boolean,
    seekBackwardSeconds: Long,
    seekForwardSeconds: Long,
    onPreviousContent: () -> Unit,
    onSeekBackward: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onNextContent: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        A2PlayerControlButton(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = stringResource(
                if (isLivePlayback) R.string.previous_channel else R.string.previous_episode
            ),
            onClick = onPreviousContent,
            enabled = previousContentEnabled && (isLivePlayback || isSeriesPlayback)
        )
        A2PlayerControlButton(
            icon = Icons.Filled.Replay10,
            contentDescription = stringResource(R.string.seek_backward_short, seekBackwardSeconds),
            onClick = onSeekBackward,
            enabled = seekable
        )
        A2PlayerControlButton(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(
                if (isPlaying) R.string.action_pause else R.string.action_play
            ),
            onClick = onPlayPause,
            stateDescription = stringResource(
                if (isPlaying) R.string.player_playing_feedback else R.string.player_paused_feedback
            )
        )
        A2PlayerControlButton(
            icon = Icons.Filled.Forward10,
            contentDescription = stringResource(R.string.seek_forward_short, seekForwardSeconds),
            onClick = onSeekForward,
            enabled = seekable
        )
        A2PlayerControlButton(
            icon = Icons.Filled.SkipNext,
            contentDescription = stringResource(
                if (isLivePlayback) R.string.next_channel else R.string.next_episode
            ),
            onClick = onNextContent,
            enabled = nextContentEnabled && (isLivePlayback || isSeriesPlayback)
        )
    }
}

@Composable
private fun A2TouchPlayerTimeline(
    positionMillis: Long,
    durationMillis: Long,
    bufferedPositionMillis: Long,
    enabled: Boolean,
    tablet: Boolean,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMillis.coerceAtLeast(1L)
    val safePosition = positionMillis.coerceIn(0L, safeDuration)
    val playedFraction = (safePosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPositionMillis.toFloat() / safeDuration.toFloat())
        .coerceIn(playedFraction, 1f)
    val trackHeight = if (tablet) 6.dp else 4.dp
    val touchHeight = if (tablet) 34.dp else 28.dp

    Column(
        modifier = modifier.height(if (tablet) 64.dp else 48.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(touchHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(A2Shape.full)
                    .background(IdealPlayerColors.CardBorder)
            )
            if (bufferedFraction > 0f) {
                Box(
                    Modifier
                        .width(maxWidth * bufferedFraction)
                        .height(trackHeight)
                        .clip(A2Shape.full)
                        .background(IdealPlayerColors.TextTertiary.copy(alpha = 0.38f))
                )
            }
            if (playedFraction > 0f) {
                Box(
                    Modifier
                        .width(maxWidth * playedFraction)
                        .height(trackHeight)
                        .clip(A2Shape.full)
                        .background(IdealPlayerColors.Primary)
                )
                Box(
                    Modifier
                        .offset(x = (maxWidth - 4.dp) * playedFraction)
                        .width(4.dp)
                        .height(trackHeight)
                        .background(IdealPlayerColors.TextPrimary)
                )
            }
            Slider(
                value = playedFraction,
                onValueChange = { fraction ->
                    onScrub((safeDuration * fraction.coerceIn(0f, 1f)).toLong())
                },
                onValueChangeFinished = onScrubFinished,
                enabled = enabled,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    disabledThumbColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxSize()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                StringUtils.formatDuration(safePosition),
                style = MaterialTheme.typography.bodySmall,
                color = IdealPlayerColors.TextSecondary
            )
            Text(
                StringUtils.formatDuration(safeDuration),
                style = MaterialTheme.typography.bodySmall,
                color = IdealPlayerColors.TextSecondary
            )
        }
    }
}

@Composable
private fun A2CompactPlayerAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tablet: Boolean
) {
    Surface(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick),
        shape = A2Shape.medium,
        color = IdealPlayerColors.SurfaceElevated,
        border = BorderStroke(1.dp, IdealPlayerColors.CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (tablet) 16.dp else 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (tablet) 8.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = IdealPlayerColors.TextPrimary,
                modifier = Modifier.size(if (tablet) 28.dp else 20.dp)
            )
            Text(
                text = label,
                color = IdealPlayerColors.TextPrimary,
                style = if (tablet) MaterialTheme.typography.labelLarge else
                    MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

// ─── EPG Mini Strip ─────────────────────────────────────────────────────────────
@Composable
fun EpgMiniStrip(
    currentProgram: com.idealplayer.app.data.parser.EpgProgram?,
    nextProgram: com.idealplayer.app.data.parser.EpgProgram?
) {
    currentProgram ?: return
    val nowMs = System.currentTimeMillis()
    val progress = ((nowMs - currentProgram.startTime).toFloat() /
        (currentProgram.endTime - currentProgram.startTime).toFloat())
        .coerceIn(0f, 1f)
    val timeFmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val startFmt = remember(currentProgram.startTime) { timeFmt.format(java.util.Date(currentProgram.startTime)) }
    val endFmt = remember(currentProgram.endTime) { timeFmt.format(java.util.Date(currentProgram.endTime)) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = IdealPlayerColors.Primary.copy(alpha = 0.85f),
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Text(stringResource(R.string.player_content_live), style = MaterialTheme.typography.labelSmall, color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
            }
            Text(text = currentProgram.title, style = MaterialTheme.typography.labelMedium,
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(text = "$startFmt – $endFmt", style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
            color = IdealPlayerColors.Primary,
            trackColor = Color.White.copy(alpha = 0.2f)
        )
        nextProgram?.let { next ->
            Spacer(Modifier.height(2.dp))
            Text(text = stringResource(R.string.player_next_program, next.title), style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChannelSwitchEpgDetails(
    epgProgram: com.idealplayer.app.data.parser.EpgProgram?,
    primaryColor: Color,
    showProgress: Boolean
) {
    when {
        epgProgram != null -> {
            val progress = epgProgram.progressFraction
            val progressPercent = (progress * 100f).roundToInt().coerceIn(0, 100)
            val timeFormatter = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
            val startTime = remember(epgProgram.startTime) {
                timeFormatter.format(java.util.Date(epgProgram.startTime))
            }
            val endTime = remember(epgProgram.endTime) {
                timeFormatter.format(java.util.Date(epgProgram.endTime))
            }

            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.epg_current_program_format, epgProgram.title),
                style = MaterialTheme.typography.bodySmall,
                color = primaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.epg_progress_percent_format,
                    startTime,
                    endTime,
                    progressPercent
                ),
                style = MaterialTheme.typography.labelSmall,
                color = primaryColor.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showProgress) {
                Spacer(modifier = Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = IdealPlayerColors.Primary,
                    trackColor = Color.White.copy(alpha = 0.14f)
                )
            }
        }

        else -> {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.epg_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = primaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Player Settings Bottom Sheet
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun playerAspectRatioLabel(mode: AspectRatioMode): String = stringResource(
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
private fun playerQualityModeLabel(mode: VideoQualityMode): String = stringResource(
    when (mode) {
        VideoQualityMode.AUTO -> R.string.player_quality_auto
        VideoQualityMode.BEST -> R.string.player_quality_best
        VideoQualityMode.BALANCED -> R.string.player_quality_balanced
        VideoQualityMode.DATA_SAVER -> R.string.player_quality_data_saver
    }
)

@Composable
private fun playerPlaybackStateLabel(state: PlaybackState): String = stringResource(
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSettingsSheet(
    playerState: PlayerState,
    diagnostics: PlaybackDiagnostics,
    isTv: Boolean,
    initialSection: String?,
    isLivePlayback: Boolean,
    sleepTimerRemaining: String?,
    onDismiss: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onLockScreen: () -> Unit,
    onSetAspectRatio: (AspectRatioMode) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetQualityMode: (VideoQualityMode) -> Unit,
    onSelectVideoTrack: (Int) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onDisableSubtitles: () -> Unit,
    onCopyDiagnostics: () -> Unit
) {
    var activeSection by remember(initialSection) { mutableStateOf(initialSection) }

    if (isTv) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    modifier = Modifier.fillMaxHeight().width(340.dp).padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = IdealPlayerColors.Surface.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    PlayerSettingsContent(
                        playerState = playerState,
                        diagnostics = diagnostics,
                        isTv = true,
                        isLivePlayback = isLivePlayback,
                        sleepTimerRemaining = sleepTimerRemaining,
                        activeSection = activeSection,
                        onSectionChange = { activeSection = it },
                        onOpenChannels = onOpenChannels,
                        onOpenSleepTimer = onOpenSleepTimer,
                        onLockScreen = onLockScreen,
                        onSetAspectRatio = onSetAspectRatio,
                        onSetSpeed = onSetSpeed,
                        onSetQualityMode = onSetQualityMode,
                        onSelectVideoTrack = onSelectVideoTrack,
                        onSelectAudio = onSelectAudio,
                        onSelectSubtitle = onSelectSubtitle,
                        onDisableSubtitles = onDisableSubtitles,
                        onCopyDiagnostics = onCopyDiagnostics
                    )
                }
            }
        }
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = IdealPlayerColors.Surface,
        contentColor = IdealPlayerColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(IdealPlayerColors.TextTertiary))
            }
        }
    ) {
        PlayerSettingsContent(
            playerState = playerState,
            diagnostics = diagnostics,
            isTv = false,
            isLivePlayback = isLivePlayback,
            sleepTimerRemaining = sleepTimerRemaining,
            activeSection = activeSection,
            onSectionChange = { activeSection = it },
            onOpenChannels = onOpenChannels,
            onOpenSleepTimer = onOpenSleepTimer,
            onLockScreen = onLockScreen,
            onSetAspectRatio = onSetAspectRatio,
            onSetSpeed = onSetSpeed,
            onSetQualityMode = onSetQualityMode,
            onSelectVideoTrack = onSelectVideoTrack,
            onSelectAudio = onSelectAudio,
            onSelectSubtitle = onSelectSubtitle,
            onDisableSubtitles = onDisableSubtitles,
            onCopyDiagnostics = onCopyDiagnostics
        )
    }
}

@Composable
private fun PlayerSettingsContent(
    playerState: PlayerState,
    diagnostics: PlaybackDiagnostics,
    isTv: Boolean,
    isLivePlayback: Boolean,
    sleepTimerRemaining: String?,
    activeSection: String?,
    onSectionChange: (String?) -> Unit,
    onOpenChannels: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onLockScreen: () -> Unit,
    onSetAspectRatio: (AspectRatioMode) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetQualityMode: (VideoQualityMode) -> Unit,
    onSelectVideoTrack: (Int) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onDisableSubtitles: () -> Unit,
    onCopyDiagnostics: () -> Unit
) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
            // Header
            Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall, color = IdealPlayerColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))

            if (activeSection == null) {
                // Main settings menu
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    // Display Mode
                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.AspectRatio,
                            title = stringResource(R.string.setting_display_mode),
                            subtitle = playerAspectRatioLabel(playerState.aspectRatioMode),
                            isTv = isTv,
                            onClick = { onSectionChange("aspect") }
                        )
                    }

                    // Video Quality
                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.HighQuality,
                            title = stringResource(R.string.setting_video_quality),
                            subtitle = if (playerState.availableQualities.isNotEmpty())
                                playerQualityModeLabel(playerState.videoQualityMode)
                            else stringResource(R.string.player_not_available),
                            isTv = isTv,
                            onClick = { onSectionChange("quality") }
                        )
                    }

                    // Playback Speed
                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.Speed,
                            title = stringResource(R.string.setting_playback_speed),
                            subtitle = "${playerState.playbackSpeed}x",
                            isTv = isTv,
                            onClick = { onSectionChange("speed") }
                        )
                    }

                    // Audio Track
                    if (playerState.audioTracks.isNotEmpty()) {
                        item {
                            val selectedAudio = playerState.audioTracks.find { it.isSelected }
                            SettingsMenuItem(
                                icon = Icons.Filled.Audiotrack,
                                title = stringResource(R.string.setting_audio_track),
                                subtitle = selectedAudio?.name
                                    ?: stringResource(R.string.player_default_track),
                                isTv = isTv,
                                onClick = { onSectionChange("audio") }
                            )
                        }
                    }

                    // Subtitle Track
                    item {
                        val selectedSub = playerState.subtitleTracks.find { it.isSelected }
                        SettingsMenuItem(
                            icon = Icons.Filled.Subtitles,
                            title = stringResource(R.string.setting_subtitles),
                            subtitle = selectedSub?.name
                                ?: stringResource(R.string.player_subtitles_off),
                            isTv = isTv,
                            onClick = { onSectionChange("subtitle") }
                        )
                    }

                    if (isLivePlayback) {
                        item {
                            SettingsMenuItem(
                                icon = Icons.AutoMirrored.Filled.List,
                                title = stringResource(R.string.channel_list),
                                subtitle = stringResource(R.string.channels),
                                isTv = isTv,
                                onClick = onOpenChannels
                            )
                        }
                    }

                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.Bedtime,
                            title = stringResource(R.string.player_sleep_timer),
                            subtitle = sleepTimerRemaining
                                ?: stringResource(R.string.player_not_active),
                            isTv = isTv,
                            onClick = onOpenSleepTimer
                        )
                    }

                    if (!isTv) {
                        item {
                            SettingsMenuItem(
                                icon = Icons.Filled.Lock,
                                title = stringResource(R.string.player_lock_screen),
                                subtitle = stringResource(R.string.player_lock_screen_description),
                                isTv = false,
                                onClick = onLockScreen
                            )
                        }
                    }

                    // Stream Info
                    item {
                        SettingsMenuItem(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.setting_stream_info),
                            subtitle = if (playerState.currentVideoResolution.isNotBlank()) playerState.currentVideoResolution else "—",
                            isTv = isTv,
                            onClick = { onSectionChange("info") }
                        )
                    }
                }
            } else {
                // Section back button
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var isBackFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { onSectionChange(null) },
                        modifier = Modifier
                            .onFocusChanged { isBackFocused = it.isFocused }
                            .focusable()
                            .background(
                                if (isBackFocused) IdealPlayerColors.SurfaceFocus else Color.Transparent,
                                CircleShape
                            )
                            .border(
                                if (isBackFocused) LocalIdealPlayerDimens.current.focusBorderWidth else 0.dp,
                                if (isBackFocused) IdealPlayerColors.FocusBorder else Color.Transparent,
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.player_back),
                            tint = IdealPlayerColors.TextPrimary
                        )
                    }
                    Text(
                        when (activeSection) {
                            "aspect" -> stringResource(R.string.setting_display_mode)
                            "quality" -> stringResource(R.string.setting_video_quality)
                            "speed" -> stringResource(R.string.setting_playback_speed)
                            "audio" -> stringResource(R.string.setting_audio_track)
                            "subtitle" -> stringResource(R.string.setting_subtitles)
                            "info" -> stringResource(R.string.setting_stream_info)
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = IdealPlayerColors.TextPrimary
                    )
                }

                HorizontalDivider(color = IdealPlayerColors.DividerColor)

                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when (activeSection) {
                        "aspect" -> {
                            items(AspectRatioMode.entries.toList()) { mode ->
                                SettingsOptionItem(
                                    label = playerAspectRatioLabel(mode),
                                    isSelected = playerState.aspectRatioMode == mode,
                                    isTv = isTv,
                                    onClick = { onSetAspectRatio(mode); onSectionChange(null) }
                                )
                            }
                        }
                        "quality" -> {
                            // Quality modes
                            items(VideoQualityMode.entries.toList()) { mode ->
                                SettingsOptionItem(
                                    label = playerQualityModeLabel(mode),
                                    isSelected = playerState.videoQualityMode == mode,
                                    isTv = isTv,
                                    onClick = { onSetQualityMode(mode); onSectionChange(null) }
                                )
                            }
                            // Specific resolutions
                            if (playerState.availableQualities.isNotEmpty()) {
                                item {
                                    HorizontalDivider(color = IdealPlayerColors.DividerColor, modifier = Modifier.padding(vertical = 8.dp))
                                    Text(stringResource(R.string.player_available_resolutions), style = MaterialTheme.typography.labelMedium,
                                        color = IdealPlayerColors.TextTertiary,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                                }
                                items(playerState.availableQualities) { q ->
                                    SettingsOptionItem(
                                        label = q.label,
                                        subtitle = if (q.bitrate > 0) "${q.bitrate / 1000} kbps" else null,
                                        isSelected = q.isSelected,
                                        isTv = isTv,
                                        onClick = { onSelectVideoTrack(q.index); onSectionChange(null) }
                                    )
                                }
                            }
                        }
                        "speed" -> {
                            items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)) { speed ->
                                SettingsOptionItem(
                                    label = "${speed}x",
                                    isSelected = playerState.playbackSpeed == speed,
                                    isTv = isTv,
                                    onClick = { onSetSpeed(speed); onSectionChange(null) }
                                )
                            }
                        }
                        "audio" -> {
                            items(playerState.audioTracks) { track ->
                                SettingsOptionItem(
                                    label = track.name,
                                    subtitle = track.language.ifEmpty { null },
                                    isSelected = track.isSelected,
                                    isTv = isTv,
                                    onClick = { onSelectAudio(track.index); onSectionChange(null) }
                                )
                            }
                        }
                        "subtitle" -> {
                            item {
                                SettingsOptionItem(
                                    label = stringResource(R.string.player_subtitles_off),
                                    isSelected = playerState.selectedSubtitleTrack == -1,
                                    isTv = isTv,
                                    onClick = { onDisableSubtitles(); onSectionChange(null) }
                                )
                            }
                            items(playerState.subtitleTracks) { track ->
                                SettingsOptionItem(
                                    label = track.name,
                                    subtitle = track.language.ifEmpty { null },
                                    isSelected = track.isSelected,
                                    isTv = isTv,
                                    onClick = { onSelectSubtitle(track.index); onSectionChange(null) }
                                )
                            }
                        }
                        "info" -> {
                            item {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    StreamInfoRow(
                                        stringResource(R.string.player_info_resolution),
                                        videoResolutionBadge(
                                            playerState.videoWidth,
                                            playerState.videoHeight
                                        ).ifBlank { playerState.currentVideoResolution.ifBlank { "—" } }
                                    )
                                    StreamInfoRow(stringResource(R.string.player_info_engine), diagnostics.engineName)
                                    StreamInfoRow(
                                        stringResource(R.string.player_info_playback),
                                        playerPlaybackStateLabel(playerState.playbackState)
                                    )
                                    StreamInfoRow(
                                        stringResource(R.string.player_info_confirmed),
                                        stringResource(
                                            if (playerState.isPlaybackConfirmed) R.string.yes else R.string.no
                                        )
                                    )
                                    StreamInfoRow(stringResource(R.string.player_info_bitrate), playerState.currentVideoBitrate.ifBlank { "—" })
                                    StreamInfoRow(stringResource(R.string.player_info_codec), playerState.currentVideoCodec.ifBlank { "—" })
                                    StreamInfoRow(stringResource(R.string.player_info_fps), playerState.currentVideoFps.ifBlank { "—" })
                                    StreamInfoRow(
                                        stringResource(R.string.player_info_network_speed),
                                        formatNetworkSpeed(playerState.networkSpeedKbps).ifBlank { "—" }
                                    )
                                    StreamInfoRow(stringResource(R.string.player_info_video_size), if (playerState.videoWidth > 0) "${playerState.videoWidth}x${playerState.videoHeight}" else "—")
                                    StreamInfoRow(
                                        stringResource(R.string.player_info_adaptive),
                                        stringResource(if (playerState.isAdaptiveStream) R.string.yes else R.string.no)
                                    )
                                    StreamInfoRow(stringResource(R.string.setting_display_mode), playerAspectRatioLabel(playerState.aspectRatioMode))
                                    StreamInfoRow(stringResource(R.string.setting_video_quality), playerQualityModeLabel(playerState.videoQualityMode))
                                    StreamInfoRow(stringResource(R.string.setting_playback_speed), "${playerState.playbackSpeed}x")
                                    StreamInfoRow(
                                        stringResource(R.string.player_info_buffer),
                                        "${(playerState.bufferedPosition - playerState.currentPosition).coerceAtLeast(0L)} ms"
                                    )
                                    StreamInfoRow(stringResource(R.string.player_info_source), "${diagnostics.streamProtocol}:${diagnostics.streamHost}")
                                    StreamInfoRow(
                                        stringResource(R.string.player_info_request_headers),
                                        diagnostics.requestHeaderNames.sorted().joinToString().ifBlank {
                                            stringResource(R.string.player_none)
                                        }
                                    )
                                    StreamInfoRow(
                                        stringResource(R.string.player_info_recovery),
                                        when {
                                            diagnostics.recovery.isRecovering -> stringResource(R.string.player_recovery_in_progress)
                                            diagnostics.recovery.automaticFallbackUsed -> stringResource(R.string.player_recovery_vlc_used)
                                            else -> stringResource(R.string.player_recovery_not_used)
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = onCopyDiagnostics,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.copy_playback_diagnostics))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isTv: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = A2Shape.medium

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = A2Spacing.sm, vertical = 2.dp)
            .defaultMinSize(minHeight = if (isTv) 56.dp else 48.dp)
            .clip(shape)
            .background(if (isFocused) IdealPlayerColors.SurfaceFocus else Color.Transparent)
            .border(
                width = if (isFocused) LocalIdealPlayerDimens.current.focusBorderWidth else 0.dp,
                color = if (isFocused) IdealPlayerColors.FocusBorder else Color.Transparent,
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isFocused) IdealPlayerColors.Primary else IdealPlayerColors.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = IdealPlayerColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = IdealPlayerColors.TextSecondary)
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = IdealPlayerColors.TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsOptionItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isTv: Boolean = false,
    subtitle: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = A2Shape.medium

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = A2Spacing.sm, vertical = 2.dp)
            .defaultMinSize(minHeight = if (isTv) 56.dp else 48.dp)
            .clip(shape)
            .background(
                when {
                    isFocused -> IdealPlayerColors.SurfaceFocus
                    isSelected -> IdealPlayerColors.SurfaceSelected
                    else -> Color.Transparent
                }
            )
            .border(
                width = when {
                    isFocused -> LocalIdealPlayerDimens.current.focusBorderWidth
                    isSelected -> 2.dp
                    else -> 0.dp
                },
                color = when {
                    isFocused -> IdealPlayerColors.FocusBorder
                    isSelected -> IdealPlayerColors.SelectedBorder
                    else -> Color.Transparent
                },
                shape = shape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) IdealPlayerColors.Secondary else IdealPlayerColors.TextPrimary
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = IdealPlayerColors.TextSecondary)
            }
        }
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = IdealPlayerColors.Secondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StreamInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = IdealPlayerColors.TextTertiary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = IdealPlayerColors.TextPrimary)
    }
}

@Composable
private fun PlayerControlButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    size: Dp = 36.dp,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = when {
        isPrimary -> IdealPlayerColors.Primary
        isFocused -> Color.White.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    val borderMod = if (isFocused && !isPrimary) Modifier.border(2.dp, IdealPlayerColors.FocusBorder, CircleShape) else Modifier

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor)
            .then(borderMod)
            .clickable(onClick = onClick)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(if (isPrimary) 12.dp else 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size)
        )
        if (label != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

private fun episodeLabel(episode: Episode): String {
    return buildString {
        append("S${episode.seasonNumber}E${episode.episodeNumber}")
        if (episode.name.isNotBlank()) {
            append(" • ")
            append(episode.name)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChannelSwitchSheet(
    channels: List<Channel>,
    currentChannelId: Long,
    isTv: Boolean,
    epgPrograms: Map<Long, com.idealplayer.app.data.parser.EpgProgram>,
    onDismiss: () -> Unit,
    onChannelSelected: (Channel) -> Unit
) {
    if (isTv) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    modifier = Modifier.fillMaxHeight().width(340.dp).padding(vertical = 16.dp).padding(end = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = IdealPlayerColors.Surface.copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = stringResource(R.string.channel_list),
                            style = MaterialTheme.typography.headlineSmall,
                            color = IdealPlayerColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(channels, key = { it.id }) { channel ->
                                val isCurrent = channel.id == currentChannelId
                                val epgProgram = epgPrograms[channel.id]
                                val interactionSource = remember(channel.id) { MutableInteractionSource() }
                                val isFocused by interactionSource.collectIsFocusedAsState()
                                val itemShape = RoundedCornerShape(12.dp)
                                
                                Surface(
                                    shape = itemShape,
                                    color = when {
                                        isFocused -> IdealPlayerColors.SurfaceElevated
                                        isCurrent -> IdealPlayerColors.SurfaceSelected
                                        else -> IdealPlayerColors.SurfaceVariant
                                    },
                                    border = BorderStroke(
                                        width = when {
                                            isFocused -> 4.dp
                                            isCurrent -> 1.dp
                                            else -> 0.dp
                                        },
                                        color = when {
                                            isFocused -> IdealPlayerColors.FocusBorder
                                            isCurrent -> IdealPlayerColors.SelectedBorder
                                            else -> Color.Transparent
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            scaleX = if (isFocused) 1.025f else 1f
                                            scaleY = if (isFocused) 1.025f else 1f
                                            shape = itemShape
                                            clip = false
                                        }
                                        .clickable(
                                            enabled = !isCurrent,
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) { onChannelSelected(channel) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = channel.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = IdealPlayerColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            ChannelSwitchEpgDetails(
                                                epgProgram = epgProgram,
                                                primaryColor = if (isFocused) IdealPlayerColors.TextSecondary else IdealPlayerColors.TextTertiary,
                                                showProgress = true
                                            )
                                        }
                                        if (isCurrent) {
                                            Text(
                                                text = stringResource(R.string.now_playing),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = IdealPlayerColors.Secondary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                tint = IdealPlayerColors.Secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = IdealPlayerColors.Surface,
        contentColor = IdealPlayerColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.78f)
        ) {
            Text(
                text = stringResource(R.string.channel_list),
                style = MaterialTheme.typography.headlineSmall,
                color = IdealPlayerColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(channels, key = { it.id }) { channel ->
                    val isCurrent = channel.id == currentChannelId
                    val epgProgram = epgPrograms[channel.id]
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrent) {
                            IdealPlayerColors.SurfaceSelected
                        } else {
                            IdealPlayerColors.SurfaceVariant
                        },
                        border = BorderStroke(
                            width = if (isCurrent) 1.dp else 0.dp,
                            color = if (isCurrent) IdealPlayerColors.SelectedBorder else Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isCurrent) { onChannelSelected(channel) }
                            .alpha(if (!isCurrent) 1f else 0.72f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = IdealPlayerColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                ChannelSwitchEpgDetails(
                                    epgProgram = epgProgram,
                                    primaryColor = IdealPlayerColors.TextTertiary,
                                    showProgress = true
                                )
                            }
                            if (isCurrent) {
                                Text(
                                    text = stringResource(R.string.now_playing),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = IdealPlayerColors.Secondary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = IdealPlayerColors.Secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
