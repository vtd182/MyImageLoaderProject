package com.example.imageloader.core

import android.graphics.Bitmap
import com.example.imageloader.cache.ActiveResources
import com.example.imageloader.cache.DiskCache
import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.decode.BitmapDecoder
import com.example.imageloader.fetcher.DataFetcher
import com.example.imageloader.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Engine(
    private val activeResources: ActiveResources,
    private val memoryCache: MemoryCache,
    private val diskCache: DiskCache,
    private val fetcher: DataFetcher
) {

    fun load(req: Request, target: Target) {
        val key = buildKey(req)

        // 1. Active Resources
        activeResources.get(key)?.let {
            target.onResourceReady(it)
            return
        }

        // 2. Memory Cache
        memoryCache.get(key)?.let {
            activeResources.put(key, it)
            target.onResourceReady(it)
            return
        }

        // 3. Disk Cache
        diskCache.get(key)?.let {
            memoryCache.put(key, it)
            activeResources.put(key, it)
            target.onResourceReady(it)
            return
        }

        // 4. Network
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bytes = fetcher.fetch(req.url)
                val bitmap = BitmapDecoder.decode(bytes, req.resizeWidth ?: 0, req.resizeHeight ?: 0)

                if (req.useMemoryCache) memoryCache.put(key, bitmap)
                activeResources.put(key, bitmap)
                if (req.useDiskCache) diskCache.put(key, bitmap)

                withContext(Dispatchers.Main) {
                    target.onResourceReady(bitmap)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { target.onLoadFailed() }
            }
        }
    }



    private fun buildKey(req: Request): String {
        return buildString {
            append(req.url)
            if (req.resizeWidth != null && req.resizeHeight != null) {
                append("#${req.resizeWidth}x${req.resizeHeight}")
            }
        }
    }
}
