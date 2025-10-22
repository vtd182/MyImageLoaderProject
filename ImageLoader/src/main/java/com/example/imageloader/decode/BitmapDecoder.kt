package com.example.imageloader.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.palette.graphics.Palette
import com.example.imageloader.core.abstract.BitmapPool

object BitmapDecoder {
    private var bitmapPool: BitmapPool? = null
    private var useBitmapPool: Boolean = false
    
    fun setBitmapPool(pool: BitmapPool) {
        bitmapPool = pool
    }
    
    fun setUseBitmapPool(enabled: Boolean) {
        useBitmapPool = enabled
        Log.d("BitmapDecoder", "BitmapPool for decode: ${if (enabled) "ENABLED" else "DISABLED"}")
    }
    
    fun decode(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
        val decodedWidth = bounds.outWidth / sample
        val decodedHeight = bounds.outHeight / sample

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = true
            
            // Try to reuse bitmap from pool if enabled
            if (useBitmapPool) {
                bitmapPool?.get(decodedWidth, decodedHeight, Bitmap.Config.ARGB_8888)?.let { poolBitmap ->
                    inBitmap = poolBitmap
                    Log.d("BitmapDecoder", "Reusing bitmap from pool for decode: ${decodedWidth}x${decodedHeight}")
                }
            }
        }

        val bmp = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (e: IllegalArgumentException) {
            // inBitmap failed, retry without it
            Log.w("BitmapDecoder", "Failed to reuse bitmap, decoding without pool")
            opts.inBitmap = null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } ?: throw IllegalStateException("Decode returned null")

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

    internal fun calculateInSampleSize(outW: Int, outH: Int, reqW: Int, reqH: Int): Int {
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

    internal fun calculateInSampleSizeForThumb(reqW: Int, reqH: Int): Int {
        return 512 / reqW
    }
}
