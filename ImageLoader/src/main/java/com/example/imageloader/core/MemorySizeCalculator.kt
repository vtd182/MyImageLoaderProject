package com.example.imageloader.core

import android.app.ActivityManager
import android.content.Context
import com.example.imageloader.logger.ImageLoaderLogger

/**
 * MemorySizeCalculator - Tính toán kích thước hợp lý cho Memory Cache và Bitmap Pool.
 *
 * ## Mục tiêu:
 * - Tối ưu cache size dựa trên memory khả dụng của device
 * - Tránh OutOfMemoryError do cache quá lớn
 * - Tối đa hóa cache hit rate trong giới hạn an toàn
 *
 * ## Chiến lược:
 * 1. **Low RAM devices** (≤512MB): Chia heap cho 8 → cache nhỏ hơn
 * 2. **Mid RAM devices** (≤256MB heap): Chia cho 5
 * 3. **High RAM devices** (>256MB heap): Chia cho 4 → cache lớn hơn
 *
 * ## Phân bổ:
 * - **Với BitmapPool**: 60% Memory Cache + 40% Bitmap Pool
 * - **Không BitmapPool**: 100% Memory Cache
 *
 * ## Ví dụ:
 * ```
 * Device: 2GB RAM, largeHeap=512MB
 * → totalCache = 512MB / 4 = 128MB
 * → memoryCache = 128MB * 60% = 76.8MB
 * → bitmapPool = 128MB * 40% = 51.2MB
 * ```
 */
object MemorySizeCalculator {
    const val LOW_RAM_DIVISOR = 8f
    const val MID_RAM_DIVISOR = 5f
    const val HIGH_RAM_DIVISOR = 4f

    const val MEMORY_CACHE_FRACTION_WITH_POOL = 0.6f
    const val BITMAP_POOL_FRACTION_WITH_POOL = 0.4f

    const val MEMORY_CACHE_FRACTION_NO_POOL = 1.0f
    const val BITMAP_POOL_FRACTION_NO_POOL = 0.0f

    /**
     * Data class chứa kích thước đã tính toán cho caches.
     *
     * @param memoryCacheSize Kích thước Memory Cache (bytes)
     * @param bitmapPoolSize Kích thước Bitmap Pool (bytes)
     */
    data class Sizes(
        val memoryCacheSize: Int,
        val bitmapPoolSize: Int
    )

    /**
     * Tính toán kích thước cache dựa trên device specs.
     *
     * ## Factors:
     * - **isLowRamDevice**: Android system flag cho low-end devices
     * - **memoryClass**: Max heap size cho app (MB)
     * - **largeHeap**: Có request largeHeap trong manifest hay không
     * - **useBitmapPool**: Có sử dụng BitmapPool hay không
     *
     * ## Logic:
     * 1. Determine divisor dựa trên RAM level
     * 2. totalCache = heap / divisor
     * 3. Split totalCache theo useBitmapPool flag
     *
     * @param context Application context
     * @param useBitmapPool Có sử dụng BitmapPool hay không
     * @return Sizes object chứa memoryCacheSize và bitmapPoolSize
     */
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
