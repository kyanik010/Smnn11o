package com.idealplayer.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.idealplayer.app.core.designsystem.theme.A2Motion
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors

@Immutable
data class TvFocusVisualState(
    val scale: Float,
    val shadowElevation: Dp,
    val borderWidth: Dp,
    val backgroundColor: Color,
    val borderColor: Color,
    val glowColor: Color,
    val contentColor: Color,
    val secondaryContentColor: Color,
    val accentColor: Color,
    val accentWidth: Dp
) {
    val elevation: Dp
        get() = shadowElevation
}

@Composable
fun rememberTvFocusVisualState(
    isFocused: Boolean,
    isSelected: Boolean = false,
    defaultSurface: Color = Color.Transparent,
    selectedSurface: Color = IdealPlayerColors.SurfaceSelected,
    focusedSurface: Color = IdealPlayerColors.SurfaceFocus,
    selectedFocusedSurface: Color = IdealPlayerColors.SurfaceFocus,
    defaultContentColor: Color = IdealPlayerColors.TextPrimary,
    defaultSecondaryContentColor: Color = IdealPlayerColors.TextSecondary,
    selectedContentColor: Color = IdealPlayerColors.TextPrimary,
    focusedContentColor: Color = Color.White,
    selectedFocusedContentColor: Color = Color.White,
    selectedBorderColor: Color = IdealPlayerColors.SelectedBorder,
    focusedBorderColor: Color = IdealPlayerColors.FocusBorder,
    selectedFocusedBorderColor: Color = IdealPlayerColors.FocusBorder,
    selectedAccentColor: Color = IdealPlayerColors.Secondary,
    focusedAccentColor: Color = IdealPlayerColors.FocusBorder,
    selectedFocusedAccentColor: Color = IdealPlayerColors.FocusBorder
): TvFocusVisualState {
    val isFocusedAndSelected = isFocused && isSelected
    val scale by animateFloatAsState(
        targetValue = when {
            isFocusedAndSelected || isFocused -> A2Motion.FocusScale
            else -> 1f
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvFocusScale"
    )
    val borderWidth by animateDpAsState(
        targetValue = when {
            isFocusedAndSelected || isFocused -> 4.dp
            isSelected -> 2.dp
            else -> 0.dp
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvFocusBorderWidth"
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> selectedFocusedSurface
            isFocused -> focusedSurface
            isSelected -> selectedSurface
            else -> defaultSurface
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvFocusBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> selectedFocusedBorderColor
            isFocused -> focusedBorderColor
            isSelected -> selectedBorderColor
            else -> Color.Transparent
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvFocusBorderColor"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> selectedFocusedContentColor
            isFocused -> focusedContentColor
            isSelected -> selectedContentColor
            else -> defaultContentColor
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvFocusContentColor"
    )
    val secondaryContentColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> selectedFocusedContentColor.copy(alpha = 0.92f)
            isFocused -> focusedContentColor.copy(alpha = 0.88f)
            isSelected -> selectedContentColor.copy(alpha = 0.84f)
            else -> defaultSecondaryContentColor
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvFocusSecondaryColor"
    )
    val accentColor by animateColorAsState(
        targetValue = when {
            isFocusedAndSelected -> selectedFocusedAccentColor
            isFocused -> focusedAccentColor
            isSelected -> selectedAccentColor
            else -> Color.Transparent
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvFocusAccentColor"
    )
    val accentWidth by animateDpAsState(
        targetValue = when {
            isFocusedAndSelected || isFocused -> 4.dp
            isSelected -> 4.dp
            else -> 0.dp
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvFocusAccentWidth"
    )

    return TvFocusVisualState(
        scale = scale,
        // Blur shadows are disproportionately expensive on entry-level TV GPUs. The strong
        // border, surface change and scale retain an unmistakable premium focus treatment.
        shadowElevation = 0.dp,
        borderWidth = borderWidth,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        glowColor = Color.Transparent,
        contentColor = contentColor,
        secondaryContentColor = secondaryContentColor,
        accentColor = accentColor,
        accentWidth = accentWidth
    )
}
