package com.example.myimageloaderproject.modules.home.data.model

data class UnsplashPhotoDTO(
    val id: String,
    val created_at: String,
    val width: Int,
    val height: Int,
    val color: String? = "#000000",
    val likes: Int,
    val description: String?,
    val urls: UnsplashUrlsDTO,
    val links: UnsplashLinksDTO,
    val user: UnsplashUserDTO
)
