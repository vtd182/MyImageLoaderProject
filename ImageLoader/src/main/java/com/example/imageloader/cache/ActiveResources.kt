package com.example.imageloader.cache

import com.example.imageloader.core.EngineResource
import com.example.imageloader.core.abstract.ResourceListener

class ActiveResources : ResourceListener {
    private val activeMap = mutableMapOf<String, EngineResource>()
    private var resourceReleasedCallback: ((String, EngineResource) -> Unit)? = null

    fun setOnResourceReleased(callback: (String, EngineResource) -> Unit) {
        resourceReleasedCallback = callback
    }

    @Synchronized
    fun put(key: String, engineResource: EngineResource) {
        activeMap[key] = engineResource
    }

    @Synchronized
    fun get(key: String): EngineResource? {
        return activeMap[key]
    }

    @Synchronized
    fun remove(key: String) {
        activeMap.remove(key)
    }

    override fun onResourceReleased(key: String, engineResource: EngineResource) {
        synchronized(this) {
            activeMap.remove(key)
        }
        resourceReleasedCallback?.invoke(key, engineResource)
    }
}
