package com.example.imageloader.transformation

import android.graphics.Bitmap
import com.example.imageloader.core.abstract.BitmapPool

/**
 * BaseTransformation - Abstract base class cho Transformation implementations.
 *
 * ## Mục đích:
 * Cung cấp utility methods và boilerplate code chung cho các transformations:
 * - Bitmap creation với pool support
 * - Key generation
 * - Common patterns
 *
 * ## Benefits:
 * - Giảm code duplication
 * - Đảm bảo pool được sử dụng đúng cách
 * - Consistent key generation
 *
 * ## Usage:
 * ```kotlin
 * class MyTransformation(private val param: Float) : BaseTransformation("MyTransformation($param)") {
 *     override fun transform(
 *         pool: BitmapPool,
 *         toTransform: Bitmap,
 *         outWidth: Int,
 *         outHeight: Int
 *     ): Bitmap {
 *         // Get bitmap from pool or create new
 *         val output = getOrCreateBitmap(pool, outWidth, outHeight)
 *         
 *         // Apply transformation
 *         // ...
 *         
 *         return output
 *     }
 * }
 * ```
 *
 * @param id Unique identifier cho transformation (dùng làm cache key)
 *
 * @see com.example.imageloader.transformation.Transformation
 * @see com.example.imageloader.transformation.CenterCropRoundedCorners
 */
abstract class BaseTransformation(
    private val id: String
) : Transformation {

    /**
     * Get bitmap từ pool hoặc tạo mới nếu pool không có.
     *
     * ## Pattern:
     * ```
     * pool.get() → Hit: reuse bitmap
     *           → Miss: create new bitmap
     * ```
     *
     * ## Use case:
     * Dùng method này thay vì tạo bitmap trực tiếp để:
     * - Tận dụng pool khi có
     * - Giảm memory allocation
     * - Consistent với pool strategy
     *
     * @param pool BitmapPool instance
     * @param width Target width
     * @param height Target height
     * @param config Bitmap config (default: ARGB_8888)
     * @return Bitmap từ pool hoặc mới tạo
     */
    protected fun getOrCreateBitmap(
        pool: BitmapPool,
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap {
        return pool.get(width, height, config) ?: createBitmap(width, height, config)
    }

    /**
     * Tạo bitmap mới với kích thước và config cho trước.
     *
     * @param width Width
     * @param height Height
     * @param config Bitmap config
     * @return New Bitmap instance
     */
    protected fun createBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        return Bitmap.createBitmap(width, height, config)
    }

    /**
     * Return key đã được set trong constructor.
     */
    override fun key(): String = id
}