package com.example.myimageloaderproject.modules.home.data.source.local

import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import java.util.concurrent.ConcurrentHashMap

class PhotoMemoryCache {
    private val cache = ConcurrentHashMap<Int, List<UnsplashPhoto>>()
    
    fun savePage(page: Int, photos: List<UnsplashPhoto>) {
        cache[page] = photos
    }
    
    fun getPage(page: Int): List<UnsplashPhoto>? {
        return cache[page]
    }
    
    fun hasPage(page: Int): Boolean {
        return cache.containsKey(page)
    }
    
    fun clear() {
        cache.clear()
    }
    
    fun clearOldPages(currentPage: Int) {
        val pagesToRemove = cache.keys.filter { it < currentPage - 1 }
        pagesToRemove.forEach { cache.remove(it) }
    }
}
