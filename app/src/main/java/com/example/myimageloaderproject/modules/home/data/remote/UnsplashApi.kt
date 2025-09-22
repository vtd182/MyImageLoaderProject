package com.example.myimageloaderproject.modules.home.data.remote

import com.example.myimageloaderproject.core.base.BaseApiService
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import com.example.myimageloaderproject.network.HttpClient

import kotlinx.serialization.builtins.ListSerializer

interface UnsplashApi {
    suspend fun getRandomPhotos(page: Int, perPage: Int = 20): List<UnsplashPhoto>
}

class UnsplashApiImpl(
    client: HttpClient
) : BaseApiService(client), UnsplashApi {

    override suspend fun getRandomPhotos(page: Int, perPage: Int): List<UnsplashPhoto> {
        return get(
            path = "photos",
            query = mapOf("page" to page.toString(), "per_page" to perPage.toString()),
            deserializer = ListSerializer(UnsplashPhoto.serializer())
        )
    }
}
