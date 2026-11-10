package com.idealplayer.app.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

internal const val A2_TV_COMFORTABLE_WIDTH_UNITS = 1536f

/**
 * TV manufacturers expose inconsistent logical densities. Mapping the approved 1920 px boards
 * one-to-one made 96 dp navigation and 56 dp focus targets only 96/56 physical pixels on common
 * 1080p televisions, which is too small at viewing distance. Use a 1536-unit comfortable viewport:
 * 1080p renders at 1.25x, while 720p and 4K remain proportional and readable.
 */
internal fun a2TvDesignDensity(
    systemScreenWidthDp: Int,
    systemDensity: Float
): Float = ((systemScreenWidthDp * systemDensity) / A2_TV_COMFORTABLE_WIDTH_UNITS)
    .coerceIn(0.75f, 4f)

private val IdealPlayerDarkColorScheme = darkColorScheme(
    primary = IdealPlayerColors.Primary,
    onPrimary = IdealPlayerColors.TextOnPrimary,
    primaryContainer = IdealPlayerColors.PrimaryPressed,
    onPrimaryContainer = IdealPlayerColors.TextPrimary,
    secondary = IdealPlayerColors.Secondary,
    onSecondary = IdealPlayerColors.TextOnPrimary,
    secondaryContainer = IdealPlayerColors.SurfaceSelected,
    onSecondaryContainer = IdealPlayerColors.TextPrimary,
    tertiary = IdealPlayerColors.Info,
    onTertiary = IdealPlayerColors.Background,
    background = IdealPlayerColors.Background,
    onBackground = IdealPlayerColors.TextPrimary,
    surface = IdealPlayerColors.Surface,
    onSurface = IdealPlayerColors.TextPrimary,
    surfaceVariant = IdealPlayerColors.SurfaceElevated,
    onSurfaceVariant = IdealPlayerColors.TextSecondary,
    error = IdealPlayerColors.Error,
    onError = IdealPlayerColors.TextOnPrimary,
    outline = IdealPlayerColors.CardBorder,
    outlineVariant = IdealPlayerColors.DividerColor,
    scrim = IdealPlayerColors.OverlayDark
)

@Composable
fun IdealPlayerTheme(
    isTv: Boolean = false,
    content: @Composable () -> Unit
) {
    val config = LocalConfiguration.current
    val systemDensity = LocalDensity.current
    val platformTheme = remember(isTv, config.smallestScreenWidthDp) {
        when {
            isTv -> TvDimens to TvIdealPlayerTypography
            config.smallestScreenWidthDp >= 600 -> TabletDimens to TabletIdealPlayerTypography
            else -> MobileDimens to MobileIdealPlayerTypography
        }
    }
    val contentDensity = remember(
        isTv,
        config.screenWidthDp,
        systemDensity.density,
        systemDensity.fontScale
    ) {
        if (isTv) {
            Density(
                density = a2TvDesignDensity(
                    systemScreenWidthDp = config.screenWidthDp,
                    systemDensity = systemDensity.density
                ),
                fontScale = systemDensity.fontScale
            )
        } else {
            systemDensity
        }
    }

    CompositionLocalProvider(
        LocalIdealPlayerDimens provides platformTheme.first,
        LocalDensity provides contentDensity
    ) {
        MaterialTheme(
            colorScheme = IdealPlayerDarkColorScheme,
            typography = platformTheme.second,
            shapes = IdealPlayerShapes,
            content = content
        )
    }
}
