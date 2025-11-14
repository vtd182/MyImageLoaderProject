package com.example.imageloader.benchmark.suite

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.imageloader.benchmark.reporter.SimplifiedAnalyzer
import com.example.imageloader.benchmark.reporter.SimplifiedHtmlReporter
import com.example.imageloader.logger.ImageLoadLog
import com.example.imageloader.logger.ImageLoaderLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * RealisticMacroBenchmark V4.0
 *
 * Mô phỏng hành vi cuộn thực tế của người dùng khi duyệt một danh sách ảnh rất dài.
 * Bài test được chia thành bốn giai đoạn, tương ứng với các mô típ sử dụng phổ biến
 * trong ứng dụng xem ảnh (feed mạng xã hội, thư viện ảnh, trang khám phá).
 *
 * 1. Phase 1: Cuộn xuống để tải số lượng ảnh lớn lần đầu.
 *    Mục tiêu: làm nóng hệ thống, tải nhiều ảnh mới qua mạng, xây dựng bộ nhớ đệm.
 *
 * 2. Phase 2: Cuộn ngược lên để kiểm tra khả năng tái sử dụng cache.
 *    Mục tiêu: đánh giá Memory Cache, Disk Cache và Active Resources.
 *
 * 3. Phase 3: Cuộn nhanh lên xuống liên tục.
 *    Mục tiêu: stress ActiveResources, tạo bind/unbind liên tục trong RecyclerView.
 *
 * 4. Phase 4: Cuộn sâu xuống dưới cùng, rồi lên, rồi xuống lại.
 *    Mục tiêu: mô phỏng phiên sử dụng dài, phân bổ lại bộ nhớ, kiểm tra khả năng khôi phục cache.
 *
 * Sau khi hoàn thành, hệ thống tạo báo cáo HTML và JSON với thống kê chi tiết về:
 * - Tỷ lệ cache hit theo từng tầng cache
 * - Số lượng request
 * - Hiệu suất cache tổng hợp
 * - So sánh tốc độ Disk và Network
 *
 * Đây là bài test tổng hợp bao quát toàn bộ pipeline của ImageLoader,
 * từ Active Cache, Memory Cache, Disk Cache cho đến Network.
 */
@RunWith(AndroidJUnit4::class)
class RealisticMacroBenchmark {

    // -----------------------------
    // Cấu hình chung
    // -----------------------------
    private val targetUniqueImages = 200
    private val baseWaitMs = 2200L
    private val maxWaitMs = 5000L
    private val pollIntervalMs = 200L

    private val waitMultiplier = mapOf(
        "tiny" to 1.0,
        "small" to 1.2,
        "medium" to 1.5,
        "large" to 2.0,
        "huge" to 2.5
    )

    private val stepPercent = 0.7

    // -----------------------------
    private lateinit var context: Context
    private lateinit var outputDir: File
    private lateinit var scenario: ActivityScenario<BenchmarkTestActivity>

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        outputDir = File(context.getExternalFilesDir(null), "benchmark-results").apply { mkdirs() }

        ImageLoaderLogger.saveToActivity = true
        ImageLoaderLogger.clear()
        ImageLoaderLogger.resetBitmapPoolStats()

        log("Setup hoàn tất")
    }

    @Test
    fun testRealisticScrollBehavior() = runBlocking {

        log("Bắt đầu RealisticMacroBenchmark V4.0")
        log("Mục tiêu Phase 1: tải $targetUniqueImages ảnh unique")

        scenario = launchBenchmarkActivity(itemCount = 1000)
        delay(2000)

        val step = calculateScrollStep()

        val startTime = System.currentTimeMillis()

        runPhase1(step)
        runPhase2(step)
        runPhase3(step)
        runPhase4(step)

        val endTime = System.currentTimeMillis()

        printFinalStats(startTime, endTime)
        generateReports(startTime, endTime)

        scenario.close()
        log("Benchmark hoàn thành")
    }

    // ============================================================
    // Phase 1
    // ============================================================

    private suspend fun runPhase1(step: Int) {
        logHeader("Phase 1: cuộn xuống để tải ảnh mới")

        var scrollCount = 0
        var previousTotal = 0

        while (true) {
            val logs = ImageLoaderLogger.getAllLogs().filterIsInstance<ImageLoadLog>()
                .filter { it.error == null }
            val unique = logs.map { it.url }.distinct().size
            val total = logs.size

            if (unique >= targetUniqueImages) {
                log("Đã tải đủ $unique ảnh unique")
                break
            }

            if (scrollCount >= 500) {
                log("Chạm giới hạn 500 scroll, dừng Phase 1")
                break
            }

            scroll(step)
            scrollCount++

            val recent = getRecentImageSizes(logs, previousTotal, total)
            val waitTime = calculateWaitMs(recent)
            delay(waitTime)

            waitForLoadCompletion(previousTotal, total)

            if (scrollCount % 5 == 0) {
                log("Scroll $scrollCount - Unique: $unique/$targetUniqueImages - Logs: $total")
            }

            previousTotal = total
        }
    }

    // ============================================================
    // Phase 2
    // ============================================================

    private suspend fun runPhase2(step: Int) {
        logHeader("Phase 2: cuộn lên để kiểm tra cache")

        val scrollBackTimes = 200
        repeat(scrollBackTimes) { i ->
            scroll(-step)
            delay(500)
            if (i % 20 == 0) log("Đã cuộn lên $i lần")
        }

        delay(1500)

        logCacheStats()
    }

    // ============================================================
    // Phase 3
    // ============================================================

    private suspend fun runPhase3(step: Int) {
        logHeader("Phase 3: stress ActiveResources bằng flick scroll")

        repeat(60) { i ->
            scroll(if (i % 2 == 0) step else -step)
            delay(220)
        }

        delay(1500)
        logCacheStats()
    }

    // ============================================================
    // Phase 4
    // ============================================================

    private suspend fun runPhase4(step: Int) {
        logHeader("Phase 4: mô phỏng phiên sử dụng dài")

        repeat(120) { scroll(step); delay(350) }
        delay(2000)

        repeat(120) { scroll(-step); delay(300) }
        delay(1500)

        repeat(80) { scroll(step); delay(320) }
        delay(2000)

        logCacheStats()
    }

    // ============================================================
    // Báo cáo cuối
    // ============================================================

    private fun printFinalStats(start: Long, end: Long) {
        val stats = ImageLoaderLogger.getLogStats()
        val total = stats.totalImageRequests

        fun pct(v: Int) = if (total > 0) "%.1f%%".format(v * 100.0 / total) else "0%"

        logHeader("Kết quả cuối cùng")

        log("Tổng request: $total")
        log("Active Cache: ${stats.activeCacheCount} (${pct(stats.activeCacheCount)})")
        log("Memory Cache: ${stats.memoryCacheCount} (${pct(stats.memoryCacheCount)})")
        log("Disk Cache:   ${stats.diskCacheCount} (${pct(stats.diskCacheCount)})")
        log("Network:      ${stats.networkCount} (${pct(stats.networkCount)})")
        log("Thời gian test: ${(end - start) / 1000}s")

        val efficiency = if (total > 0)
            (stats.activeCacheCount + stats.memoryCacheCount + stats.diskCacheCount) * 100.0 / total
        else 0.0

        log("Hiệu suất cache tổng hợp: %.1f%%".format(efficiency))
    }

    private fun generateReports(start: Long, end: Long) {
        val result = SimplifiedAnalyzer.analyze(start, end, null)
        val timestamp = System.currentTimeMillis()

        val html = File(outputDir, "realistic-benchmark-$timestamp.html")

        SimplifiedHtmlReporter.generate(result, html)

        log("Đã tạo report HTML")
        log("Thư mục: ${outputDir.absolutePath}")
    }

    // ============================================================
    // Helper
    // ============================================================

    private fun launchBenchmarkActivity(itemCount: Int): ActivityScenario<BenchmarkTestActivity> {
        val intent = Intent(context, BenchmarkTestActivity::class.java)
            .putExtra("ITEM_COUNT", itemCount)
        return ActivityScenario.launch(intent)
    }

    private fun calculateScrollStep(): Int {
        var step = 0
        scenario.onActivity { activity ->
            val height = activity.recyclerView.height
            step = (height * stepPercent).toInt()
            log("Chiều cao screen: $height - step = $step")
        }
        return step
    }

    private fun scroll(distance: Int) {
        scenario.onActivity { it.recyclerView.scrollBy(0, distance) }
    }

    private fun getRecentImageSizes(all: List<ImageLoadLog>, prev: Int, curr: Int): List<String> {
        val count = (curr - prev).coerceAtLeast(0)
        if (count == 0) return emptyList()

        return all.takeLast(count).map { detectImageSize(it.url) }
    }

    private fun detectImageSize(url: String): String {
        return when {
            "/200/200" in url -> "tiny"
            "/400/600" in url -> "small"
            "/1080/1440" in url -> "medium"
            "/2560/1440" in url -> "large"
            "/4096/4096" in url -> "huge"
            else -> "small"
        }
    }

    private fun calculateWaitMs(sizes: List<String>): Long {
        if (sizes.isEmpty()) return baseWaitMs
        val maxMul = sizes.mapNotNull { waitMultiplier[it] }.maxOrNull() ?: 1.0
        return (baseWaitMs * maxMul).toLong().coerceAtMost(maxWaitMs)
    }

    private suspend fun waitForLoadCompletion(prev: Int, expectedMin: Int): Boolean {
        var last = prev
        repeat(10) {
            delay(pollIntervalMs)

            val logs = ImageLoaderLogger.getAllLogs().filterIsInstance<ImageLoadLog>()
                .filter { it.error == null }
            val curr = logs.size

            if (curr == last && curr >= expectedMin) return true
            last = curr
        }
        return false
    }

    private fun logCacheStats() {
        val s = ImageLoaderLogger.getLogStats()
        log("ActiveCache: ${s.activeCacheCount}, MemoryCache: ${s.memoryCacheCount}, DiskCache: ${s.diskCacheCount}, Network: ${s.networkCount}")
    }

    private fun log(msg: String) = println("[Benchmark] $msg")

    private fun logHeader(title: String) {
        println("\n==================== $title ====================")
    }
}
