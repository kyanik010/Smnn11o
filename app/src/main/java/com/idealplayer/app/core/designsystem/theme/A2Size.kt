package com.idealplayer.app.core.designsystem.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class IdealPlayerDimens(
    val screenPadding: Dp,
    val contentPadding: Dp,
    val cardWidth: Dp,
    val cardSpacing: Dp,
    val bannerHeight: Dp,
    val posterWidth: Dp,
    val sectionSpacing: Dp,
    val iconSize: Dp,
    val buttonHeight: Dp,
    val drawerWidth: Dp,
    val drawerCollapsedWidth: Dp,
    val categoryPanelWidth: Dp,
    val gridColumns: Int,
    val borderRadius: Dp,
    val focusBorderWidth: Dp,
    val touchTargetMin: Dp,
    val playerControlSize: Dp,
    val playerControlSpacing: Dp,
    val drawerGutter: Dp = 0.dp,
    val metadataTextSize: TextUnit = 12.sp
)

val TvDimens = IdealPlayerDimens(
    screenPadding = 48.dp,
    contentPadding = 24.dp,
    cardWidth = 216.dp,
    cardSpacing = 16.dp,
    bannerHeight = 320.dp,
    posterWidth = 200.dp,
    sectionSpacing = 24.dp,
    iconSize = 32.dp,
    buttonHeight = 56.dp,
    drawerWidth = 232.dp,
    drawerCollapsedWidth = 96.dp,
    drawerGutter = 24.dp,
    categoryPanelWidth = 242.dp,
    gridColumns = 5,
    borderRadius = 16.dp,
    focusBorderWidth = 4.dp,
    touchTargetMin = 56.dp,
    playerControlSize = 72.dp,
    playerControlSpacing = 40.dp,
    metadataTextSize = 16.sp
)

val MobileDimens = IdealPlayerDimens(
    screenPadding = 16.dp,
    contentPadding = 12.dp,
    cardWidth = 132.dp,
    cardSpacing = 8.dp,
    bannerHeight = 208.dp,
    posterWidth = 110.dp,
    sectionSpacing = 16.dp,
    iconSize = 24.dp,
    buttonHeight = 48.dp,
    drawerWidth = 0.dp,
    drawerCollapsedWidth = 0.dp,
    drawerGutter = 0.dp,
    categoryPanelWidth = 0.dp,
    gridColumns = 2,
    borderRadius = 12.dp,
    focusBorderWidth = 2.dp,
    touchTargetMin = 48.dp,
    playerControlSize = 56.dp,
    playerControlSpacing = 28.dp,
    metadataTextSize = 12.sp
)

val TabletDimens = IdealPlayerDimens(
    screenPadding = 24.dp,
    contentPadding = 16.dp,
    cardWidth = 220.dp,
    cardSpacing = 12.dp,
    bannerHeight = 280.dp,
    posterWidth = 160.dp,
    sectionSpacing = 20.dp,
    iconSize = 26.dp,
    buttonHeight = 48.dp,
    drawerWidth = 80.dp,
    drawerCollapsedWidth = 80.dp,
    drawerGutter = 0.dp,
    categoryPanelWidth = 220.dp,
    gridColumns = 4,
    borderRadius = 14.dp,
    focusBorderWidth = 2.dp,
    touchTargetMin = 48.dp,
    playerControlSize = 64.dp,
    playerControlSpacing = 32.dp,
    metadataTextSize = 14.sp
)

val LocalIdealPlayerDimens = compositionLocalOf { MobileDimens }
