package com.example.myimageloaderproject.modules.home.domain.model
import kotlinx.serialization.Serializable

@Serializable
data class UnsplashLinks(
    val self: String,
    val html: String,
    val photos: String? = null,
    val likes: String? = null,
    val portfolio: String? = null,
    val download: String? = null,
    val download_location: String? = null
)
