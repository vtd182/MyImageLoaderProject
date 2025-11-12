package com.example.imageloader.benchmark.reporter

import android.os.Build

/**
 * BenchmarkResult - Base result for a single benchmark test.
 *
 * Contains statistical measurements from multiple iterations.
 */
data class BenchmarkResult(
    val name: String,
    val iterations: Int,
    val mean: Double,
    val median: Double,
    val p95: Double,
    val p99: Double,
    val min: Long,
    val max: Long,
    val stdDev: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * ComprehensiveResult - Aggregated results from all benchmark suites.
 *
 * This is the top-level result object that gets exported.
 */
data class ComprehensiveResult(
    val timestamp: Long,
    val deviceInfo: DeviceInfo,
    val cacheResult: CacheBenchmarkResult? = null,
    val decodeResult: DecodeBenchmarkResult? = null,
    val transformResult: TransformBenchmarkResult? = null,
    val memoryResult: MemoryBenchmarkResult? = null,
    val scrollResult: ScrollBenchmarkResult? = null,
    val comparisonResult: ComparisonBenchmarkResult? = null,
    val summary: BenchmarkSummary
) {
    fun getCacheHitRate(): Double {
        return cacheResult?.let {
            it.activeCacheHitRate + it.memoryCacheHitRate + it.diskCacheHitRate
        } ?: 0.0
    }
    
    fun getAvgLoadTime(): Long {
        return cacheResult?.let {
            val totalRequests = it.totalRequests
            val weightedTime = 
                it.avgActiveCacheTime * it.activeCacheCount +
                it.avgMemoryCacheTime * it.memoryCacheCount +
                it.avgDiskCacheTime * it.diskCacheCount +
                it.avgNetworkTime * it.networkCount
            (weightedTime / totalRequests).toLong()
        } ?: 0
    }
}

/**
 * DeviceInfo - Information about the test device.
 */
data class DeviceInfo(
    val manufacturer: String = Build.MANUFACTURER,
    val model: String = Build.MODEL,
    val androidVersion: Int = Build.VERSION.SDK_INT,
    val androidRelease: String = Build.VERSION.RELEASE,
    val cpuAbi: String = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
    val totalMemoryMB: Long,
    val availableMemoryMB: Long,
    val screenDensity: Float,
    val screenResolution: String,
    val isEmulator: Boolean = Build.FINGERPRINT.contains("generic") || 
                              Build.PRODUCT.contains("sdk")
)

/**
 * BenchmarkSummary - High-level summary of benchmark execution.
 */
data class BenchmarkSummary(
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val totalDurationMs: Long,
    val overallScore: Double,
    val highlights: List<String>,
    val regressions: List<String>
) {
    val passRate: Double
        get() = if (totalTests > 0) passedTests / totalTests.toDouble() else 0.0
}

/**
 * CacheBenchmarkResult - Results from cache performance tests.
 */
data class CacheBenchmarkResult(
    val activeCacheCount: Int,
    val memoryCacheCount: Int,
    val diskCacheCount: Int,
    val networkCount: Int,
    val totalRequests: Int,
    val activeCacheHitRate: Double,
    val memoryCacheHitRate: Double,
    val diskCacheHitRate: Double,
    val networkRate: Double,
    val avgActiveCacheTime: Long,
    val avgMemoryCacheTime: Long,
    val avgDiskCacheTime: Long,
    val avgNetworkTime: Long,
    val cacheEfficiency: Double
) {
    companion object {
        fun fromLogStats(
            activeCacheCount: Int,
            memoryCacheCount: Int,
            diskCacheCount: Int,
            networkCount: Int,
            totalRequests: Int,
            activeCacheAvgTime: Double,
            memoryCacheAvgTime: Double,
            diskCacheAvgTime: Double,
            networkAvgTime: Double
        ): CacheBenchmarkResult {
            return CacheBenchmarkResult(
                activeCacheCount = activeCacheCount,
                memoryCacheCount = memoryCacheCount,
                diskCacheCount = diskCacheCount,
                networkCount = networkCount,
                totalRequests = totalRequests,
                activeCacheHitRate = if (totalRequests > 0) activeCacheCount / totalRequests.toDouble() else 0.0,
                memoryCacheHitRate = if (totalRequests > 0) memoryCacheCount / totalRequests.toDouble() else 0.0,
                diskCacheHitRate = if (totalRequests > 0) diskCacheCount / totalRequests.toDouble() else 0.0,
                networkRate = if (totalRequests > 0) networkCount / totalRequests.toDouble() else 0.0,
                avgActiveCacheTime = activeCacheAvgTime.toLong(),
                avgMemoryCacheTime = memoryCacheAvgTime.toLong(),
                avgDiskCacheTime = diskCacheAvgTime.toLong(),
                avgNetworkTime = networkAvgTime.toLong(),
                cacheEfficiency = if (totalRequests > 0) {
                    (activeCacheCount + memoryCacheCount + diskCacheCount) / totalRequests.toDouble()
                } else 0.0
            )
        }
    }
}

/**
 * DecodeBenchmarkResult - Results from bitmap decoding tests.
 */
data class DecodeBenchmarkResult(
    val avgDecodeTimeTiny: Long,
    val avgDecodeTimeSmall: Long,
    val avgDecodeTimeMedium: Long,
    val avgDecodeTimeLarge: Long,
    val avgDecodeTimeHuge: Long,
    val bitmapPoolHitRate: Double,
    val allocationsWithPool: Int,
    val allocationsWithoutPool: Int,
    val allocationReduction: Double,
    val gcCountWithPool: Int,
    val avgMemoryUsedMB: Double,
    val downsamplingAccuracy: Double
)

/**
 * TransformBenchmarkResult - Results from transformation tests.
 */
data class TransformBenchmarkResult(
    val avgTransformTime: Long,
    val maxTransformTime: Long,
    val poolReuseRate: Double,
    val avgTimeWithPool: Long,
    val avgTimeWithoutPool: Long,
    val timeReduction: Double,
    val intermediateAllocations: Int,
    val transformCacheHitRate: Double,
    val memoryPeakMB: Double
)

/**
 * MemoryBenchmarkResult - Results from memory usage tests.
 */
data class MemoryBenchmarkResult(
    val initialHeapMB: Double,
    val peakHeapMB: Double,
    val steadyStateHeapMB: Double,
    val heapGrowthRate: Double,
    val bitmapPoolSizeMB: Double,
    val bitmapPoolUtilization: Double,
    val leakDetected: Boolean,
    val leakRateMBPerCycle: Double,
    val gcCount: Int,
    val totalGCTimeMs: Long,
    val avgGCPauseMs: Double,
    val memoryCacheEvictions: Int,
    val lruCorrectnessScore: Double
)

/**
 * ScrollBenchmarkResult - Results from UI scroll performance tests.
 */
data class ScrollBenchmarkResult(
    val avgFPS: Double,
    val minFPS: Double,
    val jankCount: Int,
    val droppedFrames: Int,
    val highPriorityAvgTime: Long,
    val normalPriorityAvgTime: Long,
    val lowPriorityAvgTime: Long,
    val priorityEffectiveness: Double,
    val pauseCancellationTime: Long,
    val requestsCancelled: Int,
    val flingFPS: Double,
    val memoryStability: Double,
    val peakMemoryDuringScrollMB: Double
)

/**
 * ComparisonResult - Result for a single library comparison.
 */
data class ComparisonResult(
    val library: String,
    val coldStartTimeMs: Long,
    val cacheHitRate: Double,
    val secondLoadTimeMs: Long,
    val peakMemoryMB: Double,
    val steadyStateMemoryMB: Double,
    val apkSizeKB: Long,
    val batteryDrainMAh: Double? = null
)

/**
 * ComparisonBenchmarkResult - Results comparing multiple libraries.
 */
data class ComparisonBenchmarkResult(
    val results: List<ComparisonResult>,
    val winner: Map<String, String>
) {
    fun getWinner(metric: String): String? = winner[metric]
}
