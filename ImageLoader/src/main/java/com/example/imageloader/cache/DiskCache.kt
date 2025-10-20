package com.example.imageloader.cache

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DiskCache(
    context: Context,
    private val maxSizeBytes: Long = 150L * 1000 * 1000, // 150MB
    private val logger: Logger = AndroidLogger
) {
    private val cacheDir = File(context.externalCacheDir, "image_cache").apply { mkdirs() }

    /**
     * Đọc file từ cache, trả về raw bytes nếu có.
     * Tự động thử cả các đuôi mở rộng phổ biến.
     */
    @Synchronized
    fun get(key: String): ByteArray? {
        // Tìm file có key tương ứng (có hoặc không đuôi)
        val file = findFile(key) ?: return null
        return try {
            file.readBytes()
        } catch (e: Exception) {
            logger.wtf("DiskCache", "get() failed: ${e.message}")
            null
        }
    }

    /**
     * Lưu raw bytes xuống cache, có thêm phần mở rộng dựa vào contentType.
     */
    @Synchronized
    fun put(key: String, data: ByteArray, contentType: String? = null): Boolean {
        val extension = when (contentType?.lowercase()) {
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/avif" -> ".avif"
            else -> ".dat"
        }

        val file = File(cacheDir, "$key$extension")
        if (file.exists()) return true

        val estimatedSize = data.size.toLong()
        val total = cacheDir.listFiles()?.sumOf { it.length() } ?: 0
        if (total + estimatedSize > maxSizeBytes) {
            logger.wtf(
                "DiskCache",
                "Trim cache: total=$total, estimated=$estimatedSize, max=$maxSizeBytes"
            )
            trimCache((total + estimatedSize) - maxSizeBytes)
        }

        val tempFile = File(cacheDir, "${file.name}.tmp")
        return try {
            FileOutputStream(tempFile).use { out ->
                out.write(data)
                out.flush()
            }
            tempFile.renameTo(file)
        } catch (e: IOException) {
            logger.wtf("DiskCache", "put() failed: ${e.message}")
            tempFile.delete()
            false
        }
    }

    @Synchronized
    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Xóa file cũ nhất cho đến khi giải phóng đủ requiredFree bytes.
     */
    private fun trimCache(requiredFree: Long) {
        logger.wtf("DiskCache", "trimCache: $requiredFree")
        var freed = 0L
        cacheDir.listFiles()
            ?.sortedBy { it.lastModified() }
            ?.forEach {
                if (freed >= requiredFree) return
                val size = it.length()
                if (it.delete()) freed += size
            }
    }

    /**
     * Tìm file cache theo key (có thể có .jpg/.png/.webp hoặc không).
     */
    private fun findFile(key: String): File? {
        val possible = listOf(".jpg", ".png", ".webp", ".avif", ".dat", "")
        return possible
            .map { File(cacheDir, "$key$it") }
            .firstOrNull { it.exists() }
    }

    interface Logger {
        fun wtf(tag: String, msg: String)
    }

    object AndroidLogger : Logger {
        override fun wtf(tag: String, msg: String) {
            Log.wtf(tag, msg)
        }
    }
}
