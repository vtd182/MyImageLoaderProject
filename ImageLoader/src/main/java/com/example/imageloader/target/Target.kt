package com.example.imageloader.target

import com.example.imageloader.core.Resource

interface Target {
    fun onResourceReady(resource: Resource)
    fun onLoadFailed()
    fun onPlaceholderColor(color: Int)
}
