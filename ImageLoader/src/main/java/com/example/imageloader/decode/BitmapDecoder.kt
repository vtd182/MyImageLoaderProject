package com.example.imageloader.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object BitmapDecoder {
    fun decode(bytes: ByteArray, reqW: Int = 0, reqH: Int = 0): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        options.inSampleSize = calculateInSampleSize(options, reqW, reqH)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqH || width > reqW) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqH && halfWidth / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
