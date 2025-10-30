package com.example.imageloader.cache

import android.content.Context
import com.example.imageloader.logger.ImageLoaderLogger
import com.example.imageloader.logger.LogCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DiskCache(
    context: Context,
    private val maxSizeBytes: Long = 150L * 1000 * 1000, // 150MB
) {
    companion object {
        private const val TAG = "DiskCache"
    }

    private val cacheDir = File(context.externalCacheDir, "image_cache").apply { mkdirs() }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var currentSize: Long = 0L
    private var sizeInitialized = false

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
            ImageLoaderLogger.e("DiskCache", "get() failed: ${e.message}")
            null
        }
    }

    /**
     * Lưu raw bytes xuống cache, có thêm phần mở rộng dựa vào contentType.
     */
    @Synchronized
    fun put(key: String, data: ByteArray, contentType: String? = null): Boolean {
        ensureSizeInitialized()

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

        // Use cached size instead of scanning all files
        if (currentSize + estimatedSize > maxSizeBytes) {
            val requiredFree = (currentSize + estimatedSize) - maxSizeBytes
            ImageLoaderLogger.w(
                TAG,
                "Cache full, trimming ${requiredFree / 1000}KB",
                category = LogCategory.CACHE
            )
            // Trim asynchronously to avoid blocking
            trimCacheAsync(requiredFree)
        }

        val tempFile = File(cacheDir, "${file.name}.tmp")
        return try {
            FileOutputStream(tempFile).use { out ->
                out.write(data)
                out.flush()
            }
            val success = tempFile.renameTo(file)
            if (success) {
                currentSize += estimatedSize
            }
            success
        } catch (e: IOException) {
            ImageLoaderLogger.e("DiskCache", "put() failed: ${e.message}")
            tempFile.delete()
            false
        }
    }

    private fun ensureSizeInitialized() {
        if (sizeInitialized) return
        sizeInitialized = true
        // Calculate initial size in background
        ioScope.launch {
            val total = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
            currentSize = total
        }
    }

    @Synchronized
    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Xóa file cũ nhất cho đến khi giải phóng đủ requiredFree bytes (async).
     */
    private fun trimCacheAsync(requiredFree: Long) {
        ioScope.launch {
            synchronized(this@DiskCache) {
                var freed = 0L
                var filesDeleted = 0
                cacheDir.listFiles()
                    ?.sortedBy { it.lastModified() }
                    ?.forEach {
                        if (freed >= requiredFree) return@synchronized
                        val size = it.length()
                        if (it.delete()) {
                            freed += size
                            currentSize -= size
                            filesDeleted++
                        }
                    }
                if (filesDeleted > 0) {
                    ImageLoaderLogger.i(
                        TAG,
                        "Trimmed cache: deleted $filesDeleted files, freed ${freed / 1000}KB",
                        LogCategory.CACHE
                    )
                }
            }
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
}
