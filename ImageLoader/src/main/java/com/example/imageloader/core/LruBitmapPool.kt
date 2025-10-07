package com.example.imageloader.core

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import com.example.imageloader.core.abstract.BitmapPool
import java.util.ArrayDeque

class LruBitmapPool(private val maxSizeBytes: Long) : BitmapPool {

    private data class Key(val width: Int, val height: Int, val config: Config)

    private val buckets = LinkedHashMap<Key, ArrayDeque<Bitmap>>(16, 0.75f, true)
    private var currentSize = 0L

    @Synchronized
    override fun get(width: Int, height: Int, config: Config): Bitmap? {
        val exactKey = Key(width, height, config)
        val exactDeque = buckets[exactKey]

        exactDeque?.pollFirst()?.let { bmp ->
            if (isReusable(bmp)) {
                currentSize -= bmp.allocationByteCount
                return bmp
            }
        }

        val iter = buckets.entries.iterator()
        while (iter.hasNext()) {
            val (key, deque) = iter.next()
            if (key.config == config && key.width >= width && key.height >= height) {
                val candidate = deque.pollFirst()
                if (candidate != null && isReusable(candidate)) {
                    currentSize -= candidate.allocationByteCount

                    if (candidate.width != width || candidate.height != height) {
                        try {
                            candidate.reconfigure(width, height, config)
                        } catch (e: Exception) {
                            candidate.recycle()
                            continue
                        }
                    }
                    return candidate
                }
            }
        }

        return null
    }

    @Synchronized
    override fun put(bitmap: Bitmap) {
        if (!isReusable(bitmap)) return
        val size = bitmap.safeByteCount()
        if (size > maxSizeBytes / 2) return

        val key = Key(bitmap.width, bitmap.height, bitmap.config ?: Config.ARGB_8888)
        val deque = buckets.getOrPut(key) { ArrayDeque() }
        deque.addFirst(bitmap)
        currentSize += size
        trimToSize(maxSizeBytes)
    }

    @Synchronized
    override fun clear() {
        for ((_, deque) in buckets) {
            deque.forEach { if (!it.isRecycled) it.recycle() }
        }
        buckets.clear()
        currentSize = 0L
    }

    override fun size(): Long = currentSize

    private fun isReusable(bitmap: Bitmap): Boolean {
        return !bitmap.isRecycled && bitmap.isMutable
    }

    private fun Bitmap.safeByteCount(): Int {
        return try {
            allocationByteCount
        } catch (_: Throwable) {
            byteCount
        }
    }

    private fun trimToSize(maxSize: Long) {
        val it = buckets.entries.iterator()
        while (currentSize > maxSize && it.hasNext()) {
            val entry = it.next()
            val deque = entry.value
            while (deque.isNotEmpty() && currentSize > maxSize) {
                val b = deque.removeLast()
                currentSize -= b.safeByteCount()
                if (!b.isRecycled) b.recycle()
            }
            if (deque.isEmpty()) it.remove()
        }
    }
}
