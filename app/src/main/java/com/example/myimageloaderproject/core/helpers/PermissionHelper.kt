package com.example.myimageloaderproject.core.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * PermissionHelper - Utility class để check và request permissions.
 *
 * ## Storage Permission Rules:
 * - **API 29+ (Android 10+)**: MediaStore API không cần quyền cho app-scoped storage
 * - **API 23-28 (Android 6-9)**: Cần WRITE_EXTERNAL_STORAGE để write vào shared storage
 * - **API < 23 (Android 5-)**: Permission declared trong manifest là đủ (auto-granted)
 *
 * ## Usage:
 * ```kotlin
 * // Check permission
 * if (PermissionHelper.hasStoragePermission(context)) {
 *     // Download
 * } else {
 *     // Request permission
 * }
 * ```
 */
object PermissionHelper {

    /**
     * Check xem có cần xin storage permission hay không.
     *
     * @return true nếu CẦN xin quyền (API 23-28 và chưa có quyền)
     */
    fun needsStoragePermission(): Boolean {
        // API 29+ không cần quyền với MediaStore API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return false
        }

        // API < 23 auto-granted
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }

        // API 23-28 cần check runtime permission
        return true
    }

    /**
     * Check xem app có storage permission hay chưa.
     *
     * @param context Context
     * @return true nếu có quyền hoặc không cần quyền, false nếu cần quyền nhưng chưa có
     */
    fun hasStoragePermission(context: Context): Boolean {
        // Không cần quyền
        if (!needsStoragePermission()) {
            return true
        }

        // Check runtime permission (API 23-28)
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get storage permission string.
     *
     * @return Permission string hoặc null nếu không cần
     */
    fun getStoragePermission(): String? {
        return if (needsStoragePermission()) {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        } else {
            null
        }
    }

    /**
     * Check xem user đã chọn "Don't ask again" hay chưa.
     *
     * @param activity Activity
     * @return true nếu user đã deny và chọn "Don't ask again"
     */
    fun shouldShowRationale(activity: android.app.Activity): Boolean {
        if (!needsStoragePermission()) {
            return false
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.shouldShowRequestPermissionRationale(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            false
        }
    }

    /**
     * Get user-friendly message cho permission rationale.
     *
     * @param isDenied true nếu permission đã bị deny
     * @return Message string
     */
    fun getPermissionMessage(isDenied: Boolean): String {
        return if (isDenied) {
            "Cần quyền truy cập bộ nhớ để tải ảnh. Vui lòng cấp quyền trong Cài đặt."
        } else {
            "Ứng dụng cần quyền truy cập bộ nhớ để lưu ảnh vào thư viện của bạn."
        }
    }
}
