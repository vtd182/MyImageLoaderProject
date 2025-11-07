package com.example.imageloader.cache

import com.example.imageloader.core.EngineResource
import com.example.imageloader.core.abstract.ResourceListener

/**
 * ActiveResources - Cache tầng 1 cho các bitmaps đang được View sử dụng (active).
 *
 * ## Vai trò trong Cache Hierarchy:
 * ```
 * 1. ActiveResources ← Bitmaps đang hiển thị trên UI
 * 2. MemoryCache     ← Bitmaps đã decode nhưng không còn dùng
 * 3. DiskCache       ← Raw image data trên disk
 * 4. Network         ← Fetch từ internet
 * ```
 *
 * ## Mục đích:
 * - Track các bitmaps đang được Views reference (đang hiển thị)
 * - Ngăn bitmap bị evict khỏi Memory Cache khi còn đang được dùng
 * - Tự động chuyển bitmap xuống Memory Cache khi không còn được dùng
 *
 * ## Reference Counting Flow:
 * ```
 * ImageView nhận bitmap → EngineResource.acquire()
 * → activeMap[key] = resource
 * → refCount = 1
 *
 * ImageView load ảnh mới → EngineResource.release()
 * → refCount = 0
 * → onResourceReleased() callback
 * → activeMap.remove(key)
 * → MemoryCache.put(key, bitmap)
 * ```
 *
 * ## Tại sao cần Active cache riêng:
 * Nếu không có Active cache:
 * - Memory Cache có thể evict bitmap đang được hiển thị
 * - ImageView sẽ hiển thị blank hoặc crash
 * - Phải decode lại ngay cả khi bitmap đang được dùng
 *
 * ## Thread-safety:
 * - Tất cả methods đều @Synchronized
 * - An toàn khi gọi từ multiple threads
 *
 * @see com.example.imageloader.core.EngineResource
 * @see com.example.imageloader.cache.MemoryCache
 */
class ActiveResources : ResourceListener {
    /** Map lưu các resources đang active, key = cache key */
    private val activeMap = mutableMapOf<String, EngineResource>()
    
    /** Callback được gọi khi resource released (để chuyển xuống Memory Cache) */
    private var resourceReleasedCallback: ((String, EngineResource) -> Unit)? = null

    /**
     * Set callback để được notify khi resource released.
     * Thường được Engine dùng để chuyển bitmap xuống Memory Cache.
     *
     * @param callback Lambda nhận (key, resource) khi resource released
     */
    fun setOnResourceReleased(callback: (String, EngineResource) -> Unit) {
        resourceReleasedCallback = callback
    }

    /**
     * Put một EngineResource vào active cache.
     * Được gọi khi Engine deliver bitmap về Target.
     *
     * @param key Cache key
     * @param engineResource Resource wrap bitmap và quản lý lifecycle
     */
    @Synchronized
    fun put(key: String, engineResource: EngineResource) {
        activeMap[key] = engineResource
    }

    /**
     * Lấy EngineResource từ active cache.
     * Return null nếu không tìm thấy hoặc đã released.
     *
     * @param key Cache key
     * @return EngineResource nếu còn active, null nếu không
     */
    @Synchronized
    fun get(key: String): EngineResource? {
        return activeMap[key]
    }

    /**
     * Remove resource khỏi active cache.
     * Thường được gọi khi resource invalid hoặc error.
     *
     * @param key Cache key cần remove
     */
    @Synchronized
    fun remove(key: String) {
        activeMap.remove(key)
    }

    /**
     * Implementation của ResourceListener.onResourceReleased().
     *
     * ## Flow:
     * 1. Remove resource khỏi activeMap
     * 2. Invoke callback (Engine sẽ put bitmap vào Memory Cache)
     *
     * ## Thread-safety:
     * Synchronized để tránh race condition với get/put operations.
     *
     * @param key Cache key của resource
     * @param engineResource Resource đã released
     */
    override fun onResourceReleased(key: String, engineResource: EngineResource) {
        synchronized(this) {
            activeMap.remove(key)
        }
        resourceReleasedCallback?.invoke(key, engineResource)
    }
}
