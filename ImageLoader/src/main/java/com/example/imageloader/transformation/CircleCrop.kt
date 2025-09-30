package com.example.imageloader.transformation

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import com.example.imageloader.core.abstract.BitmapPool

class CircleCrop : BaseTransformation("CircleCrop") {
    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val size = minOf(outWidth, outHeight)

        val scale = maxOf(
            size.toFloat() / toTransform.width,
            size.toFloat() / toTransform.height
        )
        val dx = (size - toTransform.width * scale) / 2f
        val dy = (size - toTransform.height * scale) / 2f

        val shader = BitmapShader(toTransform, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(dx, dy)
            }
            setLocalMatrix(matrix)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        val result = getOrCreateBitmap(pool, size, size)
        Canvas(result).drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return result
    }
}
