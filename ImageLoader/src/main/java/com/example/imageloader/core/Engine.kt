package com.example.imageloader.core

import android.util.Log
import com.example.imageloader.cache.ActiveResources
import com.example.imageloader.cache.DiskCache
import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.decode.BitmapDecoder
import com.example.imageloader.fetcher.DataFetcher
import com.example.imageloader.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Engine(
    private val activeResources: ActiveResources,
    private val memoryCache: MemoryCache,
    private val diskCache: DiskCache,
    private val fetcher: DataFetcher,
    private val bitmapPool: BitmapPool? = null
) {
    companion object {
        private const val TAG = "Engine"
    }

    init {
        activeResources.setOnResourceReleased { key, resource ->
            val bitmap = resource.getBitmap()

            memoryCache.put(key, bitmap)

//            if (bitmap.isMutable && bitmapPool != null) {
//                bitmapPool.put(bitmap)
//            }

            Log.d(TAG, "Resource released -> moved to cache/pool: $key")
        }
    }

    fun load(req: Request, target: Target): Job? {
        val key = buildKey(req)
        Log.d(TAG, "Start load: $key")

        // 1. Active Resources
        activeResources.get(key)?.let {
            Log.d(TAG, "Hit ActiveResources: $key")
            target.onResourceReady(it)
            return null
        }

        // 2. Memory Cache
        memoryCache.get(key)?.let { bitmap ->
            Log.d(TAG, "Hit MemoryCache: $key")
            val res = Resource(key, bitmap, activeResources)
            activeResources.put(key, res)
            target.onResourceReady(res)
            return null
        }

        // 3. Disk Cache
        diskCache.get(key)?.let { bitmap ->
            Log.d(TAG, "Hit DiskCache: $key")
            memoryCache.put(key, bitmap)
            val res = Resource(key, bitmap, activeResources)
            activeResources.put(key, res)
            target.onResourceReady(res)
            return null
        }

        // 4. Network
        Log.d(TAG, "Miss cache -> fetch from network: $key")
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                val bytes = fetcher.fetch(req.url)
                Log.d(TAG, "Fetched bytes size=${bytes.size} for $key")

                val dominantColor = BitmapDecoder.extractDominantColor(bytes)

                withContext(Dispatchers.Main) {
                    target.onPlaceholderColor(dominantColor)
                }

                val bitmap = BitmapDecoder.decode(
                    bytes,
                    req.resizeWidth ?: 0,
                    req.resizeHeight ?: 0,
                )
                Log.d(TAG, "Decoded bitmap w=${bitmap.width} h=${bitmap.height} for $key")

                val res = Resource(key, bitmap, activeResources)
                activeResources.put(key, res)

                if (req.useDiskCache) {
                    diskCache.put(key, bitmap)
                }

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Deliver to target: $key")
                    target.onResourceReady(res)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load failed: $key", e)
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
