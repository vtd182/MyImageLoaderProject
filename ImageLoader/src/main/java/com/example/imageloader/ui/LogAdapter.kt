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

/**
 * LogAdapter - RecyclerView adapter cho log entries display.
 *
 * ## Responsibilities:
 * - Quản lý list of logs (all + filtered)
 * - Filter logs theo categories
 * - Real-time insert logs mới at position 0
 * - Bind LogEntry data vào ViewHolder
 * - Handle "Open URL" action
 *
 * ## Architecture:
 * ```
 * LogAdapter
 *     ↓
 * ├─ allLogs: MutableList<LogEntry>      (full dataset)
 * ├─ filteredLogs: MutableList<LogEntry> (displayed in UI)
 * ├─ LogEntryFilter                       (filtering logic)
 * ├─ LogEntryUiModelMapper               (LogEntry → UI model)
 * └─ LogViewHolder                        (bind UI)
 * ```
 *
 * ## Data Flow:
 * ```
 * ImageLoaderLogger.log()
 *         ↓
 * Activity listener callback
 *         ↓
 * adapter.addLog(log, selectedCategories)
 *         ↓
 * 1. Add to allLogs at position 0
 * 2. Check if log passes filter
 * 3. If yes: Add to filteredLogs + notifyItemInserted(0)
 *         ↓
 * onBindViewHolder()
 *         ↓
 * 1. Get log from filteredLogs[position]
 * 2. Map to UI model
 * 3. Bind to ViewHolder
 * ```
 *
 * ## Filtering:
 * - User selects categories in Filter Dialog
 * - Activity calls `filterByCategories(selectedCategories)`
 * - Adapter rebuilds filteredLogs + notifyDataSetChanged()
 *
 * ## Performance:
 * - **Insert at 0**: O(1) for ArrayList prepend
 * - **Filtering**: O(n) where n = allLogs.size (max 500)
 * - **ViewHolder pattern**: Efficient view recycling
 * - **No DiffUtil**: Simple insert, không cần sophisticated diff
 *
 * ## Testing:
 * Adapter is `internal` và testable:
 * - Constructor injection (filter, mapper, callback)
 * - Snapshot methods: snapshotFilteredLogs(), snapshotAllLogs()
 *
 * @param logEntryFilter Filter logic (injectable for testing)
 * @param uiModelMapper Mapper logic (injectable for testing)
 * @param onOpenUrl Callback khi user tap "Open URL"
 *
 * @see LogViewerActivity
 * @see LogEntryFilter
 * @see LogEntryUiModelMapper
 */
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

    /**
     * LogViewHolder - ViewHolder cho log entry item.
     *
     * ## UI Components:
     * - txtLogTime: Timestamp (HH:mm:ss.SSS)
     * - txtLogSource: Source label với icon
     * - txtLogTotal: Total time hoặc log level
     * - layoutTimings: Container cho timing breakdown
     * - txtTimings: Fetch/Decode/Transform times
     * - txtLogUrl: URL hoặc message
     * - txtLogError: Error text (nếu có)
     * - btnOpenUrl: Button để open URL trong browser
     */
    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtLogTime: TextView = view.findViewById(R.id.txtLogTime)
        private val txtLogSource: TextView = view.findViewById(R.id.txtLogSource)
        private val txtLogTotal: TextView = view.findViewById(R.id.txtLogTotal)
        private val layoutTimings: View = view.findViewById(R.id.layoutTimings)
        private val txtTimings: TextView = view.findViewById(R.id.txtTimings)
        private val txtLogUrl: TextView = view.findViewById(R.id.txtLogUrl)
        private val txtLogError: TextView = view.findViewById(R.id.txtLogError)
        private val btnOpenUrl: MaterialButton = view.findViewById(R.id.btnOpenUrl)

        /**
         * Bind UI model vào views.
         *
         * @param model UI model từ LogEntryUiModelMapper
         * @param onOpenUrl Callback khi tap button
         */
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
