package com.example.imageloader.core

import android.content.Context
import com.example.imageloader.cache.ActiveResources
import com.example.imageloader.cache.DiskCache
import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.fetcher.HttpFetcher

class ImageLoader private constructor(context: Context) {

    private val memoryCache = MemoryCache((Runtime.getRuntime().maxMemory() / 8).toInt())
    private val diskCache = DiskCache(context)
    private val activeResources = ActiveResources(memoryCache)
    private val fetcher = HttpFetcher()
    val engine = Engine(activeResources, memoryCache, diskCache, fetcher)

    companion object {
        @Volatile
        private var INSTANCE: ImageLoader? = null

        fun getInstance(context: Context): ImageLoader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageLoader(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun with(context: Context): RequestBuilder {
            return getInstance(context).let { instance ->
                RequestBuilder(instance.engine, instance.activeResources)
            }
        }
    }
}
