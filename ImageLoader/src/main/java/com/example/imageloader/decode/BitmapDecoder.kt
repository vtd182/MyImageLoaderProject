package com.example.imageloader.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import com.example.imageloader.core.BitmapPool

object BitmapDecoder {
    fun decode(bytes: ByteArray, reqW: Int, reqH: Int, pool: BitmapPool?): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = true
        }

        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IllegalStateException("Decode returned null")

        return bmp
    }

    fun extractDominantColor(bytes: ByteArray, thumbSize: Int = 10): Int {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSizeForThumb(thumbSize, thumbSize)
        }

        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: return 0xFFCCCCCC.toInt() // xám nhạt

        val palette = Palette.from(bmp).generate()
        bmp.recycle()

        return palette.getVibrantColor(
            palette.getMutedColor(
                palette.getDominantColor(0xFFCCCCCC.toInt())
            )
        )
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

    private fun calculateInSampleSizeForThumb(reqW: Int, reqH: Int): Int {
        return 512 / reqW
    }
}
