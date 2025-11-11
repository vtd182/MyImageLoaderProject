package com.example.myimageloaderproject.modules.home.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UnsplashUserDTO(
    val id: String,
    val username: String,
    val name: String,
    val portfolio_url: String? = null,
    val bio: String?,
    val location: String? = null,
    val total_likes: Int,
    val total_photos: Int,
    val total_collection: Int = 0,
    val profile_image: UnsplashUrlsDTO,
    val links: UnsplashLinksDTO
)
