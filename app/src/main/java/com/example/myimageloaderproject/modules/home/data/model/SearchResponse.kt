package com.example.myimageloaderproject.modules.home.data.model

import UnsplashPhoto

data class SearchResponse(
    val total: Int,
    val total_pages: Int,
    val results: List<UnsplashPhoto>
)
