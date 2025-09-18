package com.example.imageloader.core

import android.widget.ImageView
import com.example.imageloader.cache.ActiveResources
import com.example.imageloader.target.ImageViewTargetWithActive
import com.example.imageloader.target.Target

class RequestBuilder(
    private val engine: Engine,
    private val activeResources: ActiveResources
) {
    private var url: String? = null
    private var resizeWidth: Int? = null
    private var resizeHeight: Int? = null
    private var useMemoryCache: Boolean = true
    private var useDiskCache: Boolean = true

    fun load(url: String): RequestBuilder { this.url = url; return this }
    fun resize(width: Int, height: Int): RequestBuilder { resizeWidth = width; resizeHeight = height; return this }
    fun skipMemoryCache(): RequestBuilder { useMemoryCache = false; return this }
    fun skipDiskCache(): RequestBuilder { useDiskCache = false; return this }

    fun into(imageView: ImageView) {
        val request = Request(url ?: throw IllegalArgumentException("URL required"), resizeWidth, resizeHeight, useMemoryCache, useDiskCache)
        val key = buildKey(request)
        val target = ImageViewTargetWithActive(imageView, activeResources, key)
        engine.load(request, target)
    }

    fun into(target: Target) {
        val request = Request(url ?: throw IllegalArgumentException("URL required"), resizeWidth, resizeHeight, useMemoryCache, useDiskCache)
        engine.load(request, target)
    }

    private fun buildKey(req: Request): String {
        return buildString {
            append(req.url)
            if (req.resizeWidth != null && req.resizeHeight != null) append("#${req.resizeWidth}x${req.resizeHeight}")
        }
    }
}
