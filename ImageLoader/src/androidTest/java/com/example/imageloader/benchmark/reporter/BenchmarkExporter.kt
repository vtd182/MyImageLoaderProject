package com.example.imageloader.benchmark.reporter

import java.io.File

/**
 * BenchmarkExporter - Interface for exporting benchmark results.
 */
interface BenchmarkExporter {
    /**
     * Export benchmark results to a file.
     *
     * @param result The comprehensive result to export
     * @return Path to the exported file
     */
    fun export(result: ComprehensiveResult): String
}

/**
 * BenchmarkReporter - Interface for generating reports.
 */
interface BenchmarkReporter {
    /**
     * Generate reports using all configured exporters.
     *
     * @param result The comprehensive result to report
     */
    fun generateReport(result: ComprehensiveResult): List<String>
}

/**
 * CompositeBenchmarkReporter - Reporter that delegates to multiple exporters.
 */
class CompositeBenchmarkReporter(
    private val exporters: List<BenchmarkExporter>
) : BenchmarkReporter {
    
    override fun generateReport(result: ComprehensiveResult): List<String> {
        return exporters.map { it.export(result) }
    }
}
