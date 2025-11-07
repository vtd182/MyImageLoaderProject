package com.example.imageloader.logger

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LogLevel - Mức độ log tương ứng với Android Log levels.
 */
enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARNING, ERROR
}

/**
 * LogCategory - Category để phân loại logs theo component.
 *
 * Giúp filter và analyze logs theo từng module.
 */
enum class LogCategory(val displayName: String) {
    ENGINE("Engine"),
    CACHE("Cache"),
    NETWORK("Network"),
    DECODE("Decode"),
    TRANSFORM("Transform"),
    IMAGE_LOAD("Image Load"),
    GENERAL("General");
}

/**
 * LogEntry - Base sealed class cho tất cả log entries.
 *
 * ## Hierarchy:
 * ```
 * LogEntry (sealed)
 * ├── MessageLog: General text messages
 * └── ImageLoadLog: Image load events với timing details
 * ```
 *
 * ## Properties:
 * - **timestamp**: Thời điểm log được tạo (ms)
 * - **level**: Mức độ nghiêm trọng
 * - **category**: Phân loại theo component
 *
 * @see MessageLog
 * @see ImageLoadLog
 */
sealed class LogEntry(
    open val timestamp: Long = System.currentTimeMillis(),
    open val level: LogLevel,
    open val category: LogCategory
) {
    /**
     * Format log để hiển thị trong UI (với icons, multiline).
     */
    abstract fun toDisplayString(): String
    
    /**
     * Format log cho Android Logcat (single line, concise).
     */
    abstract fun toLogcatString(): String
    
    protected fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    protected fun getLevelIcon(level: LogLevel): String {
        return when (level) {
            LogLevel.VERBOSE -> "💬"
            LogLevel.DEBUG -> "🐛"
            LogLevel.INFO -> "ℹ️"
            LogLevel.WARNING -> "⚠️"
            LogLevel.ERROR -> "❌"
        }
    }
}

/**
 * MessageLog - Log entry cho general text messages.
 *
 * ## Use cases:
 * - Debug messages từ components
 * - Warnings và errors với exceptions
 * - General flow tracking
 *
 * ## Example:
 * ```kotlin
 * MessageLog(
 *     level = LogLevel.ERROR,
 *     category = LogCategory.NETWORK,
 *     tag = "HttpFetcher",
 *     message = "Failed to fetch image",
 *     throwable = IOException("Connection timeout")
 * )
 * ```
 */
data class MessageLog(
    override val timestamp: Long = System.currentTimeMillis(),
    override val level: LogLevel,
    override val category: LogCategory = LogCategory.GENERAL,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) : LogEntry(timestamp, level, category) {
    
    override fun toDisplayString(): String {
        return buildString {
            append("[${formatTime(timestamp)}] ")
            append(getLevelIcon(level))
            append(" ${category.displayName} | $tag")
            append("\n   $message")
            
            if (throwable != null) {
                append("\n   Exception: ${throwable.javaClass.simpleName}")
                append("\n   ${throwable.message ?: "No message"}")
            }
        }
    }
    
    override fun toLogcatString(): String {
        return buildString {
            append("[${category.displayName}/$tag] $message")
            if (throwable != null) {
                append(" | ${throwable.message}")
            }
        }
    }
}

/**
 * ImageLoadLog - Log entry cho image load events với detailed timing breakdown.
 *
 * ## Purpose:
 * Track và measure performance của image loading operations.
 *
 * ## Timing Fields:
 * - **fetchTimeMs**: Network fetch time (chỉ có khi source = NETWORK)
 * - **decodeTimeMs**: Bitmap decode time
 * - **transformTimeMs**: Transformations time (nếu có)
 * - **cacheWriteTimeMs**: Disk cache write time
 * - **totalTimeMs**: Total time từ start đến finish
 *
 * ## Use cases:
 * - Performance monitoring
 * - Cache hit rate analysis
 * - Bottleneck identification
 * - Error tracking
 *
 * ## Example:
 * ```kotlin
 * // Network load
 * ImageLoadLog(
 *     url = "https://example.com/photo.jpg",
 *     source = LogSource.NETWORK,
 *     fetchTimeMs = 300,
 *     decodeTimeMs = 50,
 *     transformTimeMs = 20,
 *     totalTimeMs = 370,
 *     transformCount = 1
 * )
 *
 * // Memory cache hit
 * ImageLoadLog(
 *     url = "https://example.com/photo.jpg",
 *     source = LogSource.MEMORY_CACHE,
 *     totalTimeMs = 2  // Very fast!
 * )
 * ```
 */
data class ImageLoadLog(
    override val timestamp: Long = System.currentTimeMillis(),
    val url: String,
    val source: LogSource,
    val fetchTimeMs: Long? = null,
    val decodeTimeMs: Long? = null,
    val transformTimeMs: Long? = null,
    val cacheWriteTimeMs: Long? = null,
    val totalTimeMs: Long,
    val transformCount: Int = 0,
    val error: String? = null
) : LogEntry(
    timestamp = timestamp,
    level = if (error != null) LogLevel.ERROR else LogLevel.INFO,
    category = LogCategory.IMAGE_LOAD
) {
    
    override fun toDisplayString(): String {
        val timeStr = formatTime(timestamp)
        
        return buildString {
            append("[$timeStr] ")
            append(if (error != null) "❌ " else "✅ ")
            append(source.name)
            append(" | Total: ${totalTimeMs}ms")
            
            if (fetchTimeMs != null) append(" | Fetch: ${fetchTimeMs}ms")
            if (decodeTimeMs != null) append(" | Decode: ${decodeTimeMs}ms")
            if (transformTimeMs != null && transformCount > 0) {
                append(" | Transform: ${transformTimeMs}ms ($transformCount)")
            }
            if (cacheWriteTimeMs != null) append(" | Cache: ${cacheWriteTimeMs}ms")
            
            append("\n   URL: $url")
            
            if (error != null) {
                append("\n   Error: $error")
            }
        }
    }

    override fun toLogcatString(): String {
        return buildString {
            append("[${source.name}] ")
            append("Total: ${totalTimeMs}ms")
            if (fetchTimeMs != null) append(", Fetch: ${fetchTimeMs}ms")
            if (decodeTimeMs != null) append(", Decode: ${decodeTimeMs}ms")
            if (transformTimeMs != null && transformCount > 0) {
                append(", Transform: ${transformTimeMs}ms (${transformCount}x)")
            }
            append(" - $url")
            if (error != null) append(" [ERROR: $error]")
        }
    }
}

/**
 * LogSource - Nguồn mà image được load từ.
 *
 * Sắp xếp theo tốc độ (nhanh → chậm):
 * 1. ACTIVE_CACHE: Currently in use (~0-2ms)
 * 2. MEMORY_CACHE: LRU cache (~1-5ms)
 * 3. DISK_CACHE: File cache (~20-100ms)
 * 4. NETWORK: HTTP fetch (~100-1000ms)
 */
enum class LogSource {
    ACTIVE_CACHE,
    MEMORY_CACHE,
    DISK_CACHE,
    NETWORK
}
