package com.idealplayer.app.ui.player

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idealplayer.app.BuildConfig
import com.idealplayer.app.core.common.SensitiveLog
import com.idealplayer.app.core.datastore.AppSettings
import com.idealplayer.app.core.datastore.SettingsDataStore
import com.idealplayer.app.core.model.Channel
import com.idealplayer.app.core.model.ContentType
import com.idealplayer.app.core.model.Episode
import com.idealplayer.app.core.model.WatchHistoryItem
import com.idealplayer.app.core.player.AspectRatioMode
import com.idealplayer.app.core.player.PlaybackState
import com.idealplayer.app.core.player.PlaybackProfile
import com.idealplayer.app.core.player.PlaybackDiagnostics
import com.idealplayer.app.core.player.buildPlaybackDiagnosticsReport
import com.idealplayer.app.core.player.PlayerManager
import com.idealplayer.app.core.player.PlayerState
import com.idealplayer.app.core.player.parsePlaybackSource
import com.idealplayer.app.core.player.withPlaybackHeaders
import com.idealplayer.app.core.player.RetryState
import com.idealplayer.app.core.player.SleepTimerManager
import com.idealplayer.app.core.player.SleepTimerState
import com.idealplayer.app.core.player.StreamRetryManager
import com.idealplayer.app.core.player.VideoQualityMode
import com.idealplayer.app.data.repository.ContentRepository
import com.idealplayer.app.data.repository.EpgRepository
import com.idealplayer.app.data.repository.PlaylistRepository

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

data class PlayerSessionState(
    val title: String = "",
    val movie: com.idealplayer.app.core.model.Movie? = null,
    val series: com.idealplayer.app.core.model.Series? = null,
    val currentEpisode: Episode? = null,
    val previousEpisode: Episode? = null,
    val nextEpisode: Episode? = null,
    val currentChannel: Channel? = null,
    val previousChannel: Channel? = null,
    val nextChannel: Channel? = null,
    val availableChannels: List<Channel> = emptyList(),
    val liveGroups: List<String> = emptyList(),
    val liveGroup: String = ""
)

data class LiveChannelSwitchState(
    val isSwitching: Boolean = false,
    val targetChannelId: Long? = null,
    val targetTitle: String = "",
    val errorMessage: String? = null
)

private data class PlaybackRecoverySnapshot(
    val playbackState: PlaybackState,
    val isPlaybackConfirmed: Boolean,
    val errorMessage: String?
)

private data class AutoPlayNextSnapshot(
    val playbackState: PlaybackState,
    val currentPosition: Long,
    val duration: Long
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerManager: PlayerManager,
    private val settingsDataStore: SettingsDataStore,
    private val contentRepository: ContentRepository,
    private val playlistRepository: PlaylistRepository,
    val retryManager: StreamRetryManager,
    val sleepTimer: SleepTimerManager,
    private val epgRepository: EpgRepository
) : ViewModel() {
    val state: StateFlow<PlayerState> = playerManager.state
    val diagnostics: StateFlow<PlaybackDiagnostics> = playerManager.diagnostics
    val settings: StateFlow<AppSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val retryState: StateFlow<RetryState> = retryManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RetryState())
    val sleepTimerState = sleepTimer.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SleepTimerState())
    val sleepTimerOptions: List<Int> = sleepTimer.availableOptions
    private val _session = MutableStateFlow(PlayerSessionState())
    val session: StateFlow<PlayerSessionState> = _session.asStateFlow()
    private val _liveChannelSwitch = MutableStateFlow(LiveChannelSwitchState())
    val liveChannelSwitch: StateFlow<LiveChannelSwitchState> = _liveChannelSwitch.asStateFlow()
    private val _playerReady = MutableStateFlow(false)
    val playerReady: StateFlow<Boolean> = _playerReady.asStateFlow()
    private val _currentEpgProgram = MutableStateFlow<com.idealplayer.app.data.parser.EpgProgram?>(null)
    val currentEpgProgram: StateFlow<com.idealplayer.app.data.parser.EpgProgram?> = _currentEpgProgram.asStateFlow()
    private val _nextEpgProgram = MutableStateFlow<com.idealplayer.app.data.parser.EpgProgram?>(null)
    val nextEpgProgram: StateFlow<com.idealplayer.app.data.parser.EpgProgram?> = _nextEpgProgram.asStateFlow()
    private val _channelListEpgPrograms =
        MutableStateFlow<Map<Long, com.idealplayer.app.data.parser.EpgProgram>>(emptyMap())
    val channelListEpgPrograms: StateFlow<Map<Long, com.idealplayer.app.data.parser.EpgProgram>> =
        _channelListEpgPrograms.asStateFlow()
    private val _currentChannelEpgPrograms =
        MutableStateFlow<List<com.idealplayer.app.data.parser.EpgProgram>>(emptyList())
    val currentChannelEpgPrograms: StateFlow<List<com.idealplayer.app.data.parser.EpgProgram>> =
        _currentChannelEpgPrograms.asStateFlow()
    private val _channelBrowserChannels = MutableStateFlow<List<Channel>>(emptyList())
    val channelBrowserChannels: StateFlow<List<Channel>> = _channelBrowserChannels.asStateFlow()

    private var currentUrl = ""
    private var currentOriginalUrl = ""
    private var currentTitle = ""
    private var currentContentId = 0L
    private var currentContentType = ""
    private var currentGroupContext = ""
    private var isInitialized = false
    private var enableTvPlaybackWorkarounds = false
    private var currentPlaybackCandidates: List<String> = emptyList()
    private var currentPlaybackCandidateIndex = 0
    private var currentStartPosition = 0L
    private var hasConfirmedCurrentContent = false
    private var livePlaybackJob: Job? = null
    private var contentPlaybackJob: Job? = null
    private var httpRetryJob: Job? = null
    private val playbackOperationMutex = Mutex()
    private var liveNavigationAnchorChannelId: Long? = null
    private var liveNavigationChannels: List<Channel> = emptyList()
    private var liveEpgAutoDiscoveryPlaylistId: Long? = null
    private var autoPlayNextTriggeredContentId: Long? = null
    private var playerReleaseRequested = false

    private var httpRetryCount = 0
    private val maxHttpRetries = 3

    private companion object {
        private const val LIVE_WARMUP_EXTENSION_MS = 2_500L
        private const val LIVE_WARMUP_MIN_PROGRESS_MS = 2_000L
        private const val LIVE_WARMUP_MIN_BUFFER_AHEAD_MS = 500L
        private const val LIVE_PRIMARY_CANDIDATE_TIMEOUT_MS = 6_000L
        private const val LIVE_FALLBACK_CANDIDATE_TIMEOUT_MS = 5_000L
        private const val LIVE_CONFIRMATION_WINDOW_MS = 300L
        private const val AUTO_PLAY_NEXT_MIN_DURATION_MS = 30_000L
        private const val AUTO_PLAY_NEXT_END_TOLERANCE_MS = 2_500L
    }

    init {
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                // Progress is also persisted on pause/stop/lifecycle events. Avoid waking the
                // database every fifteen seconds while the player is idle, buffering, or paused.
                if (state.value.playbackState == PlaybackState.PLAYING) {
                    saveProgressInternal()
                }
            }
        }

        viewModelScope.launch {
            state
                .map { playerState ->
                    PlaybackRecoverySnapshot(
                        playbackState = playerState.playbackState,
                        isPlaybackConfirmed = playerState.isPlaybackConfirmed,
                        errorMessage = playerState.errorMessage
                    )
                }
                .distinctUntilChanged()
                .collect { recovery ->
                    if (recovery.playbackState == PlaybackState.ERROR &&
                        !retryManager.state.value.isRetrying &&
                        !isManagedLivePlaybackRunning()
                    ) {
                        if (tryNextSeriesPlaybackCandidate()) {
                            return@collect
                        }

                        val errorMessage = recovery.errorMessage
                        val isHttpError = errorMessage?.contains("503") == true ||
                            errorMessage?.contains("HTTP_STATUS") == true ||
                            errorMessage?.contains("BAD_HTTP") == true
                        val isNonRetryablePlaybackError = isNonRetryablePlaybackError(errorMessage)
                        
                        if (isHttpError && httpRetryCount < maxHttpRetries) {
                            httpRetryCount++
                            val failedContentId = currentContentId
                            val failedUrl = currentUrl
                            httpRetryJob?.cancel()
                            httpRetryJob = viewModelScope.launch {
                                delay(2000L * httpRetryCount)
                                Timber.d("HTTP error retry $httpRetryCount/$maxHttpRetries")
                                if (
                                    currentContentId == failedContentId &&
                                    currentUrl == failedUrl &&
                                    failedUrl.isNotBlank()
                                ) {
                                    playerManager.play(
                                        url = failedUrl,
                                        startPosition = 0L,
                                        profile = currentPlaybackProfile()
                                    )
                                }
                            }
                            return@collect
                        } else {
                            httpRetryCount = 0
                            handlePlaybackError(errorMessage)

                            val autoRetry = settings.value.liveReconnectOnFailure
                            if (autoRetry &&
                                currentContentType == ContentType.LIVE.name &&
                                !isNonRetryablePlaybackError
                            ) {
                                retryManager.startRetry(
                                    errorMessage = errorMessage,
                                    onRetry = { retryCurrent() },
                                    onExhausted = { Timber.w("Auto-retry exhausted") }
                                )
                            } else if (isNonRetryablePlaybackError &&
                                currentContentType == ContentType.LIVE.name
                            ) {
                                _liveChannelSwitch.value = LiveChannelSwitchState(
                                    errorMessage = errorMessage ?: buildPlaybackFailureMessage(
                                        currentTitle.ifBlank { "This channel" }
                                    )
                                )
                            }
                        }
                    } else if (recovery.isPlaybackConfirmed) {
                        hasConfirmedCurrentContent = true
                        httpRetryCount = 0
                        httpRetryJob?.cancel()
                        httpRetryJob = null
                        retryManager.onPlaybackSuccess()
                    }
                }
        }

        viewModelScope.launch {
            state
                .map { playerState ->
                    AutoPlayNextSnapshot(
                        playbackState = playerState.playbackState,
                        currentPosition = playerState.currentPosition,
                        duration = playerState.duration
                    )
                }
                .distinctUntilChanged()
                .collect(::maybeAutoPlayNextEpisode)
        }
    }

    private fun maybeAutoPlayNextEpisode(snapshot: AutoPlayNextSnapshot) {
        val contentId = currentContentId.takeIf { it > 0L } ?: return
        val nextEpisode = session.value.nextEpisode ?: return
        if (!shouldTriggerAutoPlayNextEpisode(
                isSeriesPlayback = currentContentType.equals(ContentType.SERIES.name, ignoreCase = true),
                autoPlayEnabled = settings.value.autoPlayNextEpisode,
                transitionInProgress = contentPlaybackJob?.isActive == true,
                contentId = contentId,
                alreadyTriggeredContentId = autoPlayNextTriggeredContentId,
                playbackState = snapshot.playbackState,
                currentPosition = snapshot.currentPosition,
                duration = snapshot.duration,
                minimumDuration = AUTO_PLAY_NEXT_MIN_DURATION_MS,
                endTolerance = AUTO_PLAY_NEXT_END_TOLERANCE_MS
            )
        ) {
            return
        }

        autoPlayNextTriggeredContentId = contentId
        Timber.d(
            "Auto-playing next episode current=%d next=%d state=%s position=%d duration=%d",
            contentId,
            nextEpisode.id,
            snapshot.playbackState,
            snapshot.currentPosition,
            snapshot.duration
        )
        playEpisode(nextEpisode)
    }

    private fun handlePlaybackError(errorMessage: String?) {
        // Engine error text can embed a stream URL or request headers; the state remains
        // available to the UI, but debug logging must not expose it.
        Timber.w("Playback error handled")
        // Engine selection is explicit; playback recovery is handled by the
        // currently active engine plus retryManager when enabled.
    }

    private fun isNonRetryablePlaybackError(errorMessage: String?): Boolean {
        val normalized = errorMessage?.lowercase() ?: return false
        return normalized.contains("not supported on this device") ||
            normalized.contains("no_exceeds_capabilities") ||
            normalized.contains("video/hevc") ||
            normalized.contains("hvc1") ||
            normalized.contains("hev1") ||
            normalized.contains("10bit")
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimer.start(minutes) {
            viewModelScope.launch {
                playerManager.pause()
            }
        }
    }

    fun cancelSleepTimer() = sleepTimer.cancel()

    fun extendSleepTimer(minutes: Int) = sleepTimer.extend(minutes)

    fun loadEpg(epgChannelId: String) {
        if (epgChannelId.isBlank()) return
        viewModelScope.launch {
            _currentEpgProgram.value = epgRepository.getCurrentProgram(epgChannelId)
            _nextEpgProgram.value = epgRepository.getNextProgram(epgChannelId)
        }
    }

    fun loadEpgForChannel(channel: Channel?) {
        if (channel == null) {
            _currentEpgProgram.value = null
            _nextEpgProgram.value = null
            _currentChannelEpgPrograms.value = emptyList()
            return
        }

        viewModelScope.launch {
            _currentEpgProgram.value = epgRepository.getCurrentProgram(channel)
            _nextEpgProgram.value = epgRepository.getNextProgram(channel)
        }
    }

    fun loadEpgProgramsForChannel(channel: Channel?) {
        if (channel == null) {
            _currentChannelEpgPrograms.value = emptyList()
            return
        }

        viewModelScope.launch {
            _currentChannelEpgPrograms.value = epgRepository.getProgramsForChannel(channel)
        }
    }

    fun loadEpgForChannels(channels: List<Channel>) {
        if (channels.isEmpty()) {
            _channelListEpgPrograms.value = emptyMap()
            return
        }

        viewModelScope.launch {
            _channelListEpgPrograms.value = epgRepository.getCurrentProgramsForChannels(channels)
        }
    }

    fun loadChannelBrowser(group: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlist = playlistRepository.getActivePlaylist().first()
            if (playlist == null) {
                _channelBrowserChannels.value = emptyList()
                _channelListEpgPrograms.value = emptyMap()
                return@launch
            }

            val normalizedGroup = group?.takeIf { it.isNotBlank() }
            val channels = if (normalizedGroup == null) {
                contentRepository.getChannels(playlist.id).first()
            } else {
                contentRepository.getChannelsByGroup(playlist.id, normalizedGroup).first()
            }

            _channelBrowserChannels.value = channels
            _channelListEpgPrograms.value = if (channels.isEmpty()) {
                emptyMap()
            } else {
                epgRepository.getCurrentProgramsForChannels(channels)
            }
        }
    }

    private fun ensureLiveEpgSynced() {
        viewModelScope.launch {
            val playlist = playlistRepository.getActivePlaylist().first() ?: return@launch
            if (liveEpgAutoDiscoveryPlaylistId == playlist.id) return@launch

            liveEpgAutoDiscoveryPlaylistId = playlist.id
            val synced = playlistRepository.ensureEpgSynced(playlist)
            if (synced) {
                loadEpgForChannel(session.value.currentChannel)
                loadEpgForChannels(session.value.availableChannels)
            }
        }
    }

    fun init(
        url: String,
        title: String,
        startPos: Long,
        contentId: Long,
        contentType: String,
        groupContext: String
    ) {
        if (
            currentOriginalUrl == url &&
            currentTitle == title &&
            currentContentId == contentId &&
            currentContentType == contentType &&
            currentGroupContext == groupContext &&
            state.value.playbackState != PlaybackState.IDLE
        ) {
            return
        }

        val playbackCandidates = resolvePlaybackCandidates(url, contentType)
        val resolvedUrl = playbackCandidates.firstOrNull().orEmpty()

        currentOriginalUrl = url
        currentUrl = resolvedUrl
        currentPlaybackCandidates = playbackCandidates
        currentPlaybackCandidateIndex = 0
        currentStartPosition = 0L
        hasConfirmedCurrentContent = false
        currentTitle = title
        currentContentId = contentId
        currentContentType = contentType
        currentGroupContext = groupContext
        autoPlayNextTriggeredContentId = null

        val isLiveContent = contentType.equals(ContentType.LIVE.name, ignoreCase = true)
        liveNavigationAnchorChannelId = contentId.takeIf { isLiveContent && it > 0L }
        retryManager.cancel()
        httpRetryJob?.cancel()
        httpRetryJob = null

        val initializeAndPlay: suspend () -> Unit = {
            // A previous live switch may finish its cancellation cleanup before this block
            // acquires the operation mutex. Re-apply the latest route request afterwards so
            // stale rollback state cannot become the active content identity.
            currentOriginalUrl = url
            currentUrl = resolvedUrl
            currentPlaybackCandidates = playbackCandidates
            currentPlaybackCandidateIndex = 0
            currentStartPosition = 0L
            hasConfirmedCurrentContent = false
            currentTitle = title
            currentContentId = contentId
            currentContentType = contentType
            currentGroupContext = groupContext
            autoPlayNextTriggeredContentId = null
            liveNavigationAnchorChannelId = contentId.takeIf { isLiveContent && it > 0L }

            val playbackSettings = settingsDataStore.settings.first()
            val effectiveStartPosition = playbackStartPosition(
                requestedPosition = startPos,
                autoResumeEnabled = playbackSettings.autoResumePlayback
            )
            currentStartPosition = effectiveStartPosition
            clearLiveChannelSwitchError()
            if (!isInitialized) {
                _playerReady.value = false
                playerManager.initializeWithSettings()
                isInitialized = true
                _playerReady.value = true
            }
            refreshSessionContext(url, title, contentId, contentType, groupContext)

            val playbackStarted = if (isLiveContent) {
                startLiveChannelPlayback(playbackCandidates, effectiveStartPosition)
            } else {
                playerManager.play(
                    url = resolvedUrl,
                    startPosition = effectiveStartPosition,
                    profile = playbackProfileFor(contentType)
                )
                true
            }

            if (
                playbackStarted &&
                isLiveContent &&
                isCurrentPlaybackRequest(contentId, contentType, url) &&
                contentId > 0L
            ) {
                if (playbackSettings.rememberLastChannel) {
                    contentRepository.updateChannelLastWatched(contentId)
                }
                ensureLiveEpgSynced()
            } else if (!playbackStarted && isCurrentPlaybackRequest(contentId, contentType, url)) {
                publishLivePlaybackFailure(
                    channelId = contentId,
                    title = title,
                    errorMessage = state.value.errorMessage,
                    allowAutoRetry = playbackSettings.liveReconnectOnFailure
                )
            }
        }

        if (isLiveContent) {
            contentPlaybackJob?.cancel()
            contentPlaybackJob = null
            launchLatestLivePlayback(initializeAndPlay)
        } else {
            livePlaybackJob?.cancel()
            contentPlaybackJob?.cancel()
            contentPlaybackJob = viewModelScope.launch {
                playbackOperationMutex.withLock { initializeAndPlay() }
            }
        }
    }

    fun configureTvPlayback(enabled: Boolean) {
        enableTvPlaybackWorkarounds = enabled
    }

    fun clearLiveChannelSwitchError() {
        if (_liveChannelSwitch.value.errorMessage != null && !_liveChannelSwitch.value.isSwitching) {
            _liveChannelSwitch.value = LiveChannelSwitchState()
        }
    }

    fun togglePlayPause() {
        val snapshot = state.value
        viewModelScope.launch {
            if (snapshot.isPlaying) {
                playerManager.pause()
            } else {
                playerManager.resume()
            }
        }
    }

    fun seekForward() {
        viewModelScope.launch {
            playerManager.seekForward(settings.value.seekForwardMs)
        }
    }

    fun seekBackward() {
        viewModelScope.launch {
            playerManager.seekBackward(settings.value.seekBackwardMs)
        }
    }

    fun seekTo(pos: Long) {
        viewModelScope.launch {
            playerManager.seekTo(pos)
        }
    }

    fun setSpeed(speed: Float) {
        viewModelScope.launch {
            playerManager.setPlaybackSpeed(speed)
        }
    }

    fun selectAudio(index: Int) {
        viewModelScope.launch {
            playerManager.selectAudioTrack(index)
        }
    }

    fun selectSubtitle(index: Int) {
        viewModelScope.launch {
            playerManager.selectSubtitleTrack(index)
        }
    }

    fun disableSubtitles() {
        viewModelScope.launch {
            playerManager.disableSubtitles()
        }
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        viewModelScope.launch {
            playerManager.setAspectRatio(mode)
        }
    }

    fun setVideoQualityMode(mode: VideoQualityMode) {
        viewModelScope.launch {
            playerManager.setVideoQualityMode(mode)
        }
    }

    fun selectVideoTrack(index: Int) {
        viewModelScope.launch {
            playerManager.selectVideoTrack(index)
        }
    }

    fun buildDiagnosticsReport(): String = buildPlaybackDiagnosticsReport(
        appVersion = BuildConfig.VERSION_NAME,
        device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        androidSdk = Build.VERSION.SDK_INT,
        contentType = currentContentType,
        diagnostics = diagnostics.value,
        state = state.value
    )

    fun saveProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            saveProgressInternal()
        }
    }

    fun retryCurrent() {
        val failedChannel = if (currentContentType == ContentType.LIVE.name) {
            val failedTargetId = _liveChannelSwitch.value.targetChannelId
            liveNavigationChannels.firstOrNull { it.id == failedTargetId }
                ?: session.value.availableChannels.firstOrNull { it.id == failedTargetId }
        } else {
            null
        }

        if (failedChannel != null) {
            requestLiveChannel(failedChannel, cancelAutomaticRetry = false)
            return
        }

        if (currentContentType == ContentType.LIVE.name) {
            launchLatestLivePlayback {
                clearLiveChannelSwitchError()
                val candidates = currentPlaybackCandidates.ifEmpty {
                    resolvePlaybackCandidates(currentOriginalUrl.ifBlank { currentUrl }, currentContentType)
                }
                val started = startLiveChannelPlayback(candidates, startPosition = 0L)
                if (!started) {
                    publishLivePlaybackFailure(
                        channelId = currentContentId,
                        title = currentTitle,
                        errorMessage = state.value.errorMessage,
                        allowAutoRetry = false
                    )
                }
            }
        } else {
            contentPlaybackJob?.cancel()
            contentPlaybackJob = viewModelScope.launch {
                playbackOperationMutex.withLock {
                    playerManager.play(
                        url = currentUrl,
                        startPosition = state.value.currentPosition,
                        profile = currentPlaybackProfile()
                    )
                }
            }
        }
    }

    fun playNextEpisode() {
        session.value.nextEpisode?.let(::playEpisode)
    }

    fun playPreviousEpisode() {
        session.value.previousEpisode?.let(::playEpisode)
    }

    fun playNextChannel() {
        adjacentLiveChannel(offset = 1)?.let(::playChannel)
    }

    fun playPreviousChannel() {
        adjacentLiveChannel(offset = -1)?.let(::playChannel)
    }

    fun playChannel(channel: Channel) {
        requestLiveChannel(channel, cancelAutomaticRetry = true)
    }

    private fun requestLiveChannel(channel: Channel, cancelAutomaticRetry: Boolean) {
        val switchState = _liveChannelSwitch.value
        if (
            currentContentType == ContentType.LIVE.name &&
            !switchState.isSwitching &&
            switchState.errorMessage == null &&
            currentContentId == channel.id &&
            session.value.currentChannel?.id == channel.id
        ) {
            Timber.d("LivePlayback playChannel ignored; already on channel=%s", channel.name)
            return
        }

        if (cancelAutomaticRetry) {
            retryManager.cancel()
        }
        httpRetryJob?.cancel()
        httpRetryJob = null
        liveNavigationAnchorChannelId = channel.id
        val navigationChannels = liveNavigationChannelsFor(channel)
        applyLiveNavigationContext(channel, navigationChannels)
        clearLiveChannelSwitchError()
        Timber.d("LivePlayback scheduling latest channel switch to %s", channel.name)
        launchLatestLivePlayback {
            applyLiveNavigationContext(channel, navigationChannels)
            switchLiveChannel(channel)
        }
    }

    fun exitPlayer(onBack: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            saveProgressInternal()
        }
        livePlaybackJob?.cancel()
        contentPlaybackJob?.cancel()
        httpRetryJob?.cancel()
        retryManager.cancel()
        releasePlayerOnce()
        onBack()
    }

    override fun onCleared() {
        super.onCleared()
        livePlaybackJob?.cancel()
        contentPlaybackJob?.cancel()
        httpRetryJob?.cancel()
        saveProgress()
        retryManager.cancel()
        releasePlayerOnce()
    }

    private fun releasePlayerOnce() {
        if (playerReleaseRequested) return
        playerReleaseRequested = true
        playerManager.release()
    }

    private fun playEpisode(episode: Episode) {
        livePlaybackJob?.cancel()
        contentPlaybackJob?.cancel()
        contentPlaybackJob = viewModelScope.launch {
            playbackOperationMutex.withLock {
                val series = session.value.series ?: contentRepository.getSeriesById(episode.seriesId)
                switchToContent(
                    url = episode.streamUrl,
                    title = buildEpisodePlayerTitle(series?.name.orEmpty(), episode),
                    contentId = episode.id,
                    contentType = ContentType.SERIES.name,
                    startPosition = episode.lastPosition,
                    groupContext = ""
                )
            }
        }
    }

    private suspend fun switchToContent(
        url: String,
        title: String,
        contentId: Long,
        contentType: String,
        startPosition: Long,
        groupContext: String
    ) {
        withContext(Dispatchers.IO) {
            saveProgressInternal()
        }

        val playbackSettings = settingsDataStore.settings.first()
        val effectiveStartPosition = playbackStartPosition(
            requestedPosition = startPosition,
            autoResumeEnabled = playbackSettings.autoResumePlayback
        )
        val playbackCandidates = resolvePlaybackCandidates(url, contentType)
        val resolvedUrl = playbackCandidates.firstOrNull().orEmpty()
        currentOriginalUrl = url
        currentUrl = resolvedUrl
        currentPlaybackCandidates = playbackCandidates
        currentPlaybackCandidateIndex = 0
        currentStartPosition = effectiveStartPosition
        hasConfirmedCurrentContent = false
        currentTitle = title
        currentContentId = contentId
        currentContentType = contentType
        currentGroupContext = groupContext
        liveNavigationAnchorChannelId = contentId.takeIf {
            contentType.equals(ContentType.LIVE.name, ignoreCase = true) && it > 0L
        }
        retryManager.cancel()
        httpRetryJob?.cancel()
        httpRetryJob = null

        refreshSessionContext(resolvedUrl, title, contentId, contentType, groupContext)
        playerManager.play(
            url = resolvedUrl,
            startPosition = effectiveStartPosition,
            profile = playbackProfileFor(contentType)
        )
        autoPlayNextTriggeredContentId = null

        if (
            contentType == ContentType.LIVE.name &&
            contentId > 0L &&
            playbackSettings.rememberLastChannel
        ) {
            contentRepository.updateChannelLastWatched(contentId)
        }
    }

    private suspend fun switchLiveChannel(channel: Channel) {
        val previousUrl = currentUrl
        val previousOriginalUrl = currentOriginalUrl
        val previousTitle = currentTitle
        val previousContentId = currentContentId
        val previousContentType = currentContentType
        val previousGroupContext = currentGroupContext
        val previousPlaybackCandidates = currentPlaybackCandidates
        val previousPlaybackCandidateIndex = currentPlaybackCandidateIndex
        val previousStartPosition = currentStartPosition
        val previousPlaybackWasConfirmed = hasConfirmedCurrentContent || state.value.isPlaybackConfirmed
        val previousSession = session.value
        val targetGroupContext = channel.groupTitle.ifBlank { currentGroupContext }
        val playbackCandidates = resolvePlaybackCandidates(channel.streamUrl, ContentType.LIVE.name)

        _liveChannelSwitch.value = LiveChannelSwitchState(
            isSwitching = true,
            targetChannelId = channel.id,
            targetTitle = channel.name
        )

        var switchErrorMessage: String? = null

        try {
            currentOriginalUrl = channel.streamUrl
            currentUrl = playbackCandidates.firstOrNull().orEmpty()
            currentPlaybackCandidates = playbackCandidates
            currentPlaybackCandidateIndex = 0
            currentStartPosition = 0L
            hasConfirmedCurrentContent = false
            currentTitle = channel.name
            currentContentId = channel.id
            currentContentType = ContentType.LIVE.name
            currentGroupContext = targetGroupContext

            val started = startLiveChannelPlayback(playbackCandidates)
            if (!started) {
                throw IllegalStateException("Live channel switch did not reach a stable playback state")
            }

            refreshSessionContext(
                url = channel.streamUrl,
                title = channel.name,
                contentId = channel.id,
                contentType = ContentType.LIVE.name,
                groupContext = targetGroupContext
            )
            liveNavigationAnchorChannelId = channel.id
            if (settingsDataStore.settings.first().rememberLastChannel) {
                contentRepository.updateChannelLastWatched(channel.id)
            }
        } catch (cancelled: CancellationException) {
            currentOriginalUrl = previousOriginalUrl
            currentUrl = previousUrl
            currentTitle = previousTitle
            currentContentId = previousContentId
            currentContentType = previousContentType
            currentGroupContext = previousGroupContext
            currentPlaybackCandidates = previousPlaybackCandidates
            currentPlaybackCandidateIndex = previousPlaybackCandidateIndex
            currentStartPosition = previousStartPosition
            hasConfirmedCurrentContent = previousPlaybackWasConfirmed
            _session.value = previousSession
            throw cancelled
        } catch (_: Exception) {
            currentOriginalUrl = previousOriginalUrl
            currentUrl = previousUrl
            currentTitle = previousTitle
            currentContentId = previousContentId
            currentContentType = previousContentType
            currentGroupContext = previousGroupContext
            currentPlaybackCandidates = previousPlaybackCandidates
            currentPlaybackCandidateIndex = previousPlaybackCandidateIndex
            currentStartPosition = previousStartPosition
            hasConfirmedCurrentContent = previousPlaybackWasConfirmed
            _session.value = previousSession
            switchErrorMessage = state.value.errorMessage ?: buildPlaybackFailureMessage(channel.name)

            if (previousPlaybackWasConfirmed && previousUrl.isNotBlank()) {
                playerManager.play(
                    url = previousUrl,
                    startPosition = 0L,
                    profile = PlaybackProfile.LIVE
                )
            }
        } finally {
            _liveChannelSwitch.value = if (switchErrorMessage.isNullOrBlank()) {
                LiveChannelSwitchState()
            } else {
                LiveChannelSwitchState(
                    targetChannelId = channel.id,
                    targetTitle = channel.name,
                    errorMessage = switchErrorMessage
                )
            }
        }
    }

    private fun launchLatestLivePlayback(block: suspend () -> Unit) {
        livePlaybackJob?.cancel()
        val nextJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            playbackOperationMutex.withLock { block() }
        }
        livePlaybackJob = nextJob
        nextJob.start()
    }

    private fun isManagedLivePlaybackRunning(): Boolean {
        return currentContentType.equals(ContentType.LIVE.name, ignoreCase = true) &&
            livePlaybackJob?.isActive == true
    }

    private fun isCurrentPlaybackRequest(
        contentId: Long,
        contentType: String,
        originalUrl: String
    ): Boolean {
        return currentContentId == contentId &&
            currentContentType.equals(contentType, ignoreCase = true) &&
            currentOriginalUrl == originalUrl
    }

    private fun adjacentLiveChannel(offset: Int): Channel? {
        val currentChannelId = session.value.currentChannel?.id
            ?: currentContentId.takeIf { currentContentType == ContentType.LIVE.name }
        val anchorChannelId = liveNavigationAnchor(
            currentChannelId = currentChannelId,
            switchingTargetChannelId = _liveChannelSwitch.value.targetChannelId,
            latestRequestedChannelId = liveNavigationAnchorChannelId
        )
        val channels = liveNavigationChannels.takeIf { navigationChannels ->
            navigationChannels.any { it.id == anchorChannelId }
        } ?: session.value.availableChannels
        return adjacentLiveChannel(
            channels = channels,
            anchorChannelId = anchorChannelId,
            offset = offset
        )
    }

    private fun liveNavigationChannelsFor(channel: Channel): List<Channel> {
        return liveNavigationChannelsForTarget(
            targetChannel = channel,
            browserChannels = _channelBrowserChannels.value,
            retainedChannels = liveNavigationChannels,
            sessionChannels = session.value.availableChannels
        )
    }

    private fun applyLiveNavigationContext(channel: Channel, channels: List<Channel>) {
        val targetIndex = channels.indexOfFirst { it.id == channel.id }
        if (targetIndex < 0) return

        liveNavigationChannels = channels
        _session.value = _session.value.copy(
            previousChannel = channels.getOrNull(targetIndex - 1),
            nextChannel = channels.getOrNull(targetIndex + 1),
            availableChannels = channels,
            liveGroup = channel.groupTitle.ifBlank { _session.value.liveGroup }
        )
    }

    private fun publishLivePlaybackFailure(
        channelId: Long,
        title: String,
        errorMessage: String?,
        allowAutoRetry: Boolean
    ) {
        val resolvedTitle = title.ifBlank { "This channel" }
        val resolvedError = errorMessage ?: buildPlaybackFailureMessage(resolvedTitle)
        liveNavigationAnchorChannelId = channelId.takeIf { it > 0L }
        _liveChannelSwitch.value = LiveChannelSwitchState(
            targetChannelId = channelId.takeIf { it > 0L },
            targetTitle = title,
            errorMessage = resolvedError
        )

        if (allowAutoRetry && !isNonRetryablePlaybackError(resolvedError)) {
            retryManager.startRetry(
                errorMessage = resolvedError,
                onRetry = { retryCurrent() },
                onExhausted = { Timber.w("Auto-retry exhausted") }
            )
        }
    }

    private suspend fun startLiveChannelPlayback(
        candidates: List<String>,
        startPosition: Long = 0L
    ): Boolean {
        val uniqueCandidates = candidates
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

        uniqueCandidates.forEachIndexed { candidateIndex, candidate ->
            Timber.d(
                "LivePlayback: trying candidate[%d] url=%s",
                candidateIndex,
                SensitiveLog.redactUrl(candidate)
            )
            resetPlaybackAttempt()
            playerManager.play(
                url = candidate,
                startPosition = startPosition,
                profile = PlaybackProfile.LIVE
            )

            val playbackConfirmed = awaitConfirmedPlaybackReady(
                timeoutMs = if (candidateIndex == 0) {
                    LIVE_PRIMARY_CANDIDATE_TIMEOUT_MS
                } else {
                    LIVE_FALLBACK_CANDIDATE_TIMEOUT_MS
                },
                confirmationWindowMs = LIVE_CONFIRMATION_WINDOW_MS
            )

            if (playbackConfirmed) {
                currentUrl = candidate
                clearLiveChannelSwitchError()
                return true
            }

            Timber.w("LivePlayback candidate failed: %s", SensitiveLog.redactUrl(candidate))
            if (isUnavailableHttpStatus(state.value.errorMessage)) {
                Timber.w("LivePlayback HTTP unavailable, moving to the next candidate")
            } else if (!shouldRetryCurrentLiveCandidate(candidate, state.value)) {
                Timber.w(
                    "LivePlayback non-rendering candidate rejected: %s",
                    SensitiveLog.redactUrl(candidate)
                )
            }
        }

        resetPlaybackAttempt()
        return false
    }

    private suspend fun refreshSessionContext(
        url: String,
        title: String,
        contentId: Long,
        contentType: String,
        groupContext: String
    ) {
        val updatedSession = withContext(Dispatchers.IO) {
            when (contentType.uppercase()) {
                ContentType.MOVIE.name -> {
                    val movie = contentRepository.getMovie(contentId)
                    PlayerSessionState(
                        title = movie?.name ?: title,
                        movie = movie
                    )
                }

                ContentType.SERIES.name -> {
                    val episode = contentRepository.getEpisode(contentId)
                    val series = episode?.let { contentRepository.getSeriesById(it.seriesId) }
                    val episodes = when {
                        episode == null -> emptyList()
                        else -> {
                            val localEpisodes = contentRepository.getAllEpisodes(episode.seriesId)
                            if (localEpisodes.isNotEmpty()) {
                                localEpisodes
                            } else if (series != null) {
                                contentRepository.syncSeriesEpisodes(series)
                                contentRepository.getAllEpisodes(series.id)
                            } else {
                                emptyList()
                            }
                        }
                    }
                    val currentIndex = episodes.indexOfFirst { it.id == episode?.id }

                    PlayerSessionState(
                        title = if (episode != null) {
                            buildEpisodePlayerTitle(series?.name.orEmpty(), episode)
                        } else {
                            title
                        },
                        series = series,
                        currentEpisode = episode,
                        previousEpisode = episodes.getOrNull(currentIndex - 1),
                        nextEpisode = episodes.getOrNull(currentIndex + 1)
                    )
                }

                ContentType.LIVE.name -> {
                    val currentChannel = contentRepository.getChannel(contentId)
                    val playlist = playlistRepository.getActivePlaylist().first()
                    val liveGroups = playlist?.let {
                        contentRepository.getChannelGroups(it.id).first()
                    } ?: emptyList()
                    val scopedChannels = if (playlist != null && groupContext.isNotBlank()) {
                        contentRepository.getChannelsByGroup(playlist.id, groupContext).first()
                    } else {
                        emptyList()
                    }
                    val primaryChannels = when {
                        scopedChannels.isNotEmpty() -> scopedChannels
                        playlist != null -> contentRepository.getChannels(playlist.id).first()
                        else -> emptyList()
                    }
                    val currentIndex = primaryChannels.indexOfFirst { channel ->
                        channel.id == contentId || channel.streamUrl == url
                    }.takeIf { it >= 0 }

                    PlayerSessionState(
                        title = currentChannel?.name ?: title,
                        currentChannel = currentChannel,
                        previousChannel = currentIndex?.let { primaryChannels.getOrNull(it - 1) },
                        nextChannel = currentIndex?.let { primaryChannels.getOrNull(it + 1) },
                        availableChannels = primaryChannels,
                        liveGroups = liveGroups,
                        liveGroup = groupContext
                    )
                }

                else -> PlayerSessionState(title = title)
            }
        }

        _session.value = updatedSession
        if (contentType.equals(ContentType.LIVE.name, ignoreCase = true)) {
            liveNavigationChannels = updatedSession.availableChannels
        }
    }

    private suspend fun saveProgressInternal() {
        val snapshot = state.value
        if (currentContentId <= 0L) return
        val playbackSettings = settingsDataStore.settings.first()

        when (currentContentType.uppercase()) {
            ContentType.MOVIE.name -> saveMovieProgress(snapshot, playbackSettings.continueWatching)
            ContentType.SERIES.name -> saveSeriesProgress(snapshot, playbackSettings.continueWatching)
            ContentType.LIVE.name -> if (playbackSettings.rememberLastChannel) {
                contentRepository.updateChannelLastWatched(currentContentId)
            }
        }
    }

    private suspend fun saveMovieProgress(snapshot: PlayerState, addToContinueWatching: Boolean) {
        if (snapshot.currentPosition <= 0L) return

        val movie = session.value.movie ?: contentRepository.getMovie(currentContentId) ?: return
        val totalDuration = snapshot.duration.takeIf { it > 0L } ?: movie.totalDuration
        val progress = calculateProgress(snapshot.currentPosition, totalDuration)

        contentRepository.updateMovieProgress(movie.id, snapshot.currentPosition, totalDuration)
        if (addToContinueWatching) contentRepository.addWatchHistory(
            WatchHistoryItem(
                contentId = movie.id,
                contentType = ContentType.MOVIE,
                title = movie.name,
                posterUrl = movie.posterUrl,
                streamUrl = movie.streamUrl,
                position = snapshot.currentPosition,
                totalDuration = totalDuration,
                progress = progress
            )
        )
    }

    private suspend fun saveSeriesProgress(snapshot: PlayerState, addToContinueWatching: Boolean) {
        if (snapshot.currentPosition <= 0L) return

        val currentEpisode = session.value.currentEpisode ?: contentRepository.getEpisode(currentContentId) ?: return
        val currentSeries = session.value.series ?: contentRepository.getSeriesById(currentEpisode.seriesId)
        val totalDuration = snapshot.duration.takeIf { it > 0L } ?: currentEpisode.totalDuration
        val progress = calculateProgress(snapshot.currentPosition, totalDuration)

        contentRepository.updateEpisodeProgress(currentEpisode.id, snapshot.currentPosition, totalDuration)
        contentRepository.updateSeriesLastWatchedEpisode(currentEpisode.seriesId, currentEpisode.id)
        if (addToContinueWatching) contentRepository.addWatchHistory(
            WatchHistoryItem(
                contentId = currentEpisode.id,
                contentType = ContentType.SERIES,
                title = currentEpisode.name.ifBlank { "Episode ${currentEpisode.episodeNumber}" },
                posterUrl = currentEpisode.posterUrl.ifBlank { currentSeries?.posterUrl.orEmpty() },
                streamUrl = currentEpisode.streamUrl,
                position = snapshot.currentPosition,
                totalDuration = totalDuration,
                progress = progress,
                seasonNumber = currentEpisode.seasonNumber,
                episodeNumber = currentEpisode.episodeNumber,
                seriesName = currentSeries?.name.orEmpty()
            )
        )
    }

    private fun calculateProgress(position: Long, totalDuration: Long): Float {
        return if (position > 0L && totalDuration > 0L) {
            (position.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    private fun resolvePlaybackUrl(url: String): String {
        val source = parsePlaybackSource(url)
        return withPlaybackHeaders(source.url, source.headers)
    }

    private fun resolvePlaybackCandidates(url: String, contentType: String): List<String> {
        val source = parsePlaybackSource(url)
        val trimmedUrl = source.url
        val defaultResolvedUrl = parsePlaybackSource(resolvePlaybackUrl(url)).url

        if (contentType.equals(ContentType.SERIES.name, ignoreCase = true)) {
            return seriesPlaybackCandidates(withPlaybackHeaders(defaultResolvedUrl, source.headers))
        }

        if (!contentType.equals(ContentType.LIVE.name, ignoreCase = true)) {
            return listOf(withPlaybackHeaders(defaultResolvedUrl, source.headers))
        }

        return buildList {
            add(defaultResolvedUrl)
            // Respect the provider's advertised/original format first. Converting every Xtream
            // HLS URL to transport stream up front delayed otherwise healthy Media3 playback.
            if (trimmedUrl != defaultResolvedUrl) add(trimmedUrl)
            if (trimmedUrl.contains("/live/") && trimmedUrl.endsWith(".m3u8", ignoreCase = true)) {
                add(trimmedUrl.removeSuffix(".m3u8") + ".ts")
            }
            if (defaultResolvedUrl.contains("/live/") && defaultResolvedUrl.endsWith(".ts", ignoreCase = true)) {
                add(defaultResolvedUrl.removeSuffix(".ts") + ".m3u8")
            }
            if (trimmedUrl.contains("/live/") && trimmedUrl.endsWith(".ts", ignoreCase = true)) {
                add(trimmedUrl.removeSuffix(".ts") + ".m3u8")
            }
            if (enableTvPlaybackWorkarounds && trimmedUrl != defaultResolvedUrl) {
                add(defaultResolvedUrl)
            }
        }.map { candidate -> withPlaybackHeaders(candidate.trim(), source.headers) }
            .filter(String::isNotBlank)
            .distinct()
    }

    private fun tryNextSeriesPlaybackCandidate(): Boolean {
        if (hasConfirmedCurrentContent ||
            !currentContentType.equals(ContentType.SERIES.name, ignoreCase = true)
        ) {
            return false
        }

        val nextIndex = currentPlaybackCandidateIndex + 1
        val nextCandidate = currentPlaybackCandidates.getOrNull(nextIndex) ?: return false
        currentPlaybackCandidateIndex = nextIndex
        currentUrl = nextCandidate
        httpRetryCount = 0

        Timber.w(
            "Series playback candidate failed; trying fallback %d/%d: %s",
            nextIndex + 1,
            currentPlaybackCandidates.size,
            SensitiveLog.redactUrl(nextCandidate)
        )
        viewModelScope.launch {
            resetPlaybackAttempt()
            playerManager.play(
                url = nextCandidate,
                startPosition = currentStartPosition,
                profile = PlaybackProfile.VOD
            )
        }
        return true
    }

    private fun buildPlaybackFailureMessage(title: String): String {
        return "$title could not reach confirmed playback."
    }

    private suspend fun awaitConfirmedPlaybackReady(
        timeoutMs: Long,
        confirmationWindowMs: Long = 900L
    ): Boolean {
        var remainingTimeoutMs = timeoutMs
        var warmupExtensionApplied = false
        var reachedCandidateState: PlayerState? = null

        while (reachedCandidateState == null) {
            val candidateState = withTimeoutOrNull(remainingTimeoutMs) {
                state.first { snapshot ->
                    snapshot.playbackState == PlaybackState.ERROR || snapshot.isPlaybackConfirmed
                }
            }

            if (candidateState != null) {
                reachedCandidateState = candidateState
                continue
            }

            val snapshot = state.value

            if (!warmupExtensionApplied && shouldExtendLiveWarmup(snapshot)) {
                warmupExtensionApplied = true
                remainingTimeoutMs = LIVE_WARMUP_EXTENSION_MS
                Timber.w(
                    "LivePlayback confirmation warmup extended: state=%s video=%sx%s firstFrame=%s surfaceReady=%s audioSession=%d position=%d buffered=%d",
                    snapshot.playbackState,
                    snapshot.videoWidth,
                    snapshot.videoHeight,
                    snapshot.hasRenderedFirstFrame,
                    snapshot.isSurfaceReady,
                    snapshot.audioSessionId,
                    snapshot.currentPosition,
                    snapshot.bufferedPosition
                )
                continue
            }

            Timber.w(
                "LivePlayback confirmation timed out: state=%s playing=%s confirmed=%s video=%sx%s firstFrame=%s surfaceReady=%s audioSession=%d position=%d buffered=%d",
                snapshot.playbackState,
                snapshot.isPlaying,
                snapshot.isPlaybackConfirmed,
                snapshot.videoWidth,
                snapshot.videoHeight,
                snapshot.hasRenderedFirstFrame,
                snapshot.isSurfaceReady,
                snapshot.audioSessionId,
                snapshot.currentPosition,
                snapshot.bufferedPosition
            )
            return false
        }

        val confirmedState = reachedCandidateState ?: return false
        if (confirmedState.playbackState == PlaybackState.ERROR) {
            Timber.w("LivePlayback failed with player error before confirmation")
            return false
        }

        delay(confirmationWindowMs)
        val confirmationSnapshot = state.value
        val stablePlayback = isStableLivePlaybackAfterConfirmation(
            confirmationSnapshot = confirmationSnapshot,
            initiallyConfirmedSnapshot = confirmedState
        )
        if (!stablePlayback) {
            Timber.w(
                "LivePlayback confirmation window expired without stable playback: state=%s confirmed=%s video=%sx%s firstFrame=%s surfaceReady=%s audioSession=%d position=%d buffered=%d",
                confirmationSnapshot.playbackState,
                confirmationSnapshot.isPlaybackConfirmed,
                confirmationSnapshot.videoWidth,
                confirmationSnapshot.videoHeight,
                confirmationSnapshot.hasRenderedFirstFrame,
                confirmationSnapshot.isSurfaceReady,
                confirmationSnapshot.audioSessionId,
                confirmationSnapshot.currentPosition,
                confirmationSnapshot.bufferedPosition
            )
        }
        return stablePlayback
    }

    private fun shouldExtendLiveWarmup(snapshot: PlayerState): Boolean {
        if (!snapshot.hasVideoTrack ||
            !snapshot.hasAudioTrack ||
            snapshot.playbackState == PlaybackState.ERROR
        ) {
            return false
        }

        if (snapshot.hasRenderedFirstFrame ||
            snapshot.videoWidth > 0 ||
            snapshot.videoHeight > 0 ||
            !snapshot.isSurfaceReady ||
            snapshot.audioSessionId <= 0
        ) {
            return false
        }

        val bufferedAheadMs =
            (snapshot.bufferedPosition - snapshot.currentPosition).coerceAtLeast(0L)

        return snapshot.currentPosition >= LIVE_WARMUP_MIN_PROGRESS_MS &&
            bufferedAheadMs >= LIVE_WARMUP_MIN_BUFFER_AHEAD_MS
    }

    private fun isUnavailableHttpStatus(errorMessage: String?): Boolean {
        val message = errorMessage.orEmpty()
        return listOf(403, 404, 410, 429, 503).any { code ->
            message.contains("Response code: $code") ||
                message.contains("HTTP $code") ||
                message.contains("code: $code")
        }
    }

    private fun shouldRetryCurrentLiveCandidate(candidate: String, snapshot: PlayerState): Boolean {
        if (!candidate.contains("/live/")) {
            return true
        }

        val videoPipelineStalled = snapshot.hasVideoTrack &&
            snapshot.hasAudioTrack &&
            snapshot.isPlaying &&
            snapshot.isSurfaceReady &&
            snapshot.audioSessionId > 0 &&
            !snapshot.hasRenderedFirstFrame &&
            snapshot.videoWidth == 0 &&
            snapshot.videoHeight == 0 &&
            snapshot.currentPosition >= LIVE_WARMUP_MIN_PROGRESS_MS

        return !videoPipelineStalled
    }

    private fun isStableLivePlaybackAfterConfirmation(
        confirmationSnapshot: PlayerState,
        initiallyConfirmedSnapshot: PlayerState
    ): Boolean {
        if (confirmationSnapshot.playbackState == PlaybackState.PLAYING &&
            confirmationSnapshot.isPlaybackConfirmed
        ) {
            return true
        }

        val wasConfirmed = initiallyConfirmedSnapshot.isPlaybackConfirmed
        if (!wasConfirmed || confirmationSnapshot.playbackState != PlaybackState.BUFFERING) {
            return false
        }

        val hasRenderableVideo = confirmationSnapshot.hasRenderedFirstFrame &&
            confirmationSnapshot.isSurfaceReady &&
            confirmationSnapshot.videoWidth > 0 &&
            confirmationSnapshot.videoHeight > 0
        val hasAudiblePlayback = confirmationSnapshot.audioSessionId > 0
        val advancedPlayback = confirmationSnapshot.currentPosition >= 250L
        val bufferedAheadMs =
            (confirmationSnapshot.bufferedPosition - confirmationSnapshot.currentPosition).coerceAtLeast(0L)

        val transientRebufferAccepted = advancedPlayback &&
            bufferedAheadMs >= 1_500L &&
            ((confirmationSnapshot.hasVideoTrack && hasRenderableVideo) ||
                (!confirmationSnapshot.hasVideoTrack && hasAudiblePlayback))

        if (transientRebufferAccepted) {
            Timber.d(
                "LivePlayback accepted after confirmed start despite transient buffering: position=%d bufferedAhead=%d video=%s audio=%s",
                confirmationSnapshot.currentPosition,
                bufferedAheadMs,
                confirmationSnapshot.hasVideoTrack,
                confirmationSnapshot.hasAudioTrack
            )
        }

        return transientRebufferAccepted
    }

    private suspend fun resetPlaybackAttempt() {
        playerManager.stop()
        delay(30L)
    }

    private fun playbackProfileFor(contentType: String): PlaybackProfile {
        return if (contentType.equals(ContentType.LIVE.name, ignoreCase = true)) {
            PlaybackProfile.LIVE
        } else {
            PlaybackProfile.VOD
        }
    }

    private fun currentPlaybackProfile(): PlaybackProfile {
        return playbackProfileFor(currentContentType)
    }

    private fun buildEpisodePlayerTitle(seriesName: String, episode: Episode): String {
        val prefix = buildString {
            if (seriesName.isNotBlank()) {
                append(seriesName)
                append(" ")
            }
            append("S${episode.seasonNumber}E${episode.episodeNumber}")
        }

        return if (episode.name.isNotBlank()) {
            "$prefix • ${episode.name}"
        } else {
            prefix
        }
    }
}

internal fun liveNavigationAnchor(
    currentChannelId: Long?,
    switchingTargetChannelId: Long?,
    latestRequestedChannelId: Long?
): Long? = latestRequestedChannelId ?: switchingTargetChannelId ?: currentChannelId

internal fun adjacentLiveChannel(
    channels: List<Channel>,
    anchorChannelId: Long?,
    offset: Int
): Channel? {
    if (anchorChannelId == null || offset == 0) return null
    val anchorIndex = channels.indexOfFirst { it.id == anchorChannelId }
    if (anchorIndex < 0) return null
    return channels.getOrNull(anchorIndex + offset)
}

internal fun liveNavigationChannelsForTarget(
    targetChannel: Channel,
    browserChannels: List<Channel>,
    retainedChannels: List<Channel>,
    sessionChannels: List<Channel>
): List<Channel> = when {
    browserChannels.any { it.id == targetChannel.id } -> browserChannels
    retainedChannels.any { it.id == targetChannel.id } -> retainedChannels
    sessionChannels.any { it.id == targetChannel.id } -> sessionChannels
    else -> listOf(targetChannel)
}

internal fun shouldTriggerAutoPlayNextEpisode(
    isSeriesPlayback: Boolean,
    autoPlayEnabled: Boolean,
    transitionInProgress: Boolean,
    contentId: Long,
    alreadyTriggeredContentId: Long?,
    playbackState: PlaybackState,
    currentPosition: Long,
    duration: Long,
    minimumDuration: Long,
    endTolerance: Long
): Boolean {
    if (!isSeriesPlayback || !autoPlayEnabled || transitionInProgress || contentId <= 0L) return false
    if (alreadyTriggeredContentId == contentId) return false
    if (playbackState == PlaybackState.ENDED) return true

    val activeNearEndState = playbackState == PlaybackState.PLAYING ||
        playbackState == PlaybackState.BUFFERING
    return duration >= minimumDuration &&
        currentPosition >= duration - endTolerance &&
        activeNearEndState
}

internal fun seriesPlaybackCandidates(url: String): List<String> {
    val source = parsePlaybackSource(url)
    val trimmedUrl = source.url
    if (trimmedUrl.isBlank() || !trimmedUrl.contains("/series/", ignoreCase = true)) {
        return listOf(withPlaybackHeaders(trimmedUrl, source.headers)).filter(String::isNotBlank)
    }

    val suffixIndex = sequenceOf(trimmedUrl.indexOf('?'), trimmedUrl.indexOf('#'))
        .filter { it >= 0 }
        .minOrNull()
        ?: trimmedUrl.length
    val baseUrl = trimmedUrl.substring(0, suffixIndex)
    val suffix = trimmedUrl.substring(suffixIndex)
    val streamId = baseUrl.substringAfterLast('/')

    if (streamId.contains('.')) {
        return listOf(withPlaybackHeaders(trimmedUrl, source.headers))
    }

    return buildList {
        add(withPlaybackHeaders(trimmedUrl, source.headers))
        listOf("mkv", "mp4", "ts").forEach { extension ->
            add(withPlaybackHeaders("$baseUrl.$extension$suffix", source.headers))
        }
    }.distinct()
}

internal fun playbackStartPosition(requestedPosition: Long, autoResumeEnabled: Boolean): Long =
    if (autoResumeEnabled) requestedPosition.coerceAtLeast(0L) else 0L
