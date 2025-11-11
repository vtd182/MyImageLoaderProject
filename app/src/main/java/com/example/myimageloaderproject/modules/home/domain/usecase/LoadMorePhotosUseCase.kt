package com.example.myimageloaderproject.modules.home.domain.usecase

import com.example.myimageloaderproject.core.config.AppConfig
import com.example.myimageloaderproject.core.error.AppError
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import com.example.myimageloaderproject.modules.home.domain.repository.PhotoRepository
import com.example.myimageloaderproject.shared.result.Result

class LoadMorePhotosUseCase(
    private val repository: PhotoRepository
) {
    suspend operator fun invoke(page: Int): Result<List<UnsplashPhoto>, AppError> {
        val result = repository.loadMorePhotos(page, AppConfig.DEFAULT_PAGE_SIZE)
        
        return when (result) {
            is Result.Success -> {
                val photos = if (result.data.size > AppConfig.OVERLAP_ITEMS) {
                    result.data.drop(AppConfig.OVERLAP_ITEMS)
                } else {
                    emptyList()
                }
                Result.Success(photos)
            }
            is Result.Error -> result
        }
    }
}
