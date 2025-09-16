package com.example.imageloader.target

import android.widget.ImageView
import android.graphics.Bitmap
import android.util.Log

class ImageViewTarget(private val imageView: ImageView) : Target {
    override fun onResourceReady(bitmap: Bitmap) {
        Log.d("Engine", "bitmap: ${bitmap.width} x ${bitmap.height}")
        imageView.setImageBitmap(bitmap)
    }

    override fun onLoadFailed() {
        imageView.setImageDrawable(null)
    }
}
