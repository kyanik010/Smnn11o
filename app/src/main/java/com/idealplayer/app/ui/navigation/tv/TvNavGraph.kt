package com.idealplayer.app.ui.navigation.tv

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.idealplayer.app.R
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.ui.navigation.Routes
import com.idealplayer.app.ui.navigation.startup.StartupRoute
import com.idealplayer.app.ui.tv.TvContinueWatchingScreen
import com.idealplayer.app.ui.tv.TvDetailScreen
import com.idealplayer.app.ui.tv.TvFavoritesScreen
import com.idealplayer.app.ui.tv.TvHomeScreen
import com.idealplayer.app.ui.tv.TvLiveTvScreen
import com.idealplayer.app.ui.tv.TvGuideRouteScreen
import com.idealplayer.app.ui.tv.TvMoviesScreen
import com.idealplayer.app.ui.tv.TvPlayerScreen
import com.idealplayer.app.ui.tv.TvPlaylistsScreen
import com.idealplayer.app.ui.tv.TvSearchScreen
import com.idealplayer.app.ui.tv.TvSeriesScreen
import com.idealplayer.app.ui.tv.TvSettingsScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.android.awaitFrame
import timber.log.Timber

private const val TV_STARTUP = "tv_startup"
private const val TV_NAV_LOG_TAG = "TvNavGraph"

/**
 * Drawer actions are only allowed to target static TV destinations. Treating an arbitrary string
 * as a route lets a stale focus callback or a duplicated key event create an invalid navigation
 * request. The drawer's "exit playlist" action intentionally resolves to the playlist screen.
 */
internal fun resolveTvTopLevelDestination(route: String): String? = when (route) {
    Routes.EXIT -> Routes.PLAYLISTS
    in Routes.TV_TOP_LEVEL_DESTINATIONS -> route
    else -> null
}

internal fun shouldNavigateToTvTopLevel(
    requestedRoute: String,
    currentDestinationRoute: String?
): Boolean = resolveTvTopLevelDestination(requestedRoute)
    ?.let { destination -> destination != currentDestinationRoute }
    ?: false

@Composable
fun TvNavGraph(
    navController: NavHostController
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isAtAppExitPoint = currentRoute in Routes.TV_TOP_LEVEL_DESTINATIONS &&
        navController.previousBackStackEntry == null
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    val navigateToTopLevel: (String) -> Unit = { requestedRoute ->
        val destination = resolveTvTopLevelDestination(requestedRoute)
        when {
            destination == null -> {
                Timber.tag(TV_NAV_LOG_TAG).w(
                    "Ignoring invalid TV top-level navigation target: %s",
                    requestedRoute
                )
            }

            !shouldNavigateToTvTopLevel(requestedRoute, currentRoute) -> {
                Timber.tag(TV_NAV_LOG_TAG).d(
                    "Ignoring duplicate TV top-level navigation target: %s",
                    destination
                )
            }

            destination == Routes.PLAYLISTS -> {
                navController.navigate(Routes.PLAYLISTS) {
                    popUpTo(0) { inclusive = true }
                }
            }

            else -> {
                navController.navigate(destination) {
                    popUpTo(Routes.HOME) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val popBackOrResetToHome: () -> Unit = {
        if (!navController.popBackStack()) {
            navController.navigate(Routes.HOME) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(isAtAppExitPoint) {
        if (!isAtAppExitPoint && showExitDialog) {
            showExitDialog = false
        }
    }

    BackHandler(enabled = isAtAppExitPoint && !showExitDialog) {
        showExitDialog = true
    }

    NavHost(
        navController = navController,
        startDestination = TV_STARTUP
    ) {
        composable(TV_STARTUP) {
            StartupRoute(
                onReady = { route ->
                    navController.navigate(route) {
                        popUpTo(TV_STARTUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PLAYLISTS) {
            TvPlaylistsScreen(
                onNavigate = navigateToTopLevel,
                onPlaylistSelected = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PLAYLISTS) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            TvHomeScreen(
                onNavigate = navigateToTopLevel,
                onContentClick = { id, type ->
                    navController.navigate(Routes.detail(id, type))
                },
                onPlayContent = { url, title, id, type, startPos ->
                    navController.navigate(Routes.player(url, title, id, type, startPos))
                }
            )
        }

        composable(Routes.LIVE_TV) {
            TvLiveTvScreen(
                onNavigate = navigateToTopLevel,
                onPlayChannel = { url, title, id, group ->
                    navController.navigate(
                        Routes.player(
                            url = url,
                            title = title,
                            contentId = id,
                            contentType = "LIVE",
                            group = group.orEmpty()
                        )
                    )
                }
            )
        }

        composable(Routes.TV_GUIDE) {
            TvGuideRouteScreen(
                onNavigate = navigateToTopLevel,
                onPlayChannel = { url, title, id, group ->
                    navController.navigate(
                        Routes.player(url, title, id, "LIVE", group = group)
                    )
                }
            )
        }

        composable(Routes.MOVIES) {
            TvMoviesScreen(
                onNavigate = navigateToTopLevel,
                onMovieClick = { id ->
                    navController.navigate(Routes.detail(id, "MOVIE"))
                }
            )
        }

        composable(Routes.SERIES) {
            TvSeriesScreen(
                onNavigate = navigateToTopLevel,
                onSeriesClick = { id ->
                    navController.navigate(Routes.detail(id, "SERIES"))
                }
            )
        }

        composable(Routes.SEARCH) {
            TvSearchScreen(
                onNavigate = navigateToTopLevel,
                onContentClick = { id, type ->
                    navController.navigate(Routes.detail(id, type))
                },
                onPlayContent = { url, title, id, type ->
                    navController.navigate(Routes.player(url, title, id, type))
                }
            )
        }

        composable(Routes.CONTINUE_WATCHING) {
            TvContinueWatchingScreen(
                onNavigate = navigateToTopLevel,
                onResume = { item ->
                    navController.navigate(
                        Routes.player(
                            url = item.streamUrl,
                            title = item.title,
                            contentId = item.contentId,
                            contentType = item.contentType.name,
                            startPos = item.position
                        )
                    )
                },
                onOpenDetail = { id, type ->
                    navController.navigate(Routes.detail(id, type))
                }
            )
        }

        composable(Routes.FAVORITES) {
            TvFavoritesScreen(
                onNavigate = navigateToTopLevel,
                onMovieClick = { id -> navController.navigate(Routes.detail(id, "MOVIE")) },
                onSeriesClick = { id -> navController.navigate(Routes.detail(id, "SERIES")) },
                onPlayChannel = { url, title, id, group ->
                    navController.navigate(
                        Routes.player(
                            url = url,
                            title = title,
                            contentId = id,
                            contentType = "LIVE",
                            group = group.orEmpty()
                        )
                    )
                }
            )
        }

        composable(Routes.SETTINGS) {
            TvSettingsScreen(
                onNavigate = navigateToTopLevel,
                onBackToPlaylists = {
                    navController.navigate(Routes.PLAYLISTS) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("contentId") { type = NavType.LongType },
                navArgument("contentType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            TvDetailScreen(
                contentId = backStackEntry.arguments?.getLong("contentId") ?: 0L,
                contentType = backStackEntry.arguments?.getString("contentType").orEmpty(),
                onBack = popBackOrResetToHome,
                onPlay = { url, title, id, type, startPos ->
                    navController.navigate(Routes.player(url, title, id, type, startPos))
                }
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("contentId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("contentType") { type = NavType.StringType; defaultValue = "" },
                navArgument("startPos") { type = NavType.LongType; defaultValue = 0L },
                navArgument("group") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val decodedUrl = decodeTvNavArg(backStackEntry.arguments?.getString("url"), "url")
            val decodedTitle = decodeTvNavArg(backStackEntry.arguments?.getString("title"), "title")
            val decodedGroup = decodeTvNavArg(backStackEntry.arguments?.getString("group"), "group")

            TvPlayerScreen(
                url = decodedUrl,
                title = decodedTitle,
                contentId = backStackEntry.arguments?.getLong("contentId") ?: 0L,
                contentType = backStackEntry.arguments?.getString("contentType").orEmpty(),
                startPosition = backStackEntry.arguments?.getLong("startPos") ?: 0L,
                groupContext = decodedGroup,
                onBack = {
                    popBackOrResetToHome()
                }
            )
        }
    }

    if (showExitDialog) {
        TvExitConfirmationDialog(
            onDismiss = { showExitDialog = false },
            onConfirmExit = {
                showExitDialog = false
                activity?.finishAffinity()
            }
        )
    }
}

private fun decodeTvNavArg(value: String?, argumentName: String): String {
    val rawValue = value.orEmpty()
    if (rawValue.isBlank()) {
        return ""
    }

    return runCatching {
        URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name())
    }.getOrElse { error ->
        Timber.tag(TV_NAV_LOG_TAG).w(error, "Failed to decode TV nav argument: %s", argumentName)
        rawValue
    }
}

@Composable
private fun TvExitConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirmExit: () -> Unit
) {
    val cancelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(cancelFocusRequester) {
        // The dialog's focus target is attached during apply; wait for the next frame before
        // requesting it so a fast route change cannot hit an uninitialized FocusRequester.
        awaitFrame()
        runCatching { cancelFocusRequester.requestFocus() }
            .onFailure { error ->
                Timber.tag(TV_NAV_LOG_TAG).w(error, "Unable to focus TV exit dialog cancel button")
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 720.dp),
            shape = RoundedCornerShape(28.dp),
            color = IdealPlayerColors.Surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                IdealPlayerColors.CardBorder.copy(alpha = 0.9f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 30.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = IdealPlayerColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.tv_exit_confirmation_message),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvExitDialogButton(
                        text = stringResource(R.string.cancel),
                        focusRequester = cancelFocusRequester,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )
                    TvExitDialogButton(
                        text = stringResource(R.string.tv_exit_action),
                        modifier = Modifier.weight(1f),
                        isDestructive = true,
                        onClick = onConfirmExit
                    )
                }
            }
        }
    }
}

@Composable
private fun TvExitDialogButton(
    text: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(160),
        label = "tvExitDialogButtonScale"
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused && isDestructive -> IdealPlayerColors.Error.copy(alpha = 0.28f)
            isFocused -> IdealPlayerColors.Primary.copy(alpha = 0.24f)
            isDestructive -> IdealPlayerColors.Error.copy(alpha = 0.14f)
            else -> IdealPlayerColors.SurfaceVariant.copy(alpha = 0.68f)
        },
        animationSpec = tween(160),
        label = "tvExitDialogButtonBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused && isDestructive -> IdealPlayerColors.Error
            isFocused -> IdealPlayerColors.FocusBorder
            isDestructive -> IdealPlayerColors.Error.copy(alpha = 0.6f)
            else -> IdealPlayerColors.CardBorder.copy(alpha = 0.8f)
        },
        animationSpec = tween(160),
        label = "tvExitDialogButtonBorder"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isDestructive -> IdealPlayerColors.Error
            else -> IdealPlayerColors.TextPrimary
        },
        animationSpec = tween(160),
        label = "tvExitDialogButtonText"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(backgroundColor)
            .border(2.dp, borderColor, shape)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}
