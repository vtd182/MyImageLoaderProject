package com.example.imageloader.logger

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARNING, ERROR
}

enum class LogCategory(val displayName: String) {
    ENGINE("Engine"),
    CACHE("Cache"),
    NETWORK("Network"),
    DECODE("Decode"),
    TRANSFORM("Transform"),
    IMAGE_LOAD("Image Load"),
    GENERAL("General");
}

sealed class LogEntry(
    open val timestamp: Long = System.currentTimeMillis(),
    open val level: LogLevel,
    open val category: LogCategory
) {
    abstract fun toDisplayString(): String
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
    val isFastScrolling: Boolean = false,
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
            if (isFastScrolling) append(" [FAST_SCROLL]")
            
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

enum class LogSource {
    ACTIVE_CACHE,
    MEMORY_CACHE,
    DISK_CACHE,
    NETWORK
}
