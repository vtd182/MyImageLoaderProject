package com.example.imageloader.transformation

import android.graphics.Bitmap
import com.example.imageloader.core.abstract.BitmapPool

/**
 * Transformation - Interface định nghĩa bitmap transformations.
 *
 * ## Mục đích:
 * Transform bitmap sau khi decode để đạt được visual effects:
 * - Crop (center crop, custom crop)
 * - Resize (scale, fit, fill)
 * - Effects (blur, grayscale, rounded corners)
 * - Filters (brightness, contrast, saturation)
 *
 * ## Chain of Transformations:
 * Có thể apply nhiều transformations tuần tự:
 * ```kotlin
 * ImageLoader.with(context)
 *     .load(url)
 *     .transform(
 *         CenterCropTransformation(),
 *         RoundedCornersTransformation(16f),
 *         BlurTransformation(25f)
 *     )
 *     .into(imageView)
 * ```
 *
 * ## BitmapPool Integration:
 * - Transformations nên dùng BitmapPool để reuse bitmaps
 * - Giảm memory allocation và GC pressure
 * - Quan trọng khi transform nhiều ảnh liên tục
 *
 * ## Key Method:
 * `key()` trả về unique identifier cho transformation:
 * - Dùng để build cache key
 * - Các transformations khác nhau phải có keys khác nhau
 * - Same transformation + same params = same key
 *
 * ## Implementation Example:
 * ```kotlin
 * class GrayscaleTransformation : Transformation {
 *     override fun transform(
 *         pool: BitmapPool,
 *         toTransform: Bitmap,
 *         outWidth: Int,
 *         outHeight: Int
 *     ): Bitmap {
 *         val output = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888)
 *             ?: Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
 *         
 *         val canvas = Canvas(output)
 *         val paint = Paint()
 *         val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
 *         paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
 *         canvas.drawBitmap(toTransform, 0f, 0f, paint)
 *         
 *         return output
 *     }
 *     
 *     override fun key(): String = "GrayscaleTransformation"
 * }
 * ```
 *
 * @see com.example.imageloader.transformation.BaseTransformation
 * @see com.example.imageloader.transformation.CenterCropRoundedCorners
 */
interface Transformation {
    /**
     * Transform bitmap theo yêu cầu.
     *
     * ## Responsibilities:
     * - Tạo bitmap mới với kích thước outWidth x outHeight
     * - Apply transformation lên toTransform
     * - Sử dụng pool để reuse bitmap (nếu có)
     * - Return transformed bitmap
     *
     * ## Performance tips:
     * - Reuse bitmaps từ pool
     * - Recycle toTransform nếu không cần nữa
     * - Tránh tạo unnecessary objects trong loop
     *
     * @param pool BitmapPool để reuse bitmaps
     * @param toTransform Bitmap gốc cần transform
     * @param outWidth Target width
     * @param outHeight Target height
     * @return Transformed bitmap
     */
    fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap
    
    /**
     * Return unique key cho transformation này.
     *
     * Key được dùng để:
     * - Build cache key (cùng transformation = cùng key = cache hit)
     * - Debug và logging
     *
     * ## Guidelines:
     * - Include class name
     * - Include parameters ảnh hưởng đến output
     * - Ví dụ: "RoundedCorners(16.0)", "Blur(25.0)"
     *
     * @return Unique identifier string
     */
    fun key(): String
}
