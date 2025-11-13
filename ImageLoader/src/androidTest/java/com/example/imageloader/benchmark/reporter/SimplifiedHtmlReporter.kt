package com.example.imageloader.benchmark.reporter

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * SimplifiedHtmlReporter - Generate HTML report focused on cache performance
 * 
 * Features:
 * - Cache hit distribution chart
 * - Network vs Disk decode time comparison chart
 * - Outlier table
 * - Image load details table with filtering
 */
object SimplifiedHtmlReporter {
    
    fun generate(result: SimplifiedBenchmarkResult, outputFile: File) {
        val html = buildHtml(result)
        outputFile.writeText(html)
    }
    
    private fun buildHtml(result: SimplifiedBenchmarkResult): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val testDate = dateFormat.format(Date(result.timestamp))
        
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ImageLoader Benchmark - Cache Performance</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { 
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: #f5f5f5;
            padding: 20px;
        }
        .container { max-width: 1400px; margin: 0 auto; }
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 30px;
            border-radius: 10px;
            margin-bottom: 20px;
        }
        .header h1 { font-size: 32px; margin-bottom: 10px; }
        .header .subtitle { opacity: 0.9; font-size: 16px; }
        
        .section {
            background: white;
            padding: 25px;
            border-radius: 10px;
            margin-bottom: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .section h2 {
            font-size: 24px;
            margin-bottom: 20px;
            color: #333;
            border-bottom: 2px solid #667eea;
            padding-bottom: 10px;
        }
        
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
            margin-bottom: 20px;
        }
        .stat-card {
            padding: 20px;
            border-radius: 8px;
            text-align: center;
        }
        .stat-card.active { background: #e8f5e9; }
        .stat-card.memory { background: #e3f2fd; }
        .stat-card.disk { background: #fff3e0; }
        .stat-card.network { background: #ffebee; }
        
        .stat-card .label {
            font-size: 14px;
            color: #666;
            margin-bottom: 5px;
        }
        .stat-card .value {
            font-size: 32px;
            font-weight: bold;
            color: #333;
        }
        .stat-card .percent {
            font-size: 18px;
            color: #666;
            margin-top: 5px;
        }
        
        .chart-container {
            position: relative;
            height: 400px;
            margin: 20px 0;
        }
        
        table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 15px;
        }
        th, td {
            padding: 12px;
            text-align: left;
            border-bottom: 1px solid #ddd;
        }
        th {
            background: #f5f5f5;
            font-weight: 600;
            color: #333;
        }
        tr:hover { background: #f9f9f9; }
        
        .badge {
            display: inline-block;
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 12px;
            font-weight: 600;
        }
        .badge.network { background: #ffcdd2; color: #c62828; }
        .badge.disk { background: #ffe0b2; color: #e65100; }
        .badge.memory { background: #bbdefb; color: #1565c0; }
        .badge.active { background: #c8e6c9; color: #2e7d32; }
        
        .badge.tiny { background: #e1bee7; color: #6a1b9a; }
        .badge.small { background: #c5cae9; color: #3949ab; }
        .badge.medium { background: #b2dfdb; color: #00695c; }
        .badge.large { background: #ffccbc; color: #d84315; }
        .badge.huge { background: #f8bbd0; color: #c2185b; }
        
        .filter-controls {
            margin-bottom: 15px;
            display: flex;
            gap: 10px;
            flex-wrap: wrap;
        }
        .filter-controls select, .filter-controls input {
            padding: 8px 12px;
            border: 1px solid #ddd;
            border-radius: 4px;
            font-size: 14px;
        }
        
        .url-cell {
            max-width: 300px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            font-family: monospace;
            font-size: 12px;
        }
        
        .comparison-box {
            background: #f9f9f9;
            padding: 20px;
            border-radius: 8px;
            border-left: 4px solid #667eea;
            margin: 20px 0;
        }
        .comparison-box h3 {
            margin-bottom: 15px;
            color: #667eea;
        }
        .comparison-stat {
            display: flex;
            justify-content: space-between;
            padding: 10px 0;
            border-bottom: 1px solid #ddd;
        }
        .comparison-stat:last-child { border-bottom: none; }
        .comparison-stat .label { color: #666; }
        .comparison-stat .value { font-weight: 600; color: #333; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📊 ImageLoader Benchmark Report</h1>
            <div class="subtitle">Cache Performance Analysis • $testDate</div>
            <div class="subtitle">${result.device.model} • Android ${result.device.androidRelease}</div>
        </div>
        
        ${buildOverviewSection(result)}
        ${buildCacheHitsSection(result)}
        ${buildDecodeComparisonSection(result)}
        ${buildOutliersSection(result)}
        ${buildImageDetailsSection(result)}
    </div>
    
    <script>
        ${buildChartsScript(result)}
        ${buildFilterScript()}
    </script>
</body>
</html>
        """.trimIndent()
    }
    
    private fun buildOverviewSection(result: SimplifiedBenchmarkResult): String {
        val cache = result.cacheMetrics
        val config = result.testConfig
        
        return """
        <div class="section">
            <h2>📝 Test Overview</h2>
            <div class="stats-grid">
                <div class="stat-card">
                    <div class="label">Total Images</div>
                    <div class="value">${config.totalImages}</div>
                </div>
                <div class="stat-card">
                    <div class="label">Total Requests</div>
                    <div class="value">${cache.totalRequests}</div>
                </div>
                <div class="stat-card">
                    <div class="label">Test Duration</div>
                    <div class="value">${config.testDuration / 1000}s</div>
                </div>
                <div class="stat-card">
                    <div class="label">Cache Efficiency</div>
                    <div class="value">${String.format("%.1f%%", cache.cacheEfficiency)}</div>
                </div>
            </div>
            
            <h3 style="margin-top: 20px; margin-bottom: 10px;">Image Size Distribution</h3>
            <div class="stats-grid">
                ${config.imageSizeDistribution.entries.joinToString("") { (size, count) ->
                    """
                    <div class="stat-card">
                        <div class="label">${size.uppercase()}</div>
                        <div class="value">$count</div>
                    </div>
                    """.trimIndent()
                }}
            </div>
        </div>
        """
    }
    
    private fun buildCacheHitsSection(result: SimplifiedBenchmarkResult): String {
        val cache = result.cacheMetrics
        
        return """
        <div class="section">
            <h2>🎯 Cache Hit Distribution</h2>
            
            <div class="stats-grid">
                <div class="stat-card active">
                    <div class="label">Active Cache</div>
                    <div class="value">${cache.activeCacheHits}</div>
                    <div class="percent">${String.format("%.1f%%", cache.activeCachePercent)}</div>
                    <div class="label" style="margin-top: 10px;">${String.format("%.0fms avg", cache.avgActiveCacheTime)}</div>
                </div>
                <div class="stat-card memory">
                    <div class="label">Memory Cache</div>
                    <div class="value">${cache.memoryCacheHits}</div>
                    <div class="percent">${String.format("%.1f%%", cache.memoryCachePercent)}</div>
                    <div class="label" style="margin-top: 10px;">${String.format("%.0fms avg", cache.avgMemoryCacheTime)}</div>
                </div>
                <div class="stat-card disk">
                    <div class="label">Disk Cache</div>
                    <div class="value">${cache.diskCacheHits}</div>
                    <div class="percent">${String.format("%.1f%%", cache.diskCachePercent)}</div>
                    <div class="label" style="margin-top: 10px;">${String.format("%.0fms avg", cache.avgDiskCacheTime)}</div>
                </div>
                <div class="stat-card network">
                    <div class="label">Network</div>
                    <div class="value">${cache.networkLoads}</div>
                    <div class="percent">${String.format("%.1f%%", cache.networkPercent)}</div>
                    <div class="label" style="margin-top: 10px;">${String.format("%.0fms avg", cache.avgNetworkTime)}</div>
                </div>
            </div>
            
            <div class="chart-container">
                <canvas id="cacheHitsChart"></canvas>
            </div>
        </div>
        """
    }
    
    private fun buildDecodeComparisonSection(result: SimplifiedBenchmarkResult): String {
        val decode = result.decodeMetrics
        
        return """
        <div class="section">
            <h2>⚡ Decode Performance: Network vs Disk Cache</h2>
            
            <div class="comparison-box">
                <h3>Performance Comparison</h3>
                <div class="comparison-stat">
                    <span class="label">Disk vs Network Speedup</span>
                    <span class="value">${String.format("%.2fx faster", decode.diskVsNetworkSpeedup)}</span>
                </div>
                <div class="comparison-stat">
                    <span class="label">Network Avg Decode</span>
                    <span class="value">${String.format("%.1fms", decode.networkAvgDecodeTime)}</span>
                </div>
                <div class="comparison-stat">
                    <span class="label">Disk Avg Decode</span>
                    <span class="value">${String.format("%.1fms", decode.diskAvgDecodeTime)}</span>
                </div>
            </div>
            
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 20px;">
                <div>
                    <h3 style="margin-bottom: 15px;">Network Decode Stats</h3>
                    <div class="comparison-box">
                        <div class="comparison-stat">
                            <span class="label">Count</span>
                            <span class="value">${decode.networkDecodeCount}</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">Min</span>
                            <span class="value">${decode.networkMinDecodeTime}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">P50</span>
                            <span class="value">${decode.networkDecodeP50}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">P95</span>
                            <span class="value">${decode.networkDecodeP95}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">P99</span>
                            <span class="value">${decode.networkDecodeP99}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">Max</span>
                            <span class="value">${decode.networkMaxDecodeTime}ms</span>
                        </div>
                    </div>
                </div>
                
                <div>
                    <h3 style="margin-bottom: 15px;">Disk Cache Decode Stats</h3>
                    <div class="comparison-box">
                        <div class="comparison-stat">
                            <span class="label">Count</span>
                            <span class="value">${decode.diskDecodeCount}</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">Min</span>
                            <span class="value">${decode.diskMinDecodeTime}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">P50</span>
                            <span class="value">${decode.diskDecodeP50}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">P95</span>
                            <span class="value">${decode.diskDecodeP95}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">P99</span>
                            <span class="value">${decode.diskDecodeP99}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">Max</span>
                            <span class="value">${decode.diskMaxDecodeTime}ms</span>
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="chart-container" style="margin-top: 20px;">
                <canvas id="decodeComparisonChart"></canvas>
            </div>
            
            <h3 style="margin-top: 30px; margin-bottom: 15px;">Decode Time by Image Size</h3>
            <div class="chart-container">
                <canvas id="decodeBySizeChart"></canvas>
            </div>
        </div>
        """
    }
    
    private fun buildOutliersSection(result: SimplifiedBenchmarkResult): String {
        val outliers = result.decodeMetrics.outliers
        
        if (outliers.isEmpty()) {
            return """
            <div class="section">
                <h2>⚠️ Decode Outliers</h2>
                <p style="color: #666;">No outliers detected (decode time < 2x average)</p>
            </div>
            """
        }
        
        return """
        <div class="section">
            <h2>⚠️ Decode Outliers (> 2x Average)</h2>
            <p style="color: #666; margin-bottom: 15px;">Images that took significantly longer to decode</p>
            
            <table>
                <thead>
                    <tr>
                        <th>URL</th>
                        <th>Source</th>
                        <th>Decode Time</th>
                        <th>Average</th>
                        <th>Ratio</th>
                    </tr>
                </thead>
                <tbody>
                    ${outliers.take(20).joinToString("") { outlier ->
                        """
                        <tr>
                            <td class="url-cell" title="${outlier.url}">${outlier.url}</td>
                            <td><span class="badge ${outlier.source.lowercase()}">${outlier.source}</span></td>
                            <td>${outlier.decodeTime}ms</td>
                            <td>${String.format("%.1f", outlier.avgDecodeTime)}ms</td>
                            <td><strong>${String.format("%.2fx", outlier.ratio)}</strong></td>
                        </tr>
                        """.trimIndent()
                    }}
                </tbody>
            </table>
        </div>
        """
    }
    
    private fun buildImageDetailsSection(result: SimplifiedBenchmarkResult): String {
        val details = result.imageDetails.takeLast(100) // Last 100 for performance
        
        return """
        <div class="section">
            <h2>📋 Image Load Details (Last 100)</h2>
            
            <div class="filter-controls">
                <select id="sourceFilter" onchange="filterTable()">
                    <option value="">All Sources</option>
                    <option value="ACTIVE_CACHE">Active Cache</option>
                    <option value="MEMORY_CACHE">Memory Cache</option>
                    <option value="DISK_CACHE">Disk Cache</option>
                    <option value="NETWORK">Network</option>
                </select>
                
                <select id="sizeFilter" onchange="filterTable()">
                    <option value="">All Sizes</option>
                    <option value="tiny">Tiny</option>
                    <option value="small">Small</option>
                    <option value="medium">Medium</option>
                    <option value="large">Large</option>
                    <option value="huge">Huge</option>
                </select>
                
                <input type="text" id="urlFilter" placeholder="Filter by URL..." onkeyup="filterTable()" style="flex: 1; min-width: 200px;">
            </div>
            
            <table id="detailsTable">
                <thead>
                    <tr>
                        <th>URL</th>
                        <th>Source</th>
                        <th>Size</th>
                        <th>Total Time</th>
                        <th>Decode</th>
                        <th>Transform</th>
                    </tr>
                </thead>
                <tbody>
                    ${details.joinToString("") { detail ->
                        """
                        <tr data-source="${detail.source}" data-size="${detail.imageSize}" data-url="${detail.url}">
                            <td class="url-cell" title="${detail.url}">${detail.url}</td>
                            <td><span class="badge ${detail.source.lowercase().replace("_cache", "")}">${detail.source}</span></td>
                            <td><span class="badge ${detail.imageSize}">${detail.imageSize?.uppercase() ?: "?"}</span></td>
                            <td>${detail.totalTime}ms</td>
                            <td>${detail.decodeTime?.let { "${it}ms" } ?: "-"}</td>
                            <td>${detail.transformTime?.let { "${it}ms" } ?: "-"}</td>
                        </tr>
                        """.trimIndent()
                    }}
                </tbody>
            </table>
        </div>
        """
    }
    
    private fun buildChartsScript(result: SimplifiedBenchmarkResult): String {
        val cache = result.cacheMetrics
        val decode = result.decodeMetrics
        
        return """
        // Cache Hits Chart
        new Chart(document.getElementById('cacheHitsChart'), {
            type: 'bar',
            data: {
                labels: ['Active Cache', 'Memory Cache', 'Disk Cache', 'Network'],
                datasets: [{
                    label: 'Hit Count',
                    data: [${cache.activeCacheHits}, ${cache.memoryCacheHits}, ${cache.diskCacheHits}, ${cache.networkLoads}],
                    backgroundColor: ['#4caf50', '#2196f3', '#ff9800', '#f44336']
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    title: { display: true, text: 'Cache Hit Distribution', font: { size: 16 } },
                    legend: { display: false }
                },
                scales: {
                    y: { beginAtZero: true, title: { display: true, text: 'Hit Count' } }
                }
            }
        });
        
        // Decode Comparison Chart
        new Chart(document.getElementById('decodeComparisonChart'), {
            type: 'bar',
            data: {
                labels: ['Network', 'Disk Cache'],
                datasets: [
                    {
                        label: 'Average',
                        data: [${decode.networkAvgDecodeTime}, ${decode.diskAvgDecodeTime}],
                        backgroundColor: '#2196f3'
                    },
                    {
                        label: 'P95',
                        data: [${decode.networkDecodeP95}, ${decode.diskDecodeP95}],
                        backgroundColor: '#ff9800'
                    },
                    {
                        label: 'P99',
                        data: [${decode.networkDecodeP99}, ${decode.diskDecodeP99}],
                        backgroundColor: '#f44336'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    title: { display: true, text: 'Decode Time Comparison (ms)', font: { size: 16 } }
                },
                scales: {
                    y: { beginAtZero: true, title: { display: true, text: 'Time (ms)' } }
                }
            }
        });
        
        // Decode by Size Chart
        new Chart(document.getElementById('decodeBySizeChart'), {
            type: 'bar',
            data: {
                labels: ['Tiny', 'Small', 'Medium', 'Large', 'Huge'],
                datasets: [{
                    label: 'Average Decode Time (ms)',
                    data: [${decode.decodeTinyAvg}, ${decode.decodeSmallAvg}, ${decode.decodeMediumAvg}, ${decode.decodeLargeAvg}, ${decode.decodeHugeAvg}],
                    backgroundColor: ['#9c27b0', '#3f51b5', '#009688', '#ff5722', '#e91e63']
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    title: { display: true, text: 'Decode Time by Image Size', font: { size: 16 } }
                },
                scales: {
                    y: { beginAtZero: true, title: { display: true, text: 'Time (ms)' } }
                }
            }
        });
        """
    }
    
    private fun buildFilterScript(): String {
        return """
        function filterTable() {
            const sourceFilter = document.getElementById('sourceFilter').value.toLowerCase();
            const sizeFilter = document.getElementById('sizeFilter').value.toLowerCase();
            const urlFilter = document.getElementById('urlFilter').value.toLowerCase();
            const rows = document.querySelectorAll('#detailsTable tbody tr');
            
            rows.forEach(row => {
                const source = row.dataset.source.toLowerCase();
                const size = row.dataset.size.toLowerCase();
                const url = row.dataset.url.toLowerCase();
                
                const matchSource = !sourceFilter || source === sourceFilter;
                const matchSize = !sizeFilter || size === sizeFilter;
                const matchUrl = !urlFilter || url.includes(urlFilter);
                
                row.style.display = (matchSource && matchSize && matchUrl) ? '' : 'none';
            });
        }
        """
    }
}
