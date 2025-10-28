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

class ShimmerDrawable(baseColor: Int? = null, private val cornerRadius: Float = 0f) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private var animator: ValueAnimator? = null
    private var translateX = 0f
    private val path = Path()
    private val rectF = RectF()
    
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
    
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    
    fun start() {
        animator?.start()
    }
    
    fun stop() {
        animator?.cancel()
    }
}
