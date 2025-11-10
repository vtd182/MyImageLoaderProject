package com.example.imageloader.drawable

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * ShimmerDrawable - Custom Drawable hiển thị shimmer effect cho loading state.
 *
 * ## Mục đích:
 * Tạo placeholder animated đẹp mắt khi đang load ảnh:
 * - Shimmer sweep animation (hiệu ứng quét sáng)
 * - Màu adaptive theo dominant color của ảnh
 * - Support rounded corners
 * - Better UX hơn static placeholder hoặc spinner
 *
 * ## Shimmer Animation:
 * ```
 * [Base] → [Light] → [Lighter] → [Light] → [Base]
 *   ↓         ↓          ↓          ↓         ↓
 * Linear gradient quét từ trái sang phải (1.5s loop)
 * ```
 *
 * ## Color Strategy:
 *
 * ### With base color (từ dominant color extraction):
 * ```
 * Base color: #E0E0E0
 * → Lighten 20%: #F0F0F0
 * → Lighten 40%: #F8F8F8
 * → Gradient: [Base, Light, Lighter, Light, Base]
 * → Smooth shimmer effect
 * ```
 *
 * ### Without base color (default):
 * ```
 * Gray gradient: [#E0E0E0, #F5F5F5, #E0E0E0]
 * → Simple 3-color shimmer
 * ```
 *
 * ## Technical Details:
 * - **LinearGradient**: Horizontal gradient với 3-5 colors
 * - **Matrix transform**: Translate gradient để tạo animation
 * - **ValueAnimator**: 0.0 → 1.0 over 1.5s, infinite repeat
 * - **Canvas clipping**: Support rounded corners với Path
 *
 * ## Performance:
 * - Lightweight: Chỉ redraw khi animate
 * - Hardware accelerated: Canvas operations optimized
 * - 60 FPS smooth animation
 *
 * ## Usage:
 * ```kotlin
 * val shimmer = ShimmerDrawable(
 *     baseColor = dominantColor, // Optional
 *     cornerRadius = 16f          // Match image corners
 * )
 * imageView.setImageDrawable(shimmer)
 * shimmer.start() // Start animation
 *
 * // Later, when image loaded:
 * shimmer.stop()
 * imageView.setImageBitmap(bitmap)
 * ```
 *
 * @param baseColor Optional base color để generate gradient (từ dominant color)
 * @param cornerRadius Border radius cho rounded shimmer (match với ảnh)
 *
 * @see com.example.imageloader.target.ImageViewTarget
 * @see com.example.imageloader.decode.BitmapDecoder.extractDominantColor
 */
class ShimmerDrawable(baseColor: Int? = null, private val cornerRadius: Float = 0f) : Drawable() {
    /** Paint cho shimmer gradient */
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Matrix để transform gradient (tạo animation) */
    private val matrix = Matrix()

    /** ValueAnimator điều khiển shimmer sweep */
    private var animator: ValueAnimator? = null

    /** Current translation X (0.0 - 1.0) */
    private var translateX = 0f

    /** Path cho rounded corners clipping */
    private val path = Path()

    /** RectF helper cho rounded rect */
    private val rectF = RectF()

    /**
     * Shimmer gradient colors.
     * - Nếu có baseColor: 5 colors (smooth gradient)
     * - Nếu không: 3 colors (simple gray gradient)
     */
    private val shimmerColors: IntArray = if (baseColor != null) {
        val lighter = lightenColor(baseColor, 0.2f)
        val evenLighter = lightenColor(baseColor, 0.4f)
        intArrayOf(baseColor, lighter, evenLighter, lighter, baseColor)
    } else {
        intArrayOf(
            0xFFE0E0E0.toInt(),
            0xFFF5F5F5.toInt(),
            0xFFE0E0E0.toInt()
        )
    }

    /**
     * Lighten một color theo factor (0.0 - 1.0).
     *
     * ## Algorithm:
     * ```
     * newColor = baseColor + (255 - baseColor) * factor
     * ```
     *
     * @param color Base color (ARGB)
     * @param factor Lighten factor (0.0 = no change, 1.0 = white)
     * @return Lightened color
     */
    private fun lightenColor(color: Int, factor: Float): Int {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val alpha = Color.alpha(color)

        val newRed = (red + (255 - red) * factor).toInt().coerceIn(0, 255)
        val newGreen = (green + (255 - green) * factor).toInt().coerceIn(0, 255)
        val newBlue = (blue + (255 - blue) * factor).toInt().coerceIn(0, 255)

        return Color.argb(alpha, newRed, newGreen, newBlue)
    }

    init {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                translateX = it.animatedValue as Float
                invalidateSelf()
            }
        }
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        val width = bounds.width().toFloat()
        val positions = if (shimmerColors.size == 5) {
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        } else {
            floatArrayOf(0f, 0.5f, 1f)
        }
        val shader = LinearGradient(
            0f, 0f, width, 0f,
            shimmerColors,
            positions,
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        val width = bounds.width().toFloat()

        matrix.reset()
        matrix.setTranslate(-width + translateX * width * 2, 0f)
        paint.shader?.setLocalMatrix(matrix)

        if (cornerRadius > 0f) {
            rectF.set(bounds)
            path.reset()
            path.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
            canvas.drawPath(path, paint)
        } else {
            canvas.drawRect(bounds, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    /**
     * Start shimmer animation.
     * Bắt đầu ValueAnimator để sweep gradient.
     */
    fun start() {
        animator?.start()
    }

    /**
     * Stop shimmer animation.
     * Dừng và cancel animator để save CPU/battery.
     */
    fun stop() {
        animator?.cancel()
    }
}
