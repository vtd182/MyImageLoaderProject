package com.example.imageloader.core

import android.util.Log
import com.example.imageloader.cache.ActiveResources
import com.example.imageloader.cache.DiskCache
import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.core.abstract.BitmapPool
import com.example.imageloader.decode.BitmapDecoder
import com.example.imageloader.fetcher.DataFetcher
import com.example.imageloader.target.Target
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.system.measureTimeMillis

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
            if (!bitmap.isRecycled) {
                memoryCache.put(key, bitmap)
                Log.d(TAG, "Resource released -> moved to memoryCache: $key")
            } else {
                Log.w(TAG, "Resource released but bitmap already recycled, skip caching: $key")
                memoryCache.remove(key)
            }
        }
    }

    fun load(req: Request, target: Target): Job {
        val key = buildKey(req)
        val dataKey = buildDataKey(req) // key for disk cache, without transformations
        val startTime = System.currentTimeMillis()

        fun logDuration(stage: String) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "[$stage] Completed in ${elapsed}ms -> $key")
        }

        // 1️⃣ Active Resources
        activeResources.get(key)?.let { resource ->
            // Check if resource is still valid (not released and bitmap not recycled)
            if (!resource.isReleased() && !resource.getBitmap().isRecycled) {
                logDuration("ActiveResource")
                target.onResourceReady(resource)
                return Job().apply { complete() }
            } else {
                // Remove stale resource from active cache
                Log.w(
                    TAG,
                    "Found invalid resource in active cache, removing: $key (released=${resource.isReleased()}, recycled=${resource.getBitmap().isRecycled})"
                )
                activeResources.remove(key)
            }
        }

        // 2️⃣ Memory Cache
        memoryCache.get(key)?.let { bitmap ->
            if (!bitmap.isRecycled) {
                logDuration("MemoryCache")
                val res = EngineResource(key, bitmap, activeResources)
                activeResources.put(key, res)
                target.onResourceReady(res)
                return Job().apply { complete() }
            } else {
                // Remove recycled bitmap from cache
                Log.w(TAG, "Found recycled bitmap in memory cache, removing: $key")
                memoryCache.remove(key)
            }
        }

        // 3️⃣ Disk Cache (raw bytes) → decode + transform lại
        diskCache.get(dataKey)?.let { bytes ->
            logDuration("DiskCache")
            return engineScope.launch {
                try {
                    // 🕐 Decode
                    var bitmap = BitmapDecoder.decode(
                        bytes,
                        req.resizeWidth ?: 0,
                        req.resizeHeight ?: 0,
                    )

                    // 🕐 Transform lại (nếu có)
                    if (req.transformations.isNotEmpty()) {
                        val t = measureTimeMillis {
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
                        }
                        Log.d(
                            TAG,
                            "[Transform from Disk] $t ms (${req.transformations.size} transforms)"
                        )
                    }

                    val res = EngineResource(key, bitmap, activeResources)
                    activeResources.put(key, res)

                    withContext(Dispatchers.Main) {
                        target.onResourceReady(res)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Disk decode/transform failed: $key", e)
                    withContext(Dispatchers.Main) {
                        target.onLoadFailed {
                            engineScope.launch {
                                withContext(Dispatchers.Main) { load(req, target) }
                            }
                        }
                    }
                }
            }
        }

        // 4️⃣ Network fetch + decode + transform
        Log.d(TAG, "Miss cache -> fetch from network: $key")

        return engineScope.launch {
            try {
                var stageStart: Long
                var elapsed: Long

                // 🕐 Fetch
                stageStart = System.currentTimeMillis()
                val result = fetcher.fetch(req.url)
                val bytes = result.bytes
                val contentType = result.contentType
                Log.d(TAG, "[Fetch] ${System.currentTimeMillis() - stageStart}ms")
                elapsed = System.currentTimeMillis() - stageStart
                Log.d(TAG, "[Fetch] ${elapsed}ms")

                // 🕐 Decode
                var bitmap = BitmapDecoder.decode(
                    bytes,
                    req.resizeWidth ?: 0,
                    req.resizeHeight ?: 0,
                ).also {
                    Log.d(
                        TAG,
                        "[Decode] ${(System.currentTimeMillis() - stageStart)}ms (since fetch)"
                    )
                }

                // 🕐 Transform
                if (req.transformations.isNotEmpty()) {
                    val t = measureTimeMillis {
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
                    }
                    Log.d(TAG, "[Transform] $t ms (${req.transformations.size} transforms)")
                }

                // 🕐 Cache
                val cacheTime = measureTimeMillis {
                    if (req.useDiskCache) diskCache.put(dataKey, bytes, contentType)
                }
                Log.d(TAG, "[Cache write] $cacheTime ms")

                // 🕐 Wrap & Deliver
                val res = EngineResource(key, bitmap, activeResources)
                activeResources.put(key, res)

                withContext(Dispatchers.Main) {
                    target.onResourceReady(res)
                    logDuration("Network + Decode + Transform")
                }
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> Log.d(TAG, "Cancelled loading: $key")
                    else -> {
                        Log.e(TAG, "Load failed: $key", e)
                        withContext(Dispatchers.Main) {
                            target.onLoadFailed {
                                engineScope.launch {
                                    withContext(Dispatchers.Main) { load(req, target) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    fun buildKey(req: Request): String {
        val rawKey = buildString {
            append(req.url)
            if (req.resizeWidth != null && req.resizeHeight != null)
                append("#resize=${req.resizeWidth}x${req.resizeHeight}")
            if (req.outWidth != null && req.outHeight != null)
                append("#out=${req.outWidth}x${req.outHeight}")
            if (req.transformations.isNotEmpty()) {
                append("#transforms=${req.transformations.joinToString(";") { it.key() }}")
            }
            append("#useMemory=${req.useMemoryCache}")
            append("#useDisk=${req.useDiskCache}")
        }
        return rawKey.md5()
    }

    fun buildDataKey(req: Request): String {
        val rawKey = buildString {
            append(req.url)
            if (req.resizeWidth != null && req.resizeHeight != null)
                append("#resize=${req.resizeWidth}x${req.resizeHeight}")
            if (req.outWidth != null && req.outHeight != null)
                append("#out=${req.outWidth}x${req.outHeight}")
            // No transformations for data key
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
