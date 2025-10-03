package com.example.myimageloaderproject.core.customView

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Choreographer
import androidx.core.graphics.toColorInt
import com.example.myimageloaderproject.R

class FPSOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : androidx.appcompat.widget.AppCompatTextView(context, attrs), Choreographer.FrameCallback {
    private var lastTime = System.nanoTime()
    private var frameCount = 0

    init {
        setBackgroundColor("#88000000".toColorInt())
        setTextColor(Color.WHITE)
        textSize = 12f
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameCount++
        val now = System.nanoTime()
        val delta = (now - lastTime) / 1_000_000_000.0
        if (delta >= 1.0) {
            val fps = frameCount / delta
            text = context.getString(R.string.fps_display, fps.toInt())
            frameCount = 0
            lastTime = now
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Choreographer.getInstance().removeFrameCallback(this)
    }
}