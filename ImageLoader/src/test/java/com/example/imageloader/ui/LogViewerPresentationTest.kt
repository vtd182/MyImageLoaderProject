package com.example.imageloader.ui

import com.example.imageloader.logger.ImageLoadLog
import com.example.imageloader.logger.LogCategory
import com.example.imageloader.logger.LogLevel
import com.example.imageloader.logger.LogSource
import com.example.imageloader.logger.LogStats
import com.example.imageloader.logger.MessageLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogViewerPresentationTest {

    @Test
    fun `should combine counts when formatting quick stats`() {
        val stats = LogStats(
            totalLogs = 10,
            totalImageRequests = 7,
            imageErrors = 3,
            messageErrors = 1,
            jsonPhotoCount = 42,
            jsonCurrentPage = 3,
            activeCacheCount = 4,
            activeCacheAvgTime = 12.0,
            memoryCacheCount = 3,
            memoryCacheAvgTime = 15.0,
            diskCacheCount = 6,
            diskCacheAvgTime = 22.0,
            diskCacheAvgDecode = 9.0,
            diskCacheAvgTransform = 5.0,
            networkCount = 9,
            networkAvgTime = 30.0,
            networkAvgFetch = 13.0,
            networkAvgDecode = 11.0,
            networkAvgTransform = 7.0
        )

        val displayData = QuickStatsFormatter().format(stats)

        assertEquals(10, displayData.totalLogs)
        assertEquals(7, displayData.memoryCount) // active + memory cache
        assertEquals(6, displayData.diskCount)
        assertEquals(9, displayData.networkCount)
        assertEquals(4, displayData.errorCount) // imageErrors + messageErrors
    }

    @Test
    fun `should return all logs when filtering with empty selection`() {
        val logs = listOf(
            sampleMessageLog(),
            sampleImageLog()
        )

        val filtered = LogEntryFilter().filter(logs, emptySet())

        assertEquals(logs, filtered)
    }

    @Test
    fun `should filter logs when selecting specific categories`() {
        val generalLog = sampleMessageLog()
        val networkLog = sampleMessageLog(category = LogCategory.NETWORK)
        val imageLog = sampleImageLog()
        val logs = listOf(generalLog, networkLog, imageLog)

        val filtered = LogEntryFilter().filter(logs, setOf(LogCategory.GENERAL, LogCategory.IMAGE_LOAD))

        assertEquals(listOf(generalLog, imageLog), filtered)
    }

    @Test
    fun `should include logs according to category selection`() {
        val filter = LogEntryFilter()
        val log = sampleMessageLog()

        assertTrue(filter.shouldInclude(log, emptySet()))
        assertTrue(filter.shouldInclude(log, setOf(LogCategory.GENERAL)))
        assertFalse(filter.shouldInclude(log, setOf(LogCategory.NETWORK)))
    }

    @Test
    fun `should map image log with timings to UI model`() {
        val mapper = LogEntryUiModelMapper { "time-${it}" }
        val log = ImageLoadLog(
            timestamp = 100L,
            url = "https://example.com/image.jpg",
            source = LogSource.DISK_CACHE,
            fetchTimeMs = 12,
            decodeTimeMs = 23,
            transformTimeMs = 34,
            totalTimeMs = 45,
            isFastScrolling = true
        )

        val model = mapper.map(log)

        assertEquals("time-100", model.timeText)
        assertEquals("DISK CACHE", model.sourceText)
        assertEquals("45ms", model.totalText)
        assertTrue(model.timingsContainerVisible)
        assertEquals("Fetch: 12ms | Decode: 23ms | Transform: 34ms", model.timingsText)
        assertTrue(model.showFastScroll)
        assertEquals(log.url, model.urlText)
        assertNull(model.errorText)
        assertTrue(model.showOpenUrlButton)
        assertEquals(log.url, model.urlToOpen)
    }

    @Test
    fun `should map errored image log to UI model`() {
        val mapper = LogEntryUiModelMapper { "formatted" }
        val log = ImageLoadLog(
            timestamp = 200L,
            url = "https://example.com/error.jpg",
            source = LogSource.NETWORK,
            totalTimeMs = 60,
            error = "Boom"
        )

        val model = mapper.map(log)

        assertEquals("formatted", model.timeText)
        assertEquals("❌ NETWORK", model.sourceText)
        assertEquals("60ms", model.totalText)
        assertFalse(model.timingsContainerVisible)
        assertNull(model.timingsText)
        assertFalse(model.showFastScroll)
        assertEquals("Error: Boom", model.errorText)
        assertFalse(model.showOpenUrlButton)
        assertNull(model.urlToOpen)
    }

    @Test
    fun `should map message log to UI model`() {
        val mapper = LogEntryUiModelMapper { "time" }
        val throwable = IllegalStateException("Bad")
        val log = MessageLog(
            timestamp = 300L,
            level = LogLevel.ERROR,
            category = LogCategory.NETWORK,
            tag = "Logger",
            message = "Something happened",
            throwable = throwable
        )

        val model = mapper.map(log)

        assertEquals("time", model.timeText)
        assertEquals("❌ Network | Logger", model.sourceText)
        assertEquals("ERROR", model.totalText)
        assertFalse(model.timingsContainerVisible)
        assertNull(model.timingsText)
        assertFalse(model.showFastScroll)
        assertEquals("Something happened", model.urlText)
        assertEquals("IllegalStateException: Bad", model.errorText)
        assertFalse(model.showOpenUrlButton)
        assertNull(model.urlToOpen)
    }

    private fun sampleMessageLog(category: LogCategory = LogCategory.GENERAL) = MessageLog(
        timestamp = 0L,
        level = LogLevel.INFO,
        category = category,
        tag = "Tag",
        message = "Message"
    )

    private fun sampleImageLog() = ImageLoadLog(
        timestamp = 1L,
        url = "https://example.com/image.png",
        source = LogSource.MEMORY_CACHE,
        totalTimeMs = 20
    )
}
