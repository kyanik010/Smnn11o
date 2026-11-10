package com.idealplayer.app.ui.components.a2

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.idealplayer.app.core.designsystem.theme.A2Motion
import com.idealplayer.app.core.designsystem.theme.A2Shape
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.IdealPlayerTheme
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens

/** Visual styles shared by A2 text and icon actions. */
enum class A2ActionVariant {
    Primary,
    Secondary,
    Ghost,
    Destructive
}

private enum class PreviewInteraction {
    Runtime,
    Focused,
    Pressed
}

/**
 * A2 action with platform-scaled targets, a red transient focus ring and a blue persistent
 * selected state. All user-facing text, including accessibility state text, is caller-owned.
 */
@Composable
fun A2ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: A2ActionVariant = A2ActionVariant.Primary,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    stateDescription: String? = null,
    contentDescription: String? = null,
    loading: Boolean = false
) {
    A2ActionButtonImpl(
        text = text,
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        icon = icon,
        iconContentDescription = iconContentDescription,
        enabled = enabled,
        selected = selected,
        stateDescription = stateDescription,
        contentDescription = contentDescription,
        loading = loading,
        previewInteraction = PreviewInteraction.Runtime
    )
}

@Composable
private fun A2ActionButtonImpl(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    variant: A2ActionVariant,
    icon: ImageVector?,
    iconContentDescription: String?,
    enabled: Boolean,
    selected: Boolean,
    stateDescription: String?,
    contentDescription: String?,
    loading: Boolean,
    previewInteraction: PreviewInteraction
) {
    val dimens = LocalIdealPlayerDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val runtimeFocused by interactionSource.collectIsFocusedAsState()
    val runtimePressed by interactionSource.collectIsPressedAsState()
    val focused = previewInteraction == PreviewInteraction.Focused ||
        (previewInteraction == PreviewInteraction.Runtime && runtimeFocused)
    val pressed = previewInteraction == PreviewInteraction.Pressed ||
        (previewInteraction == PreviewInteraction.Runtime && runtimePressed)
    val actionEnabled = enabled && !loading
    val containerColor by animateColorAsState(
        targetValue = actionContainerColor(variant, selected, pressed, enabled),
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2ActionContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> IdealPlayerColors.FocusBorder
            selected -> IdealPlayerColors.SelectedBorder
            variant == A2ActionVariant.Secondary -> IdealPlayerColors.Secondary
            variant == A2ActionVariant.Ghost -> IdealPlayerColors.GlassBorder
            variant == A2ActionVariant.Destructive -> IdealPlayerColors.Error.copy(alpha = 0.72f)
            else -> Color.Transparent
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2ActionBorder"
    )
    val borderWidth by animateDpAsState(
        targetValue = when {
            focused -> dimens.focusBorderWidth
            selected -> 2.dp
            variant == A2ActionVariant.Primary -> 0.dp
            variant == A2ActionVariant.Secondary -> 2.dp
            else -> 1.dp
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2ActionBorderWidth"
    )
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.98f
            focused -> A2Motion.FocusScale
            else -> 1f
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2ActionScale"
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            !enabled -> A2Motion.DisabledAlpha
            pressed -> A2Motion.PressedAlpha
            else -> 1f
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2ActionAlpha"
    )
    val foreground = actionContentColor(variant, selected)

    Row(
        modifier = modifier
            .defaultMinSize(
                minWidth = dimens.touchTargetMin,
                minHeight = maxOf(dimens.buttonHeight, dimens.touchTargetMin)
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .then(
                if (focused && dimens.touchTargetMin < 56.dp) {
                    Modifier.shadow(
                        elevation = 6.dp,
                        shape = A2Shape.medium,
                        ambientColor = IdealPlayerColors.FocusGlow,
                        spotColor = IdealPlayerColors.FocusGlow
                    )
                } else {
                    Modifier
                }
            )
            .background(containerColor, A2Shape.medium)
            .border(BorderStroke(borderWidth, borderColor), A2Shape.medium)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = actionEnabled,
                role = Role.Button,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.selected = selected
                contentDescription?.let { this.contentDescription = it }
                stateDescription?.let { this.stateDescription = it }
            }
            .padding(horizontal = A2Spacing.md, vertical = A2Spacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(dimens.iconSize),
                color = foreground,
                strokeWidth = 2.dp
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(dimens.iconSize),
                tint = foreground
            )
        }
        if (loading || icon != null) {
            Spacer(Modifier.width(A2Spacing.xs))
        }
        Text(
            text = text,
            color = foreground,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Icon-only A2 action. [contentDescription] is mandatory because no visible label is present. */
@Composable
fun A2IconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: A2ActionVariant = A2ActionVariant.Ghost,
    enabled: Boolean = true,
    selected: Boolean = false,
    stateDescription: String? = null,
    size: Dp? = null,
    iconSize: Dp? = null
) {
    A2IconButtonImpl(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        enabled = enabled,
        selected = selected,
        stateDescription = stateDescription,
        size = size,
        iconSize = iconSize,
        previewInteraction = PreviewInteraction.Runtime
    )
}

@Composable
private fun A2IconButtonImpl(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier,
    variant: A2ActionVariant,
    enabled: Boolean,
    selected: Boolean,
    stateDescription: String?,
    size: Dp?,
    iconSize: Dp?,
    previewInteraction: PreviewInteraction
) {
    val dimens = LocalIdealPlayerDimens.current
    val targetSize = maxOf(size ?: dimens.touchTargetMin, dimens.touchTargetMin)
    val resolvedIconSize = iconSize ?: dimens.iconSize
    val interactionSource = remember { MutableInteractionSource() }
    val runtimeFocused by interactionSource.collectIsFocusedAsState()
    val runtimePressed by interactionSource.collectIsPressedAsState()
    val focused = previewInteraction == PreviewInteraction.Focused ||
        (previewInteraction == PreviewInteraction.Runtime && runtimeFocused)
    val pressed = previewInteraction == PreviewInteraction.Pressed ||
        (previewInteraction == PreviewInteraction.Runtime && runtimePressed)
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.94f
            focused -> A2Motion.FocusScale
            else -> 1f
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2IconActionScale"
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            !enabled -> A2Motion.DisabledAlpha
            pressed -> A2Motion.PressedAlpha
            else -> 1f
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2IconActionAlpha"
    )
    val containerColor by animateColorAsState(
        targetValue = actionContainerColor(variant, selected, pressed, enabled),
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2IconActionContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> IdealPlayerColors.FocusBorder
            selected -> IdealPlayerColors.SelectedBorder
            variant == A2ActionVariant.Secondary -> IdealPlayerColors.CardBorder
            variant == A2ActionVariant.Destructive -> IdealPlayerColors.Error.copy(alpha = 0.72f)
            else -> Color.Transparent
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2IconActionBorder"
    )
    val borderWidth by animateDpAsState(
        targetValue = when {
            focused -> dimens.focusBorderWidth
            selected -> 2.dp
            variant == A2ActionVariant.Primary || variant == A2ActionVariant.Ghost -> 0.dp
            else -> 1.dp
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2IconActionBorderWidth"
    )

    Box(
        modifier = modifier
            .size(targetSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .then(
                if (focused) {
                    Modifier.shadow(
                        elevation = if (dimens.touchTargetMin >= 56.dp) 12.dp else 6.dp,
                        shape = CircleShape,
                        ambientColor = IdealPlayerColors.FocusGlow,
                        spotColor = IdealPlayerColors.FocusGlow
                    )
                } else {
                    Modifier
                }
            )
            .background(containerColor, CircleShape)
            .border(BorderStroke(borderWidth, borderColor), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.contentDescription = contentDescription
                this.selected = selected
                stateDescription?.let { this.stateDescription = it }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(resolvedIconSize),
            tint = actionContentColor(variant, selected)
        )
    }
}

private fun actionContainerColor(
    variant: A2ActionVariant,
    selected: Boolean,
    pressed: Boolean,
    enabled: Boolean
): Color {
    if (selected) {
        return if (pressed) IdealPlayerColors.SecondaryPressed else IdealPlayerColors.SurfaceSelected
    }
    if (!enabled) {
        return when (variant) {
            A2ActionVariant.Ghost -> Color.Transparent
            else -> IdealPlayerColors.SurfaceElevated
        }
    }
    return when (variant) {
        A2ActionVariant.Primary -> if (pressed) IdealPlayerColors.PrimaryPressed else IdealPlayerColors.Primary
        A2ActionVariant.Secondary -> if (pressed) IdealPlayerColors.SurfaceVariant else IdealPlayerColors.SurfaceElevated
        A2ActionVariant.Ghost -> if (pressed) IdealPlayerColors.GlassBackground else Color.Transparent
        A2ActionVariant.Destructive -> if (pressed) {
            IdealPlayerColors.Error.copy(alpha = 0.34f)
        } else {
            IdealPlayerColors.Error.copy(alpha = 0.16f)
        }
    }
}

private fun actionContentColor(variant: A2ActionVariant, selected: Boolean): Color {
    if (selected) return IdealPlayerColors.TextPrimary
    return when (variant) {
        A2ActionVariant.Primary -> IdealPlayerColors.TextOnPrimary
        A2ActionVariant.Secondary,
        A2ActionVariant.Ghost -> IdealPlayerColors.TextPrimary
        A2ActionVariant.Destructive -> IdealPlayerColors.Error
    }
}

@Preview(name = "Mobile states", group = "A2 actions", widthDp = 390)
@Composable
private fun A2ActionButtonMobilePreview() {
    IdealPlayerTheme {
        Column(
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.md),
            verticalArrangement = Arrangement.spacedBy(A2Spacing.sm)
        ) {
            A2ActionButton(text = "Oynat", onClick = {}, icon = Icons.Filled.PlayArrow)
            A2ActionButton(
                text = "İzlemeye kaldığınız yerden devam edin",
                onClick = {},
                variant = A2ActionVariant.Secondary,
                selected = true,
                stateDescription = "Seçili"
            )
            A2ActionButtonImpl(
                text = "Yeniden dene",
                onClick = {},
                modifier = Modifier,
                variant = A2ActionVariant.Ghost,
                icon = Icons.Filled.Refresh,
                iconContentDescription = null,
                enabled = true,
                selected = false,
                stateDescription = "Odaklandı",
                contentDescription = null,
                loading = false,
                previewInteraction = PreviewInteraction.Focused
            )
            A2ActionButton(
                text = "Listeyi sil",
                onClick = {},
                variant = A2ActionVariant.Destructive,
                icon = Icons.Filled.Delete,
                enabled = false
            )
            Row(horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm)) {
                A2IconButton(Icons.Filled.Refresh, "Yenile", {})
                A2IconButton(
                    Icons.Filled.PlayArrow,
                    "Seçili oynatma eylemi",
                    {},
                    selected = true,
                    stateDescription = "Seçili"
                )
                A2IconButton(Icons.Filled.Delete, "Devre dışı silme eylemi", {}, enabled = false)
            }
        }
    }
}

@Preview(name = "TV focus and pressed", group = "A2 actions", widthDp = 960, heightDp = 260)
@Composable
private fun A2ActionButtonTvPreview() {
    IdealPlayerTheme(isTv = true) {
        Row(
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(A2Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            A2ActionButtonImpl(
                text = "Canlı yayını başlat",
                onClick = {},
                modifier = Modifier,
                variant = A2ActionVariant.Primary,
                icon = Icons.Filled.PlayArrow,
                iconContentDescription = null,
                enabled = true,
                selected = false,
                stateDescription = "Odaklandı",
                contentDescription = null,
                loading = false,
                previewInteraction = PreviewInteraction.Focused
            )
            A2ActionButtonImpl(
                text = "Basılı",
                onClick = {},
                modifier = Modifier,
                variant = A2ActionVariant.Secondary,
                icon = null,
                iconContentDescription = null,
                enabled = true,
                selected = false,
                stateDescription = "Basılı",
                contentDescription = null,
                loading = false,
                previewInteraction = PreviewInteraction.Pressed
            )
            A2IconButtonImpl(
                icon = Icons.Filled.Refresh,
                contentDescription = "Yayını yenile",
                onClick = {},
                modifier = Modifier,
                variant = A2ActionVariant.Ghost,
                enabled = true,
                selected = false,
                stateDescription = "Odaklandı",
                size = null,
                iconSize = null,
                previewInteraction = PreviewInteraction.Focused
            )
        }
    }
}
