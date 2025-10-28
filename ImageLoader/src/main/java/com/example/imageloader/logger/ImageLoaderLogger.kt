package com.example.imageloader.logger

import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

object ImageLoaderLogger {
    private const val TAG = "ImageLoaderLogger"
    private const val MAX_LOGS = 500
    
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val listeners = mutableListOf<(LogEntry) -> Unit>()
    
    var saveToActivity = true
    var jsonPhotoCount = 0

    fun log(log: ImageLoadLog) {
        logEntry(log)
    }
    
    fun v(tag: String, message: String, category: LogCategory = LogCategory.GENERAL) {
        logEntry(MessageLog(level = LogLevel.VERBOSE, category = category, tag = tag, message = message))
    }
    
    fun d(tag: String, message: String, category: LogCategory = LogCategory.GENERAL) {
        logEntry(MessageLog(level = LogLevel.DEBUG, category = category, tag = tag, message = message))
    }
    
    fun i(tag: String, message: String, category: LogCategory = LogCategory.GENERAL) {
        logEntry(MessageLog(level = LogLevel.INFO, category = category, tag = tag, message = message))
    }
    
    fun w(tag: String, message: String, throwable: Throwable? = null, category: LogCategory = LogCategory.GENERAL) {
        logEntry(MessageLog(level = LogLevel.WARNING, category = category, tag = tag, message = message, throwable = throwable))
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null, category: LogCategory = LogCategory.GENERAL) {
        logEntry(MessageLog(level = LogLevel.ERROR, category = category, tag = tag, message = message, throwable = throwable))
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

    fun getAllLogs(): List<LogEntry> {
        return logs.toList()
    }

    fun clear() {
        logs.clear()
        Log.d(TAG, "Logs cleared")
    }

    fun addListener(listener: (LogEntry) -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: (LogEntry) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun getLogStats(): LogStats {
        val imageLoadLogs = logs.filterIsInstance<ImageLoadLog>()
        val messageLogs = logs.filterIsInstance<MessageLog>()
        
        return LogStats(
            totalLogs = logs.size,
            totalImageRequests = imageLoadLogs.size,
            fromCache = imageLoadLogs.count { it.source != LogSource.NETWORK },
            fromNetwork = imageLoadLogs.count { it.source == LogSource.NETWORK },
            imageErrors = imageLoadLogs.count { it.error != null },
            messageErrors = messageLogs.count { it.level == LogLevel.ERROR },
            jsonPhotoCount = jsonPhotoCount,
            avgTotalTime = imageLoadLogs.filter { it.error == null }.map { it.totalTimeMs }.average().takeIf { !it.isNaN() } ?: 0.0,
            avgDecodeTime = imageLoadLogs.mapNotNull { it.decodeTimeMs }.average().takeIf { !it.isNaN() } ?: 0.0,
            avgTransformTime = imageLoadLogs.mapNotNull { it.transformTimeMs }.average().takeIf { !it.isNaN() } ?: 0.0
        )
    }
}

data class LogStats(
    val totalLogs: Int,
    val totalImageRequests: Int,
    val fromCache: Int,
    val fromNetwork: Int,
    val imageErrors: Int,
    val messageErrors: Int,
    val jsonPhotoCount: Int,
    val avgTotalTime: Double,
    val avgDecodeTime: Double,
    val avgTransformTime: Double
)
