package com.example.imageloader.benchmark.reporter

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HtmlReporter - Generate beautiful HTML reports with interactive charts.
 *
 * Produces self-contained HTML files suitable for:
 * - Presentations
 * - Sharing with stakeholders
 * - Visual analysis
 * - Documentation
 *
 * Features:
 * - Interactive Chart.js charts
 * - Color-coded pass/fail status
 * - Responsive design
 * - Dark mode support
 * - Exportable/printable
 */
class HtmlReporter(
    private val outputDir: File
) : BenchmarkExporter {
    
    init {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
    }
    
    override fun export(result: ComprehensiveResult): String {
        val html = generateHtml(result)
        val filename = "benchmark-${result.timestamp}.html"
        val file = File(outputDir, filename)
        file.writeText(html)
        return file.absolutePath
    }
    
    private fun generateHtml(result: ComprehensiveResult): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ImageLoader Benchmark Report</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            line-height: 1.6;
            color: #333;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            padding: 20px;
        }
        
        .container {
            max-width: 1200px;
            margin: 0 auto;
            background: white;
            border-radius: 12px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            overflow: hidden;
        }
        
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 40px;
            text-align: center;
        }
        
        .header h1 {
            font-size: 2.5em;
            margin-bottom: 10px;
        }
        
        .header .timestamp {
            font-size: 0.9em;
            opacity: 0.9;
        }
        
        .content {
            padding: 40px;
        }
        
        .section {
            margin-bottom: 50px;
        }
        
        .section-title {
            font-size: 1.8em;
            color: #667eea;
            margin-bottom: 20px;
            padding-bottom: 10px;
            border-bottom: 3px solid #667eea;
        }
        
        .summary-cards {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        
        .card {
            background: #f8f9fa;
            border-radius: 8px;
            padding: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        
        .card-title {
            font-size: 0.9em;
            color: #666;
            text-transform: uppercase;
            letter-spacing: 1px;
            margin-bottom: 10px;
        }
        
        .card-value {
            font-size: 2em;
            font-weight: bold;
            color: #333;
        }
        
        .card-subtitle {
            font-size: 0.85em;
            color: #999;
            margin-top: 5px;
        }
        
        .pass {
            color: #28a745;
        }
        
        .fail {
            color: #dc3545;
        }
        
        .info {
            color: #17a2b8;
        }
        
        .chart-container {
            position: relative;
            height: 400px;
            margin-bottom: 40px;
        }
        
        .device-info {
            background: #f8f9fa;
            border-left: 4px solid #667eea;
            padding: 20px;
            border-radius: 4px;
            margin-bottom: 30px;
        }
        
        .device-info-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
            margin-top: 15px;
        }
        
        .device-info-item {
            font-size: 0.9em;
        }
        
        .device-info-label {
            color: #666;
            font-weight: 600;
        }
        
        .highlights {
            background: #d4edda;
            border-left: 4px solid #28a745;
            padding: 20px;
            border-radius: 4px;
            margin-bottom: 20px;
        }
        
        .regressions {
            background: #f8d7da;
            border-left: 4px solid #dc3545;
            padding: 20px;
            border-radius: 4px;
            margin-bottom: 20px;
        }
        
        .highlights ul, .regressions ul {
            margin-left: 20px;
            margin-top: 10px;
        }
        
        .footer {
            background: #f8f9fa;
            padding: 20px;
            text-align: center;
            font-size: 0.85em;
            color: #666;
        }
        
        @media print {
            body {
                background: white;
                padding: 0;
            }
            
            .container {
                box-shadow: none;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📊 ImageLoader Benchmark Report</h1>
            <p class="timestamp">Generated: ${formatTimestamp(result.timestamp)}</p>
        </div>
        
        <div class="content">
            ${generateSummarySection(result.summary)}
            ${generateDeviceInfoSection(result.deviceInfo)}
            ${generateHighlightsSection(result.summary)}
            ${result.cacheResult?.let { generateCacheSection(it) } ?: ""}
            ${result.decodeResult?.let { generateDecodeSection(it) } ?: ""}
            ${result.transformResult?.let { generateTransformSection(it) } ?: ""}
            ${result.memoryResult?.let { generateMemorySection(it) } ?: ""}
            ${result.scrollResult?.let { generateScrollSection(it) } ?: ""}
        </div>
        
        <div class="footer">
            <p>ImageLoader Benchmark System v1.0</p>
            <p>Report generated automatically by HtmlReporter</p>
        </div>
    </div>
</body>
</html>
        """.trimIndent()
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    private fun generateSummarySection(summary: BenchmarkSummary): String {
        val scoreColor = if (summary.overallScore >= 80) "pass" else "fail"
        val passRateColor = if (summary.passRate >= 0.8) "pass" else "fail"
        
        return """
            <div class="section">
                <h2 class="section-title">Executive Summary</h2>
                <div class="summary-cards">
                    <div class="card">
                        <div class="card-title">Overall Score</div>
                        <div class="card-value $scoreColor">
                            ${String.format("%.1f", summary.overallScore)}/100
                        </div>
                        <div class="card-subtitle">${if (summary.overallScore >= 80) "Excellent" else "Needs Improvement"}</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Pass Rate</div>
                        <div class="card-value $passRateColor">
                            ${String.format("%.0f", summary.passRate * 100)}%
                        </div>
                        <div class="card-subtitle">${summary.passedTests}/${summary.totalTests} tests passed</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Total Duration</div>
                        <div class="card-value info">
                            ${String.format("%.1f", summary.totalDurationMs / 1000.0)}s
                        </div>
                        <div class="card-subtitle">${summary.totalTests} tests executed</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Failed Tests</div>
                        <div class="card-value ${if (summary.failedTests > 0) "fail" else "pass"}">
                            ${summary.failedTests}
                        </div>
                        <div class="card-subtitle">${if (summary.failedTests == 0) "All passed!" else "Review failures"}</div>
                    </div>
                </div>
            </div>
        """.trimIndent()
    }
    
    private fun generateDeviceInfoSection(info: DeviceInfo): String {
        return """
            <div class="section">
                <h2 class="section-title">Device Information</h2>
                <div class="device-info">
                    <div class="device-info-grid">
                        <div class="device-info-item">
                            <span class="device-info-label">Device:</span> ${info.manufacturer} ${info.model}
                        </div>
                        <div class="device-info-item">
                            <span class="device-info-label">Android:</span> ${info.androidRelease} (API ${info.androidVersion})
                        </div>
                        <div class="device-info-item">
                            <span class="device-info-label">CPU ABI:</span> ${info.cpuAbi}
                        </div>
                        <div class="device-info-item">
                            <span class="device-info-label">Memory:</span> ${info.totalMemoryMB} MB (${info.availableMemoryMB} MB available)
                        </div>
                        <div class="device-info-item">
                            <span class="device-info-label">Screen:</span> ${info.screenResolution} @ ${info.screenDensity} dpi
                        </div>
                        <div class="device-info-item">
                            <span class="device-info-label">Emulator:</span> ${if (info.isEmulator) "Yes" else "No"}
                        </div>
                    </div>
                </div>
            </div>
        """.trimIndent()
    }
    
    private fun generateHighlightsSection(summary: BenchmarkSummary): String {
        val highlightsHtml = if (summary.highlights.isNotEmpty()) {
            """
                <div class="highlights">
                    <h3>✨ Highlights</h3>
                    <ul>
                        ${summary.highlights.joinToString("\n") { "<li>$it</li>" }}
                    </ul>
                </div>
            """.trimIndent()
        } else ""
        
        val regressionsHtml = if (summary.regressions.isNotEmpty()) {
            """
                <div class="regressions">
                    <h3>⚠️ Regressions</h3>
                    <ul>
                        ${summary.regressions.joinToString("\n") { "<li>$it</li>" }}
                    </ul>
                </div>
            """.trimIndent()
        } else ""
        
        return if (highlightsHtml.isNotEmpty() || regressionsHtml.isNotEmpty()) {
            """
                <div class="section">
                    $highlightsHtml
                    $regressionsHtml
                </div>
            """.trimIndent()
        } else ""
    }
    
    private fun generateCacheSection(cache: CacheBenchmarkResult): String {
        return """
            <div class="section">
                <h2 class="section-title">Cache Performance</h2>
                <div class="chart-container">
                    <canvas id="cacheHitRateChart"></canvas>
                </div>
                <div class="chart-container">
                    <canvas id="cacheTimeChart"></canvas>
                </div>
                <script>
                    // Cache Hit Rate Pie Chart
                    new Chart(document.getElementById('cacheHitRateChart'), {
                        type: 'pie',
                        data: {
                            labels: ['Active Cache', 'Memory Cache', 'Disk Cache', 'Network'],
                            datasets: [{
                                data: [
                                    ${String.format("%.2f", cache.activeCacheHitRate * 100)},
                                    ${String.format("%.2f", cache.memoryCacheHitRate * 100)},
                                    ${String.format("%.2f", cache.diskCacheHitRate * 100)},
                                    ${String.format("%.2f", cache.networkRate * 100)}
                                ],
                                backgroundColor: ['#28a745', '#17a2b8', '#ffc107', '#dc3545']
                            }]
                        },
                        options: {
                            responsive: true,
                            maintainAspectRatio: false,
                            plugins: {
                                title: {
                                    display: true,
                                    text: 'Cache Hit Rate Distribution',
                                    font: { size: 18 }
                                },
                                legend: {
                                    position: 'bottom'
                                },
                                tooltip: {
                                    callbacks: {
                                        label: function(context) {
                                            return context.label + ': ' + context.parsed.toFixed(1) + '%';
                                        }
                                    }
                                }
                            }
                        }
                    });
                    
                    // Cache Time Bar Chart
                    new Chart(document.getElementById('cacheTimeChart'), {
                        type: 'bar',
                        data: {
                            labels: ['Active', 'Memory', 'Disk', 'Network'],
                            datasets: [{
                                label: 'Average Load Time (ms)',
                                data: [${cache.avgActiveCacheTime}, ${cache.avgMemoryCacheTime}, ${cache.avgDiskCacheTime}, ${cache.avgNetworkTime}],
                                backgroundColor: ['#28a745', '#17a2b8', '#ffc107', '#dc3545']
                            }]
                        },
                        options: {
                            responsive: true,
                            maintainAspectRatio: false,
                            plugins: {
                                title: {
                                    display: true,
                                    text: 'Average Load Time by Cache Tier',
                                    font: { size: 18 }
                                },
                                legend: {
                                    display: false
                                }
                            },
                            scales: {
                                y: {
                                    beginAtZero: true,
                                    title: {
                                        display: true,
                                        text: 'Time (ms)'
                                    }
                                }
                            }
                        }
                    });
                </script>
            </div>
        """.trimIndent()
    }
    
    private fun generateDecodeSection(decode: DecodeBenchmarkResult): String {
        return """
            <div class="section">
                <h2 class="section-title">Decode Performance</h2>
                <div class="summary-cards">
                    <div class="card">
                        <div class="card-title">Bitmap Pool Hit Rate</div>
                        <div class="card-value ${if (decode.bitmapPoolHitRate >= 0.5) "pass" else "fail"}">
                            ${String.format("%.1f", decode.bitmapPoolHitRate * 100)}%
                        </div>
                        <div class="card-subtitle">Target: ≥50%</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Allocation Reduction</div>
                        <div class="card-value ${if (decode.allocationReduction >= 0.3) "pass" else "info"}">
                            ${String.format("%.1f", decode.allocationReduction * 100)}%
                        </div>
                        <div class="card-subtitle">${decode.allocationsWithPool} vs ${decode.allocationsWithoutPool} allocs</div>
                    </div>
                    <div class="card">
                        <div class="card-title">GC Count</div>
                        <div class="card-value ${if (decode.gcCountWithPool < 10) "pass" else "fail"}">
                            ${decode.gcCountWithPool}
                        </div>
                        <div class="card-subtitle">Target: <10</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Avg Memory Used</div>
                        <div class="card-value info">
                            ${String.format("%.1f", decode.avgMemoryUsedMB)} MB
                        </div>
                    </div>
                </div>
                <div class="chart-container">
                    <canvas id="decodeTimeChart"></canvas>
                </div>
                <script>
                    new Chart(document.getElementById('decodeTimeChart'), {
                        type: 'line',
                        data: {
                            labels: ['Tiny (200x200)', 'Small (400x600)', 'Medium (1080x1440)', 'Large (2560x1440)', 'Huge (4096x4096)'],
                            datasets: [{
                                label: 'Decode Time (ms)',
                                data: [${decode.avgDecodeTimeTiny}, ${decode.avgDecodeTimeSmall}, ${decode.avgDecodeTimeMedium}, ${decode.avgDecodeTimeLarge}, ${decode.avgDecodeTimeHuge}],
                                borderColor: '#667eea',
                                backgroundColor: 'rgba(102, 126, 234, 0.1)',
                                fill: true,
                                tension: 0.4
                            }]
                        },
                        options: {
                            responsive: true,
                            maintainAspectRatio: false,
                            plugins: {
                                title: {
                                    display: true,
                                    text: 'Decode Time by Image Size',
                                    font: { size: 18 }
                                }
                            },
                            scales: {
                                y: {
                                    beginAtZero: true,
                                    title: {
                                        display: true,
                                        text: 'Time (ms)'
                                    }
                                }
                            }
                        }
                    });
                </script>
            </div>
        """.trimIndent()
    }
    
    private fun generateTransformSection(transform: TransformBenchmarkResult): String {
        return """
            <div class="section">
                <h2 class="section-title">Transform Performance</h2>
                <div class="summary-cards">
                    <div class="card">
                        <div class="card-title">Avg Transform Time</div>
                        <div class="card-value ${if (transform.avgTransformTime <= 50) "pass" else "fail"}">
                            ${transform.avgTransformTime} ms
                        </div>
                        <div class="card-subtitle">Target: ≤50ms</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Pool Reuse Rate</div>
                        <div class="card-value ${if (transform.poolReuseRate >= 0.4) "pass" else "fail"}">
                            ${String.format("%.1f", transform.poolReuseRate * 100)}%
                        </div>
                        <div class="card-subtitle">Target: ≥40%</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Time Reduction</div>
                        <div class="card-value ${if (transform.timeReduction >= 0.2) "pass" else "info"}">
                            ${String.format("%.1f", transform.timeReduction * 100)}%
                        </div>
                        <div class="card-subtitle">With pool vs without</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Transform Cache Hit</div>
                        <div class="card-value ${if (transform.transformCacheHitRate >= 0.8) "pass" else "fail"}">
                            ${String.format("%.1f", transform.transformCacheHitRate * 100)}%
                        </div>
                        <div class="card-subtitle">Target: ≥80%</div>
                    </div>
                </div>
            </div>
        """.trimIndent()
    }
    
    private fun generateMemorySection(memory: MemoryBenchmarkResult): String {
        val leakStatus = if (memory.leakDetected) "fail" else "pass"
        val heapStatus = if (memory.peakHeapMB <= 256) "pass" else "fail"
        
        return """
            <div class="section">
                <h2 class="section-title">Memory Analysis</h2>
                <div class="summary-cards">
                    <div class="card">
                        <div class="card-title">Peak Heap</div>
                        <div class="card-value $heapStatus">
                            ${String.format("%.1f", memory.peakHeapMB)} MB
                        </div>
                        <div class="card-subtitle">Target: ≤256 MB</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Leak Detected</div>
                        <div class="card-value $leakStatus">
                            ${if (memory.leakDetected) "YES" else "NO"}
                        </div>
                        <div class="card-subtitle">${String.format("%.3f", memory.leakRateMBPerCycle)} MB/cycle</div>
                    </div>
                    <div class="card">
                        <div class="card-title">GC Count</div>
                        <div class="card-value ${if (memory.gcCount < 10) "pass" else "fail"}">
                            ${memory.gcCount}
                        </div>
                        <div class="card-subtitle">Avg pause: ${String.format("%.2f", memory.avgGCPauseMs)}ms</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Bitmap Pool Size</div>
                        <div class="card-value ${if (memory.bitmapPoolSizeMB <= 50) "pass" else "fail"}">
                            ${String.format("%.1f", memory.bitmapPoolSizeMB)} MB
                        </div>
                        <div class="card-subtitle">${String.format("%.1f", memory.bitmapPoolUtilization * 100)}% utilized</div>
                    </div>
                </div>
            </div>
        """.trimIndent()
    }
    
    private fun generateScrollSection(scroll: ScrollBenchmarkResult): String {
        return """
            <div class="section">
                <h2 class="section-title">Scroll Performance</h2>
                <div class="summary-cards">
                    <div class="card">
                        <div class="card-title">Avg FPS</div>
                        <div class="card-value ${if (scroll.avgFPS >= 55) "pass" else "fail"}">
                            ${String.format("%.1f", scroll.avgFPS)}
                        </div>
                        <div class="card-subtitle">Target: ≥55 FPS</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Jank Count</div>
                        <div class="card-value ${if (scroll.jankCount <= 5) "pass" else "fail"}">
                            ${scroll.jankCount}
                        </div>
                        <div class="card-subtitle">Target: ≤5</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Priority Effectiveness</div>
                        <div class="card-value ${if (scroll.priorityEffectiveness >= 30) "pass" else "fail"}">
                            ${String.format("%.1f", scroll.priorityEffectiveness)}%
                        </div>
                        <div class="card-subtitle">High vs Low priority</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Memory Stability</div>
                        <div class="card-value ${if (scroll.memoryStability < 5.0) "pass" else "fail"}">
                            ${String.format("%.2f", scroll.memoryStability)}
                        </div>
                        <div class="card-subtitle">Variance during scroll</div>
                    </div>
                </div>
            </div>
        """.trimIndent()
    }
}
