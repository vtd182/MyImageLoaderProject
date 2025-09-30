package com.example.imageloader.core.abstract

import com.example.imageloader.core.EngineResource

interface ResourceListener {
    fun onResourceReleased(key: String, engineResource: EngineResource)
}