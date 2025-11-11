package com.example.myimageloaderproject.modules.home.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UnsplashUrlsDTO(
    val thumb: String?,
    val small: String,
    val medium: String?,
    val regular: String?,
    val large: String?,
    val full: String?,
    val raw: String?
)
