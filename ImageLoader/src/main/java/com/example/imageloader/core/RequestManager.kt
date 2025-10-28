package com.example.imageloader.core

import android.view.Choreographer
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object RequestManager {
    private val running = ConcurrentHashMap<ImageView, Job>()
    private val pending = ConcurrentHashMap<ImageView, () -> Unit>()
    private var isPaused = false

    private var resumeJob: Job? = null
    private const val DEBOUNCE_DELAY = 150L
    private const val BASE_RESUME_INTERVAL = 80L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var lastFrameTimeNanos = 0L
    private var frameCount = 0
    private var lastFpsTime = 0L
    private var currentFps = 60f
    private var fpsRunning = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!fpsRunning) return
            if (lastFrameTimeNanos > 0) {
                frameCount++
                val diff = frameTimeNanos - lastFpsTime
                if (diff >= 1_000_000_000L) { // mỗi giây cập nhật FPS
                    currentFps = frameCount * (1_000_000_000f / diff)
                    frameCount = 0
                    lastFpsTime = frameTimeNanos
                }
            } else {
                lastFpsTime = frameTimeNanos
            }
            lastFrameTimeNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun startFpsMonitor() {
        if (fpsRunning) return
        fpsRunning = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun getAdaptiveDelay(): Long {
        return when {
            currentFps < 45 -> BASE_RESUME_INTERVAL * 3
            currentFps < 50 -> BASE_RESUME_INTERVAL * 2
            else -> BASE_RESUME_INTERVAL
        }
    }

    @Synchronized
    fun track(imageView: ImageView, job: Job?, onResume: (() -> Unit)? = null) {
        if (isPaused) {
            if (onResume != null) {
                pending[imageView] = onResume
            }
            return
        }

        running[imageView]?.cancel()
        if (job != null) {
            running[imageView] = job
        } else {
            running.remove(imageView)
        }
    }

    @Synchronized
    fun clear(imageView: ImageView) {
        running[imageView]?.cancel()
        running.remove(imageView)
        pending.remove(imageView)
        imageView.setImageDrawable(null)
    }

    @Synchronized
    fun pauseAll() {
        isPaused = true
        resumeJob?.cancel()
    }

    @Synchronized
    fun resumeVisibleOnly(visibleViews: List<ImageView>) {
        if (pending.isEmpty()) {
            isPaused = false
            return
        }

        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(DEBOUNCE_DELAY)
            isPaused = false
            startFpsMonitor()

            val visibleToResume = visibleViews.filter { pending.containsKey(it) }
            val others = pending.keys.filterNot { it in visibleToResume }

            val ordered = visibleToResume + others

            for (imageView in ordered) {
                pending.remove(imageView)?.invoke()
                delay(getAdaptiveDelay())
            }
        }
    }

    fun isPaused(): Boolean = isPaused
}
