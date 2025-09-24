package com.example.imageloader.cache

import com.example.imageloader.core.Resource
import com.example.imageloader.core.ResourceListener

class ActiveResources : ResourceListener {
    private val activeMap = mutableMapOf<String, Resource>()
    private var resourceReleasedCallback: ((String, Resource) -> Unit)? = null

    fun setOnResourceReleased(callback: (String, Resource) -> Unit) {
        resourceReleasedCallback = callback
    }

    @Synchronized
    fun put(key: String, resource: Resource) {
        activeMap[key] = resource
    }

    @Synchronized
    fun get(key: String): Resource? {
        return activeMap[key]
    }

    @Synchronized
    fun remove(key: String) {
        activeMap.remove(key)
    }

    override fun onResourceReleased(key: String, resource: Resource) {
        synchronized(this) {
            activeMap.remove(key)
        }
        resourceReleasedCallback?.invoke(key, resource)
    }
}
