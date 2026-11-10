package com.idealplayer.app.ui.components.a2

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
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
import kotlin.math.roundToLong

/** Player transport action sized from [LocalIdealPlayerDimens]. */
@Composable
fun A2PlayerControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    primary: Boolean = false,
    stateDescription: String? = null
) {
    val dimens = LocalIdealPlayerDimens.current
    A2IconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        // Figma player controls always use the cinematic card surface. `primary` is retained as a
        // compatibility hook for call sites that intentionally request the red semantic action.
        variant = if (primary) A2ActionVariant.Primary else A2ActionVariant.Secondary,
        enabled = enabled,
        selected = selected,
        stateDescription = stateDescription,
        size = dimens.playerControlSize,
        iconSize = dimens.iconSize
    )
}

/**
 * Stateless playback timeline shell. Seeking is delegated to [onSeek]; the shell only clamps and
 * translates touch, accessibility and D-pad input into a requested media position.
 */
@Composable
fun A2PlayerTimeline(
    positionMillis: Long,
    durationMillis: Long,
    contentDescription: String,
    stateDescription: String,
    modifier: Modifier = Modifier,
    bufferedPositionMillis: Long = positionMillis,
    positionLabel: String? = null,
    endLabel: String? = null,
    liveLabel: String? = null,
    isAtLiveEdge: Boolean = false,
    enabled: Boolean = true,
    seekStepMillis: Long = 10_000L,
    onSeek: ((Long) -> Unit)? = null
) {
    A2PlayerTimelineImpl(
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        contentDescription = contentDescription,
        stateDescription = stateDescription,
        modifier = modifier,
        bufferedPositionMillis = bufferedPositionMillis,
        positionLabel = positionLabel,
        endLabel = endLabel,
        liveLabel = liveLabel,
        isAtLiveEdge = isAtLiveEdge,
        enabled = enabled,
        seekStepMillis = seekStepMillis,
        onSeek = onSeek,
        previewFocused = false
    )
}

@Composable
private fun A2PlayerTimelineImpl(
    positionMillis: Long,
    durationMillis: Long,
    contentDescription: String,
    stateDescription: String,
    modifier: Modifier,
    bufferedPositionMillis: Long,
    positionLabel: String?,
    endLabel: String?,
    liveLabel: String?,
    isAtLiveEdge: Boolean,
    enabled: Boolean,
    seekStepMillis: Long,
    onSeek: ((Long) -> Unit)?,
    previewFocused: Boolean
) {
    val dimens = LocalIdealPlayerDimens.current
    val safeDuration = durationMillis.coerceAtLeast(0L)
    val safePosition = positionMillis.coerceIn(0L, safeDuration.takeIf { it > 0L } ?: 0L)
    val safeBuffered = bufferedPositionMillis.coerceIn(safePosition, safeDuration.takeIf { it > 0L } ?: safePosition)
    val playedRatio = if (safeDuration > 0L) safePosition.toFloat() / safeDuration.toFloat() else 0f
    val bufferedRatio = if (safeDuration > 0L) safeBuffered.toFloat() / safeDuration.toFloat() else 0f
    val canSeek = enabled && safeDuration > 0L && onSeek != null
    val interactionSource = remember { MutableInteractionSource() }
    val runtimeFocused by interactionSource.collectIsFocusedAsState()
    val focused = previewFocused || runtimeFocused
    val borderColor by animateColorAsState(
        targetValue = if (focused) IdealPlayerColors.FocusBorder else Color.Transparent,
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2TimelineFocusBorder"
    )
    val trackHeight = if (dimens.touchTargetMin >= 56.dp) 8.dp else 6.dp
    val thumbSize = if (dimens.touchTargetMin >= 56.dp) 18.dp else 14.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dimens.touchTargetMin)
            .background(
                color = if (focused) IdealPlayerColors.SurfaceFocus else Color.Transparent,
                shape = A2Shape.medium
            )
            .border(
                width = if (focused) dimens.focusBorderWidth else 0.dp,
                color = borderColor,
                shape = A2Shape.medium
            )
            .onPreviewKeyEvent { event ->
                if (!canSeek || event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onSeek?.invoke((safePosition - seekStepMillis).coerceAtLeast(0L))
                            true
                        }
                        Key.DirectionRight -> {
                            onSeek?.invoke((safePosition + seekStepMillis).coerceAtMost(safeDuration))
                            true
                        }
                        else -> false
                    }
                }
            }
            .pointerInput(canSeek, safeDuration) {
                if (canSeek) {
                    detectTapGestures { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val ratio = (offset.x / width).coerceIn(0f, 1f)
                        onSeek?.invoke((safeDuration * ratio).roundToLong())
                    }
                }
            }
            .focusable(enabled = canSeek, interactionSource = interactionSource)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription
                progressBarRangeInfo = ProgressBarRangeInfo(playedRatio, 0f..1f)
                if (canSeek) {
                    setProgress { target ->
                        onSeek?.invoke((safeDuration * target.coerceIn(0f, 1f)).roundToLong())
                        true
                    }
                }
            }
            .padding(horizontal = A2Spacing.sm, vertical = A2Spacing.xs),
        verticalArrangement = Arrangement.Center
    ) {
        if (positionLabel != null || endLabel != null || liveLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = positionLabel.orEmpty(),
                    color = IdealPlayerColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
                Text(
                    text = if (isAtLiveEdge && liveLabel != null) liveLabel else endLabel.orEmpty(),
                    color = if (isAtLiveEdge) IdealPlayerColors.Primary else IdealPlayerColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(Modifier.height(A2Spacing.xs))
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbSize),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(IdealPlayerColors.SurfaceElevated)
            )
            if (bufferedRatio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedRatio)
                        .height(trackHeight)
                        .clip(CircleShape)
                        .background(IdealPlayerColors.TextTertiary.copy(alpha = 0.42f))
                )
            }
            if (playedRatio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(playedRatio)
                        .height(trackHeight)
                        .clip(CircleShape)
                        .background(IdealPlayerColors.Primary)
                )
            }
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - thumbSize) * playedRatio)
                    .size(thumbSize)
                    .background(IdealPlayerColors.Primary, CircleShape)
                    .border(
                        width = if (focused) 2.dp else 0.dp,
                        color = IdealPlayerColors.TextOnPrimary,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Preview(name = "Mobile controls", group = "A2 player", widthDp = 390, heightDp = 240)
@Composable
private fun A2PlayerMobilePreview() {
    IdealPlayerTheme {
        Column(
            modifier = Modifier
                .background(IdealPlayerColors.OverlayPlayer)
                .padding(A2Spacing.md),
            verticalArrangement = Arrangement.spacedBy(A2Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                A2PlayerControlButton(Icons.Filled.Replay10, "10 saniye geri", {})
                A2PlayerControlButton(Icons.Filled.Pause, "Duraklat", {}, primary = true)
                A2PlayerControlButton(Icons.Filled.Forward10, "10 saniye ileri", {})
                A2PlayerControlButton(Icons.Filled.PlayArrow, "Devre dışı oynat", {}, enabled = false)
            }
            A2PlayerTimeline(
                positionMillis = 3_612_000L,
                durationMillis = 7_200_000L,
                bufferedPositionMillis = 4_440_000L,
                positionLabel = "1:00:12",
                endLabel = "2:00:00",
                contentDescription = "Oynatma konumu",
                stateDescription = "1 saat 12 saniye oynatıldı",
                onSeek = {}
            )
        }
    }
}

@Preview(name = "TV focused live timeline", group = "A2 player", widthDp = 960, heightDp = 320)
@Composable
private fun A2PlayerTvPreview() {
    IdealPlayerTheme(isTv = true) {
        Column(
            modifier = Modifier
                .background(IdealPlayerColors.OverlayPlayer)
                .padding(A2Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(A2Spacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LocalIdealPlayerDimens.current.playerControlSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                A2PlayerControlButton(Icons.Filled.Replay10, "10 saniye geri", {})
                A2PlayerControlButton(Icons.Filled.PlayArrow, "Oynat", {}, primary = true)
                A2PlayerControlButton(
                    Icons.Filled.Forward10,
                    "10 saniye ileri",
                    {},
                    selected = true,
                    stateDescription = "Seçili"
                )
            }
            A2PlayerTimelineImpl(
                positionMillis = 1_740_000L,
                durationMillis = 1_800_000L,
                contentDescription = "Canlı yayın zaman çizelgesi",
                stateDescription = "Canlı yayının 1 dakika gerisinde",
                modifier = Modifier,
                bufferedPositionMillis = 1_800_000L,
                positionLabel = "20:14",
                endLabel = "20:15",
                liveLabel = "CANLI",
                isAtLiveEdge = true,
                enabled = true,
                seekStepMillis = 10_000L,
                onSeek = {},
                previewFocused = true
            )
        }
    }
}
