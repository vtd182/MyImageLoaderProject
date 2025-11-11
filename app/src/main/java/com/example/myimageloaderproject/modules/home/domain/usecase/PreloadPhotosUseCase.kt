package com.example.myimageloaderproject.modules.home.domain.usecase

import com.example.myimageloaderproject.core.config.AppConfig
import com.example.myimageloaderproject.modules.home.domain.repository.PhotoRepositoryV2
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class PreloadPhotosUseCase(
    private val repository: PhotoRepositoryV2
) {
    suspend operator fun invoke(currentPage: Int) {
        val pagesToPreload = (currentPage + 1)..(currentPage + AppConfig.PRELOAD_PAGE_COUNT)
        
        coroutineScope {
            pagesToPreload.forEach { page ->
                launch {
                    repository.preloadPage(page, AppConfig.DEFAULT_PAGE_SIZE)
                }
            }
        }
    }
}
