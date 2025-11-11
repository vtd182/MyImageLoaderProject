package com.example.myimageloaderproject.modules.home.data.source.local

import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto

class PhotoLocalDataSource(
    private val diskCache: PhotoDiskCache,
    private val memoryCache: PhotoMemoryCache
) {
    
    /**
     * Load tất cả cached photos từ disk.
     * Returns flattened sorted list.
     */
    suspend fun getCachedPhotos(): CachedPhotoData? {
        return diskCache.loadBackup()
    }
    
    /**
     * Save một page vào disk cache (incremental).
     */
    suspend fun savePage(page: Int, photos: List<UnsplashPhoto>) {
        diskCache.savePage(page, photos)
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

/**
 * CachedPhotoData - Structure lưu trữ pages trong disk cache.
 * 
 * @param pages Map từ page number -> list photos
 * @param timestamp Thời điểm cache được tạo (để check expiry)
 */
data class CachedPhotoData(
    val pages: Map<Int, List<UnsplashPhoto>>,
    val timestamp: Long
) {
    /**
     * Flatten tất cả pages thành single sorted list.
     */
    fun getAllPhotos(): List<UnsplashPhoto> {
        return pages.toSortedMap().values.flatten()
    }
}
