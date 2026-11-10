package com.idealplayer.app.ui.navigation.tv

import com.google.common.truth.Truth.assertThat
import com.idealplayer.app.ui.navigation.Routes
import org.junit.Test

class TvNavigationTargetTest {

    @Test
    fun `exit playlist action resolves to playlists`() {
        assertThat(resolveTvTopLevelDestination(Routes.EXIT)).isEqualTo(Routes.PLAYLISTS)
    }

    @Test
    fun `every TV top level destination is accepted`() {
        Routes.TV_TOP_LEVEL_DESTINATIONS.forEach { route ->
            assertThat(resolveTvTopLevelDestination(route)).isEqualTo(route)
        }
    }

    @Test
    fun `dynamic and unknown routes are rejected for drawer navigation`() {
        assertThat(resolveTvTopLevelDestination(Routes.DETAIL)).isNull()
        assertThat(resolveTvTopLevelDestination(Routes.PLAYER)).isNull()
        assertThat(resolveTvTopLevelDestination("not_a_route")).isNull()
        assertThat(resolveTvTopLevelDestination("")).isNull()
    }

    @Test
    fun `duplicate destination does not navigate`() {
        assertThat(
            shouldNavigateToTvTopLevel(
                requestedRoute = Routes.HOME,
                currentDestinationRoute = Routes.HOME
            )
        ).isFalse()
        assertThat(
            shouldNavigateToTvTopLevel(
                requestedRoute = Routes.EXIT,
                currentDestinationRoute = Routes.PLAYLISTS
            )
        ).isFalse()
    }

    @Test
    fun `valid destination different from current is navigable`() {
        assertThat(
            shouldNavigateToTvTopLevel(
                requestedRoute = Routes.MOVIES,
                currentDestinationRoute = Routes.HOME
            )
        ).isTrue()
        assertThat(
            shouldNavigateToTvTopLevel(
                requestedRoute = "not_a_route",
                currentDestinationRoute = Routes.HOME
            )
        ).isFalse()
    }
}
