package com.example.imageloader.core

data class Request(
    val url: String,
    val resizeWidth: Int? = null,
    val resizeHeight: Int? = null,
    val useMemoryCache: Boolean = true,
    val useDiskCache: Boolean = true
)
