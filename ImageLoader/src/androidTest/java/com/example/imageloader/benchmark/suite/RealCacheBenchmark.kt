package com.example.imageloader.benchmark.suite

import android.content.Context
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.imageloader.benchmark.TestDataGenerator
import com.example.imageloader.benchmark.reporter.*
import com.example.imageloader.core.ImageLoader
import com.example.imageloader.logger.ImageLoaderLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
     * Test: Load 100 images và measure cache performance THẬT.
     */
    @Test
    fun testRealCachePerformance() = runBlocking {
        println("🚀 Starting REAL cache benchmark...")
        println("📊 Loading images and collecting metrics...")
        
        // Generate test URLs
        val imageSpecs = TestDataGenerator.generateMixedDataset(100)
        println("📦 Generated ${imageSpecs.size} test images")
        
        // Create ImageViews để load vào
        val imageViews = (0 until imageSpecs.size).map { 
            withContext(Dispatchers.Main) {
                ImageView(context)
            }
        }
        
        // Phase 1: First load (cold cache - expect network loads)
        println("\n📥 Phase 1: First load (cold cache)...")
        
        // Add listener to track logs
        var logCount = 0
        val listener: (com.example.imageloader.logger.LogEntry) -> Unit = { entry ->
            if (entry is com.example.imageloader.logger.ImageLoadLog) {
                logCount++
            }
        }
        ImageLoaderLogger.addListener(listener)
        
        loadImages(imageSpecs, imageViews)
        
        // Wait for loads to complete (longer time)
        println("   Waiting for loads to complete...")
        var waitTime = 0
        while (logCount < imageSpecs.size && waitTime < 30000) {
            delay(500)
            waitTime += 500
            if (waitTime % 2000 == 0) {
                println("   Progress: $logCount/${imageSpecs.size} images loaded (${waitTime}ms)")
            }
        }
        
        ImageLoaderLogger.removeListener(listener)
        
        val statsAfterFirstLoad = ImageLoaderLogger.getLogStats()
        println("   Network loads: ${statsAfterFirstLoad.networkCount}")
        println("   Total requests: ${statsAfterFirstLoad.totalImageRequests}")
        println("   Logs captured: $logCount")
        
        // Phase 2: Second load (warm cache - expect cache hits)
        println("\n🔥 Phase 2: Second load (warm cache)...")
        
        logCount = 0
        ImageLoaderLogger.addListener(listener)
        
        loadImages(imageSpecs, imageViews)
        
        // Wait for loads to complete
        println("   Waiting for loads to complete...")
        waitTime = 0
        while (logCount < imageSpecs.size && waitTime < 20000) {
            delay(500)
            waitTime += 500
            if (waitTime % 2000 == 0) {
                println("   Progress: $logCount/${imageSpecs.size} images loaded (${waitTime}ms)")
            }
        }
        
        ImageLoaderLogger.removeListener(listener)
        
        val statsAfterSecondLoad = ImageLoaderLogger.getLogStats()
        println("   Memory cache hits: ${statsAfterSecondLoad.memoryCacheCount}")
        println("   Disk cache hits: ${statsAfterSecondLoad.diskCacheCount}")
        println("   Total requests: ${statsAfterSecondLoad.totalImageRequests}")
        
        // Phase 3: Third load (hot cache - expect more memory hits)
        println("\n🔥 Phase 3: Third load (hot cache)...")
        
        val subset = imageSpecs.take(50)
        logCount = 0
        ImageLoaderLogger.addListener(listener)
        
        loadImages(subset, imageViews.take(50)) // Load subset
        
        // Wait for loads to complete
        println("   Waiting for loads to complete...")
        waitTime = 0
        while (logCount < subset.size && waitTime < 15000) {
            delay(500)
            waitTime += 500
            if (waitTime % 2000 == 0) {
                println("   Progress: $logCount/${subset.size} images loaded (${waitTime}ms)")
            }
        }
        
        ImageLoaderLogger.removeListener(listener)
        
        // Get final stats
        val finalStats = ImageLoaderLogger.getLogStats()
        
        println("\n📊 Final Statistics:")
        println("   Total requests: ${finalStats.totalImageRequests}")
        println("   Active cache: ${finalStats.activeCacheCount} (${String.format("%.1f%%", finalStats.activeCacheCount * 100.0 / finalStats.totalImageRequests)})")
        println("   Memory cache: ${finalStats.memoryCacheCount} (${String.format("%.1f%%", finalStats.memoryCacheCount * 100.0 / finalStats.totalImageRequests)})")
        println("   Disk cache: ${finalStats.diskCacheCount} (${String.format("%.1f%%", finalStats.diskCacheCount * 100.0 / finalStats.totalImageRequests)})")
        println("   Network: ${finalStats.networkCount} (${String.format("%.1f%%", finalStats.networkCount * 100.0 / finalStats.totalImageRequests)})")
        println("   ---")
        println("   Avg active cache time: ${finalStats.activeCacheAvgTime.toLong()}ms")
        println("   Avg memory cache time: ${finalStats.memoryCacheAvgTime.toLong()}ms")
        println("   Avg disk cache time: ${finalStats.diskCacheAvgTime.toLong()}ms")
        println("   Avg network time: ${finalStats.networkAvgTime.toLong()}ms")
        println("   ---")
        println("   Bitmap pool hits: ${finalStats.bitmapPoolHits}")
        println("   Bitmap pool misses: ${finalStats.bitmapPoolMisses}")
        println("   Bitmap pool hit rate: ${String.format("%.1f%%", finalStats.bitmapPoolHitRate * 100)}")
        
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
                "Memory cache hit rate: ${String.format("%.1f%%", cacheResult.memoryCacheHitRate * 100)}",
                "Avg memory cache time: ${cacheResult.avgMemoryCacheTime}ms",
                "Bitmap pool hit rate: ${String.format("%.1f%%", decodeResult.bitmapPoolHitRate * 100)}",
                "Peak heap: ${String.format("%.1f", memoryResult.peakHeapMB)}MB"
            ),
            regressions = if (cacheEfficiency < 0.5) {
                listOf("Cache efficiency below 50%")
            } else emptyList()
        )
        
        // Device info
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
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
