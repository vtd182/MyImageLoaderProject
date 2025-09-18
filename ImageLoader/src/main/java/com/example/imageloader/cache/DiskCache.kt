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

    fun get(url: String): Bitmap? {
        val file = File(cacheDir, hashKey(url))
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    fun put(url: String, bitmap: Bitmap) {
        val file = File(cacheDir, hashKey(url))
        if (file.exists()) return
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        trimCache()
    }

    private fun trimCache() {
        var total = cacheDir.listFiles()?.sumOf { it.length() } ?: 0
        if (total <= maxSizeBytes) return

        val sorted = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        for (f in sorted) {
            if (total <= maxSizeBytes) break
            total -= f.length()
            f.delete()
        }
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
