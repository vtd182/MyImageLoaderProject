package com.example.imageloader.core

import android.content.Context
import com.example.imageloader.cache.ActiveResources
import com.example.imageloader.cache.DiskCache
import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.decode.BitmapDecoder
import com.example.imageloader.fetcher.HttpFetcher

class ImageLoader private constructor(context: Context, useBitmapPool: Boolean) {
    private val sizes = MemorySizeCalculator.calculate(context, useBitmapPool)
    private val bitmapPool = LruBitmapPool(sizes.bitmapPoolSize.toLong())
    private val memoryCache = MemoryCache(sizes.memoryCacheSize, bitmapPool)

    private val diskCache = DiskCache(context)
    private val activeResources = ActiveResources()
    private val fetcher = HttpFetcher()

    val engine = Engine(activeResources, memoryCache, diskCache, fetcher, bitmapPool)

    init {
        BitmapDecoder.setBitmapPool(bitmapPool)
        BitmapDecoder.setUseBitmapPool(useBitmapPool)
    }

    companion object {
        @Volatile
        private var INSTANCE: ImageLoader? = null

        fun getInstance(context: Context, useBitmapPool: Boolean = false): ImageLoader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageLoader(context.applicationContext, useBitmapPool).also {
                    INSTANCE = it
                }
            }
        }

        fun with(context: Context, useBitmapPool: Boolean = false): RequestBuilder {
            return RequestBuilder(getInstance(context, useBitmapPool).engine)
        }
    }
}
