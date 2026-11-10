package com.idealplayer.app.ui.mobile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.idealplayer.app.R
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.ui.components.a2.A2StatusSurface
import com.idealplayer.app.ui.components.a2.A2StatusType
import com.idealplayer.app.ui.details.DetailScreen
import com.idealplayer.app.ui.home.HomeScreen
import com.idealplayer.app.ui.guide.TvGuideScreen
import com.idealplayer.app.ui.live.LiveTvScreen
import com.idealplayer.app.ui.movies.MoviesScreen
import com.idealplayer.app.ui.onboarding.OnboardingScreen
import com.idealplayer.app.ui.player.PlayerScreen
import com.idealplayer.app.ui.search.SearchScreen
import com.idealplayer.app.ui.series.SeriesScreen
import com.idealplayer.app.ui.settings.SettingsScreen

@Composable
fun MobilePlaylistScreen(
    onPlaylistSelected: () -> Unit
) {
    OnboardingScreen(
        isTv = false,
        onPlaylistSelected = onPlaylistSelected
    )
}

@Composable
fun MobileHomeScreen(
    onNavigate: (String) -> Unit,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String, Long) -> Unit
) {
    HomeScreen(
        isTv = false,
        onNavigate = onNavigate,
        onContentClick = onContentClick,
        onPlayContent = onPlayContent
    )
}

// ─── Tablet Split-View Live TV ────────────────────────────────────────────────
// On expanded-width screens (≥840dp) in landscape, shows channel list + player
// side-by-side. Falls back to standard navigation on phones/portrait.
@Composable
fun MobileLiveTvScreen(
    onNavigate: (String) -> Unit,
    onPlayChannel: (String, String, Long, String?) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isTabletLandscape = screenWidthDp >= 840 && isLandscape

    if (isTabletLandscape) {
        // Split view — capture channel selection inside; navigate only for non-channel content
        data class InlineChannel(val url: String, val title: String, val id: Long, val group: String?)

        var inlineChannel by remember { mutableStateOf<InlineChannel?>(null) }

        Row(modifier = Modifier.fillMaxSize()) {
            // Left: channel list (35%)
            Box(modifier = Modifier.weight(0.35f)) {
                LiveTvScreen(
                    isTv = false,
                    onNavigate = onNavigate,
                    onPlayChannel = { url, title, id, group ->
                        // Play inline instead of navigating away
                        inlineChannel = InlineChannel(url, title, id, group)
                    }
                )
            }
            // Right: inline player (65%)
            Box(modifier = Modifier.weight(0.65f)) {
                val ch = inlineChannel
                if (ch != null) {
                    PlayerScreen(
                        url = ch.url,
                        title = ch.title,
                        contentId = ch.id,
                        contentType = "LIVE",
                        startPosition = 0L,
                        groupContext = ch.group ?: "",
                        isTv = false,
                        onBack = { inlineChannel = null }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        A2StatusSurface(
                            type = A2StatusType.Empty,
                            title = stringResource(R.string.a2_tablet_live_empty_title),
                            message = stringResource(R.string.a2_tablet_live_empty_message),
                            modifier = Modifier
                                .padding(A2Spacing.lg)
                                .widthIn(max = 440.dp)
                        )
                    }
                }
            }
        }
    } else {
        LiveTvScreen(
            isTv = false,
            onNavigate = onNavigate,
            onPlayChannel = onPlayChannel
        )
    }
}

@Composable
fun MobileTvGuideScreen(
    onNavigate: (String) -> Unit,
    onPlayChannel: (String, String, Long, String) -> Unit
) {
    TvGuideScreen(
        isTv = false,
        onNavigate = onNavigate,
        onPlayChannel = onPlayChannel
    )
}


@Composable
fun MobileMoviesScreen(
    onNavigate: (String) -> Unit,
    onMovieClick: (Long) -> Unit
) {
    MoviesScreen(
        isTv = false,
        onNavigate = onNavigate,
        onMovieClick = onMovieClick
    )
}

@Composable
fun MobileSeriesScreen(
    onNavigate: (String) -> Unit,
    onSeriesClick: (Long) -> Unit
) {
    SeriesScreen(
        isTv = false,
        onNavigate = onNavigate,
        onSeriesClick = onSeriesClick
    )
}

@Composable
fun MobileSearchScreen(
    onNavigate: (String) -> Unit,
    onContentClick: (Long, String) -> Unit,
    onPlayContent: (String, String, Long, String) -> Unit
) {
    SearchScreen(
        isTv = false,
        onNavigate = onNavigate,
        onContentClick = onContentClick,
        onPlayContent = onPlayContent
    )
}

@Composable
fun MobileSettingsScreen(
    onNavigate: (String) -> Unit,
    onBackToPlaylists: () -> Unit
) {
    SettingsScreen(
        isTv = false,
        onNavigate = onNavigate,
        onBackToOnboarding = onBackToPlaylists
    )
}

@Composable
fun MobileDetailScreen(
    contentId: Long,
    contentType: String,
    onBack: () -> Unit,
    onPlay: (String, String, Long, String, Long) -> Unit
) {
    DetailScreen(
        contentId = contentId,
        contentType = contentType,
        isTv = false,
        onBack = onBack,
        onPlay = onPlay
    )
}

@Composable
fun MobilePlayerScreen(
    url: String,
    title: String,
    contentId: Long,
    contentType: String,
    startPosition: Long,
    groupContext: String,
    onBack: () -> Unit
) {
    PlayerScreen(
        url = url,
        title = title,
        contentId = contentId,
        contentType = contentType,
        startPosition = startPosition,
        groupContext = groupContext,
        isTv = false,
        onBack = onBack
    )
}
