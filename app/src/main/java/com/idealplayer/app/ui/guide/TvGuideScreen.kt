package com.idealplayer.app.ui.guide

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.idealplayer.app.R
import com.idealplayer.app.core.catchup.CatchupUrlResolver
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.A2Motion
import com.idealplayer.app.core.designsystem.theme.A2Shape
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.core.model.Channel
import com.idealplayer.app.core.model.Playlist
import com.idealplayer.app.core.model.PlaylistType
import com.idealplayer.app.data.parser.EpgProgram
import com.idealplayer.app.data.repository.ContentRepository
import com.idealplayer.app.data.repository.EpgRepository
import com.idealplayer.app.data.repository.PlaylistRepository
import com.idealplayer.app.ui.components.IdealPlayerDrawer
import com.idealplayer.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GUIDE_WINDOW_MS = 4L * 60 * 60 * 1000
private const val GUIDE_SHIFT_MS = 2L * 60 * 60 * 1000
private const val GUIDE_DAY_MS = 24L * 60 * 60 * 1000
private const val GUIDE_SLOT_MS = 30L * 60 * 1000

data class GuideRow(
    val channel: Channel,
    val programs: List<EpgProgram>
)

data class GuidePlaybackRequest(
    val url: String,
    val title: String,
    val channelId: Long,
    val group: String
)

enum class GuideStatus {
    PROGRAM_NOT_STARTED,
    CATCHUP_UNAVAILABLE,
    NO_EPG_MATCH,
    NO_CHANNELS
}

data class TvGuideState(
    val playlist: Playlist? = null,
    val rows: List<GuideRow> = emptyList(),
    val groups: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val windowStart: Long = guideWindowStart(System.currentTimeMillis()),
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val status: GuideStatus? = null,
    val playbackRequest: GuidePlaybackRequest? = null
)

@HiltViewModel
class TvGuideViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository,
    private val epgRepository: EpgRepository,
    private val catchupUrlResolver: CatchupUrlResolver
) : ViewModel() {
    private val _state = MutableStateFlow(TvGuideState())
    val state: StateFlow<TvGuideState> = _state.asStateFlow()
    private var allChannels: List<Channel> = emptyList()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            playlistRepository.getActivePlaylist().collectLatest { playlist ->
                if (playlist == null) {
                    allChannels = emptyList()
                    _state.value = TvGuideState(isLoading = false)
                    return@collectLatest
                }

                val channels = contentRepository.getChannels(playlist.id).first()
                val groups = contentRepository.getChannelGroups(playlist.id).first()
                allChannels = channels
                _state.update {
                    it.copy(
                        playlist = playlist,
                        groups = groups,
                        selectedGroup = it.selectedGroup?.takeIf(groups::contains),
                        isLoading = true
                    )
                }
                loadGuide(syncWhenEmpty = true)
            }
        }
    }

    fun selectGroup(group: String?) {
        _state.update { it.copy(selectedGroup = group) }
        loadGuide(syncWhenEmpty = false)
    }

    fun shiftWindow(deltaMs: Long) {
        _state.update { current -> current.copy(windowStart = current.windowStart + deltaMs) }
        loadGuide(syncWhenEmpty = false)
    }

    fun goToNow() {
        _state.update { it.copy(windowStart = guideWindowStart(System.currentTimeMillis())) }
        loadGuide(syncWhenEmpty = false)
    }

    fun refresh() {
        loadGuide(syncWhenEmpty = true, forceSync = true)
    }

    fun playChannel(channel: Channel) {
        _state.update {
            it.copy(
                playbackRequest = GuidePlaybackRequest(
                    url = channel.streamUrl,
                    title = channel.name,
                    channelId = channel.id,
                    group = channel.groupTitle
                ),
                status = null
            )
        }
    }

    fun playProgram(channel: Channel, program: EpgProgram) {
        val now = System.currentTimeMillis()
        if (program.startTime > now) {
            _state.update { it.copy(status = GuideStatus.PROGRAM_NOT_STARTED) }
            return
        }
        if (program.endTime > now) {
            playChannel(channel)
            return
        }

        val playlist = _state.value.playlist ?: return
        val durationMinutes = ((program.endTime - program.startTime + 59_999L) / 60_000L)
            .coerceAtLeast(1L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val catchupUrl = catchupUrlResolver.resolve(
            channel = channel,
            startTimeMs = program.startTime,
            durationMins = durationMinutes,
            serverUrl = playlist.serverUrl.takeIf { playlist.type == PlaylistType.XTREAM_CODES }.orEmpty(),
            username = playlist.username.takeIf { playlist.type == PlaylistType.XTREAM_CODES }.orEmpty(),
            password = playlist.password.takeIf { playlist.type == PlaylistType.XTREAM_CODES }.orEmpty()
        )
        if (catchupUrl == null) {
            _state.update { it.copy(status = GuideStatus.CATCHUP_UNAVAILABLE) }
            return
        }

        _state.update {
            it.copy(
                playbackRequest = GuidePlaybackRequest(
                    url = catchupUrl,
                    title = "${channel.name} • ${program.title}",
                    channelId = channel.id,
                    group = channel.groupTitle
                ),
                status = null
            )
        }
    }

    fun consumePlaybackRequest() {
        _state.update { it.copy(playbackRequest = null) }
    }

    private fun loadGuide(syncWhenEmpty: Boolean, forceSync: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val snapshot = _state.value
            val playlist = snapshot.playlist ?: return@launch
            val channels = snapshot.selectedGroup
                ?.let { group -> allChannels.filter { it.groupTitle == group } }
                ?: allChannels
            _state.update { it.copy(isLoading = true, isSyncing = forceSync, status = null) }

            var programs = withContext(Dispatchers.IO) {
                epgRepository.getGuideProgramsForChannels(
                    channels = channels,
                    windowStart = snapshot.windowStart,
                    windowEnd = snapshot.windowStart + GUIDE_WINDOW_MS
                )
            }
            if (forceSync || (syncWhenEmpty && programs.values.all(List<EpgProgram>::isEmpty))) {
                playlistRepository.ensureEpgSynced(playlist, forceRefresh = forceSync)
                programs = withContext(Dispatchers.IO) {
                    epgRepository.getGuideProgramsForChannels(
                        channels = channels,
                        windowStart = snapshot.windowStart,
                        windowEnd = snapshot.windowStart + GUIDE_WINDOW_MS
                    )
                }
            }

            _state.update {
                it.copy(
                    rows = channels.map { channel -> GuideRow(channel, programs[channel.id].orEmpty()) },
                    isLoading = false,
                    isSyncing = false,
                    status = when {
                        channels.isEmpty() -> GuideStatus.NO_CHANNELS
                        programs.values.all(List<EpgProgram>::isEmpty) -> GuideStatus.NO_EPG_MATCH
                        else -> null
                    }
                )
            }
        }
    }
}

@Composable
fun TvGuideScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onPlayChannel: (String, String, Long, String) -> Unit,
    viewModel: TvGuideViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var drawerExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.playbackRequest) {
        state.playbackRequest?.let { request ->
            viewModel.consumePlaybackRequest()
            onPlayChannel(request.url, request.title, request.channelId, request.group)
        }
    }

    val content: @Composable () -> Unit = {
        GuideContent(
            state = state,
            isTv = isTv,
            onBack = { onNavigate(Routes.LIVE_TV) },
            onSelectGroup = viewModel::selectGroup,
            onShiftWindow = viewModel::shiftWindow,
            onNow = viewModel::goToNow,
            onRefresh = viewModel::refresh,
            onChannelClick = viewModel::playChannel,
            onProgramClick = viewModel::playProgram
        )
    }

    if (isTv) {
        IdealPlayerDrawer(
            isExpanded = drawerExpanded,
            selectedRoute = Routes.TV_GUIDE,
            isTv = true,
            onToggle = { drawerExpanded = !drawerExpanded },
            onNavigate = onNavigate
        ) { content() }
    } else {
        content()
    }
}

@Composable
private fun MobileGuideContent(
    state: TvGuideState,
    onBack: () -> Unit,
    onSelectGroup: (String?) -> Unit,
    onShiftWindow: (Long) -> Unit,
    onNow: () -> Unit,
    onRefresh: () -> Unit,
    onChannelClick: (Channel) -> Unit,
    onProgramClick: (Channel, EpgProgram) -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
    val zone = remember { ZoneId.systemDefault() }
    val nowLabel = timeFormatter.format(Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdealPlayerColors.Background)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            color = IdealPlayerColors.Surface,
            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(dimens.touchTargetMin)
                        .background(IdealPlayerColors.CardBackground, A2Shape.medium)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.player_back),
                        tint = IdealPlayerColors.TextPrimary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.tv_guide),
                    style = MaterialTheme.typography.titleLarge,
                    color = IdealPlayerColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onRefresh,
                    enabled = !state.isSyncing,
                    modifier = Modifier.size(dimens.touchTargetMin)
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = IdealPlayerColors.Primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            tint = IdealPlayerColors.TextSecondary
                        )
                    }
                }
            }
        }

        LazyRow(
            modifier = Modifier.padding(top = 16.dp),
            contentPadding = PaddingValues(horizontal = dimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "guide-window-previous-day") {
                GuideWindowChip(
                    text = stringResource(R.string.guide_previous_day),
                    onClick = { onShiftWindow(-GUIDE_DAY_MS) }
                )
            }
            item(key = "guide-window-previous") {
                GuideWindowChip(
                    text = stringResource(R.string.guide_previous_two_hours),
                    onClick = { onShiftWindow(-GUIDE_SHIFT_MS) }
                )
            }
            item(key = "guide-window-now") {
                GuideWindowChip(
                    text = stringResource(R.string.guide_now),
                    primary = true,
                    onClick = onNow
                )
            }
            item(key = "guide-window-next") {
                GuideWindowChip(
                    text = stringResource(R.string.guide_next_two_hours),
                    onClick = { onShiftWindow(GUIDE_SHIFT_MS) }
                )
            }
            item(key = "guide-window-next-day") {
                GuideWindowChip(
                    text = stringResource(R.string.guide_next_day),
                    onClick = { onShiftWindow(GUIDE_DAY_MS) }
                )
            }
        }

        LazyRow(
            modifier = Modifier.padding(top = 8.dp),
            contentPadding = PaddingValues(horizontal = dimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "guide-group-all") {
                GuideGroupChip(
                    text = stringResource(R.string.category_all),
                    selected = state.selectedGroup == null,
                    onClick = { onSelectGroup(null) }
                )
            }
            items(state.groups, key = { it }) { group ->
                GuideGroupChip(
                    text = group,
                    selected = state.selectedGroup == group,
                    onClick = { onSelectGroup(group) }
                )
            }
        }

        Text(
            text = "${stringResource(R.string.guide_now)} · $nowLabel",
            style = MaterialTheme.typography.titleLarge,
            color = IdealPlayerColors.TextPrimary,
            modifier = Modifier.padding(horizontal = dimens.screenPadding, vertical = 12.dp)
        )

        state.status?.let { status ->
            Text(
                text = guideStatusMessage(status),
                color = IdealPlayerColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = dimens.screenPadding, vertical = 4.dp)
            )
        }

        if (state.isLoading && state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IdealPlayerColors.Primary)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                bottom = dimens.screenPadding
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.rows, key = { it.channel.id }) { row ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MobileGuideChannelRow(
                        channel = row.channel,
                        programmeTitle = row.programs.firstOrNull()?.title,
                        onClick = { onChannelClick(row.channel) }
                    )
                    if (row.programs.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(
                                items = row.programs,
                                key = { guideProgramStableKey(row.channel.id, it) }
                            ) { program ->
                                GuideProgramCell(
                                    program = program,
                                    catchupAvailable = row.channel.catchupSource.isNotBlank() ||
                                        (state.playlist?.type == PlaylistType.XTREAM_CODES && row.channel.streamId > 0),
                                    isTv = false,
                                    modifier = Modifier.width(220.dp).height(72.dp),
                                    onClick = { onProgramClick(row.channel, program) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideWindowChip(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(48.dp),
        shape = A2Shape.medium,
        color = if (primary) IdealPlayerColors.Primary else IdealPlayerColors.CardBackground,
        border = if (primary) null else BorderStroke(1.dp, IdealPlayerColors.CardBorder)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (primary) IdealPlayerColors.TextOnPrimary else IdealPlayerColors.TextSecondary
            )
        }
    }
}

@Composable
private fun GuideGroupChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(48.dp),
        shape = A2Shape.medium,
        color = if (selected) IdealPlayerColors.SurfaceSelected else IdealPlayerColors.CardBackground,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) IdealPlayerColors.SelectedBorder else IdealPlayerColors.CardBorder
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) IdealPlayerColors.TextPrimary else IdealPlayerColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MobileGuideChannelRow(
    channel: Channel,
    programmeTitle: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = A2Shape.medium,
        color = IdealPlayerColors.CardBackground,
        border = BorderStroke(1.dp, IdealPlayerColors.CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IdealPlayerColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.name.take(2).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = IdealPlayerColors.Secondary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = IdealPlayerColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = programmeTitle ?: stringResource(R.string.epg_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = IdealPlayerColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun guideStatusMessage(status: GuideStatus): String = when (status) {
    GuideStatus.PROGRAM_NOT_STARTED -> stringResource(R.string.guide_program_not_started)
    GuideStatus.CATCHUP_UNAVAILABLE -> stringResource(R.string.guide_catchup_unavailable)
    GuideStatus.NO_EPG_MATCH -> stringResource(R.string.guide_no_epg_match)
    GuideStatus.NO_CHANNELS -> stringResource(R.string.guide_no_channels)
}

@Composable
private fun a2GuideFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = IdealPlayerColors.CardBackground,
    labelColor = IdealPlayerColors.TextSecondary,
    selectedContainerColor = IdealPlayerColors.SurfaceSelected,
    selectedLabelColor = IdealPlayerColors.TextPrimary
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideContent(
    state: TvGuideState,
    isTv: Boolean,
    onBack: () -> Unit,
    onSelectGroup: (String?) -> Unit,
    onShiftWindow: (Long) -> Unit,
    onNow: () -> Unit,
    onRefresh: () -> Unit,
    onChannelClick: (Channel) -> Unit,
    onProgramClick: (Channel, EpgProgram) -> Unit
) {
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    if (!isTv && !isTablet) {
        MobileGuideContent(
            state = state,
            onBack = onBack,
            onSelectGroup = onSelectGroup,
            onShiftWindow = onShiftWindow,
            onNow = onNow,
            onRefresh = onRefresh,
            onChannelClick = onChannelClick,
            onProgramClick = onProgramClick
        )
        return
    }

    val horizontalScroll = rememberScrollState()
    val channelWidth = if (isTv) 410.dp else 210.dp
    val rowHeight = if (isTv) 108.dp else 82.dp
    val minuteWidth = if (isTv) 5.3.dp else 3.dp
    val timelineWidth = minuteWidth * (GUIDE_WINDOW_MS / 60_000L).toFloat()
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }
    val zone = remember { ZoneId.systemDefault() }
    val windowEnd = state.windowStart + GUIDE_WINDOW_MS
    val refreshFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    // The guide has several horizontal focus regions and no natural first child while EPG data
    // is loading. Focus the always-present refresh action once the initial load completes;
    // do not repeat this on group/window changes or it interrupts D-pad traversal.
    LaunchedEffect(isTv, state.isLoading, state.isSyncing) {
        if (isTv && !state.isLoading && !state.isSyncing && !hasRequestedInitialFocus) {
            withFrameNanos { }
            runCatching { refreshFocusRequester.requestFocus() }
            hasRequestedInitialFocus = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdealPlayerColors.Background)
            .padding(horizontal = if (isTv) 48.dp else 24.dp, vertical = if (isTv) 32.dp else 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tv_guide),
                    style = if (isTv) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                    color = IdealPlayerColors.TextPrimary
                )
                Text(
                    text = dateFormatter.format(Instant.ofEpochMilli(state.windowStart).atZone(zone)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IdealPlayerColors.TextSecondary
                )
            }
            OutlinedButton(
                onClick = onRefresh,
                enabled = !state.isSyncing,
                modifier = Modifier
                    .heightIn(min = if (isTv) 56.dp else 48.dp)
                    .then(if (isTv) Modifier.focusRequester(refreshFocusRequester) else Modifier)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
            }
        }

        LazyRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            item {
                OutlinedButton(
                    onClick = { onShiftWindow(-GUIDE_DAY_MS) },
                    modifier = Modifier.heightIn(min = if (isTv) 56.dp else 48.dp)
                ) {
                    Text(stringResource(R.string.guide_previous_day))
                }
            }
            item {
                OutlinedButton(
                    onClick = { onShiftWindow(-GUIDE_SHIFT_MS) },
                    modifier = Modifier.heightIn(min = if (isTv) 56.dp else 48.dp)
                ) {
                    Text(stringResource(R.string.guide_previous_two_hours))
                }
            }
            item {
                Button(
                    onClick = onNow,
                    modifier = Modifier.heightIn(min = if (isTv) 56.dp else 48.dp)
                ) { Text(stringResource(R.string.guide_now)) }
            }
            item {
                OutlinedButton(
                    onClick = { onShiftWindow(GUIDE_SHIFT_MS) },
                    modifier = Modifier.heightIn(min = if (isTv) 56.dp else 48.dp)
                ) {
                    Text(stringResource(R.string.guide_next_two_hours))
                }
            }
            item {
                OutlinedButton(
                    onClick = { onShiftWindow(GUIDE_DAY_MS) },
                    modifier = Modifier.heightIn(min = if (isTv) 56.dp else 48.dp)
                ) {
                    Text(stringResource(R.string.guide_next_day))
                }
            }
        }

        LazyRow(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            item {
                FilterChip(
                    selected = state.selectedGroup == null,
                    onClick = { onSelectGroup(null) },
                    label = { Text(stringResource(R.string.category_all)) },
                    modifier = Modifier.heightIn(min = if (isTv) 56.dp else 48.dp),
                    colors = a2GuideFilterChipColors()
                )
            }
            items(state.groups, key = { group -> group }) { group ->
                FilterChip(
                    selected = state.selectedGroup == group,
                    onClick = { onSelectGroup(group) },
                    label = { Text(group, maxLines = 1) },
                    modifier = Modifier.heightIn(min = if (isTv) 56.dp else 48.dp),
                    colors = a2GuideFilterChipColors()
                )
            }
        }

        state.status?.let { status ->
            val message = when (status) {
                GuideStatus.PROGRAM_NOT_STARTED -> stringResource(R.string.guide_program_not_started)
                GuideStatus.CATCHUP_UNAVAILABLE -> stringResource(R.string.guide_catchup_unavailable)
                GuideStatus.NO_EPG_MATCH -> stringResource(R.string.guide_no_epg_match)
                GuideStatus.NO_CHANNELS -> stringResource(R.string.guide_no_channels)
            }
            Text(
                text = message,
                color = IdealPlayerColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (state.isLoading && state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IdealPlayerColors.Primary)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            stickyHeader {
                Row(modifier = Modifier.background(IdealPlayerColors.Background)) {
                    Box(
                        modifier = Modifier.width(channelWidth).height(44.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(stringResource(R.string.channels), color = IdealPlayerColors.TextSecondary)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .horizontalScroll(horizontalScroll)
                    ) {
                        Box(modifier = Modifier.width(timelineWidth).fillMaxHeight()) {
                            guideTicks(state.windowStart, windowEnd).forEach { tick ->
                                val x = minuteWidth * ((tick - state.windowStart) / 60_000f)
                                Text(
                                    text = timeFormatter.format(Instant.ofEpochMilli(tick).atZone(zone)),
                                    color = IdealPlayerColors.TextSecondary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.offset(x = x).padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            items(state.rows, key = { row -> row.channel.id }) { row ->
                Row(modifier = Modifier.height(rowHeight)) {
                    GuideChannelCell(
                        channel = row.channel,
                        width = channelWidth,
                        isTv = isTv,
                        subtitle = row.programs.firstOrNull()?.title,
                        onClick = { onChannelClick(row.channel) }
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(IdealPlayerColors.Surface)
                            .horizontalScroll(horizontalScroll)
                    ) {
                        Box(modifier = Modifier.width(timelineWidth).fillMaxHeight()) {
                            row.programs.forEach { program ->
                                val layout = guideProgramLayout(program, state.windowStart, windowEnd)
                                    ?: return@forEach
                                key(guideProgramStableKey(row.channel.id, program)) {
                                    GuideProgramCell(
                                        program = program,
                                        catchupAvailable = row.channel.catchupSource.isNotBlank() ||
                                            (state.playlist?.type == PlaylistType.XTREAM_CODES && row.channel.streamId > 0),
                                        isTv = isTv,
                                        modifier = Modifier
                                            .offset(x = minuteWidth * layout.offsetMinutes)
                                            .width((minuteWidth * layout.durationMinutes).coerceAtLeast(1.dp))
                                            .fillMaxHeight()
                                            .padding(2.dp),
                                        onClick = { onProgramClick(row.channel, program) }
                                    )
                                }
                            }
                            val now = System.currentTimeMillis()
                            if (now in state.windowStart until windowEnd) {
                                val x = minuteWidth * ((now - state.windowStart) / 60_000f)
                                Box(
                                    modifier = Modifier
                                        .offset(x = x)
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(IdealPlayerColors.Primary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideChannelCell(
    channel: Channel,
    width: Dp,
    isTv: Boolean,
    subtitle: String?,
    onClick: () -> Unit
) {
    val interactionSource = remember(channel.id) { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val showFocus = isTv && focused
    val scale by animateFloatAsState(
        targetValue = if (showFocus) A2Motion.FocusScale else 1f,
        animationSpec = tween(A2Motion.StandardMillis),
        label = "guideChannelScale"
    )
    val container by animateColorAsState(
        targetValue = if (showFocus) IdealPlayerColors.SurfaceFocus else IdealPlayerColors.CardBackground,
        animationSpec = tween(A2Motion.FastMillis),
        label = "guideChannelContainer"
    )
    Surface(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(end = 4.dp)
            .zIndex(if (showFocus) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                if (showFocus) 4.dp else 1.dp,
                if (showFocus) IdealPlayerColors.FocusBorder else IdealPlayerColors.CardBorder,
                A2Shape.medium
            )
            .clickable(
                interactionSource = interactionSource,
                indication = if (isTv) null else LocalIndication.current,
                onClick = onClick
            ),
        color = container,
        shape = A2Shape.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isTv) 16.dp else 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (isTv) 60.dp else 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IdealPlayerColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.name.take(2).uppercase(Locale.getDefault()),
                    color = IdealPlayerColors.Secondary,
                    style = if (isTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = IdealPlayerColors.TextPrimary,
                    style = if (isTv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = IdealPlayerColors.TextSecondary,
                        style = if (isTv) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideProgramCell(
    program: EpgProgram,
    catchupAvailable: Boolean,
    isTv: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val now = System.currentTimeMillis()
    val isCurrent = program.startTime <= now && program.endTime > now
    val isPast = program.endTime <= now
    val interactionSource = remember(program.startTime, program.endTime, program.title) {
        MutableInteractionSource()
    }
    val focused by interactionSource.collectIsFocusedAsState()
    val showFocus = isTv && focused
    val color by animateColorAsState(
        targetValue = when {
        showFocus -> IdealPlayerColors.SurfaceFocus
        isCurrent -> IdealPlayerColors.SurfaceFocus
        isPast -> IdealPlayerColors.SurfaceVariant.copy(alpha = 0.55f)
        else -> IdealPlayerColors.SurfaceVariant
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "guideProgramContainer"
    )
    val scale by animateFloatAsState(
        targetValue = if (showFocus) A2Motion.FocusScale else 1f,
        animationSpec = tween(A2Motion.StandardMillis),
        label = "guideProgramScale"
    )
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
    val zone = remember { ZoneId.systemDefault() }
    val timeLabel = remember(program.startTime, program.endTime, zone) {
        val start = formatter.format(Instant.ofEpochMilli(program.startTime).atZone(zone))
        val end = formatter.format(Instant.ofEpochMilli(program.endTime).atZone(zone))
        "$start–$end"
    }

    Surface(
        modifier = modifier
            .zIndex(if (showFocus) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                if (showFocus) 4.dp else 1.dp,
                if (showFocus || isCurrent) IdealPlayerColors.FocusBorder else IdealPlayerColors.CardBorder,
                A2Shape.medium
            )
            .clickable(
                interactionSource = interactionSource,
                indication = if (isTv) null else LocalIndication.current,
                onClick = onClick
            ),
        color = color,
        shape = A2Shape.medium
    ) {
        Column(modifier = Modifier.padding(horizontal = if (isTv) 16.dp else 10.dp, vertical = 8.dp)) {
            Text(
                text = if (isCurrent) "${stringResource(R.string.guide_now)} · $timeLabel" else timeLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) IdealPlayerColors.Primary else IdealPlayerColors.TextSecondary,
                style = if (isTv) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = program.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = IdealPlayerColors.TextPrimary,
                style = if (isTv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
            )
            if (isCurrent) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { program.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(if (isTv) 5.dp else 3.dp).clip(A2Shape.full),
                    color = IdealPlayerColors.Primary,
                    trackColor = IdealPlayerColors.CardBorder
                )
            }
            if (isPast && catchupAvailable) {
                Text(
                    text = stringResource(R.string.guide_catchup),
                    color = IdealPlayerColors.TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

data class GuideProgramLayout(val offsetMinutes: Float, val durationMinutes: Float)

internal fun guideWindowStart(nowMs: Long): Long =
    (nowMs / GUIDE_SLOT_MS) * GUIDE_SLOT_MS - GUIDE_SLOT_MS

internal fun guideProgramLayout(
    program: EpgProgram,
    windowStart: Long,
    windowEnd: Long
): GuideProgramLayout? {
    val clippedStart = maxOf(program.startTime, windowStart)
    val clippedEnd = minOf(program.endTime, windowEnd)
    if (clippedEnd <= clippedStart) return null
    return GuideProgramLayout(
        offsetMinutes = (clippedStart - windowStart) / 60_000f,
        durationMinutes = (clippedEnd - clippedStart) / 60_000f
    )
}

internal fun guideProgramStableKey(channelId: Long, program: EpgProgram): String =
    "$channelId:${program.startTime}:${program.endTime}:${program.title}"

private fun guideTicks(windowStart: Long, windowEnd: Long): List<Long> = buildList {
    var tick = ((windowStart + GUIDE_SLOT_MS - 1) / GUIDE_SLOT_MS) * GUIDE_SLOT_MS
    while (tick < windowEnd) {
        add(tick)
        tick += GUIDE_SLOT_MS
    }
}
