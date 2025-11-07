package com.example.imageloader.target

import com.example.imageloader.core.EngineResource

/**
 * Target - Interface định nghĩa lifecycle callbacks cho image loading operations.
 *
 * ## Mục đích:
 * Target là abstraction của nơi nhận kết quả load ảnh:
 * - Thường là ImageView (ImageViewTarget)
 * - Có thể là custom target (vd: notification, widget)
 * - Nhận callbacks về các trạng thái load: started, success, failed
 *
 * ## Lifecycle:
 * ```
 * 1. onLoadStarted()
 *    ↓
 * 2a. onResourceReady()      → Success
 * 2b. onLoadFailed()         → Failed (with retry option)
 * ```
 *
 * ## Use cases:
 * - **ImageViewTarget**: Display bitmap vào ImageView
 * - **Custom targets**: Bitmap post-processing, analytics, etc.
 * - **Notification**: Load ảnh cho notification
 * - **Widget**: Load ảnh cho home screen widget
 *
 * ## Implementation example:
 * ```kotlin
 * class MyCustomTarget : Target {
 *     override fun onLoadStarted() {
 *         showLoadingIndicator()
 *     }
 *
 *     override fun onResourceReady(engineResource: EngineResource) {
 *         val bitmap = engineResource.getBitmap()
 *         // Do something with bitmap
 *         engineResource.acquire() // Important: track usage
 *     }
 *
 *     override fun onLoadFailed(onRetry: (() -> Unit)?) {
 *         showErrorState()
 *         retryButton.setOnClickListener { onRetry?.invoke() }
 *     }
 *
 *     override fun isValidFor(key: String): Boolean {
 *         return currentRequestKey == key
 *     }
 * }
 * ```
 *
 * ## Thread-safety:
 * - Callbacks được gọi trên Main thread
 * - Implementation không cần handle threading
 *
 * @see com.example.imageloader.target.ImageViewTarget
 * @see com.example.imageloader.core.EngineResource
 */
interface Target {
    /**
     * Callback khi bắt đầu load ảnh.
     *
     * ## Use cases:
     * - Show loading placeholder
     * - Show shimmer effect
     * - Clear old image
     * - Start loading animation
     *
     * ## Threading:
     * Luôn được gọi trên Main thread.
     */
    fun onLoadStarted()

    /**
     * Callback khi load thành công và resource ready.
     *
     * ## Responsibilities:
     * - Get bitmap từ engineResource
     * - Display/process bitmap
     * - **IMPORTANT**: Call `engineResource.acquire()` để track usage
     * - Release resource khi done (gọi `release()`)
     *
     * ## Example:
     * ```kotlin
     * override fun onResourceReady(engineResource: EngineResource) {
     *     engineResource.acquire() // Track usage
     *     imageView.setImageBitmap(engineResource.getBitmap())
     *     
     *     // Later, when done:
     *     engineResource.release() // When ImageView cleared
     * }
     * ```
     *
     * ## Threading:
     * Luôn được gọi trên Main thread.
     *
     * @param engineResource Resource wrapper chứa bitmap và lifecycle management
     */
    fun onResourceReady(engineResource: EngineResource)

    /**
     * Callback khi load failed.
     *
     * ## Responsibilities:
     * - Show error placeholder/drawable
     * - Log error
     * - Optionally trigger retry
     *
     * ## Retry mechanism:
     * ```kotlin
     * override fun onLoadFailed(onRetry: (() -> Unit)?) {
     *     showErrorImage()
     *     retryButton.setOnClickListener {
     *         onRetry?.invoke() // Retry load
     *     }
     * }
     * ```
     *
     * ## Threading:
     * Luôn được gọi trên Main thread.
     *
     * @param onRetry Optional callback để retry load request
     */
    fun onLoadFailed(onRetry: (() -> Unit)? = null)

    /**
     * Callback để set placeholder color (từ dominant color extraction).
     *
     * ## Use case:
     * Show dominant color của ảnh làm placeholder trước khi ảnh load xong.
     * Tạo smooth transition và better UX.
     *
     * @param color Dominant color extracted từ ảnh (ARGB format)
     */
    fun onPlaceholderColor(color: Int)

    /**
     * Kiểm tra target có còn valid cho request key này không.
     *
     * ## Purpose:
     * Tránh deliver kết quả vào wrong target khi:
     * - ImageView bị reuse (RecyclerView)
     * - Request bị cancel và start request mới
     * - View đã bị destroyed
     *
     * ## Example:
     * ```kotlin
     * private var currentKey: String? = null
     *
     * override fun isValidFor(key: String): Boolean {
     *     return currentKey == key && imageView.isAttachedToWindow
     * }
     * ```
     *
     * @param key Cache key của request
     * @return true nếu target còn valid cho key này, false nếu không
     */
    fun isValidFor(key: String): Boolean
}
