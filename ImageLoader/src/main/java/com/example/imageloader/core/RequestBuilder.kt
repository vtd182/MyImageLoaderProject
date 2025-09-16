package com.example.imageloader.core

import com.example.imageloader.target.ImageViewTarget
import com.example.imageloader.target.Target
import android.widget.ImageView

class RequestBuilder(private val engine: Engine) {
    private var url: String? = null
    private var resizeWidth: Int? = null
    private var resizeHeight: Int? = null

    fun load(url: String): RequestBuilder {
        this.url = url
        return this
    }

    fun resize(width: Int, height: Int): RequestBuilder {
        this.resizeWidth = width
        this.resizeHeight = height
        return this
    }

    fun into(imageView: ImageView) {
        val req = Request(
            url = url ?: throw IllegalArgumentException("URL required"),
            resizeWidth = resizeWidth,
            resizeHeight = resizeHeight
        )
        engine.load(req, ImageViewTarget(imageView))
    }

    fun into(target: Target) {
        val req = Request(
            url = url ?: throw IllegalArgumentException("URL required"),
            resizeWidth = resizeWidth,
            resizeHeight = resizeHeight
        )
        engine.load(req, target)
    }
}
