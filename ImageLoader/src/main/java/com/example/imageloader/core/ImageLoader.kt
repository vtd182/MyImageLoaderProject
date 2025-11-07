package com.example.imageloader.core

import android.content.Context
import com.example.imageloader.cache.ActiveResources
import com.example.imageloader.cache.DiskCache
import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.decode.BitmapDecoder
import com.example.imageloader.fetcher.HttpFetcher

/**
 * ImageLoader - Entry point chính của thư viện.
 *
 * Quản lý toàn bộ infrastructure của image loading system:
 * - Khởi tạo và cấu hình các cache layers
 * - Tạo Engine để xử lý requests
 * - Cung cấp API để load ảnh
 *
 * ## Singleton Pattern:
 * ImageLoader sử dụng thread-safe singleton với Double-Checked Locking:
 * - Chỉ một instance duy nhất trong toàn app
 * - Tái sử dụng cache và connection pool
 * - Tiết kiệm memory và resources
 *
 * ## Usage:
 * ```kotlin
 * // Cách 1: Load ảnh đơn giản
 * ImageLoader.with(context)
 *     .load("https://example.com/image.jpg")
 *     .into(imageView)
 *
 * // Cách 2: Với transformations
 * ImageLoader.with(context)
 *     .load(url)
 *     .resize(500, 500)
 *     .centerCrop()
 *     .into(imageView)
 * ```
 *
 * @param context Application context
 * @param useBitmapPool Có sử dụng BitmapPool để tái sử dụng bitmap hay không
 */
class ImageLoader private constructor(context: Context, useBitmapPool: Boolean) {
    /** Tính toán kích thước cache dựa trên available memory */
    private val sizes = MemorySizeCalculator.calculate(context, useBitmapPool)
    
    /** Pool để tái sử dụng bitmap, giảm GC pressure */
    private val bitmapPool = LruBitmapPool(sizes.bitmapPoolSize.toLong())
    
    /** LRU cache trong RAM cho decoded bitmaps */
    private val memoryCache = MemoryCache(sizes.memoryCacheSize, bitmapPool)

    /** Disk cache cho raw image data */
    private val diskCache = DiskCache(context)
    
    /** Cache cho bitmaps đang được View sử dụng */
    private val activeResources = ActiveResources()
    
    /** HTTP client để fetch ảnh từ network */
    private val fetcher = HttpFetcher()

    /** Engine chịu trách nhiệm điều phối toàn bộ quá trình load */
    val engine = Engine(activeResources, memoryCache, diskCache, fetcher, bitmapPool)

    init {
        // Cấu hình BitmapDecoder để sử dụng pool
        BitmapDecoder.setBitmapPool(bitmapPool)
        BitmapDecoder.setUseBitmapPool(useBitmapPool)
    }

    companion object {
        /** Volatile để đảm bảo visibility across threads */
        @Volatile
        private var INSTANCE: ImageLoader? = null

        /**
         * Lấy singleton instance của ImageLoader.
         *
         * Thread-safe với Double-Checked Locking pattern:
         * 1. Check nhanh ngoài synchronized block
         * 2. Synchronized chỉ khi instance == null
         * 3. Double-check bên trong synchronized
         *
         * @param context Context của app (sẽ convert sang applicationContext)
         * @param useBitmapPool Có enable BitmapPool hay không (mặc định: false)
         * @return Singleton instance
         */
        fun getInstance(context: Context, useBitmapPool: Boolean = false): ImageLoader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageLoader(context.applicationContext, useBitmapPool).also {
                    INSTANCE = it
                }
            }
        }

        /**
         * Tạo RequestBuilder để bắt đầu một image request.
         *
         * Đây là API entry point được khuyên dùng:
         * ```kotlin
         * ImageLoader.with(context)
         *     .load(url)
         *     .into(imageView)
         * ```
         *
         * @param context Context của app
         * @param useBitmapPool Có enable BitmapPool hay không
         * @return RequestBuilder để config và execute request
         */
        fun with(context: Context, useBitmapPool: Boolean = false): RequestBuilder {
            return RequestBuilder(getInstance(context, useBitmapPool).engine)
        }
    }
}
