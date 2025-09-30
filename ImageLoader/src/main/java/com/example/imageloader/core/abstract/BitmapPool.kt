package com.example.imageloader.core.abstract

import android.graphics.Bitmap
import android.graphics.Bitmap.Config

interface BitmapPool {
    fun get(width: Int, height: Int, config: Config): Bitmap?
    fun put(bitmap: Bitmap)
    fun clear()
    fun size(): Long
}