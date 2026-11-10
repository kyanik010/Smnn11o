package com.idealplayer.app.ui.components.a2

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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

data class A2DropdownOption<T>(
    val value: T,
    val label: String,
    val contentDescription: String = label,
    val stateDescription: String? = null,
    val enabled: Boolean = true
)

/** Stateless dropdown selector with an external expanded state for durable screen restoration. */
@Composable
fun <T> A2DropdownSelector(
    label: String,
    selectedOption: A2DropdownOption<T>?,
    options: List<A2DropdownOption<T>>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOptionSelected: (A2DropdownOption<T>) -> Unit,
    placeholder: String,
    stateDescription: String,
    modifier: Modifier = Modifier,
    contentDescription: String = label,
    enabled: Boolean = true
) {
    val dimens = LocalIdealPlayerDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val container by animateColorAsState(
        targetValue = if (expanded) IdealPlayerColors.SurfaceSelected else IdealPlayerColors.SurfaceVariant,
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2DropdownContainer"
    )

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = dimens.touchTargetMin)
                .background(container, A2Shape.medium)
                .border(
                    width = when {
                        focused -> dimens.focusBorderWidth
                        expanded -> 2.dp
                        else -> 1.dp
                    },
                    color = when {
                        focused -> IdealPlayerColors.FocusBorder
                        expanded -> IdealPlayerColors.SelectedBorder
                        else -> IdealPlayerColors.CardBorder
                    },
                    shape = A2Shape.medium
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    role = Role.DropdownList,
                    onClick = { onExpandedChange(!expanded) }
                )
                .semantics(mergeDescendants = true) {
                    role = Role.DropdownList
                    this.contentDescription = contentDescription
                    this.stateDescription = stateDescription
                }
                .padding(horizontal = A2Spacing.md, vertical = A2Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = if (focused) IdealPlayerColors.Primary else IdealPlayerColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = selectedOption?.label ?: placeholder,
                    color = if (selectedOption == null) IdealPlayerColors.TextTertiary else IdealPlayerColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = if (focused) IdealPlayerColors.Primary else IdealPlayerColors.TextSecondary,
                modifier = Modifier.size(dimens.iconSize)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier
                .widthIn(min = 240.dp)
                .background(IdealPlayerColors.SurfaceElevated)
        ) {
            options.forEach { option ->
                val isSelected = selectedOption?.value == option.value
                A2DropdownOptionRow(
                    option = option,
                    selected = isSelected,
                    onClick = {
                        onOptionSelected(option)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun <T> A2DropdownOptionRow(
    option: A2DropdownOption<T>,
    selected: Boolean,
    onClick: () -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.touchTargetMin)
            .background(
                color = when {
                    selected -> IdealPlayerColors.SurfaceSelected
                    focused -> IdealPlayerColors.SurfaceFocus
                    else -> Color.Transparent
                },
                shape = A2Shape.small
            )
            .border(
                width = if (focused) dimens.focusBorderWidth else 0.dp,
                color = if (focused) IdealPlayerColors.FocusBorder else Color.Transparent,
                shape = A2Shape.small
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = option.enabled,
                role = Role.Button,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.selected = selected
                this.contentDescription = option.contentDescription
                option.stateDescription?.let { this.stateDescription = it }
            }
            .padding(horizontal = A2Spacing.md, vertical = A2Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = option.label,
            color = if (option.enabled) IdealPlayerColors.TextPrimary else IdealPlayerColors.TextTertiary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = IdealPlayerColors.Secondary,
                modifier = Modifier.size(dimens.iconSize)
            )
        }
    }
}

@Preview(name = "Mobile expanded", group = "A2 selectors", widthDp = 390, heightDp = 480)
@Composable
private fun A2DropdownMobilePreview() {
    IdealPlayerTheme {
        val options = listOf(
            A2DropdownOption("auto", "Otomatik (önerilen)", stateDescription = "Seçili"),
            A2DropdownOption("high", "Yüksek kalite — daha fazla veri kullanır"),
            A2DropdownOption("low", "Veri tasarrufu")
        )
        A2DropdownSelector(
            label = "Tercih edilen görüntü kalitesi",
            selectedOption = options.first(),
            options = options,
            expanded = true,
            onExpandedChange = {},
            onOptionSelected = {},
            placeholder = "Bir kalite seçin",
            stateDescription = "Otomatik seçili, liste açık",
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.md)
                .fillMaxWidth()
        )
    }
}

@Preview(name = "TV collapsed", group = "A2 selectors", widthDp = 960, heightDp = 260)
@Composable
private fun A2DropdownTvPreview() {
    IdealPlayerTheme(isTv = true) {
        val selected = A2DropdownOption("media3", "Media3 — sorun olursa VLC'ye geç")
        A2DropdownSelector(
            label = "Canlı yayın oynatıcı motoru",
            selectedOption = selected,
            options = listOf(selected, A2DropdownOption("vlc", "Her zaman VLC kullan")),
            expanded = false,
            onExpandedChange = {},
            onOptionSelected = {},
            placeholder = "Oynatıcı seçin",
            stateDescription = "Media3 seçili, liste kapalı",
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.xl)
                .fillMaxWidth()
        )
    }
}
