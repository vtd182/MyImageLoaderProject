package com.example.myimageloaderproject.modules.home.data.repository

import UnsplashPhoto
import com.example.myimageloaderproject.modules.home.data.remote.UnsplashApi
import com.example.myimageloaderproject.modules.home.domain.repository.PhotoRepository
import javax.inject.Inject

class PhotoRepositoryImpl @Inject constructor(
    private val api: UnsplashApi
) : PhotoRepository {
    override suspend fun getRandomPhotos(count: Int): List<UnsplashPhoto> {
        return api.getRandomPhotos(
            page = 1,
            perPage = count
        )
    }
}

