package com.example.imageloader.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.imageloader.core.BitmapPool
import java.io.ByteArrayInputStream

object BitmapDecoder {
    fun decode(bytes: ByteArray, reqW: Int, reqH: Int, pool: BitmapPool?): Bitmap {
        // 1. decode bounds
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = true
        }

        // try to get an inBitmap from pool
        if (pool != null) {
            val targetW = (bounds.outWidth / sample).coerceAtLeast(1)
            val targetH = (bounds.outHeight / sample).coerceAtLeast(1)
            val candidate = pool.get(targetW, targetH, Bitmap.Config.ARGB_8888)
            if (candidate != null) {
                try {
                    opts.inBitmap = candidate
                } catch (e: IllegalArgumentException) {
                    // inBitmap not compatible, ignore and continue without it
                }
            }
        }

        // actual decode
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IllegalStateException("Decode returned null")

        return bmp
    }

    private fun calculateInSampleSize(outW: Int, outH: Int, reqW: Int, reqH: Int): Int {
        if (reqW <= 0 || reqH <= 0) return 1
        var inSampleSize = 1
        if (outH > reqH || outW > reqW) {
            val halfH = outH / 2
            val halfW = outW / 2
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
