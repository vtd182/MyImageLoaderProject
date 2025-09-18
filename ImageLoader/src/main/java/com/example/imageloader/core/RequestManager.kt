package com.example.imageloader.core

import android.widget.ImageView
import kotlinx.coroutines.Job

object RequestManager {
    private val running = mutableMapOf<ImageView, Job>()


    @Synchronized
    fun track(imageView: ImageView, job: Job?) {
        running[imageView]?.cancel()
        if (job != null) {
            running[imageView] = job
        } else {
            running.remove(imageView)
        }
    }

    @Synchronized
    fun clear(imageView: ImageView) {
        running[imageView]?.cancel()
        running.remove(imageView)
        imageView.setImageDrawable(null)
    }
}
