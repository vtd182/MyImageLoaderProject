package com.example.imageloader.transformation

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.example.imageloader.core.abstract.BitmapPool
import kotlin.math.max

/**
 * CenterCropRoundedCorners - Transformation kết hợp center crop và rounded corners.
 *
 * ## Mục đích:
 * Transform ảnh theo 2 bước:
 * 1. **Center Crop**: Scale và crop ảnh để fill đầy outWidth x outHeight
 * 2. **Rounded Corners**: Bo tròn các góc với radius cho trước
 *
 * ## Center Crop Algorithm:
 * ```
 * Scale = max(outWidth/imageWidth, outHeight/imageHeight)
 * → Scale để ảnh cover toàn bộ output size
 * → Crop phần thừa ở center
 * ```
 *
 * ## Visual Example:
 * ```
 * Input: 1000x500 image
 * Output: 300x300
 * 
 * Step 1: Scale
 * scale = max(300/1000, 300/500) = 0.6
 * scaled = 600x300
 * 
 * Step 2: Center & Crop
 * dx = (300 - 600) / 2 = -150 (shift left)
 * dy = (300 - 300) / 2 = 0
 * → Crop center 300x300 portion
 * 
 * Step 3: Round corners with radius
 * ```
 *
 * ## Use cases:
 * - Avatar images (crop face to center + round)
 * - Thumbnail previews
 * - Card images trong lists
 * - Material Design cards
 *
 * ## Usage:
 * ```kotlin
 * ImageLoader.with(context)
 *     .load(avatarUrl)
 *     .transform(CenterCropRoundedCorners(16f)) // 16dp radius
 *     .into(imageView)
 * ```
 *
 * @param radius Corner radius in pixels
 *
 * @see com.example.imageloader.transformation.BaseTransformation
 */
class CenterCropRoundedCorners(val radius: Float) :
    BaseTransformation("CenterCropRoundedCorners($radius)") {

    /**
     * Apply center crop và rounded corners transformation.
     *
     * ## Process:
     * 1. Validate input (size > 0, bitmap not recycled)
     * 2. Create output bitmap (outWidth x outHeight)
     * 3. Ensure source bitmap is usable (mutable copy if needed)
     * 4. Calculate scale và translation (center crop math)
     * 5. Create BitmapShader với matrix transform
     * 6. Draw rounded rect với shader
     *
     * ## Performance:
     * - BitmapShader: Hardware accelerated
     * - Single draw call: Efficient
     * - No intermediate bitmaps: Saves memory
     */
    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        if (outWidth <= 0 || outHeight <= 0 || toTransform.isRecycled) {
            return toTransform
        }

        val result = createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)

        val safeBitmap = if (toTransform.isMutable && !toTransform.isRecycled)
            toTransform else toTransform.copy(Bitmap.Config.ARGB_8888, false)

        val canvas = Canvas(result)

        val scale = max(
            outWidth.toFloat() / safeBitmap.width,
            outHeight.toFloat() / safeBitmap.height
        )
        val scaledWidth = safeBitmap.width * scale
        val scaledHeight = safeBitmap.height * scale
        val dx = (outWidth - scaledWidth) / 2f
        val dy = (outHeight - scaledHeight) / 2f

        val shader = BitmapShader(safeBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        shader.setLocalMatrix(matrix)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            isFilterBitmap = true
        }

        val rect = RectF(0f, 0f, outWidth.toFloat(), outHeight.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)

        return result
    }
}
