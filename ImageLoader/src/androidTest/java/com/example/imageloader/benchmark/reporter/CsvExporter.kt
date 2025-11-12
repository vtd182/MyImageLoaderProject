package com.example.imageloader.benchmark.reporter

import com.example.imageloader.benchmark.BenchmarkConfig
import java.io.File

/**
 * CsvExporter - Export benchmark results as CSV.
 *
 * Produces tabular CSV format suitable for:
 * - Excel/Google Sheets analysis
 * - Creating custom charts
 * - Statistical analysis
 *
 * Example output:
 * ```csv
 * Category,Metric,Value,Unit,Threshold,Status
 * Cache,Active Cache Hit Rate,12.5,%,N/A,INFO
 * Cache,Memory Cache Hit Rate,43.2,%,>40%,PASS
 * Cache,Avg Active Cache Time,5,ms,<10ms,PASS
 * ...
 * ```
 */
class CsvExporter(
    private val outputDir: File
) : BenchmarkExporter {
    
    init {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
    }
    
    override fun export(result: ComprehensiveResult): String {
        val csv = buildCsv(result)
        val filename = "benchmark-${result.timestamp}.csv"
        val file = File(outputDir, filename)
        file.writeText(csv)
        return file.absolutePath
    }
    
    private fun buildCsv(result: ComprehensiveResult): String {
        return buildString {
            appendLine("Category,Metric,Value,Unit,Threshold,Status")
            
            appendLine()
            appendLine("# Device Information")
            appendDeviceInfo(result.deviceInfo)
            
            appendLine()
            appendLine("# Summary")
            appendSummary(result.summary)
            
            result.cacheResult?.let {
                appendLine()
                appendLine("# Cache Performance")
                appendCacheResult(it)
            }
            
            result.decodeResult?.let {
                appendLine()
                appendLine("# Decode Performance")
                appendDecodeResult(it)
            }
            
            result.transformResult?.let {
                appendLine()
                appendLine("# Transform Performance")
                appendTransformResult(it)
            }
            
            result.memoryResult?.let {
                appendLine()
                appendLine("# Memory Analysis")
                appendMemoryResult(it)
            }
            
            result.scrollResult?.let {
                appendLine()
                appendLine("# Scroll Performance")
                appendScrollResult(it)
            }
        }
    }
    
    private fun StringBuilder.appendDeviceInfo(info: DeviceInfo) {
        appendRow("Device", "Manufacturer", info.manufacturer, "", "", "INFO")
        appendRow("Device", "Model", info.model, "", "", "INFO")
        appendRow("Device", "Android Version", info.androidVersion, "", "", "INFO")
        appendRow("Device", "Android Release", info.androidRelease, "", "", "INFO")
        appendRow("Device", "CPU ABI", info.cpuAbi, "", "", "INFO")
        appendRow("Device", "Total Memory", info.totalMemoryMB, "MB", "", "INFO")
        appendRow("Device", "Available Memory", info.availableMemoryMB, "MB", "", "INFO")
        appendRow("Device", "Screen Density", info.screenDensity, "dpi", "", "INFO")
        appendRow("Device", "Screen Resolution", info.screenResolution, "", "", "INFO")
        appendRow("Device", "Is Emulator", info.isEmulator, "", "", "INFO")
    }
    
    private fun StringBuilder.appendSummary(summary: BenchmarkSummary) {
        appendRow("Summary", "Total Tests", summary.totalTests, "", "", "INFO")
        appendRow("Summary", "Passed Tests", summary.passedTests, "", "", "PASS")
        appendRow("Summary", "Failed Tests", summary.failedTests, "", "", if (summary.failedTests > 0) "FAIL" else "PASS")
        appendRow("Summary", "Pass Rate", "%.1f".format(summary.passRate * 100), "%", "", if (summary.passRate >= 0.8) "PASS" else "FAIL")
        appendRow("Summary", "Overall Score", "%.1f".format(summary.overallScore), "/100", ">80", if (summary.overallScore >= 80) "PASS" else "FAIL")
        appendRow("Summary", "Total Duration", summary.totalDurationMs, "ms", "", "INFO")
    }
    
    private fun StringBuilder.appendCacheResult(cache: CacheBenchmarkResult) {
        val thresholds = BenchmarkConfig.Thresholds
        
        appendRow("Cache", "Total Requests", cache.totalRequests, "", "", "INFO")
        appendRow("Cache", "Active Cache Count", cache.activeCacheCount, "", "", "INFO")
        appendRow("Cache", "Memory Cache Count", cache.memoryCacheCount, "", "", "INFO")
        appendRow("Cache", "Disk Cache Count", cache.diskCacheCount, "", "", "INFO")
        appendRow("Cache", "Network Count", cache.networkCount, "", "", "INFO")
        
        appendRow("Cache", "Active Cache Hit Rate", "%.1f".format(cache.activeCacheHitRate * 100), "%", "", "INFO")
        appendRow("Cache", "Memory Cache Hit Rate", "%.1f".format(cache.memoryCacheHitRate * 100), "%", ">${thresholds.MIN_MEMORY_HIT_RATE * 100}%", 
            if (cache.memoryCacheHitRate >= thresholds.MIN_MEMORY_HIT_RATE) "PASS" else "FAIL")
        appendRow("Cache", "Disk Cache Hit Rate", "%.1f".format(cache.diskCacheHitRate * 100), "%", "", "INFO")
        appendRow("Cache", "Network Rate", "%.1f".format(cache.networkRate * 100), "%", "", "INFO")
        appendRow("Cache", "Cache Efficiency", "%.1f".format(cache.cacheEfficiency * 100), "%", ">${thresholds.MIN_CACHE_HIT_RATE * 100}%",
            if (cache.cacheEfficiency >= thresholds.MIN_CACHE_HIT_RATE) "PASS" else "FAIL")
        
        appendRow("Cache", "Avg Active Cache Time", cache.avgActiveCacheTime, "ms", "<${thresholds.MAX_CACHE_LOOKUP_MS}ms",
            if (cache.avgActiveCacheTime <= thresholds.MAX_CACHE_LOOKUP_MS) "PASS" else "FAIL")
        appendRow("Cache", "Avg Memory Cache Time", cache.avgMemoryCacheTime, "ms", "<${thresholds.MAX_TOTAL_TIME_CACHED_MS}ms",
            if (cache.avgMemoryCacheTime <= thresholds.MAX_TOTAL_TIME_CACHED_MS) "PASS" else "FAIL")
        appendRow("Cache", "Avg Disk Cache Time", cache.avgDiskCacheTime, "ms", "<${thresholds.MAX_DISK_READ_MS}ms",
            if (cache.avgDiskCacheTime <= thresholds.MAX_DISK_READ_MS) "PASS" else "FAIL")
        appendRow("Cache", "Avg Network Time", cache.avgNetworkTime, "ms", "<${thresholds.MAX_TOTAL_TIME_NETWORK_MS}ms",
            if (cache.avgNetworkTime <= thresholds.MAX_TOTAL_TIME_NETWORK_MS) "PASS" else "FAIL")
    }
    
    private fun StringBuilder.appendDecodeResult(decode: DecodeBenchmarkResult) {
        val thresholds = BenchmarkConfig.Thresholds
        
        appendRow("Decode", "Avg Decode Time (Tiny)", decode.avgDecodeTimeTiny, "ms", "", "INFO")
        appendRow("Decode", "Avg Decode Time (Small)", decode.avgDecodeTimeSmall, "ms", "", "INFO")
        appendRow("Decode", "Avg Decode Time (Medium)", decode.avgDecodeTimeMedium, "ms", "<${thresholds.MAX_DECODE_TIME_MS}ms",
            if (decode.avgDecodeTimeMedium <= thresholds.MAX_DECODE_TIME_MS) "PASS" else "FAIL")
        appendRow("Decode", "Avg Decode Time (Large)", decode.avgDecodeTimeLarge, "ms", "", "INFO")
        appendRow("Decode", "Avg Decode Time (Huge)", decode.avgDecodeTimeHuge, "ms", "", "INFO")
        
        appendRow("Decode", "Bitmap Pool Hit Rate", "%.1f".format(decode.bitmapPoolHitRate * 100), "%", ">50%",
            if (decode.bitmapPoolHitRate >= 0.5) "PASS" else "FAIL")
        appendRow("Decode", "Allocations With Pool", decode.allocationsWithPool, "", "", "INFO")
        appendRow("Decode", "Allocations Without Pool", decode.allocationsWithoutPool, "", "", "INFO")
        appendRow("Decode", "Allocation Reduction", "%.1f".format(decode.allocationReduction * 100), "%", ">30%",
            if (decode.allocationReduction >= 0.3) "PASS" else "FAIL")
        appendRow("Decode", "GC Count With Pool", decode.gcCountWithPool, "", "<10", 
            if (decode.gcCountWithPool < 10) "PASS" else "FAIL")
        appendRow("Decode", "Avg Memory Used", "%.1f".format(decode.avgMemoryUsedMB), "MB", "", "INFO")
        appendRow("Decode", "Downsampling Accuracy", "%.1f".format(decode.downsamplingAccuracy * 100), "%", ">90%",
            if (decode.downsamplingAccuracy >= 0.9) "PASS" else "FAIL")
    }
    
    private fun StringBuilder.appendTransformResult(transform: TransformBenchmarkResult) {
        appendRow("Transform", "Avg Transform Time", transform.avgTransformTime, "ms", "<50ms",
            if (transform.avgTransformTime <= 50) "PASS" else "FAIL")
        appendRow("Transform", "Max Transform Time", transform.maxTransformTime, "ms", "<200ms",
            if (transform.maxTransformTime <= 200) "PASS" else "FAIL")
        appendRow("Transform", "Pool Reuse Rate", "%.1f".format(transform.poolReuseRate * 100), "%", ">40%",
            if (transform.poolReuseRate >= 0.4) "PASS" else "FAIL")
        appendRow("Transform", "Avg Time With Pool", transform.avgTimeWithPool, "ms", "", "INFO")
        appendRow("Transform", "Avg Time Without Pool", transform.avgTimeWithoutPool, "ms", "", "INFO")
        appendRow("Transform", "Time Reduction", "%.1f".format(transform.timeReduction * 100), "%", ">20%",
            if (transform.timeReduction >= 0.2) "PASS" else "FAIL")
        appendRow("Transform", "Intermediate Allocations", transform.intermediateAllocations, "", "<100",
            if (transform.intermediateAllocations < 100) "PASS" else "FAIL")
        appendRow("Transform", "Transform Cache Hit Rate", "%.1f".format(transform.transformCacheHitRate * 100), "%", ">80%",
            if (transform.transformCacheHitRate >= 0.8) "PASS" else "FAIL")
        appendRow("Transform", "Memory Peak", "%.1f".format(transform.memoryPeakMB), "MB", "", "INFO")
    }
    
    private fun StringBuilder.appendMemoryResult(memory: MemoryBenchmarkResult) {
        val thresholds = BenchmarkConfig.Thresholds
        
        appendRow("Memory", "Initial Heap", "%.1f".format(memory.initialHeapMB), "MB", "", "INFO")
        appendRow("Memory", "Peak Heap", "%.1f".format(memory.peakHeapMB), "MB", "<${thresholds.MAX_HEAP_SIZE_MB}MB",
            if (memory.peakHeapMB <= thresholds.MAX_HEAP_SIZE_MB) "PASS" else "FAIL")
        appendRow("Memory", "Steady State Heap", "%.1f".format(memory.steadyStateHeapMB), "MB", "", "INFO")
        appendRow("Memory", "Heap Growth Rate", "%.2f".format(memory.heapGrowthRate), "MB/100img", "<1.0",
            if (memory.heapGrowthRate < 1.0) "PASS" else "FAIL")
        appendRow("Memory", "Bitmap Pool Size", "%.1f".format(memory.bitmapPoolSizeMB), "MB", "<${thresholds.MAX_BITMAP_POOL_MB}MB",
            if (memory.bitmapPoolSizeMB <= thresholds.MAX_BITMAP_POOL_MB) "PASS" else "FAIL")
        appendRow("Memory", "Bitmap Pool Utilization", "%.1f".format(memory.bitmapPoolUtilization * 100), "%", "", "INFO")
        appendRow("Memory", "Leak Detected", memory.leakDetected, "", "false",
            if (!memory.leakDetected) "PASS" else "FAIL")
        appendRow("Memory", "Leak Rate", "%.3f".format(memory.leakRateMBPerCycle), "MB/cycle", "<0.1",
            if (memory.leakRateMBPerCycle < 0.1) "PASS" else "FAIL")
        appendRow("Memory", "GC Count", memory.gcCount, "", "<10",
            if (memory.gcCount < 10) "PASS" else "FAIL")
        appendRow("Memory", "Total GC Time", memory.totalGCTimeMs, "ms", "", "INFO")
        appendRow("Memory", "Avg GC Pause", "%.2f".format(memory.avgGCPauseMs), "ms", "<5ms",
            if (memory.avgGCPauseMs < 5) "PASS" else "FAIL")
        appendRow("Memory", "Memory Cache Evictions", memory.memoryCacheEvictions, "", "", "INFO")
        appendRow("Memory", "LRU Correctness Score", "%.1f".format(memory.lruCorrectnessScore * 100), "%", ">95%",
            if (memory.lruCorrectnessScore >= 0.95) "PASS" else "FAIL")
    }
    
    private fun StringBuilder.appendScrollResult(scroll: ScrollBenchmarkResult) {
        val thresholds = BenchmarkConfig.Thresholds
        
        appendRow("Scroll", "Avg FPS", "%.1f".format(scroll.avgFPS), "fps", ">${thresholds.MIN_FPS}",
            if (scroll.avgFPS >= thresholds.MIN_FPS) "PASS" else "FAIL")
        appendRow("Scroll", "Min FPS", "%.1f".format(scroll.minFPS), "fps", ">${thresholds.MIN_FPS}",
            if (scroll.minFPS >= thresholds.MIN_FPS) "PASS" else "FAIL")
        appendRow("Scroll", "Jank Count", scroll.jankCount, "", "<${thresholds.MAX_JANK_COUNT}",
            if (scroll.jankCount <= thresholds.MAX_JANK_COUNT) "PASS" else "FAIL")
        appendRow("Scroll", "Dropped Frames", scroll.droppedFrames, "", "<10",
            if (scroll.droppedFrames < 10) "PASS" else "FAIL")
        appendRow("Scroll", "High Priority Avg Time", scroll.highPriorityAvgTime, "ms", "", "INFO")
        appendRow("Scroll", "Normal Priority Avg Time", scroll.normalPriorityAvgTime, "ms", "", "INFO")
        appendRow("Scroll", "Low Priority Avg Time", scroll.lowPriorityAvgTime, "ms", "", "INFO")
        appendRow("Scroll", "Priority Effectiveness", "%.1f".format(scroll.priorityEffectiveness), "%", ">30%",
            if (scroll.priorityEffectiveness >= 30) "PASS" else "FAIL")
        appendRow("Scroll", "Pause Cancellation Time", scroll.pauseCancellationTime, "ms", "<50ms",
            if (scroll.pauseCancellationTime < 50) "PASS" else "FAIL")
        appendRow("Scroll", "Requests Cancelled", scroll.requestsCancelled, "", "", "INFO")
        appendRow("Scroll", "Fling FPS", "%.1f".format(scroll.flingFPS), "fps", ">${thresholds.MIN_FPS}",
            if (scroll.flingFPS >= thresholds.MIN_FPS) "PASS" else "FAIL")
        appendRow("Scroll", "Memory Stability", "%.2f".format(scroll.memoryStability), "", "<5.0",
            if (scroll.memoryStability < 5.0) "PASS" else "FAIL")
        appendRow("Scroll", "Peak Memory During Scroll", "%.1f".format(scroll.peakMemoryDuringScrollMB), "MB", "", "INFO")
    }
    
    private fun StringBuilder.appendRow(
        category: String,
        metric: String,
        value: Any,
        unit: String,
        threshold: String,
        status: String
    ) {
        appendLine("$category,$metric,$value,$unit,$threshold,$status")
    }
}
