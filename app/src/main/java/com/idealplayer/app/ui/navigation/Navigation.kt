package com.idealplayer.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.idealplayer.app.BuildConfig
import com.idealplayer.app.core.common.DeviceUiMode
import com.idealplayer.app.ui.navigation.mobile.MobileNavGraph
import com.idealplayer.app.ui.navigation.tv.TvNavGraph
import com.idealplayer.app.ui.update.AppUpdateHost

object Routes {
    const val PLAYLISTS = "playlists"
    const val ONBOARDING = PLAYLISTS
    const val HOME = "home"
    const val LIVE_TV = "live_tv"
    const val TV_GUIDE = "tv_guide"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val SEARCH = "search"
    const val CONTINUE_WATCHING = "continue_watching"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val EXIT = "exit"
    const val PLAYER = "player?url={url}&title={title}&contentId={contentId}&contentType={contentType}&startPos={startPos}&group={group}"
    const val DETAIL = "detail/{contentId}/{contentType}"

    fun player(
        url: String,
        title: String = "",
        contentId: Long = 0,
        contentType: String = "",
        startPos: Long = 0,
        group: String = ""
    ): String {
        val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        val encodedGroup = java.net.URLEncoder.encode(group, "UTF-8")
        return "player?url=$encodedUrl&title=$encodedTitle&contentId=$contentId&contentType=$contentType&startPos=$startPos&group=$encodedGroup"
    }

    fun detail(contentId: Long, contentType: String): String = "detail/$contentId/$contentType"

    val MOBILE_MAIN_TABS = setOf(HOME, LIVE_TV, MOVIES, SERIES, FAVORITES)
    val TABLET_MAIN_TABS = setOf(
        HOME,
        LIVE_TV,
        MOVIES,
        SERIES,
        TV_GUIDE,
        SEARCH,
        FAVORITES,
        SETTINGS
    )
    val TV_TOP_LEVEL_DESTINATIONS = setOf(
        HOME,
        SEARCH,
        LIVE_TV,
        TV_GUIDE,
        MOVIES,
        SERIES,
        CONTINUE_WATCHING,
        FAVORITES,
        PLAYLISTS,
        SETTINGS
    )
}

@Composable
fun IdealPlayerNavHost(
    deviceUiMode: DeviceUiMode,
    navController: NavHostController = rememberNavController()
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (deviceUiMode.isTv) {
            TvNavGraph(navController = navController)
        } else {
            MobileNavGraph(navController = navController)
        }
        if (BuildConfig.SELF_HOSTED_UPDATES_ENABLED) {
            AppUpdateHost(isTv = deviceUiMode.isTv)
        }
    }
}
