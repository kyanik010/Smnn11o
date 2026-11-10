package com.idealplayer.app.core.common

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

enum class DeviceUiMode {
    MOBILE,
    TV;

    val isTv: Boolean
        get() = this == TV
}

object DeviceUtils {
    fun resolveUiMode(context: Context): DeviceUiMode {
        return if (isTvDevice(context)) DeviceUiMode.TV else DeviceUiMode.MOBILE
    }

    @Suppress("DEPRECATION")
    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val packageManager = context.packageManager

        val isTelevisionMode =
            uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasTelevisionFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        val hasLeanbackFeature =
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)

        return isTelevisionMode || hasTelevisionFeature || hasLeanbackFeature
    }

    fun isTablet(context: Context): Boolean {
        val config = context.resources.configuration
        val screenWidthDp = config.smallestScreenWidthDp
        return screenWidthDp >= 600
    }

    fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    fun isPortrait(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    fun screenWidthDp(context: Context): Int {
        return context.resources.configuration.screenWidthDp
    }

    fun screenHeightDp(context: Context): Int {
        return context.resources.configuration.screenHeightDp
    }
}
