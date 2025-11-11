package com.example.myimageloaderproject.modules.home.domain.repository

import com.example.myimageloaderproject.core.error.AppError
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import com.example.myimageloaderproject.shared.result.Result

interface PhotoRepository {
    suspend fun loadInitialPhotos(pageSize: Int): Result<List<UnsplashPhoto>, AppError>
    
    suspend fun loadMorePhotos(page: Int, pageSize: Int): Result<List<UnsplashPhoto>, AppError>
    
    suspend fun refreshPhotos(pageSize: Int): Result<List<UnsplashPhoto>, AppError>
    
    suspend fun getCachedPhotos(): Result<List<UnsplashPhoto>, AppError>
    
    suspend fun preloadPage(page: Int, pageSize: Int): Result<Unit, AppError>
    
    suspend fun clearCache()
}
