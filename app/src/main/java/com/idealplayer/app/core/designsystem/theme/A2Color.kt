package com.idealplayer.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * SONIX IPTV Design System - Core Color Palette.
 *
 * Primary: Royal Purple (#7258E8) - Primary actions, brand identity, focus borders.
 * PrimaryVariant: Violet Blue (#8995FF) - Highlights, gradients.
 * Secondary: Electric Blue (#2878FF) - Active selection, persistent routes.
 * SecondaryVariant: Cyan (#73C9F5) - Gradients, accents, details.
 * PinkSecondary: Secondary Pink (#F41450) - Live badges, alerts, special actions.
 * Background: Deep Charcoal Navy (#0B0A10).
 */
object IdealPlayerColors {
    val Background = Color(0xFF0B0A10)
    val Surface = Color(0xFF13111C)
    val SurfaceVariant = Color(0xFF191726)
    val SurfaceElevated = Color(0xFF221F33)
    val SurfaceFocus = Color(0xFF2B2352)
    val SurfaceSelected = Color(0xFF172347)
    val CardBackground = Color(0xFF151322)
    val CardBorder = Color(0xFF25213B)

    // Primary: Royal Purple (#7258E8)
    val Primary = Color(0xFF7258E8)
    val PrimaryPressed = Color(0xFF5D43D1)
    val PrimaryVariant = Color(0xFF8995FF)

    // Secondary: Electric Blue (#2878FF)
    val Secondary = Color(0xFF2878FF)
    val SecondaryPressed = Color(0xFF1E60D4)
    val SecondaryVariant = Color(0xFF73C9F5)

    // Specific SONIX Palette constants
    val RoyalPurple = Color(0xFF7258E8)
    val VioletBlue = Color(0xFF8995FF)
    val Cyan = Color(0xFF73C9F5)
    val ElectricBlue = Color(0xFF2878FF)
    val PinkSecondary = Color(0xFFF41450)

    val GradientStart = RoyalPurple
    val GradientEnd = Secondary

    /** Source-compatible legacy alias mapped to Secondary (Electric Blue) */
    val Accent = Secondary

    /** Source-compatible legacy alias mapped to Secondary (Electric Blue) */
    val GradientPurple = Secondary

    val TextPrimary = Color(0xFFF5F5FA)
    val TextSecondary = Color(0xFFB0B0C8)
    val TextTertiary = Color(0xFF8A8AA8)
    val TextOnPrimary = Color.White

    val DividerColor = CardBorder
    val Shimmer = Color(0xFF221F33)
    val ShimmerHighlight = Color(0xFF35304F)

    val Success = Color(0xFF33D17A)
    val Warning = Color(0xFFFFB020)
    val Error = Color(0xFFF41450)
    val Info = Color(0xFF73C9F5)
    val Disabled = Color(0xFF545466)

    val FocusBorder = RoyalPurple
    val SelectedBorder = ElectricBlue
    val FocusGlow = Color(0x337258E8)

    val GlassBackground = Color(0x1A7258E8)
    val GlassBorder = Color(0x338995FF)

    val RatingStarColor = Warning
    val OverlayDark = Color(0xC00B0A10)
    val OverlayPlayer = Color(0xE80B0A10)
    val OverlayGradient = Color.Transparent
}

typealias SonixColors = IdealPlayerColors
