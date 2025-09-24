package com.example.imageloader.target


import android.util.Log
import android.widget.ImageView
import androidx.core.view.doOnDetach
import com.example.imageloader.core.Resource


class ImageViewTarget(private val imageView: ImageView) : Target {
    private var current: Resource? = null


    override fun onResourceReady(resource: Resource) {
        // release previous
        current?.release()


        current = resource
        current?.acquire()


        imageView.setImageBitmap(resource.getBitmap())


        imageView.doOnDetach {
            Log.d("ImageViewTarget", "onResourceReady doOnDetach: ${resource.key}")
            current?.release()
            current = null
        }
    }


    override fun onLoadFailed() {
        imageView.setImageDrawable(null)
    }

    fun clear() {
        current?.release()
        current = null
        imageView.setImageDrawable(null)
    }
}
