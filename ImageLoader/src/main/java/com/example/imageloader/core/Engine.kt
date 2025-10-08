package com.example.imageloader.core

import android.util.Log
import com.example.imageloader.cache.ActiveResources
import com.example.imageloader.cache.DiskCache
import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.core.abstract.BitmapPool
import com.example.imageloader.decode.BitmapDecoder
import com.example.imageloader.fetcher.DataFetcher
import com.example.imageloader.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class Engine(
    private val activeResources: ActiveResources,
    private val memoryCache: MemoryCache,
    private val diskCache: DiskCache,
    private val fetcher: DataFetcher,
    private val bitmapPool: BitmapPool,
) {
    companion object {
        private const val TAG = "Engine"
    }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        activeResources.setOnResourceReleased { key, resource ->
            val bitmap = resource.getBitmap()
            memoryCache.put(key, bitmap)
            Log.d(TAG, "Resource released -> moved to memoryCache: $key")
        }
    }

    fun load(req: Request, target: Target): Job {
        val key = buildKey(req)
        Log.d(TAG, "Start load: $key")

        // 1. Active Resources
        activeResources.get(key)?.let {
            Log.d(TAG, "Hit ActiveResources: $key")
            target.onResourceReady(it)
            return Job().apply { complete() }
        }

        // 2. Memory Cache
        memoryCache.get(key)?.let { bitmap ->
            Log.d(TAG, "Hit MemoryCache: $key")
            val res = EngineResource(key, bitmap, activeResources)
            activeResources.put(key, res)
            target.onResourceReady(res)
            return Job().apply { complete() }
        }

        // 3. Disk Cache
        diskCache.get(key)?.let { bitmap ->
            Log.d(TAG, "Hit DiskCache: $key")
            memoryCache.put(key, bitmap)
            val res = EngineResource(key, bitmap, activeResources)
            activeResources.put(key, res)
            target.onResourceReady(res)
            return Job().apply { complete() }
        }

        // 4. Network fetch + decode + transform
        Log.d(TAG, "Miss cache -> fetch from network: $key")

        return engineScope.launch {
            try {
                // Fetch: I/O bound
                val bytes = fetcher.fetch(req.url)
                Log.d(TAG, "Fetched bytes size=${bytes.size} for $key")

                // Decode: I/O bound
                var bitmap = BitmapDecoder.decode(
                    bytes,
                    req.resizeWidth ?: 0,
                    req.resizeHeight ?: 0,
                )

                Log.d(TAG, "Decoded bitmap w=${bitmap.width} h=${bitmap.height} for $key")

                // Transform: CPU bound Dispatchers.Default
                if (req.transformations.isNotEmpty()) {
                    bitmap = withContext(Dispatchers.Default) {
                        req.transformations.fold(bitmap) { bmp, transform ->
                            transform.transform(
                                bitmapPool,
                                bmp,
                                req.outWidth ?: req.resizeWidth ?: bmp.width,
                                req.outHeight ?: req.resizeHeight ?: bmp.height
                            )
                        }
                    }
                    Log.d(TAG, "Applied ${req.transformations.size} transforms for $key")
                }

                // Wrap Resource
                val res = EngineResource(key, bitmap, activeResources)
                activeResources.put(key, res)

                // Cache
                if (req.useDiskCache) {
                    diskCache.put(key, bitmap)
                }

                // Deliver: UI bound
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
        val rawKey = buildString {
            append(req.url)

            if (req.resizeWidth != null && req.resizeHeight != null)
                append("#resize=${req.resizeWidth}x${req.resizeHeight}")

            if (req.outWidth != null && req.outHeight != null)
                append("#out=${req.outWidth}x${req.outHeight}")

            if (req.transformations.isNotEmpty()) {
                append("#transforms=")
                req.transformations.forEach {
                    append(it.key())
                    append(";")
                }
            }

            append("#useMemory=${req.useMemoryCache}")
            append("#useDisk=${req.useDiskCache}")
        }
        return rawKey.md5()
    }
}

private fun String.md5(): String {
    val digest = MessageDigest.getInstance("MD5")
    val bytes = digest.digest(toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
