package com.example.imageloader.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.imageloader.R
import com.example.imageloader.logger.LogCategory
import com.example.imageloader.logger.LogEntry
import com.google.android.material.button.MaterialButton

internal class LogAdapter(
    private val logEntryFilter: LogEntryFilter = LogEntryFilter(),
    private val uiModelMapper: LogEntryUiModelMapper = LogEntryUiModelMapper(),
    private val onOpenUrl: (String) -> Unit = {}
) : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val allLogs = mutableListOf<LogEntry>()
    private val filteredLogs = mutableListOf<LogEntry>()

    fun submitLogs(newLogs: List<LogEntry>, selectedCategories: Set<LogCategory>) {
        allLogs.clear()
        allLogs.addAll(newLogs)
        updateFilteredLogs(selectedCategories)
    }

    fun addLog(log: LogEntry, selectedCategories: Set<LogCategory>) {
        allLogs.add(0, log)
        if (logEntryFilter.shouldInclude(log, selectedCategories)) {
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
        updateFilteredLogs(selectedCategories)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.imageloader_item_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = filteredLogs[position]
        holder.bind(uiModelMapper.map(log), onOpenUrl)
    }

    override fun getItemCount(): Int = filteredLogs.size

    private fun updateFilteredLogs(selectedCategories: Set<LogCategory>) {
        filteredLogs.clear()
        filteredLogs.addAll(logEntryFilter.filter(allLogs, selectedCategories))
        notifyDataSetChanged()
    }

    internal fun snapshotFilteredLogs(): List<LogEntry> = filteredLogs.toList()

    internal fun snapshotAllLogs(): List<LogEntry> = allLogs.toList()

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtLogTime: TextView = view.findViewById(R.id.txtLogTime)
        private val txtLogSource: TextView = view.findViewById(R.id.txtLogSource)
        private val txtLogTotal: TextView = view.findViewById(R.id.txtLogTotal)
        private val layoutTimings: View = view.findViewById(R.id.layoutTimings)
        private val txtTimings: TextView = view.findViewById(R.id.txtTimings)
        private val txtLogUrl: TextView = view.findViewById(R.id.txtLogUrl)
        private val txtLogError: TextView = view.findViewById(R.id.txtLogError)
        private val btnOpenUrl: MaterialButton = view.findViewById(R.id.btnOpenUrl)

        fun bind(model: LogEntryUiModel, onOpenUrl: (String) -> Unit) {
            txtLogTime.text = model.timeText
            txtLogSource.text = model.sourceText
            txtLogTotal.text = model.totalText

            layoutTimings.visibility =
                if (model.timingsContainerVisible) View.VISIBLE else View.GONE
            txtTimings.text = model.timingsText

            txtLogUrl.text = model.urlText

            val hasError = model.errorText != null
            txtLogError.visibility = if (hasError) View.VISIBLE else View.GONE
            txtLogError.text = model.errorText

            if (model.showOpenUrlButton && model.urlToOpen != null) {
                btnOpenUrl.visibility = View.VISIBLE
                btnOpenUrl.setOnClickListener { onOpenUrl(model.urlToOpen) }
            } else {
                btnOpenUrl.visibility = View.GONE
                btnOpenUrl.setOnClickListener(null)
            }
        }
    }
}
