package com.example.myimageloaderproject.modules.home.domain.model

import UnsplashPhoto

data class SearchResponse(
    val total: Int,
    val total_pages: Int,
    val results: List<UnsplashPhoto>
)
