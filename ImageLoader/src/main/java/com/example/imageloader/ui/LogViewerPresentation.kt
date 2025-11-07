package com.example.imageloader.ui

import com.example.imageloader.logger.ImageLoadLog
import com.example.imageloader.logger.LogCategory
import com.example.imageloader.logger.LogEntry
import com.example.imageloader.logger.LogLevel
import com.example.imageloader.logger.LogStats
import com.example.imageloader.logger.MessageLog
import java.util.Date
import java.util.Locale

/**
 * QuickStatsDisplayData - Data class cho quick stats bar display.
 *
 * Hiển thị ở top bar của LogViewerActivity với format:
 * "Total: X | Memory: Y | Disk: Z | Network: W | Errors: E"
 */
data class QuickStatsDisplayData(
    val totalLogs: Int,
    val memoryCount: Int,
    val diskCount: Int,
    val networkCount: Int,
    val errorCount: Int
)

/**
 * QuickStatsFormatter - Transform LogStats thành QuickStatsDisplayData.
 *
 * ## Mục đích:
 * Tách logic format stats ra khỏi Activity để:
 * - Unit testable (không cần Android dependencies)
 * - Single Responsibility: Chỉ lo format data
 * - Reusable: Có thể dùng ở nhiều nơi
 *
 * ## Calculation:
 * - **totalMemory**: activeCacheCount + memoryCacheCount
 * - **totalErrors**: imageErrors + messageErrors
 *
 * ## Testing:
 * ```kotlin
 * @Test
 * fun `format stats correctly`() {
 *     val stats = LogStats(...)
 *     val formatter = QuickStatsFormatter()
 *     val result = formatter.format(stats)
 *     assertEquals(expected, result)
 * }
 * ```
 */
class QuickStatsFormatter {
    /**
     * Format LogStats thành UI-friendly display data.
     *
     * @param stats Raw stats từ ImageLoaderLogger.getLogStats()
     * @return Formatted data cho quick stats bar
     */
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
 * LogEntryFilter - Handles filtering logic for log entries.
 *
 * ## Mục đích:
 * Tách filter logic ra khỏi Adapter để:
 * - Unit testable
 * - Reusable
 * - Single Responsibility
 *
 * ## Filter Rule:
 * - **Empty selection**: Show ALL logs (no filter)
 * - **Has selection**: Show only logs matching selected categories
 *
 * ## Usage:
 * ```kotlin
 * val filter = LogEntryFilter()
 * val selectedCategories = setOf(LogCategory.NETWORK, LogCategory.ERROR)
 * val filtered = filter.filter(allLogs, selectedCategories)
 * ```
 */
class LogEntryFilter {
    /**
     * Filter danh sách logs theo categories.
     *
     * @param allLogs Full list of logs
     * @param selectedCategories Categories được chọn (empty = show all)
     * @return Filtered list
     */
    fun filter(allLogs: List<LogEntry>, selectedCategories: Set<LogCategory>): List<LogEntry> {
        if (selectedCategories.isEmpty()) return allLogs
        return allLogs.filter { it.category in selectedCategories }
    }

    /**
     * Check xem một log có nên được include hay không.
     *
     * Dùng cho real-time insert (check trước khi add vào filtered list).
     *
     * @param log Log entry cần check
     * @param selectedCategories Categories được chọn
     * @return true nếu log passes filter
     */
    fun shouldInclude(log: LogEntry, selectedCategories: Set<LogCategory>): Boolean {
        return selectedCategories.isEmpty() || log.category in selectedCategories
    }
}

/**
 * LogEntryUiModel - UI model cho một log entry item.
 *
 * ## Purpose:
 * Separation of concerns:
 * - LogEntry: Domain model (data + business logic)
 * - LogEntryUiModel: View model (display strings + visibility flags)
 *
 * ## Benefits:
 * - ViewHolder chỉ lo bind strings/visibility
 * - Logic format nằm ở Mapper (testable)
 * - Thay đổi UI không ảnh hưởng domain model
 *
 * ## Fields:
 * - **timeText**: "HH:mm:ss.SSS"
 * - **sourceText**: "NETWORK" hoặc "❌ NETWORK"
 * - **totalText**: "450ms" hoặc "ERROR"
 * - **timingsContainerVisible**: Show/hide timing breakdown
 * - **timingsText**: "Fetch: 300ms | Decode: 100ms"
 * - **urlText**: URL hoặc message text
 * - **errorText**: Error message (nullable)
 * - **showOpenUrlButton**: true nếu có URL để open
 * - **urlToOpen**: URL string (nullable)
 */
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
 * LogEntryUiModelMapper - Transforms LogEntry domain models thành UI models.
 *
 * ## Responsibilities:
 * - Format timestamp (HH:mm:ss.SSS)
 * - Add icons dựa trên log type/level
 * - Build timing breakdown strings
 * - Determine visibility flags
 * - Extract URLs for "Open" button
 *
 * ## Mapping Rules:
 *
 * ### ImageLoadLog:
 * ```
 * sourceText: "NETWORK" (success) hoặc "❌ NETWORK" (error)
 * totalText: "450ms"
 * timingsText: "Fetch: 300ms | Decode: 100ms | Transform: 50ms"
 * showOpenUrlButton: true nếu không có error
 * ```
 *
 * ### MessageLog:
 * ```
 * sourceText: "💬 GENERAL | Engine" (với icon theo level)
 * totalText: "INFO" (log level name)
 * errorText: "IOException: Connection timeout" (nếu có throwable)
 * showOpenUrlButton: false (không có URL)
 * ```
 *
 * ## Testing:
 * Injectable timeFormatter cho testing:
 * ```kotlin
 * val mapper = LogEntryUiModelMapper(
 *     timeFormatter = { "12:34:56.789" } // Fixed time for tests
 * )
 * ```
 *
 * @param timeFormatter Function format timestamp (injectable for testing)
 */
class LogEntryUiModelMapper(
    private val timeFormatter: (Long) -> String = { timestamp ->
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        sdf.format(Date(timestamp))
    }
) {
    /**
     * Map LogEntry thành LogEntryUiModel.
     *
     * @param log Domain model (ImageLoadLog hoặc MessageLog)
     * @return UI model với formatted strings
     */
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
