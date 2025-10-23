package com.example.myimageloaderproject.modules.home.data.cache

import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import com.example.myimageloaderproject.modules.home.domain.usecase.GetRandomPhotosUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PhotoPreloader(
    private val getRandomPhotosUseCase: GetRandomPhotosUseCase,
    private val scope: CoroutineScope
) {
    private val preloadedPages = mutableMapOf<Int, List<UnsplashPhoto>>()
    private val preloadJobs = mutableMapOf<Int, Job>()
    private val perPage = 25
    private val preloadAhead = 3

    fun preloadPages(currentPage: Int) {
        val targetPages = (currentPage + 1)..(currentPage + preloadAhead)
        
        targetPages.forEach { page ->
            if (!preloadedPages.containsKey(page) && !preloadJobs.containsKey(page)) {
                preloadJobs[page] = scope.launch(Dispatchers.IO) {
                    try {
                        val photos = getRandomPhotosUseCase(perPage, page)
                        preloadedPages[page] = photos
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        preloadJobs.remove(page)
                    }
                }
            }
        }

        cleanupOldPages(currentPage)
    }

    fun getPreloadedPage(page: Int): List<UnsplashPhoto>? {
        return preloadedPages[page]
    }

    fun hasPreloadedPage(page: Int): Boolean {
        return preloadedPages.containsKey(page)
    }

    private fun cleanupOldPages(currentPage: Int) {
        val pagesToRemove = preloadedPages.keys.filter { it < currentPage - 1 }
        pagesToRemove.forEach { page ->
            preloadedPages.remove(page)
            preloadJobs[page]?.cancel()
            preloadJobs.remove(page)
        }
    }

    fun clear() {
        preloadJobs.values.forEach { it.cancel() }
        preloadJobs.clear()
        preloadedPages.clear()
    }
}
