package com.idealplayer.app.ui.components.a2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.idealplayer.app.core.designsystem.theme.A2Shape
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.IdealPlayerTheme
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens

enum class A2StatusType {
    Loading,
    Buffering,
    Empty,
    Error,
    Offline,
    Updating,
    Syncing,
    Success
}

/** A complete loading/empty/error/offline surface with caller-supplied copy and retry action. */
@Composable
fun A2StatusSurface(
    type: A2StatusType,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    stateDescription: String = title,
    contentDescription: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionVariant: A2ActionVariant = A2ActionVariant.Primary,
    actionContentDescription: String? = null,
    progress: Float? = null,
    progressContentDescription: String? = null,
    progressValueLabel: String? = null
) {
    val dimens = LocalIdealPlayerDimens.current
    val resolvedIcon = icon ?: when (type) {
        A2StatusType.Loading -> Icons.Filled.HourglassTop
        A2StatusType.Buffering -> Icons.Filled.HourglassTop
        A2StatusType.Empty -> Icons.Filled.Inbox
        A2StatusType.Error -> Icons.Filled.ErrorOutline
        A2StatusType.Offline -> Icons.Filled.CloudOff
        A2StatusType.Updating -> Icons.Filled.SystemUpdate
        A2StatusType.Syncing -> Icons.Filled.Sync
        A2StatusType.Success -> Icons.Filled.CheckCircle
    }
    val accent = when (type) {
        A2StatusType.Loading,
        A2StatusType.Buffering,
        A2StatusType.Updating,
        A2StatusType.Syncing -> IdealPlayerColors.Primary
        A2StatusType.Empty -> IdealPlayerColors.TextTertiary
        A2StatusType.Error -> IdealPlayerColors.Error
        A2StatusType.Offline -> IdealPlayerColors.Warning
        A2StatusType.Success -> IdealPlayerColors.Success
    }

    Column(
        modifier = modifier
            .background(IdealPlayerColors.Surface, A2Shape.large)
            .semantics(mergeDescendants = false) {
                liveRegion = LiveRegionMode.Polite
                this.stateDescription = stateDescription
                contentDescription?.let { this.contentDescription = it }
            }
            .padding(if (dimens.touchTargetMin >= 56.dp) A2Spacing.xl else A2Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val showIndeterminateIndicator = type == A2StatusType.Loading ||
            type == A2StatusType.Buffering ||
            ((type == A2StatusType.Updating || type == A2StatusType.Syncing) &&
                progressContentDescription == null)
        if (showIndeterminateIndicator) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(maxOf(dimens.touchTargetMin, 48.dp))
                    .clearAndSetSemantics { },
                color = accent,
                trackColor = IdealPlayerColors.SurfaceElevated
            )
        } else {
            Icon(
                imageVector = resolvedIcon,
                contentDescription = null,
                modifier = Modifier.size(maxOf(dimens.iconSize, 32.dp)),
                tint = accent
            )
        }
        Spacer(Modifier.height(A2Spacing.md))
        Text(
            text = title,
            color = IdealPlayerColors.TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(A2Spacing.xs))
        Text(
            text = message,
            color = IdealPlayerColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        if (progressContentDescription != null) {
            Spacer(Modifier.height(A2Spacing.md))
            A2ProgressIndicator(
                contentDescription = progressContentDescription,
                progress = progress,
                valueLabel = progressValueLabel,
                stateDescription = progressValueLabel,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(A2Spacing.lg))
            A2ActionButton(
                text = actionLabel,
                onClick = onAction,
                variant = actionVariant,
                contentDescription = actionContentDescription
            )
        }
    }
}

enum class A2BadgeTone {
    Neutral,
    Primary,
    Selected,
    Success,
    Warning,
    Error
}

/** Compact non-interactive status badge. */
@Composable
fun A2Badge(
    text: String,
    modifier: Modifier = Modifier,
    tone: A2BadgeTone = A2BadgeTone.Neutral,
    contentDescription: String = text,
    stateDescription: String? = null,
    leadingIcon: ImageVector? = null
) {
    val (container, foreground) = when (tone) {
        A2BadgeTone.Neutral -> IdealPlayerColors.SurfaceElevated to IdealPlayerColors.TextSecondary
        A2BadgeTone.Primary -> IdealPlayerColors.Primary to IdealPlayerColors.TextOnPrimary
        A2BadgeTone.Selected -> IdealPlayerColors.SurfaceSelected to IdealPlayerColors.TextPrimary
        A2BadgeTone.Success -> IdealPlayerColors.Success.copy(alpha = 0.18f) to IdealPlayerColors.Success
        A2BadgeTone.Warning -> IdealPlayerColors.Warning.copy(alpha = 0.18f) to IdealPlayerColors.Warning
        A2BadgeTone.Error -> IdealPlayerColors.Error.copy(alpha = 0.18f) to IdealPlayerColors.Error
    }
    Row(
        modifier = modifier
            .background(container, CircleShape)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                stateDescription?.let { this.stateDescription = it }
            }
            .padding(horizontal = A2Spacing.sm, vertical = A2Spacing.xs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = foreground
            )
            Spacer(Modifier.width(A2Spacing.xs))
        }
        Text(
            text = text,
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

enum class A2ProgressTone {
    Primary,
    Selected,
    Success,
    Warning,
    Error
}

/** Determinate or indeterminate A2 progress with one merged accessibility progress node. */
@Composable
fun A2ProgressIndicator(
    contentDescription: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    label: String? = null,
    valueLabel: String? = null,
    stateDescription: String? = valueLabel,
    tone: A2ProgressTone = A2ProgressTone.Primary
) {
    val normalizedProgress = progress?.coerceIn(0f, 1f)
    val color = when (tone) {
        A2ProgressTone.Primary -> IdealPlayerColors.Primary
        A2ProgressTone.Selected -> IdealPlayerColors.Secondary
        A2ProgressTone.Success -> IdealPlayerColors.Success
        A2ProgressTone.Warning -> IdealPlayerColors.Warning
        A2ProgressTone.Error -> IdealPlayerColors.Error
    }
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
            stateDescription?.let { this.stateDescription = it }
            progressBarRangeInfo = normalizedProgress?.let {
                ProgressBarRangeInfo(it, 0f..1f)
            } ?: ProgressBarRangeInfo.Indeterminate
        },
        verticalArrangement = Arrangement.spacedBy(A2Spacing.xs)
    ) {
        if (label != null || valueLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (label != null) {
                    Text(
                        text = label,
                        color = IdealPlayerColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (valueLabel != null) {
                    Text(
                        text = valueLabel,
                        color = IdealPlayerColors.TextPrimary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        if (normalizedProgress == null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .clearAndSetSemantics { },
                color = color,
                trackColor = IdealPlayerColors.SurfaceElevated
            )
        } else {
            LinearProgressIndicator(
                progress = { normalizedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .clearAndSetSemantics { },
                color = color,
                trackColor = IdealPlayerColors.SurfaceElevated
            )
        }
    }
}

@Preview(name = "Mobile status and progress", group = "A2 status", widthDp = 390, heightDp = 760)
@Composable
private fun A2StatusMobilePreview() {
    IdealPlayerTheme {
        Column(
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.md),
            verticalArrangement = Arrangement.spacedBy(A2Spacing.md)
        ) {
            A2StatusSurface(
                type = A2StatusType.Offline,
                title = "İnternet bağlantısı yok",
                message = "Bağlantınız geri geldiğinde kişisel oynatma listeniz otomatik olarak yenilenecek.",
                actionLabel = "Yeniden dene",
                onAction = {},
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(A2Spacing.xs)) {
                A2Badge("CANLI", tone = A2BadgeTone.Primary, stateDescription = "Canlı yayın")
                A2Badge("SEÇİLİ", tone = A2BadgeTone.Selected, stateDescription = "Seçili")
                A2Badge("BAĞLI", tone = A2BadgeTone.Success, stateDescription = "Bağlantı başarılı")
            }
            A2ProgressIndicator(
                progress = 0.64f,
                label = "Oynatma listesi eşitleniyor",
                valueLabel = "%64",
                contentDescription = "Oynatma listesi eşitleme ilerlemesi",
                modifier = Modifier.fillMaxWidth()
            )
            A2ProgressIndicator(
                label = "Program rehberi hazırlanıyor",
                valueLabel = "Bekleniyor",
                contentDescription = "Program rehberi hazırlama durumu",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "Loading and empty", group = "A2 status", widthDp = 720, heightDp = 420)
@Composable
private fun A2StatusVariantsPreview() {
    IdealPlayerTheme {
        Row(
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(A2Spacing.md)
        ) {
            A2StatusSurface(
                type = A2StatusType.Loading,
                title = "İçerikler hazırlanıyor",
                message = "Kişisel listenizdeki kanallar güvenli biçimde okunuyor.",
                modifier = Modifier.weight(1f)
            )
            A2StatusSurface(
                type = A2StatusType.Empty,
                title = "Henüz favori yok",
                message = "Beğendiğiniz film, dizi ve kanalları burada görmek için favorilerinize ekleyin.",
                actionLabel = "İçeriklere göz at",
                onAction = {},
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview(name = "Update and sync", group = "A2 status", widthDp = 720, heightDp = 430)
@Composable
private fun A2StatusProgressVariantsPreview() {
    IdealPlayerTheme {
        Row(
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(A2Spacing.md)
        ) {
            A2StatusSurface(
                type = A2StatusType.Updating,
                title = "Güncelleme indiriliyor",
                message = "Uygulamayı kapatmadan bekleyin.",
                progress = 0.42f,
                progressContentDescription = "Uygulama güncelleme ilerlemesi",
                progressValueLabel = "%42",
                modifier = Modifier.weight(1f)
            )
            A2StatusSurface(
                type = A2StatusType.Syncing,
                title = "Kaynak eşitleniyor",
                message = "Kanal ve program bilgileri güvenli biçimde yenileniyor.",
                progressContentDescription = "Kaynak eşitleme durumu",
                progressValueLabel = "Hazırlanıyor",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview(name = "TV error", group = "A2 status", widthDp = 960, heightDp = 540)
@Composable
private fun A2StatusTvPreview() {
    IdealPlayerTheme(isTv = true) {
        A2StatusSurface(
            type = A2StatusType.Error,
            title = "Yayın şu anda oynatılamıyor",
            message = "Oynatıcı motoru değiştirildi ancak ilk görüntü alınamadı. Bağlantınızı denetleyip yeniden deneyebilirsiniz.",
            actionLabel = "Yayını yeniden dene",
            onAction = {},
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.xxl)
                .fillMaxWidth()
        )
    }
}
