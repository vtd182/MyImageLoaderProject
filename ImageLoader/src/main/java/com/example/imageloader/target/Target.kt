package com.example.imageloader.target

import android.graphics.Bitmap

interface Target {
    fun onResourceReady(bitmap: Bitmap)
    fun onLoadFailed()
}
