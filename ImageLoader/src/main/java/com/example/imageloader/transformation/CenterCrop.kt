package com.example.imageloader.transformation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.example.imageloader.core.abstract.BitmapPool

class CenterCrop : BaseTransformation("CenterCrop") {
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

        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }

        val result = getOrCreateBitmap(pool, outWidth, outHeight)
        Canvas(result).drawBitmap(toTransform, matrix, Paint(Paint.ANTI_ALIAS_FLAG))
        return result
    }
}

