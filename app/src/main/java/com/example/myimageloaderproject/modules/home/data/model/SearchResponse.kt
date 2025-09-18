package com.example.myimageloaderproject.modules.home.data.model

data class SearchResponse(
    val total: Int,
    val total_pages: Int,
    val results: List<UnsplashPhotoDTO>
)
