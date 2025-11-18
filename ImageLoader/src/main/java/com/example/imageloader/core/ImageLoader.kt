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
 * // Cách 1: Load ảnh đơn giản với default config
 * ImageLoader.with(context)
 *     .load("https://example.com/image.jpg")
 *     .into(imageView)
 *
 * // Cách 2: Config bitmap pool trước khi load
 * ImageLoader.init(context) {
 *     enableBitmapPool(0.4f) // 40% bitmap pool, 60% memory cache
 * }
 * ImageLoader.with(context)
 *     .load(url)
 *     .resize(500, 500)
 *     .into(imageView)
 * ```
 *
 * @param context Application context
 * @param config Configuration object
 */
class ImageLoader private constructor(context: Context, config: Config) {
    /** Tính toán kích thước cache dựa trên available memory và config */
    private val sizes = MemorySizeCalculator.calculate(
        context,
        config.useBitmapPool,
        config.memoryCacheFraction,
        config.bitmapPoolFraction
    )

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
        BitmapDecoder.setUseBitmapPool(config.useBitmapPool)
    }

    /**
     * Configuration cho ImageLoader.
     *
     * @param useBitmapPool Có enable BitmapPool hay không
     * @param memoryCacheFraction Custom ratio cho memory cache (null = dùng default 0.6)
     * @param bitmapPoolFraction Custom ratio cho bitmap pool (null = dùng default 0.4)
     */
    data class Config(
        val useBitmapPool: Boolean = false,
        val memoryCacheFraction: Float? = null,
        val bitmapPoolFraction: Float? = null
    )

    /**
     * Builder để config ImageLoader trước khi khởi tạo.
     *
     * ## Usage:
     * ```kotlin
     * ImageLoader.init(context) {
     *     enableBitmapPool(0.4f)  // 40% pool, 60% memory
     * }
     * ```
     */
    class Builder {
        private var useBitmapPool: Boolean = false
        private var memoryCacheFraction: Float? = null
        private var bitmapPoolFraction: Float? = null

        /**
         * Enable bitmap pool với tỉ lệ tùy chỉnh.
         *
         * Bitmap pool giúp tái sử dụng bitmap và giảm GC pressure.
         *
         * @param poolFraction Tỉ lệ phân bổ cho bitmap pool (0.0 - 1.0).
         *                     Mặc định: 0.4 (40% pool, 60% memory cache)
         *                     Nếu truyền null hoặc không truyền gì thì dùng default 0.4
         * @return Builder để chain calls
         *
         * ## Ví dụ:
         * ```kotlin
         * enableBitmapPool()        // 40% pool (default)
         * enableBitmapPool(0.3f)    // 30% pool, 70% memory
         * enableBitmapPool(0.5f)    // 50% pool, 50% memory
         * ```
         */
        fun enableBitmapPool(poolFraction: Float? = null): Builder {
            useBitmapPool = true
            // Nếu không truyền hoặc truyền null, dùng default
            // Nếu truyền fraction, tự động tính memory fraction
            if (poolFraction != null) {
                bitmapPoolFraction = poolFraction.coerceIn(0f, 1f)
                memoryCacheFraction = 1f - bitmapPoolFraction!!
            }
            // Nếu null thì để null, MemorySizeCalculator sẽ dùng default 0.4 và 0.6
            return this
        }

        /**
         * Set custom memory cache fraction.
         *
         * @param fraction Tỉ lệ memory cache (0.0 - 1.0)
         * @return Builder để chain calls
         */
        fun setMemoryCacheFraction(fraction: Float): Builder {
            memoryCacheFraction = fraction.coerceIn(0f, 1f)
            return this
        }

        internal fun build(): Config {
            return Config(useBitmapPool, memoryCacheFraction, bitmapPoolFraction)
        }
    }

    companion object {
        /** Volatile để đảm bảo visibility across threads */
        @Volatile
        private var INSTANCE: ImageLoader? = null

        /** Config hiện tại */
        @Volatile
        private var CURRENT_CONFIG: Config = Config()

        /**
         * Khởi tạo ImageLoader với custom config.
         *
         * **Lưu ý**: Phải gọi TRƯỚC lần `with()` đầu tiên.
         * Sau khi singleton đã được tạo thì config không thể thay đổi.
         *
         * @param context Application context
         * @param block Lambda để config Builder
         *
         * ## Example:
         * ```kotlin
         * ImageLoader.init(context) {
         *     enableBitmapPool(0.4f)
         * }
         * ```
         */
        fun init(context: Context, block: Builder.() -> Unit) {
            synchronized(this) {
                if (INSTANCE != null) {
                    throw IllegalStateException(
                        "ImageLoader đã được khởi tạo. " +
                                "init() phải được gọi trước khi sử dụng with()"
                    )
                }
                val builder = Builder().apply(block)
                CURRENT_CONFIG = builder.build()
                INSTANCE = ImageLoader(context.applicationContext, CURRENT_CONFIG)
            }
        }

        /**
         * Lấy singleton instance của ImageLoader.
         *
         * Thread-safe với Double-Checked Locking pattern:
         * 1. Check nhanh ngoài synchronized block
         * 2. Synchronized chỉ khi instance == null
         * 3. Double-check bên trong synchronized
         *
         * @param context Context của app (sẽ convert sang applicationContext)
         * @return Singleton instance
         */
        private fun getInstance(context: Context): ImageLoader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageLoader(context.applicationContext, CURRENT_CONFIG).also {
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
         * @return RequestBuilder để config và execute request
         */
        fun with(context: Context): RequestBuilder {
            return RequestBuilder(getInstance(context).engine)
        }
    }
}
