package com.example.imageloader.logger

import android.content.Context
import android.content.Intent
import com.example.imageloader.ui.LogViewerActivity

/**
 * LogViewer - Utility object để mở LogViewerActivity cho debugging.
 *
 * ## Mục đích:
 * Cung cấp in-app log viewer UI để developers có thể:
 * - Xem real-time logs ngay trong app (không cần adb/logcat)
 * - Filter logs theo level và category
 * - Phân tích performance metrics và cache hit rates
 * - Debug image loading issues trực tiếp trên device
 *
 * ## LogViewerActivity Features:
 * - **Real-time updates**: Auto-scroll khi có log mới
 * - **Filtering**: Filter by LogLevel (ERROR, WARNING, INFO, etc.)
 * - **Statistics**: Cache hit rates, average load times
 * - **Search**: Tìm kiếm logs theo URL hoặc message
 * - **Export**: Share logs qua text/file
 * - **Color coding**: Visual distinction giữa log levels
 *
 * ## Use Cases:
 *
 * ### 1. Development & Debugging:
 * ```kotlin
 * // Thêm button trong debug drawer hoặc settings
 * debugButton.setOnClickListener {
 *     LogViewer.open(this)
 * }
 * ```
 *
 * ### 2. QA Testing:
 * ```kotlin
 * // Shake gesture để mở log viewer
 * class MyActivity : AppCompatActivity() {
 *     private val shakeDetector = ShakeDetector {
 *         if (BuildConfig.DEBUG) {
 *             LogViewer.open(this)
 *         }
 *     }
 * }
 * ```
 *
 * ### 3. Production Debug Menu:
 * ```kotlin
 * // Triple tap trên header để reveal debug menu
 * headerView.setOnClickListener(object : DoubleClickListener() {
 *     override fun onTripleClick() {
 *         if (isDeveloperMode()) {
 *             showDebugMenu()  // Contains LogViewer.open()
 *         }
 *     }
 * })
 * ```
 *
 * ### 4. Error Reporting:
 * ```kotlin
 * // User reports "images not loading"
 * // → Open LogViewer to see errors
 * supportMenu.addItem("View Image Logs") {
 *     LogViewer.open(this)
 *     Toast.makeText(this, "Check for RED error logs", Toast.LENGTH_LONG).show()
 * }
 * ```
 *
 * ## Integration với ImageLoaderLogger:
 * ```
 * ImageLoaderLogger → [Memory Buffer (500 logs)]
 *                              ↓
 *                      LogViewer.open()
 *                              ↓
 *                      LogViewerActivity
 *                              ↓
 *                      Display logs with:
 *                      - Timestamps
 *                      - Icons (✅❌⚠️)
 *                      - Color coding
 *                      - Performance stats
 * ```
 *
 * ## Performance:
 * - **Memory**: ~50KB cho 500 logs (text only)
 * - **UI**: RecyclerView với ViewHolder pattern (smooth scrolling)
 * - **Thread-safe**: Reads từ ConcurrentLinkedQueue
 *
 * ## Best Practices:
 *
 * ### ✅ DO:
 * - Dùng trong debug builds hoặc developer mode
 * - Provide easy access (shake, triple tap, debug menu)
 * - Check `BuildConfig.DEBUG` hoặc feature flag
 *
 * ### ❌ DON'T:
 * - Expose publicly trong production release
 * - Log sensitive data (passwords, tokens, PII)
 * - Open automatically (let user trigger it)
 *
 * ## Example UI Flow:
 * ```
 * User Action → LogViewer.open() → LogViewerActivity
 *                                         ↓
 *                                   Load logs from ImageLoaderLogger
 *                                         ↓
 *                                   Display in RecyclerView
 *                                         ↓
 *                                   Listen for new logs (real-time)
 *                                         ↓
 *                                   Update UI automatically
 * ```
 *
 * @see com.example.imageloader.logger.ImageLoaderLogger
 * @see com.example.imageloader.ui.LogViewerActivity
 */
object LogViewer {
    
    /**
     * Mở LogViewerActivity để hiển thị tất cả logs.
     *
     * ## Behavior:
     * - Start LogViewerActivity với standard Intent
     * - Activity load logs từ ImageLoaderLogger.getAllLogs()
     * - Register listener để nhận real-time updates
     * - Unregister listener khi Activity destroyed
     *
     * ## Requirements:
     * - Context phải là Activity hoặc có FLAG_ACTIVITY_NEW_TASK
     * - LogViewerActivity phải được declare trong AndroidManifest.xml
     *
     * ## Threading:
     * - Safe để call từ main thread
     * - Activity lifecycle được Android quản lý
     *
     * ## Example:
     * ```kotlin
     * // From Activity
     * findViewById<Button>(R.id.debugLogsBtn).setOnClickListener {
     *     LogViewer.open(this)
     * }
     *
     * // From Fragment
     * requireActivity().let { activity ->
     *     LogViewer.open(activity)
     * }
     *
     * // From Application Context (cần FLAG)
     * val intent = Intent(appContext, LogViewerActivity::class.java)
     * intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
     * appContext.startActivity(intent)
     * ```
     *
     * @param context Context để start activity (thường là Activity)
     * @throws android.util.AndroidRuntimeException nếu context không phải Activity và thiếu FLAG_ACTIVITY_NEW_TASK
     */
    fun open(context: Context) {
        val intent = Intent(context, LogViewerActivity::class.java)
        context.startActivity(intent)
    }
}
