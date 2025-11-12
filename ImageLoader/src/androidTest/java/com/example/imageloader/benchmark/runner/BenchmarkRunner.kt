package com.example.imageloader.benchmark.runner

import com.example.imageloader.benchmark.reporter.BenchmarkResult
import kotlinx.coroutines.withTimeout
import kotlin.math.sqrt

/**
 * BenchmarkRunner - Interface for executing benchmark tests.
 */
interface BenchmarkRunner {
    /**
     * Run a benchmark test with warmup and measurement phases.
     *
     * @param name Test name
     * @param warmupIterations Number of warmup runs (results discarded)
     * @param measurementIterations Number of measurement runs
     * @param timeoutSeconds Timeout per iteration
     * @param test The test to run
     * @return BenchmarkResult with statistics
     */
    suspend fun runBenchmark(
        name: String,
        warmupIterations: Int = 3,
        measurementIterations: Int = 10,
        timeoutSeconds: Long = 300,
        test: suspend () -> Unit
    ): BenchmarkResult
}

/**
 * DefaultBenchmarkRunner - Standard implementation of BenchmarkRunner.
 *
 * Performs warmup, measurement, and statistical analysis.
 */
class DefaultBenchmarkRunner : BenchmarkRunner {
    
    override suspend fun runBenchmark(
        name: String,
        warmupIterations: Int,
        measurementIterations: Int,
        timeoutSeconds: Long,
        test: suspend () -> Unit
    ): BenchmarkResult {
        
        // Phase 1: Warmup (discard results)
        repeat(warmupIterations) {
            withTimeout(timeoutSeconds * 1000) {
                test()
            }
        }
        
        // Phase 2: Measurement
        val timings = mutableListOf<Long>()
        repeat(measurementIterations) {
            val start = System.nanoTime()
            withTimeout(timeoutSeconds * 1000) {
                test()
            }
            val end = System.nanoTime()
            timings.add((end - start) / 1_000_000) // Convert to ms
        }
        
        // Phase 3: Statistical analysis
        return BenchmarkResult(
            name = name,
            iterations = timings.size,
            mean = timings.average(),
            median = timings.median(),
            p95 = timings.percentile(95),
            p99 = timings.percentile(99),
            min = timings.minOrNull() ?: 0L,
            max = timings.maxOrNull() ?: 0L,
            stdDev = timings.standardDeviation(),
            timestamp = System.currentTimeMillis()
        )
    }
}

/**
 * Statistical extension functions for List<Long>.
 */

fun List<Long>.median(): Double {
    if (isEmpty()) return 0.0
    val sorted = sorted()
    return if (size % 2 == 0) {
        (sorted[size / 2 - 1] + sorted[size / 2]) / 2.0
    } else {
        sorted[size / 2].toDouble()
    }
}

fun List<Long>.percentile(p: Int): Double {
    if (isEmpty()) return 0.0
    require(p in 0..100) { "Percentile must be between 0 and 100" }
    val sorted = sorted()
    val index = (p / 100.0 * (sorted.size - 1)).toInt()
    return sorted[index].toDouble()
}

fun List<Long>.standardDeviation(): Double {
    if (isEmpty()) return 0.0
    val mean = average()
    val variance = map { (it - mean) * (it - mean) }.average()
    return sqrt(variance)
}
