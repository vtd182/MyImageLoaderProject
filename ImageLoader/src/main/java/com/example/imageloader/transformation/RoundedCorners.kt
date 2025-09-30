package com.example.imageloader.transformation

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.example.imageloader.core.abstract.BitmapPool

class RoundedCorners(private val radius: Float) : BaseTransformation("RoundedCorners($radius)") {
    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val scale = maxOf(
            outWidth.toFloat() / toTransform.width,
            outHeight.toFloat() / toTransform.height
        )
        val dx = (outWidth - toTransform.width * scale) / 2f
        val dy = (outHeight - toTransform.height * scale) / 2f

        val shader = BitmapShader(toTransform, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
            }
            setLocalMatrix(matrix)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        val result = getOrCreateBitmap(pool, outWidth, outHeight)
        Canvas(result).drawRoundRect(
            RectF(0f, 0f, outWidth.toFloat(), outHeight.toFloat()),
            radius,
            radius,
            paint
        )
        return result
    }
}
