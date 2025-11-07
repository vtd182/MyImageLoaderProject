package com.example.imageloader.ui

import com.example.imageloader.logger.ImageLoadLog
import com.example.imageloader.logger.LogCategory
import com.example.imageloader.logger.LogEntry
import com.example.imageloader.logger.LogLevel
import com.example.imageloader.logger.LogStats
import com.example.imageloader.logger.MessageLog
import java.util.Date
import java.util.Locale

data class QuickStatsDisplayData(
    val totalLogs: Int,
    val memoryCount: Int,
    val diskCount: Int,
    val networkCount: Int,
    val errorCount: Int
)

/**
 * Prepares quick stats data so it can be unit tested without Android dependencies.
 */
class QuickStatsFormatter {
    fun format(stats: LogStats): QuickStatsDisplayData {
        val totalMemory = stats.activeCacheCount + stats.memoryCacheCount
        val totalErrors = stats.imageErrors + stats.messageErrors

        return QuickStatsDisplayData(
            totalLogs = stats.totalLogs,
            memoryCount = totalMemory,
            diskCount = stats.diskCacheCount,
            networkCount = stats.networkCount,
            errorCount = totalErrors
        )
    }
}

/**
 * Handles filtering logic for log entries.
 */
class LogEntryFilter {
    fun filter(allLogs: List<LogEntry>, selectedCategories: Set<LogCategory>): List<LogEntry> {
        if (selectedCategories.isEmpty()) return allLogs
        return allLogs.filter { it.category in selectedCategories }
    }

    fun shouldInclude(log: LogEntry, selectedCategories: Set<LogCategory>): Boolean {
        return selectedCategories.isEmpty() || log.category in selectedCategories
    }
}

data class LogEntryUiModel(
    val timeText: String,
    val sourceText: String,
    val totalText: String,
    val timingsContainerVisible: Boolean,
    val timingsText: String?,
    val urlText: String,
    val errorText: String?,
    val showOpenUrlButton: Boolean,
    val urlToOpen: String?
)

/**
 * Transforms [LogEntry] objects into a UI friendly representation.
 */
class LogEntryUiModelMapper(
    private val timeFormatter: (Long) -> String = { timestamp ->
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        sdf.format(Date(timestamp))
    }
) {
    fun map(log: LogEntry): LogEntryUiModel {
        return when (log) {
            is ImageLoadLog -> mapImageLog(log)
            is MessageLog -> mapMessageLog(log)
        }
    }

    private fun mapImageLog(log: ImageLoadLog): LogEntryUiModel {
        val sourceLabel = log.source.name.replace("_", " ")
        val sourceText = if (log.error != null) {
            "❌ $sourceLabel"
        } else {
            sourceLabel
        }

        val timings = buildList {
            log.fetchTimeMs?.let { add("Fetch: ${it}ms") }
            log.decodeTimeMs?.let { add("Decode: ${it}ms") }
            log.transformTimeMs?.let { add("Transform: ${it}ms") }
        }

        val errorText = log.error?.let { "Error: $it" }
        val showOpenUrlButton = log.error == null

        return LogEntryUiModel(
            timeText = timeFormatter(log.timestamp),
            sourceText = sourceText,
            totalText = "${log.totalTimeMs}ms",
            timingsContainerVisible = timings.isNotEmpty(),
            timingsText = timings.takeIf { it.isNotEmpty() }?.joinToString(" | "),
            urlText = log.url,
            errorText = errorText,
            showOpenUrlButton = showOpenUrlButton,
            urlToOpen = log.url.takeIf { showOpenUrlButton }
        )
    }

    private fun mapMessageLog(log: MessageLog): LogEntryUiModel {
        val icon = when (log.level) {
            LogLevel.VERBOSE -> "💬"
            LogLevel.DEBUG -> "🐛"
            LogLevel.INFO -> "ℹ️"
            LogLevel.WARNING -> "⚠️"
            LogLevel.ERROR -> "❌"
        }

        val throwableText = log.throwable?.let {
            "${it.javaClass.simpleName}: ${it.message}"
        }

        return LogEntryUiModel(
            timeText = timeFormatter(log.timestamp),
            sourceText = "$icon ${log.category.displayName} | ${log.tag}",
            totalText = log.level.name,
            timingsContainerVisible = false,
            timingsText = null,
            urlText = log.message,
            errorText = throwableText,
            showOpenUrlButton = false,
            urlToOpen = null
        )
    }
}
