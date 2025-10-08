package com.example.imageloader.core

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
    private const val RESUME_INTERVAL = 60L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
    fun resumeAll() {
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(DEBOUNCE_DELAY)
            isPaused = false

            val copy = pending.toMap()
            pending.clear()
            copy.values.forEach { it.invoke() }
        }
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

            val toResume = visibleViews.filter { pending.containsKey(it) }

            for (imageView in toResume) {
                pending.remove(imageView)?.invoke()
                delay(RESUME_INTERVAL)
            }
        }
    }

    fun isPaused(): Boolean = isPaused
}
