package com.example.myimageloaderproject.modules.home.domain.model
data class SearchResponse(
    val total: Int,
    val total_pages: Int,
    val results: List<UnsplashPhoto>
)
