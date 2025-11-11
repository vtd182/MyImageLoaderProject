package com.example.myimageloaderproject.modules.home.domain.model

data class LoadPhotoResult(
    val photos: List<UnsplashPhoto>,
    val currentPage: Int,
    val isFromCache: Boolean = false
)
