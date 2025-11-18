package com.example.imageloader.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.imageloader.R
import com.example.imageloader.logger.ImageLoaderLogger
import com.example.imageloader.logger.LogCategory
import com.example.imageloader.logger.LogEntry
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * LogViewerActivity - Activity hiển thị logs của ImageLoader library.
 *
 * ## Mục đích:
 * In-app debug UI để developers và QA có thể:
 * - Xem real-time logs mà không cần adb/logcat
 * - Monitor image loading performance
 * - Debug cache issues và network errors
 * - Analyze performance bottlenecks
 *
 * ## Features:
 *
 * ### 1. Real-time Log Display:
 * - RecyclerView với LogAdapter
 * - Auto-scroll to top khi có log mới
 * - Color-coded by log level
 * - Icons cho visual identification (✅❌⚠️)
 *
 * ### 2. Quick Stats Bar:
 * - Total logs count
 * - Memory/Disk/Network counts
 * - Error count
 * - Updates real-time
 *
 * ### 3. Filter Dialog:
 * - Filter by LogCategory (ENGINE, CACHE, NETWORK, etc.)
 * - Multi-select với Material Chips
 * - Live filtering khi thay đổi selection
 *
 * ### 4. Detailed Stats Dialog:
 * - Total logs và image requests
 * - Cache hit rates (Memory, Disk, Network)
 * - Average load times per source
 * - Average fetch/decode/transform times
 * - Error counts breakdown
 *
 * ### 5. Actions:
 * - **Clear**: Xóa tất cả logs (có confirmation)
 * - **Open URL**: Tap vào log để open URL trong browser
 * - **Back**: Close activity
 *
 * ## Architecture:
 * ```
 * LogViewerActivity
 *     ↓
 * ├─ LogAdapter (RecyclerView)
 * │   ├─ LogEntryFilter (filtering logic)
 * │   ├─ LogEntryUiModelMapper (transform to UI model)
 * │   └─ LogViewHolder (bind UI)
 * ├─ QuickStatsFormatter (quick stats bar)
 * └─ ImageLoaderLogger (data source)
 * ```
 *
 * ## Lifecycle:
 * ```
 * onCreate():
 * - Setup RecyclerView
 * - Load initial logs
 * - Register listener
 * - Show quick stats
 *
 * onDestroy():
 * - Unregister listener
 * - Cleanup
 * ```
 *
 * ## UI Components:
 * - **RecyclerView**: Main log list
 * - **Quick Stats Bar**: Top bar với counts
 * - **Toolbar**: Back, Stats, Filter, Clear buttons
 * - **Filter Dialog**: Category selection
 * - **Stats Dialog**: Detailed metrics
 * - **Clear Dialog**: Confirmation dialog
 *
 * ## Performance:
 * - RecyclerView với ViewHolder pattern (efficient scrolling)
 * - DiffUtil KHÔNG dùng (simple insert at position 0)
 * - Real-time updates via listener pattern
 * - Filtering on main thread (500 logs = fast enough)
 *
 * ## Testing:
 * Activity có thể launch từ:
 * - LogViewer.open(context)
 * - Direct Intent to LogViewerActivity
 *
 * @see com.example.imageloader.logger.LogViewer
 * @see com.example.imageloader.logger.ImageLoaderLogger
 * @see com.example.imageloader.ui.LogAdapter
 */
class LogViewerActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var txtQuickStats: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnStats: ImageView
    private lateinit var btnFilter: ImageView
    private lateinit var btnClear: ImageView
    private lateinit var adapter: LogAdapter
    private val selectedCategories = mutableSetOf<LogCategory>()
    private val quickStatsFormatter = QuickStatsFormatter()

    private val logListener: (LogEntry) -> Unit = { log ->
        runOnUiThread {
            adapter.addLog(log, selectedCategories)
            recyclerView.smoothScrollToPosition(0)
            updateQuickStats()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.imageloader_activity_log_viewer)

        recyclerView = findViewById(R.id.recyclerViewLogs)
        txtQuickStats = findViewById(R.id.txtQuickStats)
        btnBack = findViewById(R.id.btnBack)
        btnStats = findViewById(R.id.btnStats)
        btnFilter = findViewById(R.id.btnFilter)
        btnClear = findViewById(R.id.btnClear)

        adapter = LogAdapter { url ->
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        LogCategory.entries.forEach { selectedCategories.add(it) }
        adapter.submitLogs(ImageLoaderLogger.getAllLogs().reversed(), selectedCategories)

        btnBack.setOnClickListener { finish() }
        btnStats.setOnClickListener { showStatsDialog() }
        btnFilter.setOnClickListener { showFilterDialog() }
        btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.imageloader_clear_title)
                .setMessage(R.string.imageloader_clear_message)
                .setPositiveButton(R.string.imageloader_clear) { _, _ ->
                    ImageLoaderLogger.clear()
                    adapter.clearLogs()
                    updateQuickStats()
                }
                .setNegativeButton(R.string.imageloader_cancel, null)
                .show()
        }

        ImageLoaderLogger.addListener(logListener)
        updateQuickStats()
    }

    private fun showStatsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.imageloader_dialog_stats, null)
        val stats = ImageLoaderLogger.getLogStats()

        dialogView.findViewById<TextView>(R.id.txtDialogTotalLogs).text = stats.totalLogs.toString()
        dialogView.findViewById<TextView>(R.id.txtDialogImageRequests).text =
            stats.totalImageRequests.toString()
        dialogView.findViewById<TextView>(R.id.txtDialogErrors).text =
            (stats.imageErrors + stats.messageErrors).toString()

        val totalMemory = stats.activeCacheCount + stats.memoryCacheCount
        val avgMemoryTime = if (totalMemory > 0) {
            ((stats.activeCacheAvgTime * stats.activeCacheCount + stats.memoryCacheAvgTime * stats.memoryCacheCount) / totalMemory)
        } else 0.0

        dialogView.findViewById<TextView>(R.id.txtDialogMemoryCount).text =
            getString(R.string.imageloader_images_count, totalMemory)
        dialogView.findViewById<TextView>(R.id.txtDialogMemoryTime).text =
            getString(R.string.imageloader_time_with_unit, avgMemoryTime)

        dialogView.findViewById<TextView>(R.id.txtDialogDiskCount).text =
            getString(R.string.imageloader_images_count, stats.diskCacheCount)
        dialogView.findViewById<TextView>(R.id.txtDialogDiskTime).text =
            getString(R.string.imageloader_time_with_unit, stats.diskCacheAvgTime)
        dialogView.findViewById<TextView>(R.id.txtDialogDiskDecode).text =
            getString(R.string.imageloader_time_with_unit, stats.diskCacheAvgDecode)
        dialogView.findViewById<TextView>(R.id.txtDialogDiskTransform).text =
            getString(R.string.imageloader_time_with_unit, stats.diskCacheAvgTransform)

        // JsonBackup section - ẩn nếu không có data
        val hasJsonBackupData = stats.jsonPhotoCount > 0 && stats.jsonCurrentPage > 0
        dialogView.findViewById<TextView>(R.id.txtJsonBackupTitle).visibility =
            if (hasJsonBackupData) View.VISIBLE else View.GONE
        dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.cardJsonBackup).visibility =
            if (hasJsonBackupData) View.VISIBLE else View.GONE

        if (hasJsonBackupData) {
            dialogView.findViewById<TextView>(R.id.txtDialogJsonCount).text =
                stats.jsonPhotoCount.toString()
            dialogView.findViewById<TextView>(R.id.txtDialogJsonPage).text =
                stats.jsonCurrentPage.toString()
        }

        dialogView.findViewById<TextView>(R.id.txtDialogNetworkCount).text =
            getString(R.string.imageloader_images_count, stats.networkCount)
        dialogView.findViewById<TextView>(R.id.txtDialogNetworkTime).text =
            getString(R.string.imageloader_time_with_unit, stats.networkAvgTime)
        dialogView.findViewById<TextView>(R.id.txtDialogNetworkFetch).text =
            getString(R.string.imageloader_time_with_unit, stats.networkAvgFetch)
        dialogView.findViewById<TextView>(R.id.txtDialogNetworkDecode).text =
            getString(R.string.imageloader_time_with_unit, stats.networkAvgDecode)
        dialogView.findViewById<TextView>(R.id.txtDialogNetworkTransform).text =
            getString(R.string.imageloader_time_with_unit, stats.networkAvgTransform)

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.imageloader_close, null)
            .show()
    }

    private fun showFilterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.imageloader_dialog_filter, null)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroupFilter)
        val tempSelected = selectedCategories.toMutableSet()

        LogCategory.entries.forEach { category ->
            val chip = Chip(this).apply {
                text = category.displayName
                isCheckable = true
                isChecked = category in selectedCategories
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) tempSelected.add(category)
                    else tempSelected.remove(category)
                }
            }
            chipGroup.addView(chip)
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
            .apply {
                show()
                dialogView.findViewById<Button>(R.id.btnSelectAll).setOnClickListener {
                    for (i in 0 until chipGroup.childCount) {
                        (chipGroup.getChildAt(i) as? Chip)?.isChecked = true
                    }
                }
                dialogView.findViewById<Button>(R.id.btnApply).setOnClickListener {
                    selectedCategories.clear()
                    selectedCategories.addAll(tempSelected)
                    adapter.filterByCategories(selectedCategories)
                    updateQuickStats()
                    dismiss()
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        ImageLoaderLogger.removeListener(logListener)
    }

    private fun updateQuickStats() {
        val stats = ImageLoaderLogger.getLogStats()
        val quickStats = quickStatsFormatter.format(stats)
        txtQuickStats.text = getString(
            R.string.imageloader_quick_stats_format,
            quickStats.totalLogs,
            quickStats.memoryCount,
            quickStats.diskCount,
            quickStats.networkCount,
            quickStats.errorCount
        )
    }
}
