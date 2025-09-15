// cache/MemoryCache.kt
package com.example.imageloader.cache

import android.graphics.Bitmap
import android.util.LruCache

class MemoryCache(maxSizeKb: Int) {
    private val cache = object : LruCache<String, Bitmap>(maxSizeKb) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun put(key: String, bitmap: Bitmap) = cache.put(key, bitmap)
    fun get(key: String): Bitmap? = cache.get(key)
    fun remove(key: String) = cache.remove(key)
}
