package com.example.myimageloaderproject.modules.home.data.source.local

import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto

class PhotoLocalDataSource(
    private val diskCache: PhotoDiskCache,
    private val memoryCache: PhotoMemoryCache
) {
    
    suspend fun getCachedPhotos(): CachedPhotoData? {
        return diskCache.loadBackup()
    }
    
    suspend fun savePhotos(photos: List<UnsplashPhoto>, currentPage: Int) {
        diskCache.saveBackup(photos, currentPage)
    }
    
    suspend fun clearDiskCache() {
        diskCache.clearBackup()
    }
    
    fun getPreloadedPage(page: Int): List<UnsplashPhoto>? {
        return memoryCache.getPage(page)
    }
    
    fun savePreloadedPage(page: Int, photos: List<UnsplashPhoto>) {
        memoryCache.savePage(page, photos)
    }
    
    fun hasPreloadedPage(page: Int): Boolean {
        return memoryCache.hasPage(page)
    }
    
    fun clearMemoryCache() {
        memoryCache.clear()
    }
    
    fun clearOldPreloadedPages(currentPage: Int) {
        memoryCache.clearOldPages(currentPage)
    }
}

data class CachedPhotoData(
    val photos: List<UnsplashPhoto>,
    val currentPage: Int,
    val timestamp: Long
)
