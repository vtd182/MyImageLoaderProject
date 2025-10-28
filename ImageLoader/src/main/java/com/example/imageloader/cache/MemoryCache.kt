package com.example.imageloader.cache

import android.graphics.Bitmap
import android.util.LruCache
import com.example.imageloader.core.abstract.BitmapPool

class MemoryCache(
    maxBytes: Int,
    private val bitmapPool: BitmapPool? = null
) {
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.safeByteCount()

        override fun entryRemoved(
            evicted: Boolean,
            key: String?,
            oldValue: Bitmap?,
            newValue: Bitmap?
        ) {
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
        return cache.put(key, bitmap)
    }

    fun remove(key: String): Bitmap? = cache.remove(key)

    fun clear() {
        cache.evictAll()
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
