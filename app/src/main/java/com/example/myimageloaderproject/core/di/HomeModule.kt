package com.example.myimageloaderproject.core.di

import com.example.myimageloaderproject.core.platform.ConnectivityProvider
import com.example.myimageloaderproject.core.platform.FileStorageProvider
import com.example.myimageloaderproject.modules.home.data.mapper.PhotoMapper
import com.example.myimageloaderproject.modules.home.data.repository.PhotoRepositoryImpl
import com.example.myimageloaderproject.modules.home.data.source.local.PhotoDiskCache
import com.example.myimageloaderproject.modules.home.data.source.local.PhotoLocalDataSource
import com.example.myimageloaderproject.modules.home.data.source.local.PhotoMemoryCache
import com.example.myimageloaderproject.modules.home.data.source.remote.UnsplashRemoteDataSource
import com.example.myimageloaderproject.modules.home.domain.repository.PhotoRepository
import com.example.myimageloaderproject.modules.home.domain.usecase.GetCachedPhotosUseCase
import com.example.myimageloaderproject.modules.home.domain.usecase.LoadInitialPhotosUseCase
import com.example.myimageloaderproject.modules.home.domain.usecase.LoadMorePhotosUseCase
import com.example.myimageloaderproject.modules.home.domain.usecase.PreloadPhotosUseCase
import com.example.myimageloaderproject.modules.home.domain.usecase.RefreshPhotosUseCase
import com.example.myimageloaderproject.network.HttpClient
import com.example.myimageloaderproject.shared.error.ErrorMapper

class HomeModule(
    httpClient: HttpClient,
    fileStorageProvider: FileStorageProvider,
    connectivityProvider: ConnectivityProvider
) {
    private val photoMapper by lazy { PhotoMapper() }

    private val errorMapper by lazy { ErrorMapper() }

    private val remoteDataSource by lazy {
        UnsplashRemoteDataSource(httpClient)
    }

    private val diskCache by lazy {
        PhotoDiskCache(fileStorageProvider)
    }

    private val memoryCache by lazy {
        PhotoMemoryCache()
    }

    private val localDataSource by lazy {
        PhotoLocalDataSource(
            diskCache = diskCache,
            memoryCache = memoryCache
        )
    }

    val photoRepository: PhotoRepository by lazy {
        PhotoRepositoryImpl(
            remoteDataSource = remoteDataSource,
            localDataSource = localDataSource,
            photoMapper = photoMapper,
            errorMapper = errorMapper
        )
    }

    val loadInitialPhotosUseCase by lazy {
        LoadInitialPhotosUseCase(photoRepository)
    }

    val refreshPhotosUseCase by lazy {
        RefreshPhotosUseCase(photoRepository)
    }

    val loadMorePhotosUseCase by lazy {
        LoadMorePhotosUseCase(photoRepository)
    }

    val preloadPhotosUseCase by lazy {
        PreloadPhotosUseCase(photoRepository)
    }

    val getCachedPhotosUseCase by lazy {
        GetCachedPhotosUseCase(photoRepository)
    }

    fun createHomeViewModelFactory(): com.example.myimageloaderproject.modules.home.presentation.HomeViewModelFactory {
        return com.example.myimageloaderproject.modules.home.presentation.HomeViewModelFactory(
            loadInitialPhotosUseCase = loadInitialPhotosUseCase,
            refreshPhotosUseCase = refreshPhotosUseCase,
            loadMorePhotosUseCase = loadMorePhotosUseCase,
            preloadPhotosUseCase = preloadPhotosUseCase,
            getCachedPhotosUseCase = getCachedPhotosUseCase
        )
    }
}
