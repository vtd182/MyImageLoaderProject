package com.example.myimageloaderproject.modules.home.data.repository

import com.example.myimageloaderproject.core.error.AppError
import com.example.myimageloaderproject.modules.home.data.mapper.PhotoMapper
import com.example.myimageloaderproject.modules.home.data.source.local.PhotoLocalDataSource
import com.example.myimageloaderproject.modules.home.data.source.remote.UnsplashRemoteDataSource
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import com.example.myimageloaderproject.modules.home.domain.repository.PhotoRepositoryV2
import com.example.myimageloaderproject.shared.error.ErrorMapper
import com.example.myimageloaderproject.shared.result.Result

class PhotoRepositoryV2Impl(
    private val remoteDataSource: UnsplashRemoteDataSource,
    private val localDataSource: PhotoLocalDataSource,
    private val photoMapper: PhotoMapper,
    private val errorMapper: ErrorMapper
) : PhotoRepositoryV2 {
    
    override suspend fun loadInitialPhotos(pageSize: Int): Result<List<UnsplashPhoto>, AppError> {
        return try {
            val cachedData = localDataSource.getCachedPhotos()
            if (cachedData != null && cachedData.photos.isNotEmpty()) {
                return Result.Success(cachedData.photos)
            }
            
            val dtos = remoteDataSource.getPhotos(page = 1, perPage = pageSize)
            val photos = photoMapper.toDomainList(dtos)
            
            localDataSource.savePhotos(photos, currentPage = 1)
            
            Result.Success(photos)
        } catch (e: Exception) {
            Result.Error(errorMapper.mapError(e))
        }
    }
    
    override suspend fun loadMorePhotos(page: Int, pageSize: Int): Result<List<UnsplashPhoto>, AppError> {
        return try {
            val cachedPage = localDataSource.getPreloadedPage(page)
            if (cachedPage != null) {
                return Result.Success(cachedPage)
            }
            
            val dtos = remoteDataSource.getPhotos(page = page, perPage = pageSize)
            val photos = photoMapper.toDomainList(dtos)
            
            Result.Success(photos)
        } catch (e: Exception) {
            Result.Error(errorMapper.mapError(e))
        }
    }
    
    override suspend fun refreshPhotos(pageSize: Int): Result<List<UnsplashPhoto>, AppError> {
        return try {
            localDataSource.clearMemoryCache()
            
            val dtos = remoteDataSource.getPhotos(page = 1, perPage = pageSize)
            val photos = photoMapper.toDomainList(dtos)
            
            localDataSource.savePhotos(photos, currentPage = 1)
            
            Result.Success(photos)
        } catch (e: Exception) {
            Result.Error(errorMapper.mapError(e))
        }
    }
    
    override suspend fun getCachedPhotos(): Result<List<UnsplashPhoto>, AppError> {
        return try {
            val cachedData = localDataSource.getCachedPhotos()
            if (cachedData != null && cachedData.photos.isNotEmpty()) {
                Result.Success(cachedData.photos)
            } else {
                Result.Error(AppError.UnknownError("No cached data available"))
            }
        } catch (e: Exception) {
            Result.Error(errorMapper.mapError(e))
        }
    }
    
    override suspend fun preloadPage(page: Int, pageSize: Int): Result<Unit, AppError> {
        return try {
            if (localDataSource.hasPreloadedPage(page)) {
                return Result.Success(Unit)
            }
            
            val dtos = remoteDataSource.getPhotos(page = page, perPage = pageSize)
            val photos = photoMapper.toDomainList(dtos)
            
            localDataSource.savePreloadedPage(page, photos)
            localDataSource.clearOldPreloadedPages(page)
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(errorMapper.mapError(e))
        }
    }
    
    override suspend fun clearCache() {
        localDataSource.clearDiskCache()
        localDataSource.clearMemoryCache()
    }
}
