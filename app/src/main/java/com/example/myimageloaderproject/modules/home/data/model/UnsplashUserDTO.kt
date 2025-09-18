package com.example.myimageloaderproject.modules.home.data.model


data class UnsplashUserDTO(
    val id: String,
    val username: String,
    val name: String,
    val portfolio_url: String?,
    val bio: String?,
    val location: String?,
    val total_likes: Int,
    val total_photos: Int,
    val total_collection: Int,
    val profile_image: UnsplashUrlsDTO,
    val links: UnsplashLinksDTO
)
