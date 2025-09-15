package com.example.myimageloaderproject.modules.home.data.remote

import UnsplashPhoto
import retrofit2.http.GET
import retrofit2.http.Query

interface UnsplashApi {
    @GET("photos")
    suspend fun getRandomPhotos(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 20
    ): List<UnsplashPhoto>
}
