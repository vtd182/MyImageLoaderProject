package com.example.imageloader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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

class LogViewerActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var txtStatTotal: TextView
    private lateinit var txtStatImages: TextView
    private lateinit var txtStatCache: TextView
    private lateinit var txtStatNetwork: TextView
    private lateinit var txtStatErrors: TextView
    private lateinit var txtStatJson: TextView
    private lateinit var txtStatAvg: TextView
    private lateinit var txtStatDecode: TextView
    private lateinit var txtStatTransform: TextView
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
        setContentView(R.layout.imageloader_activity_log_viewer)

        recyclerView = findViewById(R.id.recyclerViewLogs)
        txtStatTotal = findViewById(R.id.txtStatTotal)
        txtStatImages = findViewById(R.id.txtStatImages)
        txtStatCache = findViewById(R.id.txtStatCache)
        txtStatNetwork = findViewById(R.id.txtStatNetwork)
        txtStatErrors = findViewById(R.id.txtStatErrors)
        txtStatJson = findViewById(R.id.txtStatJson)
        txtStatAvg = findViewById(R.id.txtStatAvg)
        txtStatDecode = findViewById(R.id.txtStatDecode)
        txtStatTransform = findViewById(R.id.txtStatTransform)
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
        txtStatTotal.text = stats.totalLogs.toString()
        txtStatImages.text = stats.totalImageRequests.toString()
        txtStatCache.text = stats.fromCache.toString()
        txtStatNetwork.text = stats.fromNetwork.toString()
        txtStatErrors.text = (stats.imageErrors + stats.messageErrors).toString()
        txtStatJson.text = stats.jsonPhotoCount.toString()
        txtStatAvg.text = "${"%.1f".format(stats.avgTotalTime)}ms"
        txtStatDecode.text = "${"%.1f".format(stats.avgDecodeTime)}ms"
        txtStatTransform.text = "${"%.1f".format(stats.avgTransformTime)}ms"
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
