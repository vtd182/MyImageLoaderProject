package com.example.imageloader.cache

import android.graphics.Bitmap
import android.util.LruCache
import com.example.imageloader.core.abstract.BitmapPool
import com.example.imageloader.logger.ImageLoaderLogger

class MemoryCache(
    maxBytes: Int,
    private val bitmapPool: BitmapPool? = null
) {
    private var itemCount = 0

    init {
        ImageLoaderLogger.d("MemoryCache", "MemoryCache initialized with maxBytes: $maxBytes")
    }

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

    fun get(key: String): Bitmap? = cache.get(key)

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
