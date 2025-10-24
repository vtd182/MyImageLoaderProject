package com.example.imageloader.core

import android.content.res.Resources
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.graphics.toColorInt
import com.example.imageloader.target.ImageViewTarget
import com.example.imageloader.target.Target
import com.example.imageloader.transformation.CenterCropRoundedCorners
import com.example.imageloader.transformation.Transformation

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
    private var errorRes: Int? = null
    private var outHeight: Int? = null
    private var outWidth: Int? = null
    private var enableShimmer: Boolean = false
    private val transformations = mutableListOf<Transformation>()

    fun transform(vararg transformations: Transformation): RequestBuilder {
        this.transformations.addAll(transformations)
        return this
    }

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
            useDiskCache,
            transformations.toList(),
            outWidth,
            outHeight,
            enableShimmer
        )
        
        val errorDrawable = errorRes?.let { 
            AppCompatResources.getDrawable(imageView.context, it)
        }
        val target = ImageViewTarget(imageView, errorDrawable, enableShimmer)

        // apply overrideSize vào layout
        if (outWidth != null && outHeight != null) {
            val params = imageView.layoutParams
            params.width = outWidth!!
            params.height = outHeight!!
            imageView.layoutParams = params
        }

        applyPlaceholder(imageView)
        val reload = { into(imageView) }

        val job = if (!RequestManager.isPaused()) {
            engine.load(request, target)
        } else null

        RequestManager.track(imageView, job, onResume = reload)
    }

    fun placeholder(hex: String?): RequestBuilder {
        hex?.let {
            placeholderColor = try {
                hex.toColorInt()
            } catch (e: IllegalArgumentException) {
                android.graphics.Color.WHITE
            }
        }
        return this
    }

    fun error(resId: Int): RequestBuilder {
        errorRes = resId
        return this
    }


    fun into(target: Target) {
        val request = Request(
            url ?: throw IllegalArgumentException("URL required"),
            resizeWidth,
            resizeHeight,
            useMemoryCache,
            useDiskCache,
            transformations.toList(),
            outWidth,
            outHeight,
            enableShimmer
        )
        engine.load(request, target)
    }

    fun overrideSize(width: Int, height: Int): RequestBuilder {
        outWidth = width; outHeight = height; return this
    }

    fun enableShimmer(enable: Boolean = true): RequestBuilder {
        enableShimmer = enable; return this
    }

    fun applyPlaceholder(imageView: ImageView) {
        when {
            placeholderRes != null -> {
                val drawable = AppCompatResources.getDrawable(imageView.context, placeholderRes!!)
                imageView.setImageDrawable(drawable?.let { roundDrawableIfNeeded(it) })
            }

            placeholderColor != null -> {
                val radius = transformations.filterIsInstance<CenterCropRoundedCorners>()
                    .firstOrNull()?.radius ?: 0f

                val shape = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(placeholderColor!!)
                }
                imageView.setImageDrawable(shape)
            }

            enableShimmer -> {
                // Set default placeholder color when shimmer is enabled
                val radius = transformations.filterIsInstance<CenterCropRoundedCorners>()
                    .firstOrNull()?.radius ?: 0f

                val shape = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = radius
                    setColor(0xFFE0E0E0.toInt()) // Default gray color
                }
                imageView.setImageDrawable(shape)
            }

            else -> imageView.setImageDrawable(null)
        }
    }

    private fun roundDrawableIfNeeded(drawable: Drawable): Drawable {
        val rounded = transformations
            .filterIsInstance<CenterCropRoundedCorners>()
            .firstOrNull()

        if (rounded != null && drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
            val roundedDrawable = RoundedBitmapDrawableFactory.create(Resources.getSystem(), bitmap)
            roundedDrawable.cornerRadius = rounded.radius
            return roundedDrawable
        }
        return drawable
    }
}
