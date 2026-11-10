package com.idealplayer.app.ui.tv

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import com.idealplayer.app.core.designsystem.theme.IdealPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TvPlayerSeekBarFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun focusedSeekBarConsumesRightAndPublishesConfiguredTarget() {
        val seekRequester = FocusRequester()
        val playPauseRequester = FocusRequester()
        val currentPosition = mutableLongStateOf(30_000L)
        var targetPosition = -1L

        composeRule.setContent {
            IdealPlayerTheme(isTv = true) {
                TvSeekBar(
                    currentPosition = currentPosition.longValue,
                    duration = 120_000L,
                    bufferedPosition = 90_000L,
                    backwardStepMs = 10_000L,
                    forwardStepMs = 15_000L,
                    focusRequester = seekRequester,
                    upFocusRequester = FocusRequester(),
                    downFocusRequester = playPauseRequester,
                    onSeekTo = { targetPosition = it },
                    onFocused = {}
                )
            }
        }

        composeRule.runOnIdle { seekRequester.requestFocus() }
        composeRule.onNodeWithTag(TV_PLAYER_SEEK_BAR_TEST_TAG).assertIsFocused()
        composeRule.runOnIdle { currentPosition.longValue = 35_000L }
        composeRule.onNodeWithTag(TV_PLAYER_SEEK_BAR_TEST_TAG).performKeyInput {
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
        }

        composeRule.runOnIdle {
            assertEquals(50_000L, targetPosition)
        }
        composeRule.onNodeWithTag(TV_PLAYER_SEEK_BAR_TEST_TAG).assertIsFocused()
    }
}
