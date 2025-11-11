package com.example.myimageloaderproject.modules.home.data.source.remote

import com.example.myimageloaderproject.modules.home.data.model.UnsplashPhotoDTO
import com.example.myimageloaderproject.network.HttpClient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class UnsplashRemoteDataSource(
    private val httpClient: HttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    suspend fun getPhotos(page: Int, perPage: Int): List<UnsplashPhotoDTO> {
        val response = httpClient.get(
            path = "photos",
            query = mapOf(
                "page" to page.toString(),
                "per_page" to perPage.toString()
            )
        )
        
        return json.decodeFromString(
            ListSerializer(UnsplashPhotoDTO.serializer()),
            response
        )
    }
}
