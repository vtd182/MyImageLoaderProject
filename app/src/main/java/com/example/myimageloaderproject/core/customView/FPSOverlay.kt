package com.example.myimageloaderproject.core.customView

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Choreographer
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.toColorInt

/**
 * FPSOverlay
 *
 * Tính FPS trung bình theo cửa sổ thời gian (2s)
 * Lọc outlier (frame > 0.2s bị loại)
 * Màu sắc thay đổi theo mức FPS trung bình
 */
class FPSOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs), Choreographer.FrameCallback {

    private data class FrameSample(val fps: Double, val timestampSec: Double)

    private var lastFrameTime = 0L
    private var frameSamples = mutableListOf<FrameSample>()
    private var lastUpdateTime = 0L
    private var isRunning = false

    // Config
    private val windowSec = 2.0
    private val updateIntervalSec = 0.5
    private val maxDeltaSec = 0.2

    init {
        setBackgroundColor("#88000000".toColorInt())
        setTextColor(Color.WHITE)
        textSize = 12f
        setPadding(12, 6, 12, 6)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startMonitoring()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopMonitoring()
    }

    private fun startMonitoring() {
        if (!isRunning) {
            isRunning = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun stopMonitoring() {
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isShown) {
            stopMonitoring()
            return
        }

        val frameTimeSec = frameTimeNanos / 1_000_000_000.0
        if (lastFrameTime > 0) {
            val deltaSec = (frameTimeNanos - lastFrameTime) / 1_000_000_000.0
            if (deltaSec in 0.0..maxDeltaSec) {
                val fps = 1.0 / deltaSec
                frameSamples.add(FrameSample(fps, frameTimeSec))
            }
        }
        lastFrameTime = frameTimeNanos

        val windowStart = frameTimeSec - windowSec
        frameSamples = frameSamples.filter { it.timestampSec >= windowStart }.toMutableList()

        val elapsedSinceUpdate = frameTimeSec - (lastUpdateTime / 1_000_000_000.0)
        if (elapsedSinceUpdate >= updateIntervalSec && frameSamples.isNotEmpty()) {
            val avgFps = frameSamples.map { it.fps }.average()
            val minFps = frameSamples.minOf { it.fps }
            val maxFps = frameSamples.maxOf { it.fps }

            val color = when {
                avgFps >= 50 -> Color.GREEN
                avgFps >= 30 -> Color.YELLOW
                else -> Color.RED
            }
            setTextColor(color)

            text = buildString {
                append("FPS Avg: ${"%.1f".format(avgFps)}")
            }

            lastUpdateTime = frameTimeNanos
        }

        if (isRunning) {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startMonitoring() else stopMonitoring()
    }
}
