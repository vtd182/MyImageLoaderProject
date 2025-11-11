package com.example.myimageloaderproject.modules.home.domain.usecase

import com.example.myimageloaderproject.core.error.AppError
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import com.example.myimageloaderproject.modules.home.domain.repository.PhotoRepository
import com.example.myimageloaderproject.shared.result.Result

class GetCachedPhotosUseCase(
    private val repository: PhotoRepository
) {
    suspend operator fun invoke(): Result<List<UnsplashPhoto>, AppError> {
        return repository.getCachedPhotos()
    }
}
