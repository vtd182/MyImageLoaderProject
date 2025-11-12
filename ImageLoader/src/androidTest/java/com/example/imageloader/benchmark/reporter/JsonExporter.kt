package com.example.imageloader.benchmark.reporter

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * JsonExporter - Export benchmark results as JSON.
 *
 * Produces machine-readable JSON format suitable for:
 * - CI/CD parsing
 * - Automated analysis
 * - Data processing pipelines
 *
 * Example output:
 * ```json
 * {
 *   "timestamp": 1699876543210,
 *   "device": {
 *     "manufacturer": "Google",
 *     "model": "Pixel 6",
 *     "androidVersion": 33
 *   },
 *   "results": {
 *     "cache": {
 *       "activeCacheHitRate": 0.12,
 *       "avgActiveCacheTime": 5
 *     }
 *   },
 *   "summary": {
 *     "overallScore": 87.5,
 *     "passedTests": 24,
 *     "failedTests": 1
 *   }
 * }
 * ```
 */
class JsonExporter(
    private val outputDir: File
) : BenchmarkExporter {
    
    init {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
    }
    
    override fun export(result: ComprehensiveResult): String {
        val json = toJson(result)
        val filename = "benchmark-${result.timestamp}.json"
        val file = File(outputDir, filename)
        file.writeText(json.toString(2)) // Pretty print with indent 2
        return file.absolutePath
    }
    
    private fun toJson(result: ComprehensiveResult): JSONObject {
        return JSONObject().apply {
            put("timestamp", result.timestamp)
            put("device", deviceInfoToJson(result.deviceInfo))
            put("results", resultsToJson(result))
            put("summary", summaryToJson(result.summary))
        }
    }
    
    private fun deviceInfoToJson(info: DeviceInfo): JSONObject {
        return JSONObject().apply {
            put("manufacturer", info.manufacturer)
            put("model", info.model)
            put("androidVersion", info.androidVersion)
            put("androidRelease", info.androidRelease)
            put("cpuAbi", info.cpuAbi)
            put("totalMemoryMB", info.totalMemoryMB)
            put("availableMemoryMB", info.availableMemoryMB)
            put("screenDensity", info.screenDensity)
            put("screenResolution", info.screenResolution)
            put("isEmulator", info.isEmulator)
        }
    }
    
    private fun resultsToJson(result: ComprehensiveResult): JSONObject {
        return JSONObject().apply {
            result.cacheResult?.let { put("cache", cacheResultToJson(it)) }
            result.decodeResult?.let { put("decode", decodeResultToJson(it)) }
            result.transformResult?.let { put("transform", transformResultToJson(it)) }
            result.memoryResult?.let { put("memory", memoryResultToJson(it)) }
            result.scrollResult?.let { put("scroll", scrollResultToJson(it)) }
            result.comparisonResult?.let { put("comparison", comparisonResultToJson(it)) }
        }
    }
    
    private fun cacheResultToJson(cache: CacheBenchmarkResult): JSONObject {
        return JSONObject().apply {
            put("activeCacheCount", cache.activeCacheCount)
            put("memoryCacheCount", cache.memoryCacheCount)
            put("diskCacheCount", cache.diskCacheCount)
            put("networkCount", cache.networkCount)
            put("totalRequests", cache.totalRequests)
            put("activeCacheHitRate", cache.activeCacheHitRate)
            put("memoryCacheHitRate", cache.memoryCacheHitRate)
            put("diskCacheHitRate", cache.diskCacheHitRate)
            put("networkRate", cache.networkRate)
            put("avgActiveCacheTime", cache.avgActiveCacheTime)
            put("avgMemoryCacheTime", cache.avgMemoryCacheTime)
            put("avgDiskCacheTime", cache.avgDiskCacheTime)
            put("avgNetworkTime", cache.avgNetworkTime)
            put("cacheEfficiency", cache.cacheEfficiency)
        }
    }
    
    private fun decodeResultToJson(decode: DecodeBenchmarkResult): JSONObject {
        return JSONObject().apply {
            put("avgDecodeTimeTiny", decode.avgDecodeTimeTiny)
            put("avgDecodeTimeSmall", decode.avgDecodeTimeSmall)
            put("avgDecodeTimeMedium", decode.avgDecodeTimeMedium)
            put("avgDecodeTimeLarge", decode.avgDecodeTimeLarge)
            put("avgDecodeTimeHuge", decode.avgDecodeTimeHuge)
            put("bitmapPoolHitRate", decode.bitmapPoolHitRate)
            put("allocationsWithPool", decode.allocationsWithPool)
            put("allocationsWithoutPool", decode.allocationsWithoutPool)
            put("allocationReduction", decode.allocationReduction)
            put("gcCountWithPool", decode.gcCountWithPool)
            put("avgMemoryUsedMB", decode.avgMemoryUsedMB)
            put("downsamplingAccuracy", decode.downsamplingAccuracy)
        }
    }
    
    private fun transformResultToJson(transform: TransformBenchmarkResult): JSONObject {
        return JSONObject().apply {
            put("avgTransformTime", transform.avgTransformTime)
            put("maxTransformTime", transform.maxTransformTime)
            put("poolReuseRate", transform.poolReuseRate)
            put("avgTimeWithPool", transform.avgTimeWithPool)
            put("avgTimeWithoutPool", transform.avgTimeWithoutPool)
            put("timeReduction", transform.timeReduction)
            put("intermediateAllocations", transform.intermediateAllocations)
            put("transformCacheHitRate", transform.transformCacheHitRate)
            put("memoryPeakMB", transform.memoryPeakMB)
        }
    }
    
    private fun memoryResultToJson(memory: MemoryBenchmarkResult): JSONObject {
        return JSONObject().apply {
            put("initialHeapMB", memory.initialHeapMB)
            put("peakHeapMB", memory.peakHeapMB)
            put("steadyStateHeapMB", memory.steadyStateHeapMB)
            put("heapGrowthRate", memory.heapGrowthRate)
            put("bitmapPoolSizeMB", memory.bitmapPoolSizeMB)
            put("bitmapPoolUtilization", memory.bitmapPoolUtilization)
            put("leakDetected", memory.leakDetected)
            put("leakRateMBPerCycle", memory.leakRateMBPerCycle)
            put("gcCount", memory.gcCount)
            put("totalGCTimeMs", memory.totalGCTimeMs)
            put("avgGCPauseMs", memory.avgGCPauseMs)
            put("memoryCacheEvictions", memory.memoryCacheEvictions)
            put("lruCorrectnessScore", memory.lruCorrectnessScore)
        }
    }
    
    private fun scrollResultToJson(scroll: ScrollBenchmarkResult): JSONObject {
        return JSONObject().apply {
            put("avgFPS", scroll.avgFPS)
            put("minFPS", scroll.minFPS)
            put("jankCount", scroll.jankCount)
            put("droppedFrames", scroll.droppedFrames)
            put("highPriorityAvgTime", scroll.highPriorityAvgTime)
            put("normalPriorityAvgTime", scroll.normalPriorityAvgTime)
            put("lowPriorityAvgTime", scroll.lowPriorityAvgTime)
            put("priorityEffectiveness", scroll.priorityEffectiveness)
            put("pauseCancellationTime", scroll.pauseCancellationTime)
            put("requestsCancelled", scroll.requestsCancelled)
            put("flingFPS", scroll.flingFPS)
            put("memoryStability", scroll.memoryStability)
            put("peakMemoryDuringScrollMB", scroll.peakMemoryDuringScrollMB)
        }
    }
    
    private fun comparisonResultToJson(comparison: ComparisonBenchmarkResult): JSONObject {
        return JSONObject().apply {
            put("results", JSONArray().apply {
                comparison.results.forEach { put(comparisonItemToJson(it)) }
            })
            put("winner", JSONObject(comparison.winner))
        }
    }
    
    private fun comparisonItemToJson(item: ComparisonResult): JSONObject {
        return JSONObject().apply {
            put("library", item.library)
            put("coldStartTimeMs", item.coldStartTimeMs)
            put("cacheHitRate", item.cacheHitRate)
            put("secondLoadTimeMs", item.secondLoadTimeMs)
            put("peakMemoryMB", item.peakMemoryMB)
            put("steadyStateMemoryMB", item.steadyStateMemoryMB)
            put("apkSizeKB", item.apkSizeKB)
            item.batteryDrainMAh?.let { put("batteryDrainMAh", it) }
        }
    }
    
    private fun summaryToJson(summary: BenchmarkSummary): JSONObject {
        return JSONObject().apply {
            put("totalTests", summary.totalTests)
            put("passedTests", summary.passedTests)
            put("failedTests", summary.failedTests)
            put("totalDurationMs", summary.totalDurationMs)
            put("overallScore", summary.overallScore)
            put("passRate", summary.passRate)
            put("highlights", JSONArray(summary.highlights))
            put("regressions", JSONArray(summary.regressions))
        }
    }
}
