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
        tr.outlier { background: #ffebee !important; }
        tr.outlier:hover { background: #ffcdd2 !important; }
        
        details {
            margin: 20px 0;
            border: 1px solid #ddd;
            border-radius: 8px;
            padding: 15px;
            background: #fafafa;
        }
        
        summary {
            cursor: pointer;
            font-weight: 600;
            font-size: 18px;
            color: #667eea;
            user-select: none;
            padding: 10px;
            margin: -15px -15px 15px -15px;
            background: #f0f0f0;
            border-radius: 8px 8px 0 0;
        }
        
        summary:hover {
            background: #e8e8e8;
        }
        
        summary::marker {
            font-size: 1.2em;
        }
        
        .table-wrapper {
            overflow-x: auto;
            margin-top: 15px;
        }
        
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
        
        .modal {
            display: none;
            position: fixed;
            z-index: 1000;
            left: 0;
            top: 0;
            width: 100%;
            height: 100%;
            overflow: auto;
            background-color: rgba(0,0,0,0.5);
        }
        
        .modal-content {
            background-color: #fefefe;
            margin: 2% auto;
            padding: 0;
            border: 1px solid #888;
            border-radius: 10px;
            width: 95%;
            max-width: 1400px;
            max-height: 90vh;
            display: flex;
            flex-direction: column;
        }
        
        .modal-header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 20px 25px;
            border-radius: 10px 10px 0 0;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        
        .modal-header h2 {
            margin: 0;
            border: none;
            padding: 0;
            color: white;
        }
        
        .close {
            color: white;
            font-size: 32px;
            font-weight: bold;
            cursor: pointer;
            line-height: 1;
            transition: color 0.3s;
        }
        
        .close:hover,
        .close:focus {
            color: #ddd;
        }
        
        .modal-body {
            padding: 25px;
            overflow-y: auto;
            flex: 1;
        }
        
        .show-requests-btn {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            padding: 15px 30px;
            font-size: 16px;
            font-weight: 600;
            border-radius: 8px;
            cursor: pointer;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
            transition: transform 0.2s, box-shadow 0.2s;
            display: inline-flex;
            align-items: center;
            gap: 10px;
        }
        
        .show-requests-btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 12px rgba(0,0,0,0.15);
        }
        
        .show-requests-btn:active {
            transform: translateY(0);
        }
        
        .info-box {
            background: #e3f2fd;
            border-left: 4px solid #2196f3;
            padding: 15px;
            border-radius: 4px;
            margin: 20px 0;
        }
        
        .info-box h4 {
            margin: 0 0 10px 0;
            color: #1565c0;
        }
        
        .info-box ul {
            margin: 5px 0;
            padding-left: 20px;
        }
        
        .info-box li {
            margin: 5px 0;
            color: #424242;
        }
        
        .tooltip-icon {
            display: inline-block;
            width: 18px;
            height: 18px;
            background: #2196f3;
            color: white;
            border-radius: 50%;
            text-align: center;
            line-height: 18px;
            font-size: 12px;
            font-weight: bold;
            cursor: help;
            margin-left: 5px;
        }
        
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
        ${buildDecodePerformanceSection(result)}
        ${buildFileSizeAnalysisSection(result)}
        ${buildAllRequestsButton()}
        ${buildAllRequestsModal(result)}
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
        val hasActiveCache = cache.activeCacheHits > 0
        
        return """
        <div class="section">
            <h2>🎯 Cache Hit Distribution <span class="tooltip-icon" title="Phân bố cache hits cho thấy hiệu quả của hệ thống cache">?</span></h2>
            
            <div class="info-box">
                <h4>📖 Giải thích các chỉ số:</h4>
                <ul>
                    ${if (hasActiveCache) "<li><strong>Active Cache</strong>: Ảnh đang hiển thị trên màn hình (tốc độ nhanh nhất, ~0-2ms)</li>" else ""}
                    <li><strong>Memory Cache</strong>: Ảnh đã load gần đây trong RAM (nhanh, ~1-5ms)</li>
                    <li><strong>Disk Cache</strong>: Ảnh được lưu trên ổ cứng (trung bình, ~20-100ms)</li>
                    <li><strong>Network</strong>: Tải ảnh từ internet (chậm nhất, ~100-1000ms)</li>
                    <li><strong>Cache Efficiency</strong>: Tỉ lệ % request không cần tải từ network (càng cao càng tốt)</li>
                </ul>
            </div>
            
            <div class="stats-grid">
                ${if (hasActiveCache) """
                <div class="stat-card active">
                    <div class="label">Active Cache</div>
                    <div class="value">${cache.activeCacheHits}</div>
                    <div class="percent">${String.format("%.1f%%", cache.activeCachePercent)}</div>
                    <div class="label" style="margin-top: 10px;">${String.format("%.0fms avg", cache.avgActiveCacheTime)}</div>
                </div>
                """.trimIndent() else ""}
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
    
    private fun buildDecodePerformanceSection(result: SimplifiedBenchmarkResult): String {
        val decode = result.decodeMetrics
        
        return """
        <div class="section">
            <h2>⚡ Decode Performance Analysis <span class="tooltip-icon" title="Phân tích hiệu suất decode giữa Network và Disk Cache">?</span></h2>
            
            <div class="info-box">
                <h4>📖 Giải thích các chỉ số Percentile:</h4>
                <ul>
                    <li><strong>Min (Minimum)</strong>: Thời gian nhanh nhất - trường hợp tốt nhất</li>
                    <li><strong>P50 (Median/Trung vị)</strong>: 50% requests nhanh hơn giá trị này - đại diện cho trải nghiệm điển hình</li>
                    <li><strong>P95 (Percentile 95)</strong>: 95% requests nhanh hơn giá trị này - gần như hầu hết người dùng</li>
                    <li><strong>P99 (Percentile 99)</strong>: 99% requests nhanh hơn giá trị này - kể cả các trường hợp xấu</li>
                    <li><strong>Max (Maximum)</strong>: Thời gian chậm nhất - trường hợp xấu nhất</li>
                    <li><strong>Tại sao không dùng Average?</strong> Vì average dễ bị ảnh hưởng bởi outliers (giá trị bất thường). P50/P95/P99 cho thấy trải nghiệm thực tế chính xác hơn.</li>
                </ul>
            </div>
            
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 20px;">
                <div>
                    <h3 style="margin-bottom: 15px;">📡 Network Decode Stats</h3>
                    <div class="comparison-box">
                        <div class="comparison-stat">
                            <span class="label">Số lượng</span>
                            <span class="value">${decode.networkDecodeCount} lần</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">Min (Nhanh nhất)</span>
                            <span class="value">${decode.networkMinDecodeTime}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">P50 (50% nhanh hơn)</span>
                            <span class="value">${decode.networkDecodeP50}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">P95 (95% nhanh hơn)</span>
                            <span class="value">${decode.networkDecodeP95}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">P99 (99% nhanh hơn)</span>
                            <span class="value">${decode.networkDecodeP99}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">Max (Chậm nhất)</span>
                            <span class="value">${decode.networkMaxDecodeTime}ms</span>
                        </div>
                    </div>
                </div>
                
                <div>
                    <h3 style="margin-bottom: 15px;">💾 Disk Cache Decode Stats</h3>
                    <div class="comparison-box">
                        <div class="comparison-stat">
                            <span class="label">Số lượng</span>
                            <span class="value">${decode.diskDecodeCount} lần</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">Min (Nhanh nhất)</span>
                            <span class="value">${decode.diskMinDecodeTime}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">P50 (50% nhanh hơn)</span>
                            <span class="value">${decode.diskDecodeP50}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">P95 (95% nhanh hơn)</span>
                            <span class="value">${decode.diskDecodeP95}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">P99 (99% nhanh hơn)</span>
                            <span class="value">${decode.diskDecodeP99}ms</span>
                        </div>
                        <div class="comparison-stat">
                            <span class="label">Max (Chậm nhất)</span>
                            <span class="value">${decode.diskMaxDecodeTime}ms</span>
                        </div>
                    </div>
                </div>
            </div>
            
            <h3 style="margin-top: 30px; margin-bottom: 15px;">📊 So sánh P95 và P99</h3>
            <p style="color: #666; margin-bottom: 15px;">
                Biểu đồ này cho thấy thời gian decode ở percentile 95 và 99 - đại diện cho trải nghiệm của hầu hết người dùng.
                <strong>Lưu ý:</strong> Không so sánh Average vì nó không phản ánh chính xác trải nghiệm thực tế.
            </p>
            <div class="chart-container">
                <canvas id="decodeComparisonChart"></canvas>
            </div>
        </div>
        """
    }
    
    private fun buildAllRequestsButton(): String {
        return """
        <div class="section" style="text-align: center;">
            <button class="show-requests-btn" onclick="document.getElementById('allRequestsModal').style.display='block'">
                📋 Xem Chi Tiết Tất Cả Requests
            </button>
        </div>
        """
    }
    
    private fun buildAllRequestsModal(result: SimplifiedBenchmarkResult): String {
        val data = result.rawRequestData
        if (data.isEmpty()) return ""
        
        val networkCount = data.count { it.source == "NETWORK" }
        val diskCount = data.count { it.source == "DISK_CACHE" }
        
        return """
        <div id="allRequestsModal" class="modal">
            <div class="modal-content">
                <div class="modal-header">
                    <h2>📊 Chi Tiết Tất Cả Requests</h2>
                    <span class="close" onclick="document.getElementById('allRequestsModal').style.display='none'">&times;</span>
                </div>
                <div class="modal-body">
                    <div class="info-box">
                        <h4>📖 Giải thích các chỉ số thời gian:</h4>
                        <ul>
                            <li><strong>Total Time</strong>: Tổng thời gian hoàn thành request từ đầu đến cuối</li>
                            <li><strong>Fetch</strong>: Thời gian tải dữ liệu (từ network hoặc disk)</li>
                            <li><strong>Decode</strong>: Thời gian giải mã ảnh thành bitmap</li>
                            <li><strong>Transform</strong>: Thời gian xử lý ảnh (resize, crop, rounded corners...)</li>
                            <li><strong>File Size</strong>: Kích thước file ảnh thực tế (KB)</li>
                            <li><strong>Image Size</strong>: Phân loại ảnh (tiny &lt; 100KB, small 100-500KB, medium 500KB-1MB, large 1-2MB, huge &gt; 2MB)</li>
                        </ul>
                    </div>
                    
                    <p style="color: #666; margin-bottom: 15px;">
                        <strong>Tổng cộng:</strong> ${data.size} requests | <strong>Network:</strong> $networkCount | <strong>Disk Cache:</strong> $diskCount
                    </p>
            
            <div class="filter-controls" style="margin-bottom: 15px;">
                <select id="sourceFilter" onchange="filterAndSortTable()">
                    <option value="">All Sources</option>
                    <option value="NETWORK">Network Only</option>
                    <option value="DISK_CACHE">Disk Cache Only</option>
                </select>
                
                <select id="sizeFilter" onchange="filterAndSortTable()">
                    <option value="">All Sizes</option>
                    <option value="tiny">Tiny</option>
                    <option value="small">Small</option>
                    <option value="medium">Medium</option>
                    <option value="large">Large</option>
                    <option value="huge">Huge</option>
                </select>
                
                <select id="sortBy" onchange="filterAndSortTable()">
                    <option value="default">Sort: Default Order</option>
                    <option value="time-desc">Sort: Time (High → Low)</option>
                    <option value="time-asc">Sort: Time (Low → High)</option>
                    <option value="filesize-desc">Sort: File Size (Large → Small)</option>
                    <option value="filesize-asc">Sort: File Size (Small → Large)</option>
                    <option value="url">Sort: URL (A → Z)</option>
                </select>
                
                <input type="text" id="urlFilter" placeholder="Search URL..." onkeyup="filterAndSortTable()" style="flex: 1; min-width: 250px;">
                
                <span id="filteredCount" style="margin-left: 10px; color: #666; font-weight: 600;"></span>
            </div>
            
            <div class="table-wrapper">
                <table id="rawDataTable">
                    <thead>
                        <tr>
                            <th>Source</th>
                            <th>URL</th>
                            <th>Total Time</th>
                            <th>Fetch</th>
                            <th>Decode</th>
                            <th>Transform</th>
                            <th>File Size</th>
                            <th>Image Size</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${data.joinToString("") { item ->
                            val fileSizeKB = item.fileSizeBytes?.let { String.format("%.1f", it / 1024.0) } ?: "N/A"
                            val fileSizeBytes = item.fileSizeBytes ?: 0
                            val sourceClass = if (item.source == "NETWORK") "network" else "disk"
                            """
                            <tr data-source="${item.source}" 
                                data-size="${item.imageSize}" 
                                data-url="${item.url}" 
                                data-time="${item.totalTime}"
                                data-filesize="$fileSizeBytes"
                                data-index="${data.indexOf(item)}">
                                <td><span class="badge $sourceClass">${if (item.source == "NETWORK") "NET" else "DISK"}</span></td>
                                <td class="url-cell" title="${item.url}">${item.url}</td>
                                <td><strong>${item.totalTime}ms</strong></td>
                                <td>${item.fetchTime?.let { "${it}ms" } ?: "-"}</td>
                                <td>${item.decodeTime?.let { "${it}ms" } ?: "-"}</td>
                                <td>${item.transformTime?.let { "${it}ms" } ?: "-"}</td>
                                <td>$fileSizeKB ${if (fileSizeBytes > 0) "KB" else ""}</td>
                                <td><span class="badge ${item.imageSize}">${item.imageSize.uppercase()}</span></td>
                            </tr>
                            """.trimIndent()
                        }}
                    </tbody>
                </table>
            </div>
                </div>
            </div>
        </div>
        
        <script>
            // Close modal when clicking outside
            window.onclick = function(event) {
                const modal = document.getElementById('allRequestsModal');
                if (event.target == modal) {
                    modal.style.display = 'none';
                }
            }
        </script>
        """
    }
    
    private fun buildChartsScript(result: SimplifiedBenchmarkResult): String {
        val cache = result.cacheMetrics
        val decode = result.decodeMetrics
        val hasActiveCache = cache.activeCacheHits > 0
        
        val labels = buildList {
            if (hasActiveCache) add("'Active Cache'")
            add("'Memory Cache'")
            add("'Disk Cache'")
            add("'Network'")
        }.joinToString(", ")
        
        val data = buildList {
            if (hasActiveCache) add(cache.activeCacheHits)
            add(cache.memoryCacheHits)
            add(cache.diskCacheHits)
            add(cache.networkLoads)
        }.joinToString(", ")
        
        val colors = buildList {
            if (hasActiveCache) add("'#4caf50'")
            add("'#2196f3'")
            add("'#ff9800'")
            add("'#f44336'")
        }.joinToString(", ")
        
        return """
        // Cache Hits Chart
        new Chart(document.getElementById('cacheHitsChart'), {
            type: 'bar',
            data: {
                labels: [$labels],
                datasets: [{
                    label: 'Hit Count',
                    data: [$data],
                    backgroundColor: [$colors]
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
        
        // Decode Comparison Chart (P95 and P99 only)
        new Chart(document.getElementById('decodeComparisonChart'), {
            type: 'bar',
            data: {
                labels: ['Network', 'Disk Cache'],
                datasets: [
                    {
                        label: 'P50 (Median)',
                        data: [${decode.networkDecodeP50}, ${decode.diskDecodeP50}],
                        backgroundColor: '#2196f3'
                    },
                    {
                        label: 'P95 (95% người dùng)',
                        data: [${decode.networkDecodeP95}, ${decode.diskDecodeP95}],
                        backgroundColor: '#ff9800'
                    },
                    {
                        label: 'P99 (99% người dùng)',
                        data: [${decode.networkDecodeP99}, ${decode.diskDecodeP99}],
                        backgroundColor: '#f44336'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    title: { 
                        display: true, 
                        text: 'So sánh Decode Time: Network vs Disk Cache (ms)', 
                        font: { size: 16 } 
                    },
                    legend: {
                        display: true,
                        position: 'bottom'
                    }
                },
                scales: {
                    y: { 
                        beginAtZero: true, 
                        title: { display: true, text: 'Thời gian (ms)' } 
                    }
                }
            }
        });
        """
    }
    
    private fun buildFilterScript(): String {
        return """
        let originalRowsOrder = [];
        
        // Store original order on page load
        window.addEventListener('DOMContentLoaded', () => {
            const tbody = document.querySelector('#rawDataTable tbody');
            if (tbody) {
                originalRowsOrder = Array.from(tbody.querySelectorAll('tr'));
            }
        });
        
        function filterAndSortTable() {
            const sourceFilter = document.getElementById('sourceFilter').value;
            const sizeFilter = document.getElementById('sizeFilter').value;
            const urlFilter = document.getElementById('urlFilter').value.toLowerCase();
            const sortBy = document.getElementById('sortBy').value;
            const tbody = document.querySelector('#rawDataTable tbody');
            
            if (!tbody) return;
            
            let rows = Array.from(tbody.querySelectorAll('tr'));
            
            // Filter
            const filteredRows = rows.filter(row => {
                const source = row.dataset.source;
                const size = row.dataset.size;
                const url = row.dataset.url.toLowerCase();
                
                const matchSource = !sourceFilter || source === sourceFilter;
                const matchSize = !sizeFilter || size === sizeFilter;
                const matchUrl = !urlFilter || url.includes(urlFilter);
                
                return matchSource && matchSize && matchUrl;
            });
            
            // Sort
            let sortedRows = filteredRows;
            switch (sortBy) {
                case 'time-desc':
                    sortedRows = filteredRows.sort((a, b) => 
                        parseInt(b.dataset.time) - parseInt(a.dataset.time)
                    );
                    break;
                case 'time-asc':
                    sortedRows = filteredRows.sort((a, b) => 
                        parseInt(a.dataset.time) - parseInt(b.dataset.time)
                    );
                    break;
                case 'filesize-desc':
                    sortedRows = filteredRows.sort((a, b) => 
                        parseInt(b.dataset.filesize) - parseInt(a.dataset.filesize)
                    );
                    break;
                case 'filesize-asc':
                    sortedRows = filteredRows.sort((a, b) => 
                        parseInt(a.dataset.filesize) - parseInt(b.dataset.filesize)
                    );
                    break;
                case 'url':
                    sortedRows = filteredRows.sort((a, b) => 
                        a.dataset.url.localeCompare(b.dataset.url)
                    );
                    break;
                case 'default':
                default:
                    sortedRows = filteredRows.sort((a, b) => 
                        parseInt(a.dataset.index) - parseInt(b.dataset.index)
                    );
                    break;
            }
            
            // Hide all rows first
            rows.forEach(row => row.style.display = 'none');
            
            // Show and reorder filtered/sorted rows
            sortedRows.forEach(row => {
                row.style.display = '';
                tbody.appendChild(row);
            });
            
            // Update count
            const countEl = document.getElementById('filteredCount');
            if (countEl) {
                const totalCount = rows.length;
                const visibleCount = sortedRows.length;
                if (visibleCount === totalCount) {
                    countEl.textContent = 'Showing all ' + totalCount + ' requests';
                } else {
                    countEl.textContent = 'Showing ' + visibleCount + ' of ' + totalCount + ' requests';
                }
            }
        }
        
        // Initialize on load
        window.addEventListener('DOMContentLoaded', filterAndSortTable);
        """
    }
    
    private fun buildFileSizeAnalysisSection(result: SimplifiedBenchmarkResult): String {
        val stats = result.fileSizeStats ?: return ""
        
        return """
        <div class="section">
            <h2>📦 File Size Analysis <span class="tooltip-icon" title="Phân tích kích thước file để đánh giá bandwidth sử dụng">?</span></h2>
            
            <div class="info-box">
                <h4>📖 Ý nghĩa của File Size Analysis:</h4>
                <ul>
                    <li><strong>Average File Size</strong>: Kích thước trung bình của mỗi ảnh - giúp ước tính bandwidth cần thiết</li>
                    <li><strong>Total Downloaded</strong>: Tổng dung lượng đã tải - quan trọng để tính data usage</li>
                    <li><strong>Size Distribution</strong>: Phân bố kích thước file - giúp tối ưu cache size</li>
                    <li><strong>Lưu ý</strong>: File size nhỏ hơn thì tốc độ tải nhanh hơn, nhưng chất lượng ảnh có thể thấp hơn</li>
                </ul>
            </div>
            
            <div class="stats-grid">
                <div class="stat-card">
                    <div class="label">Average File Size</div>
                    <div class="value">${String.format("%.1f", stats.avgFileSizeKB)} KB</div>
                    <div class="percent">${String.format("%.2f", stats.avgFileSizeBytes / 1024.0 / 1024.0)} MB</div>
                </div>
                <div class="stat-card">
                    <div class="label">Min File Size</div>
                    <div class="value">${String.format("%.1f", stats.minFileSizeBytes / 1024.0)} KB</div>
                </div>
                <div class="stat-card">
                    <div class="label">Max File Size</div>
                    <div class="value">${String.format("%.1f", stats.maxFileSizeBytes / 1024.0)} KB</div>
                </div>
                <div class="stat-card">
                    <div class="label">Total Downloaded</div>
                    <div class="value">${String.format("%.1f", stats.totalFileSizeKB / 1024.0)} MB</div>
                </div>
            </div>
            
            <h3 style="margin-top: 20px; margin-bottom: 10px;">Average Size by Category</h3>
            <div class="comparison-box">
                ${stats.fileSizeByCategory.entries.joinToString("") { (category, avgSize) ->
                    """
                    <div class="comparison-stat">
                        <span class="label">${category.uppercase()}</span>
                        <span class="value">${String.format("%.1f KB", avgSize / 1024.0)}</span>
                    </div>
                    """.trimIndent()
                }}
            </div>
            
            <h3 style="margin-top: 20px; margin-bottom: 10px;">Size Distribution</h3>
            <div class="chart-container">
                <canvas id="fileSizeDistChart"></canvas>
            </div>
            
            <script>
                new Chart(document.getElementById('fileSizeDistChart'), {
                    type: 'bar',
                    data: {
                        labels: [${stats.sizeDistribution.joinToString(", ") { "'${it.first}'" }}],
                        datasets: [{
                            label: 'File Count',
                            data: [${stats.sizeDistribution.joinToString(", ") { it.second.toString() }}],
                            backgroundColor: '#667eea'
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: {
                            title: { display: true, text: 'File Size Distribution', font: { size: 16 } }
                        },
                        scales: {
                            y: { beginAtZero: true, title: { display: true, text: 'Count' } }
                        }
                    }
                });
            </script>
        </div>
        """
    }
    
}
