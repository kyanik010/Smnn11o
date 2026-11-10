package com.idealplayer.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.idealplayer.app.core.designsystem.theme.A2Motion
import com.idealplayer.app.core.designsystem.theme.IdealPlayerColors
import com.idealplayer.app.core.designsystem.theme.LocalIdealPlayerDimens

internal fun tvCategoryLazyKey(scope: String, category: String?): String = when (category) {
    null -> "$scope:all"
    else -> "$scope:item:$category"
}

@Composable
fun TvCategoryPanel(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    val dimens = LocalIdealPlayerDimens.current
    val panelShape = RoundedCornerShape(dimens.borderRadius)

    Box(
        modifier = modifier
            .width(dimens.categoryPanelWidth)
            .fillMaxHeight()
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(panelShape)
                .background(IdealPlayerColors.Surface)
                .border(
                    width = 1.dp,
                    color = IdealPlayerColors.CardBorder,
                    shape = panelShape
                )
                .padding(horizontal = 10.dp, vertical = 14.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                content = content
            )
        }
    }
}

@Composable
fun TvRailCategoryItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimens = LocalIdealPlayerDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val itemShape = RoundedCornerShape(14.dp)

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> IdealPlayerColors.SurfaceFocus
            isSelected -> IdealPlayerColors.SurfaceSelected
            else -> Color.Transparent
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvCategoryBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused || isSelected -> IdealPlayerColors.TextPrimary
            else -> IdealPlayerColors.TextSecondary
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvCategoryText"
    )

    val trailingGlowColor by animateColorAsState(
        targetValue = when {
            isFocused -> IdealPlayerColors.Primary.copy(alpha = 0.22f)
            isSelected -> IdealPlayerColors.Secondary.copy(alpha = 0.22f)
            else -> Color.Transparent
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvCategoryGlow"
    )

    val effectiveScale by animateFloatAsState(
        targetValue = when {
            isFocused -> A2Motion.FocusScale
            else -> 1f
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvCategoryEffectiveScale"
    )

    val effectiveBorderWidth by animateDpAsState(
        targetValue = when {
            isFocused -> 4.dp
            isSelected -> 2.dp
            else -> 0.dp
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvCategoryEffectiveBorderWidth"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> IdealPlayerColors.FocusBorder
            isSelected -> IdealPlayerColors.SelectedBorder
            else -> Color.Transparent
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvCategoryBorderColor"
    )

    val accentWidth by animateDpAsState(
        targetValue = when {
            isFocused -> 4.dp
            isSelected -> 4.dp
            else -> 0.dp
        },
        animationSpec = tween(A2Motion.StandardMillis),
        label = "tvCategoryAccentWidth"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.touchTargetMin)
            .graphicsLayer {
                scaleX = effectiveScale
                scaleY = effectiveScale
                this.shape = itemShape
                clip = false
            }
            .clip(itemShape)
            .background(backgroundColor)
            .border(effectiveBorderWidth, borderColor, itemShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                selected = isSelected
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(accentWidth)
                .height(36.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (isFocused) IdealPlayerColors.FocusBorder else IdealPlayerColors.Secondary)
        )
        Spacer(modifier = Modifier.width(if (accentWidth > 0.dp) 12.dp else 6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = when {
                isFocused || isSelected -> FontWeight.Bold
                else -> FontWeight.Medium
            },
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingGlowColor != Color.Transparent) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(trailingGlowColor)
            )
        }
    }
}
