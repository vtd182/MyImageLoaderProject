package com.example.imageloader.core.abstract

import android.graphics.Bitmap
import android.graphics.Bitmap.Config

/**
 * BitmapPool - Interface định nghĩa contract cho bitmap pooling system.
 *
 * ## Mục đích:
 * Bitmap pooling là kỹ thuật quan trọng để giảm memory churn và GC pressure:
 * - Tái sử dụng Bitmap objects thay vì tạo mới
 * - Sử dụng `BitmapFactory.Options.inBitmap` để decode vào bitmap có sẵn
 * - Giảm đáng kể memory allocation khi decode nhiều ảnh
 *
 * ## Lợi ích:
 * ```
 * Không có Pool:
 * Decode 100 ảnh → allocate 100 bitmaps → GC runs frequently → UI jank
 *
 * Có Pool:
 * Decode 100 ảnh → reuse ~10-20 bitmaps → minimal GC → smooth UI
 * ```
 *
 * ## Implementation:
 * Thư viện cung cấp `LruBitmapPool` - implementation mặc định với LRU eviction.
 *
 * ## Thread-safety:
 * Implementations phải đảm bảo thread-safe vì có thể được gọi từ nhiều threads.
 *
 * @see com.example.imageloader.core.LruBitmapPool
 */
interface BitmapPool {
    /**
     * Lấy bitmap từ pool để reuse.
     *
     * ## Behavior:
     * - Trả về bitmap phù hợp (exact match hoặc lớn hơn)
     * - Trả về null nếu không có bitmap available
     * - Caller phải handle null case (allocate new bitmap)
     *
     * ## BitmapFactory usage:
     * ```kotlin
     * val options = BitmapFactory.Options()
     * options.inBitmap = bitmapPool.get(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
     * options.inMutable = true
     * BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
     * ```
     *
     * @param width Width cần thiết
     * @param height Height cần thiết
     * @param config Bitmap config (ARGB_8888, RGB_565, etc.)
     * @return Bitmap có thể reuse hoặc null
     */
    fun get(width: Int, height: Int, config: Config): Bitmap?

    /**
     * Đưa bitmap vào pool để reuse sau này.
     *
     * ## Requirements:
     * - Bitmap phải mutable (`bitmap.isMutable == true`)
     * - Bitmap không được recycled
     * - Thường được gọi sau khi decode xong và bitmap không còn dùng nữa
     *
     * ## Pool behavior:
     * - Nếu pool đầy → evict bitmap cũ nhất (LRU)
     * - Nếu bitmap quá lớn → có thể reject
     *
     * @param bitmap Bitmap cần put vào pool
     */
    fun put(bitmap: Bitmap)

    /**
     * Xóa toàn bộ bitmaps trong pool.
     *
     * ## Use cases:
     * - onLowMemory() / onTrimMemory() callbacks
     * - App cần giải phóng memory khẩn cấp
     * - Cleanup khi shutdown
     *
     * ## Note:
     * Sau khi clear, pool có thể tiếp tục được sử dụng.
     */
    fun clear()

    /**
     * Trả về tổng kích thước hiện tại của pool.
     *
     * ## Usage:
     * - Monitoring và debugging
     * - Analytics để tune pool size
     * - Health checks
     *
     * @return Tổng size tính bằng bytes
     */
    fun size(): Long
}