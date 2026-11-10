package com.idealplayer.app.ui.components.a2

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.idealplayer.app.core.designsystem.theme.A2Motion
import com.idealplayer.app.core.designsystem.theme.A2Shape
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.IdealPlayerTheme
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens

private enum class SettingsPreviewInteraction {
    Runtime,
    Focused
}

/** Section/category heading for grouped settings content. */
@Composable
fun A2SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(A2Spacing.xs)
    ) {
        Text(
            text = title,
            color = IdealPlayerColors.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        if (description != null) {
            Text(
                text = description,
                color = IdealPlayerColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.size(A2Spacing.xs))
        content()
    }
}

/** A whole-row boolean control with one switch semantic node and platform-correct focus geometry. */
@Composable
fun A2BooleanSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    stateDescription: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    contentDescription: String? = null,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    enabled: Boolean = true
) {
    A2BooleanSettingRowImpl(
        title = title,
        checked = checked,
        onCheckedChange = onCheckedChange,
        stateDescription = stateDescription,
        modifier = modifier,
        description = description,
        contentDescription = contentDescription,
        icon = icon,
        iconContentDescription = iconContentDescription,
        enabled = enabled,
        previewInteraction = SettingsPreviewInteraction.Runtime
    )
}

@Composable
private fun A2BooleanSettingRowImpl(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    stateDescription: String,
    modifier: Modifier,
    description: String?,
    contentDescription: String?,
    icon: ImageVector?,
    iconContentDescription: String?,
    enabled: Boolean,
    previewInteraction: SettingsPreviewInteraction
) {
    val dimens = LocalIdealPlayerDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val runtimeFocused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused = previewInteraction == SettingsPreviewInteraction.Focused ||
        (previewInteraction == SettingsPreviewInteraction.Runtime && runtimeFocused)
    val background by animateColorAsState(
        targetValue = when {
            checked -> IdealPlayerColors.SurfaceSelected
            focused -> IdealPlayerColors.SurfaceFocus
            else -> Color.Transparent
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2BooleanSettingBackground"
    )
    val border = when {
        focused -> IdealPlayerColors.FocusBorder
        checked -> IdealPlayerColors.SelectedBorder
        else -> Color.Transparent
    }
    val alpha by animateFloatAsState(
        targetValue = when {
            !enabled -> A2Motion.DisabledAlpha
            pressed -> A2Motion.PressedAlpha
            else -> 1f
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2BooleanSettingAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dimens.touchTargetMin)
            .graphicsLayer { this.alpha = alpha }
            .background(background, A2Shape.medium)
            .border(
                width = when {
                    focused -> dimens.focusBorderWidth
                    checked -> 2.dp
                    else -> 0.dp
                },
                color = border,
                shape = A2Shape.medium
            )
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onValueChange = onCheckedChange
            )
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                this.stateDescription = stateDescription
                contentDescription?.let { this.contentDescription = it }
            }
            .padding(horizontal = A2Spacing.md, vertical = A2Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                tint = if (checked) IdealPlayerColors.Secondary else IdealPlayerColors.TextSecondary,
                modifier = Modifier.size(dimens.iconSize)
            )
            Spacer(Modifier.width(A2Spacing.md))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(A2Spacing.xs)
        ) {
            Text(
                text = title,
                color = IdealPlayerColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (description != null) {
                Text(
                    text = description,
                    color = IdealPlayerColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(A2Spacing.md))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.clearAndSetSemantics { },
            colors = SwitchDefaults.colors(
                checkedThumbColor = IdealPlayerColors.TextOnPrimary,
                checkedTrackColor = IdealPlayerColors.Secondary,
                checkedBorderColor = IdealPlayerColors.Secondary,
                uncheckedThumbColor = IdealPlayerColors.TextSecondary,
                uncheckedTrackColor = IdealPlayerColors.SurfaceElevated,
                uncheckedBorderColor = IdealPlayerColors.CardBorder,
                disabledCheckedThumbColor = IdealPlayerColors.Disabled,
                disabledCheckedTrackColor = IdealPlayerColors.SurfaceElevated,
                disabledUncheckedThumbColor = IdealPlayerColors.Disabled,
                disabledUncheckedTrackColor = IdealPlayerColors.SurfaceVariant
            )
        )
    }
}

/** A settings row that opens a selector or subordinate route. */
@Composable
fun A2SelectorSettingRow(
    title: String,
    selectedValue: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    contentDescription: String? = null,
    stateDescription: String = selectedValue,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    A2SelectorSettingRowImpl(
        title = title,
        selectedValue = selectedValue,
        onClick = onClick,
        modifier = modifier,
        description = description,
        contentDescription = contentDescription,
        stateDescription = stateDescription,
        icon = icon,
        iconContentDescription = iconContentDescription,
        enabled = enabled,
        selected = selected,
        previewInteraction = SettingsPreviewInteraction.Runtime
    )
}

@Composable
private fun A2SelectorSettingRowImpl(
    title: String,
    selectedValue: String,
    onClick: () -> Unit,
    modifier: Modifier,
    description: String?,
    contentDescription: String?,
    stateDescription: String,
    icon: ImageVector?,
    iconContentDescription: String?,
    enabled: Boolean,
    selected: Boolean,
    previewInteraction: SettingsPreviewInteraction
) {
    val dimens = LocalIdealPlayerDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val runtimeFocused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused = previewInteraction == SettingsPreviewInteraction.Focused ||
        (previewInteraction == SettingsPreviewInteraction.Runtime && runtimeFocused)
    val background by animateColorAsState(
        targetValue = when {
            selected -> IdealPlayerColors.SurfaceSelected
            focused -> IdealPlayerColors.SurfaceFocus
            else -> Color.Transparent
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2SelectorSettingBackground"
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            !enabled -> A2Motion.DisabledAlpha
            pressed -> A2Motion.PressedAlpha
            else -> 1f
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2SelectorSettingAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dimens.touchTargetMin)
            .graphicsLayer { this.alpha = alpha }
            .background(background, A2Shape.medium)
            .border(
                width = when {
                    focused -> dimens.focusBorderWidth
                    selected -> 2.dp
                    else -> 0.dp
                },
                color = when {
                    focused -> IdealPlayerColors.FocusBorder
                    selected -> IdealPlayerColors.SelectedBorder
                    else -> Color.Transparent
                },
                shape = A2Shape.medium
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.selected = selected
                this.stateDescription = stateDescription
                contentDescription?.let { this.contentDescription = it }
            }
            .padding(horizontal = A2Spacing.md, vertical = A2Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                tint = if (selected) IdealPlayerColors.Secondary else IdealPlayerColors.TextSecondary,
                modifier = Modifier.size(dimens.iconSize)
            )
            Spacer(Modifier.width(A2Spacing.md))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(A2Spacing.xs)
        ) {
            Text(
                text = title,
                color = IdealPlayerColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (description != null) {
                Text(
                    text = description,
                    color = IdealPlayerColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(A2Spacing.md))
        Text(
            text = selectedValue,
            color = if (selected) IdealPlayerColors.Secondary else IdealPlayerColors.TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.6f, fill = false)
        )
        Spacer(Modifier.width(A2Spacing.xs))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = if (focused) IdealPlayerColors.Primary else IdealPlayerColors.TextTertiary,
            modifier = Modifier.size(dimens.iconSize)
        )
    }
}

@Preview(name = "Mobile rows", group = "A2 settings", widthDp = 390)
@Composable
private fun A2SettingsMobilePreview() {
    IdealPlayerTheme {
        Column(
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.md),
            verticalArrangement = Arrangement.spacedBy(A2Spacing.xs)
        ) {
            A2SettingsSection(
                title = "Oynatma ve bildirim tercihleri",
                description = "Bu ayarlar yalnızca bu cihazdaki kişisel deneyiminizi değiştirir."
            ) {
                A2BooleanSettingRow(
                    title = "Yeni bölüm bildirimleri",
                    description = "Favori dizilerinize yeni bir bölüm eklendiğinde bu cihazda bildirim gösterilir.",
                    checked = true,
                    onCheckedChange = {},
                    stateDescription = "Açık",
                    icon = Icons.Filled.Notifications
                )
                A2BooleanSettingRow(
                    title = "Otomatik oynatma",
                    checked = false,
                    onCheckedChange = {},
                    stateDescription = "Kapalı",
                    enabled = false
                )
                A2SelectorSettingRow(
                    title = "Tercih edilen görüntü kalitesi",
                    description = "Bağlantı hızına göre otomatik seçim yapılabilir.",
                    selectedValue = "Otomatik (önerilen)",
                    onClick = {},
                    icon = Icons.Filled.HighQuality
                )
            }
        }
    }
}

@Preview(name = "TV focused row", group = "A2 settings", widthDp = 960, heightDp = 260)
@Composable
private fun A2SettingsTvPreview() {
    IdealPlayerTheme(isTv = true) {
        A2SelectorSettingRowImpl(
            title = "Canlı yayın için tercih edilen oynatıcı motoru",
            selectedValue = "Media3 — sorun olursa VLC'ye geç",
            onClick = {},
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.xl),
            description = "Kanal değiştirirken hazır olma ve ilk görüntü durumu doğrulanır.",
            contentDescription = null,
            stateDescription = "Media3 seçili",
            icon = Icons.Filled.HighQuality,
            iconContentDescription = null,
            enabled = true,
            selected = true,
            previewInteraction = SettingsPreviewInteraction.Focused
        )
    }
}
