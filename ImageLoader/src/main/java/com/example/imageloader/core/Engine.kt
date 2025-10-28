package com.example.imageloader.core

import com.example.imageloader.cache.ActiveResources
import com.example.imageloader.cache.DiskCache
import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.core.abstract.BitmapPool
import com.example.imageloader.decode.BitmapDecoder
import com.example.imageloader.fetcher.DataFetcher
import com.example.imageloader.logger.ImageLoadLog
import com.example.imageloader.logger.ImageLoaderLogger
import com.example.imageloader.logger.LogCategory
import com.example.imageloader.logger.LogSource
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
            } else {
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

    fun checkMemoryCache(req: Request, target: Target): Boolean {
        val key = buildKey(req)
        val startTime = System.currentTimeMillis()

        // 1️⃣ Active Resources
        activeResources.get(key)?.let { resource ->
            if (!resource.isReleased() && !resource.getBitmap().isRecycled) {
                target.onResourceReady(resource)

                ImageLoaderLogger.log(
                    ImageLoadLog(
                        url = req.url,
                        source = LogSource.ACTIVE_CACHE,
                        totalTimeMs = System.currentTimeMillis() - startTime
                    )
                )
                return true
            } else {
                activeResources.remove(key)
            }
        }

        // 2️⃣ Memory Cache
        memoryCache.get(key)?.let { bitmap ->
            if (!bitmap.isRecycled) {
                val res = EngineResource(key, bitmap, activeResources)
                activeResources.put(key, res)
                target.onResourceReady(res)

                ImageLoaderLogger.log(
                    ImageLoadLog(
                        url = req.url,
                        source = LogSource.MEMORY_CACHE,
                        totalTimeMs = System.currentTimeMillis() - startTime
                    )
                )
                return true
            } else {
                memoryCache.remove(key)
            }
        }

        return false
    }

    fun load(
        req: Request,
        target: Target,
        priority: RequestPriority = RequestPriority.NORMAL
    ): Job {
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
            try {
                // 🕐 Decode (skip if fast scrolling and low quality is acceptable)
                val decodeStart = System.currentTimeMillis()
                var bitmap =
                    if (isFastScrolling && req.resizeWidth != null && req.resizeHeight != null) {
                        BitmapDecoder.decode(bytes, req.resizeWidth * 2, req.resizeHeight * 2)
                    } else {
                        BitmapDecoder.decode(bytes, req.resizeWidth ?: 0, req.resizeHeight ?: 0)
                    }
                val decodeTime = System.currentTimeMillis() - decodeStart
                
                // 🕐 Transform lại (nếu có) - skip during fast scroll for better performance
                var transformTime: Long? = null
                if (req.transformations.isNotEmpty() && !isFastScrolling) {
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
                    transformTime = t
                }

                val res = EngineResource(key, bitmap, activeResources)
                activeResources.put(key, res)

                withContext(Dispatchers.Main) {
                    target.onResourceReady(res)
                }

                val totalTime = System.currentTimeMillis() - startTime

                ImageLoaderLogger.log(
                    ImageLoadLog(
                        url = req.url,
                        source = LogSource.DISK_CACHE,
                        decodeTimeMs = decodeTime,
                        transformTimeMs = transformTime,
                        totalTimeMs = totalTime,
                        transformCount = req.transformations.size,
                        isFastScrolling = isFastScrolling
                    )
                )
                return
            } catch (e: Exception) {
                ImageLoaderLogger.e(TAG, "Disk cache decode failed for: ${req.url}", e, LogCategory.CACHE)
            }
        }

        // 4️⃣ Network fetch + decode + transform
        try {
            // 🕐 Fetch
            val fetchStart = System.currentTimeMillis()
            val result = fetcher.fetch(req.url)
            val bytes = result.bytes
            val contentType = result.contentType
            val fetchElapsed = System.currentTimeMillis() - fetchStart

            // 🕐 Decode (optimized for fast scroll)
            val decodeStart = System.currentTimeMillis()
            var bitmap =
                if (isFastScrolling && req.resizeWidth != null && req.resizeHeight != null) {
                    BitmapDecoder.decode(bytes, req.resizeWidth * 2, req.resizeHeight * 2)
                } else {
                    BitmapDecoder.decode(bytes, req.resizeWidth ?: 0, req.resizeHeight ?: 0)
                }
            val decodeTime = System.currentTimeMillis() - decodeStart

            // 🕐 Transform (skip during fast scroll)
            var transformTime: Long? = null
            if (req.transformations.isNotEmpty() && !isFastScrolling) {
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
                transformTime = t
            }

            // 🕐 Cache
            val cacheTime = measureTimeMillis {
                if (req.useDiskCache) diskCache.put(dataKey, bytes, contentType)
            }

            // 🕐 Wrap & Deliver
            val res = EngineResource(key, bitmap, activeResources)
            activeResources.put(key, res)

            withContext(Dispatchers.Main) {
                target.onResourceReady(res)
            }

            val totalTime = System.currentTimeMillis() - startTime

            ImageLoaderLogger.log(
                ImageLoadLog(
                    url = req.url,
                    source = LogSource.NETWORK,
                    fetchTimeMs = fetchElapsed,
                    decodeTimeMs = decodeTime,
                    transformTimeMs = transformTime,
                    cacheWriteTimeMs = cacheTime,
                    totalTimeMs = totalTime,
                    transformCount = req.transformations.size,
                    isFastScrolling = isFastScrolling
                )
            )
        } catch (e: Exception) {
            when (e) {
                is CancellationException -> {
                    // Silently ignore cancellations
                }
                else -> {
                    ImageLoaderLogger.log(
                        ImageLoadLog(
                            url = req.url,
                            source = LogSource.NETWORK,
                            totalTimeMs = System.currentTimeMillis() - startTime,
                            error = e.message ?: e.javaClass.simpleName
                        )
                    )

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
