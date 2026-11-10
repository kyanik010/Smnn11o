package com.idealplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import com.idealplayer.app.core.designsystem.theme.IdealPlayerTheme
import com.idealplayer.app.ui.navigation.Routes
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvDrawerFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rightDpadMovesFromDrawerToContentWithoutEscapingTheActivity() {
        composeRule.setContent {
            IdealPlayerTheme(isTv = true) {
                TvDrawerLayout(
                    isExpanded = true,
                    selectedRoute = Routes.HOME,
                    onToggle = {},
                    onNavigate = {}
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .focusable()
                            .testTag("tv_drawer_content_target")
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_drawer_item_${Routes.HOME}").assertIsFocused()

        composeRule.onNodeWithTag("tv_drawer_item_${Routes.HOME}").performKeyInput {
            keyDown(Key.DirectionRight)
        }

        composeRule.onNodeWithTag("tv_drawer_content_target").assertIsFocused()
    }

    @Test
    fun verticalDpadTraversesOffscreenDrawerItemsWithoutDetachedRequesterCrash() {
        var transitionsBetweenHomeAndExit = 0
        composeRule.setContent {
            IdealPlayerTheme(isTv = true) {
                val regularDrawerItemCount = tvDrawerItems.size
                SideEffect {
                    transitionsBetweenHomeAndExit = regularDrawerItemCount
                }
                Box(modifier = Modifier.width(900.dp).height(420.dp)) {
                    TvDrawerLayout(
                        isExpanded = true,
                        selectedRoute = Routes.HOME,
                        onToggle = {},
                        onNavigate = {}
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .focusable()
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val homeTag = "tv_drawer_item_${Routes.HOME}"
        val exitTag = "tv_drawer_item_${Routes.EXIT}"
        composeRule.onNodeWithTag(homeTag).assertIsFocused()

        composeRule.onNodeWithTag(homeTag).performKeyInput {
            repeat(transitionsBetweenHomeAndExit) {
                keyDown(Key.DirectionDown)
                keyUp(Key.DirectionDown)
            }
        }
        composeRule.onNodeWithTag(exitTag).assertIsFocused()

        composeRule.onNodeWithTag(exitTag).performKeyInput {
            repeat(transitionsBetweenHomeAndExit) {
                keyDown(Key.DirectionUp)
                keyUp(Key.DirectionUp)
            }
        }
        composeRule.onNodeWithTag(homeTag).assertIsFocused()
    }

    @Test
    fun selectedOffscreenDrawerRouteIsScrolledBeforeProgrammaticFocus() {
        composeRule.setContent {
            IdealPlayerTheme(isTv = true) {
                Box(modifier = Modifier.width(900.dp).height(420.dp)) {
                    TvDrawerLayout(
                        isExpanded = true,
                        selectedRoute = Routes.SETTINGS,
                        onToggle = {},
                        onNavigate = {}
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .focusable()
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_drawer_item_${Routes.SETTINGS}").assertIsFocused()
    }
}
