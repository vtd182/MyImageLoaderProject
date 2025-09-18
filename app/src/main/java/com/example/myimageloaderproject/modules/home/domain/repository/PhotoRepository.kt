package com.example.myimageloaderproject.modules.home.domain.repository

import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto


interface PhotoRepository {
    suspend fun getRandomPhotos(count: Int, page: Int): List<UnsplashPhoto>
}