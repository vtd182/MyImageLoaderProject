package com.example.imageloader.core

import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.fetcher.HttpFetcher

class ImageLoader private constructor() {

    private val memoryCache = MemoryCache(8 * 1024) // 8MB
    private val fetcher = HttpFetcher()
    val engine = Engine(memoryCache, fetcher)

    companion object {
        val instance: ImageLoader by lazy { ImageLoader() }
        fun with(): RequestBuilder = RequestBuilder(instance.engine)
    }
}
