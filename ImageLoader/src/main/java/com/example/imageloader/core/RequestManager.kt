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

/**
 * RequestManager - Quản lý lifecycle và scheduling của image load requests.
 *
 * ## Core Features:
 * 1. **Request Tracking**: Track requests đang chạy cho mỗi ImageView
 * 2. **Pause/Resume**: Pause requests khi scroll nhanh, resume khi dừng
 * 3. **FPS-based Throttling**: Điều chỉnh tốc độ resume dựa trên FPS
 * 4. **Priority Scheduling**: Resume visible items trước, off-screen sau
 *
 * ## Use Cases:
 * - **RecyclerView scroll**: Pause loading khi scroll nhanh để giữ smooth UI
 * - **View reuse**: Cancel request cũ khi ImageView được reuse
 * - **Memory management**: Clear requests khi không cần thiết
 *
 * ## Pause/Resume Strategy:
 * ```
 * User scroll nhanh → pauseAll()
 * → Requests mới → pending queue
 * User dừng scroll → resumeVisibleOnly(visibleViews)
 * → Visible items load trước
 * → Off-screen items load sau với debounce
 * → FPS monitoring để adaptive throttling
 * ```
 *
 * ## Thread-safety:
 * - ConcurrentHashMap cho running/pending maps
 * - @Synchronized methods cho critical sections
 * - CoroutineScope with SupervisorJob
 */
object RequestManager {
    /** Map tracking requests đang execute cho mỗi ImageView */
    private val running = ConcurrentHashMap<ImageView, Job>()
    
    /** Map chứa pending requests (callback để execute khi resume) */
    private val pending = ConcurrentHashMap<ImageView, () -> Unit>()
    
    /** Flag đánh dấu có đang paused hay không */
    private var isPaused = false

    /** Job cho resume operation (để cancel nếu có resume mới) */
    private var resumeJob: Job? = null
    
    /** Debounce delay trước khi bắt đầu resume (ms) */
    private const val DEBOUNCE_DELAY = 150L
    
    /** Base interval giữa các resume requests (ms) */
    private const val BASE_RESUME_INTERVAL = 80L
    
    /** CoroutineScope cho resume operations */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // FPS monitoring variables
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

    /**
     * Track một request cho ImageView.
     *
     * ## Responsibilities:
     * - Cancel request cũ nếu ImageView được reuse
     * - Track Job để có thể cancel sau
     * - Enqueue pending callback nếu đang paused
     *
     * @param imageView ImageView đích
     * @param job Coroutine Job của request (null nếu hit cache)
     * @param onResume Callback để re-execute request khi resume
     */
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

    /**
     * Clear request của một ImageView.
     * Dùng khi ImageView bị recycle hoặc không còn cần load ảnh.
     *
     * @param imageView ImageView cần clear
     */
    @Synchronized
    fun clear(imageView: ImageView) {
        running[imageView]?.cancel()
        running.remove(imageView)
        pending.remove(imageView)
        imageView.setImageDrawable(null)
    }

    /**
     * Pause tất cả requests mới.
     * Requests đang chạy không bị cancel, nhưng requests mới sẽ pending.
     *
     * Use case: User scroll nhanh trong RecyclerView
     */
    @Synchronized
    fun pauseAll() {
        isPaused = true
        resumeJob?.cancel()
    }

    /**
     * Resume requests với priority cho visible items.
     *
     * ## Flow:
     * 1. Debounce 150ms để chờ scroll ổn định
     * 2. Start FPS monitoring
     * 3. Resume visible items trước
     * 4. Resume off-screen items sau với throttling
     * 5. Adaptive delay dựa trên FPS:
     *    - FPS < 45: delay x3 (240ms)
     *    - FPS < 50: delay x2 (160ms)
     *    - FPS >= 50: delay base (80ms)
     *
     * @param visibleViews Danh sách ImageViews đang visible
     */
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
