package com.example.imageloader.target


import android.widget.ImageView
import androidx.core.view.doOnDetach
import com.example.imageloader.core.EngineResource


class ImageViewTarget(private val imageView: ImageView) : Target {
    private var current: EngineResource? = null


    override fun onResourceReady(engineResource: EngineResource) {
        // release previous
        current?.release()


        current = engineResource
        current?.acquire()


        imageView.setImageBitmap(engineResource.getBitmap())


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
