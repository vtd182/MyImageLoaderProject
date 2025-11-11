package com.example.myimageloaderproject.modules.home.domain.usecase

import com.example.myimageloaderproject.core.config.AppConfig
import com.example.myimageloaderproject.core.error.AppError
import com.example.myimageloaderproject.modules.home.domain.model.LoadPhotoResult
import com.example.myimageloaderproject.modules.home.domain.repository.PhotoRepositoryV2
import com.example.myimageloaderproject.shared.result.Result

class LoadInitialPhotosUseCase(
    private val repository: PhotoRepositoryV2
) {
    suspend operator fun invoke(): Result<LoadPhotoResult, AppError> {
        val cachedResult = repository.getCachedPhotos()
        if (cachedResult is Result.Success && cachedResult.data.isNotEmpty()) {
            return Result.Success(
                LoadPhotoResult(
                    photos = cachedResult.data,
                    currentPage = 1,
                    isFromCache = true
                )
            )
        }
        
        return when (val result = repository.loadInitialPhotos(AppConfig.DEFAULT_PAGE_SIZE)) {
            is Result.Success -> Result.Success(
                LoadPhotoResult(
                    photos = result.data,
                    currentPage = 1,
                    isFromCache = false
                )
            )
            is Result.Error -> Result.Error(result.error)
        }
    }
}
