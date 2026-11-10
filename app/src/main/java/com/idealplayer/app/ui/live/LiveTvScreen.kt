package com.idealplayer.app.ui.live

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.core.designsystem.theme.A2Motion
import com.idealplayer.app.core.designsystem.theme.A2Shape
import com.idealplayer.app.core.model.Channel
import com.idealplayer.app.data.repository.ContentRepository
import com.idealplayer.app.data.repository.PlaylistRepository
import com.idealplayer.app.ui.components.*
import com.idealplayer.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.compose.ui.res.stringResource
import com.idealplayer.app.R

data class LiveTvState(
    val channels: List<Channel> = emptyList(),
    val mobileChannels: List<Channel> = emptyList(),
    val groups: List<String> = emptyList(),
    val mobileGroups: List<String> = emptyList(),
    val groupCounts: Map<String, Int> = emptyMap(),
    val selectedGroup: String? = null,
    val isLoading: Boolean = true,
    val epgPrograms: Map<Long, com.idealplayer.app.data.parser.EpgProgram> = emptyMap()
)

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val contentRepository: ContentRepository,
    private val epgRepository: com.idealplayer.app.data.repository.EpgRepository
) : ViewModel() {
    private val _state = MutableStateFlow(LiveTvState())
    val state: StateFlow<LiveTvState> = _state.asStateFlow()
    private var epgAutoDiscoveryPlaylistId: Long? = null

    init {
        viewModelScope.launch {
            playlistRepository.getActivePlaylist().collectLatest { playlist ->
                if (playlist == null) {
                    epgAutoDiscoveryPlaylistId = null
                    _state.value = LiveTvState()
                } else {
                    _state.update {
                        it.copy(
                            channels = emptyList(),
                            mobileChannels = emptyList(),
                            groups = emptyList(),
                            mobileGroups = emptyList(),
                            groupCounts = emptyMap(),
                            selectedGroup = null,
                            isLoading = true
                        )
                    }

                    contentRepository.getChannels(playlist.id)
                        .combine(contentRepository.getChannelGroups(playlist.id)) { channels, groups ->
                            channels to groups
                        }
                        .collectLatest { (channels, groups) ->
                            val orderedChannels = channels
                            val processed = withContext(Dispatchers.Default) {
                                val groupCounts = orderedChannels
                                    .filter { it.groupTitle.isNotBlank() }
                                    .groupBy { it.groupTitle }
                                    .mapValues { it.value.size }

                                ProcessedLiveTvContent(
                                    groups = groups,
                                    mobileGroups = groups,
                                    mobileChannels = orderedChannels,
                                    groupCounts = groupCounts
                                )
                            }

                            _state.update { currentState ->
                                val selectedGroup = currentState.selectedGroup
                                    ?.takeIf { it in processed.groups }

                                currentState.copy(
                                    channels = orderedChannels,
                                    mobileChannels = processed.mobileChannels,
                                    groups = processed.groups,
                                    mobileGroups = processed.mobileGroups,
                                    groupCounts = processed.groupCounts,
                                    selectedGroup = selectedGroup,
                                    isLoading = false
                                )
                            }

                            if (
                                orderedChannels.isNotEmpty() &&
                                epgAutoDiscoveryPlaylistId != playlist.id
                            ) {
                                epgAutoDiscoveryPlaylistId = playlist.id
                                viewModelScope.launch {
                                    val synced = playlistRepository.ensureEpgSynced(playlist)
                                    if (synced) {
                                        val channelsForEpg = _state.value.channels
                                        if (channelsForEpg.isNotEmpty()) {
                                            val programs = epgRepository.getCurrentProgramsForChannels(channelsForEpg)
                                            _state.update { it.copy(epgPrograms = programs) }
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    fun selectGroup(group: String?) = _state.update { it.copy(selectedGroup = group) }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            contentRepository.toggleChannelFavorite(channel.id, !channel.isFavorite)
        }
    }

    fun loadEpgForVisibleChannels(channels: List<Channel>) {
        viewModelScope.launch {
            val programs = epgRepository.getCurrentProgramsForChannels(channels)
            _state.update { it.copy(epgPrograms = programs) }
        }
    }
}

@Composable
fun LiveTvScreen(
    isTv: Boolean,
    onNavigate: (String) -> Unit,
    onPlayChannel: (String, String, Long, String?) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isDrawerExpanded by remember { mutableStateOf(false) }

    if (isTv) {
        IdealPlayerDrawer(
            isExpanded = isDrawerExpanded,
            selectedRoute = Routes.LIVE_TV,
            isTv = true,
            onToggle = { isDrawerExpanded = !isDrawerExpanded },
            onNavigate = onNavigate
        ) {
            if (state.isLoading) LoadingScreen()
            else TvLiveTvContent(state, viewModel, onPlayChannel)
        }
    } else {
        MobileLiveTvContent(state, viewModel, onPlayChannel, onNavigate)
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TvLiveTvContent(
    state: LiveTvState,
    viewModel: LiveTvViewModel,
    onPlayChannel: (String, String, Long, String?) -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val allCategoryLabel = stringResource(R.string.category_all)
    val displayCategories = remember(state.groups, allCategoryLabel) {
        listOf(
            TvCategoryRailItem(
                id = tvCategoryLazyKey("live", null),
                title = allCategoryLabel,
                group = null
            )
        ) + state.groups.map { group ->
            TvCategoryRailItem(
                id = tvCategoryLazyKey("live", group),
                title = group,
                group = group
            )
        }
    }
    val initialCategoryFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var previewChannelId by remember { mutableStateOf<Long?>(null) }
    Row(modifier = Modifier.fillMaxSize()) {
        val display = remember(state.channels, state.selectedGroup, query) {
            state.channels.filter { channel ->
                (state.selectedGroup == null || channel.groupTitle == state.selectedGroup) &&
                    (query.isBlank() || channel.name.contains(query, ignoreCase = true))
            }
        }
        val previewChannel = display.firstOrNull { it.id == previewChannelId } ?: display.firstOrNull()

        LaunchedEffect(display.map(Channel::id)) {
            if (display.isNotEmpty()) {
                viewModel.loadEpgForVisibleChannels(display)
            }
        }

        // A selected group and the first channel change frequently as EPG/content flows
        // update. Re-requesting a category on each change interrupted D-pad navigation and
        // made focus jump back to the category rail. Establish one deterministic entry focus
        // only; normal focus traversal owns all subsequent updates.
        LaunchedEffect(state.isLoading) {
            if (!hasRequestedInitialFocus && !state.isLoading) {
                withFrameNanos { }
                initialCategoryFocusRequester.requestFocusSafely()
                hasRequestedInitialFocus = true
            }
        }

        TvCategoryPanel {
            itemsIndexed(displayCategories, key = { _, category -> category.id }) { index, category ->
                TvRailCategoryItem(
                    name = category.title,
                    isSelected = state.selectedGroup == category.group,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(initialCategoryFocusRequester) else Modifier)
                        .focusProperties {
                            // Both rails are lazy and retain scroll state. Default spatial search
                            // selects a visible channel; an explicit index-zero requester can be
                            // detached after the channel list has scrolled.
                            if (display.isEmpty()) right = FocusRequester.Cancel
                            // Prevent wrap-around: block UP on first item, DOWN on last item
                            if (index == 0) up = FocusRequester.Cancel
                            if (index == displayCategories.lastIndex) down = FocusRequester.Cancel
                        },
                    onClick = { viewModel.selectGroup(category.group) }
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 24.dp, top = 32.dp, end = 48.dp, bottom = 48.dp)
        ) {
            Text(
                text = stringResource(R.string.live_tv),
                style = MaterialTheme.typography.headlineLarge,
                color = IdealPlayerColors.TextPrimary
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 420.dp, max = 500.dp)
                        .fillMaxHeight()
                        .clip(A2Shape.large)
                        .background(IdealPlayerColors.Surface)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiveSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        isTv = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (display.isEmpty()) {
                        EmptyScreen(
                            message = stringResource(R.string.no_content),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            itemsIndexed(display, key = { _, channel -> channel.id }) { index, channel ->
                                ChannelListItem(
                                    channel = channel,
                                    isTv = true,
                                    selected = previewChannel?.id == channel.id,
                                    epgProgram = state.epgPrograms[channel.id],
                                    modifier = Modifier.focusProperties {
                                        left = FocusRequester.Default
                                        right = FocusRequester.Cancel
                                        if (index == 0) up = FocusRequester.Cancel
                                        if (index == display.lastIndex) down = FocusRequester.Cancel
                                    },
                                    onFocused = { previewChannelId = channel.id },
                                    onClick = {
                                        onPlayChannel(
                                            channel.streamUrl,
                                            channel.name,
                                            channel.id,
                                            state.selectedGroup ?: channel.groupTitle
                                        )
                                    },
                                    onFavoriteToggle = { viewModel.toggleFavorite(channel) }
                                )
                            }
                        }
                    }
                }
                TvChannelPreview(
                    channel = previewChannel,
                    epgProgram = previewChannel?.let { state.epgPrograms[it.id] },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun MobileLiveTvContent(
    state: LiveTvState,
    viewModel: LiveTvViewModel,
    onPlayChannel: (String, String, Long, String?) -> Unit,
    onNavigate: (String) -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    var showCategorySheet by remember { mutableStateOf(false) }
    var recentGroups by remember { mutableStateOf(listOf<String>()) }
    var pinnedGroups by remember { mutableStateOf(listOf<String>()) }
    var query by remember { mutableStateOf("") }
    val rankedChannels = state.mobileChannels
    val mobileGroups = state.mobileGroups

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
                modifier = Modifier.padding(horizontal = dimens.screenPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.live_tv),
                    style = MaterialTheme.typography.titleLarge,
                    color = IdealPlayerColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onNavigate(Routes.TV_GUIDE) },
                    modifier = Modifier.size(dimens.touchTargetMin)
                ) {
                    Icon(
                        Icons.Filled.DateRange,
                        contentDescription = stringResource(R.string.tv_guide),
                        tint = IdealPlayerColors.TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = dimens.screenPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.live_tv),
                style = MaterialTheme.typography.headlineMedium,
                color = IdealPlayerColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { viewModel.selectGroup(null) }) {
                Text(
                    text = stringResource(R.string.all_categories),
                    color = IdealPlayerColors.Primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (isTablet) {
            LiveSearchField(
                query = query,
                onQueryChange = { query = it },
                isTv = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenPadding)
            )
            Spacer(Modifier.height(8.dp))
        }

        QuickCategoryBar(
            categories = mobileGroups,
            selectedCategory = state.selectedGroup,
            recentCategories = recentGroups,
            onCategorySelected = { group ->
                viewModel.selectGroup(group)
                if (group != null && group !in recentGroups) {
                    recentGroups = (listOf(group) + recentGroups).take(10)
                }
            },
            onBrowseAll = { showCategorySheet = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        val display = remember(rankedChannels, state.selectedGroup, query) {
            rankedChannels.filter { channel ->
                (state.selectedGroup == null || channel.groupTitle == state.selectedGroup) &&
                    (query.isBlank() || channel.name.contains(query, ignoreCase = true))
            }
        }

        LaunchedEffect(display.map(Channel::id)) {
            if (display.isNotEmpty()) {
                viewModel.loadEpgForVisibleChannels(display)
            }
        }

        if (state.isLoading && display.isEmpty()) {
            ChannelListLoadingPlaceholder()
        } else if (display.isEmpty()) {
            EmptyScreen(message = stringResource(R.string.no_content))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = dimens.screenPadding, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(display, key = { it.id }) { channel ->
                    ChannelListItem(
                        channel = channel,
                        isTv = false,
                        selected = false,
                        epgProgram = state.epgPrograms[channel.id],
                        onClick = { onPlayChannel(channel.streamUrl, channel.name, channel.id, state.selectedGroup) },
                        onFavoriteToggle = { viewModel.toggleFavorite(channel) }
                    )
                }
            }
        }
    }

    CategoryBottomSheet(
        isVisible = showCategorySheet,
        categories = mobileGroups,
        categoryCounts = state.groupCounts,
        selectedCategory = state.selectedGroup,
        recentCategories = recentGroups,
        pinnedCategories = pinnedGroups,
        onCategorySelected = { group ->
            viewModel.selectGroup(group)
            if (group != null && group !in recentGroups) {
                recentGroups = (listOf(group) + recentGroups).take(10)
            }
        },
        onDismiss = { showCategorySheet = false },
        onTogglePin = { g ->
            pinnedGroups = if (g in pinnedGroups) pinnedGroups - g else pinnedGroups + g
        }
    )
}

private data class ProcessedLiveTvContent(
    val groups: List<String>,
    val mobileGroups: List<String>,
    val mobileChannels: List<Channel>,
    val groupCounts: Map<String, Int>
)

@Composable
private fun ChannelListLoadingPlaceholder() {
    val dimens = LocalIdealPlayerDimens.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = dimens.screenPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(IdealPlayerColors.CardBackground)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                ShimmerBox(modifier = Modifier.size(24.dp).clip(CircleShape))
            }
        }
    }
}

@Composable
private fun LiveSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.height(if (isTv) 56.dp else 48.dp),
        singleLine = true,
        textStyle = if (isTv) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(
                text = stringResource(R.string.search_hint),
                color = IdealPlayerColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = IdealPlayerColors.TextSecondary,
                modifier = Modifier.size(if (isTv) 24.dp else 20.dp)
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(if (isTv) 56.dp else 48.dp)
                ) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.action_clear_search),
                        tint = IdealPlayerColors.TextSecondary
                    )
                }
            }
        } else {
            null
        },
        shape = A2Shape.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = IdealPlayerColors.TextPrimary,
            unfocusedTextColor = IdealPlayerColors.TextPrimary,
            cursorColor = IdealPlayerColors.Primary,
            focusedContainerColor = IdealPlayerColors.CardBackground,
            unfocusedContainerColor = IdealPlayerColors.CardBackground,
            focusedBorderColor = IdealPlayerColors.FocusBorder,
            unfocusedBorderColor = IdealPlayerColors.CardBorder
        )
    )
}

@Composable
private fun TvChannelPreview(
    channel: Channel?,
    epgProgram: com.idealplayer.app.data.parser.EpgProgram?,
    modifier: Modifier = Modifier
) {
    if (channel == null) {
        EmptyScreen(
            message = stringResource(R.string.no_content),
            modifier = modifier
        )
        return
    }

    Surface(
        modifier = modifier,
        shape = A2Shape.large,
        color = IdealPlayerColors.CardBackground,
        border = BorderStroke(1.dp, IdealPlayerColors.CardBorder)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(A2Shape.large)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(IdealPlayerColors.SurfaceElevated, IdealPlayerColors.SurfaceSelected)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                PosterImage(
                    url = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    fallbackStyle = ArtworkFallbackStyle.Channel,
                    modifier = Modifier.fillMaxSize().padding(72.dp)
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                    shape = A2Shape.small,
                    color = IdealPlayerColors.Primary
                ) {
                    Text(
                        text = stringResource(R.string.player_content_live),
                        color = IdealPlayerColors.TextOnPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = epgProgram?.title ?: channel.name,
                style = MaterialTheme.typography.headlineMedium,
                color = IdealPlayerColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = listOfNotNull(
                    channel.name,
                    channel.groupTitle.takeIf(String::isNotBlank)
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = IdealPlayerColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            epgProgram?.let { program ->
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { program.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = IdealPlayerColors.Primary,
                    trackColor = IdealPlayerColors.CardBorder
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ChannelListItem(
    channel: Channel,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    epgProgram: com.idealplayer.app.data.parser.EpgProgram? = null,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onFocused: () -> Unit = {}
) {
    val interactionSource = remember(channel.id) {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val itemShape = RoundedCornerShape(12.dp)
    LaunchedEffect(isFocused, channel.id) {
        if (isFocused) onFocused()
    }

    val targetBackgroundColor = when {
        isTv && isFocused -> IdealPlayerColors.SurfaceFocus
        isTv && selected -> IdealPlayerColors.SurfaceSelected
        else -> IdealPlayerColors.CardBackground
    }
    val backgroundColor by animateColorAsState(
        targetBackgroundColor,
        tween(A2Motion.FastMillis),
        label = "channelBg"
    )

    val targetContentColor = IdealPlayerColors.TextPrimary
    val contentColor by animateColorAsState(
        targetContentColor,
        tween(A2Motion.FastMillis),
        label = "channelText"
    )

    val targetSecondaryColor = if (isTv) IdealPlayerColors.TextSecondary else IdealPlayerColors.TextTertiary
    val secondaryContentColor by animateColorAsState(
        targetSecondaryColor,
        tween(A2Motion.FastMillis),
        label = "channelSecondaryText"
    )

    val targetScale = if (isTv && isFocused) A2Motion.FocusScale else 1f
    val scale by animateFloatAsState(
        targetScale,
        tween(A2Motion.StandardMillis),
        label = "channelScale"
    )

    val borderWidth = when {
        isTv && isFocused -> 4.dp
        isTv && selected -> 2.dp
        else -> 1.dp
    }
    val borderColor = when {
        isTv && isFocused -> IdealPlayerColors.FocusBorder
        isTv && selected -> IdealPlayerColors.SelectedBorder
        else -> IdealPlayerColors.CardBorder
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (isTv) 96.dp else 72.dp)
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.shape = itemShape
                clip = false
            }
            .clip(itemShape)
            .background(backgroundColor, itemShape)
            .border(borderWidth, borderColor, itemShape)
            .clickable(
                interactionSource = interactionSource,
                indication = if (isTv) null else androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = if (isTv) 16.dp else 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterImage(
            url = channel.logoUrl,
            contentDescription = channel.name,
            contentScale = ContentScale.Fit,
            fallbackStyle = ArtworkFallbackStyle.Channel,
            modifier = Modifier
                .size(if (isTv) 60.dp else 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isTv && isFocused) IdealPlayerColors.Primary.copy(alpha = 0.14f) else IdealPlayerColors.Surface)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(channel.name, style = if (isTv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = if (isTv) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(
                text = epgProgram?.title ?: stringResource(R.string.epg_no_data),
                style = if (isTv) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall,
                color = secondaryContentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            epgProgram?.let { program ->
                LinearProgressIndicator(
                    progress = { program.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(2.dp).padding(top = 2.dp),
                    color = IdealPlayerColors.Primary,
                    trackColor = if (isTv && isFocused) IdealPlayerColors.CardBorder else IdealPlayerColors.Surface
                )
            }
        }
        IconButton(
            onClick = onFavoriteToggle,
            modifier = Modifier
                .size(if (isTv) 56.dp else 48.dp)
                .then(if (isTv) Modifier.focusProperties { canFocus = false } else Modifier)
        ) {
            Icon(
                imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(
                    if (channel.isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites
                ),
                tint = if (channel.isFavorite) IdealPlayerColors.Primary else if (isTv && isFocused) IdealPlayerColors.TextSecondary else IdealPlayerColors.TextTertiary,
                modifier = Modifier.size(if (isTv) 28.dp else 22.dp)
            )
        }
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.action_play),
            tint = IdealPlayerColors.Primary,
            modifier = Modifier.size(if (isTv) 32.dp else 24.dp)
        )
    }
}

private data class TvCategoryRailItem(
    val id: String,
    val title: String,
    val group: String?
)

private fun FocusRequester.requestFocusSafely() {
    runCatching { requestFocus() }
}
