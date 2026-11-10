package com.idealplayer.app.core.common

import android.app.ActivityManager
import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Builds an optimized Coil [ImageLoader] with:
 * - 100 MB disk cache
 * - RAM-aware memory cache
 * - crossfade(300ms)
 * - Respectful cache policy
 */
fun buildIdealPlayerImageLoader(context: Context, okHttpClient: OkHttpClient): ImageLoader {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val memoryCachePercent = imageMemoryCachePercent(
        isLowRamDevice = activityManager?.isLowRamDevice == true,
        memoryClassMb = activityManager?.memoryClass ?: 256
    )

    return ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(memoryCachePercent)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                .build()
        }
        .okHttpClient {
            okHttpClient.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .crossfade(300)
        .respectCacheHeaders(false) // Many image servers send no-cache headers
        .build()
}

internal fun imageMemoryCachePercent(
    isLowRamDevice: Boolean,
    memoryClassMb: Int
): Double = if (isLowRamDevice || memoryClassMb <= 256) 0.12 else 0.20
