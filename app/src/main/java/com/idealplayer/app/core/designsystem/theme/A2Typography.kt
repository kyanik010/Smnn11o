package com.idealplayer.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun a2TextStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    color: Color = IdealPlayerColors.TextPrimary,
    letterSpacing: Float = 0f
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    color = color
)

val MobileIdealPlayerTypography = Typography(
    displayLarge = a2TextStyle(30, 36, FontWeight.Bold),
    displayMedium = a2TextStyle(28, 34, FontWeight.Bold, letterSpacing = -0.3f),
    displaySmall = a2TextStyle(24, 30, FontWeight.Bold),
    headlineLarge = a2TextStyle(22, 28, FontWeight.Bold),
    headlineMedium = a2TextStyle(20, 26, FontWeight.SemiBold),
    headlineSmall = a2TextStyle(18, 24, FontWeight.SemiBold),
    titleLarge = a2TextStyle(16, 22, FontWeight.Medium),
    titleMedium = a2TextStyle(14, 20, FontWeight.Medium),
    titleSmall = a2TextStyle(12, 16, FontWeight.Medium, IdealPlayerColors.TextSecondary),
    bodyLarge = a2TextStyle(14, 20, FontWeight.Normal),
    bodyMedium = a2TextStyle(14, 20, FontWeight.Normal, IdealPlayerColors.TextSecondary),
    bodySmall = a2TextStyle(12, 16, FontWeight.Normal, IdealPlayerColors.TextTertiary, 0.1f),
    labelLarge = a2TextStyle(14, 20, FontWeight.Medium, letterSpacing = 0.1f),
    labelMedium = a2TextStyle(12, 16, FontWeight.Medium, IdealPlayerColors.TextSecondary),
    labelSmall = a2TextStyle(12, 16, FontWeight.Medium, IdealPlayerColors.TextTertiary)
)

val TabletIdealPlayerTypography = Typography(
    displayLarge = a2TextStyle(36, 44, FontWeight.Bold),
    displayMedium = a2TextStyle(32, 40, FontWeight.Bold),
    displaySmall = a2TextStyle(28, 34, FontWeight.Bold),
    headlineLarge = a2TextStyle(28, 34, FontWeight.Bold),
    headlineMedium = a2TextStyle(24, 30, FontWeight.SemiBold),
    headlineSmall = a2TextStyle(22, 28, FontWeight.SemiBold),
    titleLarge = a2TextStyle(18, 24, FontWeight.Medium),
    titleMedium = a2TextStyle(16, 24, FontWeight.Medium),
    titleSmall = a2TextStyle(14, 20, FontWeight.Medium, IdealPlayerColors.TextSecondary),
    bodyLarge = a2TextStyle(16, 24, FontWeight.Normal),
    bodyMedium = a2TextStyle(16, 24, FontWeight.Normal, IdealPlayerColors.TextSecondary),
    bodySmall = a2TextStyle(14, 20, FontWeight.Normal, IdealPlayerColors.TextTertiary, 0.1f),
    labelLarge = a2TextStyle(16, 22, FontWeight.Medium, letterSpacing = 0.1f),
    labelMedium = a2TextStyle(14, 20, FontWeight.Medium, IdealPlayerColors.TextSecondary),
    labelSmall = a2TextStyle(14, 20, FontWeight.Medium, IdealPlayerColors.TextTertiary)
)

val TvIdealPlayerTypography = Typography(
    displayLarge = a2TextStyle(48, 56, FontWeight.Bold),
    displayMedium = a2TextStyle(40, 48, FontWeight.Bold),
    displaySmall = a2TextStyle(34, 42, FontWeight.Bold),
    headlineLarge = a2TextStyle(34, 42, FontWeight.Bold),
    headlineMedium = a2TextStyle(30, 38, FontWeight.SemiBold, letterSpacing = -0.2f),
    headlineSmall = a2TextStyle(26, 34, FontWeight.SemiBold),
    titleLarge = a2TextStyle(22, 28, FontWeight.SemiBold),
    titleMedium = a2TextStyle(20, 28, FontWeight.Medium),
    titleSmall = a2TextStyle(18, 24, FontWeight.Medium, IdealPlayerColors.TextSecondary),
    bodyLarge = a2TextStyle(20, 28, FontWeight.Normal),
    bodyMedium = a2TextStyle(18, 26, FontWeight.Normal, IdealPlayerColors.TextSecondary),
    bodySmall = a2TextStyle(16, 22, FontWeight.Normal, IdealPlayerColors.TextTertiary, 0.1f),
    labelLarge = a2TextStyle(18, 24, FontWeight.Medium, letterSpacing = 0.1f),
    labelMedium = a2TextStyle(16, 22, FontWeight.Medium, IdealPlayerColors.TextSecondary),
    labelSmall = a2TextStyle(16, 22, FontWeight.Medium, IdealPlayerColors.TextTertiary)
)

/** Backward-compatible mobile default; [IdealPlayerTheme] supplies the platform scale at runtime. */
val IdealPlayerTypography = MobileIdealPlayerTypography
