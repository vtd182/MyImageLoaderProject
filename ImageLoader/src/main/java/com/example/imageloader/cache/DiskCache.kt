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
     */
    @Synchronized
    fun get(key: String): ByteArray? {
        val file = File(cacheDir, key)
        return try {
            if (!file.exists()) return null
            file.readBytes()
        } catch (e: Exception) {
            logger.wtf("DiskCache", "get() failed: ${e.message}")
            null
        }
    }

    /**
     * Lưu raw bytes xuống cache.
     */
    @Synchronized
    fun put(key: String, data: ByteArray): Boolean {
        val file = File(cacheDir, key)
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

    interface Logger {
        fun wtf(tag: String, msg: String)
    }

    object AndroidLogger : Logger {
        override fun wtf(tag: String, msg: String) {
            Log.wtf(tag, msg)
        }
    }
}
