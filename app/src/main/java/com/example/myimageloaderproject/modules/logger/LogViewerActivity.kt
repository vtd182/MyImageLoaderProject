package com.example.myimageloaderproject.modules.logger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.imageloader.logger.ImageLoaderLogger
import com.example.imageloader.logger.LogCategory
import com.example.imageloader.logger.LogEntry
import com.example.myimageloaderproject.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class LogViewerActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var statsText: TextView
    private lateinit var btnClear: Button
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var adapter: LogAdapter
    private val selectedCategories = mutableSetOf<LogCategory>()
    
    private val logListener: (LogEntry) -> Unit = { log ->
        runOnUiThread {
            adapter.addLog(log, selectedCategories)
            recyclerView.smoothScrollToPosition(0)
            updateStats()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        recyclerView = findViewById(R.id.recyclerViewLogs)
        statsText = findViewById(R.id.txtStats)
        btnClear = findViewById(R.id.btnClearLogs)
        chipGroupFilter = findViewById(R.id.chipGroupFilter)

        adapter = LogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupFilterChips()
        adapter.submitLogs(ImageLoaderLogger.getAllLogs().reversed(), selectedCategories)

        btnClear.setOnClickListener {
            ImageLoaderLogger.clear()
            adapter.clearLogs()
            updateStats()
        }

        ImageLoaderLogger.addListener(logListener)
        updateStats()
    }
    
    private fun setupFilterChips() {
        LogCategory.values().forEach { category ->
            val chip = Chip(this).apply {
                text = category.displayName
                isCheckable = true
                isChecked = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedCategories.add(category)
                    } else {
                        selectedCategories.remove(category)
                    }
                    adapter.filterByCategories(selectedCategories)
                }
            }
            selectedCategories.add(category)
            chipGroupFilter.addView(chip)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ImageLoaderLogger.removeListener(logListener)
    }

    private fun updateStats() {
        val stats = ImageLoaderLogger.getLogStats()
        statsText.text = buildString {
            append("Total Logs: ${stats.totalLogs}")
            append(" | Images: ${stats.totalImageRequests}")
            append(" | Cache: ${stats.fromCache}")
            append(" | Network: ${stats.fromNetwork}")
            append("\nErrors: ${stats.imageErrors + stats.messageErrors}")
            append(" | Avg Total: ${"%.1f".format(stats.avgTotalTime)}ms")
            append(" | Decode: ${"%.1f".format(stats.avgDecodeTime)}ms")
            append(" | Transform: ${"%.1f".format(stats.avgTransformTime)}ms")
        }
    }
}

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {
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
            .inflate(R.layout.item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(filteredLogs[position])
    }

    override fun getItemCount() = filteredLogs.size

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtLog: TextView = view.findViewById(R.id.txtLog)

        fun bind(log: LogEntry) {
            txtLog.text = log.toDisplayString()
        }
    }
}
