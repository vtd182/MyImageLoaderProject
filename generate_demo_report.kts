#!/usr/bin/env kotlin

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Sample benchmark data for presentation
val timestamp = System.currentTimeMillis()

val htmlContent = """
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
        
        .highlights h3 {
            margin-bottom: 10px;
        }
        
        .highlights ul {
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
            <p class="timestamp">Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))}</p>
        </div>
        
        <div class="content">
            <div class="section">
                <h2 class="section-title">Executive Summary</h2>
                <div class="summary-cards">
                    <div class="card">
                        <div class="card-title">Overall Score</div>
                        <div class="card-value pass">92.5/100</div>
                        <div class="card-subtitle">Excellent</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Pass Rate</div>
                        <div class="card-value pass">96%</div>
                        <div class="card-subtitle">24/25 tests passed</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Total Duration</div>
                        <div class="card-value info">287.5s</div>
                        <div class="card-subtitle">25 tests executed</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Failed Tests</div>
                        <div class="card-value fail">1</div>
                        <div class="card-subtitle">Review failures</div>
                    </div>
                </div>
            </div>
            
            <div class="section">
                <h2 class="section-title">Device Information</h2>
                <div class="device-info">
                    <div class="device-info-grid">
                        <div class="device-info-item">
                            <span class="device-info-label">Device:</span> Google Pixel 6
                        </div>
                        <div class="device-info-item">
                            <span class="device-info-label">Android:</span> 13 (API 33)
                        </div>
                        <div class="device-info-item">
                            <span class="device-info-label">CPU ABI:</span> arm64-v8a
                        </div>
                        <div class="device-info-item">
                            <span class="device-info-label">Memory:</span> 8192 MB (3456 MB available)
                        </div>
                        <div class="device-info-item">
                            <span class="device-info-label">Screen:</span> 1080x2400 @ 3.0 dpi
                        </div>
                        <div class="device-info-item">
                            <span class="device-info-label">Emulator:</span> No
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="section">
                <div class="highlights">
                    <h3>✨ Highlights</h3>
                    <ul>
                        <li>Cache efficiency: 89% (excellent!)</li>
                        <li>Active cache lookup: 5ms (blazing fast)</li>
                        <li>Bitmap pool hit rate: 62% (great reuse)</li>
                        <li>Avg FPS: 58.3 (smooth scrolling)</li>
                        <li>Peak heap: 198.7MB (well within limits)</li>
                        <li>No memory leaks detected</li>
                    </ul>
                </div>
            </div>
            
            <div class="section">
                <h2 class="section-title">Cache Performance</h2>
                <div class="chart-container">
                    <canvas id="cacheHitRateChart"></canvas>
                </div>
                <div class="chart-container">
                    <canvas id="cacheTimeChart"></canvas>
                </div>
            </div>
            
            <div class="section">
                <h2 class="section-title">Decode Performance</h2>
                <div class="summary-cards">
                    <div class="card">
                        <div class="card-title">Bitmap Pool Hit Rate</div>
                        <div class="card-value pass">62.0%</div>
                        <div class="card-subtitle">Target: ≥50%</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Allocation Reduction</div>
                        <div class="card-value pass">65.0%</div>
                        <div class="card-subtitle">180 vs 520 allocs</div>
                    </div>
                    <div class="card">
                        <div class="card-title">GC Count</div>
                        <div class="card-value pass">5</div>
                        <div class="card-subtitle">Target: <10</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Avg Memory Used</div>
                        <div class="card-value info">145.7 MB</div>
                    </div>
                </div>
                <div class="chart-container">
                    <canvas id="decodeTimeChart"></canvas>
                </div>
            </div>
            
            <div class="section">
                <h2 class="section-title">Transform Performance</h2>
                <div class="summary-cards">
                    <div class="card">
                        <div class="card-title">Avg Transform Time</div>
                        <div class="card-value pass">42 ms</div>
                        <div class="card-subtitle">Target: ≤50ms</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Pool Reuse Rate</div>
                        <div class="card-value pass">58.0%</div>
                        <div class="card-subtitle">Target: ≥40%</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Time Reduction</div>
                        <div class="card-value pass">38.0%</div>
                        <div class="card-subtitle">With pool vs without</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Transform Cache Hit</div>
                        <div class="card-value pass">87.0%</div>
                        <div class="card-subtitle">Target: ≥80%</div>
                    </div>
                </div>
            </div>
            
            <div class="section">
                <h2 class="section-title">Memory Analysis</h2>
                <div class="summary-cards">
                    <div class="card">
                        <div class="card-title">Peak Heap</div>
                        <div class="card-value pass">198.7 MB</div>
                        <div class="card-subtitle">Target: ≤256 MB</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Leak Detected</div>
                        <div class="card-value pass">NO</div>
                        <div class="card-subtitle">0.020 MB/cycle</div>
                    </div>
                    <div class="card">
                        <div class="card-title">GC Count</div>
                        <div class="card-value pass">6</div>
                        <div class="card-subtitle">Avg pause: 2.8ms</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Bitmap Pool Size</div>
                        <div class="card-value pass">38.5 MB</div>
                        <div class="card-subtitle">77.0% utilized</div>
                    </div>
                </div>
            </div>
            
            <div class="section">
                <h2 class="section-title">Scroll Performance</h2>
                <div class="summary-cards">
                    <div class="card">
                        <div class="card-title">Avg FPS</div>
                        <div class="card-value pass">58.3</div>
                        <div class="card-subtitle">Target: ≥55 FPS</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Jank Count</div>
                        <div class="card-value pass">2</div>
                        <div class="card-subtitle">Target: ≤5</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Priority Effectiveness</div>
                        <div class="card-value pass">68.3%</div>
                        <div class="card-subtitle">High vs Low priority</div>
                    </div>
                    <div class="card">
                        <div class="card-title">Memory Stability</div>
                        <div class="card-value pass">2.30</div>
                        <div class="card-subtitle">Variance during scroll</div>
                    </div>
                </div>
            </div>
        </div>
        
        <div class="footer">
            <p>ImageLoader Benchmark System v1.0</p>
            <p>Report generated automatically by HtmlReporter</p>
        </div>
    </div>
    
    <script>
        // Cache Hit Rate Pie Chart
        new Chart(document.getElementById('cacheHitRateChart'), {
            type: 'pie',
            data: {
                labels: ['Active Cache', 'Memory Cache', 'Disk Cache', 'Network'],
                datasets: [{
                    data: [12.0, 45.0, 32.0, 11.0],
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
                    data: [5, 38, 87, 654],
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
        
        // Decode Time Line Chart
        new Chart(document.getElementById('decodeTimeChart'), {
            type: 'line',
            data: {
                labels: ['Tiny (200x200)', 'Small (400x600)', 'Medium (1080x1440)', 'Large (2560x1440)', 'Huge (4096x4096)'],
                datasets: [{
                    label: 'Decode Time (ms)',
                    data: [8, 25, 92, 187, 456],
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
</body>
</html>
""".trimIndent()

// Write to file
val outputDir = File("/Users/lap15116/working/ZTF2025/image-loader/benchmark-results")
if (!outputDir.exists()) {
    outputDir.mkdirs()
}

val htmlFile = File(outputDir, "benchmark-demo-$timestamp.html")
htmlFile.writeText(htmlContent)

println("✅ Demo benchmark report generated:")
println("   📊 HTML: ${htmlFile.absolutePath}")
println("")
println("🎉 Open the HTML file in a browser to view the interactive report!")
println("   Command: open ${htmlFile.absolutePath}")
