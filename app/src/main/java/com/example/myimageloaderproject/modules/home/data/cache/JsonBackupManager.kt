package com.example.myimageloaderproject.modules.home.data.cache

import android.content.Context
import android.os.Environment
import com.example.imageloader.logger.ImageLoaderLogger
import com.example.imageloader.logger.LogCategory
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PhotoBackup(
    val photos: List<UnsplashPhoto>,
    val currentPage: Int,
    val timestamp: Long
)

class JsonBackupManager(private val context: Context) {
    private val gson = Gson()
    
    companion object {
        private const val TAG = "JsonBackupManager"
    }
    
    private val backupFile: File
        get() {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            return File(cacheDir, "photo_backup.json")
        }

    suspend fun saveBackup(photos: List<UnsplashPhoto>, currentPage: Int) {
        withContext(Dispatchers.IO) {
            try {
                val backup = PhotoBackup(
                    photos = photos,
                    currentPage = currentPage,
                    timestamp = System.currentTimeMillis()
                )
                val json = gson.toJson(backup)
                backupFile.writeText(json)
                ImageLoaderLogger.jsonPhotoCount = photos.size
                ImageLoaderLogger.i(TAG, "JSON backup saved: ${photos.size} photos, page $currentPage", LogCategory.CACHE)
            } catch (e: Exception) {
                ImageLoaderLogger.e(TAG, "Failed to save JSON backup", e, LogCategory.CACHE)
                e.printStackTrace()
            }
        }
    }

    suspend fun loadBackup(): PhotoBackup? {
        return withContext(Dispatchers.IO) {
            try {
                if (!backupFile.exists()) {
                    return@withContext null
                }
                
                val json = backupFile.readText()
                val type = object : TypeToken<PhotoBackup>() {}.type
                val backup = gson.fromJson<PhotoBackup>(json, type)
                
                val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                if (backup.timestamp < oneDayAgo) {
                    ImageLoaderLogger.i(TAG, "JSON backup expired, deleting", LogCategory.CACHE)
                    backupFile.delete()
                    return@withContext null
                }
                
                ImageLoaderLogger.jsonPhotoCount = backup.photos.size
                ImageLoaderLogger.i(TAG, "JSON backup loaded: ${backup.photos.size} photos, page ${backup.currentPage}", LogCategory.CACHE)
                backup
            } catch (e: Exception) {
                ImageLoaderLogger.e(TAG, "Failed to load JSON backup", e, LogCategory.CACHE)
                e.printStackTrace()
                backupFile.delete()
                null
            }
        }
    }

    suspend fun clearBackup() {
        withContext(Dispatchers.IO) {
            try {
                if (backupFile.exists()) {
                    backupFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
