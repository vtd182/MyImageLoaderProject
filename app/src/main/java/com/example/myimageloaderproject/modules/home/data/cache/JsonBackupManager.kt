package com.example.myimageloaderproject.modules.home.data.cache

import android.content.Context
import android.os.Environment
import android.util.Log
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
                Log.d("JsonBackupManager", "Backup saved: ${photos.size} photos, page $currentPage, to: ${backupFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("JsonBackupManager", "Failed to save backup", e)
                e.printStackTrace()
            }
        }
    }

    suspend fun loadBackup(): PhotoBackup? {
        return withContext(Dispatchers.IO) {
            try {
                if (!backupFile.exists()) {
                    Log.d("JsonBackupManager", "No backup file found")
                    return@withContext null
                }
                
                val json = backupFile.readText()
                val type = object : TypeToken<PhotoBackup>() {}.type
                val backup = gson.fromJson<PhotoBackup>(json, type)
                
                val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                if (backup.timestamp < oneDayAgo) {
                    Log.d("JsonBackupManager", "Backup expired, deleting")
                    backupFile.delete()
                    return@withContext null
                }
                
                Log.d("JsonBackupManager", "Backup loaded: ${backup.photos.size} photos, page ${backup.currentPage}, from: ${backupFile.absolutePath}")
                backup
            } catch (e: Exception) {
                Log.e("JsonBackupManager", "Failed to load backup", e)
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
