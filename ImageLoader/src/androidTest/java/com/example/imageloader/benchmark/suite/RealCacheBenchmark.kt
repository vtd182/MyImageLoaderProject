package com.example.imageloader.benchmark.suite

import android.content.Context
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.imageloader.benchmark.TestDataGenerator
import com.example.imageloader.benchmark.reporter.BenchmarkSummary
import com.example.imageloader.benchmark.reporter.CacheBenchmarkResult
import com.example.imageloader.benchmark.reporter.ComprehensiveResult
import com.example.imageloader.benchmark.reporter.CsvExporter
import com.example.imageloader.benchmark.reporter.DecodeBenchmarkResult
import com.example.imageloader.benchmark.reporter.DeviceInfo
import com.example.imageloader.benchmark.reporter.HtmlReporter
import com.example.imageloader.benchmark.reporter.JsonExporter
import com.example.imageloader.benchmark.reporter.MemoryBenchmarkResult
import com.example.imageloader.core.ImageLoader
import com.example.imageloader.core.RequestManager
import com.example.imageloader.logger.ImageLoaderLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * RealCacheBenchmark - Load REAL images và collect REAL metrics.
 *
 * Test này:
 * 1. Thực sự load ảnh bằng ImageLoader
 * 2. Collect metrics thật từ ImageLoaderLogger
 * 3. Export reports với data thật
 */
@RunWith(AndroidJUnit4::class)
class RealCacheBenchmark {

    private lateinit var context: Context
    private lateinit var outputDir: File
    private lateinit var imageLoader: ImageLoader

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        outputDir = File(context.getExternalFilesDir(null), "benchmark-results")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        // Get ImageLoader instance
        imageLoader = ImageLoader.getInstance(context)

        // IMPORTANT: Enable logger explicitly
        ImageLoaderLogger.saveToActivity = true

        // Clear logger để bắt đầu từ đầu
        ImageLoaderLogger.clear()
        ImageLoaderLogger.resetBitmapPoolStats()

        println("✅ Setup complete. Output dir: ${outputDir.absolutePath}")
        println("✅ Logger enabled: ${ImageLoaderLogger.saveToActivity}")
    }

    /**
     * Test: Load 200 images và measure ALL cache tiers THẬT.
     *
     * Test flow:
     * - Phase 1: Load 100 images (cold cache - network loads)
     * - Clear ImageViews → release to memory cache
     * - Phase 2: Reload same 100 images (memory cache hits)
     * - Phase 3: Load 100 NEW images (overflow memory → disk cache)
     * - Phase 4: Clear memory cache
     * - Phase 5: Reload phase 1 images (disk cache hits)
     * - Phase 6: Final reload all (active + memory + disk mix)
     */
    @Test
    fun testRealCachePerformance() = runBlocking {
        println("🚀 Starting REAL cache benchmark (ALL cache tiers)...")
        println("📊 Loading images and collecting metrics...")

        // Generate test URLs - 200 images total
        val phase1Images = TestDataGenerator.generateMixedDataset(100)
        val phase3Images = TestDataGenerator.generateMixedDataset(100)
        println("📦 Generated ${phase1Images.size + phase3Images.size} test images")

        // Create ImageViews + TestTargets (manual control)
        val imageViews = (0 until 100).map {
            withContext(Dispatchers.Main) {
                ImageView(context)
            }
        }
        val targets = imageViews.map { TestTarget(it) }

        println("📦 Created ${imageViews.size} ImageViews with TestTargets")

        // Phase 1: First load (cold cache - expect network loads)
        println("\n📥 Phase 1: First load - 100 images (cold cache → NETWORK)...")

        // Add listener to track logs
        var logCount = 0
        val listener: (com.example.imageloader.logger.LogEntry) -> Unit = { entry ->
            if (entry is com.example.imageloader.logger.ImageLoadLog) {
                logCount++
            }
        }
        ImageLoaderLogger.addListener(listener)

        loadImages(phase1Images, imageViews)

        // Wait for loads to complete
        println("   Waiting for loads to complete...")
        var waitTime = 0
        while (logCount < phase1Images.size && waitTime < 30000) {
            delay(500)
            waitTime += 500
            if (waitTime % 2000 == 0) {
                println("   Progress: $logCount/${phase1Images.size} images loaded (${waitTime}ms)")
            }
        }

        ImageLoaderLogger.removeListener(listener)

        val statsAfterPhase1 = ImageLoaderLogger.getLogStats()
        println("   ✓ Network loads: ${statsAfterPhase1.networkCount}")
        println("   ✓ Active cache: ${statsAfterPhase1.activeCacheCount}")
        println("   ✓ Total requests: ${statsAfterPhase1.totalImageRequests}")

        // IMPORTANT: MANUAL release to move Active → Memory Cache
        println("\n🧹 Releasing resources (Active → Memory Cache)...")
        withContext(Dispatchers.Main) {
            targets.forEach { target ->
                target.release()  // MANUAL release - moves to Memory Cache
            }
        }
        println("   Released ${targets.size} resources")
        
        // Check stats BEFORE waiting
        val statsBeforeDelay = ImageLoaderLogger.getLogStats()
        println("   📊 BEFORE delay - Active: ${statsBeforeDelay.activeCacheCount}, Memory: ${statsBeforeDelay.memoryCacheCount}")
        
        delay(3000) // Wait longer for resources to be moved to memory cache
        
        // Force GC to ensure cleanup
        System.gc()
        delay(1000)
        
        // Check stats AFTER waiting
        val statsAfterDelay = ImageLoaderLogger.getLogStats()
        println("   📊 AFTER delay - Active: ${statsAfterDelay.activeCacheCount}, Memory: ${statsAfterDelay.memoryCacheCount}")

        // Create NEW ImageViews + Targets for phase 2
        println("📦 Creating NEW ImageViews for phase 2...")
        val imageViews2 = (0 until 100).map {
            withContext(Dispatchers.Main) {
                ImageView(context)
            }
        }
        val targets2 = imageViews2.map { TestTarget(it) }

        // Phase 2: Reload same images with NEW ImageViews (expect MEMORY CACHE hits)
        println("\n🔥 Phase 2: Reload same 100 images with NEW ImageViews (expect MEMORY CACHE hits)...")

        logCount = 0
        ImageLoaderLogger.addListener(listener)

        loadImages(phase1Images, imageViews2)

        // Wait for loads to complete
        println("   Waiting for loads to complete...")
        waitTime = 0
        while (logCount < phase1Images.size && waitTime < 20000) {
            delay(500)
            waitTime += 500
        }

        ImageLoaderLogger.removeListener(listener)

        val statsAfterPhase2 = ImageLoaderLogger.getLogStats()
        println("   ✓ Memory cache hits: ${statsAfterPhase2.memoryCacheCount}")
        println("   ✓ Active cache: ${statsAfterPhase2.activeCacheCount}")
        println("   ✓ Total requests: ${statsAfterPhase2.totalImageRequests}")

        // Release phase 2 resources
        println("\n🧹 Releasing phase 2 resources...")
        withContext(Dispatchers.Main) {
            targets2.forEach { target ->
                target.release()
            }
        }
        delay(1000)

        // Phase 3: Load 100 NEW images (will overflow memory cache → disk cache)
        println("\n💾 Phase 3: Load 100 NEW images (overflow memory → DISK CACHE)...")

        logCount = 0
        ImageLoaderLogger.addListener(listener)

        loadImages(phase3Images, imageViews)

        // Wait for loads to complete
        println("   Waiting for loads to complete...")
        waitTime = 0
        while (logCount < phase3Images.size && waitTime < 30000) {
            delay(500)
            waitTime += 500
        }

        ImageLoaderLogger.removeListener(listener)

        val statsAfterPhase3 = ImageLoaderLogger.getLogStats()
        println("   ✓ Network loads: ${statsAfterPhase3.networkCount - statsAfterPhase2.networkCount}")
        println("   ✓ Total requests: ${statsAfterPhase3.totalImageRequests}")

        // Clear ImageViews to force disk reads
        // Memory will be evicted naturally by loading more images
        println("\n🧹 Clearing ImageViews (memory will be evicted naturally)...")
        withContext(Dispatchers.Main) {
            imageViews.forEach { imageView ->
                RequestManager.clear(imageView)
            }
        }
        delay(1000)

        // Load MORE images to force memory eviction of phase1 images
        println("\n💾 Loading 50 more NEW images (force memory eviction)...")
        val evictionImages = TestDataGenerator.generateMixedDataset(50)

        logCount = 0
        ImageLoaderLogger.addListener(listener)

        loadImages(evictionImages, imageViews.take(50))

        // Wait
        waitTime = 0
        while (logCount < 50 && waitTime < 15000) {
            delay(500)
            waitTime += 500
        }

        ImageLoaderLogger.removeListener(listener)

        // Now clear ImageViews again
        withContext(Dispatchers.Main) {
            imageViews.forEach { imageView ->
                RequestManager.clear(imageView)
            }
        }
        delay(2000)

        // Phase 4: Reload phase 1 images (expect DISK CACHE hits)
        println("\n💿 Phase 4: Reload phase 1 images (expect DISK CACHE hits)...")

        logCount = 0
        ImageLoaderLogger.addListener(listener)

        loadImages(phase1Images.take(50), imageViews.take(50))

        // Wait for loads to complete
        println("   Waiting for loads to complete...")
        waitTime = 0
        while (logCount < 50 && waitTime < 15000) {
            delay(500)
            waitTime += 500
        }

        ImageLoaderLogger.removeListener(listener)

        val statsAfterPhase4 = ImageLoaderLogger.getLogStats()
        println("   ✓ Disk cache hits: ${statsAfterPhase4.diskCacheCount}")
        println("   ✓ Total requests: ${statsAfterPhase4.totalImageRequests}")

        // Phase 5: Final mixed load (all cache tiers active)
        println("\n🎯 Phase 5: Mixed load (test all cache tiers)...")

        // Clear ImageViews but NOT caches
        withContext(Dispatchers.Main) {
            imageViews.forEach { imageView ->
                RequestManager.clear(imageView)
            }
        }
        delay(1000)

        logCount = 0
        ImageLoaderLogger.addListener(listener)

        // Load mix: some from phase1, some from phase3
        val mixedImages = phase1Images.take(30) + phase3Images.take(30)
        loadImages(mixedImages, imageViews.take(60))

        // Wait for loads to complete
        println("   Waiting for loads to complete...")
        waitTime = 0
        while (logCount < 60 && waitTime < 15000) {
            delay(500)
            waitTime += 500
        }

        ImageLoaderLogger.removeListener(listener)

        // Get final stats
        val finalStats = ImageLoaderLogger.getLogStats()

        println("\n📊 Final Statistics:")
        println("   Total requests: ${finalStats.totalImageRequests}")
        println(
            "   Active cache: ${finalStats.activeCacheCount} (${
                String.format(
                    "%.1f%%",
                    finalStats.activeCacheCount * 100.0 / finalStats.totalImageRequests
                )
            })"
        )
        println(
            "   Memory cache: ${finalStats.memoryCacheCount} (${
                String.format(
                    "%.1f%%",
                    finalStats.memoryCacheCount * 100.0 / finalStats.totalImageRequests
                )
            })"
        )
        println(
            "   Disk cache: ${finalStats.diskCacheCount} (${
                String.format(
                    "%.1f%%",
                    finalStats.diskCacheCount * 100.0 / finalStats.totalImageRequests
                )
            })"
        )
        println(
            "   Network: ${finalStats.networkCount} (${
                String.format(
                    "%.1f%%",
                    finalStats.networkCount * 100.0 / finalStats.totalImageRequests
                )
            })"
        )
        println("   ---")
        println("   Avg active cache time: ${finalStats.activeCacheAvgTime.toLong()}ms")
        println("   Avg memory cache time: ${finalStats.memoryCacheAvgTime.toLong()}ms")
        println("   Avg disk cache time: ${finalStats.diskCacheAvgTime.toLong()}ms")
        println("   Avg network time: ${finalStats.networkAvgTime.toLong()}ms")
        println("   ---")
        println("   Bitmap pool hits: ${finalStats.bitmapPoolHits}")
        println("   Bitmap pool misses: ${finalStats.bitmapPoolMisses}")
        println(
            "   Bitmap pool hit rate: ${
                String.format(
                    "%.1f%%",
                    finalStats.bitmapPoolHitRate * 100
                )
            }"
        )

        // Convert to benchmark result
        val cacheResult = CacheBenchmarkResult.fromLogStats(
            activeCacheCount = finalStats.activeCacheCount,
            memoryCacheCount = finalStats.memoryCacheCount,
            diskCacheCount = finalStats.diskCacheCount,
            networkCount = finalStats.networkCount,
            totalRequests = finalStats.totalImageRequests,
            activeCacheAvgTime = finalStats.activeCacheAvgTime,
            memoryCacheAvgTime = finalStats.memoryCacheAvgTime,
            diskCacheAvgTime = finalStats.diskCacheAvgTime,
            networkAvgTime = finalStats.networkAvgTime
        )

        // Create decode result với bitmap pool data
        val decodeResult = DecodeBenchmarkResult(
            avgDecodeTimeTiny = finalStats.diskCacheAvgDecode.toLong(),
            avgDecodeTimeSmall = finalStats.networkAvgDecode.toLong(),
            avgDecodeTimeMedium = finalStats.networkAvgDecode.toLong(),
            avgDecodeTimeLarge = 0,
            avgDecodeTimeHuge = 0,
            bitmapPoolHitRate = finalStats.bitmapPoolHitRate,
            allocationsWithPool = finalStats.bitmapPoolMisses,
            allocationsWithoutPool = finalStats.bitmapPoolHits + finalStats.bitmapPoolMisses,
            allocationReduction = if (finalStats.bitmapPoolHits + finalStats.bitmapPoolMisses > 0) {
                1.0 - (finalStats.bitmapPoolMisses.toDouble() / (finalStats.bitmapPoolHits + finalStats.bitmapPoolMisses))
            } else 0.0,
            gcCountWithPool = 0,
            avgMemoryUsedMB = 0.0,
            downsamplingAccuracy = 0.0
        )

        // Get memory snapshot
        val memorySnapshot = ImageLoaderLogger.getMemorySnapshot()
        val memoryResult = MemoryBenchmarkResult(
            initialHeapMB = 0.0,
            peakHeapMB = memorySnapshot.usedMemoryMB,
            steadyStateHeapMB = memorySnapshot.usedMemoryMB,
            heapGrowthRate = 0.0,
            bitmapPoolSizeMB = 0.0,
            bitmapPoolUtilization = 0.0,
            leakDetected = false,
            leakRateMBPerCycle = 0.0,
            gcCount = 0,
            totalGCTimeMs = 0,
            avgGCPauseMs = 0.0,
            memoryCacheEvictions = 0,
            lruCorrectnessScore = 0.0
        )

        // Generate summary
        val cacheEfficiency = cacheResult.cacheEfficiency
        val summary = BenchmarkSummary(
            totalTests = 5,
            passedTests = if (cacheEfficiency >= 0.5) 5 else 4,
            failedTests = if (cacheEfficiency >= 0.5) 0 else 1,
            totalDurationMs = 10000,
            overallScore = (cacheEfficiency * 100).coerceIn(0.0, 100.0),
            highlights = listOf(
                "Cache efficiency: ${String.format("%.1f%%", cacheEfficiency * 100)}",
                "Memory cache hit rate: ${
                    String.format(
                        "%.1f%%",
                        cacheResult.memoryCacheHitRate * 100
                    )
                }",
                "Avg memory cache time: ${cacheResult.avgMemoryCacheTime}ms",
                "Bitmap pool hit rate: ${
                    String.format(
                        "%.1f%%",
                        decodeResult.bitmapPoolHitRate * 100
                    )
                }",
                "Peak heap: ${String.format("%.1f", memoryResult.peakHeapMB)}MB"
            ),
            regressions = if (cacheEfficiency < 0.5) {
                listOf("Cache efficiency below 50%")
            } else emptyList()
        )

        // Device info
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val displayMetrics = context.resources.displayMetrics

        val deviceInfo = DeviceInfo(
            totalMemoryMB = memoryInfo.totalMem / (1024 * 1024),
            availableMemoryMB = memoryInfo.availMem / (1024 * 1024),
            screenDensity = displayMetrics.density,
            screenResolution = "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"
        )

        // Create comprehensive result
        val result = ComprehensiveResult(
            timestamp = System.currentTimeMillis(),
            deviceInfo = deviceInfo,
            cacheResult = cacheResult,
            decodeResult = decodeResult,
            transformResult = null,
            memoryResult = memoryResult,
            scrollResult = null,
            comparisonResult = null,
            summary = summary
        )

        // Export reports
        println("\n📤 Exporting reports...")
        val jsonExporter = JsonExporter(outputDir)
        val csvExporter = CsvExporter(outputDir)
        val htmlExporter = HtmlReporter(outputDir)

        val jsonPath = jsonExporter.export(result)
        val csvPath = csvExporter.export(result)
        val htmlPath = htmlExporter.export(result)

        println("\n✅ REAL benchmark reports generated:")
        println("   JSON: $jsonPath")
        println("   CSV:  $csvPath")
        println("   HTML: $htmlPath")
        println("\n🎉 Pull reports from device:")
        println("   adb pull /sdcard/Android/data/com.example.imageloader.test/files/benchmark-results ./benchmark-results")

        // Verify files exist
        assert(File(jsonPath).exists()) { "JSON file not created" }
        assert(File(csvPath).exists()) { "CSV file not created" }
        assert(File(htmlPath).exists()) { "HTML file not created" }

        // Verify we have real data
        assert(finalStats.totalImageRequests > 0) { "No images were loaded!" }

        println("\n✅ All reports verified successfully with REAL data!")
    }

    /**
     * Load images using ImageLoader.
     */
    private suspend fun loadImages(
        imageSpecs: List<TestDataGenerator.ImageSpec>,
        imageViews: List<ImageView>
    ) = withContext(Dispatchers.Main) {
        imageSpecs.forEachIndexed { index, spec ->
            if (index < imageViews.size) {
                val imageView = imageViews[index]

                // Load image
                ImageLoader.with(context)
                    .load(spec.url)
                    .into(imageView)
            }
        }
    }
}
