package com.example.imageloader.target


import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.view.doOnAttach
import androidx.core.view.doOnDetach
import com.example.imageloader.core.EngineResource


class ImageViewTarget(
    private val imageView: ImageView,
    private val errorDrawable: Drawable? = null
) : Target {
    private var current: EngineResource? = null
    private var retryCallback: (() -> Unit)? = null
    private val loadingHandler = Handler(Looper.getMainLooper())
    private var loadingRunnable: Runnable? = null
    private var progressBar: ProgressBar? = null

    companion object {
        private const val LOADING_DELAY_MS = 500L
    }


    override fun onLoadStarted() {
        loadingRunnable?.let { loadingHandler.removeCallbacks(it) }

        // Clear placeholder background when starting to load
        imageView.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        loadingRunnable = Runnable {
            showLoading()
        }
        loadingHandler.postDelayed(loadingRunnable!!, LOADING_DELAY_MS)
    }

    override fun onResourceReady(engineResource: EngineResource) {
        hideLoading()
        
        // clear old bitmap reference in ImageView
        imageView.setImageDrawable(null)

        // release previous resource safely
        current?.release()

        // set new resource
        current = engineResource
        current?.acquire()

        val bitmap = engineResource.getBitmap()
        if (bitmap.isRecycled) {
            android.util.Log.w("ImageViewTarget", "⚠️ Bitmap already recycled")
            return
        }

        try {
            imageView.setImageBitmap(bitmap)
            imageView.setOnClickListener(null)
        } catch (e: Exception) {
            android.util.Log.e("ImageViewTarget", "❌ Failed to set bitmap", e)
        }


        imageView.doOnAttach {
            it.doOnDetach {
                current?.release()
                current = null
            }
        }
    }


    override fun onLoadFailed(onRetry: (() -> Unit)?) {
        hideLoading()
        retryCallback = onRetry
        imageView.setImageDrawable(errorDrawable)

        if (onRetry != null && errorDrawable != null) {
            imageView.setOnClickListener {
                // Show loading immediately when user clicks retry
                imageView.setImageDrawable(null)
                imageView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                showLoading()
                onRetry.invoke()
            }
        } else {
            imageView.setOnClickListener(null)
        }
    }

    private fun showLoading() {
        val parent = imageView.parent as? FrameLayout ?: return

        if (progressBar == null) {
            progressBar = ProgressBar(imageView.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                isIndeterminate = true
                // Set black color for the progress bar
                indeterminateDrawable?.setColorFilter(
                    android.graphics.Color.BLACK,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
        }

        if (progressBar?.parent == null) {
            parent.addView(progressBar)
        }
        progressBar?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadingRunnable?.let { loadingHandler.removeCallbacks(it) }
        loadingRunnable = null
        progressBar?.visibility = View.GONE
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
