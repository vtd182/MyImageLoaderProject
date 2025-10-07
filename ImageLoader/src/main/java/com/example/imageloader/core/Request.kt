package com.example.imageloader.core

import com.example.imageloader.transformation.Transformation

data class Request(
    val url: String,
    val resizeWidth: Int? = null,
    val resizeHeight: Int? = null,
    val useMemoryCache: Boolean = true,
    val useDiskCache: Boolean = true,
    val transformations: List<Transformation> = emptyList(),
    val outWidth: Int? = null,
    val outHeight: Int? = null,
)
