package com.example.imageloader.core

import android.app.ActivityManager
import android.content.Context
import com.example.imageloader.logger.ImageLoaderLogger

object MemorySizeCalculator {
    const val LOW_RAM_DIVISOR = 8f
    const val MID_RAM_DIVISOR = 5f
    const val HIGH_RAM_DIVISOR = 4f

    const val MEMORY_CACHE_FRACTION_WITH_POOL = 0.6f
    const val BITMAP_POOL_FRACTION_WITH_POOL = 0.4f

    const val MEMORY_CACHE_FRACTION_NO_POOL = 1.0f
    const val BITMAP_POOL_FRACTION_NO_POOL = 0.0f

    data class Sizes(
        val memoryCacheSize: Int,
        val bitmapPoolSize: Int
    )

    fun calculate(context: Context, useBitmapPool: Boolean): Sizes {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isLowRam = am.isLowRamDevice

        val isLargeHeap = context.applicationInfo.flags and
                android.content.pm.ApplicationInfo.FLAG_LARGE_HEAP != 0

        val memoryClassMB = if (isLargeHeap) am.largeMemoryClass else am.memoryClass
        val totalHeapBytes = memoryClassMB * 1024 * 1024

        val divisor = when {
            isLowRam -> LOW_RAM_DIVISOR
            memoryClassMB <= 256 -> MID_RAM_DIVISOR
            else -> HIGH_RAM_DIVISOR
        }

        val totalCacheBytes = (totalHeapBytes / divisor).toInt()

        val memoryFraction = if (useBitmapPool)
            MEMORY_CACHE_FRACTION_WITH_POOL
        else
            MEMORY_CACHE_FRACTION_NO_POOL

        val poolFraction = if (useBitmapPool)
            BITMAP_POOL_FRACTION_WITH_POOL
        else
            BITMAP_POOL_FRACTION_NO_POOL

        val memoryCacheSize = (totalCacheBytes * memoryFraction).toInt()
        val bitmapPoolSize = (totalCacheBytes * poolFraction).toInt()

        ImageLoaderLogger.d(
            "MemorySizeCalculator",
            "memoryClass=${memoryClassMB}MB (largeHeap=$isLargeHeap), " +
                    "lowRam=$isLowRam, usePool=$useBitmapPool, " +
                    "totalCache=${totalCacheBytes / 1024 / 1024}MB, " +
                    "memCache=${memoryCacheSize / 1024 / 1024}MB, " +
                    "bitmapPool=${bitmapPoolSize / 1024 / 1024}MB"
        )

        return Sizes(memoryCacheSize, bitmapPoolSize)
    }
}
