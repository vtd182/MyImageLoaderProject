package com.example.imageloader.cache

import android.graphics.Bitmap
import android.util.LruCache
import com.example.imageloader.core.abstract.BitmapPool
import com.example.imageloader.logger.ImageLoaderLogger

/**
 * MemoryCache - Cache tầng 2 lưu trữ decoded Bitmaps trong RAM.
 *
 * ## Vai trò trong Cache Hierarchy:
 * ```
 * 1. ActiveResources ← Bitmaps đang hiển thị
 * 2. MemoryCache     ← Bitmaps đã decode, không còn active (ĐÂY)
 * 3. DiskCache       ← Raw image data
 * 4. Network         ← Fetch từ internet
 * ```
 *
 * ## Mục đích:
 * - Cache các bitmaps đã decode để tránh decode lại (decode rất tốn CPU)
 * - LRU eviction: Bitmap ít dùng nhất bị remove khi cache đầy
 * - Integration với BitmapPool: Bitmap evicted được put vào pool để reuse
 *
 * ## LRU Cache Strategy:
 * ```
 * Cache đầy (100MB), cần thêm 10MB:
 * → Evict bitmap cũ nhất (ít access nhất)
 * → Nếu bitmap mutable → put vào BitmapPool
 * → Giải phóng 10MB
 * → Put bitmap mới vào
 * ```
 *
 * ## Flow khi load ảnh:
 * ```
 * Request → Check Active → Miss
 *        → Check Memory → HIT → Move to Active
 *        → Miss → Decode → Put to Active
 *        → Active released → Put to Memory
 * ```
 *
 * ## Integration với BitmapPool:
 * Khi bitmap bị evict:
 * 1. Check bitmap có mutable không
 * 2. Nếu có → BitmapPool.put(bitmap)
 * 3. Bitmap được reuse cho decode tiếp theo
 * 4. Giảm memory allocation và GC
 *
 * ## Performance benefits:
 * - **Hit rate cao**: ~70-90% trong typical app
 * - **Tiết kiệm CPU**: Không cần decode lại
 * - **Tiết kiệm battery**: Ít CPU cycles = ít battery drain
 * - **Smooth UX**: Instant display từ cache
 *
 * @param maxBytes Kích thước tối đa của cache (bytes)
 * @param bitmapPool Optional pool để reuse evicted bitmaps
 *
 * @see com.example.imageloader.cache.ActiveResources
 * @see com.example.imageloader.core.LruBitmapPool
 */
class MemoryCache(
    maxBytes: Int,
    private val bitmapPool: BitmapPool? = null
) {
    /** Số lượng items hiện tại trong cache */
    private var itemCount = 0

    init {
        ImageLoaderLogger.d("MemoryCache", "MemoryCache initialized with maxBytes: $maxBytes")
    }

    /**
     * Android LruCache implementation với custom size calculation và eviction callback.
     */
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.safeByteCount()

        override fun entryRemoved(
            evicted: Boolean,
            key: String?,
            oldValue: Bitmap?,
            newValue: Bitmap?
        ) {
            if (evicted) {
                ImageLoaderLogger.d(
                    "MemoryCache",
                    "Evicting image, items in cache before evict: ${itemCount + 1}"
                )
                itemCount--
            }
            if (evicted && oldValue != null && oldValue.isMutable && !oldValue.isRecycled) {
                bitmapPool?.put(oldValue)
            }
        }
    }

    /**
     * Lấy bitmap từ cache.
     *
     * @param key Cache key
     * @return Bitmap nếu tìm thấy, null nếu cache miss
     */
    fun get(key: String): Bitmap? = cache.get(key)

    /**
     * Put bitmap vào cache.
     *
     * ## Behavior:
     * - Nếu cache đầy → LRU eviction tự động
     * - Bitmap evicted sẽ được put vào BitmapPool (nếu có)
     * - Return old bitmap nếu key đã tồn tại
     *
     * @param key Cache key
     * @param bitmap Bitmap cần cache
     * @return Old bitmap nếu replace, null nếu insert mới
     */
    fun put(key: String, bitmap: Bitmap): Bitmap? {
        if (bitmap.isRecycled) {
            return null
        }
        val oldBitmap = cache.put(key, bitmap)
        if (oldBitmap == null) {
            itemCount++
        }
        return oldBitmap
    }

    fun remove(key: String): Bitmap? {
        val removed = cache.remove(key)
        if (removed != null) {
            itemCount--
        }
        return removed
    }

    fun clear() {
        cache.evictAll()
        itemCount = 0
    }

    val size: Int get() = cache.size()
}

private fun Bitmap.safeByteCount(): Int {
    return try {
        if (isRecycled) 0 else allocationByteCount
    } catch (_: Throwable) {
        0
    }
}
