package com.idealplayer.app.ui.components.a2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.idealplayer.app.core.designsystem.theme.A2Motion
import com.idealplayer.app.core.designsystem.theme.A2Shape
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.IdealPlayerTheme
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens

/**
 * Secure, stateless numeric PIN field. Filtering and completion are UI concerns; validation,
 * lockout and persistence remain in the caller/ViewModel.
 */
@Composable
fun A2PinInput(
    pin: String,
    onPinChange: (String) -> Unit,
    contentDescription: String,
    stateDescription: String,
    modifier: Modifier = Modifier,
    digitCount: Int = 4,
    enabled: Boolean = true,
    revealDigits: Boolean = false,
    errorMessage: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    onComplete: ((String) -> Unit)? = null
) {
    A2PinInputImpl(
        pin = pin,
        onPinChange = onPinChange,
        contentDescription = contentDescription,
        stateDescription = stateDescription,
        modifier = modifier,
        digitCount = digitCount,
        enabled = enabled,
        revealDigits = revealDigits,
        errorMessage = errorMessage,
        keyboardActions = keyboardActions,
        focusRequester = focusRequester,
        onComplete = onComplete,
        previewFocused = false
    )
}

@Composable
private fun A2PinInputImpl(
    pin: String,
    onPinChange: (String) -> Unit,
    contentDescription: String,
    stateDescription: String,
    modifier: Modifier,
    digitCount: Int,
    enabled: Boolean,
    revealDigits: Boolean,
    errorMessage: String?,
    keyboardActions: KeyboardActions,
    focusRequester: FocusRequester?,
    onComplete: ((String) -> Unit)?,
    previewFocused: Boolean
) {
    require(digitCount in 4..8) { "A2PinInput supports 4 to 8 digits" }
    val dimens = LocalIdealPlayerDimens.current
    val sanitizedPin = pin.filter(Char::isDigit).take(digitCount)
    var runtimeFocused by remember { mutableStateOf(false) }
    val focused = previewFocused || runtimeFocused

    Column(modifier = modifier) {
        BasicTextField(
            value = sanitizedPin,
            onValueChange = { proposed ->
                val next = proposed.filter(Char::isDigit).take(digitCount)
                onPinChange(next)
                if (next.length == digitCount) onComplete?.invoke(next)
            },
            modifier = Modifier
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }
                )
                .onFocusChanged { runtimeFocused = it.isFocused }
                .semantics {
                    this.contentDescription = contentDescription
                    this.stateDescription = stateDescription
                    errorMessage?.let { error(it) }
                }
                .alpha(if (enabled) 1f else A2Motion.DisabledAlpha),
            enabled = enabled,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.Transparent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = keyboardActions,
            singleLine = true,
            cursorBrush = SolidColor(Color.Transparent),
            visualTransformation = if (revealDigits) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    Row(
                        modifier = Modifier.clearAndSetSemantics { },
                        horizontalArrangement = Arrangement.spacedBy(A2Spacing.xs)
                    ) {
                        repeat(digitCount) { index ->
                            val hasDigit = index < sanitizedPin.length
                            val active = focused && index == sanitizedPin.length.coerceAtMost(digitCount - 1)
                            Box(
                                modifier = Modifier
                                    .size(dimens.touchTargetMin)
                                    .background(
                                        color = when {
                                            hasDigit -> IdealPlayerColors.SurfaceSelected
                                            else -> IdealPlayerColors.SurfaceVariant
                                        },
                                        shape = A2Shape.medium
                                    )
                                    .border(
                                        width = when {
                                            active -> dimens.focusBorderWidth
                                            hasDigit -> 2.dp
                                            else -> 1.dp
                                        },
                                        color = when {
                                            errorMessage != null -> IdealPlayerColors.Error
                                            active -> IdealPlayerColors.FocusBorder
                                            hasDigit -> IdealPlayerColors.SelectedBorder
                                            else -> IdealPlayerColors.CardBorder
                                        },
                                        shape = A2Shape.medium
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when {
                                        !hasDigit -> ""
                                        revealDigits -> sanitizedPin[index].toString()
                                        else -> "•"
                                    },
                                    color = IdealPlayerColors.TextPrimary,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                    }
                    Box(Modifier.size(1.dp).alpha(0f)) {
                        innerTextField()
                    }
                }
            }
        )
        if (errorMessage != null) {
            Spacer(Modifier.height(A2Spacing.xs))
            Text(
                text = errorMessage,
                color = IdealPlayerColors.Error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Preview(name = "Mobile PIN states", group = "A2 security", widthDp = 390, heightDp = 260)
@Composable
private fun A2PinMobilePreview() {
    IdealPlayerTheme {
        Column(
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.md),
            verticalArrangement = Arrangement.spacedBy(A2Spacing.lg)
        ) {
            A2PinInput(
                pin = "27",
                onPinChange = {},
                contentDescription = "Ebeveyn denetimi PIN kodu",
                stateDescription = "4 haneden 2 hane girildi"
            )
            A2PinInput(
                pin = "1234",
                onPinChange = {},
                contentDescription = "Ebeveyn denetimi PIN kodu",
                stateDescription = "Girilen PIN hatalı",
                errorMessage = "PIN eşleşmedi. Lütfen yeniden deneyin."
            )
        }
    }
}

@Preview(name = "TV focused PIN", group = "A2 security", widthDp = 960, heightDp = 260)
@Composable
private fun A2PinTvPreview() {
    IdealPlayerTheme(isTv = true) {
        A2PinInputImpl(
            pin = "8",
            onPinChange = {},
            contentDescription = "Kilitli içeriğin PIN kodu",
            stateDescription = "4 haneden 1 hane girildi",
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.xl),
            digitCount = 4,
            enabled = true,
            revealDigits = false,
            errorMessage = null,
            keyboardActions = KeyboardActions.Default,
            focusRequester = null,
            onComplete = null,
            previewFocused = true
        )
    }
}
