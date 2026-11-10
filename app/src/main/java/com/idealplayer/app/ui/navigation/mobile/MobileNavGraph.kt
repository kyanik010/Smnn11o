package com.idealplayer.app.ui.navigation.mobile

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.idealplayer.app.ui.components.MobileScaffoldLayout
import com.idealplayer.app.ui.favorites.FavoritesScreen
import com.idealplayer.app.ui.mobile.MobileDetailScreen
import com.idealplayer.app.ui.mobile.MobileHomeScreen
import com.idealplayer.app.ui.mobile.MobileLiveTvScreen
import com.idealplayer.app.ui.mobile.MobileTvGuideScreen
import com.idealplayer.app.ui.mobile.MobileMoviesScreen
import com.idealplayer.app.ui.mobile.MobilePlayerScreen
import com.idealplayer.app.ui.mobile.MobilePlaylistScreen
import com.idealplayer.app.ui.mobile.MobileSearchScreen
import com.idealplayer.app.ui.mobile.MobileSeriesScreen
import com.idealplayer.app.ui.mobile.MobileSettingsScreen
import com.idealplayer.app.ui.navigation.Routes
import com.idealplayer.app.ui.navigation.startup.StartupRoute
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import timber.log.Timber

private const val MOBILE_STARTUP = "mobile_startup"
private const val MOBILE_NAV_LOG_TAG = "MobileNavGraph"

@Composable
fun MobileNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route ?: MOBILE_STARTUP
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val shellRoutes = if (isTablet) Routes.TABLET_MAIN_TABS else Routes.MOBILE_MAIN_TABS
    val showNavigationShell = currentRoute in shellRoutes

    val navigateToTopLevel: (String) -> Unit = { route ->
        if (route == Routes.EXIT) {
            navController.navigate(Routes.PLAYLISTS) {
                popUpTo(0) { inclusive = true }
            }
        } else {
            navController.navigate(route) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    if (showNavigationShell) {
        MobileScaffoldLayout(
            selectedRoute = currentRoute,
            onNavigate = navigateToTopLevel
        ) { paddingValues ->
            MobileNavHostContent(
                navController = navController,
                modifier = modifier.padding(paddingValues),
                onNavigate = navigateToTopLevel
            )
        }
    } else {
        MobileNavHostContent(
            navController = navController,
            modifier = modifier,
            onNavigate = navigateToTopLevel
        )
    }
}

@Composable
private fun MobileNavHostContent(
    navController: NavHostController,
    modifier: Modifier,
    onNavigate: (String) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = MOBILE_STARTUP,
        modifier = modifier
    ) {
        composable(MOBILE_STARTUP) {
            StartupRoute(
                onReady = { route ->
                    navController.navigate(route) {
                        popUpTo(MOBILE_STARTUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PLAYLISTS) {
            MobilePlaylistScreen(
                onPlaylistSelected = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PLAYLISTS) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            MobileHomeScreen(
                onNavigate = onNavigate,
                onContentClick = { id, type ->
                    navController.navigate(Routes.detail(id, type))
                },
                onPlayContent = { url, title, id, type, startPos ->
                    navController.navigate(Routes.player(url, title, id, type, startPos))
                }
            )
        }

        composable(Routes.LIVE_TV) {
            MobileLiveTvScreen(
                onNavigate = onNavigate,
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
            MobileTvGuideScreen(
                onNavigate = onNavigate,
                onPlayChannel = { url, title, id, group ->
                    navController.navigate(
                        Routes.player(url, title, id, "LIVE", group = group)
                    )
                }
            )
        }

        composable(Routes.MOVIES) {
            MobileMoviesScreen(
                onNavigate = onNavigate,
                onMovieClick = { id ->
                    navController.navigate(Routes.detail(id, "MOVIE"))
                }
            )
        }

        composable(Routes.SERIES) {
            MobileSeriesScreen(
                onNavigate = onNavigate,
                onSeriesClick = { id ->
                    navController.navigate(Routes.detail(id, "SERIES"))
                }
            )
        }

        composable(Routes.SEARCH) {
            MobileSearchScreen(
                onNavigate = onNavigate,
                onContentClick = { id, type ->
                    navController.navigate(Routes.detail(id, type))
                },
                onPlayContent = { url, title, id, type ->
                    navController.navigate(Routes.player(url, title, id, type))
                }
            )
        }

        composable(Routes.FAVORITES) {
            FavoritesScreen(
                onMovieClick = { id ->
                    navController.navigate(Routes.detail(id, "MOVIE"))
                },
                onSeriesClick = { id ->
                    navController.navigate(Routes.detail(id, "SERIES"))
                },
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
            MobileSettingsScreen(
                onNavigate = onNavigate,
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
            MobileDetailScreen(
                contentId = backStackEntry.arguments?.getLong("contentId") ?: 0L,
                contentType = backStackEntry.arguments?.getString("contentType").orEmpty(),
                onBack = { navController.popBackStack() },
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
            MobilePlayerScreen(
                url = decodeMobileNavArg(backStackEntry.arguments?.getString("url"), "url"),
                title = decodeMobileNavArg(backStackEntry.arguments?.getString("title"), "title"),
                contentId = backStackEntry.arguments?.getLong("contentId") ?: 0L,
                contentType = backStackEntry.arguments?.getString("contentType").orEmpty(),
                startPosition = backStackEntry.arguments?.getLong("startPos") ?: 0L,
                groupContext = decodeMobileNavArg(backStackEntry.arguments?.getString("group"), "group"),
                onBack = { navController.popBackStack() }
            )
        }
    }
}

private fun decodeMobileNavArg(value: String?, argumentName: String): String {
    val rawValue = value.orEmpty()
    if (rawValue.isBlank()) {
        return ""
    }

    return runCatching {
        URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name())
    }.getOrElse { error ->
        Timber.tag(MOBILE_NAV_LOG_TAG).w(error, "Failed to decode mobile nav argument: %s", argumentName)
        rawValue
    }
}
