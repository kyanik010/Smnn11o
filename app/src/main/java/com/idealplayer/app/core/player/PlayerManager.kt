package com.idealplayer.app.core.player

import com.idealplayer.app.core.datastore.SettingsDataStore
import com.idealplayer.app.core.model.PlayerEngineType
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class PlaybackRecoveryState(
    val isRecovering: Boolean = false,
    val automaticFallbackUsed: Boolean = false,
    val fromEngine: String = "",
    val toEngine: String = "",
    val lastErrorCategory: String = ""
)

data class PlaybackDiagnostics(
    val engineName: String = "—",
    val streamProtocol: String = "unknown",
    val streamHost: String = "unknown",
    val requestHeaderNames: Set<String> = emptySet(),
    val recovery: PlaybackRecoveryState = PlaybackRecoveryState()
)

@Singleton
class PlayerManager @Inject constructor(
    private val exoPlayerProvider: Provider<ExoPlayerEngine>,
    private val vlcPlayerProvider: Provider<VlcPlayerEngine>,
    private val settingsDataStore: SettingsDataStore
) {
    private data class PlaybackRequest(
        val id: Long,
        val url: String,
        val startPosition: Long,
        val profile: PlaybackProfile
    )

    private val managerScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val switchMutex = Mutex() // Prevents concurrent engine switching

    private var currentEngine: PlayerEngine? = null
    private var stateCollectionJob: Job? = null
    private var lastPlaybackRequest: PlaybackRequest? = null
    private var currentPlaybackProfileKey: String? = null
    private var automaticFallbackAttempted = false
    private var profilePersistedForRequest = false
    private var hasConfirmedCurrentRequest = false
    private val playbackRequestSequence = AtomicLong(0L)

    private val _playerState = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _activeEngineName = MutableStateFlow<String?>(null)
    val activeEngineName: StateFlow<String?> = _activeEngineName.asStateFlow()

    private val _switchState = MutableStateFlow(EngineSwitchState.IDLE)
    val switchState: StateFlow<EngineSwitchState> = _switchState.asStateFlow()

    private val _recoveryState = MutableStateFlow(PlaybackRecoveryState())
    val recoveryState: StateFlow<PlaybackRecoveryState> = _recoveryState.asStateFlow()

    private val _diagnostics = MutableStateFlow(PlaybackDiagnostics())
    val diagnostics: StateFlow<PlaybackDiagnostics> = _diagnostics.asStateFlow()

    private var defaultAudioLang: String = "eng"
    private var defaultSubtitleLang: String = "eng"
    private var autoEnableSubtitles: Boolean = false
    private var preferredAspectRatio: AspectRatioMode = AspectRatioMode.FIT
    private var preferredVideoQualityMode: VideoQualityMode = VideoQualityMode.AUTO
    private var defaultPlaybackSpeed: Float = 1f

    private var cachedBufferMs: Long = 30000L
    private var cachedLatencyMode: String = "BALANCED"
    private var cachedPreferHw: Boolean = true
    private var cachedAllowQualityFallback: Boolean = true
    private var cachedAutoPlayerFallback: Boolean = true

    fun getEngine(): PlayerEngine? = currentEngine
    fun getActiveEngineType(): PlayerEngineType? = activeEngineTypeFor(currentEngine)

    suspend fun initializeWithSettings() {
        // A player route owns a new playback session. Invalidate any release queued by the
        // previous route before touching the shared singleton engine.
        val sessionRequestId = playbackRequestSequence.incrementAndGet()
        val settings = refreshSettingsCache()
        switchEngineInternal(
            targetType = PlayerEngineType.fromStoredValue(settings.playerEngine),
            replayRequest = false,
            expectedRequestId = sessionRequestId
        )
    }

    suspend fun updatePreferredEngine(targetType: PlayerEngineType) {
        settingsDataStore.updatePlayerEngine(targetType.name)
        if (activeEngineTypeFor(currentEngine) == targetType) {
            _activeEngineName.value = currentEngine?.engineName
            _switchState.value = EngineSwitchState.SUCCESS
            return
        }

        if (currentEngine != null) {
            switchEngine(targetType)
        }
    }

    private fun startCollectingState() {
        stateCollectionJob?.cancel()
        val engine = currentEngine ?: return
        stateCollectionJob = managerScope.launch {
            engine.state.collect { engineState ->
                if (engine !== currentEngine) return@collect
                if (engineState.isPlaybackConfirmed) hasConfirmedCurrentRequest = true

                if (shouldAttemptAutomaticFallback(
                        enabled = cachedAutoPlayerFallback,
                        activeEngineType = activeEngineTypeFor(engine),
                        state = engineState,
                        hasPreviouslyConfirmed = hasConfirmedCurrentRequest,
                        alreadyAttempted = automaticFallbackAttempted,
                        hasPlaybackRequest = lastPlaybackRequest != null
                    )
                ) {
                    automaticFallbackAttempted = true
                    val errorCategory = classifyPlaybackError(engineState.errorMessage)
                    _recoveryState.value = PlaybackRecoveryState(
                        isRecovering = true,
                        automaticFallbackUsed = true,
                        fromEngine = engine.engineName,
                        toEngine = PlayerEngineType.VLC.name,
                        lastErrorCategory = errorCategory
                    )
                    _playerState.value = engineState.copy(
                        playbackState = PlaybackState.BUFFERING,
                        isPlaying = false,
                        errorMessage = null
                    )
                    updateDiagnostics(engine.engineName)
                    val failedRequestId = lastPlaybackRequest?.id ?: return@collect
                    managerScope.launch {
                        Timber.w("Media3 playback failed before confirmation; attempting VLC recovery (%s)", errorCategory)
                        switchEngineInternal(
                            targetType = PlayerEngineType.VLC,
                            replayRequest = true,
                            expectedRequestId = failedRequestId
                        )
                    }
                    return@collect
                }

                _playerState.value = engineState
                if (engineState.isPlaybackConfirmed) {
                    val currentType = activeEngineTypeFor(engine)
                    val profileKey = currentPlaybackProfileKey
                    if (
                        _recoveryState.value.automaticFallbackUsed &&
                        currentType == PlayerEngineType.VLC &&
                        profileKey != null &&
                        !profilePersistedForRequest
                    ) {
                        profilePersistedForRequest = true
                        managerScope.launch(Dispatchers.IO) {
                            settingsDataStore.updatePlaybackEngineProfile(profileKey, PlayerEngineType.VLC.name)
                        }
                    }
                    if (_recoveryState.value.isRecovering) {
                        _recoveryState.value = _recoveryState.value.copy(isRecovering = false)
                    }
                } else if (
                    engineState.playbackState == PlaybackState.ERROR &&
                    _recoveryState.value.isRecovering
                ) {
                    _recoveryState.value = _recoveryState.value.copy(
                        isRecovering = false,
                        lastErrorCategory = classifyPlaybackError(engineState.errorMessage)
                    )
                }
                updateDiagnostics(engine.engineName)
            }
        }
    }

    /**
     * Safely switches the playback engine.
     * Guarantees old engine is released before the new one is instantiated.
     */
    suspend fun switchEngine(targetType: PlayerEngineType) {
        switchEngineInternal(targetType, replayRequest = true)
    }

    private suspend fun switchEngineInternal(
        targetType: PlayerEngineType,
        replayRequest: Boolean,
        expectedRequestId: Long? = null
    ) {
        switchMutex.withLock {
            if (expectedRequestId != null && expectedRequestId != playbackRequestSequence.get()) {
                Timber.d("Skipping stale playback pipeline request=%d", expectedRequestId)
                return@withLock
            }
            if (expectedRequestId != null && !replayRequest) {
                // A new route must never replay the previous route's last stream while its own
                // request is being prepared.
                lastPlaybackRequest = null
                currentPlaybackProfileKey = null
                automaticFallbackAttempted = false
                profilePersistedForRequest = false
                hasConfirmedCurrentRequest = false
            }
            _switchState.value = EngineSwitchState.SWITCHING
            var engineBeingPrepared: PlayerEngine? = null

            try {
                val previousState = _playerState.value
                val pendingPlaybackRequest = lastPlaybackRequest?.takeIf {
                    replayRequest &&
                    shouldReplayAfterEngineSwitch(previousState.playbackState)
                }?.let { request ->
                    request.copy(
                        startPosition = engineSwitchStartPosition(
                            profile = request.profile,
                            currentPosition = previousState.currentPosition
                        )
                    )
                }

                // 1. Stop collecting state
                stateCollectionJob?.cancel()
                stateCollectionJob = null

                // 2. Safely release current engine completely
                currentEngine?.let { oldEngine ->
                    safelyReleaseEngine(oldEngine)
                }
                currentEngine = null
                _activeEngineName.value = null
                _playerState.value = PlayerState(
                    aspectRatioMode = preferredAspectRatio,
                    videoQualityMode = preferredVideoQualityMode,
                    playbackSpeed = defaultPlaybackSpeed
                )

                // 3. Create new engine via Provider (Lazy Init)
                val newEngine = when (targetType) {
                    PlayerEngineType.EXOPLAYER -> exoPlayerProvider.get()
                    PlayerEngineType.VLC -> vlcPlayerProvider.get()
                }

                // Verify availability, fallback if needed
                val finalEngine = if (!newEngine.isAvailable() && targetType == PlayerEngineType.EXOPLAYER) {
                    Timber.w("Requested EXOPLAYER but it's unavailable. Falling back to VLC.")
                    vlcPlayerProvider.get()
                } else {
                    newEngine
                }
                engineBeingPrepared = finalEngine

                // 4. Initialize new engine
                withContext(Dispatchers.Main.immediate) {
                    finalEngine.initialize()
                    applyCachedSettingsToEngine(finalEngine)
                }
                if (finalEngine.state.value.playbackState == PlaybackState.ERROR) {
                    throw IllegalStateException(
                        finalEngine.state.value.errorMessage ?: "Playback engine initialization failed"
                    )
                }

                currentEngine = finalEngine
                _activeEngineName.value = finalEngine.engineName
                updateDiagnostics(finalEngine.engineName)
                startCollectingState()

                pendingPlaybackRequest
                    ?.takeIf { request ->
                        request.id == playbackRequestSequence.get() &&
                            lastPlaybackRequest?.id == request.id
                    }
                    ?.let { request ->
                        withContext(Dispatchers.Main.immediate) {
                            finalEngine.play(
                                url = request.url,
                                startPosition = request.startPosition,
                                profile = request.profile
                            )
                        }
                    }

                engineBeingPrepared = null
                _switchState.value = EngineSwitchState.SUCCESS
                Timber.d("Successfully switched to engine: ${finalEngine.engineName}")

            } catch (cancellation: CancellationException) {
                withContext(NonCancellable) {
                    stateCollectionJob?.cancel()
                    stateCollectionJob = null
                    (currentEngine ?: engineBeingPrepared)?.let { safelyReleaseEngine(it) }
                    currentEngine = null
                    _activeEngineName.value = null
                    _switchState.value = EngineSwitchState.IDLE
                }
                throw cancellation
            } catch (e: Exception) {
                Timber.e(e, "Failed to switch engine")
                stateCollectionJob?.cancel()
                stateCollectionJob = null
                (currentEngine ?: engineBeingPrepared)?.let { safelyReleaseEngine(it) }
                currentEngine = null
                _activeEngineName.value = null
                _switchState.value = EngineSwitchState.ERROR
                _recoveryState.value = _recoveryState.value.copy(isRecovering = false)
                _playerState.value = PlayerState(
                    playbackState = PlaybackState.ERROR,
                    errorMessage = "Playback engine switch failed",
                    aspectRatioMode = preferredAspectRatio,
                    videoQualityMode = preferredVideoQualityMode,
                    playbackSpeed = defaultPlaybackSpeed
                )
                updateDiagnostics()
            }
        }
    }

    suspend fun play(
        url: String,
        startPosition: Long = 0L,
        profile: PlaybackProfile = PlaybackProfile.VOD
    ) {
        val requestId = playbackRequestSequence.incrementAndGet()
        val settings = refreshSettingsCache()
        val profileKey = playbackProfileKey(url)
        val rememberedEngine = settingsDataStore.getPlaybackEngineProfile(profileKey)
            ?.let(PlayerEngineType::fromStoredValue)
        val preferredEngine = rememberedEngine
            ?: PlayerEngineType.fromStoredValue(settings.playerEngine)
        if (requestId != playbackRequestSequence.get()) return

        val source = parsePlaybackSource(url)
        val (protocol, host) = playbackSourceSummary(url)
        val request = PlaybackRequest(
            id = requestId,
            url = url,
            startPosition = startPosition,
            profile = profile
        )

        var engineSwitchAttempted = false
        while (requestId == playbackRequestSequence.get()) {
            val shouldSwitchEngine = switchMutex.withLock {
                if (requestId != playbackRequestSequence.get()) {
                    return@withLock false
                }

                val engine = currentEngine
                if (shouldPreparePlaybackEngine(
                        activeEngineType = activeEngineTypeFor(engine),
                        preferredEngineType = preferredEngine,
                        engineSwitchAttempted = engineSwitchAttempted
                    )
                ) {
                    return@withLock true
                }

                currentPlaybackProfileKey = profileKey
                automaticFallbackAttempted = false
                profilePersistedForRequest = false
                hasConfirmedCurrentRequest = false
                _recoveryState.value = PlaybackRecoveryState()
                _diagnostics.value = PlaybackDiagnostics(
                    engineName = engine?.engineName ?: "—",
                    streamProtocol = protocol,
                    streamHost = host,
                    requestHeaderNames = source.headers.keys,
                    recovery = _recoveryState.value
                )
                lastPlaybackRequest = request

                withContext(Dispatchers.Main.immediate) {
                    if (requestId != playbackRequestSequence.get()) return@withContext

                    if (engine == null) {
                        _playerState.value = PlayerState(
                            playbackState = PlaybackState.ERROR,
                            errorMessage = "Playback engine is not initialized",
                            aspectRatioMode = preferredAspectRatio,
                            videoQualityMode = preferredVideoQualityMode,
                            playbackSpeed = defaultPlaybackSpeed
                        )
                    } else {
                        applyCachedSettingsToEngine(engine)
                        engine.play(url, startPosition, profile)
                    }
                }
                false
            }

            if (!shouldSwitchEngine) return
            if (requestId != playbackRequestSequence.get()) return
            engineSwitchAttempted = true
            switchEngineInternal(preferredEngine, replayRequest = false)
        }
    }

    suspend fun pause() {
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.pause()
        }
    }

    suspend fun resume() {
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.resume()
        }
    }

    suspend fun stop() {
        playbackRequestSequence.incrementAndGet()
        switchMutex.withLock {
            lastPlaybackRequest = null
            currentPlaybackProfileKey = null
            withContext(Dispatchers.Main.immediate) {
                currentEngine?.stop()
            }
        }
    }

    suspend fun seekTo(position: Long) {
        withContext(Dispatchers.Main.immediate) {
            // The active engine owns the authoritative timeline/seekability state. Manager state
            // collection can be one callback behind when a manifest publishes its duration.
            currentEngine?.seekTo(position)
        }
    }

    suspend fun seekForward(ms: Long) {
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.seekForward(ms.coerceAtLeast(0L))
        }
    }

    suspend fun seekBackward(ms: Long) {
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.seekBackward(ms.coerceAtLeast(0L))
        }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        defaultPlaybackSpeed = speed
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.setPlaybackSpeed(speed)
        }
    }

    suspend fun selectAudioTrack(index: Int) {
        withContext(Dispatchers.Main.immediate) {
            if (_playerState.value.audioTracks.any { it.index == index }) {
                currentEngine?.selectAudioTrack(index)
            } else {
                Timber.w("Ignoring unavailable audio track id=%d", index)
            }
        }
    }

    suspend fun selectSubtitleTrack(index: Int) {
        withContext(Dispatchers.Main.immediate) {
            if (_playerState.value.subtitleTracks.any { it.index == index }) {
                currentEngine?.selectSubtitleTrack(index)
            } else {
                Timber.w("Ignoring unavailable subtitle track id=%d", index)
            }
        }
    }

    suspend fun disableSubtitles() {
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.disableSubtitles()
        }
    }

    suspend fun setAspectRatio(mode: AspectRatioMode) {
        preferredAspectRatio = mode
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.setAspectRatio(mode)
        }
        settingsDataStore.updateAspectRatio(mode.name.lowercase())
    }

    suspend fun setVideoQualityMode(mode: VideoQualityMode) {
        preferredVideoQualityMode = mode
        withContext(Dispatchers.Main.immediate) {
            currentEngine?.setVideoQualityMode(mode)
        }
        settingsDataStore.updateVideoQualityMode(mode.name)
    }

    suspend fun selectVideoTrack(index: Int) {
        withContext(Dispatchers.Main.immediate) {
            if (index < 0) {
                currentEngine?.setVideoQualityMode(VideoQualityMode.AUTO)
                return@withContext
            }
            currentEngine?.selectVideoTrack(index)
        }
    }

    fun release() {
        val releaseRequestId = playbackRequestSequence.incrementAndGet()
        managerScope.launch(Dispatchers.Main.immediate) {
            try {
                switchMutex.withLock {
                    // Navigation may create a new player route while an old route's asynchronous
                    // cleanup is waiting for this mutex. Never let stale cleanup tear down the
                    // newly initialized engine.
                    if (releaseRequestId != playbackRequestSequence.get()) {
                        Timber.d("Skipping stale playback release request=%d", releaseRequestId)
                        return@withLock
                    }
                    stateCollectionJob?.cancel()
                    stateCollectionJob = null
                    lastPlaybackRequest = null
                    currentPlaybackProfileKey = null
                    automaticFallbackAttempted = false
                    profilePersistedForRequest = false
                    hasConfirmedCurrentRequest = false

                    val engineToRelease = currentEngine
                    if (engineToRelease != null) {
                        safelyReleaseEngine(engineToRelease)
                    }

                    currentEngine = null
                    _activeEngineName.value = null
                    _switchState.value = EngineSwitchState.IDLE
                    _recoveryState.value = PlaybackRecoveryState()
                    _diagnostics.value = PlaybackDiagnostics()
                    _playerState.value = PlayerState(
                        aspectRatioMode = preferredAspectRatio,
                        videoQualityMode = preferredVideoQualityMode,
                        playbackSpeed = defaultPlaybackSpeed
                    )
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Error releasing playback pipeline")
            }
        }
    }

    private fun activeEngineTypeFor(engine: PlayerEngine?): PlayerEngineType? {
        return when (engine) {
            is ExoPlayerEngine -> PlayerEngineType.EXOPLAYER
            is VlcPlayerEngine -> PlayerEngineType.VLC
            else -> null
        }
    }

    private suspend fun safelyReleaseEngine(engine: PlayerEngine) {
        withContext(Dispatchers.Main.immediate) {
            try {
                engine.stop()
                if (engine is VlcPlayerEngine) {
                    engine.detachSurface()
                }
            } catch (exception: Exception) {
                Timber.w(exception, "Error stopping playback engine")
            }
        }

        // LibVLC native release can block; Android view/surface cleanup above remains on main.
        withContext(if (engine is VlcPlayerEngine) Dispatchers.IO else Dispatchers.Main.immediate) {
            try {
                engine.release()
            } catch (exception: Exception) {
                Timber.w(exception, "Error releasing playback engine")
            }
        }
    }

    private suspend fun refreshSettingsCache(): com.idealplayer.app.core.datastore.AppSettings {
        val settings = settingsDataStore.settings.first()

        defaultAudioLang = settings.defaultAudioLanguage
        defaultSubtitleLang = settings.defaultSubtitleLanguage
        autoEnableSubtitles = settings.autoEnableSubtitles
        preferredAspectRatio = AspectRatioMode.entries.firstOrNull {
            it.name.equals(settings.aspectRatio, ignoreCase = true)
        } ?: AspectRatioMode.FIT
        preferredVideoQualityMode = VideoQualityMode.entries.firstOrNull {
            it.name.equals(settings.videoQualityMode, ignoreCase = true)
        } ?: VideoQualityMode.AUTO
        defaultPlaybackSpeed = settings.defaultPlaybackSpeed

        cachedBufferMs = settings.bufferDurationMs.toLong()
        cachedLatencyMode = settings.liveLatencyMode
        cachedPreferHw = settings.preferHwDecoding
        cachedAllowQualityFallback = settings.allowQualityFallback
        cachedAutoPlayerFallback = settings.autoPlayerFallback
        return settings
    }

    private fun applyCachedSettingsToEngine(engine: PlayerEngine) {
        engine.setPlaybackConfiguration(
            cachedBufferMs,
            cachedLatencyMode,
            cachedPreferHw,
            cachedAllowQualityFallback
        )
        engine.setAspectRatio(preferredAspectRatio)
        engine.setVideoQualityMode(preferredVideoQualityMode)
        engine.setPlaybackSpeed(defaultPlaybackSpeed)
        applyPreferredTracks(engine)
    }

    private fun applyPreferredTracks(engine: PlayerEngine) {
        val audioLanguage = resolvePreferredLanguage(defaultAudioLang)
        val subtitleLanguage = resolvePreferredLanguage(defaultSubtitleLang)
        val subtitlesDisabled = defaultSubtitleLang.isExplicitlyDisabledLanguage()

        when (engine) {
            is ExoPlayerEngine -> {
                audioLanguage?.let(engine::setPreferredAudioLanguage)
                when {
                    subtitlesDisabled -> engine.disableSubtitles()
                    subtitleLanguage != null -> engine.setPreferredSubtitleLanguage(subtitleLanguage)
                    !autoEnableSubtitles -> engine.disableSubtitles()
                }
            }

            is VlcPlayerEngine -> {
                audioLanguage?.let(engine::setPreferredAudioLanguage)
                when {
                    subtitlesDisabled -> engine.disableSubtitles()
                    subtitleLanguage != null -> engine.setPreferredSubtitleLanguage(subtitleLanguage)
                    !autoEnableSubtitles -> engine.disableSubtitles()
                }
            }
        }
    }

    private fun resolvePreferredLanguage(language: String): String? {
        val normalized = language.trim()
        if (normalized.isBlank() || normalized.isExplicitlyDisabledLanguage()) {
            return null
        }

        return when (normalized.lowercase(Locale.getDefault())) {
            "auto", "default" -> null
            "system" -> Locale.getDefault().language.takeIf { it.isNotBlank() }
            else -> normalized
        }
    }

    private fun String.isExplicitlyDisabledLanguage(): Boolean {
        return lowercase(Locale.getDefault()).trim() in listOf("off", "none")
    }

    private fun updateDiagnostics(engineName: String = currentEngine?.engineName ?: "—") {
        _diagnostics.value = _diagnostics.value.copy(
            engineName = engineName,
            recovery = _recoveryState.value
        )
    }
}

internal fun shouldReplayAfterEngineSwitch(playbackState: PlaybackState): Boolean =
    playbackState !in listOf(
        PlaybackState.IDLE,
        PlaybackState.STOPPED,
        PlaybackState.ENDED
    )

internal fun engineSwitchStartPosition(
    profile: PlaybackProfile,
    currentPosition: Long
): Long = if (profile == PlaybackProfile.LIVE) 0L else currentPosition.coerceAtLeast(0L)

internal fun shouldPreparePlaybackEngine(
    activeEngineType: PlayerEngineType?,
    preferredEngineType: PlayerEngineType,
    engineSwitchAttempted: Boolean
): Boolean = !engineSwitchAttempted && activeEngineType != preferredEngineType

internal fun shouldAttemptAutomaticFallback(
    enabled: Boolean,
    activeEngineType: PlayerEngineType?,
    state: PlayerState,
    hasPreviouslyConfirmed: Boolean,
    alreadyAttempted: Boolean,
    hasPlaybackRequest: Boolean
): Boolean = enabled &&
    activeEngineType == PlayerEngineType.EXOPLAYER &&
    state.playbackState == PlaybackState.ERROR &&
    !state.isPlaybackConfirmed &&
    !hasPreviouslyConfirmed &&
    !alreadyAttempted &&
    hasPlaybackRequest

internal fun classifyPlaybackError(message: String?): String {
    val normalized = message.orEmpty().lowercase(Locale.ROOT)
    return when {
        normalized.contains("401") || normalized.contains("403") -> "authorization"
        normalized.contains("404") -> "not_found"
        normalized.contains("http") || normalized.contains("network") || normalized.contains("timeout") -> "network"
        normalized.contains("codec") || normalized.contains("decoder") || normalized.contains("hevc") -> "decoder"
        normalized.contains("source") || normalized.contains("container") || normalized.contains("format") -> "source"
        normalized.isBlank() -> "unknown"
        else -> "playback"
    }
}
