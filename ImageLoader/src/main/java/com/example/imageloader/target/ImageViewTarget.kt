package com.example.imageloader.target


import android.widget.ImageView
import androidx.core.view.doOnDetach
import com.example.imageloader.core.EngineResource


class ImageViewTarget(private val imageView: ImageView) : Target {
    private var current: EngineResource? = null


    override fun onResourceReady(engineResource: EngineResource) {
        // clear old bitmap reference in ImageView
        imageView.setImageDrawable(null)

        // release previous resource safely
        current?.release()

        // set new resource
        current = engineResource
        current?.acquire()

        val bitmap = engineResource.getBitmap()
        if (bitmap.isRecycled) {
            android.util.Log.w("ImageViewTarget", "⚠️ Bitmap already recycled, skip setting image")
            return
        }

        try {
            imageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            android.util.Log.e("ImageViewTarget", "❌ Failed to set bitmap", e)
        }

        // clear on detach
        imageView.doOnDetach {
            current?.release()
            current = null
        }
    }


    override fun onLoadFailed() {
        imageView.setImageDrawable(null)
    }

    override fun onPlaceholderColor(color: Int) {
        imageView.setBackgroundColor(color)
    }

    override fun isValidFor(key: String): Boolean {
        return current?.key == key
    }

    fun clear() {
        current?.release()
        current = null
        imageView.setImageDrawable(null)
    }
}
