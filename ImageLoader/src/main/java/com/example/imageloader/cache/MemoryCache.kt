package com.example.imageloader.cache

import android.graphics.Bitmap
import android.util.LruCache

class MemoryCache(maxBytes: Int) {
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) = cache.put(key, bitmap)
    fun remove(key: String) = cache.remove(key)
    fun clear() = cache.evictAll()
}
