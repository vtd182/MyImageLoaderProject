package com.example.imageloader.benchmark.reporter

import android.os.Build

/**
 * Simplified Benchmark Results - Focus on Cache Performance
 * 
 * Loại bỏ: FPS, scroll metrics, scores
 * Tập trung: Cache hits, decode times, outliers
 */
data class SimplifiedBenchmarkResult(
    val timestamp: Long,
    val device: DeviceInfo,
    val testConfig: TestConfig,
    val cacheMetrics: CacheMetrics,
    val decodeMetrics: DecodeMetrics,
    val rawRequestData: List<RawRequestData>,
    val fileSizeStats: FileSizeStats?
)

data class TestConfig(
    val totalImages: Int,
    val imageSizeDistribution: Map<String, Int>, // tiny/small/medium/large/huge → count
    val testDuration: Long // milliseconds
)

data class CacheMetrics(
    // Hit counts
    val activeCacheHits: Int,
    val memoryCacheHits: Int,
    val diskCacheHits: Int,
    val networkLoads: Int,
    val totalRequests: Int,
    
    // Percentages
    val activeCachePercent: Double,
    val memoryCachePercent: Double,
    val diskCachePercent: Double,
    val networkPercent: Double,
    
    // Overall efficiency
    val cacheEfficiency: Double, // (Active + Memory + Disk) / Total
    
    // Average times
    val avgActiveCacheTime: Double,
    val avgMemoryCacheTime: Double,
    val avgDiskCacheTime: Double,
    val avgNetworkTime: Double
)

data class DecodeMetrics(
    // Network decode stats
    val networkDecodeCount: Int,
    val networkAvgDecodeTime: Double,
    val networkMinDecodeTime: Long,
    val networkMaxDecodeTime: Long,
    val networkDecodeP50: Long,
    val networkDecodeP95: Long,
    val networkDecodeP99: Long,
    
    // Disk decode stats
    val diskDecodeCount: Int,
    val diskAvgDecodeTime: Double,
    val diskMinDecodeTime: Long,
    val diskMaxDecodeTime: Long,
    val diskDecodeP50: Long,
    val diskDecodeP95: Long,
    val diskDecodeP99: Long,
    
    // Comparison
    val diskVsNetworkSpeedup: Double, // Disk decode bao nhiêu lần nhanh hơn Network
    
    // Decode by size
    val decodeTinyAvg: Double,
    val decodeSmallAvg: Double,
    val decodeMediumAvg: Double,
    val decodeLargeAvg: Double,
    val decodeHugeAvg: Double,
    
    // Outliers (decode > 2x avg)
    val outliers: List<DecodeOutlier>
)

data class DecodeOutlier(
    val url: String,
    val source: String, // NETWORK or DISK_CACHE
    val decodeTime: Long,
    val avgDecodeTime: Double,
    val ratio: Double // decodeTime / avgDecodeTime
)

/**
 * RawRequestData - Unified data for Network and Disk requests
 */
data class RawRequestData(
    val url: String,
    val source: String,  // "NETWORK" or "DISK_CACHE"
    val totalTime: Long,
    val fetchTime: Long?,  // Only for network
    val decodeTime: Long?,
    val transformTime: Long?,
    val fileSizeBytes: Long?,
    val imageSize: String  // tiny/small/medium/large/huge
)

/**
 * DeviceInfo - Information about the test device
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
 * FileSizeStats - Analysis of file sizes
 */
data class FileSizeStats(
    val avgFileSizeBytes: Long,
    val minFileSizeBytes: Long,
    val maxFileSizeBytes: Long,
    val avgFileSizeKB: Double,
    val totalFileSizeKB: Double,
    val fileSizeByCategory: Map<String, Long>,
    val sizeDistribution: List<Pair<String, Int>>
)
