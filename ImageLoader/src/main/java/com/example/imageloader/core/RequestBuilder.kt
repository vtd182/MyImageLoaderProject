package com.example.imageloader.core

import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import com.example.imageloader.target.ImageViewTarget
import com.example.imageloader.target.Target

class RequestBuilder(
    private val engine: Engine,
) {
    private var url: String? = null
    private var resizeWidth: Int? = null
    private var resizeHeight: Int? = null
    private var useMemoryCache: Boolean = true
    private var useDiskCache: Boolean = true
    private var placeholderRes: Int? = null
    private var placeholderColor: Int? = null

    private var outHeight: Int? = null
    private var outWidth: Int? = null

    fun load(url: String): RequestBuilder {
        this.url = url; return this
    }

    fun resize(width: Int, height: Int): RequestBuilder {
        resizeWidth = width; resizeHeight = height; return this
    }

    fun skipMemoryCache(): RequestBuilder {
        useMemoryCache = false; return this
    }

    fun skipDiskCache(): RequestBuilder {
        useDiskCache = false; return this
    }

    fun into(imageView: ImageView) {
        val request = Request(
            url ?: throw IllegalArgumentException("URL required"),
            resizeWidth,
            resizeHeight,
            useMemoryCache,
            useDiskCache
        )
        val target = ImageViewTarget(imageView)
        
        // apply overrideSize vào layout
        if (outWidth != null && outHeight != null) {
            val params = imageView.layoutParams
            params.width = outWidth!!
            params.height = outHeight!!
            imageView.layoutParams = params
        }

        when {
            placeholderRes != null -> imageView.setImageResource(placeholderRes!!)
            placeholderColor != null -> imageView.setImageDrawable(placeholderColor!!.toDrawable())
            else -> imageView.setImageDrawable(null)
        }
        val job = engine.load(request, target)
        RequestManager.track(imageView, job)
    }

    fun placeholder(hex: String?): RequestBuilder {
        val colorInt = hex?.toColorInt()
        hex?.let {
            placeholderColor = try {
                hex.toColorInt()
            } catch (e: IllegalArgumentException) {
                android.graphics.Color.WHITE
            }
        }
        return this
    }


    fun into(target: Target) {
        val request = Request(
            url ?: throw IllegalArgumentException("URL required"),
            resizeWidth,
            resizeHeight,
            useMemoryCache,
            useDiskCache
        )
        engine.load(request, target)
    }

    private fun buildKey(req: Request): String {
        return buildString {
            append(req.url)
            if (req.resizeWidth != null && req.resizeHeight != null) append("#${req.resizeWidth}x${req.resizeHeight}")
        }
    }

    fun overrideSize(width: Int, height: Int): RequestBuilder {
        outWidth = width; outHeight = height; return this
    }

}
