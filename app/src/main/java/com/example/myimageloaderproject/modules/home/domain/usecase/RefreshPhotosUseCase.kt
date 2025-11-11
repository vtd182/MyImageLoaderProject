package com.example.myimageloaderproject.modules.home.domain.usecase

import com.example.myimageloaderproject.core.config.AppConfig
import com.example.myimageloaderproject.core.error.AppError
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import com.example.myimageloaderproject.modules.home.domain.repository.PhotoRepositoryV2
import com.example.myimageloaderproject.shared.result.Result

class RefreshPhotosUseCase(
    private val repository: PhotoRepositoryV2
) {
    suspend operator fun invoke(): Result<List<UnsplashPhoto>, AppError> {
        return repository.refreshPhotos(AppConfig.DEFAULT_PAGE_SIZE)
    }
}
