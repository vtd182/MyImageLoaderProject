package com.example.imageloader.core

import android.graphics.Bitmap
import com.example.imageloader.core.abstract.ResourceListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * EngineResource - Wrapper quản lý lifecycle của Bitmap trong cache system.
 *
 * Đây là thành phần quan trọng giúp theo dõi và quản lý việc sử dụng bitmap:
 * - Track số lượng View đang reference đến bitmap (reference counting)
 * - Tự động chuyển bitmap xuống Memory Cache khi không còn được sử dụng
 * - Đảm bảo bitmap không bị recycle khi đang được hiển thị
 *
 * ## Reference Counting Pattern:
 * ```
 * View acquire bitmap → refCount++
 * View release bitmap → refCount--
 * refCount == 0 → callback listener → chuyển xuống Memory Cache
 * ```
 *
 * ## Thread Safety:
 * - Sử dụng `@Synchronized` cho acquire/release
 * - AtomicBoolean cho trạng thái released
 * - Tránh race condition khi nhiều View cùng release
 *
 * @param key Cache key của resource
 * @param bitmap Bitmap được quản lý
 * @param listener Callback khi resource được released (thường là ActiveResources)
 */
class EngineResource(
    val key: String,
    private val bitmap: Bitmap,
    private val listener: ResourceListener
) {
    /** Số lượng View đang reference đến bitmap này */
    private var refCount = 0
    
    /** Flag đánh dấu resource đã được released hay chưa */
    private val released = AtomicBoolean(false)

    /**
     * Tăng reference count khi View bắt đầu sử dụng resource.
     *
     * Được gọi khi Target (ImageView) nhận được bitmap.
     * Thread-safe nhờ @Synchronized annotation.
     *
     * @throws IllegalStateException nếu resource đã bị released
     */
    @Synchronized
    fun acquire() {
        check(!released.get()) { "Cannot acquire a released resource" }
        refCount++
    }
    
    /**
     * Kiểm tra resource đã được released hay chưa.
     *
     * @return true nếu resource đã released và được chuyển xuống Memory Cache
     */
    fun isReleased(): Boolean = released.get()

    /**
     * Giảm reference count khi View không còn sử dụng resource.
     *
     * Được gọi khi:
     * - View bị detached
     * - ImageView load ảnh mới
     * - Activity/Fragment bị destroy
     *
     * Khi refCount về 0:
     * - Set flag released = true (atomic)
     * - Callback listener để chuyển bitmap xuống Memory Cache
     * - Bitmap sẵn sàng để tái sử dụng cho request khác
     *
     * @throws IllegalStateException nếu release mà chưa acquire
     */
    @Synchronized
    fun release() {
        check(refCount > 0) { "Cannot release a resource that is not acquired" }
        refCount--
        if (refCount == 0 && released.compareAndSet(false, true)) {
            listener.onResourceReleased(key, this)
        }
    }

    /**
     * Lấy Bitmap được wrap bởi resource này.
     *
     * @return Bitmap instance
     */
    fun getBitmap(): Bitmap = bitmap

    /**
     * Kiểm tra bitmap có thể modify được không.
     *
     * @return true nếu bitmap mutable (có thể dùng cho BitmapPool)
     */
    fun isMutable(): Boolean = bitmap.isMutable

    /**
     * Tính kích thước bitmap trong memory (bytes).
     *
     * ## Kỹ thuật:
     * - Ưu tiên dùng `allocationByteCount` (API 19+) - chính xác hơn
     * - Fallback sang `byteCount` nếu fail
     * - Dùng để tính toán LRU cache eviction
     *
     * @return Kích thước bitmap tính bằng bytes
     */
    fun sizeInBytes(): Int = try {
        bitmap.allocationByteCount
    } catch (t: Throwable) {
        bitmap.byteCount
    }

    /**
     * Recycle bitmap để giải phóng memory.
     *
     * ## Chú ý:
     * - Chỉ nên gọi khi chắc chắn bitmap không còn được dùng
     * - Bitmap sau khi recycle không thể sử dụng lại
     * - Thường được gọi bởi cache eviction logic
     */
    fun recycle() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}