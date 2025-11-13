package com.example.imageloader.benchmark.suite

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.imageloader.benchmark.reporter.SimplifiedAnalyzer
import com.example.imageloader.benchmark.reporter.SimplifiedHtmlReporter
import com.example.imageloader.logger.ImageLoadLog
import com.example.imageloader.logger.ImageLoaderLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * RealisticMacroBenchmark V3.0 — Simulates Real User Scroll Behavior
 *
 * Key Features:
 * - Scroll half-screen at a time (natural reading pattern)
 * - Wait for images to load based on size (large/huge need more time)
 * - Target-based phases: Load N unique images before moving to next phase
 * - Smart waiting: Check if pending loads completed before scrolling
 */

@RunWith(AndroidJUnit4::class)
class RealisticMacroBenchmark {

    // ============================================================
    // 🔧 REALISTIC BEHAVIOR CONFIG
    // ============================================================

    /** Phase 1 Target: Load this many UNIQUE images (realistic for viewport) */
    private val PHASE1_TARGET_IMAGES = 100

    /** Base wait time after each scroll (OPTIMIZED for speed) */
    private val BASE_WAIT_MS = 2000L

    /** Wait multipliers by image size (detected from URL patterns) */
    private val WAIT_MULTIPLIER = mapOf(
        "tiny" to 1.0,    // 400ms
        "small" to 1.2,   // 480ms
        "medium" to 1.5,  // 600ms
        "large" to 2.0,   // 800ms
        "huge" to 2.5     // 1000ms
    )

    /** Max wait time safety timeout */
    private val MAX_WAIT_MS = 5000L

    /** Poll interval for checking load completion */
    private val POLL_INTERVAL_MS = 200L

    // ============================================================

    private lateinit var context: Context
    private lateinit var outputDir: File
    private lateinit var scenario: ActivityScenario<BenchmarkTestActivity>

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        outputDir = File(context.getExternalFilesDir(null), "benchmark-results").apply { mkdirs() }

        ImageLoaderLogger.saveToActivity = true
        ImageLoaderLogger.clear()
        ImageLoaderLogger.resetBitmapPoolStats()

        println("✅ RealisticMacroBenchmark V3.0 — Setup complete")
    }

    @Test
    fun testRealisticScrollBehavior() = runBlocking {
        println("\n🚀 RealisticMacroBenchmark V3.0 — Natural User Behavior")
        println("📋 Target: Load $PHASE1_TARGET_IMAGES unique images in Phase 1")

        val itemCount = 1000
        val intent = Intent(context, BenchmarkTestActivity::class.java)
            .putExtra("ITEM_COUNT", itemCount)

        scenario = ActivityScenario.launch(intent)
        delay(2000) // Initial load

        // ============================================================
        // Calculate FULL screen scroll distance (better for loading)
        // ============================================================
        var screenHeight = 0
        scenario.onActivity { activity ->
            screenHeight = activity.recyclerView.height
            println("📏 Screen height: ${screenHeight}px (scroll full screen for better loading)")
            screenHeight = (0.6 * screenHeight).toInt()
        }
        delay(500)

        val memoryBefore = ImageLoaderLogger.getMemorySnapshot()
        val startTime = System.currentTimeMillis()

        // ============================================================
        // Phase 1: Scroll DOWN until we load TARGET images
        // ============================================================
        println("\n📥 Phase 1: Scroll DOWN (natural pace, load $PHASE1_TARGET_IMAGES unique images)")

        var scrollCount = 0
        var previousTotalLogs = 0

        while (true) {
            // Get current stats
            val currentLogs: List<Any> = ImageLoaderLogger.getAllLogs()
            val imageLogs: List<ImageLoadLog> = currentLogs.filterIsInstance<ImageLoadLog>()
                .filter { it.error == null }

            val uniqueImageUrls = imageLogs.map { it.url }.distinct()
            val currentUniqueCount = uniqueImageUrls.size
            val currentTotalLogs = imageLogs.size

            // Check if we've reached target
            if (currentUniqueCount >= PHASE1_TARGET_IMAGES) {
                println("   ✅ Reached target! Loaded $currentUniqueCount unique images")
                break
            }

            // Safety check: Max 500 scrolls
            if (scrollCount >= 500) {
                println("   ⚠️  Safety limit reached (500 scrolls)")
                break
            }

            // Scroll FULL screen down (loads more items per scroll)
            scenario.onActivity { activity ->
                activity.recyclerView.scrollBy(0, screenHeight)
            }
            scrollCount++

            // Smart wait based on recent image sizes
            val recentImages = getRecentImageSizes(imageLogs, previousTotalLogs, currentTotalLogs)
            val waitTime = calculateWaitTime(recentImages)

            // Wait for images to load
            delay(waitTime)

            // Additional wait if still loading (compare total log counts)
            val settled = waitForLoadCompletion(previousTotalLogs, currentTotalLogs)

            previousTotalLogs = currentTotalLogs

            // Detailed progress every 5 scrolls
            if (scrollCount % 5 == 0) {
                println("   [#$scrollCount] Unique: $currentUniqueCount/$PHASE1_TARGET_IMAGES | Logs: $currentTotalLogs | Wait: ${waitTime}ms")
            }
        }

        println("\n   Total scrolls: $scrollCount")
        val stats1 = ImageLoaderLogger.getLogStats()
        println("   Total requests: ${stats1.totalImageRequests}")
        println("   Network loads: ${stats1.networkCount}")

        // ============================================================
        // Phase 2: Scroll BACK UP to test cache
        // ============================================================
        println("\n💾 Phase 2: Scroll BACK UP (test Disk Cache & Memory Cache)")

        val phase2Scrolls = scrollCount // Same distance back
        repeat(phase2Scrolls) { i ->
            scenario.onActivity { activity ->
                activity.recyclerView.scrollBy(0, -screenHeight)
            }

            // Faster scroll up (images should be cached)
            delay(500)

            if (i % 20 == 0) print(".")
        }
        println()

        delay(2000) // Final settle

        val stats2 = ImageLoaderLogger.getLogStats()
        println("   💿 Disk Cache hits: ${stats2.diskCacheCount}")
        println("   🔵 Memory Cache hits: ${stats2.memoryCacheCount}")
        println("   🟢 Active Cache hits: ${stats2.activeCacheCount}")

        // ============================================================
        // Final Stats
        // ============================================================
        val finalStats = ImageLoaderLogger.getLogStats()
        val memoryAfter = ImageLoaderLogger.getMemorySnapshot()
        val endTime = System.currentTimeMillis()

        println("\n" + "=".repeat(50))
        println("📊 FINAL RESULTS — Realistic User Behavior")
        println("=".repeat(50))

        val total = finalStats.totalImageRequests
        fun pct(v: Int) = if (total > 0) String.format("%.1f%%", v * 100.0 / total) else "0.0%"

        println("Total Requests:  $total")
        println("Active Cache:    ${finalStats.activeCacheCount} (${pct(finalStats.activeCacheCount)})")
        println("Memory Cache:    ${finalStats.memoryCacheCount} (${pct(finalStats.memoryCacheCount)})")
        println("Disk Cache:      ${finalStats.diskCacheCount} (${pct(finalStats.diskCacheCount)})")
        println("Network:         ${finalStats.networkCount} (${pct(finalStats.networkCount)})")

        val efficiency = if (total > 0) {
            (finalStats.activeCacheCount + finalStats.memoryCacheCount + finalStats.diskCacheCount) * 100.0 / total
        } else 0.0

        println("Cache Efficiency: ${String.format("%.1f%%", efficiency)}")
        println("Test Duration:    ${(endTime - startTime) / 1000}s")

        // ============================================================
        // Generate Simplified Reports
        // ============================================================
        val simplifiedResult = SimplifiedAnalyzer.analyze(
            testStartTime = startTime,
            testEndTime = endTime,
            imageSpecs = null
        )

        val timestamp = System.currentTimeMillis()
        val htmlFile = File(outputDir, "realistic-benchmark-$timestamp.html")
        val jsonFile = File(outputDir, "realistic-benchmark-$timestamp.json")

        SimplifiedHtmlReporter.generate(simplifiedResult, htmlFile)
        jsonFile.writeText("See HTML report for details")

        println("\n📤 Reports Generated:")
        println("   🌐 HTML: ${htmlFile.name}")
        println("   📊 Location: ${outputDir.absolutePath}")

        if (simplifiedResult.decodeMetrics.diskVsNetworkSpeedup > 0) {
            println(
                "\n⚡ Disk vs Network Speedup: ${
                    String.format(
                        "%.2fx",
                        simplifiedResult.decodeMetrics.diskVsNetworkSpeedup
                    )
                }"
            )
        }

        scenario.close()
        println("\n🎉 RealisticMacroBenchmark completed!")
    }

    // ============================================================
    // Helper Functions
    // ============================================================

    /**
     * Get image sizes from recent loads
     */
    private fun getRecentImageSizes(
        allLogs: List<ImageLoadLog>,
        previousCount: Int,
        currentCount: Int
    ): List<String> {
        val newLogsCount = (currentCount - previousCount).coerceAtLeast(0)

        if (newLogsCount == 0) {
            return emptyList()
        }

        val recentLogs = allLogs.takeLast(newLogsCount)
        return recentLogs.map { log ->
            detectImageSize(log.url)
        }
    }

    /**
     * Detect image size from URL pattern
     */
    private fun detectImageSize(url: String): String {
        return when {
            url.contains("/200/200") -> "tiny"
            url.contains("/400/600") -> "small"
            url.contains("/1080/1440") -> "medium"
            url.contains("/2560/1440") -> "large"
            url.contains("/4096/4096") -> "huge"
            else -> "small" // default
        }
    }

    /**
     * Calculate wait time based on image sizes in viewport
     */
    private fun calculateWaitTime(imageSizes: List<String>): Long {
        if (imageSizes.isEmpty()) return BASE_WAIT_MS

        // Get max multiplier from visible images
        val maxMultiplier = imageSizes.mapNotNull { size ->
            WAIT_MULTIPLIER[size]
        }.maxOrNull() ?: 1.0

        val waitTime = (BASE_WAIT_MS * maxMultiplier).toLong()
        return waitTime.coerceAtMost(MAX_WAIT_MS)
    }

    /**
     * Wait for pending image loads to complete
     * Polls stats to check if new images are still loading
     */
    private suspend fun waitForLoadCompletion(
        previousTotalLogs: Int,
        expectedMinCount: Int
    ): Boolean {
        var attempts = 0
        val maxAttempts = 10 // Max 2 seconds additional wait
        var lastSeenCount = previousTotalLogs

        while (attempts < maxAttempts) {
            delay(POLL_INTERVAL_MS)
            attempts++

            val currentLogs: List<Any> = ImageLoaderLogger.getAllLogs()
            val imageLogs: List<ImageLoadLog> = currentLogs.filterIsInstance<ImageLoadLog>()
                .filter { it.error == null }
            val currentCount = imageLogs.size

            // If no new images loaded in last poll, assume settled
            if (currentCount == lastSeenCount && currentCount >= expectedMinCount) {
                return true
            }

            lastSeenCount = currentCount
        }

        return false
    }
}
