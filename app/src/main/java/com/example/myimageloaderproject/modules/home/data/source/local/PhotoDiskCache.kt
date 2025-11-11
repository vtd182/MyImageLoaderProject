package com.example.myimageloaderproject.modules.home.data.source.local

import com.example.imageloader.logger.ImageLoaderLogger
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
        private const val TAG = "PhotoDiskCache"
    }
    
    /**
     * Save hoặc update một page vào disk cache.
     * Merge với data hiện có (nếu có).
     */
    suspend fun savePage(page: Int, photos: List<UnsplashPhoto>) {
        withContext(Dispatchers.IO) {
            try {
                val existing = loadBackupInternal()
                val pagesMap = existing?.pages?.toMutableMap() ?: mutableMapOf()
                
                pagesMap[page] = photos
                
                val backup = CachedPhotoData(
                    pages = pagesMap,
                    timestamp = System.currentTimeMillis()
                )
                
                val json = gson.toJson(backup)
                fileStorageProvider.writeTextFile(BACKUP_FILE_NAME, json)
                
                val totalPhotos = pagesMap.values.sumOf { it.size }
                ImageLoaderLogger.d(TAG, "Saved page $page (${photos.size} photos). Total cached: $totalPhotos photos across ${pagesMap.size} pages")
            } catch (e: Exception) {
                ImageLoaderLogger.e(TAG, "Failed to save page $page", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Load tất cả pages từ disk cache.
     * Returns sorted flattened list của tất cả photos.
     */
    suspend fun loadBackup(): CachedPhotoData? {
        return withContext(Dispatchers.IO) {
            try {
                val backup = loadBackupInternal() ?: return@withContext null
                
                if (isCacheExpired(backup.timestamp)) {
                    ImageLoaderLogger.d(TAG, "Cache expired, clearing")
                    fileStorageProvider.deleteFile(BACKUP_FILE_NAME)
                    return@withContext null
                }
                
                val totalPhotos = backup.pages.values.sumOf { it.size }
                val maxPage = backup.pages.keys.maxOrNull() ?: 0
                
                ImageLoaderLogger.jsonPhotoCount = totalPhotos
                ImageLoaderLogger.jsonCurrentPage = maxPage
                
                ImageLoaderLogger.i(TAG, "Loaded from disk: $totalPhotos photos across ${backup.pages.size} pages (max page: $maxPage)")
                
                backup
            } catch (e: Exception) {
                ImageLoaderLogger.e(TAG, "Failed to load backup", e)
                e.printStackTrace()
                fileStorageProvider.deleteFile(BACKUP_FILE_NAME)
                null
            }
        }
    }
    
    private fun loadBackupInternal(): CachedPhotoData? {
        try {
            if (!fileStorageProvider.fileExists(BACKUP_FILE_NAME)) {
                return null
            }
            
            val json = fileStorageProvider.readTextFile(BACKUP_FILE_NAME) ?: return null
            val type = object : TypeToken<CachedPhotoData>() {}.type
            return gson.fromJson<CachedPhotoData>(json, type)
        } catch (e: Exception) {
            return null
        }
    }
    
    suspend fun clearBackup() {
        withContext(Dispatchers.IO) {
            try {
                fileStorageProvider.deleteFile(BACKUP_FILE_NAME)
                ImageLoaderLogger.jsonPhotoCount = 0
                ImageLoaderLogger.jsonCurrentPage = 0
                ImageLoaderLogger.d(TAG, "Disk cache cleared")
            } catch (e: Exception) {
                ImageLoaderLogger.e(TAG, "Failed to clear backup", e)
                e.printStackTrace()
            }
        }
    }
    
    private fun isCacheExpired(timestamp: Long): Boolean {
        val expiryTime = AppConfig.CACHE_EXPIRY_HOURS * 60 * 60 * 1000
        return System.currentTimeMillis() - timestamp > expiryTime
    }
}
