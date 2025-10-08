package com.example.imageloader.target

import com.example.imageloader.core.EngineResource

interface Target {
    fun onResourceReady(engineResource: EngineResource)
    fun onLoadFailed()
    fun onPlaceholderColor(color: Int)
    fun isValidFor(key: String): Boolean
}
