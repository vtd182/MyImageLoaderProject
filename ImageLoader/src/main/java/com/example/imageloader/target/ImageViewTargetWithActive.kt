package com.example.imageloader.target

import android.graphics.Bitmap
import android.widget.ImageView
import com.example.imageloader.cache.ActiveResources

class ImageViewTargetWithActive(
    private val imageView: ImageView,
    private val activeResources: ActiveResources,
    private val key: String
) : Target {

    override fun onResourceReady(bitmap: Bitmap) {
        imageView.setImageBitmap(bitmap)
        activeResources.put(key, bitmap)
    }

    override fun onLoadFailed() {
        imageView.setImageBitmap(null)
        activeResources.remove(key)
    }
}
