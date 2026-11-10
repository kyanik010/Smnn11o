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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idealplayer.app.core.designsystem.theme.A2Motion
import com.idealplayer.app.core.designsystem.theme.A2Shape
import com.idealplayer.app.core.designsystem.theme.A2Spacing
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.IdealPlayerTheme
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens

/**
 * Layout families supported by [A2ContentCard]. Existing poster call sites can retain
 * `ContentPosterCard` until their route is migrated.
 */
enum class A2ContentCardKind {
    Movie,
    Series,
    Landscape,
    Channel,
    Programme,
    Episode,
    ContinueWatching,
    Playlist,
    Category
}

private enum class CardPreviewInteraction {
    Runtime,
    Focused,
    Pressed
}

/**
 * Stateless card primitive for landscape, channel, programme, episode, continue-watching,
 * playlist and category surfaces. Callers own artwork loading, progress state and navigation.
 */
@Composable
fun A2ContentCard(
    kind: A2ContentCardKind,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    metadata: String? = null,
    contentDescription: String = title,
    stateDescription: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    progress: Float? = null,
    badgeText: String? = null,
    badgeTone: A2BadgeTone = A2BadgeTone.Neutral,
    placeholderIcon: ImageVector = defaultCardIcon(kind),
    artworkContentDescription: String? = null,
    artwork: (@Composable BoxScope.() -> Unit)? = null
) {
    A2ContentCardImpl(
        kind = kind,
        title = title,
        onClick = onClick,
        modifier = modifier,
        subtitle = subtitle,
        metadata = metadata,
        contentDescription = contentDescription,
        stateDescription = stateDescription,
        enabled = enabled,
        selected = selected,
        progress = progress,
        badgeText = badgeText,
        badgeTone = badgeTone,
        placeholderIcon = placeholderIcon,
        artworkContentDescription = artworkContentDescription,
        artwork = artwork,
        previewInteraction = CardPreviewInteraction.Runtime
    )
}

@Composable
private fun A2ContentCardImpl(
    kind: A2ContentCardKind,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier,
    subtitle: String?,
    metadata: String?,
    contentDescription: String,
    stateDescription: String?,
    enabled: Boolean,
    selected: Boolean,
    progress: Float?,
    badgeText: String?,
    badgeTone: A2BadgeTone,
    placeholderIcon: ImageVector,
    artworkContentDescription: String?,
    artwork: (@Composable BoxScope.() -> Unit)?,
    previewInteraction: CardPreviewInteraction
) {
    val dimens = LocalIdealPlayerDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val runtimeFocused by interactionSource.collectIsFocusedAsState()
    val runtimePressed by interactionSource.collectIsPressedAsState()
    val focused = previewInteraction == CardPreviewInteraction.Focused ||
        (previewInteraction == CardPreviewInteraction.Runtime && runtimeFocused)
    val pressed = previewInteraction == CardPreviewInteraction.Pressed ||
        (previewInteraction == CardPreviewInteraction.Runtime && runtimePressed)
    val isRow = kind == A2ContentCardKind.Channel ||
        kind == A2ContentCardKind.Playlist ||
        kind == A2ContentCardKind.Category
    val container by animateColorAsState(
        targetValue = when {
            focused -> IdealPlayerColors.SurfaceFocus
            selected -> IdealPlayerColors.SurfaceSelected
            else -> IdealPlayerColors.CardBackground
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2ContentCardContainer"
    )
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.98f
            focused -> A2Motion.FocusScale
            else -> 1f
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2ContentCardScale"
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            !enabled -> A2Motion.DisabledAlpha
            pressed -> A2Motion.PressedAlpha
            else -> 1f
        },
        animationSpec = tween(A2Motion.FastMillis),
        label = "a2ContentCardAlpha"
    )
    val normalizedProgress = progress?.coerceIn(0f, 1f)

    val cardModifier = modifier
        .defaultMinSize(minWidth = dimens.touchTargetMin, minHeight = dimens.touchTargetMin)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .then(
            if (focused && dimens.touchTargetMin < 56.dp) {
                Modifier.shadow(
                    elevation = 8.dp,
                    shape = A2Shape.medium,
                    ambientColor = IdealPlayerColors.FocusGlow,
                    spotColor = IdealPlayerColors.FocusGlow
                )
            } else {
                Modifier
            }
        )
        .clip(A2Shape.medium)
        .background(container)
        .border(
            width = when {
                focused -> dimens.focusBorderWidth
                selected -> 2.dp
                else -> 1.dp
            },
            color = when {
                focused -> IdealPlayerColors.FocusBorder
                selected -> IdealPlayerColors.SelectedBorder
                else -> IdealPlayerColors.CardBorder
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
            this.contentDescription = contentDescription
            this.selected = selected
            stateDescription?.let { this.stateDescription = it }
        }

    if (isRow) {
        Row(
            modifier = cardModifier.padding(A2Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardArtwork(
                modifier = Modifier.size(if (dimens.touchTargetMin >= 56.dp) 96.dp else 72.dp),
                placeholderIcon = placeholderIcon,
                artworkContentDescription = artworkContentDescription,
                artwork = artwork,
                progress = normalizedProgress
            )
            Spacer(Modifier.width(A2Spacing.md))
            CardText(
                title = title,
                subtitle = subtitle,
                metadata = metadata,
                badgeText = badgeText,
                badgeTone = badgeTone,
                modifier = Modifier.weight(1f)
            )
        }
    } else if (kind == A2ContentCardKind.Movie || kind == A2ContentCardKind.Series) {
        PosterCardLayout(
            modifier = cardModifier,
            title = title,
            subtitle = subtitle,
            metadata = metadata,
            placeholderIcon = placeholderIcon,
            artworkContentDescription = artworkContentDescription,
            artwork = artwork,
            progress = normalizedProgress,
            badgeText = badgeText,
            badgeTone = badgeTone
        )
    } else if (kind == A2ContentCardKind.ContinueWatching) {
        ContinueWatchingCardLayout(
            modifier = cardModifier,
            title = title,
            subtitle = subtitle,
            metadata = metadata,
            placeholderIcon = placeholderIcon,
            artworkContentDescription = artworkContentDescription,
            artwork = artwork,
            progress = normalizedProgress,
            badgeText = badgeText,
            badgeTone = badgeTone
        )
    } else if (kind == A2ContentCardKind.Landscape) {
        LandscapeCardLayout(
            modifier = cardModifier,
            title = title,
            subtitle = subtitle,
            metadata = metadata,
            placeholderIcon = placeholderIcon,
            artworkContentDescription = artworkContentDescription,
            artwork = artwork,
            progress = normalizedProgress,
            badgeText = badgeText,
            badgeTone = badgeTone
        )
    } else {
        Column(modifier = cardModifier) {
            CardArtwork(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        if (kind == A2ContentCardKind.Movie || kind == A2ContentCardKind.Series) {
                            2f / 3f
                        } else {
                            16f / 9f
                        }
                    ),
                placeholderIcon = placeholderIcon,
                artworkContentDescription = artworkContentDescription,
                artwork = artwork,
                progress = normalizedProgress,
                badgeText = badgeText,
                badgeTone = badgeTone
            )
            CardText(
                title = title,
                subtitle = subtitle,
                metadata = metadata,
                badgeText = null,
                badgeTone = badgeTone,
                modifier = Modifier.padding(A2Spacing.sm)
            )
        }
    }
}

/** Canonical A2 poster geometry with a responsive 2:3 mobile/tablet artwork surface. */
@Composable
private fun PosterCardLayout(
    modifier: Modifier,
    title: String,
    subtitle: String?,
    metadata: String?,
    placeholderIcon: ImageVector,
    artworkContentDescription: String?,
    artwork: (@Composable BoxScope.() -> Unit)?,
    progress: Float?,
    badgeText: String?,
    badgeTone: A2BadgeTone
) {
    val dimens = LocalIdealPlayerDimens.current
    val isTv = dimens.touchTargetMin >= 56.dp
    val artworkWidth = dimens.posterWidth

    Column(
        modifier = modifier.padding(A2Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.Start
    ) {
        CardArtwork(
            modifier = Modifier
                .then(
                    if (isTv) {
                        Modifier.width(artworkWidth).height(260.dp)
                    } else {
                        Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                    }
                ),
            placeholderIcon = placeholderIcon,
            artworkContentDescription = artworkContentDescription,
            artwork = artwork,
            progress = progress,
            badgeText = badgeText,
            badgeTone = badgeTone
        )
        CompactTwoLineCardText(
            title = title,
            secondary = combineCardMetadata(subtitle, metadata),
            modifier = if (isTv) Modifier.width(artworkWidth) else Modifier.fillMaxWidth(),
            textGap = 6.dp
        )
    }
}

/** A2 Continue Watching reserves a dedicated artwork band and a non-clipping info band. */
@Composable
private fun ContinueWatchingCardLayout(
    modifier: Modifier,
    title: String,
    subtitle: String?,
    metadata: String?,
    placeholderIcon: ImageVector,
    artworkContentDescription: String?,
    artwork: (@Composable BoxScope.() -> Unit)?,
    progress: Float?,
    badgeText: String?,
    badgeTone: A2BadgeTone
) {
    val dimens = LocalIdealPlayerDimens.current
    val isTv = dimens.touchTargetMin >= 56.dp

    Column(modifier = modifier) {
        CardArtwork(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTv) 112.dp else 80.dp),
            placeholderIcon = placeholderIcon,
            artworkContentDescription = artworkContentDescription,
            artwork = artwork,
            progress = progress,
            badgeText = badgeText,
            badgeTone = badgeTone
        )
        DenseThreeLineCardText(
            title = title,
            subtitle = subtitle,
            metadata = metadata,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTv) 80.dp else 60.dp)
                .padding(horizontal = A2Spacing.xs, vertical = A2Spacing.xxs)
        )
    }
}

/** Canonical favorite/landscape card: 272 x 192 dp on TV with 256 x 126 dp artwork. */
@Composable
private fun LandscapeCardLayout(
    modifier: Modifier,
    title: String,
    subtitle: String?,
    metadata: String?,
    placeholderIcon: ImageVector,
    artworkContentDescription: String?,
    artwork: (@Composable BoxScope.() -> Unit)?,
    progress: Float?,
    badgeText: String?,
    badgeTone: A2BadgeTone
) {
    val dimens = LocalIdealPlayerDimens.current
    val isTv = dimens.touchTargetMin >= 56.dp

    Column(
        modifier = modifier.padding(A2Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CardArtwork(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isTv) {
                        Modifier.height(126.dp)
                    } else {
                        Modifier.aspectRatio(16f / 9f)
                    }
                ),
            placeholderIcon = placeholderIcon,
            artworkContentDescription = artworkContentDescription,
            artwork = artwork,
            progress = progress,
            badgeText = badgeText,
            badgeTone = badgeTone
        )
        CompactTwoLineCardText(
            title = title,
            secondary = combineCardMetadata(subtitle, metadata),
            modifier = Modifier.fillMaxWidth(),
            textGap = if (isTv) 2.dp else A2Spacing.xxs
        )
    }
}

@Composable
private fun CompactTwoLineCardText(
    title: String,
    secondary: String?,
    modifier: Modifier,
    textGap: androidx.compose.ui.unit.Dp
) {
    val dimens = LocalIdealPlayerDimens.current
    val isTv = dimens.touchTargetMin >= 56.dp
    val isTablet = !isTv && dimens.gridColumns >= 4
    val titleStyle = MaterialTheme.typography.titleSmall.copy(
        fontSize = when {
            isTv -> 19.sp
            isTablet -> 16.sp
            else -> 13.sp
        },
        lineHeight = when {
            isTv -> 20.sp
            isTablet -> 20.sp
            else -> 16.sp
        },
        fontWeight = FontWeight.SemiBold
    )
    val metadataStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = when {
            isTv -> 18.sp
            isTablet -> dimens.metadataTextSize
            else -> 12.sp
        },
        lineHeight = when {
            isTv -> 20.sp
            isTablet -> 18.sp
            else -> 14.sp
        },
        fontWeight = FontWeight.Medium
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(textGap)) {
        Text(
            text = title,
            color = IdealPlayerColors.TextPrimary,
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (secondary != null) {
            Text(
                text = secondary,
                color = IdealPlayerColors.TextSecondary,
                style = metadataStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DenseThreeLineCardText(
    title: String,
    subtitle: String?,
    metadata: String?,
    modifier: Modifier
) {
    val dimens = LocalIdealPlayerDimens.current
    val isTv = dimens.touchTargetMin >= 56.dp
    val titleStyle = MaterialTheme.typography.titleSmall.copy(
        fontSize = if (isTv) 18.sp else 14.sp,
        lineHeight = if (isTv) 20.sp else 18.sp,
        fontWeight = FontWeight.SemiBold
    )
    val supportingStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = dimens.metadataTextSize,
        lineHeight = if (isTv) 18.sp else 15.sp
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = title,
            color = IdealPlayerColors.TextPrimary,
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = IdealPlayerColors.TextSecondary,
                style = supportingStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (metadata != null) {
            Text(
                text = metadata,
                color = IdealPlayerColors.TextTertiary,
                style = supportingStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun combineCardMetadata(subtitle: String?, metadata: String?): String? =
    listOfNotNull(subtitle, metadata)
        .filter(String::isNotBlank)
        .joinToString(" • ")
        .takeIf(String::isNotBlank)

@Composable
private fun CardArtwork(
    modifier: Modifier,
    placeholderIcon: ImageVector,
    artworkContentDescription: String?,
    artwork: (@Composable BoxScope.() -> Unit)?,
    progress: Float?,
    badgeText: String? = null,
    badgeTone: A2BadgeTone = A2BadgeTone.Neutral
) {
    Box(
        modifier = modifier
            .clip(A2Shape.small)
            .background(
                Brush.verticalGradient(
                    colors = listOf(IdealPlayerColors.SurfaceElevated, IdealPlayerColors.SurfaceVariant)
                )
            )
            .semantics {
                artworkContentDescription?.let { this.contentDescription = it }
            },
        contentAlignment = Alignment.Center
    ) {
        if (artwork != null) {
            artwork()
        } else {
            A2ArtworkFallback(
                seed = artworkContentDescription.orEmpty() + placeholderIcon.hashCode(),
                modifier = Modifier.fillMaxWidth().fillMaxHeight()
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(A2Shape.full)
                    .background(IdealPlayerColors.Background.copy(alpha = 0.52f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = null,
                    tint = IdealPlayerColors.TextSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, IdealPlayerColors.CardBackground))
                )
        )
        if (badgeText != null) {
            A2Badge(
                text = badgeText,
                tone = badgeTone,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(A2Spacing.xs)
            )
        }
        if (progress != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = A2Spacing.xs, vertical = 5.dp)
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(A2Shape.full)
                    .background(IdealPlayerColors.SurfaceElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(A2Shape.full)
                        .background(IdealPlayerColors.Primary)
                )
            }
        }
    }
}

@Composable
private fun CardText(
    title: String,
    subtitle: String?,
    metadata: String?,
    badgeText: String?,
    badgeTone: A2BadgeTone,
    modifier: Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(A2Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = IdealPlayerColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (badgeText != null) {
                Spacer(Modifier.width(A2Spacing.xs))
                A2Badge(text = badgeText, tone = badgeTone)
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = IdealPlayerColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (metadata != null) {
            Text(
                text = metadata,
                color = IdealPlayerColors.TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun defaultCardIcon(kind: A2ContentCardKind): ImageVector = when (kind) {
    A2ContentCardKind.Channel,
    A2ContentCardKind.Programme -> Icons.Filled.LiveTv
    A2ContentCardKind.Playlist,
    A2ContentCardKind.Category -> Icons.AutoMirrored.Filled.PlaylistPlay
    A2ContentCardKind.Movie,
    A2ContentCardKind.Series,
    A2ContentCardKind.Landscape,
    A2ContentCardKind.Episode,
    A2ContentCardKind.ContinueWatching -> Icons.Filled.VideoLibrary
}

@Preview(name = "Mobile card families", group = "A2 cards", widthDp = 390, heightDp = 760)
@Composable
private fun A2CardsMobilePreview() {
    IdealPlayerTheme {
        Column(
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.md),
            verticalArrangement = Arrangement.spacedBy(A2Spacing.md)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(A2Spacing.sm)) {
                A2ContentCard(
                    kind = A2ContentCardKind.Movie,
                    title = "İstanbul'da Uzun Bir Gece",
                    subtitle = "Dram • 2026",
                    metadata = "2 sa 14 dk",
                    badgeText = "YENİ",
                    badgeTone = A2BadgeTone.Primary,
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
                A2ContentCard(
                    kind = A2ContentCardKind.ContinueWatching,
                    title = "Kaldığınız yerden devam edin",
                    subtitle = "3. bölüm • Sessiz Kıyılar",
                    progress = 0.63f,
                    stateDescription = "%63 izlendi",
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
            }
            A2ContentCard(
                kind = A2ContentCardKind.Channel,
                title = "Kişisel Haber Kanalı",
                subtitle = "Şimdi: Günün gelişmeleri ve ayrıntılı değerlendirme",
                metadata = "20:00–21:30",
                badgeText = "CANLI",
                badgeTone = A2BadgeTone.Primary,
                onClick = {},
                selected = true,
                stateDescription = "Seçili kanal",
                modifier = Modifier.fillMaxWidth()
            )
            A2ContentCard(
                kind = A2ContentCardKind.Playlist,
                title = "Ev televizyonu için kişisel oynatma listem",
                subtitle = "1.248 kanal • Son eşitleme bugün 18:42",
                badgeText = "ETKİN",
                badgeTone = A2BadgeTone.Success,
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "TV focused episode", group = "A2 cards", widthDp = 960, heightDp = 440)
@Composable
private fun A2CardsTvPreview() {
    IdealPlayerTheme(isTv = true) {
        A2ContentCardImpl(
            kind = A2ContentCardKind.Episode,
            title = "12. Bölüm — Yolculuğun Beklenmedik Son Durağı",
            onClick = {},
            modifier = Modifier
                .background(IdealPlayerColors.Background)
                .padding(A2Spacing.xl)
                .width(420.dp),
            subtitle = "Kahramanlar uzun süredir aradıkları yanıtı bulmak üzere son kez bir araya gelir.",
            metadata = "52 dk",
            contentDescription = "12. bölümü oynat",
            stateDescription = "Odaklandı",
            enabled = true,
            selected = false,
            progress = 0.28f,
            badgeText = null,
            badgeTone = A2BadgeTone.Neutral,
            placeholderIcon = Icons.Filled.Image,
            artworkContentDescription = null,
            artwork = null,
            previewInteraction = CardPreviewInteraction.Focused
        )
    }
}
