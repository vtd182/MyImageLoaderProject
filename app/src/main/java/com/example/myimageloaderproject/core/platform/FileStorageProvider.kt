package com.example.myimageloaderproject.core.platform

import android.content.Context
import java.io.File

interface FileStorageProvider {
    fun getCacheDir(): File
    fun readTextFile(fileName: String): String?
    fun writeTextFile(fileName: String, content: String): Boolean
    fun deleteFile(fileName: String): Boolean
    fun fileExists(fileName: String): Boolean
    fun getFileLastModified(fileName: String): Long
}

class AndroidFileStorageProvider(
    private val context: Context
) : FileStorageProvider {
    
    override fun getCacheDir(): File {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }
    
    override fun readTextFile(fileName: String): String? {
        return try {
            val file = File(getCacheDir(), fileName)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    override fun writeTextFile(fileName: String, content: String): Boolean {
        return try {
            val file = File(getCacheDir(), fileName)
            file.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    override fun deleteFile(fileName: String): Boolean {
        return try {
            val file = File(getCacheDir(), fileName)
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    override fun fileExists(fileName: String): Boolean {
        return try {
            File(getCacheDir(), fileName).exists()
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getFileLastModified(fileName: String): Long {
        return try {
            File(getCacheDir(), fileName).lastModified()
        } catch (e: Exception) {
            0L
        }
    }
}
