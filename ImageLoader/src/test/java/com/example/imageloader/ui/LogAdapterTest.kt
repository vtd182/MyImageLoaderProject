package com.example.imageloader.ui

import com.example.imageloader.logger.ImageLoadLog
import com.example.imageloader.logger.LogCategory
import com.example.imageloader.logger.LogLevel
import com.example.imageloader.logger.LogSource
import com.example.imageloader.logger.MessageLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LogAdapterTest {

    private val logEntryFilter = LogEntryFilter()
    private val uiModelMapper = LogEntryUiModelMapper { timestamp -> "time-$timestamp" }

    @Test
    fun `should populate filtered list when submitting logs with selected categories`() {
        val adapter = createAdapter()
        val logs = listOf(sampleMessageLog(), sampleImageLog())

        adapter.submitLogs(logs, setOf(LogCategory.GENERAL, LogCategory.IMAGE_LOAD))

        assertEquals(2, adapter.itemCount)
        assertSame(logs.first(), adapter.snapshotFilteredLogs().first())
    }

    @Test
    fun `should update filtered list to match selection when filtering by categories`() {
        val adapter = createAdapter()
        val logs = listOf(sampleMessageLog(), sampleImageLog())
        adapter.submitLogs(logs, setOf(LogCategory.GENERAL, LogCategory.IMAGE_LOAD))

        adapter.filterByCategories(setOf(LogCategory.GENERAL))

        assertEquals(1, adapter.itemCount)
        assertEquals(LogCategory.GENERAL, adapter.snapshotFilteredLogs().first().category)
    }

    @Test
    fun `should insert log at top when adding log with matching category`() {
        val adapter = createAdapter()
        val newLog = sampleImageLog()

        adapter.submitLogs(emptyList(), setOf(LogCategory.IMAGE_LOAD))
        adapter.addLog(newLog, setOf(LogCategory.IMAGE_LOAD))

        assertEquals(1, adapter.itemCount)
        assertSame(newLog, adapter.snapshotFilteredLogs().first())
    }

    @Test
    fun `should not alter filtered list when adding log with non-matching category`() {
        val adapter = createAdapter()
        val existing = sampleMessageLog()
        adapter.submitLogs(listOf(existing), setOf(LogCategory.GENERAL))

        adapter.addLog(sampleImageLog(), setOf(LogCategory.GENERAL))

        assertEquals(1, adapter.itemCount)
        assertSame(existing, adapter.snapshotFilteredLogs().first())
        assertEquals(2, adapter.snapshotAllLogs().size) // still tracked internally
    }

    @Test
    fun `should reset all state when clearing logs`() {
        val adapter = createAdapter()
        adapter.submitLogs(
            listOf(sampleMessageLog(), sampleImageLog()),
            setOf(LogCategory.GENERAL, LogCategory.IMAGE_LOAD)
        )

        adapter.clearLogs()

        assertEquals(0, adapter.itemCount)
        assertEquals(0, adapter.snapshotFilteredLogs().size)
        assertEquals(0, adapter.snapshotAllLogs().size)
    }

    private fun createAdapter(): LogAdapter {
        return LogAdapter(
            logEntryFilter = logEntryFilter,
            uiModelMapper = uiModelMapper
        )
    }

    private fun sampleMessageLog(category: LogCategory = LogCategory.GENERAL): MessageLog {
        return MessageLog(
            timestamp = 1L,
            level = LogLevel.INFO,
            category = category,
            tag = "Test",
            message = "message"
        )
    }

    private fun sampleImageLog(error: String? = null): ImageLoadLog {
        return ImageLoadLog(
            timestamp = 2L,
            url = "https://example.com/image.jpg",
            source = LogSource.NETWORK,
            fetchTimeMs = 10,
            decodeTimeMs = 20,
            transformTimeMs = 30,
            totalTimeMs = 40,
            error = error
        )
    }

}
