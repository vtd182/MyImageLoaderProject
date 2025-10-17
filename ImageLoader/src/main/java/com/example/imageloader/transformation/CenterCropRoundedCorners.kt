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

class CenterCropRoundedCorners(val radius: Float) :
    BaseTransformation("CenterCropRoundedCorners($radius)") {

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        if (outWidth <= 0 || outHeight <= 0 || toTransform.isRecycled) {
            return toTransform
        }

        // ✅ Lấy bitmap mới hoặc từ pool, luôn làm sạch
        val result = createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        if (result === toTransform) {
            // Nếu pool trả chính bitmap này thì tạo bản copy tránh đè lên
            return toTransform.copy(Bitmap.Config.ARGB_8888, false)
        }

        result.eraseColor(0) // đảm bảo canvas trống

        val safeBitmap = if (toTransform.isMutable && !toTransform.isRecycled)
            toTransform else toTransform.copy(Bitmap.Config.ARGB_8888, false)

        val canvas = Canvas(result)

        // ✅ Tính toán crop trung tâm
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
