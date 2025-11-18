package com.example.myimageloaderproject.core.helpers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.myimageloaderproject.R

/**
 * NotificationHelper - Utility class để quản lý notifications.
 *
 * ## Notification Permission Rules:
 * - **API 33+ (Android 13+)**: Cần POST_NOTIFICATIONS runtime permission
 * - **API < 33**: Không cần permission (enabled by default)
 *
 * ## Usage:
 * ```kotlin
 * NotificationHelper.showDownloadComplete(
 *     context,
 *     fileName = "photo.jpg",
 *     fileUri = Uri.parse("content://...")
 * )
 * ```
 */
object NotificationHelper {

    private const val CHANNEL_ID = "download_channel"
    private const val CHANNEL_NAME = "Tải xuống"
    private const val CHANNEL_DESCRIPTION = "Thông báo khi tải ảnh hoàn tất"
    private const val NOTIFICATION_ID_BASE = 1000

    /**
     * Check xem có cần xin notification permission hay không.
     *
     * @return true nếu CẦN xin quyền (API 33+ và chưa có quyền)
     */
    fun needsNotificationPermission(context: Context): Boolean {
        // Chỉ API 33+ mới cần permission
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }

        // Check runtime permission
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check xem có notification permission hay chưa.
     *
     * @param context Context
     * @return true nếu có quyền hoặc không cần quyền
     */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get notification permission string.
     *
     * @return Permission string hoặc null nếu không cần
     */
    fun getNotificationPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }
    }

    /**
     * Create notification channel (cần cho API 26+).
     * Phải gọi trước khi show notification.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Show notification khi download ảnh hoàn tất.
     *
     * @param context Context
     * @param fileName Tên file đã download
     * @param fileUri Uri của file (để mở khi click)
     */
    fun showDownloadComplete(
        context: Context,
        fileName: String,
        fileUri: Uri
    ) {
        // Check permission
        if (!hasNotificationPermission(context)) {
            // Không có permission → skip notification
            return
        }

        // Create channel nếu chưa có
        createNotificationChannel(context)

        // Intent để mở ảnh khi click notification
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "image/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Tải ảnh thành công")
            .setContentText(fileName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Tự động dismiss khi click
            .build()

        // Show notification
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            // Dùng timestamp làm ID để mỗi download có notification riêng
            val notificationId = NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 1000).toInt()
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Permission bị revoke trong lúc chạy → ignore
        }
    }

    /**
     * Show notification khi download fail.
     *
     * @param context Context
     * @param errorMessage Error message
     */
    fun showDownloadFailed(
        context: Context,
        errorMessage: String
    ) {
        if (!hasNotificationPermission(context)) {
            return
        }

        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Tải ảnh thất bại")
            .setContentText(errorMessage)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            val notificationId = NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 1000).toInt()
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Ignore
        }
    }

    /**
     * Get user-friendly message cho permission rationale.
     */
    fun getPermissionMessage(): String {
        return "Ứng dụng cần quyền thông báo để báo cho bạn khi tải ảnh hoàn tất."
    }
}
