package com.example.imageloader.cache

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import com.example.imageloader.core.abstract.BitmapPool

class MemoryCache(
    maxBytes: Int, private val bitmapPool: BitmapPool? = null
) {
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount

        override fun entryRemoved(
            evicted: Boolean,
            key: String?,
            oldValue: Bitmap?,
            newValue: Bitmap?
        ) {
            if (evicted && oldValue != null && oldValue.isMutable) {
                Log.d("MemoryCache", "Put bitmap to pool: $key")
                bitmapPool?.put(oldValue)
                Log.d("MemoryCache", "Pool size: ${bitmapPool?.size()}")
            }
        }
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap): Bitmap? = cache.put(key, bitmap)
}
