package com.example.myimageloaderproject.modules.home.domain.model
import kotlinx.serialization.Serializable

@Serializable
data class UnsplashUrls(
    val thumb: String? = null,
    val small: String? = "",
    val medium: String? = "",
    val regular: String? = null,
    val large: String? = "",
    val full: String? = null,
    val raw: String? = null
)
