package com.example.imageloader.core

import com.example.imageloader.cache.ActiveResources
import com.example.imageloader.cache.DiskCache
import com.example.imageloader.cache.MemoryCache
import com.example.imageloader.core.abstract.BitmapPool
import com.example.imageloader.core.enums.RequestPriority
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.system.measureTimeMillis

/**
 * Engine - Trung tâm xử lý chính của image loader.
 *
 * Đây là thành phần core chịu trách nhiệm điều phối toàn bộ quá trình load ảnh,
 * từ kiểm tra cache đến fetch từ network, decode và transform bitmap.
 *
 * ## Kiến trúc Cache (4 tầng):
 * 1. **Active Resources**: Cache cho các bitmap đang được sử dụng (hiển thị trên UI)
 * 2. **Memory Cache**: LRU cache trong RAM cho các bitmap không còn được sử dụng
 * 3. **Disk Cache**: Cache dữ liệu thô (raw bytes) trên ổ đĩa
 * 4. **Network**: Fetch từ internet nếu không có trong cache
 *
 * ## Priority Queue System:
 * Engine sử dụng 3 hàng đợi ưu tiên để xử lý request:
 * - **HIGH**: 2 workers xử lý song song (cho ảnh cần hiển thị ngay)
 * - **NORMAL**: 1 worker (cho ảnh trong viewport)
 * - **LOW**: 1 worker (cho ảnh ngoài viewport, prefetch)
 *
 * ## Lifecycle của một request:
 * ```
 * checkMemoryCache() → load() → executeLoad()
 *     ↓                    ↓            ↓
 * Active/Memory      Priority Queue   Disk/Network
 * ```
 *
 * @param activeResources Quản lý bitmap đang active (đang được View reference)
 * @param memoryCache LRU cache trong RAM
 * @param diskCache Cache dữ liệu thô trên disk
 * @param fetcher Component fetch dữ liệu từ network
 * @param bitmapPool Pool để tái sử dụng bitmap, giảm memory allocation
 */

// edgecase: 100 D cùng vào priority queue -> đang bị gọi 100 lần
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

    /**
     * CoroutineScope riêng cho Engine với SupervisorJob để đảm bảo:
     * - Job con bị fail không làm crash toàn bộ scope
     * - Tất cả IO operations chạy trên Dispatchers.IO
     */
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Hàng đợi ưu tiên cao - xử lý bởi 2 workers song song */
    private val highPriorityQueue = Channel<PrioritizedRequest>(Channel.UNLIMITED)

    /** Hàng đợi ưu tiên thường - xử lý bởi 1 worker */
    private val normalPriorityQueue = Channel<PrioritizedRequest>(Channel.UNLIMITED)

    /** Hàng đợi ưu tiên thấp - xử lý bởi 1 worker */
    private val lowPriorityQueue = Channel<PrioritizedRequest>(Channel.UNLIMITED)

    init {
        // Khi một resource không còn được sử dụng (released), chuyển nó xuống Memory Cache
        // để có thể tái sử dụng mà không cần decode lại
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

    /**
     * Khởi động các worker threads để xử lý requests từ priority queues.
     *
     * Kiến trúc này cho phép:
     * - HIGH priority có 2 workers → xử lý nhanh hơn, không block
     * - NORMAL và LOW mỗi loại 1 worker → tiết kiệm tài nguyên
     */
    private fun startPriorityWorkers() {
        startWorkerForQueue(highPriorityQueue, workerCount = 2)
        startWorkerForQueue(normalPriorityQueue, workerCount = 1)
        startWorkerForQueue(lowPriorityQueue, workerCount = 1)
    }

    /**
     * Khởi tạo worker coroutines để lắng nghe và xử lý requests từ một queue.
     *
     * @param queue Hàng đợi chứa các requests cần xử lý
     * @param workerCount Số lượng workers chạy song song cho queue này
     */
    private fun startWorkerForQueue(queue: Channel<PrioritizedRequest>, workerCount: Int) {
        repeat(workerCount) {
            engineScope.launch {
                for (prioritizedReq in queue) {
                    if (!prioritizedReq.job.isCancelled) {
                        executeLoad(prioritizedReq.request, prioritizedReq.target)
                    }
                }
            }
        }
    }

    /**
     * Kiểm tra và lấy ảnh từ memory cache (Active Resources và Memory Cache).
     *
     * Đây là bước đầu tiên và nhanh nhất trong chuỗi cache lookup:
     * 1. **Active Resources**: Bitmap đang được View sử dụng → trả về ngay
     * 2. **Memory Cache**: Bitmap đã decode sẵn trong RAM → chuyển lên Active
     *
     * ## Kỹ thuật:
     * - Kiểm tra trạng thái `isRecycled` để tránh crash khi bitmap đã bị hủy
     * - Tự động dọn dẹp (remove) các bitmap không hợp lệ
     * - Log thời gian lookup để theo dõi hiệu năng
     * - Respect skipMemoryCache flag: nếu request skip memory cache thì bỏ qua cache lookup
     *
     * @param req Request chứa thông tin ảnh và transformations
     * @param target Target nhận kết quả (thường là ImageView)
     * @return `true` nếu tìm thấy trong cache, `false` nếu cần load từ disk/network
     */
    fun checkMemoryCache(req: Request, target: Target): Boolean {
        // Skip memory cache nếu request yêu cầu
        if (!req.useMemoryCache) {
            return false
        }

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

    /**
     * Bắt đầu quá trình load ảnh từ Disk Cache hoặc Network.
     *
     * Function này được gọi khi `checkMemoryCache()` trả về `false`.
     * Request sẽ được đưa vào priority queue tương ứng để xử lý.
     *
     * ## Flow:
     * ```
     * load() → Queue → executeLoad() → Disk/Network → Decode → Transform → Deliver
     * ```
     *
     * ## Priority System:
     * - **HIGH**: Ảnh trong viewport, cần hiển thị ngay (vd: ảnh đang visible)
     * - **NORMAL**: Ảnh trong viewport nhưng chưa urgent
     * - **LOW**: Prefetch, ảnh ngoài viewport
     *
     * @param req Request chứa URL và các config (resize, transformations, etc.)
     * @param target Target nhận kết quả và error callbacks
     * @param priority Mức độ ưu tiên của request
     * @return Job có thể dùng để cancel request
     */
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

    /**
     * Thực thi quá trình load ảnh từ Disk Cache hoặc Network.
     *
     * Function này được gọi bởi worker từ priority queue và thực hiện:
     *
     * ## Bước 1: Disk Cache
     * - Lấy raw bytes từ disk
     * - Decode thành Bitmap với size phù hợp
     * - Apply transformations (nếu có)
     * - Deliver về target
     *
     * ## Bước 2: Network (nếu Disk miss)
     * - Fetch raw bytes từ network
     * - Decode và transform
     * - Lưu raw bytes vào Disk Cache (nếu enabled)
     * - Deliver về target
     *
     * ## Error Handling:
     * - Disk decode fail → fallback sang Network
     * - Network fail → callback `onLoadFailed()` với retry option
     * - CancellationException → im lặng (request đã bị cancel)
     *
     * ## Performance Tracking:
     * Log chi tiết thời gian của từng bước: fetch, decode, transform, cache write
     *
     * @param req Request chứa thông tin ảnh
     * @param target Target để deliver kết quả
     */
    private suspend fun executeLoad(req: Request, target: Target) {
        val key = buildKey(req)
        val dataKey = buildDataKey(req)
        val startTime = System.currentTimeMillis()

        // 3️⃣ Disk Cache (raw bytes) → decode + transform lại
        if (req.useDiskCache) {
            diskCache.get(dataKey)?.let { bytes ->
                try {
                    val (bitmap, decodeTime, transformTime) = decodeAndTransform(bytes, req)
                    deliverResource(key, bitmap, target)

                    logImageLoad(
                        url = req.url,
                        source = LogSource.DISK_CACHE,
                        startTime = startTime,
                        decodeTimeMs = decodeTime,
                        transformTimeMs = transformTime,
                        transformCount = req.transformations.size,
                        fileSizeBytes = bytes.size.toLong()
                    )
                    return
                } catch (e: Exception) {
                    ImageLoaderLogger.e(
                        TAG,
                        "Disk cache decode failed for: ${req.url}",
                        e,
                        LogCategory.CACHE
                    )
                }
            }
        }

        // 4️⃣ Network fetch + decode + transform
        try {
            val fetchStart = System.currentTimeMillis()
            val result = fetcher.fetch(req.url)
            val fetchElapsed = System.currentTimeMillis() - fetchStart

            val (bitmap, decodeTime, transformTime) = decodeAndTransform(result.bytes, req)

            val cacheTime = measureTimeMillis {
                if (req.useDiskCache) diskCache.put(dataKey, result.bytes, result.contentType)
            }

            deliverResource(key, bitmap, target)

            logImageLoad(
                url = req.url,
                source = LogSource.NETWORK,
                startTime = startTime,
                fetchTimeMs = fetchElapsed,
                decodeTimeMs = decodeTime,
                transformTimeMs = transformTime,
                cacheWriteTimeMs = cacheTime,
                transformCount = req.transformations.size,
                fileSizeBytes = result.bytes.size.toLong()
            )
        } catch (e: Exception) {
            when (e) {
                is CancellationException -> {}
                else -> {
                    logImageLoad(
                        url = req.url,
                        source = LogSource.NETWORK,
                        startTime = startTime,
                        error = e.message ?: e.javaClass.simpleName
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

    /**
     * Decode raw bytes thành Bitmap và apply transformations.
     *
     * ## Decode Process:
     * - Sử dụng BitmapFactory với inSampleSize để giảm memory footprint
     * - Resize về kích thước yêu cầu nếu có `resizeWidth/Height`
     *
     * ## Transform Process (nếu có):
     * - Chạy trên Dispatchers.Default (CPU-intensive)
     * - Apply tuần tự các transformations (vd: crop, blur, round corners)
     * - Sử dụng BitmapPool để tái sử dụng bitmap cũ
     *
     * ## Kỹ thuật tối ưu:
     * - `fold()` để áp dụng chain of transformations
     * - `measureTimeMillis` để track performance
     * - Tất cả transformations dùng chung bitmap pool → giảm GC
     *
     * @param bytes Raw image data (JPEG, PNG, WebP, etc.)
     * @param req Request chứa resize dimensions và transformations
     * @return Triple<Bitmap, decodeTime, transformTime?>
     */
    private suspend fun decodeAndTransform(
        bytes: ByteArray,
        req: Request
    ): Triple<android.graphics.Bitmap, Long, Long?> {
        val decodeStart = System.currentTimeMillis()
        var bitmap = BitmapDecoder.decode(bytes, req.resizeWidth ?: 0, req.resizeHeight ?: 0)
        val decodeTime = System.currentTimeMillis() - decodeStart

        var transformTime: Long? = null
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
            transformTime = t
        }

        return Triple(bitmap, decodeTime, transformTime)
    }

    /**
     * Wrap bitmap thành EngineResource và gửi đến Target.
     *
     * ## EngineResource:
     * - Wrapper quản lý lifecycle của bitmap
     * - Tự động track số lượng acquire/release
     * - Khi released → chuyển xuống Memory Cache
     *
     * ## Thread Safety:
     * - Đảm bảo callback `onResourceReady()` chạy trên Main thread
     * - Tránh ANR và thread-related crashes
     *
     * @param key Cache key của resource
     * @param bitmap Bitmap đã decode và transform xong
     * @param target Target nhận resource (thường là ImageView)
     */
    private suspend fun deliverResource(
        key: String,
        bitmap: android.graphics.Bitmap,
        target: Target
    ) {
        val res = EngineResource(key, bitmap, activeResources)
        activeResources.put(key, res)
        withContext(Dispatchers.Main) {
            target.onResourceReady(res)
        }
    }

    /**
     * Helper function để log chi tiết quá trình load ảnh.
     *
     * ## Metrics được track:
     * - **fetchTimeMs**: Thời gian download từ network
     * - **decodeTimeMs**: Thời gian decode bytes → Bitmap
     * - **transformTimeMs**: Thời gian apply transformations
     * - **cacheWriteTimeMs**: Thời gian ghi vào Disk Cache
     * - **totalTimeMs**: Tổng thời gian từ đầu đến khi deliver
     *
     * ## Use cases:
     * - Performance monitoring và debugging
     * - Phát hiện bottlenecks (decode chậm, network chậm, etc.)
     * - Analytics để tối ưu cache strategies
     *
     * @param url URL của ảnh
     * @param source Nguồn của ảnh (NETWORK, DISK_CACHE, MEMORY_CACHE, ACTIVE_CACHE)
     * @param startTime Timestamp bắt đầu load
     * @param error Message lỗi nếu load fail
     */
    private fun logImageLoad(
        url: String,
        source: LogSource,
        startTime: Long,
        fetchTimeMs: Long? = null,
        decodeTimeMs: Long? = null,
        transformTimeMs: Long? = null,
        cacheWriteTimeMs: Long? = null,
        transformCount: Int? = null,
        error: String? = null,
        fileSizeBytes: Long? = null
    ) {
        ImageLoaderLogger.log(
            ImageLoadLog(
                url = url,
                source = source,
                fetchTimeMs = fetchTimeMs,
                decodeTimeMs = decodeTimeMs,
                transformTimeMs = transformTimeMs,
                cacheWriteTimeMs = cacheWriteTimeMs,
                totalTimeMs = System.currentTimeMillis() - startTime,
                transformCount = transformCount ?: 0,
                error = error,
                fileSizeBytes = fileSizeBytes
            )
        )
    }

    /**
     * Tạo cache key duy nhất cho mỗi request.
     *
     * ## Composition:
     * - **URL**: Base identifier
     * - **resize**: Kích thước resize (nếu có)
     * - **out**: Output dimensions (nếu có)
     * - **transforms**: Danh sách transformations được apply
     *
     * ## Ví dụ:
     * ```
     * URL: "https://example.com/image.jpg"
     * resize: 500x500
     * transforms: [CenterCrop, RoundedCorners]
     * → Key: MD5("https://example.com/image.jpg#resize=500x500#out=500x500#transforms=center_crop;rounded_corners")
     * ```
     *
     * ## Kỹ thuật:
     * - Sử dụng MD5 để tạo hash nhỏ gọn (32 chars thay vì URL dài)
     * - Đảm bảo các request giống nhau → cùng key → cache hit
     * - Request khác transformations → khác key → cache riêng
     *
     * @param req Request cần tạo key
     * @return MD5 hash của request parameters
     */
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
        }
        return rawKey.md5()
    }

    /**
     * Tạo data key cho Disk Cache (chỉ dựa trên URL và resize).
     *
     * ## Khác biệt với buildKey():
     * - **buildKey()**: Bao gồm cả transformations → dùng cho Memory Cache
     * - **buildDataKey()**: Không có transformations → dùng cho Disk Cache
     *
     * ## Lý do:
     * - Disk Cache lưu raw bytes chưa transform
     * - Nhiều transformations khác nhau có thể dùng chung raw bytes
     * - Tiết kiệm disk space (không lưu duplicate data)
     *
     * ## Ví dụ:
     * ```
     * Request A: URL + CenterCrop
     * Request B: URL + RoundedCorners
     * → Cùng dataKey → chỉ download 1 lần, transform 2 cách
     * ```
     *
     * @param req Request cần tạo data key
     * @return MD5 hash của URL và resize params
     */
    fun buildDataKey(req: Request): String {
        val rawKey = buildString {
            append(req.url)
            if (req.resizeWidth != null && req.resizeHeight != null)
                append("#resize=${req.resizeWidth}x${req.resizeHeight}")
        }
        return rawKey.md5()
    }
}

/**
 * Extension function để tạo MD5 hash từ String.
 *
 * ## Use cases:
 * - Tạo cache key ngắn gọn từ URL dài
 * - Đảm bảo key có độ dài cố định (32 chars)
 * - Tránh ký tự đặc biệt trong filename
 *
 * @return Hex string 32 ký tự (128-bit hash)
 */
private fun String.md5(): String {
    val digest = MessageDigest.getInstance("MD5")
    val bytes = digest.digest(toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
