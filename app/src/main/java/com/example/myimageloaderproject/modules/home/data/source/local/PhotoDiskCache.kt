package com.example.myimageloaderproject.modules.home.data.source.local

import com.example.myimageloaderproject.core.config.AppConfig
import com.example.myimageloaderproject.core.platform.FileStorageProvider
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoDiskCache(
    private val fileStorageProvider: FileStorageProvider
) {
    private val gson = Gson()
    
    companion object {
        private const val BACKUP_FILE_NAME = "photo_backup.json"
    }
    
    suspend fun saveBackup(photos: List<UnsplashPhoto>, currentPage: Int) {
        withContext(Dispatchers.IO) {
            try {
                val backup = CachedPhotoData(
                    photos = photos,
                    currentPage = currentPage,
                    timestamp = System.currentTimeMillis()
                )
                val json = gson.toJson(backup)
                fileStorageProvider.writeTextFile(BACKUP_FILE_NAME, json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    suspend fun loadBackup(): CachedPhotoData? {
        return withContext(Dispatchers.IO) {
            try {
                if (!fileStorageProvider.fileExists(BACKUP_FILE_NAME)) {
                    return@withContext null
                }
                
                val json = fileStorageProvider.readTextFile(BACKUP_FILE_NAME) ?: return@withContext null
                val type = object : TypeToken<CachedPhotoData>() {}.type
                val backup = gson.fromJson<CachedPhotoData>(json, type)
                
                if (isCacheExpired(backup.timestamp)) {
                    fileStorageProvider.deleteFile(BACKUP_FILE_NAME)
                    return@withContext null
                }
                
                backup
            } catch (e: Exception) {
                e.printStackTrace()
                fileStorageProvider.deleteFile(BACKUP_FILE_NAME)
                null
            }
        }
    }
    
    suspend fun clearBackup() {
        withContext(Dispatchers.IO) {
            try {
                fileStorageProvider.deleteFile(BACKUP_FILE_NAME)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun isCacheExpired(timestamp: Long): Boolean {
        val expiryTime = AppConfig.CACHE_EXPIRY_HOURS * 60 * 60 * 1000
        return System.currentTimeMillis() - timestamp > expiryTime
    }
}
