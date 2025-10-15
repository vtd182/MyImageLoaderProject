package com.example.imageloader.transformation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
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
        if (outWidth <= 0 || outHeight <= 0) return toTransform

        val result = getOrCreateBitmap(pool, outWidth, outHeight, Bitmap.Config.ARGB_8888)
        result.eraseColor(Color.TRANSPARENT)

        val canvas = Canvas(result)

        val scale = max(
            outWidth.toFloat() / toTransform.width,
            outHeight.toFloat() / toTransform.height
        )

        val scaledWidth = toTransform.width * scale
        val scaledHeight = toTransform.height * scale

        val left = (outWidth - scaledWidth) / 2f
        val top = (outHeight - scaledHeight) / 2f

        val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)

        val saveCount = canvas.saveLayer(0f, 0f, outWidth.toFloat(), outHeight.toFloat(), null)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val rect = RectF(0f, 0f, outWidth.toFloat(), outHeight.toFloat())
        canvas.drawRoundRect(rect, radius, radius, maskPaint)

        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        val srcRect = Rect(0, 0, toTransform.width, toTransform.height)
        canvas.drawBitmap(toTransform, srcRect, destRect, imagePaint)
        imagePaint.xfermode = null

        canvas.restoreToCount(saveCount)

        return result
    }
}
