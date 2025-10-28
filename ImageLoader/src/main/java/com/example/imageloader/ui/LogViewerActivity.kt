package com.example.imageloader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.imageloader.R
import com.example.imageloader.logger.ImageLoaderLogger
import com.example.imageloader.logger.LogCategory
import com.example.imageloader.logger.LogEntry
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LogViewerActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var txtQuickStats: TextView
    private lateinit var btnStats: ImageView
    private lateinit var btnFilter: ImageView
    private lateinit var btnClear: ImageView
    private lateinit var adapter: LogAdapter
    private val selectedCategories = mutableSetOf<LogCategory>()
    
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
        btnStats = findViewById(R.id.btnStats)
        btnFilter = findViewById(R.id.btnFilter)
        btnClear = findViewById(R.id.btnClear)

        adapter = LogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        LogCategory.values().forEach { selectedCategories.add(it) }
        adapter.submitLogs(ImageLoaderLogger.getAllLogs().reversed(), selectedCategories)

        btnStats.setOnClickListener { showStatsDialog() }
        btnFilter.setOnClickListener { showFilterDialog() }
        btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear Logs")
                .setMessage("Are you sure you want to clear all logs?")
                .setPositiveButton("Clear") { _, _ ->
                    ImageLoaderLogger.clear()
                    adapter.clearLogs()
                    updateQuickStats()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        ImageLoaderLogger.addListener(logListener)
        updateQuickStats()
    }
    
    private fun showStatsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.imageloader_dialog_stats, null)
        val stats = ImageLoaderLogger.getLogStats()
        
        dialogView.findViewById<TextView>(R.id.txtDialogSummary).text = buildString {
            append("Total: ${stats.totalLogs} logs | Images: ${stats.totalImageRequests}")
            append(" | Errors: ${stats.imageErrors + stats.messageErrors}")
            append(" | JSON: ${stats.jsonPhotoCount} photos")
        }
        
        val totalMemory = stats.activeCacheCount + stats.memoryCacheCount
        val avgMemoryTime = if (totalMemory > 0) {
            ((stats.activeCacheAvgTime * stats.activeCacheCount + stats.memoryCacheAvgTime * stats.memoryCacheCount) / totalMemory)
        } else 0.0
        
        dialogView.findViewById<TextView>(R.id.txtDialogMemory).text = buildString {
            append("Count: $totalMemory images\n")
            append("Avg Time: ${"%.1f".format(avgMemoryTime)}ms (instant)")
        }
        
        dialogView.findViewById<TextView>(R.id.txtDialogDisk).text = buildString {
            append("Count: ${stats.diskCacheCount} images\n")
            append("Avg Time: ${"%.1f".format(stats.diskCacheAvgTime)}ms")
            append(" | Decode: ${"%.1f".format(stats.diskCacheAvgDecode)}ms")
            append(" | Transform: ${"%.1f".format(stats.diskCacheAvgTransform)}ms")
        }
        
        dialogView.findViewById<TextView>(R.id.txtDialogNetwork).text = buildString {
            append("Count: ${stats.networkCount} images\n")
            append("Avg Time: ${"%.1f".format(stats.networkAvgTime)}ms")
            append(" | Fetch: ${"%.1f".format(stats.networkAvgFetch)}ms")
            append(" | Decode: ${"%.1f".format(stats.networkAvgDecode)}ms")
            append(" | Transform: ${"%.1f".format(stats.networkAvgTransform)}ms")
        }
        
        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }
    
    private fun showFilterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.imageloader_dialog_filter, null)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroupFilter)
        val tempSelected = selectedCategories.toMutableSet()
        
        LogCategory.values().forEach { category ->
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
        val totalMemory = stats.activeCacheCount + stats.memoryCacheCount
        
        txtQuickStats.text = buildString {
            append("${stats.totalLogs} logs")
            append(" | Mem: $totalMemory")
            append(" | Disk: ${stats.diskCacheCount}")
            append(" | Net: ${stats.networkCount}")
            append(" | Err: ${stats.imageErrors + stats.messageErrors}")
        }
    }
}

internal class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {
    private val allLogs = mutableListOf<LogEntry>()
    private val filteredLogs = mutableListOf<LogEntry>()

    fun submitLogs(newLogs: List<LogEntry>, selectedCategories: Set<LogCategory>) {
        allLogs.clear()
        allLogs.addAll(newLogs)
        filterByCategories(selectedCategories)
    }

    fun addLog(log: LogEntry, selectedCategories: Set<LogCategory>) {
        allLogs.add(0, log)
        if (selectedCategories.isEmpty() || log.category in selectedCategories) {
            filteredLogs.add(0, log)
            notifyItemInserted(0)
        }
    }

    fun clearLogs() {
        allLogs.clear()
        filteredLogs.clear()
        notifyDataSetChanged()
    }
    
    fun filterByCategories(selectedCategories: Set<LogCategory>) {
        filteredLogs.clear()
        if (selectedCategories.isEmpty()) {
            filteredLogs.addAll(allLogs)
        } else {
            filteredLogs.addAll(allLogs.filter { it.category in selectedCategories })
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.imageloader_item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(filteredLogs[position])
    }

    override fun getItemCount() = filteredLogs.size

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtLogTime: TextView = view.findViewById(R.id.txtLogTime)
        private val txtLogIcon: TextView = view.findViewById(R.id.txtLogIcon)
        private val txtLogSource: TextView = view.findViewById(R.id.txtLogSource)
        private val txtLogTotal: TextView = view.findViewById(R.id.txtLogTotal)
        private val layoutTimings: View = view.findViewById(R.id.layoutTimings)
        private val txtTimings: TextView = view.findViewById(R.id.txtTimings)
        private val txtFastScroll: TextView = view.findViewById(R.id.txtFastScroll)
        private val txtLogUrl: TextView = view.findViewById(R.id.txtLogUrl)
        private val txtLogError: TextView = view.findViewById(R.id.txtLogError)
        private val btnOpenUrl: MaterialButton = view.findViewById(R.id.btnOpenUrl)

        fun bind(log: LogEntry) {
            when (log) {
                is com.example.imageloader.logger.ImageLoadLog -> bindImageLog(log)
                is com.example.imageloader.logger.MessageLog -> bindMessageLog(log)
            }
        }
        
        private fun bindImageLog(log: com.example.imageloader.logger.ImageLoadLog) {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            txtLogTime.text = sdf.format(java.util.Date(log.timestamp))
            txtLogIcon.text = if (log.error != null) "❌" else "✅"
            txtLogSource.text = log.source.name.replace("_", " ")
            txtLogTotal.text = "${log.totalTimeMs}ms"
            
            val timingParts = mutableListOf<String>()
            log.fetchTimeMs?.let { timingParts.add("Fetch: ${it}ms") }
            log.decodeTimeMs?.let { timingParts.add("Decode: ${it}ms") }
            log.transformTimeMs?.let { timingParts.add("Transform: ${it}ms") }
            
            if (timingParts.isNotEmpty()) {
                layoutTimings.visibility = View.VISIBLE
                txtTimings.text = timingParts.joinToString(" | ")
            } else {
                layoutTimings.visibility = View.GONE
            }
            
            txtFastScroll.visibility = if (log.isFastScrolling) View.VISIBLE else View.GONE
            
            txtLogUrl.text = log.url
            
            if (log.error != null) {
                txtLogError.visibility = View.VISIBLE
                txtLogError.text = "Error: ${log.error}"
                btnOpenUrl.visibility = View.GONE
            } else {
                txtLogError.visibility = View.GONE
                btnOpenUrl.visibility = View.VISIBLE
                btnOpenUrl.setOnClickListener {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(log.url))
                    itemView.context.startActivity(intent)
                }
            }
        }
        
        private fun bindMessageLog(log: com.example.imageloader.logger.MessageLog) {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            txtLogTime.text = sdf.format(java.util.Date(log.timestamp))
            
            txtLogIcon.text = when (log.level) {
                com.example.imageloader.logger.LogLevel.VERBOSE -> "💬"
                com.example.imageloader.logger.LogLevel.DEBUG -> "🐛"
                com.example.imageloader.logger.LogLevel.INFO -> "ℹ️"
                com.example.imageloader.logger.LogLevel.WARNING -> "⚠️"
                com.example.imageloader.logger.LogLevel.ERROR -> "❌"
            }
            
            txtLogSource.text = "${log.category.displayName} | ${log.tag}"
            txtLogTotal.text = log.level.name
            
            layoutTimings.visibility = View.GONE
            txtLogUrl.text = log.message
            btnOpenUrl.visibility = View.GONE
            
            if (log.throwable != null) {
                txtLogError.visibility = View.VISIBLE
                txtLogError.text = "${log.throwable.javaClass.simpleName}: ${log.throwable.message}"
            } else {
                txtLogError.visibility = View.GONE
            }
        }
    }
}
