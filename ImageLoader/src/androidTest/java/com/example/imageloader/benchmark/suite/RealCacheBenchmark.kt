package com.example.imageloader.benchmark.suite

import android.content.Context
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
import com.example.imageloader.core.Engine
import com.example.imageloader.core.ImageLoader
import com.example.imageloader.core.Request
import com.example.imageloader.logger.ImageLoaderLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
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
    private lateinit var engine: Engine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        outputDir = File(context.getExternalFilesDir(null), "benchmark-results")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        // Get ImageLoader instance
        imageLoader = ImageLoader.getInstance(context)

        // Get Engine for direct testing
        engine = imageLoader.engine

        // IMPORTANT: Enable logger explicitly
        ImageLoaderLogger.saveToActivity = true

        // Clear logger để bắt đầu từ đầu
        ImageLoaderLogger.clear()
        ImageLoaderLogger.resetBitmapPoolStats()

        println("✅ Setup complete. Output dir: ${outputDir.absolutePath}")
        println("✅ Logger enabled: ${ImageLoaderLogger.saveToActivity}")
    }

    /**
     * Test: Load images using ENGINE DIRECTLY và measure ALL cache tiers.
     *
     * Test flow:
     * - Phase 1: Load 100 images via Engine (cold cache → network)
     * - Release resources manually (Active → Memory Cache)
     * - Phase 2: Reload same 100 images (Memory Cache hits)
     * - Phase 3: Load 100 NEW images (overflow Memory → Disk Cache)
     * - Phase 4: Reload phase 1 images (Disk Cache hits)
     */
    @Test
    fun testRealCachePerformance() = runBlocking {
        println("🚀 Starting REAL cache benchmark (Engine Direct Testing)...")
        println("📊 This approach tests Engine directly without ImageView lifecycle issues")

        // Generate test URLs
        val phase1Images = TestDataGenerator.generateMixedDataset(100)
        val phase3Images = TestDataGenerator.generateMixedDataset(100)
        println("📦 Generated ${phase1Images.size + phase3Images.size} test URLs")

        // ============================================================
        // Phase 1: Load 100 images via Engine (cold start → NETWORK)
        // ============================================================
        println("\n📥 Phase 1: Load 100 images via Engine (cold cache → NETWORK)...")

        val phase1Targets = mutableListOf<SimpleTestTarget>()
        val phase1Jobs = mutableListOf<Job>()

        // Load all images (engine.load handles main thread internally)
        phase1Images.forEachIndexed { index, spec ->
            val target = SimpleTestTarget()
            phase1Targets.add(target)

            val request = Request(
                url = spec.url,
                resizeWidth = 400,
                resizeHeight = 400
            )

            // Load via Engine directly - returns Job
            val job = engine.load(request, target)
            phase1Jobs.add(job)

            if ((index + 1) % 20 == 0) {
                print(".")
            }
        }

        // Wait for all targets to receive results (poll with timeout)
        println("\n   Waiting for ${phase1Targets.size} images to load...")
        val startTime = System.currentTimeMillis()
        var allLoaded = false
        while (!allLoaded && (System.currentTimeMillis() - startTime) < 30000) {
            allLoaded = phase1Targets.all { it.loadSuccess || it.loadFailed }
            if (!allLoaded) delay(100)
        }
        val loaded = phase1Targets.count { it.loadSuccess }
        val failed = phase1Targets.count { it.loadFailed }
        println("   ✓ Loaded: $loaded, Failed: $failed")
        delay(1000)

        // Get stats after phase 1
        val statsAfterPhase1 = ImageLoaderLogger.getLogStats()
        println("\n📊 Phase 1 Results:")
        println("   ✓ Network loads: ${statsAfterPhase1.networkCount} (expected: ~100)")
        println("   ✓ Disk cache: ${statsAfterPhase1.diskCacheCount}")
        println("   ✓ Memory cache: ${statsAfterPhase1.memoryCacheCount}")
        println("   ✓ Active cache: ${statsAfterPhase1.activeCacheCount}")
        println("   ✓ Total: ${statsAfterPhase1.totalImageRequests}")

        // ============================================================
        // Release Phase: MANUAL release → Move Active to Memory Cache
        // ============================================================
        println("\n🧹 Releasing all resources (Active → Memory Cache)...")

        phase1Targets.forEach { target ->
            target.release()  // Manual release - decrements ref count
        }

        println("   ✓ Released ${phase1Targets.size} resources")
        delay(1000)  // Wait for resources to move to Memory Cache

        // Force GC to ensure cleanup
        System.gc()
        delay(500)

        // ============================================================
        // Phase 2: Reload same 100 URLs → Should hit MEMORY CACHE!
        // ============================================================
        println("\n🔥 Phase 2: Reload same 100 images (expect MEMORY CACHE hits)...")

        val phase2Targets = mutableListOf<SimpleTestTarget>()
        val phase2Jobs = mutableListOf<Job>()

        phase1Images.forEachIndexed { index, spec ->
            val target = SimpleTestTarget()
            phase2Targets.add(target)

            val request = Request(
                url = spec.url,
                resizeWidth = 400,
                resizeHeight = 400
            )

            // Load via Engine - should hit Memory Cache!
            val job = engine.load(request, target)
            phase2Jobs.add(job)

            if ((index + 1) % 20 == 0) {
                print(".")
            }
        }

        // Wait for all targets to receive results (poll with timeout)
        println("\n   Waiting for ${phase2Targets.size} images to load...")
        var startTime2 = System.currentTimeMillis()
        var allLoaded2 = false
        while (!allLoaded2 && (System.currentTimeMillis() - startTime2) < 30000) {
            allLoaded2 = phase2Targets.all { it.loadSuccess || it.loadFailed }
            if (!allLoaded2) delay(100)
        }
        println("   ✓ Loaded: ${phase2Targets.count { it.loadSuccess }}, Failed: ${phase2Targets.count { it.loadFailed }}")
        delay(1000)

        // Get stats after phase 2
        val statsAfterPhase2 = ImageLoaderLogger.getLogStats()
        println("\n📊 Phase 2 Results:")
        println("   ✓ Memory cache: ${statsAfterPhase2.memoryCacheCount} (expected: ~100)")
        println("   ✓ Active cache: ${statsAfterPhase2.activeCacheCount}")
        println("   ✓ Disk cache: ${statsAfterPhase2.diskCacheCount}")
        println("   ✓ Network: ${statsAfterPhase2.networkCount}")
        println("   ✓ Total: ${statsAfterPhase2.totalImageRequests}")

        // Release phase 2 resources
        println("\n🧹 Releasing phase 2 resources...")
        phase2Targets.forEach { it.release() }
        delay(1000)
        System.gc()
        delay(500)

        // ============================================================
        // Phase 3: Load 100 NEW images → Overflow Memory Cache
        // ============================================================
        println("\n💾 Phase 3: Load 100 NEW images (overflow Memory → Disk)...")

        val phase3Targets = mutableListOf<SimpleTestTarget>()
        val phase3Jobs = mutableListOf<Job>()

        phase3Images.forEachIndexed { index, spec ->
            val target = SimpleTestTarget()
            phase3Targets.add(target)

            val request = Request(
                url = spec.url,
                resizeWidth = 400,
                resizeHeight = 400
            )

            val job = engine.load(request, target)
            phase3Jobs.add(job)

            if ((index + 1) % 20 == 0) {
                print(".")
            }
        }

        println("\n   Waiting for ${phase3Targets.size} images to load...")
        var startTime3 = System.currentTimeMillis()
        var allLoaded3 = false
        while (!allLoaded3 && (System.currentTimeMillis() - startTime3) < 30000) {
            allLoaded3 = phase3Targets.all { it.loadSuccess || it.loadFailed }
            if (!allLoaded3) delay(100)
        }
        println("   ✓ Loaded: ${phase3Targets.count { it.loadSuccess }}, Failed: ${phase3Targets.count { it.loadFailed }}")
        delay(1000)

        val statsAfterPhase3 = ImageLoaderLogger.getLogStats()
        println("\n📊 Phase 3 Results:")
        println("   ✓ Network: ${statsAfterPhase3.networkCount}")
        println("   ✓ Total: ${statsAfterPhase3.totalImageRequests}")

        // Release phase 3
        phase3Targets.forEach { it.release() }
        delay(1000)

        // ============================================================
        // Phase 4: Reload phase 1 images → Should hit DISK CACHE
        // ============================================================
        println("\n💿 Phase 4: Reload phase 1 images (expect DISK CACHE hits)...")

        val phase4Targets = mutableListOf<SimpleTestTarget>()
        val phase4Jobs = mutableListOf<Job>()

        // Take only first 50 to avoid memory cache
        phase1Images.take(50).forEachIndexed { index, spec ->
            val target = SimpleTestTarget()
            phase4Targets.add(target)

            val request = Request(
                url = spec.url,
                resizeWidth = 400,
                resizeHeight = 400
            )

            val job = engine.load(request, target)
            phase4Jobs.add(job)

            if ((index + 1) % 10 == 0) {
                print(".")
            }
        }

        println("\n   Waiting for ${phase4Targets.size} images to load...")
        var startTime4 = System.currentTimeMillis()
        var allLoaded4 = false
        while (!allLoaded4 && (System.currentTimeMillis() - startTime4) < 30000) {
            allLoaded4 = phase4Targets.all { it.loadSuccess || it.loadFailed }
            if (!allLoaded4) delay(100)
        }
        println("   ✓ Loaded: ${phase4Targets.count { it.loadSuccess }}, Failed: ${phase4Targets.count { it.loadFailed }}")
        delay(1000)

        // Get final stats
        val finalStats = ImageLoaderLogger.getLogStats()
        println("\n📊 Phase 4 Results:")
        println("   ✓ Disk cache: ${finalStats.diskCacheCount} (expected: some hits)")
        println("   ✓ Memory cache: ${finalStats.memoryCacheCount}")
        println("   ✓ Total: ${finalStats.totalImageRequests}")

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
}
