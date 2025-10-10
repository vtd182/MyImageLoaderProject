package com.example.imageloader.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class DiskCache(
    context: Context,
    private val maxSizeBytes: Long = 150L * 1000 * 1000 // 150MB
) {
    private val cacheDir = File(context.externalCacheDir, "image_cache").apply { mkdirs() }

    @Synchronized
    fun get(key: String): Bitmap? {
        val file = File(cacheDir, key)
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    @Synchronized
    fun put(key: String, bitmap: Bitmap): Boolean {
        val file = File(cacheDir, key)
        if (file.exists()) return true

        val estimatedSize = bitmap.byteCount.toLong()
        val total = cacheDir.listFiles()?.sumOf { it.length() } ?: 0
        if (total + estimatedSize > maxSizeBytes) {
            Log.wtf(
                "DiskCache",
                "Trim with total: $total, estimatedSize: $estimatedSize, maxSizeBytes: $maxSizeBytes"
            )
            trimCache((total + estimatedSize) - maxSizeBytes)
        }

        val tempFile = File(cacheDir, "${file.name}.tmp")
        return try {
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.wtf("DiskCache", "total affter: $total ")
            tempFile.renameTo(file)
        } catch (e: Exception) {
            tempFile.delete()
            false
        }
    }

    @Synchronized
    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun trimCache(requiredFree: Long) {
        Log.wtf("DiskCache", "trimCache: $requiredFree")
        var freed = 0L
        cacheDir.listFiles()
            ?.sortedBy { it.lastModified() }
            ?.forEach {
                if (freed >= requiredFree) return
                val size = it.length()
                if (it.delete()) freed += size
            }
    }
}
