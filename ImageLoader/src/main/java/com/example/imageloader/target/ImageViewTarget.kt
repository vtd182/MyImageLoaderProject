package com.example.imageloader.target


import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
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
import com.example.imageloader.drawable.ShimmerDrawable
import com.example.imageloader.logger.ImageLoaderLogger
import com.example.imageloader.logger.LogCategory


class ImageViewTarget(
    private val imageView: ImageView,
    private val errorDrawable: Drawable? = null,
    private val enableShimmer: Boolean = false
) : Target {
    private var current: EngineResource? = null
    private var retryCallback: (() -> Unit)? = null
    private val loadingHandler = Handler(Looper.getMainLooper())
    private var loadingRunnable: Runnable? = null
    private var progressBar: ProgressBar? = null
    private var shimmerDrawable: ShimmerDrawable? = null

    companion object {
        private const val TAG = "ImageViewTarget"
        private const val LOADING_DELAY_MS = 500L
    }


    override fun onLoadStarted() {
        loadingRunnable?.let { loadingHandler.removeCallbacks(it) }

        if (enableShimmer) {
            showShimmer()
        } else {
            // Clear placeholder background when starting to load
            imageView.setBackgroundColor(android.graphics.Color.TRANSPARENT)

//            loadingRunnable = Runnable {
//                showLoading()
//            }
//            loadingHandler.postDelayed(loadingRunnable!!, LOADING_DELAY_MS)
        }
    }

    override fun onResourceReady(engineResource: EngineResource) {
        //hideLoading()
        hideShimmer()

        // clear old bitmap reference in ImageView
        imageView.setImageDrawable(null)

        // release previous resource safely
        current?.release()

        // set new resource
        current = engineResource
        current?.acquire()

        val bitmap = engineResource.getBitmap()
        if (bitmap.isRecycled) {
            ImageLoaderLogger.w(TAG, "Bitmap already recycled", category = LogCategory.ENGINE)
            return
        }

        try {
            imageView.setImageBitmap(bitmap)
            imageView.setOnClickListener(null)
        } catch (e: Exception) {
            ImageLoaderLogger.e(TAG, "Failed to set bitmap", e, LogCategory.ENGINE)
        }


        imageView.doOnAttach {
            it.doOnDetach {
                current?.release()
                current = null
            }
        }
    }


    override fun onLoadFailed(onRetry: (() -> Unit)?) {
        //hideLoading()
        retryCallback = onRetry
        imageView.setImageDrawable(errorDrawable)
        imageView.setBackgroundColor(0xFFFFEB3B.toInt()) // Yellow background

        if (onRetry != null && errorDrawable != null) {
            imageView.setOnClickListener {
                // Show loading immediately when user clicks retry
                imageView.setImageDrawable(null)
                imageView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                //showLoading()
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

    private fun showShimmer() {
        val currentDrawable = imageView.drawable
        
        if (currentDrawable == null || currentDrawable is ShimmerDrawable) {
            return
        }

        var cornerRadius = 0f
        var placeholderColor: Int? = null
        
        if (currentDrawable is android.graphics.drawable.GradientDrawable) {
            try {
                val radii = currentDrawable.cornerRadii
                cornerRadius = radii?.get(0) ?: currentDrawable.cornerRadius
                
                val colorState = currentDrawable.color
                placeholderColor = colorState?.defaultColor
            } catch (e: Exception) {
                // Ignore
            }
        }

        shimmerDrawable = ShimmerDrawable(placeholderColor, cornerRadius)
        val layers = arrayOf(currentDrawable, shimmerDrawable!!)
        val layerDrawable = LayerDrawable(layers)
        imageView.setImageDrawable(layerDrawable)
        shimmerDrawable?.start()
    }

    private fun hideShimmer() {
        shimmerDrawable?.stop()
        shimmerDrawable = null
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
