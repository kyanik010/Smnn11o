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
                "Background" to "FF0B0A10",
                "Surface" to "FF13111C",
                "SurfaceVariant" to "FF191726",
                "SurfaceElevated" to "FF221F33",
                "SurfaceFocus" to "FF2B2352",
                "SurfaceSelected" to "FF172347",
                "CardBackground" to "FF151322",
                "CardBorder" to "FF25213B",
                "Primary" to "FF7258E8",
                "PrimaryPressed" to "FF5D43D1",
                "PrimaryVariant" to "FF5D43D1",
                "Secondary" to "FF2878FF",
                "SecondaryPressed" to "FF1E60D4",
                "SecondaryVariant" to "FF1E60D4",
                "GradientStart" to "FF7258E8",
                "GradientEnd" to "FF2878FF",
                "Accent" to "FF2878FF",
                "GradientPurple" to "FF2878FF",
                "TextPrimary" to "FFF5F5FA",
                "TextSecondary" to "FFB0B0C8",
                "TextTertiary" to "FF8A8AA8",
                "TextOnPrimary" to "FFFFFFFF",
                "DividerColor" to "FF25213B",
                "Shimmer" to "FF25213B",
                "ShimmerHighlight" to "FF35304F",
                "Success" to "FF33D17A",
                "Warning" to "FFFFB020",
                "Error" to "FFF41450",
                "Info" to "FF73C9F5",
                "Disabled" to "FF545466",
                "FocusBorder" to "FF7258E8",
                "SelectedBorder" to "FF2878FF",
                "FocusGlow" to "337258E8",
                "GlassBackground" to "1A7258E8",
                "GlassBorder" to "338995FF",
                "RatingStarColor" to "FFFFB020",
                "OverlayDark" to "C00B0A10",
                "OverlayPlayer" to "E80B0A10",
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
