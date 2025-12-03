package com.example.imageloader.logger

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageLoaderLoggerTest {

    @Before
    fun setup() {
        ImageLoaderLogger.saveToActivity = true // Enable logging
        ImageLoaderLogger.clear() // Reset state
    }

    @After
    fun tearDown() {
        ImageLoaderLogger.clear()
    }

    @Test
    fun `should add log to list when logging image load log`() {
        val log = createSampleImageLoadLog()

        ImageLoaderLogger.log(log)

        val allLogs = ImageLoaderLogger.getAllLogs()
        assertEquals(1, allLogs.size)
        assertEquals(log, allLogs[0])
    }

    @Test
    fun `should add log to list when logging message log`() {
        val log = createSampleMessageLog()

        ImageLoaderLogger.i(log.tag, log.message, log.category)

        val allLogs = ImageLoaderLogger.getAllLogs()
        assertEquals(1, allLogs.size)
        assertEquals(log, allLogs[0])
    }

    @Test
    fun `should clear all logs`() {
        ImageLoaderLogger.log(createSampleImageLoadLog())

        ImageLoaderLogger.clear()

        assertTrue(ImageLoaderLogger.getAllLogs().isEmpty())
    }

    @Test
    fun `should handle empty logs in stats`() {
        val stats = ImageLoaderLogger.getLogStats()

        assertEquals(0, stats.totalLogs)
        assertEquals(0, stats.totalImageRequests)
        // All others should be 0
    }

    @Test
    fun `should calculate average times correctly when multiple entries`() {
        val log1 = createSampleImageLoadLog(
            source = LogSource.NETWORK,
            totalTimeMs = 100L,
            fetchTimeMs = 20L,
            decodeTimeMs = 30L,
            transformTimeMs = 50L
        )
        val log2 = createSampleImageLoadLog(
            source = LogSource.NETWORK,
            totalTimeMs = 200L,
            fetchTimeMs = 40L,
            decodeTimeMs = 60L,
            transformTimeMs = 100L
        )

        ImageLoaderLogger.log(log1)
        ImageLoaderLogger.log(log2)

        val stats = ImageLoaderLogger.getLogStats()

        assertEquals(2, stats.networkCount)
        assertEquals(150.0, stats.networkAvgTime, 0.0)
        assertEquals(30.0, stats.networkAvgFetch, 0.0)
        assertEquals(45.0, stats.networkAvgDecode, 0.0)
        assertEquals(75.0, stats.networkAvgTransform, 0.0)
    }

    @Test
    fun `should log all message levels correctly`() {
        ImageLoaderLogger.v("TAG", "Verbose log")
        ImageLoaderLogger.d("TAG", "Debug log")
        ImageLoaderLogger.w("TAG", "Warn log", RuntimeException("warn"))
        ImageLoaderLogger.e("TAG", "Error log", RuntimeException("error"))

        val all = ImageLoaderLogger.getAllLogs()
        assertEquals(4, all.size)
        assertTrue(all.all { it is MessageLog })
        val levels = all.map { (it as MessageLog).level }
        assertTrue(
            levels.containsAll(
                listOf(
                    LogLevel.VERBOSE,
                    LogLevel.DEBUG,
                    LogLevel.WARNING,
                    LogLevel.ERROR
                )
            )
        )
    }

    @Test
    fun `should not add log when saveToActivity is false`() {
        ImageLoaderLogger.saveToActivity = false
        ImageLoaderLogger.d("TAG", "Should skip")
        assertTrue(ImageLoaderLogger.getAllLogs().isEmpty())
    }

    @Test
    fun `should notify listeners when new log added`() {
        var called = false
        val listener: (LogEntry) -> Unit = { called = true }

        ImageLoaderLogger.addListener(listener)
        ImageLoaderLogger.d("TAG", "notify")
        assertTrue(called)

        ImageLoaderLogger.removeListener(listener)
    }

    @Test
    fun `should limit logs to max capacity`() {
        repeat(2010) {
            ImageLoaderLogger.log(createSampleImageLoadLog(totalTimeMs = it.toLong()))
        }
        val logs = ImageLoaderLogger.getAllLogs()
        assertTrue("Logs should be trimmed to 2010 max", logs.size <= 2010)
    }

    @Test
    fun `should handle image load logs from multiple sources and errors`() {
        val network = createSampleImageLoadLog(source = LogSource.NETWORK, totalTimeMs = 100)
        val memory = createSampleImageLoadLog(source = LogSource.MEMORY_CACHE, totalTimeMs = 50)
        val disk = createSampleImageLoadLog(
            source = LogSource.DISK_CACHE,
            totalTimeMs = 75,
            decodeTimeMs = 10
        )
        val active = createSampleImageLoadLog(source = LogSource.ACTIVE_CACHE, totalTimeMs = 25)
        val failed =
            createSampleImageLoadLog(source = LogSource.NETWORK, error = "404", totalTimeMs = 0)

        listOf(network, memory, disk, active, failed).forEach(ImageLoaderLogger::log)

        val stats = ImageLoaderLogger.getLogStats()
        assertEquals(5, stats.totalImageRequests)
        assertEquals(1, stats.imageErrors)
        assertTrue(stats.networkAvgTime > 0)
        assertTrue(stats.diskCacheAvgDecode > 0)
    }

    @Test
    fun `should clear logs and print logcat`() {
        ImageLoaderLogger.d("TAG", "before clear")
        ImageLoaderLogger.clear()
        assertTrue(ImageLoaderLogger.getAllLogs().isEmpty())
    }


    private fun createSampleImageLoadLog(
        timestamp: Long = System.currentTimeMillis(),
        source: LogSource = LogSource.MEMORY_CACHE,
        totalTimeMs: Long = 50L,
        fetchTimeMs: Long? = 10L,
        decodeTimeMs: Long? = 20L,
        transformTimeMs: Long? = 20L,
        error: String? = null
    ) = ImageLoadLog(
        timestamp = timestamp,
        url = "https://example.com/image.jpg",
        source = source,
        totalTimeMs = totalTimeMs,
        fetchTimeMs = fetchTimeMs,
        decodeTimeMs = decodeTimeMs,
        transformTimeMs = transformTimeMs,
        error = error
    )

    private fun createSampleMessageLog(
        timestamp: Long = System.currentTimeMillis(),
        level: LogLevel = LogLevel.INFO,
        category: LogCategory = LogCategory.GENERAL,
        tag: String = "TestTag",
        message: String = "Test message",
        throwable: Throwable? = null
    ) = MessageLog(
        timestamp = timestamp,
        level = level,
        category = category,
        tag = tag,
        message = message,
        throwable = throwable
    )
}
