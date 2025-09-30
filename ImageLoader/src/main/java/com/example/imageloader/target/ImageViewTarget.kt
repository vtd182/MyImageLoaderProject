package com.example.imageloader.target


import android.util.Log
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
            Log.d("ImageViewTarget", "onResourceReady doOnDetach: ${engineResource.key}")
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

    fun clear() {
        current?.release()
        current = null
        imageView.setImageDrawable(null)
    }
}
