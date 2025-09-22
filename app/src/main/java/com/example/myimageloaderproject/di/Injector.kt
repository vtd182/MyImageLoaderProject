package com.example.myimageloaderproject.di
import com.example.myimageloaderproject.modules.home.data.remote.UnsplashApi
import com.example.myimageloaderproject.modules.home.data.remote.UnsplashApiImpl
import com.example.myimageloaderproject.modules.home.data.repository.PhotoRepositoryImpl
import com.example.myimageloaderproject.modules.home.domain.repository.PhotoRepository
import com.example.myimageloaderproject.modules.home.domain.usecase.GetRandomPhotosUseCase
import com.example.myimageloaderproject.network.HttpClient


object Injector {
    private const val BASE_URL = "https://api.unsplash.com/"
    private const val ACCESS_KEY = "WisKyjbbno1lYFrCkYyzZUhiffEkjFdEEAC-kQvMs3I"

    private val httpClient by lazy { HttpClient(BASE_URL, ACCESS_KEY) }

    private val unsplashApi: UnsplashApi by lazy { UnsplashApiImpl(httpClient) }

    private val photoRepository: PhotoRepository by lazy { PhotoRepositoryImpl(unsplashApi) }

    val getRandomPhotosUseCase: GetRandomPhotosUseCase by lazy { GetRandomPhotosUseCase(photoRepository) }
}
