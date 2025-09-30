package com.example.imageloader.transformation

import android.graphics.Bitmap
import com.example.imageloader.core.abstract.BitmapPool

interface Transformation {
    fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap
    fun key(): String
}
