package com.example.imageloader.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class DiskCache(
    context: Context,
    private val maxSizeBytes: Long = 250L * 1024 * 1024 // 250MB
) {
    private val cacheDir = File(context.cacheDir, "image_cache").apply { mkdirs() }

    private fun hashKey(url: String): String {
        val digest = MessageDigest.getInstance("MD5")
        digest.update(url.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    fun get(url: String): Bitmap? {
        val file = File(cacheDir, hashKey(url))
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    @Synchronized
    fun put(url: String, bitmap: Bitmap): Boolean {
        val file = File(cacheDir, hashKey(url))
        if (file.exists()) return true

        val estimatedSize = bitmap.byteCount.toLong()
        val total = cacheDir.listFiles()?.sumOf { it.length() } ?: 0
        if (total + estimatedSize > maxSizeBytes) {
            trimCache((total + estimatedSize) - maxSizeBytes)
        }

        val tempFile = File(cacheDir, "${file.name}.tmp")
        return try {
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
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
