package com.example.myimageloaderproject.modules.home.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UnsplashUrlsDTO(
    val thumb: String? = null,
    val small: String? = "",
    val medium: String? = "",
    val regular: String? = null,
    val large: String? = "",
    val full: String? = null,
    val raw: String? = null
)
