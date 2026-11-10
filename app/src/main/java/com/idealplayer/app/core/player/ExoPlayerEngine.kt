package com.idealplayer.app.core.player

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.idealplayer.app.core.common.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class ExoPlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : PlayerEngine {

    override val engineName: String = "EXOPLAYER"
    override fun isAvailable(): Boolean = true

    companion object {
        private const val PLAYBACK_CONFIRMATION_PROGRESS_MS = 750L
        private const val PLAYBACK_PROGRESS_POLL_INTERVAL_MS = 500L
    }

    private data class PendingPlaybackRequest(
        val profile: PlaybackProfile,
        val startPosition: Long
    )

    private interface SurfaceBinding {
        fun isReady(): Boolean
        fun dispose()
    }

    private val mainScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val userAgent = "IdealPlayer/Android"

    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var progressJob: Job? = null

    private var playerView: PlayerView? = null
    private var surfaceBinding: SurfaceBinding? = null
    private var pendingPlaybackRequest: PendingPlaybackRequest? = null
    private var currentProfile: PlaybackProfile? = null

    private var configuredBufferMs: Long = Constants.DEFAULT_BUFFER_MS.toLong()
    private var configuredLatencyMode: String = LiveLatencyMode.BALANCED.name
    private var configuredPreferHw: Boolean = true
    private var configuredAllowQualityFallback: Boolean = true

    private var preferredAudioLanguage: String? = null
    private var preferredSubtitleLanguage: String? = null
    private var subtitlesDisabled = false
    private var preferredVideoQualityMode: VideoQualityMode = VideoQualityMode.AUTO
    private var currentPlaybackSpeed = 1f

    private var surfaceReady = false
    private var hasVideoTrack = false
    private var hasAudioTrack = false
    private var firstFrameRendered = false
    private var playbackConfirmationBaselinePositionMs = C.TIME_UNSET
    private var playbackProgressConfirmed = false
    private val networkThroughputSampler = NetworkThroughputSampler()

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _playerInstance = MutableStateFlow<ExoPlayer?>(null)
    val playerInstance: StateFlow<ExoPlayer?> = _playerInstance.asStateFlow()

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainScope.launch { block() }
        }
    }

    override fun setPlaybackConfiguration(
        bufferDurationMs: Long,
        liveLatencyMode: String,
        preferHwDecoding: Boolean,
        allowQualityFallback: Boolean
    ) {
        val configurationChanged = configuredBufferMs != bufferDurationMs ||
            !configuredLatencyMode.equals(liveLatencyMode, ignoreCase = true) ||
            configuredPreferHw != preferHwDecoding ||
            configuredAllowQualityFallback != allowQualityFallback
        configuredBufferMs = bufferDurationMs
        configuredLatencyMode = liveLatencyMode
        configuredPreferHw = preferHwDecoding
        configuredAllowQualityFallback = allowQualityFallback

        if (configurationChanged) {
            runOnMain {
                val profile = currentProfile ?: return@runOnMain
                rebuildPlayer(profile)
            }
        }
    }

    override fun initialize() {
        runOnMain {
            ensurePlayer(currentProfile ?: PlaybackProfile.VOD)
        }
    }

    override fun release() {
        runOnMain {
            releasePlayer()
        }
    }

    override fun play(url: String, startPosition: Long, profile: PlaybackProfile) {
        runOnMain {
            ensurePlayer(profile)

            val exoPlayer = player ?: return@runOnMain
            val source = parsePlaybackSource(url)
            val mediaItem = buildMediaItem(source.url, profile)

            resetPlaybackAttemptState()
            pendingPlaybackRequest = PendingPlaybackRequest(
                profile = profile,
                startPosition = startPosition
            )

            // Close the gate before replacing the source. setMediaSource() may synchronously
            // publish an empty Tracks snapshot for the previous item; that callback must never
            // be followed by another playWhenReady=false assignment that pauses the new item.
            exoPlayer.playWhenReady = false
            exoPlayer.setMediaSource(buildMediaSource(mediaItem, source))
            exoPlayer.volume = 1f
            exoPlayer.prepare()

            if (startPosition > 0L) {
                exoPlayer.seekTo(startPosition)
            }

            updatePlaybackGate()
            publishPlayerState()
        }
    }

    override fun pause() {
        runOnMain {
            player?.pause()
            pendingPlaybackRequest = null
            publishPlayerState()
        }
    }

    override fun resume() {
        runOnMain {
            val exoPlayer = player ?: return@runOnMain
            if (hasVideoTrack && !surfaceReady) {
                pendingPlaybackRequest = PendingPlaybackRequest(
                    profile = currentProfile ?: PlaybackProfile.VOD,
                    startPosition = exoPlayer.currentPosition
                )
                exoPlayer.playWhenReady = false
                publishPlayerState()
                return@runOnMain
            }

            exoPlayer.play()
            publishPlayerState()
        }
    }

    override fun stop() {
        runOnMain {
            stopInternal(clearMediaItems = true)
        }
    }

    override fun seekTo(position: Long) {
        runOnMain {
            val exoPlayer = player ?: return@runOnMain
            val target = resolveSeekPosition(
                requestedPosition = position,
                duration = exoPlayer.duration,
                isSeekable = isCurrentMediaItemSeekable(exoPlayer)
            ) ?: run {
                Timber.d("Ignoring Media3 seek because the current stream is not seekable")
                return@runOnMain
            }
            exoPlayer.seekTo(target)
            _state.value = _state.value.copy(currentPosition = target)
        }
    }

    override fun seekForward(ms: Long) {
        runOnMain {
            val exoPlayer = player ?: return@runOnMain
            val target = resolveRelativeSeekPosition(
                currentPosition = exoPlayer.currentPosition,
                deltaMs = ms.coerceAtLeast(0L),
                duration = exoPlayer.duration,
                isSeekable = isCurrentMediaItemSeekable(exoPlayer)
            ) ?: return@runOnMain
            exoPlayer.seekTo(target)
            _state.value = _state.value.copy(currentPosition = target)
        }
    }

    override fun seekBackward(ms: Long) {
        runOnMain {
            val exoPlayer = player ?: return@runOnMain
            val target = resolveRelativeSeekPosition(
                currentPosition = exoPlayer.currentPosition,
                deltaMs = -ms.coerceAtLeast(0L),
                duration = exoPlayer.duration,
                isSeekable = isCurrentMediaItemSeekable(exoPlayer)
            ) ?: return@runOnMain
            exoPlayer.seekTo(target)
            _state.value = _state.value.copy(currentPosition = target)
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        currentPlaybackSpeed = speed
        runOnMain {
            player?.setPlaybackSpeed(speed)
            publishPlayerState()
        }
    }

    override fun selectAudioTrack(index: Int) {
        runOnMain {
            val selection = findTrackSelection(C.TRACK_TYPE_AUDIO, index) ?: return@runOnMain
            val selector = trackSelector ?: return@runOnMain
            selector.setParameters(
                selector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .setOverrideForType(
                        TrackSelectionOverride(
                            selection.group.mediaTrackGroup,
                            selection.trackIndexInGroup
                        )
                    )
            )
            _state.value = _state.value.copy(selectedAudioTrack = index)
            publishPlayerState()
        }
    }

    override fun selectSubtitleTrack(index: Int) {
        runOnMain {
            val selection = findTrackSelection(C.TRACK_TYPE_TEXT, index) ?: return@runOnMain
            val selector = trackSelector ?: return@runOnMain
            subtitlesDisabled = false
            selector.setParameters(
                selector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setOverrideForType(
                        TrackSelectionOverride(
                            selection.group.mediaTrackGroup,
                            selection.trackIndexInGroup
                        )
                    )
            )
            _state.value = _state.value.copy(selectedSubtitleTrack = index)
            publishPlayerState()
        }
    }

    override fun disableSubtitles() {
        subtitlesDisabled = true
        runOnMain {
            val selector = trackSelector ?: return@runOnMain
            selector.setParameters(
                selector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            )
            _state.value = _state.value.copy(selectedSubtitleTrack = -1)
            publishPlayerState()
        }
    }

    override fun setAspectRatio(mode: AspectRatioMode) {
        _state.value = _state.value.copy(aspectRatioMode = mode)
        runOnMain {
            applyResizeModeToView(mode)
        }
    }

    override fun setVideoQualityMode(mode: VideoQualityMode) {
        preferredVideoQualityMode = mode
        runOnMain {
            val selector = trackSelector
            if (selector == null) {
                _state.value = _state.value.copy(videoQualityMode = mode)
                return@runOnMain
            }

            when (mode) {
                VideoQualityMode.AUTO -> {
                    selector.setParameters(
                        selector.buildUponParameters()
                            .setExceedVideoConstraintsIfNecessary(configuredAllowQualityFallback)
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .clearVideoSizeConstraints()
                            .setForceHighestSupportedBitrate(false)
                            .setMaxVideoBitrate(Int.MAX_VALUE)
                    )
                }

                VideoQualityMode.BEST -> {
                    selector.setParameters(
                        selector.buildUponParameters()
                            .setExceedVideoConstraintsIfNecessary(configuredAllowQualityFallback)
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .clearVideoSizeConstraints()
                            .setForceHighestSupportedBitrate(true)
                            .setMaxVideoBitrate(Int.MAX_VALUE)
                    )
                }

                VideoQualityMode.BALANCED -> {
                    selector.setParameters(
                        selector.buildUponParameters()
                            .setExceedVideoConstraintsIfNecessary(configuredAllowQualityFallback)
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .setForceHighestSupportedBitrate(false)
                            .setMaxVideoSize(1920, 1080)
                            .setMaxVideoBitrate(8_000_000)
                    )
                }

                VideoQualityMode.DATA_SAVER -> {
                    selector.setParameters(
                        selector.buildUponParameters()
                            .setExceedVideoConstraintsIfNecessary(configuredAllowQualityFallback)
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .setForceHighestSupportedBitrate(false)
                            .setMaxVideoSize(1280, 720)
                            .setMaxVideoBitrate(3_000_000)
                    )
                }
            }

            _state.value = _state.value.copy(videoQualityMode = mode)
        }
    }

    override fun selectVideoTrack(index: Int) {
        runOnMain {
            val selection = findTrackSelection(C.TRACK_TYPE_VIDEO, index) ?: return@runOnMain
            val selector = trackSelector ?: return@runOnMain
            selector.setParameters(
                selector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .setOverrideForType(
                        TrackSelectionOverride(
                            selection.group.mediaTrackGroup,
                            selection.trackIndexInGroup
                        )
                    )
            )
            publishPlayerState()
        }
    }

    fun setPreferredAudioLanguage(langCode: String) {
        preferredAudioLanguage = normalizeLanguageCode(langCode).takeIf { it.isNotBlank() }
        runOnMain {
            applyPreferredAudioLanguage()
        }
    }

    fun setPreferredSubtitleLanguage(langCode: String) {
        val lower = langCode.lowercase(Locale.getDefault()).trim()
        if (lower == "off" || lower == "none") {
            preferredSubtitleLanguage = null
            disableSubtitles()
            return
        }

        subtitlesDisabled = false
        preferredSubtitleLanguage = normalizeLanguageCode(langCode).takeIf { it.isNotBlank() }
        runOnMain {
            applyPreferredSubtitleLanguage()
        }
    }

    fun attachPlayerView(view: PlayerView) {
        runOnMain {
            if (playerView === view) {
                view.player = player
                if (surfaceBinding == null) {
                    monitorVideoSurface(view.videoSurfaceView)
                }
                applyResizeModeToView(_state.value.aspectRatioMode)
                publishPlayerState()
                updatePlaybackGate()
                return@runOnMain
            }

            clearPlayerViewInternal()
            playerView = view
            configurePlayerView(view)
            view.player = player
            monitorVideoSurface(view.videoSurfaceView)
            applyResizeModeToView(_state.value.aspectRatioMode)
            publishPlayerState()
            updatePlaybackGate()
        }
    }

    fun clearPlayerView() {
        runOnMain {
            clearPlayerViewInternal()
        }
    }

    fun getExoPlayer(): ExoPlayer? = player

    fun getResizeModeFor(mode: AspectRatioMode = _state.value.aspectRatioMode): Int {
        return when (mode) {
            AspectRatioMode.AUTO -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatioMode.FORCE_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatioMode.FORCE_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    fun getViewportAspectRatio(mode: AspectRatioMode = _state.value.aspectRatioMode): Float? {
        return when (mode) {
            AspectRatioMode.FORCE_16_9 -> 16f / 9f
            AspectRatioMode.FORCE_4_3 -> 4f / 3f
            AspectRatioMode.ORIGINAL -> {
                val width = _state.value.videoWidth
                val height = _state.value.videoHeight
                if (width > 0 && height > 0) width.toFloat() / height.toFloat() else null
            }

            else -> null
        }
    }

    private fun ensurePlayer(profile: PlaybackProfile) {
        if (player == null) {
            buildPlayer(profile)
            return
        }

        if (currentProfile != profile) {
            rebuildPlayer(profile)
        }
    }

    private fun rebuildPlayer(profile: PlaybackProfile) {
        releasePlayer(retainUiState = true)
        buildPlayer(profile)
    }

    private fun buildPlayer(profile: PlaybackProfile) {
        val selector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setExceedVideoConstraintsIfNecessary(configuredAllowQualityFallback)
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            )
        }
        trackSelector = selector

        val dataSourceFactory = buildDataSourceFactory()
        val renderersFactory = IdealPlayerRenderersFactory(
            context = context,
            preferHardwareVideoDecoding = configuredPreferHw
        )

        val exoPlayer = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(selector)
            .setLoadControl(buildLoadControl(profile))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                volume = 1f
                setPlaybackSpeed(currentPlaybackSpeed)
            }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                publishPlayerState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startProgressTracking()
                } else {
                    stopProgressTracking()
                }
                publishPlayerState()
            }

            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                publishPlayerState()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                // Duration and the seek command can arrive after preparation, including while
                // paused when the progress poller is intentionally stopped.
                publishPlayerState()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                publishPlayerState()
            }

            override fun onPlayerError(error: PlaybackException) {
                // Media exceptions may include a DataSpec URL with provider credentials.
                Timber.e("ExoPlayer error: %s", error.errorCodeName)
                pendingPlaybackRequest = null
                stopProgressTracking()
                networkThroughputSampler.reset()
                _state.value = _state.value.copy(
                    playbackState = PlaybackState.ERROR,
                    isPlaying = false,
                    errorMessage = buildPlaybackErrorMessage(error),
                    hasRenderedFirstFrame = false,
                    isPlaybackConfirmed = false,
                    networkSpeedKbps = 0L
                )
            }

            override fun onTracksChanged(tracks: Tracks) {
                ensureDefaultAudioTrackSelected(tracks)
                refreshTrackFlags(tracks)
                updateTrackInfo(tracks)
                updateQualityInfo(tracks)
                publishPlayerState()
                updatePlaybackGate()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                _state.value = _state.value.copy(
                    videoWidth = videoSize.width,
                    videoHeight = videoSize.height,
                    currentVideoResolution = if (videoSize.width > 0 && videoSize.height > 0) {
                        "${videoSize.width}x${videoSize.height}"
                    } else {
                        ""
                    }
                )
                publishPlayerState()
            }

            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
                publishPlayerState()
            }
        }

        player = exoPlayer
        playerListener = listener
        currentProfile = profile
        exoPlayer.addListener(listener)

        applyPreferredAudioLanguage()
        applyPreferredSubtitleLanguage()
        setVideoQualityMode(preferredVideoQualityMode)

        playerView?.let { view ->
            configurePlayerView(view)
            view.player = exoPlayer
            monitorVideoSurface(view.videoSurfaceView)
            applyResizeModeToView(_state.value.aspectRatioMode)
        }

        _playerInstance.value = exoPlayer
        publishPlayerState()
    }

    private fun releasePlayer(retainUiState: Boolean = false) {
        stopProgressTracking()
        pendingPlaybackRequest = null

        playerListener?.let { listener ->
            player?.removeListener(listener)
        }
        playerListener = null

        if (retainUiState) {
            surfaceBinding?.dispose()
            surfaceBinding = null
            surfaceReady = false
            if (hasVideoTrack) {
                firstFrameRendered = false
            }
            playerView?.player = null
        } else {
            clearPlayerViewInternal()
        }

        player?.let { exoPlayer ->
            exoPlayer.playWhenReady = false
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }

        player = null
        trackSelector = null
        currentProfile = null
        _playerInstance.value = null

        if (retainUiState) {
            resetPlaybackAttemptState()
        } else {
            networkThroughputSampler.reset()
            surfaceReady = false
            hasVideoTrack = false
            hasAudioTrack = false
            firstFrameRendered = false
            _state.value = PlayerState(
                aspectRatioMode = _state.value.aspectRatioMode,
                videoQualityMode = preferredVideoQualityMode,
                playbackSpeed = currentPlaybackSpeed
            )
        }
    }

    private fun stopInternal(clearMediaItems: Boolean) {
        pendingPlaybackRequest = null
        stopProgressTracking()

        player?.let { exoPlayer ->
            exoPlayer.playWhenReady = false
            exoPlayer.stop()
            if (clearMediaItems) {
                exoPlayer.clearMediaItems()
            }
        }

        resetPlaybackAttemptState()
        publishPlayerState()
    }

    private fun buildLoadControl(profile: PlaybackProfile): DefaultLoadControl {
        val requestedBufferMs = configuredBufferMs.toInt()
            .coerceIn(Constants.MIN_BUFFER_MS, Constants.MAX_BUFFER_MS)

        val (minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferAfterRebufferMs) = when (profile) {
            PlaybackProfile.LIVE -> {
                val liveMin = (requestedBufferMs / 2).coerceIn(7_500, 18_000)
                val liveMax = requestedBufferMs.coerceIn(liveMin + 4_000, 28_000)
                val (startupBufferMs, rebufferMs) =
                    when (configuredLatencyMode.uppercase(Locale.getDefault())) {
                        LiveLatencyMode.LOW_LATENCY.name -> 400 to 1_200
                        LiveLatencyMode.STABLE.name -> 1_000 to 2_500
                        else -> 700 to 1_800
                    }
                listOf(liveMin, liveMax, startupBufferMs, rebufferMs)
            }

            PlaybackProfile.VOD -> {
                val vodMin = requestedBufferMs.coerceAtMost(50_000)
                val vodMax = requestedBufferMs
                listOf(
                    vodMin,
                    vodMax,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
            }
        }

        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferAfterRebufferMs
            )
            .build()
    }

    private fun buildMediaItem(url: String, profile: PlaybackProfile): MediaItem {
        val mimeType = inferMimeType(url)

        return MediaItem.Builder()
            .setUri(Uri.parse(url))
            .apply {
                if (mimeType != null) {
                    setMimeType(mimeType)
                }
                if (profile == PlaybackProfile.LIVE) {
                    setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setTargetOffsetMs(resolveLiveTargetOffsetMs())
                            .setMinPlaybackSpeed(0.97f)
                            .setMaxPlaybackSpeed(1.03f)
                            .build()
                    )
                }
            }
            .build()
    }

    private fun buildMediaSource(mediaItem: MediaItem, source: PlaybackSource): MediaSource {
        val dataSourceFactory = buildDataSourceFactory(source)
        return DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
    }

    private fun buildDataSourceFactory(source: PlaybackSource? = null): OkHttpDataSource.Factory {
        return OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(source?.userAgent ?: userAgent)
            .apply {
                source?.requestProperties
                    ?.takeIf(Map<String, String>::isNotEmpty)
                    ?.let(::setDefaultRequestProperties)
            }
    }

    private fun inferMimeType(url: String): String? {
        val normalized = url.substringBefore("?").lowercase(Locale.getDefault())
        return when {
            normalized.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            normalized.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            normalized.endsWith(".ism") || normalized.endsWith(".isml") -> MimeTypes.APPLICATION_SS
            normalized.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
            normalized.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            normalized.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
            normalized.endsWith(".mp3") -> MimeTypes.AUDIO_MPEG
            else -> null
        }
    }

    private fun resolveLiveTargetOffsetMs(): Long {
        return when (configuredLatencyMode.uppercase(Locale.getDefault())) {
            LiveLatencyMode.LOW_LATENCY.name -> 2_500L
            LiveLatencyMode.STABLE.name -> 8_000L
            else -> 5_000L
        }
    }

    private fun configurePlayerView(view: PlayerView) {
        view.setEnableComposeSurfaceSyncWorkaround(true)
        view.setShutterBackgroundColor(Color.BLACK)
        view.setKeepContentOnPlayerReset(false)
    }

    private fun monitorVideoSurface(videoSurfaceView: View?) {
        surfaceBinding?.dispose()
        surfaceBinding = null

        val binding = when (videoSurfaceView) {
            is SurfaceView -> createSurfaceViewBinding(videoSurfaceView)
            is View -> createGenericViewBinding(videoSurfaceView)
            else -> null
        }

        surfaceBinding = binding
        surfaceReady = binding?.isReady() == true
    }

    private fun createSurfaceViewBinding(surfaceView: SurfaceView): SurfaceBinding {
        lateinit var callback: SurfaceHolder.Callback
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            onSurfaceReadyChanged(
                surfaceView.holder.surface?.isValid == true &&
                    surfaceView.width > 0 &&
                    surfaceView.height > 0
            )
        }

        callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                onSurfaceReadyChanged(
                    holder.surface?.isValid == true &&
                        surfaceView.width > 0 &&
                        surfaceView.height > 0
                )
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                onSurfaceReadyChanged(
                    holder.surface?.isValid == true &&
                        width > 0 &&
                        height > 0
                )
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                onSurfaceReadyChanged(false)
            }
        }

        surfaceView.holder.addCallback(callback)
        surfaceView.addOnLayoutChangeListener(layoutListener)

        return object : SurfaceBinding {
            override fun isReady(): Boolean {
                return surfaceView.holder.surface?.isValid == true &&
                    surfaceView.width > 0 &&
                    surfaceView.height > 0
            }

            override fun dispose() {
                surfaceView.holder.removeCallback(callback)
                surfaceView.removeOnLayoutChangeListener(layoutListener)
            }
        }
    }

    private fun createGenericViewBinding(videoView: View): SurfaceBinding {
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            onSurfaceReadyChanged(
                videoView.isAttachedToWindow &&
                    videoView.width > 0 &&
                    videoView.height > 0
            )
        }
        val attachStateChangeListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                onSurfaceReadyChanged(view.width > 0 && view.height > 0)
            }

            override fun onViewDetachedFromWindow(view: View) {
                onSurfaceReadyChanged(false)
            }
        }

        videoView.addOnLayoutChangeListener(layoutListener)
        videoView.addOnAttachStateChangeListener(attachStateChangeListener)

        return object : SurfaceBinding {
            override fun isReady(): Boolean {
                return videoView.isAttachedToWindow &&
                    videoView.width > 0 &&
                    videoView.height > 0
            }

            override fun dispose() {
                videoView.removeOnLayoutChangeListener(layoutListener)
                videoView.removeOnAttachStateChangeListener(attachStateChangeListener)
            }
        }
    }

    private fun onSurfaceReadyChanged(isReady: Boolean) {
        surfaceReady = isReady
        if (!isReady && hasVideoTrack) {
            firstFrameRendered = false
        }
        publishPlayerState()
        updatePlaybackGate()
    }

    private fun clearPlayerViewInternal() {
        surfaceBinding?.dispose()
        surfaceBinding = null
        surfaceReady = false
        if (hasVideoTrack) {
            firstFrameRendered = false
        }

        playerView?.player = null
        playerView = null
    }

    private fun applyResizeModeToView(mode: AspectRatioMode) {
        val view = playerView ?: return
        view.resizeMode = getResizeModeFor(mode)
    }

    private fun updatePlaybackGate() {
        val exoPlayer = player ?: return
        val request = pendingPlaybackRequest ?: return

        val canStartPlayback = shouldOpenPlaybackGate(
            hasVideoTrack = hasVideoTrack,
            hasAudioTrack = hasAudioTrack,
            surfaceReady = surfaceReady
        )

        if (!canStartPlayback) {
            exoPlayer.playWhenReady = false
            publishPlayerState()
            return
        }

        exoPlayer.playWhenReady = true
        pendingPlaybackRequest = null
        if (request.startPosition > 0L && exoPlayer.currentPosition == 0L) {
            exoPlayer.seekTo(request.startPosition)
        }
        publishPlayerState()
    }

    private fun publishPlayerState() {
        val exoPlayer = player
        if (exoPlayer == null) {
            networkThroughputSampler.reset()
            _state.value = _state.value.copy(
                isSurfaceReady = surfaceReady,
                hasVideoTrack = hasVideoTrack,
                hasAudioTrack = hasAudioTrack,
                hasRenderedFirstFrame = firstFrameRendered,
                isPlaybackConfirmed = false,
                networkSpeedKbps = 0L,
                audioSessionId = 0
            )
            return
        }

        val playbackState = when (exoPlayer.playbackState) {
            Player.STATE_IDLE -> PlaybackState.IDLE
            Player.STATE_BUFFERING -> PlaybackState.BUFFERING
            Player.STATE_READY -> if (exoPlayer.isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED
            Player.STATE_ENDED -> PlaybackState.ENDED
            else -> PlaybackState.IDLE
        }

        updatePlaybackProgressConfirmation(
            exoPlayer = exoPlayer,
            playbackState = playbackState
        )

        val confirmed = when {
            playbackState != PlaybackState.PLAYING -> false
            hasVideoTrack -> {
                surfaceReady &&
                    (firstFrameRendered || playbackProgressConfirmed) &&
                    _state.value.videoWidth > 0 &&
                    _state.value.videoHeight > 0
            }

            hasAudioTrack -> exoPlayer.audioSessionId > 0 && playbackProgressConfirmed
            else -> false
        }
        val networkSpeedKbps = if (
            playbackState == PlaybackState.PLAYING || playbackState == PlaybackState.BUFFERING
        ) {
            networkThroughputSampler.sample()
        } else {
            networkThroughputSampler.reset()
            0L
        }

        _state.value = _state.value.copy(
            playbackState = playbackState,
            isPlaying = exoPlayer.isPlaying,
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L),
            duration = exoPlayer.duration.takeIf { it > 0L } ?: 0L,
            bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L),
            isSeekable = isCurrentMediaItemSeekable(exoPlayer),
            playbackSpeed = exoPlayer.playbackParameters.speed,
            hasVideoTrack = hasVideoTrack,
            hasAudioTrack = hasAudioTrack,
            isSurfaceReady = surfaceReady,
            hasRenderedFirstFrame = firstFrameRendered,
            isPlaybackConfirmed = confirmed,
            networkSpeedKbps = networkSpeedKbps,
            audioSessionId = exoPlayer.audioSessionId
        )
    }

    private fun refreshTrackFlags(tracks: Tracks) {
        hasVideoTrack = tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_VIDEO && group.length > 0
        }
        hasAudioTrack = tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_AUDIO && group.length > 0
        }
        if (!hasVideoTrack) {
            firstFrameRendered = false
        }
    }

    private fun updateTrackInfo(tracks: Tracks) {
        val audioTracks = mutableListOf<TrackInfo>()
        val subtitleTracks = mutableListOf<TrackInfo>()
        var audioIndex = 0
        var subtitleIndex = 0
        var selectedAudioTrack = -1
        var selectedSubtitleTrack = -1
        var unsupportedAudioCount = 0
        var unsupportedSubtitleCount = 0

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (trackIndex in 0 until group.length) {
                        if (!group.isTrackSupported(trackIndex)) {
                            unsupportedAudioCount++
                            continue
                        }
                        val format = group.getTrackFormat(trackIndex)
                        val displayLabel = format.preferredDisplayLabel()
                        val role = media3TrackRole(format.roleFlags)
                        val isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0
                        val isHearingImpaired = format.roleFlags and (
                            C.ROLE_FLAG_CAPTION or
                                C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND or
                                C.ROLE_FLAG_TRANSCRIBES_DIALOG
                            ) != 0
                        audioTracks += TrackInfo(
                            index = audioIndex,
                            name = buildTrackTitle(
                                kind = TrackKind.AUDIO,
                                ordinal = audioIndex + 1,
                                label = displayLabel,
                                language = format.language,
                                isDefault = isDefault,
                                isHearingImpaired = isHearingImpaired,
                                role = role
                            ),
                            language = format.language ?: "",
                            isSelected = group.isTrackSelected(trackIndex),
                            codec = format.codecs ?: format.sampleMimeType.orEmpty(),
                            channelCount = format.channelCount.coerceAtLeast(0),
                            role = role,
                            isDefault = isDefault,
                            isHearingImpaired = isHearingImpaired
                        )
                        if (group.isTrackSelected(trackIndex)) {
                            selectedAudioTrack = audioIndex
                        }
                        audioIndex++
                    }
                }

                C.TRACK_TYPE_TEXT -> {
                    for (trackIndex in 0 until group.length) {
                        if (!group.isTrackSupported(trackIndex)) {
                            unsupportedSubtitleCount++
                            continue
                        }
                        val format = group.getTrackFormat(trackIndex)
                        val displayLabel = format.preferredDisplayLabel()
                        val role = media3TrackRole(format.roleFlags)
                        val isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0
                        val isForced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
                        val isHearingImpaired = format.roleFlags and (
                            C.ROLE_FLAG_CAPTION or
                                C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND or
                                C.ROLE_FLAG_TRANSCRIBES_DIALOG
                            ) != 0
                        subtitleTracks += TrackInfo(
                            index = subtitleIndex,
                            name = buildTrackTitle(
                                kind = TrackKind.SUBTITLE,
                                ordinal = subtitleIndex + 1,
                                label = displayLabel,
                                language = format.language,
                                isDefault = isDefault,
                                isForced = isForced,
                                isHearingImpaired = isHearingImpaired,
                                role = role
                            ),
                            language = format.language ?: "",
                            isSelected = group.isTrackSelected(trackIndex),
                            codec = format.codecs ?: format.sampleMimeType.orEmpty(),
                            role = role,
                            isDefault = isDefault,
                            isForced = isForced,
                            isHearingImpaired = isHearingImpaired
                        )
                        if (group.isTrackSelected(trackIndex)) {
                            selectedSubtitleTrack = subtitleIndex
                        }
                        subtitleIndex++
                    }
                }
            }
        }

        _state.value = _state.value.copy(
            audioTracks = disambiguateTrackNames(audioTracks),
            subtitleTracks = disambiguateTrackNames(subtitleTracks),
            selectedAudioTrack = selectedAudioTrack,
            selectedSubtitleTrack = selectedSubtitleTrack
        )
        if (unsupportedAudioCount > 0 || unsupportedSubtitleCount > 0) {
            Timber.w(
                "Media3 omitted unsupported tracks: audio=%d subtitle=%d",
                unsupportedAudioCount,
                unsupportedSubtitleCount
            )
        }
    }

    private fun ensureDefaultAudioTrackSelected(tracks: Tracks) {
        val selector = trackSelector ?: return
        val hasSelectedAudio = tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_AUDIO && (0 until group.length).any(group::isTrackSelected)
        }
        if (hasSelectedAudio) {
            return
        }

        val firstAudioGroup = tracks.groups.firstOrNull { group ->
            group.type == C.TRACK_TYPE_AUDIO &&
                (0 until group.length).any(group::isTrackSupported)
        } ?: return
        val firstSupportedTrack = (0 until firstAudioGroup.length)
            .first(firstAudioGroup::isTrackSupported)

        selector.setParameters(
            selector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setOverrideForType(
                    TrackSelectionOverride(firstAudioGroup.mediaTrackGroup, firstSupportedTrack)
                )
        )
        player?.volume = 1f
    }

    private fun updateQualityInfo(tracks: Tracks) {
        val qualities = mutableListOf<QualityOption>()
        var isAdaptive = false
        var videoIndex = 0
        var currentCodec = ""
        var currentBitrate = ""
        var currentFps = ""

        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) {
                continue
            }

            if (group.length > 1) {
                isAdaptive = true
            }

            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val width = format.width
                val height = format.height
                val bitrate = format.bitrate
                val label = when {
                    height >= 2160 -> "4K (${width}x${height})"
                    height >= 1440 -> "1440p (${width}x${height})"
                    height >= 1080 -> "1080p (${width}x${height})"
                    height >= 720 -> "720p (${width}x${height})"
                    height >= 480 -> "480p (${width}x${height})"
                    height >= 360 -> "360p (${width}x${height})"
                    height > 0 -> "${height}p (${width}x${height})"
                    else -> "Quality ${videoIndex + 1}"
                }

                if (group.isTrackSelected(trackIndex)) {
                    currentCodec = format.codecs ?: format.sampleMimeType ?: ""
                    currentBitrate = if (bitrate > 0) "${bitrate / 1000} kbps" else ""
                    currentFps = formatFrameRate(format.frameRate.toDouble())
                }

                qualities += QualityOption(
                    index = videoIndex,
                    label = label,
                    width = width,
                    height = height,
                    bitrate = bitrate,
                    isSelected = group.isTrackSelected(trackIndex),
                    isAdaptive = group.length > 1
                )
                videoIndex++
            }
        }

        if (isAdaptive) {
            qualities.add(
                0,
                QualityOption(
                    index = -1,
                    label = "Auto",
                    isAdaptive = true,
                    isSelected = _state.value.videoQualityMode == VideoQualityMode.AUTO
                )
            )
        }

        _state.value = _state.value.copy(
            availableQualities = qualities,
            isAdaptiveStream = isAdaptive,
            currentVideoCodec = currentCodec,
            currentVideoBitrate = currentBitrate,
            currentVideoFps = currentFps
        )
    }

    private fun applyPreferredAudioLanguage() {
        val selector = trackSelector ?: return
        val language = preferredAudioLanguage ?: return
        selector.setParameters(
            selector.buildUponParameters()
                .setPreferredAudioLanguage(language)
        )
    }

    private fun applyPreferredSubtitleLanguage() {
        val selector = trackSelector ?: return
        if (subtitlesDisabled) {
            selector.setParameters(
                selector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            )
            return
        }

        val language = preferredSubtitleLanguage ?: return
        selector.setParameters(
            selector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(language)
        )
    }

    private data class IndexedTrackSelection(
        val group: Tracks.Group,
        val trackIndexInGroup: Int
    )

    private fun findTrackSelection(trackType: Int, targetIndex: Int): IndexedTrackSelection? {
        val tracks = player?.currentTracks ?: return null
        var runningIndex = 0

        for (group in tracks.groups) {
            if (group.type != trackType) {
                continue
            }
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) {
                    continue
                }
                if (runningIndex == targetIndex) {
                    return IndexedTrackSelection(group = group, trackIndexInGroup = trackIndex)
                }
                runningIndex++
            }
        }

        return null
    }

    private fun isCurrentMediaItemSeekable(exoPlayer: ExoPlayer): Boolean {
        return exoPlayer.duration > 0L &&
            exoPlayer.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
    }

    private fun androidx.media3.common.Format.preferredDisplayLabel(): String? {
        val deviceLanguage = Locale.getDefault().language
        return label
            ?: labels.firstOrNull { it.language == deviceLanguage }?.value
            ?: labels.firstOrNull()?.value
    }

    private fun media3TrackRole(roleFlags: Int): String = buildList {
        if (roleFlags and C.ROLE_FLAG_DUB != 0) add("Dub")
        if (roleFlags and C.ROLE_FLAG_COMMENTARY != 0) add("Commentary")
        if (roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) add("Audio description")
    }.joinToString()

    private fun normalizeLanguageCode(code: String): String {
        return when (code.lowercase(Locale.getDefault()).trim()) {
            "tr", "tur", "turkish" -> "tur"
            "en", "eng", "english" -> "eng"
            "ar", "ara", "arabic" -> "ara"
            "de", "deu", "ger", "german" -> "deu"
            "fr", "fra", "fre", "french" -> "fra"
            "es", "spa", "spanish" -> "spa"
            "ru", "rus", "russian" -> "rus"
            "it", "ita", "italian" -> "ita"
            "pt", "por", "portuguese" -> "por"
            "ja", "jpn", "japanese" -> "jpn"
            "ko", "kor", "korean" -> "kor"
            "zh", "zho", "chi", "chinese" -> "zho"
            "system" -> Locale.getDefault().isO3Language
            else -> code.lowercase(Locale.getDefault()).take(3)
        }
    }

    private fun resetPlaybackAttemptState() {
        networkThroughputSampler.reset()
        hasVideoTrack = false
        hasAudioTrack = false
        firstFrameRendered = false
        playbackConfirmationBaselinePositionMs = C.TIME_UNSET
        playbackProgressConfirmed = false

        _state.value = PlayerState(
            playbackSpeed = currentPlaybackSpeed,
            aspectRatioMode = _state.value.aspectRatioMode,
            videoQualityMode = preferredVideoQualityMode,
            isSurfaceReady = surfaceReady
        )
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = mainScope.launch {
            while (isActive) {
                val exoPlayer = player
                if (exoPlayer != null && exoPlayer.isPlaying) {
                    publishPlayerState()
                }
                delay(PLAYBACK_PROGRESS_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun buildPlaybackErrorMessage(error: PlaybackException): String {
        val rawMessage = buildString {
            append(error.errorCodeName)
            error.localizedMessage?.takeIf { it.isNotBlank() }?.let {
                append(": ")
                append(it)
            }
            error.cause?.message?.takeIf { it.isNotBlank() }?.let {
                append(" | cause=")
                append(it)
            }
        }

        val normalized = rawMessage.lowercase(Locale.getDefault())
        return when {
            normalized.contains("video/hevc") ||
                normalized.contains("hvc1") ||
                normalized.contains("hev1") ||
                normalized.contains("no_exceeds_capabilities") ||
                normalized.contains("10bit") ||
                normalized.contains("decoder failed: c2.android.hevc.decoder") -> {
                "This stream uses an HEVC/HDR video profile that is not supported on this device."
            }

            else -> rawMessage.ifBlank { "Playback error" }
        }
    }

    private fun updatePlaybackProgressConfirmation(
        exoPlayer: ExoPlayer,
        playbackState: PlaybackState
    ) {
        if (playbackState != PlaybackState.PLAYING) {
            playbackConfirmationBaselinePositionMs = C.TIME_UNSET
            playbackProgressConfirmed = false
            return
        }

        val currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        val baselinePositionMs = playbackConfirmationBaselinePositionMs
        if (baselinePositionMs == C.TIME_UNSET) {
            playbackConfirmationBaselinePositionMs = currentPositionMs
            return
        }

        if (!playbackProgressConfirmed &&
            currentPositionMs >= baselinePositionMs + PLAYBACK_CONFIRMATION_PROGRESS_MS
        ) {
            playbackProgressConfirmed = true
            Timber.d(
                "Playback confirmation progress reached: baseline=%d current=%d video=%s firstFrame=%s",
                baselinePositionMs,
                currentPositionMs,
                hasVideoTrack,
                firstFrameRendered
            )
        }
    }
}

internal fun shouldOpenPlaybackGate(
    hasVideoTrack: Boolean,
    hasAudioTrack: Boolean,
    surfaceReady: Boolean
): Boolean = when {
    hasVideoTrack -> surfaceReady
    hasAudioTrack -> true
    else -> false
}
