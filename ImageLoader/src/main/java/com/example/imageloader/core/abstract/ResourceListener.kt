package com.example.imageloader.core.abstract

import com.example.imageloader.core.EngineResource

/**
 * ResourceListener - Callback interface để lắng nghe sự kiện resource lifecycle.
 *
 * ## Mục đích:
 * Interface này cho phép các components (như ActiveResources) được notify
 * khi một EngineResource không còn được sử dụng và sẵn sàng chuyển xuống Memory Cache.
 *
 * ## Pattern:
 * Observer pattern - EngineResource notify listener khi reference count về 0.
 *
 * ## Flow:
 * ```
 * EngineResource.release()
 * → refCount--
 * → refCount == 0
 * → listener.onResourceReleased()
 * → ActiveResources remove resource
 * → Memory Cache put bitmap
 * ```
 *
 * ## Implementation:
 * Thư viện cung cấp `ActiveResources` - implementation chính của interface này.
 *
 * @see com.example.imageloader.core.EngineResource
 * @see com.example.imageloader.cache.ActiveResources
 */
interface ResourceListener {
    /**
     * Callback khi một resource được released (reference count về 0).
     *
     * ## Được gọi khi:
     * - EngineResource.release() được gọi và refCount về 0
     * - ImageView không còn hiển thị bitmap
     * - View bị detached hoặc load ảnh mới
     *
     * ## Typical implementation:
     * ```kotlin
     * override fun onResourceReleased(key: String, engineResource: EngineResource) {
     *     // 1. Remove khỏi active cache
     *     activeMap.remove(key)
     *
     *     // 2. Chuyển bitmap xuống memory cache để reuse
     *     val bitmap = engineResource.getBitmap()
     *     if (!bitmap.isRecycled) {
     *         memoryCache.put(key, bitmap)
     *     }
     * }
     * ```
     *
     * ## Thread-safety:
     * Implementation phải thread-safe vì có thể được gọi từ nhiều threads.
     *
     * @param key Cache key của resource
     * @param engineResource EngineResource đã được released
     */
    fun onResourceReleased(key: String, engineResource: EngineResource)
}