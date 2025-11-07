package com.example.imageloader.core

import android.content.res.Resources
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.graphics.toColorInt
import com.example.imageloader.core.enums.RequestPriority
import com.example.imageloader.target.ImageViewTarget
import com.example.imageloader.transformation.CenterCropRoundedCorners
import com.example.imageloader.transformation.Transformation

/**
 * RequestBuilder - Builder pattern để config và execute image load request.
 *
 * Cung cấp fluent API để:
 * - Config URL, resize, transformations
 * - Set placeholder, error drawable
 * - Control caching behavior
 * - Set priority cho request
 * - Execute request vào ImageView
 *
 * ## Usage Example:
 * ```kotlin
 * ImageLoader.with(context)
 *     .load("https://example.com/image.jpg")
 *     .resize(500, 500)
 *     .transform(CenterCropTransformation(), RoundedCornersTransformation(16f))
 *     .placeholder("#E0E0E0")
 *     .error(R.drawable.error_placeholder)
 *     .priority(RequestPriority.HIGH)
 *     .enableShimmer()
 *     .into(imageView)
 * ```
 *
 * ## Thread-safety:
 * RequestBuilder là mutable và không thread-safe.
 * Mỗi request nên có builder riêng.
 *
 * @param engine Engine instance để execute request
 */
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
    private var priority: RequestPriority = RequestPriority.NORMAL
    private val transformations = mutableListOf<Transformation>()

    /**
     * Thêm transformations để áp dụng lên bitmap.
     * Transformations sẽ được apply tuần tự theo thứ tự add.
     *
     * @param transformations Vararg của Transformation objects
     * @return Builder để chain calls
     */
    fun transform(vararg transformations: Transformation): RequestBuilder {
        this.transformations.addAll(transformations)
        return this
    }

    /**
     * Set URL của ảnh cần load.
     *
     * @param url URL của ảnh (http/https)
     * @return Builder để chain calls
     */
    fun load(url: String): RequestBuilder {
        this.url = url; return this
    }

    /**
     * Set kích thước để decode ảnh (downsampling).
     * Giúp tiết kiệm RAM khi load ảnh lớn.
     *
     * @param width Target width
     * @param height Target height
     * @return Builder để chain calls
     */
    fun resize(width: Int, height: Int): RequestBuilder {
        resizeWidth = width; resizeHeight = height; return this
    }

    /**
     * Skip Memory Cache - ảnh sẽ không được cache trong RAM.
     * Use case: Ảnh dynamic, thay đổi thường xuyên.
     *
     * @return Builder để chain calls
     */
    fun skipMemoryCache(): RequestBuilder {
        useMemoryCache = false; return this
    }

    /**
     * Skip Disk Cache - ảnh sẽ không được cache trên disk.
     * Use case: Ảnh nhạy cảm, không muốn persist.
     *
     * @return Builder để chain calls
     */
    fun skipDiskCache(): RequestBuilder {
        useDiskCache = false; return this
    }

    /**
     * Execute request và load ảnh vào ImageView.
     *
     * ## Flow:
     * 1. Build Request object từ builder config
     * 2. Create ImageViewTarget
     * 3. Apply placeholder
     * 4. Check memory cache (sync)
     * 5. Nếu miss → load từ disk/network (async)
     * 6. Track request với RequestManager
     *
     * ## Pause/Resume:
     * - Nếu RequestManager đang paused (scroll fast) → request pending
     * - Khi resume → request được execute
     *
     * @param imageView Target ImageView
     * @throws IllegalArgumentException nếu URL không được set
     */
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

        // Check memory cache first (sync)
        if (engine.checkMemoryCache(request, target)) {
            RequestManager.track(imageView, null)
            return
        }

        // Miss cache -> check if paused
        val job = if (!RequestManager.isPaused()) {
            engine.load(request, target, priority)
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

    fun overrideSize(width: Int, height: Int): RequestBuilder {
        outWidth = width; outHeight = height; return this
    }

    fun enableShimmer(enable: Boolean = true): RequestBuilder {
        enableShimmer = enable; return this
    }

    fun priority(priority: RequestPriority): RequestBuilder {
        this.priority = priority; return this
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
