package com.idealplayer.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.idealplayer.app.core.designsystem.theme.IdealPlayerTheme
import com.idealplayer.app.ui.navigation.Routes
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class A2NavigationShellTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun phoneUsesExactlyFiveA2DestinationsAndFavoritesNavigates() {
        var navigatedRoute: String? = null

        composeRule.setContent {
            WithTestConfiguration(
                smallestWidthDp = 360,
                widthDp = 360,
                heightDp = 800
            ) {
                IdealPlayerTheme {
                    MobileScaffoldLayout(
                        selectedRoute = Routes.MOVIES,
                        onNavigate = { navigatedRoute = it }
                    ) { _: PaddingValues ->
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }

        val expectedRoutes = listOf(
            Routes.HOME,
            Routes.LIVE_TV,
            Routes.MOVIES,
            Routes.SERIES,
            Routes.FAVORITES
        )
        expectedRoutes.forEach { route ->
            composeRule.onNodeWithTag(mobileTag(route)).assertExists()
        }
        composeRule.onAllNodes(isSelectable()).assertCountEquals(expectedRoutes.size)
        composeRule.onNodeWithTag(mobileTag(Routes.SEARCH)).assertDoesNotExist()
        composeRule.onNodeWithTag(mobileTag(Routes.SETTINGS)).assertDoesNotExist()
        composeRule.onNodeWithTag(mobileTag(Routes.MOVIES)).assertIsSelected()

        composeRule.onNodeWithTag(mobileTag(Routes.FAVORITES)).performClick()
        composeRule.runOnIdle {
            assertEquals(Routes.FAVORITES, navigatedRoute)
        }
    }

    @Test
    fun tabletUsesTheEightItemA2RailAndPushesContentBy80Dp() {
        val densityCapture = DensityCapture()

        composeRule.setContent {
            WithTestConfiguration(
                smallestWidthDp = 600,
                widthDp = 1280,
                heightDp = 800
            ) {
                IdealPlayerTheme {
                    CaptureDensity(densityCapture)
                    MobileScaffoldLayout(
                        selectedRoute = Routes.TV_GUIDE,
                        onNavigate = {}
                    ) { _: PaddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag(TABLET_CONTENT_TAG)
                        )
                    }
                }
            }
        }

        val expectedRoutes = listOf(
            Routes.HOME,
            Routes.LIVE_TV,
            Routes.MOVIES,
            Routes.SERIES,
            Routes.TV_GUIDE,
            Routes.SEARCH,
            Routes.FAVORITES,
            Routes.SETTINGS
        )
        expectedRoutes.forEach { route ->
            composeRule.onNodeWithTag(tabletTag(route)).assertExists()
        }
        composeRule.onAllNodes(isSelectable()).assertCountEquals(expectedRoutes.size)
        composeRule.onNodeWithTag(tabletTag(Routes.TV_GUIDE)).assertIsSelected()
        composeRule.onNodeWithTag(mobileTag(Routes.HOME)).assertDoesNotExist()

        assertLeftPositionDp(
            tag = TABLET_CONTENT_TAG,
            expectedDp = 80f,
            densityCapture = densityCapture
        )
    }

    @Test
    fun tvDrawerPushesContentByDrawerWidthPlus24DpGutter() {
        val isExpanded = mutableStateOf(false)
        val densityCapture = DensityCapture()

        composeRule.setContent {
            WithTestConfiguration(
                smallestWidthDp = 540,
                widthDp = 960,
                heightDp = 540
            ) {
                IdealPlayerTheme(isTv = true) {
                    CaptureDensity(densityCapture)
                    TvDrawerLayout(
                        isExpanded = isExpanded.value,
                        selectedRoute = Routes.HOME,
                        onToggle = {},
                        onNavigate = {}
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag(TV_CONTENT_TAG)
                        )
                    }
                }
            }
        }

        assertLeftPositionDp(
            tag = TV_CONTENT_TAG,
            expectedDp = 120f,
            densityCapture = densityCapture
        )

        composeRule.runOnIdle { isExpanded.value = true }
        assertLeftPositionDp(
            tag = TV_CONTENT_TAG,
            expectedDp = 256f,
            densityCapture = densityCapture
        )
    }

    private fun assertLeftPositionDp(
        tag: String,
        expectedDp: Float,
        densityCapture: DensityCapture,
        toleranceDp: Float = 1.5f
    ) {
        composeRule.waitUntil(timeoutMillis = 3_000) {
            runCatching {
                abs(leftPositionDp(tag, densityCapture.value) - expectedDp) <= toleranceDp
            }.getOrDefault(false)
        }

        assertEquals(
            expectedDp.toDouble(),
            leftPositionDp(tag, densityCapture.value).toDouble(),
            toleranceDp.toDouble()
        )
    }

    private fun leftPositionDp(tag: String, density: Float): Float {
        return composeRule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .boundsInRoot
            .left / density
    }

    private companion object {
        const val TABLET_CONTENT_TAG = "a2_tablet_content"
        const val TV_CONTENT_TAG = "a2_tv_drawer_content"

        fun mobileTag(route: String) = "mobile_bottom_nav_$route"

        fun tabletTag(route: String) = "tablet_rail_$route"
    }
}

@Composable
private fun WithTestConfiguration(
    smallestWidthDp: Int,
    widthDp: Int,
    heightDp: Int,
    content: @Composable () -> Unit
) {
    val configuration = Configuration(LocalConfiguration.current).apply {
        smallestScreenWidthDp = smallestWidthDp
        screenWidthDp = widthDp
        screenHeightDp = heightDp
    }
    CompositionLocalProvider(
        LocalConfiguration provides configuration,
        content = content
    )
}

@Composable
private fun CaptureDensity(capture: DensityCapture) {
    val density = LocalDensity.current.density
    SideEffect { capture.value = density }
}

private class DensityCapture {
    @Volatile
    var value: Float = 1f
}
