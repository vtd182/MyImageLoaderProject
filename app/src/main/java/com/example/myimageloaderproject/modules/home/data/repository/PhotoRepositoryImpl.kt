package com.example.myimageloaderproject.modules.home.data.repository

import com.example.myimageloaderproject.modules.home.data.remote.UnsplashApi
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import com.example.myimageloaderproject.modules.home.domain.repository.PhotoRepository

class PhotoRepositoryImpl (
    private val api: UnsplashApi
) : PhotoRepository {
    override suspend fun getRandomPhotos(count: Int, page: Int): List<UnsplashPhoto> {
        return api.getRandomPhotos(
            page = page,
            perPage = count
        )
    }
}

