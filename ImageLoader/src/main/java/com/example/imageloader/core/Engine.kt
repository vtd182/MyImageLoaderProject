// core/Engine.kt
package com.example.imageloader.core

import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.decode.BitmapDecoder
import com.example.imageloader.fetcher.DataFetcher
import com.example.imageloader.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Engine(
    private val memoryCache: MemoryCache,
    private val fetcher: DataFetcher
) {
    fun load(req: Request, target: Target) {
        val key = req.url
        val cached = memoryCache.get(key)
        if (cached != null) {
            target.onResourceReady(cached)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bytes = fetcher.fetch(req.url)
                val bitmap = BitmapDecoder.decode(
                    bytes,
                    req.resizeWidth ?: 0,
                    req.resizeHeight ?: 0
                )
                memoryCache.put(key, bitmap)
                withContext(Dispatchers.Main) {
                    target.onResourceReady(bitmap)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    target.onLoadFailed()
                }
            }
        }
    }
}
