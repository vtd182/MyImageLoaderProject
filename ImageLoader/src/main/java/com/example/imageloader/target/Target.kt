package com.example.imageloader.target

import com.example.imageloader.core.EngineResource

interface Target {
    fun onLoadStarted()
    fun onResourceReady(engineResource: EngineResource)
    fun onLoadFailed(onRetry: (() -> Unit)? = null)
    fun onPlaceholderColor(color: Int)
    fun isValidFor(key: String): Boolean
}
