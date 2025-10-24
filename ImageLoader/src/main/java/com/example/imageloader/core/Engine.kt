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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.system.measureTimeMillis

enum class RequestPriority {
    HIGH, NORMAL, LOW
}

data class PrioritizedRequest(
    val request: Request,
    val target: Target,
    val priority: RequestPriority,
    val job: Job
)

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
    private val highPriorityQueue = Channel<PrioritizedRequest>(Channel.UNLIMITED)
    private val normalPriorityQueue = Channel<PrioritizedRequest>(Channel.UNLIMITED)
    private val lowPriorityQueue = Channel<PrioritizedRequest>(Channel.UNLIMITED)

    @Volatile
    private var isFastScrolling = false
    private var fastScrollJob: Job? = null

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

        startPriorityWorkers()
    }

    private fun startPriorityWorkers() {
        repeat(2) {
            engineScope.launch {
                for (prioritizedReq in highPriorityQueue) {
                    if (!prioritizedReq.job.isCancelled) {
                        executeLoad(prioritizedReq.request, prioritizedReq.target)
                    }
                }
            }
        }

        engineScope.launch {
            for (prioritizedReq in normalPriorityQueue) {
                if (!prioritizedReq.job.isCancelled) {
                    if (isFastScrolling) {
                        delay(50)
                    }
                    executeLoad(prioritizedReq.request, prioritizedReq.target)
                }
            }
        }

        engineScope.launch {
            for (prioritizedReq in lowPriorityQueue) {
                if (!prioritizedReq.job.isCancelled) {
                    if (isFastScrolling) {
                        delay(100)
                    }
                    executeLoad(prioritizedReq.request, prioritizedReq.target)
                }
            }
        }
    }

    fun setFastScrolling(isFast: Boolean) {
        isFastScrolling = isFast
        if (isFast) {
            fastScrollJob?.cancel()
            fastScrollJob = engineScope.launch {
                delay(300)
                isFastScrolling = false
            }
        }
    }

    fun load(
        req: Request,
        target: Target,
        priority: RequestPriority = RequestPriority.NORMAL
    ): Job {
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

        // 3️⃣ Disk Cache or Network - use priority queue
        // Notify target that loading has started
        engineScope.launch(Dispatchers.Main) {
            target.onLoadStarted()
        }
        
        val job = Job()
        val prioritizedReq = PrioritizedRequest(req, target, priority, job)

        engineScope.launch {
            when (priority) {
                RequestPriority.HIGH -> highPriorityQueue.send(prioritizedReq)
                RequestPriority.NORMAL -> normalPriorityQueue.send(prioritizedReq)
                RequestPriority.LOW -> lowPriorityQueue.send(prioritizedReq)
            }
        }

        return job
    }

    private suspend fun executeLoad(req: Request, target: Target) {
        val key = buildKey(req)
        val dataKey = buildDataKey(req)
        val startTime = System.currentTimeMillis()

        // 3️⃣ Disk Cache (raw bytes) → decode + transform lại
        diskCache.get(dataKey)?.let { bytes ->
            Log.d(TAG, "[DiskCache] Found cached data: $key")
            try {
                // 🕐 Decode (skip if fast scrolling and low quality is acceptable)
                val decodeStart = System.currentTimeMillis()
                var bitmap =
                    if (isFastScrolling && req.resizeWidth != null && req.resizeHeight != null) {
                        Log.d(TAG, "[FastScroll] Using downscaled decode for: $key")
                        BitmapDecoder.decode(bytes, req.resizeWidth * 2, req.resizeHeight * 2)
                    } else {
                        BitmapDecoder.decode(bytes, req.resizeWidth ?: 0, req.resizeHeight ?: 0)
                    }
                Log.d(TAG, "[Decode from Disk] ${System.currentTimeMillis() - decodeStart}ms")

                // 🕐 Transform lại (nếu có) - skip during fast scroll for better performance
                if (req.transformations.isNotEmpty()) {
                    if (isFastScrolling) {
                        Log.d(TAG, "[FastScroll] Skipping transformations for: $key")
                    } else {
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
                }

                val res = EngineResource(key, bitmap, activeResources)
                activeResources.put(key, res)

                withContext(Dispatchers.Main) {
                    target.onResourceReady(res)
                }
                Log.d(
                    TAG,
                    "[DiskCache] Completed in ${System.currentTimeMillis() - startTime}ms -> $key"
                )
                return
            } catch (e: Exception) {
                Log.e(TAG, "Disk decode/transform failed: $key", e)
            }
        }

        // 4️⃣ Network fetch + decode + transform
        Log.d(TAG, "Miss cache -> fetch from network: $key")
        try {
            // 🕐 Fetch
            val fetchStart = System.currentTimeMillis()
            val result = fetcher.fetch(req.url)
            val bytes = result.bytes
            val contentType = result.contentType
            val fetchElapsed = System.currentTimeMillis() - fetchStart
            Log.d(TAG, "[Fetch] ${fetchElapsed}ms")

            // 🕐 Decode (optimized for fast scroll)
            val decodeStart = System.currentTimeMillis()
            var bitmap =
                if (isFastScrolling && req.resizeWidth != null && req.resizeHeight != null) {
                    Log.d(TAG, "[FastScroll] Using downscaled decode for: $key")
                    BitmapDecoder.decode(bytes, req.resizeWidth * 2, req.resizeHeight * 2)
                } else {
                    BitmapDecoder.decode(bytes, req.resizeWidth ?: 0, req.resizeHeight ?: 0)
                }
            Log.d(TAG, "[Decode] ${System.currentTimeMillis() - decodeStart}ms")

            // 🕐 Transform (skip during fast scroll)
            if (req.transformations.isNotEmpty()) {
                if (isFastScrolling) {
                    Log.d(TAG, "[FastScroll] Skipping transformations for: $key")
                } else {
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
            }
            Log.d(TAG, "[Network] Completed in ${System.currentTimeMillis() - startTime}ms -> $key")
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
