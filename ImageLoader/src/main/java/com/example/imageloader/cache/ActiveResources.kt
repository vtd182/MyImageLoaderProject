package com.example.imageloader.cache

import android.graphics.Bitmap
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

class ActiveResources(private val memoryCache: MemoryCache) {

    private val activeMap = mutableMapOf<String, WeakReference<Bitmap>>()
    private val refQueue = ReferenceQueue<Bitmap>()

    @Synchronized
    fun put(key: String, bitmap: Bitmap) {
        activeMap[key] = WeakReference(bitmap, refQueue)
        cleanup()
    }

    @Synchronized
    fun get(key: String): Bitmap? {
        cleanup()
        return activeMap[key]?.get()
    }

    @Synchronized
    fun remove(key: String) {
        activeMap.remove(key)
    }

    private fun cleanup() {
        var ref = refQueue.poll()
        while (ref != null) {
            val entry = activeMap.entries.find { it.value == ref }
            if (entry != null) {
                entry.value.get()?.let { bitmap ->
                    memoryCache.put(entry.key, bitmap)
                }
                activeMap.remove(entry.key)
            }
            ref = refQueue.poll()
        }
    }
}
