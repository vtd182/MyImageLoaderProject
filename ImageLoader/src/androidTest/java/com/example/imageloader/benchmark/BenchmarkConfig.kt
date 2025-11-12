package com.example.imageloader.benchmark

/**
 * BenchmarkConfig - Centralized configuration for benchmark tests.
 */
object BenchmarkConfig {
    
    /**
     * Test datasets by scenario.
     */
    object Datasets {
        // Quick smoke test (for CI/CD)
        const val QUICK_TEST_SIZE = 50
        
        // Standard benchmark (comprehensive)
        const val STANDARD_TEST_SIZE = 1000
        
        // Stress test (memory/performance limits)
        const val STRESS_TEST_SIZE = 5000
        
        // Comparison test (vs competitors)
        const val COMPARISON_TEST_SIZE = 500
    }
    
    /**
     * Performance thresholds (fail test if exceeded).
     */
    object Thresholds {
        // Latency (milliseconds)
        const val MAX_CACHE_LOOKUP_MS = 10L
        const val MAX_DISK_READ_MS = 100L
        const val MAX_DECODE_TIME_MS = 200L
        const val MAX_TOTAL_TIME_CACHED_MS = 500L
        const val MAX_TOTAL_TIME_NETWORK_MS = 3000L
        
        // Cache hit rates (percentage)
        const val MIN_CACHE_HIT_RATE = 0.85  // 85%
        const val MIN_MEMORY_HIT_RATE = 0.40  // 40%
        
        // Memory (megabytes)
        const val MAX_HEAP_SIZE_MB = 256
        const val MAX_BITMAP_POOL_MB = 50
        
        // UI performance
        const val MIN_FPS = 55  // Target 60, allow 5 dropped frames
        const val MAX_JANK_COUNT = 5
    }
    
    /**
     * Test image specifications.
     */
    object TestImages {
        // Representative real-world distribution
        val REALISTIC_MIX = mapOf(
            TestDataGenerator.SizeCategory.TINY to 150,    // 15%
            TestDataGenerator.SizeCategory.SMALL to 600,   // 60%
            TestDataGenerator.SizeCategory.MEDIUM to 200,  // 20%
            TestDataGenerator.SizeCategory.LARGE to 50     // 5%
        )
        
        // Stress test with large images
        val MEMORY_STRESS = mapOf(
            TestDataGenerator.SizeCategory.LARGE to 70,    // 70%
            TestDataGenerator.SizeCategory.HUGE to 30      // 30%
        )
    }
    
    /**
     * Benchmark iterations and warmup.
     */
    object Execution {
        const val WARMUP_ITERATIONS = 3
        const val MEASUREMENT_ITERATIONS = 10
        const val TIMEOUT_SECONDS = 300L  // 5 minutes max
    }
    
    /**
     * Result export configuration.
     */
    object Export {
        const val RESULTS_DIR = "/sdcard/Download/benchmark-results"
        const val JSON_OUTPUT = true
        const val CSV_OUTPUT = true
        const val CHARTS = true
    }
}
