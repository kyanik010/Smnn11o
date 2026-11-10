package com.idealplayer.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.idealplayer.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.idealplayer.app.core.common.orderCategoryNames
import com.idealplayer.app.core.common.isUsableArtworkUrl
import com.idealplayer.app.core.designsystem.theme.A2Motion
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens
import com.idealplayer.app.ui.components.a2.A2ActionButton
import com.idealplayer.app.ui.components.a2.A2ActionVariant
import com.idealplayer.app.ui.components.a2.A2ArtworkFallback
import com.idealplayer.app.ui.components.a2.A2StatusSurface
import com.idealplayer.app.ui.components.a2.A2StatusType
import java.util.Locale

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Card Components
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun IdealPlayerCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    isTv: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) A2Motion.FocusScale else 1f,
        animationSpec = tween(A2Motion.StandardMillis), label = "cardScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) IdealPlayerColors.FocusBorder else IdealPlayerColors.CardBorder,
        animationSpec = tween(A2Motion.StandardMillis), label = "cardBorder"
    )
    val borderWidth = if (isFocused) {
        if (isTv) LocalIdealPlayerDimens.current.focusBorderWidth else 2.dp
    } else {
        1.dp
    }

    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = IdealPlayerColors.CardBackground),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, focusedElevation = if (isTv) 16.dp else 12.dp),
        onClick = onClick
    ) {
        Column(content = content)
    }
}

@Composable
fun ContentPosterCard(
    title: String,
    posterUrl: String,
    modifier: Modifier = Modifier,
    rating: Double = 0.0,
    year: Int = 0,
    isTv: Boolean = false,
    cardWidth: Dp? = null,
    onPosterError: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val dimens = LocalIdealPlayerDimens.current
    val width = cardWidth ?: dimens.cardWidth
    val interactionSource = remember(title, posterUrl, year, isTv) { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val tvFocusState = if (isTv) {
        rememberTvFocusVisualState(
            isFocused = isFocused,
            defaultSurface = IdealPlayerColors.CardBackground,
            selectedSurface = IdealPlayerColors.CardBackground,
            focusedSurface = IdealPlayerColors.SurfaceFocus,
            selectedFocusedSurface = IdealPlayerColors.SurfaceFocus
        )
    } else {
        null
    }
    val scale by animateFloatAsState(
        targetValue = when {
            tvFocusState != null -> tvFocusState.scale
            isFocused -> A2Motion.FocusScale
            else -> 1f
        },
        animationSpec = tween(A2Motion.StandardMillis), label = "posterScale"
    )
    val borderWidth by animateDpAsState(
        targetValue = when {
            tvFocusState != null -> tvFocusState.borderWidth
            isFocused -> dimens.focusBorderWidth
            else -> 0.dp
        },
        animationSpec = tween(A2Motion.StandardMillis), label = "posterBorderWidth"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.14f else 0f,
        animationSpec = tween(A2Motion.StandardMillis), label = "glowAlpha"
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            tvFocusState != null -> tvFocusState.backgroundColor
            isFocused -> IdealPlayerColors.SurfaceFocus
            else -> IdealPlayerColors.CardBackground
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "posterBackground"
    )
    val focusTint by animateColorAsState(
        targetValue = when {
            tvFocusState != null && isFocused -> IdealPlayerColors.Primary.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "posterFocusTint"
    )

    Column(
        modifier = modifier
            .width(width)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (isFocused) tvFocusState?.shadowElevation ?: 12.dp else 0.dp,
                shape = RoundedCornerShape(dimens.borderRadius),
                clip = false,
                ambientColor = (tvFocusState?.glowColor ?: IdealPlayerColors.FocusGlow).copy(alpha = glowAlpha),
                spotColor = (tvFocusState?.glowColor ?: IdealPlayerColors.FocusGlow).copy(alpha = glowAlpha)
            )
            .clip(RoundedCornerShape(dimens.borderRadius))
            .background(backgroundColor, RoundedCornerShape(dimens.borderRadius))
            .then(
                if (isFocused) {
                    Modifier.border(
                        borderWidth.coerceAtLeast(if (isTv) 4.dp else borderWidth),
                        tvFocusState?.borderColor ?: IdealPlayerColors.FocusBorder,
                        RoundedCornerShape(dimens.borderRadius)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = if (isTv) null else LocalIndication.current,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            PosterImage(
                url = posterUrl,
                contentDescription = title,
                onError = onPosterError,
                modifier = Modifier.fillMaxSize()
            )
            if (focusTint.alpha > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(focusTint)
                )
            }

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, IdealPlayerColors.CardBackground)
                        )
                    )
            )

            if (rating > 0) {
                RatingBadge(
                    rating = rating,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when {
                        isTv && isFocused -> IdealPlayerColors.SurfaceFocus
                        else -> Color.Transparent
                    }
                )
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = tvFocusState?.contentColor ?: IdealPlayerColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (year > 0) {
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = tvFocusState?.secondaryContentColor
                        ?: if (isFocused) IdealPlayerColors.TextSecondary else IdealPlayerColors.TextTertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Image & Loading Components
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun PosterImage(
    url: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackStyle: ArtworkFallbackStyle = ArtworkFallbackStyle.Cinematic,
    onError: () -> Unit = {}
) {
    val safeUrl = url.takeIf(::isUsableArtworkUrl)
    val isTvLayout = LocalIdealPlayerDimens.current.touchTargetMin >= 56.dp
    var isLoading by remember { mutableStateOf(true) }
    var hasImageError by remember(url) { mutableStateOf(false) }

    Box(modifier = modifier) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(safeUrl)
                .crossfade(!isTvLayout)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            onState = { state ->
                isLoading = state is AsyncImagePainter.State.Loading
                hasImageError = state is AsyncImagePainter.State.Error
                if (state is AsyncImagePainter.State.Error && safeUrl != null) onError()
            }
        )

        if (isLoading) {
            ShimmerBox(modifier = Modifier.fillMaxSize())
        } else if (safeUrl == null || hasImageError) {
            PosterFallback(
                title = contentDescription.takeIf { it.isNotBlank() && !it.equals("Backdrop", ignoreCase = true) },
                style = fallbackStyle,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

enum class ArtworkFallbackStyle {
    Cinematic,
    Channel
}

@Composable
private fun PosterFallback(
    title: String?,
    style: ArtworkFallbackStyle,
    modifier: Modifier = Modifier
) {
    if (style == ArtworkFallbackStyle.Channel) {
        val safeTitle = title.orEmpty()
        val hue = remember(safeTitle) {
            (safeTitle.hashCode().and(Int.MAX_VALUE) % 360).toFloat()
        }
        val monogram = remember(safeTitle) { channelMonogram(safeTitle) }
        Box(
            modifier = modifier.background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.hsv(hue, 0.58f, 0.50f),
                        Color.hsv((hue + 28f) % 360f, 0.72f, 0.24f)
                    )
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = monogram,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                maxLines = 1
            )
        }
        return
    }

    Box(
        modifier = modifier.background(IdealPlayerColors.SurfaceElevated)
    ) {
        A2ArtworkFallback(
            seed = title.orEmpty(),
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to IdealPlayerColors.Background.copy(alpha = 0.30f)
                    )
                )
        )
    }
}

private fun channelMonogram(title: String): String {
    val ignored = setOf(
        "TR", "TV", "HD", "FHD", "UHD", "SD", "4K", "RAW", "HEVC", "VIP", "YEDEK"
    )
    val tokens = title
        .uppercase(Locale.ROOT)
        .replace(Regex("[^A-Z0-9ÇĞİÖŞÜ]+"), " ")
        .split(' ')
        .filter { token -> token.isNotBlank() && token !in ignored }
    return tokens
        .take(3)
        .joinToString("") { token -> token.take(1) }
        .ifBlank { "TV" }
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val isTvLayout = LocalIdealPlayerDimens.current.touchTargetMin >= 56.dp
    if (isTvLayout) {
        Box(modifier = modifier.background(IdealPlayerColors.Shimmer))
        return
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    Box(
        modifier = modifier
            .background(IdealPlayerColors.Shimmer)
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            IdealPlayerColors.Shimmer,
                            IdealPlayerColors.ShimmerHighlight,
                            IdealPlayerColors.Shimmer
                        ),
                        start = Offset(shimmerOffset - 200, 0f),
                        end = Offset(shimmerOffset, size.height)
                    )
                )
            }
    )
}

@Composable
fun RatingBadge(
    rating: Double,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = IdealPlayerColors.RatingStarColor.copy(alpha = 0.9f)
    ) {
        Text(
            text = String.format(Locale.getDefault(), "%.1f", rating),
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Button Components
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isTv: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    A2ActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier
            .then(if (isTv) Modifier.defaultMinSize(minHeight = 56.dp) else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        variant = A2ActionVariant.Primary,
        icon = icon,
        enabled = enabled,
        contentDescription = text
    )
}

@Composable
fun IdealPlayerOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isTv: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    A2ActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier
            .then(if (isTv) Modifier.defaultMinSize(minHeight = 56.dp) else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        variant = A2ActionVariant.Secondary,
        icon = icon,
        enabled = enabled,
        contentDescription = text
    )
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Glass / Layout Components
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(IdealPlayerColors.GlassBackground)
            .border(1.dp, IdealPlayerColors.GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = IdealPlayerColors.TextPrimary
        )
        trailing?.invoke()
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// State Screens
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IdealPlayerColors.Background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = IdealPlayerColors.Primary)
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IdealPlayerColors.Background),
        contentAlignment = Alignment.Center
    ) {
        A2StatusSurface(
            type = A2StatusType.Error,
            title = stringResource(R.string.error_occurred),
            message = message,
            actionLabel = stringResource(R.string.retry),
            onAction = onRetry,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .widthIn(max = 560.dp)
        )
    }
}

@Composable
fun EmptyScreen(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IdealPlayerColors.Background),
        contentAlignment = Alignment.Center
    ) {
        A2StatusSurface(
            type = A2StatusType.Empty,
            title = stringResource(R.string.no_content),
            message = message,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .widthIn(max = 560.dp)
        )
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TV Category (untouched — TV code preserved)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
fun TvCategoryItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val dimens = LocalIdealPlayerDimens.current
    val scale by animateFloatAsState(
        targetValue = if (isFocused) A2Motion.FocusScale else 1f,
        animationSpec = tween(A2Motion.StandardMillis),
        label = "legacyTvCategoryScale"
    )
    val bg = when {
        isFocused -> IdealPlayerColors.SurfaceFocus
        isSelected -> IdealPlayerColors.SurfaceSelected
        else -> Color.Transparent
    }

    Text(
        text = name,
        style = MaterialTheme.typography.titleSmall,
        color = when {
            isSelected && isFocused -> IdealPlayerColors.TextPrimary
            isSelected -> IdealPlayerColors.Secondary
            isFocused -> IdealPlayerColors.TextPrimary
            else -> IdealPlayerColors.TextSecondary
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.touchTargetMin)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(
                if (isFocused) Modifier.border(
                    width = dimens.focusBorderWidth,
                    color = IdealPlayerColors.FocusBorder,
                    shape = RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                selected = isSelected
            }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// MOBILE CATEGORY UX — Bottom Sheet + Quick Bar
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Quick category bar: "All" + the first source-order categories + "Browse" button.
 * Used at the top of Movies/Series/LiveTV mobile screens.
 */
@Composable
fun QuickCategoryBar(
    categories: List<String>,
    selectedCategory: String?,
    recentCategories: List<String>,
    onCategorySelected: (String?) -> Unit,
    onBrowseAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalIdealPlayerDimens.current
    val allLabel = stringResource(R.string.category_all)
    val quickList = buildList {
        add(allLabel)
        categories.take(5).forEach { add(it) }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(quickList) { cat ->
            val isAll = cat == allLabel
            val isSelected = if (isAll) selectedCategory == null else selectedCategory == cat
            FilterChip(
                modifier = Modifier.heightIn(min = dimens.touchTargetMin),
                selected = isSelected,
                onClick = { onCategorySelected(if (isAll) null else cat) },
                label = { Text(cat, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = IdealPlayerColors.SurfaceSelected,
                    selectedLabelColor = IdealPlayerColors.Secondary,
                    containerColor = IdealPlayerColors.SurfaceVariant,
                    labelColor = IdealPlayerColors.TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = Color.Transparent,
                    selectedBorderColor = IdealPlayerColors.SelectedBorder,
                    enabled = true,
                    selected = isSelected
                )
            )
        }

        // "Browse All" button
        item {
            AssistChip(
                modifier = Modifier.heightIn(min = dimens.touchTargetMin),
                onClick = onBrowseAll,
                label = { Text(stringResource(R.string.action_browse_all)) },
                leadingIcon = {
                    Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = IdealPlayerColors.SurfaceElevated,
                    labelColor = IdealPlayerColors.TextPrimary,
                    leadingIconContentColor = IdealPlayerColors.Primary
                ),
                border = AssistChipDefaults.assistChipBorder(
                    borderColor = IdealPlayerColors.Primary.copy(alpha = 0.3f),
                    enabled = true
                )
            )
        }
    }
}

/**
 * Full-height category bottom sheet with search and source-order item counts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBottomSheet(
    isVisible: Boolean,
    categories: List<String>,
    categoryCounts: Map<String, Int>,
    selectedCategory: String?,
    recentCategories: List<String>,
    pinnedCategories: List<String>,
    onCategorySelected: (String?) -> Unit,
    onDismiss: () -> Unit,
    onTogglePin: (String) -> Unit
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val filteredCategories = remember(categories, searchQuery) {
        val orderedCategories = orderCategoryNames(categories)
        if (searchQuery.isBlank()) {
            orderedCategories
        } else {
            orderedCategories.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = IdealPlayerColors.Surface,
        contentColor = IdealPlayerColors.TextPrimary,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(IdealPlayerColors.TextTertiary)
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.action_browse_all), style = MaterialTheme.typography.headlineSmall, color = IdealPlayerColors.TextPrimary)
                Text(
                    pluralStringResource(R.plurals.category_count, categories.size, categories.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = IdealPlayerColors.TextTertiary
                )
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                placeholder = { Text(stringResource(R.string.category_search_hint), color = IdealPlayerColors.TextTertiary) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = IdealPlayerColors.TextTertiary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                stringResource(R.string.action_clear_search),
                                tint = IdealPlayerColors.TextTertiary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IdealPlayerColors.Primary,
                    unfocusedBorderColor = IdealPlayerColors.CardBorder,
                    focusedContainerColor = IdealPlayerColors.SurfaceVariant,
                    unfocusedContainerColor = IdealPlayerColors.SurfaceVariant,
                    focusedTextColor = IdealPlayerColors.TextPrimary,
                    unfocusedTextColor = IdealPlayerColors.TextPrimary,
                    cursorColor = IdealPlayerColors.Primary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // "All" option
            CategorySheetItem(
                name = stringResource(R.string.all_categories),
                count = categories.sumOf { categoryCounts[it] ?: 0 },
                isSelected = selectedCategory == null,
                isPinned = false,
                onClick = { onCategorySelected(null); onDismiss() },
                onTogglePin = {}
            )

            HorizontalDivider(color = IdealPlayerColors.DividerColor, modifier = Modifier.padding(horizontal = 20.dp))

            // Category list
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(filteredCategories, key = { it }) { cat ->
                    CategorySheetItem(
                        name = cat,
                        count = categoryCounts[cat] ?: 0,
                        isSelected = selectedCategory == cat,
                        isPinned = false,
                        onClick = { onCategorySelected(cat); onDismiss() },
                        onTogglePin = {}
                    )
                }

                if (filteredCategories.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.category_no_matches, searchQuery),
                            style = MaterialTheme.typography.bodyMedium,
                            color = IdealPlayerColors.TextTertiary,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }

            // Clear filter button
            if (selectedCategory != null) {
                TextButton(
                    onClick = { onCategorySelected(null); onDismiss() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.ClearAll, null, tint = IdealPlayerColors.Primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_clear_filter), color = IdealPlayerColors.Primary)
                }
            }
        }
    }
}

@Composable
private fun CategorySheetItem(
    name: String,
    count: Int,
    isSelected: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) IdealPlayerColors.SurfaceSelected else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (isSelected) {
                Icon(Icons.Filled.Check, null, tint = IdealPlayerColors.Secondary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) IdealPlayerColors.Secondary else IdealPlayerColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (count > 0) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.bodySmall,
                    color = IdealPlayerColors.TextTertiary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}
