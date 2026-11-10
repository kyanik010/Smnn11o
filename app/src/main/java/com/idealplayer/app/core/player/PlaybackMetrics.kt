package com.idealplayer.app.core.player

import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class VideoResolutionTier(val label: String) {
    UHD_4K("4K"),
    QHD("QHD"),
    FULL_HD("FHD"),
    HD("HD"),
    SD("SD"),
    LOW("LOW")
}

internal fun videoResolutionTier(width: Int, height: Int): VideoResolutionTier? {
    if (width <= 0 || height <= 0) return null
    val longEdge = maxOf(width, height)
    val shortEdge = minOf(width, height)
    return when {
        longEdge >= 3_840 || shortEdge >= 2_160 -> VideoResolutionTier.UHD_4K
        longEdge >= 2_560 || shortEdge >= 1_440 -> VideoResolutionTier.QHD
        longEdge >= 1_920 || shortEdge >= 1_080 -> VideoResolutionTier.FULL_HD
        longEdge >= 1_280 || shortEdge >= 720 -> VideoResolutionTier.HD
        longEdge >= 720 || shortEdge >= 480 -> VideoResolutionTier.SD
        else -> VideoResolutionTier.LOW
    }
}

internal fun videoResolutionBadge(width: Int, height: Int): String {
    val tier = videoResolutionTier(width, height) ?: return ""
    return "${tier.label} • ${width}×$height"
}

internal fun formatNetworkSpeed(networkSpeedKbps: Long): String = when {
    networkSpeedKbps >= 1_000L -> String.format(
        Locale.getDefault(),
        "%.1f Mbps",
        networkSpeedKbps / 1_000.0
    )
    networkSpeedKbps > 0L -> "$networkSpeedKbps Kbps"
    else -> ""
}

internal fun formatFrameRate(frameRate: Double): String {
    if (!frameRate.isFinite() || frameRate <= 0.0 || frameRate > 240.0) return ""
    val rounded = frameRate.roundToInt()
    val value = if (abs(frameRate - rounded) < 0.05) {
        rounded.toString()
    } else {
        String.format(Locale.US, "%.2f", frameRate).trimEnd('0').trimEnd('.')
    }
    return "$value FPS"
}

internal fun calculateNetworkSpeedKbps(receivedByteDelta: Long, elapsedMs: Long): Long {
    if (receivedByteDelta <= 0L || elapsedMs <= 0L) return 0L
    val bits = if (receivedByteDelta > Long.MAX_VALUE / 8L) {
        Long.MAX_VALUE
    } else {
        receivedByteDelta * 8L
    }
    // bits/ms is numerically equal to decimal kilobits/second.
    return bits / elapsedMs
}

/**
 * Samples app receive traffic at a low frequency so both Media3 and native LibVLC transfers are
 * covered without running a separate speed test or adding traffic to the user's stream.
 */
internal class NetworkThroughputSampler(
    private val minimumSampleDurationMs: Long = 1_000L,
    private val receivedBytes: () -> Long = {
        TrafficStats.getUidRxBytes(Process.myUid())
    },
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime
) {
    private var previousBytes = -1L
    private var previousTimestampMs = -1L
    private var lastSpeedKbps = 0L

    fun reset() {
        previousBytes = -1L
        previousTimestampMs = -1L
        lastSpeedKbps = 0L
    }

    fun sample(): Long {
        val currentBytes = receivedBytes()
        val currentTimestampMs = elapsedRealtimeMs()
        if (currentBytes < 0L || currentTimestampMs < 0L) {
            reset()
            return 0L
        }

        if (previousBytes < 0L || previousTimestampMs < 0L || currentBytes < previousBytes) {
            previousBytes = currentBytes
            previousTimestampMs = currentTimestampMs
            lastSpeedKbps = 0L
            return 0L
        }

        val elapsedMs = currentTimestampMs - previousTimestampMs
        if (elapsedMs < minimumSampleDurationMs) return lastSpeedKbps

        lastSpeedKbps = calculateNetworkSpeedKbps(
            receivedByteDelta = currentBytes - previousBytes,
            elapsedMs = elapsedMs
        )
        previousBytes = currentBytes
        previousTimestampMs = currentTimestampMs
        return lastSpeedKbps
    }
}
