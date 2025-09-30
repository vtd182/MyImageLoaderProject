package com.example.imageloader.core

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import com.example.imageloader.core.abstract.BitmapPool
import java.util.ArrayDeque

class LruBitmapPool(private val maxSizeBytes: Long) : BitmapPool {
    private data class Key(val size: Int, val config: Config)

    private val buckets = LinkedHashMap<Key, ArrayDeque<Bitmap>>(16, 0.75f, true)
    private var currentSize = 0L

    @Synchronized
    override fun get(width: Int, height: Int, config: Config): Bitmap? {
        val size = computeSizeBytes(width, height, config)
        val key = Key(size, config)
        val deque = buckets[key]
        val bmp = deque?.pollFirst()
        if (bmp != null) {
            currentSize -= bmp.allocationByteCount
            if (bmp.isRecycled || !bmp.isMutable) {
                return get(width, height, config)
            }
            return bmp
        }

        val iter = buckets.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.key.config == config && entry.key.size >= size) {
                val candidate = entry.value.pollFirst()
                if (candidate != null) {
                    currentSize -= candidate.allocationByteCount
                    if (candidate.isRecycled || !candidate.isMutable) {
                        continue
                    }
                    return candidate
                }
            }
        }
        return null
    }

    @Synchronized
    override fun put(bitmap: Bitmap) {
        if (bitmap.isRecycled || !bitmap.isMutable) return
        val size = try {
            bitmap.allocationByteCount
        } catch (t: Throwable) {
            bitmap.byteCount
        }
        if (size > maxSizeBytes / 2) {
            return
        }
        val key = Key(size, bitmap.config ?: Config.ARGB_8888)
        val deque = buckets.getOrPut(key) { ArrayDeque() }
        deque.addFirst(bitmap)
        currentSize += size
        trimToSize(maxSizeBytes)
    }

    @Synchronized
    override fun clear() {
        for ((_, deque) in buckets) {
            for (b in deque) {
                if (!b.isRecycled) b.recycle()
            }
        }
        buckets.clear()
        currentSize = 0L
    }

    private fun trimToSize(maxSize: Long) {
        val it = buckets.entries.iterator()
        while (currentSize > maxSize && it.hasNext()) {
            val entry = it.next()
            val deque = entry.value
            while (deque.isNotEmpty() && currentSize > maxSize) {
                val b = deque.removeLast()
                currentSize -= try {
                    b.allocationByteCount
                } catch (t: Throwable) {
                    b.byteCount
                }
                if (!b.isRecycled) b.recycle()
            }
            if (deque.isEmpty()) it.remove()
        }
    }

    private fun computeSizeBytes(width: Int, height: Int, config: Config): Int {
        val bytesPerPixel = when (config) {
            Config.ALPHA_8 -> 1
            Config.RGB_565 -> 2
            Config.ARGB_4444 -> 2
            Config.ARGB_8888 -> 4
            else -> 4
        }
        val s = width * height * bytesPerPixel
        return s
    }

    override fun size(): Long = currentSize
}