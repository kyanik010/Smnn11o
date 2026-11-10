package com.idealplayer.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class A2FoundationContractTest {

    @Test
    fun `semantic colors use the exact approved A2 ARGB roles`() {
        val actualArgbByRole = linkedMapOf(
            "Background" to IdealPlayerColors.Background,
            "Surface" to IdealPlayerColors.Surface,
            "SurfaceVariant" to IdealPlayerColors.SurfaceVariant,
            "SurfaceElevated" to IdealPlayerColors.SurfaceElevated,
            "SurfaceFocus" to IdealPlayerColors.SurfaceFocus,
            "SurfaceSelected" to IdealPlayerColors.SurfaceSelected,
            "CardBackground" to IdealPlayerColors.CardBackground,
            "CardBorder" to IdealPlayerColors.CardBorder,
            "Primary" to IdealPlayerColors.Primary,
            "PrimaryPressed" to IdealPlayerColors.PrimaryPressed,
            "PrimaryVariant" to IdealPlayerColors.PrimaryVariant,
            "Secondary" to IdealPlayerColors.Secondary,
            "SecondaryPressed" to IdealPlayerColors.SecondaryPressed,
            "SecondaryVariant" to IdealPlayerColors.SecondaryVariant,
            "GradientStart" to IdealPlayerColors.GradientStart,
            "GradientEnd" to IdealPlayerColors.GradientEnd,
            "Accent" to IdealPlayerColors.Accent,
            "GradientPurple" to IdealPlayerColors.GradientPurple,
            "TextPrimary" to IdealPlayerColors.TextPrimary,
            "TextSecondary" to IdealPlayerColors.TextSecondary,
            "TextTertiary" to IdealPlayerColors.TextTertiary,
            "TextOnPrimary" to IdealPlayerColors.TextOnPrimary,
            "DividerColor" to IdealPlayerColors.DividerColor,
            "Shimmer" to IdealPlayerColors.Shimmer,
            "ShimmerHighlight" to IdealPlayerColors.ShimmerHighlight,
            "Success" to IdealPlayerColors.Success,
            "Warning" to IdealPlayerColors.Warning,
            "Error" to IdealPlayerColors.Error,
            "Info" to IdealPlayerColors.Info,
            "Disabled" to IdealPlayerColors.Disabled,
            "FocusBorder" to IdealPlayerColors.FocusBorder,
            "SelectedBorder" to IdealPlayerColors.SelectedBorder,
            "FocusGlow" to IdealPlayerColors.FocusGlow,
            "GlassBackground" to IdealPlayerColors.GlassBackground,
            "GlassBorder" to IdealPlayerColors.GlassBorder,
            "RatingStarColor" to IdealPlayerColors.RatingStarColor,
            "OverlayDark" to IdealPlayerColors.OverlayDark,
            "OverlayPlayer" to IdealPlayerColors.OverlayPlayer,
            "OverlayGradient" to IdealPlayerColors.OverlayGradient
        ).mapValues { (_, color) -> color.argbHex() }

        assertThat(actualArgbByRole).containsExactlyEntriesIn(
            linkedMapOf(
                "Background" to "FF0A0A0F",
                "Surface" to "FF12121A",
                "SurfaceVariant" to "FF171724",
                "SurfaceElevated" to "FF1E1E30",
                "SurfaceFocus" to "FF24131A",
                "SurfaceSelected" to "FF10264A",
                "CardBackground" to "FF161625",
                "CardBorder" to "FF2A2A3E",
                "Primary" to "FFFF1744",
                "PrimaryPressed" to "FFC41136",
                "PrimaryVariant" to "FFC41136",
                "Secondary" to "FF2979FF",
                "SecondaryPressed" to "FF1D5CCF",
                "SecondaryVariant" to "FF1D5CCF",
                "GradientStart" to "FFFF1744",
                "GradientEnd" to "FF2979FF",
                "Accent" to "FF2979FF",
                "GradientPurple" to "FF2979FF",
                "TextPrimary" to "FFF5F5F5",
                "TextSecondary" to "FFB0B0C0",
                "TextTertiary" to "FF8A8AA0",
                "TextOnPrimary" to "FFFFFFFF",
                "DividerColor" to "FF2A2A3E",
                "Shimmer" to "FF2A2A3E",
                "ShimmerHighlight" to "FF3A3A50",
                "Success" to "FF33D17A",
                "Warning" to "FFFFB020",
                "Error" to "FFFF5252",
                "Info" to "FF40C4FF",
                "Disabled" to "FF545466",
                "FocusBorder" to "FFFF1744",
                "SelectedBorder" to "FF2979FF",
                "FocusGlow" to "24FF1744",
                "GlassBackground" to "1AFFFFFF",
                "GlassBorder" to "33FFFFFF",
                "RatingStarColor" to "FFFFB020",
                "OverlayDark" to "B8000000",
                "OverlayPlayer" to "E0000000",
                "OverlayGradient" to "00000000"
            )
        )
    }

    @Test
    fun `production semantic palette contains no legacy purple value`() {
        val semanticColors = listOf(
            IdealPlayerColors.Background,
            IdealPlayerColors.Surface,
            IdealPlayerColors.SurfaceVariant,
            IdealPlayerColors.SurfaceElevated,
            IdealPlayerColors.SurfaceFocus,
            IdealPlayerColors.SurfaceSelected,
            IdealPlayerColors.CardBackground,
            IdealPlayerColors.CardBorder,
            IdealPlayerColors.Primary,
            IdealPlayerColors.PrimaryPressed,
            IdealPlayerColors.Secondary,
            IdealPlayerColors.SecondaryPressed,
            IdealPlayerColors.TextPrimary,
            IdealPlayerColors.TextSecondary,
            IdealPlayerColors.TextTertiary,
            IdealPlayerColors.TextOnPrimary,
            IdealPlayerColors.ShimmerHighlight,
            IdealPlayerColors.Success,
            IdealPlayerColors.Warning,
            IdealPlayerColors.Error,
            IdealPlayerColors.Info,
            IdealPlayerColors.Disabled,
            IdealPlayerColors.FocusGlow,
            IdealPlayerColors.GlassBackground,
            IdealPlayerColors.GlassBorder,
            IdealPlayerColors.OverlayDark,
            IdealPlayerColors.OverlayPlayer,
            IdealPlayerColors.OverlayGradient
        )

        assertThat(semanticColors.map(Color::toArgb))
            .doesNotContain(0xFF7C4DFF.toInt())
    }

    @Test
    fun `legacy purple-named aliases resolve to selected blue`() {
        assertThat(IdealPlayerColors.Accent).isEqualTo(IdealPlayerColors.Secondary)
        assertThat(IdealPlayerColors.GradientPurple).isEqualTo(IdealPlayerColors.Secondary)
        assertThat(IdealPlayerColors.Accent.toArgb()).isNotEqualTo(0xFF7C4DFF.toInt())
        assertThat(IdealPlayerColors.GradientPurple.toArgb()).isNotEqualTo(0xFF7C4DFF.toInt())
    }

    @Test
    fun `mobile and tablet targets are 48dp while TV targets are 56dp`() {
        assertThat(MobileDimens.buttonHeight).isEqualTo(48.dp)
        assertThat(MobileDimens.touchTargetMin).isEqualTo(48.dp)
        assertThat(TabletDimens.buttonHeight).isEqualTo(48.dp)
        assertThat(TabletDimens.touchTargetMin).isEqualTo(48.dp)
        assertThat(TvDimens.buttonHeight).isEqualTo(56.dp)
        assertThat(TvDimens.touchTargetMin).isEqualTo(56.dp)
    }

    @Test
    fun `TV navigation dimensions match the A2 drawer and category contract`() {
        assertThat(TvDimens.drawerCollapsedWidth).isEqualTo(96.dp)
        assertThat(TvDimens.drawerWidth).isEqualTo(232.dp)
        assertThat(TvDimens.drawerGutter).isEqualTo(24.dp)
        assertThat(TvDimens.categoryPanelWidth).isEqualTo(242.dp)
    }

    @Test
    fun `platform grids use approved column counts`() {
        assertThat(MobileDimens.gridColumns).isEqualTo(2)
        assertThat(TabletDimens.gridColumns).isEqualTo(4)
        assertThat(TvDimens.gridColumns).isEqualTo(5)
    }

    @Test
    fun `platform metadata minimums scale from 12sp through 16sp`() {
        assertThat(MobileDimens.metadataTextSize).isEqualTo(12.sp)
        assertThat(TabletDimens.metadataTextSize).isEqualTo(14.sp)
        assertThat(TvDimens.metadataTextSize).isEqualTo(16.sp)
    }

    @Test
    fun `TV design density keeps controls readable across resolutions`() {
        assertThat(a2TvDesignDensity(systemScreenWidthDp = 960, systemDensity = 2f))
            .isEqualTo(1.25f)
        assertThat(a2TvDesignDensity(systemScreenWidthDp = 960, systemDensity = 4f))
            .isEqualTo(2.5f)
        assertThat(a2TvDesignDensity(systemScreenWidthDp = 960, systemDensity = 1.5f))
            .isEqualTo(0.9375f)
    }

    private fun Color.argbHex(): String =
        toArgb().toUInt().toString(radix = 16).uppercase().padStart(8, '0')
}
