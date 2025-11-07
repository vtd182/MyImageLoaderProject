package com.example.imageloader.core

import android.graphics.Bitmap
import com.example.imageloader.core.abstract.BitmapPool

/**
 * LruBitmapPool - Pool để tái sử dụng Bitmap objects, giảm memory allocation và GC.
 *
 * ## Vấn đề:
 * - Tạo mới Bitmap rất tốn kém (memory allocation + GC pressure)
 * - Decode nhiều ảnh liên tục → OutOfMemoryError
 * - GC chạy thường xuyên → app lag, frame drops
 *
 * ## Giải pháp:
 * LruBitmapPool lưu trữ và tái sử dụng Bitmap:
 * 1. Khi decode xong, bitmap cũ được `put()` vào pool thay vì GC
 * 2. Lần decode tiếp theo, `get()` bitmap từ pool để reuse
 * 3. Sử dụng `inBitmap` option của BitmapFactory
 * 4. LRU eviction khi pool đầy
 *
 * ## Kỹ thuật:
 * - **LinkedHashMap với accessOrder=true**: LRU cache
 * - **Key = (width, height, config)**: Nhóm bitmap cùng size
 * - **Fallback search**: Tìm bitmap lớn hơn, reconfigure về size mong muốn
 * - **Metrics tracking**: hits/misses/evictions để debug performance
 *
 * ## Ví dụ:
 * ```
 * Decode ảnh 1000x1000 → bitmap A
 * Put bitmap A vào pool
 * Decode ảnh 1000x1000 tiếp theo → get bitmap A từ pool → reuse
 * → Tiết kiệm 1000*1000*4 = 4MB allocation
 * ```
 *
 * @param maxSizeBytes Kích thước tối đa của pool (bytes)
 */
class LruBitmapPool(private val maxSizeBytes: Long) : BitmapPool {

    /**
     * Key để nhóm các bitmap cùng kích thước và config.
     * Bitmap cùng key có thể được reuse cho nhau.
     */
    private data class Key(val width: Int, val height: Int, val config: Bitmap.Config)

    /** LRU map: accessOrder=true → bitmap được access sẽ move to end */
    private val map = LinkedHashMap<Key, MutableList<Bitmap>>(0, 0.75f, true)
    
    /** Tổng size hiện tại của pool */
    private var currentSize = 0L

    // Performance metrics
    private var hits = 0      // Số lần tìm thấy bitmap reusable
    private var misses = 0    // Số lần không tìm thấy → phải allocate new
    private var puts = 0      // Số lần put bitmap vào pool
    private var evictions = 0 // Số lần evict bitmap do pool đầy

    /**
     * Lấy bitmap từ pool để tái sử dụng.
     *
     * ## Chiến lược tìm kiếm:
     * 1. **Exact match**: Tìm bitmap đúng size và config
     * 2. **Fallback**: Tìm bitmap lớn hơn cùng config → reconfigure
     * 3. **Miss**: Return null → BitmapDecoder sẽ allocate new
     *
     * ## Thread-safety:
     * @Synchronized để tránh race condition khi multi-thread access
     *
     * @param width Width mong muốn
     * @param height Height mong muốn
     * @param config Bitmap config (ARGB_8888, RGB_565, etc.)
     * @return Bitmap reusable hoặc null nếu không tìm thấy
     */
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

    /**
     * Đưa bitmap vào pool để tái sử dụng sau.
     *
     * ## Validation:
     * - Bitmap phải mutable (có thể reconfigure)
     * - Bitmap không bị recycled
     * - Size <= 50% maxSize (tránh bitmap quá lớn chiếm hết pool)
     *
     * ## Flow:
     * 1. Add vào list tương ứng với key
     * 2. Update currentSize
     * 3. Trim pool nếu vượt maxSize
     *
     * @param bitmap Bitmap cần put vào pool
     */
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

    /**
     * Xóa toàn bộ bitmap trong pool.
     * Thường được gọi khi app cần giải phóng memory (low memory warning).
     */
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
