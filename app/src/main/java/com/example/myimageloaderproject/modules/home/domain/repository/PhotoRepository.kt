package com.example.myimageloaderproject.modules.home.domain.repository

import UnsplashPhoto


interface PhotoRepository {
    suspend fun getRandomPhotos(count: Int): List<UnsplashPhoto>
}