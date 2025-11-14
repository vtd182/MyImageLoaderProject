package com.example.imageloader.logger

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ImageLoaderLogger - Centralized logging system cho ImageLoader library.
 *
 * ## Mục đích:
 * - Log tất cả image load events (network, cache hits, errors)
 * - Log general messages từ các components
 * - Provide in-memory log buffer cho debugging UI
 * - Real-time listener notifications
 * - Performance metrics và statistics
 *
 * ## Architecture:
 * ```
 * Components → ImageLoaderLogger → [Memory Buffer] → Logcat
 *                                  ↓
 *                              Listeners (UI)
 * ```
 *
 * ## Features:
 *
 * ### 1. Dual Logging:
 * - **Logcat**: Standard Android logging (filterable by tag)
 * - **Memory Buffer**: Last 500 logs for in-app viewer
 *
 * ### 2. Log Categories:
 * - CACHE: Cache operations (hit/miss/put)
 * - NETWORK: HTTP fetching
 * - DECODE: Bitmap decoding
 * - TRANSFORM: Transformations
 * - GENERAL: Other messages
 *
 * ### 3. Log Levels:
 * - VERBOSE: Detailed flow information
 * - DEBUG: Development info
 * - INFO: Important events
 * - WARNING: Potential issues
 * - ERROR: Failures
 *
 * ### 4. Real-time Listeners:
 * UI components có thể đăng ký để nhận logs real-time:
 * ```kotlin
 * ImageLoaderLogger.addListener { logEntry ->
 *     when (logEntry) {
 *         is ImageLoadLog -> updateImageStats(logEntry)
 *         is MessageLog -> appendToLogView(logEntry)
 *     }
 * }
 * ```
 *
 * ## Usage Examples:
 *
 * ### General logging:
 * ```kotlin
 * ImageLoaderLogger.d("Engine", "Starting image load: $url", LogCategory.NETWORK)
 * ImageLoaderLogger.e("Decoder", "Decode failed", throwable, LogCategory.DECODE)
 * ```
 *
 * ### Image load logging:
 * ```kotlin
 * val log = ImageLoadLog(
 *     url = url,
 *     source = LogSource.NETWORK,
 *     totalTimeMs = 450,
 *     fetchTimeMs = 300,
 *     decodeTimeMs = 100,
 *     transformTimeMs = 50
 * )
 * ImageLoaderLogger.log(log)
 * ```
 *
 * ## Thread-safety:
 * - ConcurrentLinkedQueue: Thread-safe buffer
 * - synchronized(listeners): Safe listener operations
 * - Multiple threads có thể log đồng thời
 *
 * @see com.example.imageloader.logger.LogEntry
 * @see com.example.imageloader.logger.ImageLoadLog
 * @see com.example.imageloader.logger.LogStats
 */
object ImageLoaderLogger {
    private const val TAG = "ImageLoaderLogger"
    private const val MAX_LOGS = 2000

    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val listeners = mutableListOf<(LogEntry) -> Unit>()

    var saveToActivity = true
    var jsonPhotoCount = 0
    var jsonCurrentPage = 0

    private var bitmapPoolHits = 0
    private var bitmapPoolMisses = 0

    /**
     * Log một ImageLoadLog entry.
     */
    fun log(log: ImageLoadLog) {
        logEntry(log)
    }

    /**
     * Log VERBOSE message.
     */
    fun v(tag: String, message: String, category: LogCategory = LogCategory.GENERAL) {
        logEntry(
            MessageLog(
                level = LogLevel.VERBOSE,
                category = category,
                tag = tag,
                message = message
            )
        )
    }

    /**
     * Log DEBUG message.
     */
    fun d(tag: String, message: String, category: LogCategory = LogCategory.GENERAL) {
        logEntry(
            MessageLog(
                level = LogLevel.DEBUG,
                category = category,
                tag = tag,
                message = message
            )
        )
    }

    /**
     * Log INFO message.
     */
    fun i(tag: String, message: String, category: LogCategory = LogCategory.GENERAL) {
        logEntry(
            MessageLog(
                level = LogLevel.INFO,
                category = category,
                tag = tag,
                message = message
            )
        )
    }

    /**
     * Log WARNING message với optional throwable.
     */
    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        category: LogCategory = LogCategory.GENERAL
    ) {
        logEntry(
            MessageLog(
                level = LogLevel.WARNING,
                category = category,
                tag = tag,
                message = message,
                throwable = throwable
            )
        )
    }

    /**
     * Log ERROR message với optional throwable.
     */
    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
        category: LogCategory = LogCategory.GENERAL
    ) {
        logEntry(
            MessageLog(
                level = LogLevel.ERROR,
                category = category,
                tag = tag,
                message = message,
                throwable = throwable
            )
        )
    }

    private fun logEntry(entry: LogEntry) {
        if (saveToActivity) {
            logs.add(entry)

            while (logs.size > MAX_LOGS) {
                logs.poll()
            }

            synchronized(listeners) {
                listeners.forEach { it(entry) }
            }
        }

        when (entry) {
            is MessageLog -> {
                when (entry.level) {
                    LogLevel.VERBOSE -> Log.v(TAG, entry.toLogcatString(), entry.throwable)
                    LogLevel.DEBUG -> Log.d(TAG, entry.toLogcatString(), entry.throwable)
                    LogLevel.INFO -> Log.i(TAG, entry.toLogcatString(), entry.throwable)
                    LogLevel.WARNING -> Log.w(TAG, entry.toLogcatString(), entry.throwable)
                    LogLevel.ERROR -> Log.e(TAG, entry.toLogcatString(), entry.throwable)
                }
            }

            is ImageLoadLog -> Log.d(TAG, entry.toLogcatString())
        }
    }

    /**
     * Get tất cả logs trong memory buffer.
     *
     * @return Snapshot của logs (max 500 entries)
     */
    fun getAllLogs(): List<LogEntry> {
        return logs.toList()
    }

    /**
     * Clear tất cả logs trong buffer.
     */
    fun clear() {
        logs.clear()
        Log.d(TAG, "Logs cleared")
    }

    /**
     * Đăng ký listener để nhận log updates real-time.
     * Listener sẽ được gọi mỗi khi có log mới.
     *
     * @param listener Callback nhận LogEntry
     */
    fun addListener(listener: (LogEntry) -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    /**
     * Hủy đăng ký listener.
     *
     * @param listener Listener cần remove
     */
    fun removeListener(listener: (LogEntry) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    /**
     * Log bitmap pool hit.
     */
    @Synchronized
    fun logBitmapPoolHit() {
        bitmapPoolHits++
    }

    /**
     * Log bitmap pool miss.
     */
    @Synchronized
    fun logBitmapPoolMiss() {
        bitmapPoolMisses++
    }

    /**
     * Get bitmap pool hit rate.
     *
     * @return Hit rate (0.0 to 1.0)
     */
    @Synchronized
    fun getBitmapPoolHitRate(): Double {
        val total = bitmapPoolHits + bitmapPoolMisses
        return if (total > 0) bitmapPoolHits / total.toDouble() else 0.0
    }

    /**
     * Reset bitmap pool statistics.
     */
    @Synchronized
    fun resetBitmapPoolStats() {
        bitmapPoolHits = 0
        bitmapPoolMisses = 0
    }

    /**
     * Get current memory snapshot.
     *
     * @return MemorySnapshot with current memory usage
     */
    fun getMemorySnapshot(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory

        return MemorySnapshot(
            usedMemoryMB = usedMemory / 1024.0 / 1024.0,
            totalMemoryMB = totalMemory / 1024.0 / 1024.0,
            maxMemoryMB = maxMemory / 1024.0 / 1024.0,
            freeMemoryMB = freeMemory / 1024.0 / 1024.0
        )
    }

    /**
     * Calculate statistics từ logs hiện tại.
     *
     * ## Metrics:
     * - Cache hit rates (active/memory/disk)
     * - Average load times per source
     * - Average fetch/decode/transform times
     * - Error counts
     * - Bitmap pool statistics
     *
     * @return LogStats chứa metrics
     */
    fun getLogStats(): LogStats {
        val imageLoadLogs = logs.filterIsInstance<ImageLoadLog>()
        val messageLogs = logs.filterIsInstance<MessageLog>()

        val activeCache =
            imageLoadLogs.filter { it.source == LogSource.ACTIVE_CACHE && it.error == null }
        val memoryCache =
            imageLoadLogs.filter { it.source == LogSource.MEMORY_CACHE && it.error == null }
        val diskCache =
            imageLoadLogs.filter { it.source == LogSource.DISK_CACHE && it.error == null }
        val network = imageLoadLogs.filter { it.source == LogSource.NETWORK && it.error == null }

        return LogStats(
            totalLogs = logs.size,
            totalImageRequests = imageLoadLogs.size,
            imageErrors = imageLoadLogs.count { it.error != null },
            messageErrors = messageLogs.count { it.level == LogLevel.ERROR },
            jsonPhotoCount = jsonPhotoCount,
            jsonCurrentPage = jsonCurrentPage,

            activeCacheCount = activeCache.size,
            activeCacheAvgTime = activeCache.map { it.totalTimeMs }.average().takeIf { !it.isNaN() }
                ?: 0.0,

            memoryCacheCount = memoryCache.size,
            memoryCacheAvgTime = memoryCache.map { it.totalTimeMs }.average().takeIf { !it.isNaN() }
                ?: 0.0,

            diskCacheCount = diskCache.size,
            diskCacheAvgTime = diskCache.map { it.totalTimeMs }.average().takeIf { !it.isNaN() }
                ?: 0.0,
            diskCacheAvgDecode = diskCache.mapNotNull { it.decodeTimeMs }.average()
                .takeIf { !it.isNaN() } ?: 0.0,
            diskCacheAvgTransform = diskCache.mapNotNull { it.transformTimeMs }.average()
                .takeIf { !it.isNaN() } ?: 0.0,

            networkCount = network.size,
            networkAvgTime = network.map { it.totalTimeMs }.average().takeIf { !it.isNaN() } ?: 0.0,
            networkAvgFetch = network.mapNotNull { it.fetchTimeMs }.average().takeIf { !it.isNaN() }
                ?: 0.0,
            networkAvgDecode = network.mapNotNull { it.decodeTimeMs }.average()
                .takeIf { !it.isNaN() } ?: 0.0,
            networkAvgTransform = network.mapNotNull { it.transformTimeMs }.average()
                .takeIf { !it.isNaN() } ?: 0.0,

            bitmapPoolHits = bitmapPoolHits,
            bitmapPoolMisses = bitmapPoolMisses,
            bitmapPoolHitRate = getBitmapPoolHitRate()
        )
    }
}

/**
 * LogStats - Statistics data class tổng hợp metrics từ logs.
 *
 * Chứa performance metrics và cache hit rates để phân tích hiệu suất.
 */
data class LogStats(
    val totalLogs: Int,
    val totalImageRequests: Int,
    val imageErrors: Int,
    val messageErrors: Int,
    val jsonPhotoCount: Int,
    val jsonCurrentPage: Int,

    val activeCacheCount: Int,
    val activeCacheAvgTime: Double,

    val memoryCacheCount: Int,
    val memoryCacheAvgTime: Double,

    val diskCacheCount: Int,
    val diskCacheAvgTime: Double,
    val diskCacheAvgDecode: Double,
    val diskCacheAvgTransform: Double,

    val networkCount: Int,
    val networkAvgTime: Double,
    val networkAvgFetch: Double,
    val networkAvgDecode: Double,
    val networkAvgTransform: Double,

    val bitmapPoolHits: Int = 0,
    val bitmapPoolMisses: Int = 0,
    val bitmapPoolHitRate: Double = 0.0
)

data class MemorySnapshot(
    val usedMemoryMB: Double,
    val totalMemoryMB: Double,
    val maxMemoryMB: Double,
    val freeMemoryMB: Double,
    val timestamp: Long = System.currentTimeMillis()
)
