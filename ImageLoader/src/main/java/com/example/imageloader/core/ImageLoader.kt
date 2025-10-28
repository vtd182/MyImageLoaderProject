package com.example.imageloader.core

import android.content.Context
import com.example.imageloader.cache.ActiveResources
import com.example.imageloader.cache.DiskCache
import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.decode.BitmapDecoder
import com.example.imageloader.fetcher.HttpFetcher

class ImageLoader private constructor(context: Context) {

    private val diskCache = DiskCache(context)
    private val activeResources = ActiveResources()
    private val fetcher = HttpFetcher()
    private val bitmapPool = LruBitmapPool((Runtime.getRuntime().maxMemory() / 8))
    private val memoryCache =
        MemoryCache((Runtime.getRuntime().maxMemory() / 8).toInt(), bitmapPool)
    val engine = Engine(activeResources, memoryCache, diskCache, fetcher, bitmapPool)
    
    init {
        // Set bitmap pool for decoder to reuse bitmaps
        BitmapDecoder.setBitmapPool(bitmapPool)
        // Enable/disable bitmap pool usage for decode
        // Set to false for better FPS, true for less GC
        BitmapDecoder.setUseBitmapPool(false)
    }

    companion object {
        @Volatile
        private var INSTANCE: ImageLoader? = null

        fun getInstance(context: Context): ImageLoader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ImageLoader(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun with(context: Context): RequestBuilder {
            return RequestBuilder(getInstance(context).engine)
        }
        
        fun setFastScrolling(context: Context, isFast: Boolean) {
            getInstance(context).engine.setFastScrolling(isFast)
        }
    }
}
