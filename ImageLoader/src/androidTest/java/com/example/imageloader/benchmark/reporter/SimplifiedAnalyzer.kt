package com.example.imageloader.benchmark.reporter

import com.example.imageloader.logger.ImageLoaderLogger
import com.example.imageloader.logger.ImageLoadLog
import com.example.imageloader.logger.LogSource

/**
 * SimplifiedAnalyzer - Analyze logs and generate simplified benchmark results
 * 
 * Focus on:
 * - Cache hit counts and percentages
 * - Decode time comparison (Network vs Disk)
 * - Outlier detection
 * - Per-image details
 */
object SimplifiedAnalyzer {
    
    fun analyze(
        testStartTime: Long,
        testEndTime: Long,
        imageSpecs: List<Any>? = null
    ): SimplifiedBenchmarkResult {
        val allLogs: List<Any> = ImageLoaderLogger.getAllLogs()
        val imageLoadLogs: List<ImageLoadLog> = allLogs.filterIsInstance<ImageLoadLog>()
            .filter { log -> log.error == null } // Only successful loads
        
        val device = collectDeviceInfo()
        val testConfig = buildTestConfig(testStartTime, testEndTime, imageSpecs)
        val cacheMetrics = analyzeCacheMetrics(imageLoadLogs)
        val decodeMetrics = analyzeDecodeMetrics(imageLoadLogs)
        val imageDetails = buildImageDetails(imageLoadLogs)
        
        return SimplifiedBenchmarkResult(
            timestamp = testStartTime,
            device = device,
            testConfig = testConfig,
            cacheMetrics = cacheMetrics,
            decodeMetrics = decodeMetrics,
            imageDetails = imageDetails
        )
    }
    
    private fun buildTestConfig(
        startTime: Long,
        endTime: Long,
        imageSpecs: List<Any>?
    ): TestConfig {
        val distribution = mutableMapOf(
            "tiny" to 0,
            "small" to 0,
            "medium" to 0,
            "large" to 0,
            "huge" to 0
        )
        
        // Count by URL pattern (picsum photo IDs correspond to sizes)
        val allLogs2: List<Any> = ImageLoaderLogger.getAllLogs()
        val imageLogs: List<ImageLoadLog> = allLogs2.filterIsInstance<ImageLoadLog>()
        imageLogs.forEach { log ->
            val url = log.url
            when {
                url.contains("/200/200") -> distribution["tiny"] = (distribution["tiny"] ?: 0) + 1
                url.contains("/400/600") -> distribution["small"] = (distribution["small"] ?: 0) + 1
                url.contains("/1080/1440") -> distribution["medium"] = (distribution["medium"] ?: 0) + 1
                url.contains("/2560/1440") -> distribution["large"] = (distribution["large"] ?: 0) + 1
                url.contains("/4096/4096") -> distribution["huge"] = (distribution["huge"] ?: 0) + 1
            }
        }
        
        val uniqueImageUrls: List<String> = imageLogs.map { log -> log.url }.distinct()
        
        return TestConfig(
            totalImages = uniqueImageUrls.size,
            imageSizeDistribution = distribution,
            testDuration = endTime - startTime
        )
    }
    
    private fun analyzeCacheMetrics(logs: List<ImageLoadLog>): CacheMetrics {
        val activeCache = logs.filter { it.source == LogSource.ACTIVE_CACHE }
        val memoryCache = logs.filter { it.source == LogSource.MEMORY_CACHE }
        val diskCache = logs.filter { it.source == LogSource.DISK_CACHE }
        val network = logs.filter { it.source == LogSource.NETWORK }
        
        val total = logs.size
        
        return CacheMetrics(
            activeCacheHits = activeCache.size,
            memoryCacheHits = memoryCache.size,
            diskCacheHits = diskCache.size,
            networkLoads = network.size,
            totalRequests = total,
            
            activeCachePercent = if (total > 0) activeCache.size * 100.0 / total else 0.0,
            memoryCachePercent = if (total > 0) memoryCache.size * 100.0 / total else 0.0,
            diskCachePercent = if (total > 0) diskCache.size * 100.0 / total else 0.0,
            networkPercent = if (total > 0) network.size * 100.0 / total else 0.0,
            
            cacheEfficiency = if (total > 0) 
                (activeCache.size + memoryCache.size + diskCache.size) * 100.0 / total 
            else 0.0,
            
            avgActiveCacheTime = activeCache.map { it.totalTimeMs }.average().takeIf { !it.isNaN() } ?: 0.0,
            avgMemoryCacheTime = memoryCache.map { it.totalTimeMs }.average().takeIf { !it.isNaN() } ?: 0.0,
            avgDiskCacheTime = diskCache.map { it.totalTimeMs }.average().takeIf { !it.isNaN() } ?: 0.0,
            avgNetworkTime = network.map { it.totalTimeMs }.average().takeIf { !it.isNaN() } ?: 0.0
        )
    }
    
    private fun analyzeDecodeMetrics(logs: List<ImageLoadLog>): DecodeMetrics {
        val networkDecodes = logs.filter { 
            it.source == LogSource.NETWORK && it.decodeTimeMs != null 
        }.mapNotNull { it.decodeTimeMs }
        
        val diskDecodes = logs.filter { 
            it.source == LogSource.DISK_CACHE && it.decodeTimeMs != null 
        }.mapNotNull { it.decodeTimeMs }
        
        // Percentiles
        val networkSorted = networkDecodes.sorted()
        val diskSorted = diskDecodes.sorted()
        
        fun percentile(list: List<Long>, p: Double): Long {
            if (list.isEmpty()) return 0
            val index = ((list.size - 1) * p).toInt()
            return list[index]
        }
        
        // Outliers (decode > 2x average)
        val networkAvg = networkDecodes.average().takeIf { !it.isNaN() } ?: 0.0
        val diskAvg = diskDecodes.average().takeIf { !it.isNaN() } ?: 0.0
        
        val outliers = mutableListOf<DecodeOutlier>()
        
        logs.filter { it.source == LogSource.NETWORK && it.decodeTimeMs != null }.forEach { log ->
            val decodeTime = log.decodeTimeMs!!
            if (decodeTime > networkAvg * 2) {
                outliers.add(DecodeOutlier(
                    url = log.url,
                    source = "NETWORK",
                    decodeTime = decodeTime,
                    avgDecodeTime = networkAvg,
                    ratio = decodeTime / networkAvg
                ))
            }
        }
        
        logs.filter { it.source == LogSource.DISK_CACHE && it.decodeTimeMs != null }.forEach { log ->
            val decodeTime = log.decodeTimeMs!!
            if (decodeTime > diskAvg * 2 && diskAvg > 0) {
                outliers.add(DecodeOutlier(
                    url = log.url,
                    source = "DISK_CACHE",
                    decodeTime = decodeTime,
                    avgDecodeTime = diskAvg,
                    ratio = decodeTime / diskAvg
                ))
            }
        }
        
        // Decode by size
        fun avgDecodeBySize(pattern: String): Double {
            return logs.filter { it.url.contains(pattern) && it.decodeTimeMs != null }
                .mapNotNull { it.decodeTimeMs }
                .average()
                .takeIf { !it.isNaN() } ?: 0.0
        }
        
        return DecodeMetrics(
            networkDecodeCount = networkDecodes.size,
            networkAvgDecodeTime = networkAvg,
            networkMinDecodeTime = networkSorted.firstOrNull() ?: 0,
            networkMaxDecodeTime = networkSorted.lastOrNull() ?: 0,
            networkDecodeP50 = percentile(networkSorted, 0.50),
            networkDecodeP95 = percentile(networkSorted, 0.95),
            networkDecodeP99 = percentile(networkSorted, 0.99),
            
            diskDecodeCount = diskDecodes.size,
            diskAvgDecodeTime = diskAvg,
            diskMinDecodeTime = diskSorted.firstOrNull() ?: 0,
            diskMaxDecodeTime = diskSorted.lastOrNull() ?: 0,
            diskDecodeP50 = percentile(diskSorted, 0.50),
            diskDecodeP95 = percentile(diskSorted, 0.95),
            diskDecodeP99 = percentile(diskSorted, 0.99),
            
            diskVsNetworkSpeedup = if (diskAvg > 0 && networkAvg > 0) networkAvg / diskAvg else 0.0,
            
            decodeTinyAvg = avgDecodeBySize("/200/200"),
            decodeSmallAvg = avgDecodeBySize("/400/600"),
            decodeMediumAvg = avgDecodeBySize("/1080/1440"),
            decodeLargeAvg = avgDecodeBySize("/2560/1440"),
            decodeHugeAvg = avgDecodeBySize("/4096/4096"),
            
            outliers = outliers.sortedByDescending { it.ratio }
        )
    }
    
    private fun buildImageDetails(logs: List<ImageLoadLog>): List<ImageLoadDetail> {
        return logs.map { log ->
            val size = when {
                log.url.contains("/200/200") -> "tiny"
                log.url.contains("/400/600") -> "small"
                log.url.contains("/1080/1440") -> "medium"
                log.url.contains("/2560/1440") -> "large"
                log.url.contains("/4096/4096") -> "huge"
                else -> "unknown"
            }
            
            ImageLoadDetail(
                url = log.url,
                source = log.source.name,
                totalTime = log.totalTimeMs,
                decodeTime = log.decodeTimeMs,
                transformTime = log.transformTimeMs,
                imageSize = size,
                timestamp = log.timestamp
            )
        }
    }
    
    private fun collectDeviceInfo(): DeviceInfo {
        // Reuse existing DeviceInfo collector
        return DeviceInfo(
            manufacturer = android.os.Build.MANUFACTURER,
            model = android.os.Build.MODEL,
            androidVersion = android.os.Build.VERSION.SDK_INT,
            androidRelease = android.os.Build.VERSION.RELEASE,
            cpuAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            totalMemoryMB = 0L, // Fill if needed
            availableMemoryMB = 0L,
            screenDensity = 0.0f,
            screenResolution = "",
            isEmulator = false
        )
    }
}
