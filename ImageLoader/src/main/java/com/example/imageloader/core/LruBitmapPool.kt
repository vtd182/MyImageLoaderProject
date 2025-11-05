package com.example.imageloader.core

import android.graphics.Bitmap
import com.example.imageloader.core.abstract.BitmapPool

class LruBitmapPool(private val maxSizeBytes: Long) : BitmapPool {

    private data class Key(val width: Int, val height: Int, val config: Bitmap.Config)

    private val map = LinkedHashMap<Key, MutableList<Bitmap>>(0, 0.75f, true)
    private var currentSize = 0L

    // Debug counters
    private var hits = 0
    private var misses = 0
    private var puts = 0
    private var evictions = 0

    @Synchronized
    override fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap? {
        val key = Key(width, height, config)
        val candidates = map[key]

        val bitmap = candidates?.firstOrNull { isReusable(it) }?.also {
            candidates.remove(it)
            if (candidates.isEmpty()) map.remove(key)
            currentSize -= it.safeByteCount()
            hits++
        }

        if (bitmap != null) {
            bitmap.eraseColor(0)
            return bitmap
        }

        // fallback: tìm bitmap lớn hơn hoặc cùng config
        for ((k, list) in map.entries) {
            if (k.config == config && k.width >= width && k.height >= height) {
                val candidate = list.firstOrNull { isReusable(it) }
                if (candidate != null) {
                    list.remove(candidate)
                    if (list.isEmpty()) map.remove(k)
                    currentSize -= candidate.safeByteCount()
                    candidate.reconfigure(width, height, config)
                    candidate.eraseColor(0)
                    hits++
                    return candidate
                }
            }
        }

        misses++
        return null
    }

    @Synchronized
    override fun put(bitmap: Bitmap) {
        if (!isReusable(bitmap)) return
        val size = bitmap.safeByteCount()
        if (size <= 0 || size > maxSizeBytes / 2) return

        val key = Key(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val list = map.getOrPut(key) { mutableListOf() }
        list.add(bitmap)
        currentSize += size
        puts++

        trimToSize(maxSizeBytes)
    }

    @Synchronized
    override fun clear() {
        for (list in map.values) {
            list.forEach {
                if (!it.isRecycled) { /* it.recycle() */
                }
            }
        }
        map.clear()
        currentSize = 0L
    }

    override fun size(): Long = currentSize

    private fun isReusable(bitmap: Bitmap): Boolean {
        return !bitmap.isRecycled && bitmap.isMutable
    }

    private fun Bitmap.safeByteCount(): Int {
        return try {
            if (isRecycled) 0 else allocationByteCount
        } catch (_: Throwable) {
            if (isRecycled) 0 else byteCount
        }
    }

    private fun trimToSize(maxSize: Long) {
        val iter = map.entries.iterator()
        while (currentSize > maxSize && iter.hasNext()) {
            val entry = iter.next()
            val list = entry.value
            while (list.isNotEmpty() && currentSize > maxSize) {
                val b = list.removeAt(list.size - 1)
                currentSize -= b.safeByteCount()
                if (!b.isRecycled) b.recycle()
                evictions++
            }
            if (list.isEmpty()) iter.remove()
        }
    }

    fun dumpStats(): String {
        return "hits=$hits, misses=$misses, puts=$puts, evictions=$evictions, size=$currentSize/$maxSizeBytes"
    }
}
