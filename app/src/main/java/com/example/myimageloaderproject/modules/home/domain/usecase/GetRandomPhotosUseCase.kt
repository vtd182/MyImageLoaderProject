package com.example.myimageloaderproject.modules.home.domain.usecase

import UnsplashPhoto
import com.example.myimageloaderproject.modules.home.domain.repository.PhotoRepository
import javax.inject.Inject

class GetRandomPhotosUseCase @Inject constructor(private val repository: PhotoRepository) {
    suspend operator fun invoke(count: Int): List<UnsplashPhoto> {
        return repository.getRandomPhotos(count)
    }
}