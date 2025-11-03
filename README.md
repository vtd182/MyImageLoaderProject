# Image Loader - ZTF2025
---

## Mục Lục

1. [Giới Thiệu](#1-giới-thiệu)
2. [Tổng Quan Tính Năng](#2-tổng-quan-tính-năng)
3. [Kiến Trúc Hệ Thống](#3-kiến-trúc-hệ-thống)
4. [Chi Tiết ImageLoader Module](#4-chi-tiết-imageloader-module)
5. [Chi Tiết App Module](#5-chi-tiết-app-module)
6. [Hướng Dẫn Sử Dụng](#6-hướng-dẫn-sử-dụng)
7. [Testing & Quality](#7-testing--quality)
8. [Performance Optimization](#8-performance-optimization)
9. [Dependencies & Requirements](#9-dependencies--requirements)

---

## 1. Giới Thiệu

### 1.1 Tổng Quan

Image Loader là một thư viện tải và cache ảnh được phát triển cho nền tảng Android, lấy cảm hứng từ
các thư viện nổi tiếng như **Glide** và **Picasso** nhưng được xây dựng từ đầu với mục tiêu:

- Tối ưu hóa hiệu năng
- Dễ dàng debug và monitoring
- Kiến trúc rõ ràng, dễ maintain

### 1.2 Mục Tiêu Dự Án

#### Về Kỹ Thuật:

- Xây dựng **hệ thống cache đa tầng** hiệu quả (Active → Memory → Disk → Network)
- **Quản lý bộ nhớ thông minh**, tự động tính toán và tránh OOM (Out of Memory)
- Hỗ trợ **xử lý ảnh song song** với Kotlin Coroutines
- **Priority queue system** cho request handling
- **Reference counting** cho active resources

#### Về Monitoring:

- Tích hợp **logging system** với UI viewer
- Track **performance metrics** real-time
- **Statistics dashboard** cho cache hits/misses
- Debug tools cho developers

#### Về Architecture:

- Áp dụng **Clean Architecture** trong sample app
- **Separation of concerns** rõ ràng
- **Testability** cao với unit tests
- **Dependency Injection** pattern

### 1.3 Use Cases Chính

1. **Social Media Apps**: Tải và hiển thị feed ảnh với infinite scroll
2. **E-commerce Apps**: Product images với nhiều kích thước khác nhau
3. **Gallery Apps**: Thumbnails và full-size images
4. **Offline Apps**: Cache ảnh để xem offline
5. **Dashboard Apps**: Monitoring và debugging image loading

---

## 2. Tổng Quan Tính Năng

### 2.1 Multi-level Caching System

Hệ thống cache 4 tầng được thiết kế theo nguyên tắc **fast-to-slow access**:

```
Request → Active Resources → Memory Cache → Disk Cache → Network
           (0.1ms)            (1-2ms)        (10-50ms)    (100-500ms)
```

#### **Tầng 1: Active Resources**

- **Mục đích**: Cache ảnh đang được hiển thị trên UI
- **Cơ chế**: Reference counting (acquire/release)
- **Lợi ích**:
    - Tránh decode lại ảnh đang hiển thị
    - Ngăn bitmap bị recycle khi còn đang dùng
    - Access time ~0.1ms
- **Implementation**: `HashMap<String, EngineResource>`

#### **Tầng 2: Memory Cache (LRU)**

- **Mục đích**: Cache ảnh đã được hiển thị gần đây
- **Cơ chế**: LRU (Least Recently Used) eviction
- **Lợi ích**:
    - Fast access ~1-2ms
    - Tự động evict ảnh cũ khi đầy
    - Tích hợp bitmap pooling
- **Kích thước**: Auto-calculated dựa trên device specs
- **Implementation**: `LruCache<String, Bitmap>`

#### **Tầng 3: Disk Cache**

- **Mục đích**: Persistent cache cho offline access
- **Cơ chế**: File-based cache với MD5 key
- **Lợi ích**:
    - Không mất cache khi restart app
    - Tiết kiệm bandwidth
    - Access time ~10-50ms
- **Location**: `context.externalCacheDir/image_cache/`
- **Implementation**: File I/O với coroutines

#### **Tầng 4: Network Fetcher**

- **Mục đích**: Tải ảnh từ server
- **Cơ chế**: HTTP request với `HttpURLConnection` thông qua `HttpFetcher`
- **Lợi ích**:
    - Retry với backoff (mặc định 2 lần)
    - Timeout cấu hình được (5s connect/read)
    - Tích hợp logging qua `ImageLoaderLogger`
- **Latency**: ~100-500ms tùy network

### 2.2 Smart Memory Management

#### **Automatic Size Calculation**

```kotlin
// MemorySizeCalculator.kt
fun calculate(context: Context, useBitmapPool: Boolean): MemorySizes {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryClass = activityManager.memoryClass // MB
    val isLowRamDevice = activityManager.isLowRamDevice

    // Screen size calculation
    val displayMetrics = context.resources.displayMetrics
    val screenWidth = displayMetrics.widthPixels
    val screenHeight = displayMetrics.heightPixels
    val bytesPerPixel = 4 // ARGB_8888

    // Base calculation
    val screenBytes = screenWidth * screenHeight * bytesPerPixel
    val multiplier = if (isLowRamDevice) 2.0 else 4.0

    // Memory cache: 2-4 screens worth
    val memoryCacheSize = (screenBytes * multiplier).toInt()

    // Bitmap pool: 2x memory cache (if enabled)
    val bitmapPoolSize = if (useBitmapPool) memoryCacheSize * 2 else 0

    return MemorySizes(memoryCacheSize, bitmapPoolSize)
}
```

**Các yếu tố ảnh hưởng:**

- **Device RAM**: Low RAM devices (< 1GB) → cache nhỏ hơn
- **Screen resolution**: 1080p vs 4K → cache lớn hơn tương ứng
- **Bitmap format**: ARGB_8888 (4 bytes/pixel) vs RGB_565 (2 bytes/pixel)

#### **Bitmap Pooling**

Bitmap Pool là một kỹ thuật **reuse memory** để giảm GC (Garbage Collection):

```kotlin
// LruBitmapPool.kt
class LruBitmapPool(maxSize: Long) : BitmapPool {
    private val map = LinkedHashMap<Key, MutableList<Bitmap>>()

    data class Key(val width: Int, val height: Int, val config: Bitmap.Config)

    override fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap? {
        // 1. Tìm exact match
        val key = Key(width, height, config)
        val bitmap = map[key]?.removeFirstOrNull()

        // 2. Fallback: tìm bitmap lớn hơn và reconfigure
        if (bitmap == null) {
            for ((k, list) in map.entries) {
                if (k.config == config && k.width >= width && k.height >= height) {
                    val candidate = list.removeFirstOrNull()
                    candidate?.reconfigure(width, height, config)
                    return candidate
                }
            }
        }

        return bitmap
    }

    override fun put(bitmap: Bitmap) {
        if (!bitmap.isMutable || bitmap.isRecycled) return
        val key = Key(bitmap.width, bitmap.height, bitmap.config)
        map.getOrPut(key) { mutableListOf() }.add(bitmap)

        trimToSize(maxSize) // Evict old bitmaps if needed
    }
}
```

**Benefits:**

- Giảm memory allocations → ít GC hơn
- Giảm memory churn
- Tăng hiệu năng khi decode nhiều ảnh liên tục
- Trade-off: Tốn thêm RAM để giữ pool

**Khi nào dùng:**

- RecyclerView với nhiều ảnh cùng size
- Gallery với thumbnails
- Ảnh có size khác nhau nhiều
- Low RAM devices

### 2.3 Request Priority System

Engine sử dụng **3 priority queues** để xử lý requests:

```kotlin
class Engine {
    private val highPriorityQueue = Channel<PrioritizedRequest>(Channel.UNLIMITED)
    private val normalPriorityQueue = Channel<PrioritizedRequest>(Channel.UNLIMITED)
    private val lowPriorityQueue = Channel<PrioritizedRequest>(Channel.UNLIMITED)

    init {
        // 2 workers cho HIGH priority
        repeat(2) {
            engineScope.launch {
                for (request in highPriorityQueue) {
                    executeLoad(request)
                }
            }
        }

        // 1 worker cho NORMAL priority (với fast scroll delay)
        engineScope.launch {
            for (request in normalPriorityQueue) {
                if (isFastScrolling) delay(50)
                executeLoad(request)
            }
        }

        // 1 worker cho LOW priority (preloading)
        engineScope.launch {
            for (request in lowPriorityQueue) {
                if (isFastScrolling) delay(100)
                executeLoad(request)
            }
        }
    }
}
```

**Priority Rules trong PhotoAdapter:**

```kotlin
override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val priority = when {
        position < 6 -> RequestPriority.HIGH      // First screen
        position < 20 -> RequestPriority.NORMAL   // Near viewport
        else -> RequestPriority.LOW               // Preloading
    }

    ImageLoader.with(context)
        .load(url)
        .priority(priority)
        .into(imageView)
}
```

**Fast Scrolling Optimization:**

- Detect fast scroll → tạm dừng NORMAL và LOW requests
- Chỉ xử lý HIGH priority (visible items)
- Auto-resume sau 300ms không scroll

### 2.4 Lifecycle Awareness

#### **Request Tracking System**

```kotlin
object RequestManager {
    fun track(view: ImageView, job: Job?, onResume: (() -> Unit)?)
    fun clear(view: ImageView)
    fun pauseAll()
    fun resumeVisibleOnly(visible: List<ImageView>)

    // Nội bộ:
    // - running/pending map bằng ConcurrentHashMap
    // - Khi resumeVisibleOnly: debounce 150ms, ưu tiên view đang hiển thị,
    //   delay adaptive dựa trên FPS đo được qua Choreographer.
}
```

**Integration trong Activity:**

```kotlin
class MyActivity : AppCompatActivity() {
    private val visibleImages = mutableListOf<ImageView>()

    private fun setupRecyclerView() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_SETTLING) {
                    RequestManager.pauseAll()
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    visibleImages.clear()
                    repeat(rv.childCount) { index ->
                        rv.getChildAt(index)
                            ?.findViewById<ImageView>(R.id.imgPhoto)
                            ?.takeIf { it.isVisible }
                            ?.let { visibleImages.add(it) }
                    }
                    RequestManager.resumeVisibleOnly(visibleImages)
                }
            }
        })
    }
}
```

**Benefits:**

- Điều tiết request theo FPS hiện tại (adaptive delay)
- Chỉ resume các ảnh đang hiển thị sau khi người dùng dừng scroll
- Tránh memory leaks nhờ `ConcurrentHashMap` + `ImageView.clear()`
- Cancel old requests khi view reuse

### 2.5 Image Transformations

#### **CenterCropRoundedCorners Implementation**

```kotlin
class CenterCropRoundedCorners(val radius: Float) : Transformation {
    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        // 1. Create output bitmap (có thể lấy từ pool)
        val result = pool.get(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            ?: Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(result)

        // 2. Calculate scale để cover toàn bộ output
        val scale = max(
            outWidth.toFloat() / toTransform.width,
            outHeight.toFloat() / toTransform.height
        )

        // 3. Center align
        val scaledWidth = toTransform.width * scale
        val scaledHeight = toTransform.height * scale
        val dx = (outWidth - scaledWidth) / 2f
        val dy = (outHeight - scaledHeight) / 2f

        // 4. Apply BitmapShader với matrix transform
        val shader = BitmapShader(toTransform, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        shader.setLocalMatrix(matrix)

        // 5. Draw rounded rect với shader
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            isFilterBitmap = true
        }

        val rect = RectF(0f, 0f, outWidth.toFloat(), outHeight.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)

        // 6. Recycle original bitmap nếu không cần
        if (toTransform != result) {
            pool.put(toTransform)
        }

        return result
    }

    override fun key(): String = "CenterCropRoundedCorners($radius)"
}
```

**Key Points:**

- **Scale calculation**: Đảm bảo cover toàn bộ output (như CSS `background-size: cover`)
- **Bitmap pooling**: Reuse bitmap từ pool để giảm allocations
- **Cache key**: Bao gồm transformation params để cache riêng biệt

#### **Transformation Pipeline**

Transformations được apply theo thứ tự:

```kotlin
bitmap = transformations.fold(bitmap) { bmp, transform ->
    transform.transform(pool, bmp, outWidth, outHeight)
}
```

**Example:**

```kotlin
ImageLoader.with(context)
    .load(url)
    .resize(800, 800)
    .transform(
        GrayscaleTransformation(),
        CenterCropRoundedCorners(32f),
        BlurTransformation(10f)
    )
    .into(imageView)
```

### 2.6 Real-time Logging System

#### **ImageLoaderLogger Architecture**

```kotlin
object ImageLoaderLogger {
    private val logs = ConcurrentLinkedQueue<LogEntry>()
    private val listeners = mutableListOf<(LogEntry) -> Unit>()

    var saveToActivity = true
    var jsonPhotoCount = 0
    var jsonCurrentPage = 0

    fun log(log: ImageLoadLog) {
        if (saveToActivity) {
            logs.add(log)

            // Limit logs size
            while (logs.size > MAX_LOGS) {
                logs.poll()
            }

            // Notify listeners (UI update)
            synchronized(listeners) {
                listeners.forEach { it(log) }
            }
        }

        // Always log to Logcat
        Log.d(TAG, log.toLogcatString())
    }
}
```

#### **Log Entry Types**

**1. ImageLoadLog:**

```kotlin
data class ImageLoadLog(
    val url: String,
    val source: LogSource,              // MEMORY, DISK, NETWORK
    val fetchTimeMs: Long? = null,      // Network fetch time
    val decodeTimeMs: Long? = null,     // Bitmap decode time
    val transformTimeMs: Long? = null,  // Transformation time
    val cacheWriteTimeMs: Long? = null, // Disk write time
    val totalTimeMs: Long,              // Total request time
    val transformCount: Int = 0,
    val isFastScrolling: Boolean = false,
    val error: String? = null
)
```

**2. MessageLog:**

```kotlin
data class MessageLog(
    val level: LogLevel,        // VERBOSE, DEBUG, INFO, WARNING, ERROR
    val category: LogCategory,  // GENERAL, CACHE, NETWORK, IMAGE, DECODE, TRANSFORM
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)
```

#### **LogViewer UI**

```kotlin
class LogViewerActivity : AppCompatActivity() {
    private lateinit var adapter: LogAdapter
    private val selectedCategories = mutableSetOf<LogCategory>()

    private val logListener: (LogEntry) -> Unit = { log ->
        runOnUiThread {
            adapter.addLog(log, selectedCategories)
            recyclerView.smoothScrollToPosition(0) // Auto scroll to top
            updateQuickStats()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load existing logs
        adapter.submitLogs(
            ImageLoaderLogger.getAllLogs().reversed(),
            selectedCategories
        )

        // Listen for new logs
        ImageLoaderLogger.addListener(logListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        ImageLoaderLogger.removeListener(logListener)
    }
}
```

#### **Statistics Dashboard**

```kotlin
data class LogStats(
    val totalLogs: Int,
    val totalImageRequests: Int,
    val imageErrors: Int,
    val messageErrors: Int,
    val jsonPhotoCount: Int,
    val jsonCurrentPage: Int,

    // Active Cache Stats
    val activeCacheCount: Int,
    val activeCacheAvgTime: Double,

    // Memory Cache Stats
    val memoryCacheCount: Int,
    val memoryCacheAvgTime: Double,

    // Disk Cache Stats
    val diskCacheCount: Int,
    val diskCacheAvgTime: Double,
    val diskCacheAvgDecode: Double,
    val diskCacheAvgTransform: Double,

    // Network Stats
    val networkCount: Int,
    val networkAvgTime: Double,
    val networkAvgFetch: Double,
    val networkAvgDecode: Double,
    val networkAvgTransform: Double
)
```

**Cache Hit Rate Calculation:**

```kotlin
val totalRequests = activeCacheCount + memoryCacheCount + diskCacheCount + networkCount
val memoryHitRate = ((activeCacheCount + memoryCacheCount).toFloat() / totalRequests * 100)
val diskHitRate = (diskCacheCount.toFloat() / totalRequests * 100)
```

**Performance Insights:**

- Memory hit rate > 80% → Cache size đủ lớn
- Disk hit rate > 50% → User quay lại xem ảnh cũ nhiều
- Network average > 500ms → Network chậm
- Decode time > 100ms → Ảnh quá lớn, cần resize

### 2.7 JSON Backup System

#### **JsonBackupManager Implementation**

```kotlin
class JsonBackupManager(private val context: Context) {
    private val gson = Gson()

    private val backupFile: File
        get() {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            if (!cacheDir.exists()) cacheDir.mkdirs()
            return File(cacheDir, "photo_backup.json")
        }

    suspend fun saveBackup(photos: List<UnsplashPhoto>, currentPage: Int) {
        withContext(Dispatchers.IO) {
            try {
                val backup = PhotoBackup(
                    photos = photos,
                    currentPage = currentPage,
                    timestamp = System.currentTimeMillis()
                )
                val json = gson.toJson(backup)
                backupFile.writeText(json)

                // Sync với Logger stats
                ImageLoaderLogger.jsonPhotoCount = photos.size
                ImageLoaderLogger.jsonCurrentPage = currentPage

                ImageLoaderLogger.i(
                    TAG,
                    "JSON backup saved: ${photos.size} photos, page $currentPage",
                    LogCategory.CACHE
                )
            } catch (e: Exception) {
                ImageLoaderLogger.e(TAG, "Failed to save JSON backup", e)
            }
        }
    }

    suspend fun loadBackup(): PhotoBackup? {
        return withContext(Dispatchers.IO) {
            try {
                if (!backupFile.exists()) return@withContext null

                val json = backupFile.readText()
                val backup = gson.fromJson<PhotoBackup>(json, PhotoBackup::class.java)

                // Check expiration (24 hours)
                val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                if (backup.timestamp < oneDayAgo) {
                    ImageLoaderLogger.i(TAG, "JSON backup expired, deleting")
                    backupFile.delete()
                    return@withContext null
                }

                // Sync với Logger stats
                ImageLoaderLogger.jsonPhotoCount = backup.photos.size
                ImageLoaderLogger.jsonCurrentPage = backup.currentPage

                ImageLoaderLogger.i(
                    TAG,
                    "JSON backup loaded: ${backup.photos.size} photos, page ${backup.currentPage}"
                )

                backup
            } catch (e: Exception) {
                ImageLoaderLogger.e(TAG, "Failed to load JSON backup", e)
                backupFile.delete()
                null
            }
        }
    }
}
```

**Data Model:**

```kotlin
data class PhotoBackup(
    val photos: List<UnsplashPhoto>,
    val currentPage: Int,
    val timestamp: Long
)
```

**Integration trong HomeViewModel:**

```kotlin
fun loadPhotos() {
    viewModelScope.launch {
        try {
            // 1. Try loading backup first
            val backup = backupManager.loadBackup()
            if (backup != null && backup.photos.isNotEmpty()) {
                currentPage = backup.currentPage
                _uiState.value = HomeUiState.Data(backup.photos)
                return@launch
            }

            // 2. Fetch from network
            val photos = getRandomPhotosUseCase(perPage, 1)
            _uiState.value = HomeUiState.Data(photos)

            // 3. Save backup
            backupManager.saveBackup(photos, currentPage)
        } catch (e: Exception) {
            handleError(e)
        }
    }
}
```

**Benefits:**

- **Offline support**: User vẫn xem được ảnh đã load trước đó
- **Fast startup**: Không cần đợi network request
- **Data persistence**: Giữ lại state khi app bị kill
- **Bandwidth saving**: Giảm API calls không cần thiết

### 2.8 Network Monitoring & Auto-Retry

#### **NetworkMonitor Implementation**

```kotlin
class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
```

#### **Error Handling System**

```kotlin
sealed class AppError {
    data class NetworkError(val message: String) : AppError()
    data class RateLimitError(val retryAfter: Long = 60_000) : AppError()
    data class ServerError(val code: Int, val message: String) : AppError()
    data class UnknownError(val message: String) : AppError()
}

object ErrorHandler {
    fun handleError(throwable: Throwable): AppError = when (throwable) {
        is UnknownHostException,
        is SocketTimeoutException -> AppError.NetworkError(
            "Không thể kết nối đến server. Vui lòng kiểm tra kết nối Internet."
        )

        is IOException -> AppError.NetworkError("Lỗi kết nối mạng. Vui lòng thử lại.")
        else -> {
            val message = throwable.message ?: "Đã xảy ra lỗi không xác định"
            when {
                message.contains("403") || message.contains("rate limit", ignoreCase = true) ->
                    AppError.RateLimitError()

                message.contains("500") || message.contains("502") || message.contains("503") ->
                    AppError.ServerError(500, "Server đang gặp sự cố. Vui lòng thử lại sau.")

                else -> AppError.UnknownError(message)
            }
        }
    }

    fun getErrorMessage(error: AppError): String = when (error) {
        is AppError.NetworkError -> error.message
        is AppError.RateLimitError -> "Đã vượt giới hạn request. Vui lòng đợi ${error.retryAfter / 1000}s."
        is AppError.ServerError -> error.message
        is AppError.UnknownError -> error.message
    }

    fun shouldRetry(error: AppError): Boolean = when (error) {
        is AppError.NetworkError -> true
        is AppError.RateLimitError -> true
        is AppError.ServerError -> error.code == 503
        is AppError.UnknownError -> false
    }

    fun getRetryDelay(error: AppError): Long = when (error) {
        is AppError.NetworkError -> 3000L
        is AppError.RateLimitError -> error.retryAfter
        is AppError.ServerError -> 5000L
        is AppError.UnknownError -> 0L
    }
}
```

#### **Auto-Retry trong ViewModel**

```kotlin
private fun scheduleRetry(error: AppError) {
    retryJob?.cancel()
    retryJob = viewModelScope.launch {
        val delay = ErrorHandler.getRetryDelay(error)
        Log.d(TAG, "Scheduling retry in ${delay}ms")
        delay(delay)

        // Check network before retry
        if (!networkMonitor.isNetworkAvailable()) {
            Log.d(TAG, "Still offline, skip retry")
            return@launch
        }

        Log.d(TAG, "Auto retrying...")
        loadPhotos()
    }
}
```

---

## 3. Kiến Trúc Hệ Thống

### 3.1 Cấu Trúc Dự Án

```
image-loader/
│
├── ImageLoader/                    # Library module (Android Library)
│   ├── src/main/java/com/example/imageloader/
│   │   ├── cache/
│   │   │   ├── ActiveResources.kt      # Reference-counted cache
│   │   │   ├── MemoryCache.kt          # LRU memory cache
│   │   │   └── DiskCache.kt            # File-based disk cache
│   │   │
│   │   ├── core/
│   │   │   ├── ImageLoader.kt          # Singleton entry point
│   │   │   ├── Engine.kt               # Core loading engine
│   │   │   ├── RequestBuilder.kt       # Fluent API builder
│   │   │   ├── Request.kt              # Request data class
│   │   │   ├── RequestManager.kt       # Lifecycle management
│   │   │   ├── EngineResource.kt       # Resource wrapper
│   │   │   ├── LruBitmapPool.kt        # Bitmap pooling
│   │   │   ├── MemorySizeCalculator.kt # Memory config
│   │   │   └── abstract/
│   │   │       ├── BitmapPool.kt
│   │   │       └── ResourceListener.kt
│   │   │
│   │   ├── decode/
│   │   │   └── BitmapDecoder.kt        # Bitmap decoding logic
│   │   │
│   │   ├── fetcher/
│   │   │   ├── DataFetcher.kt          # Interface
│   │   │   ├── HttpFetcher.kt          # HttpURLConnection implementation
│   │   │   ├── ConnectionFactory.kt     # Tạo HttpURLConnection (dễ mock)
│   │   │   └── HttpResult.kt           # Result wrapper
│   │   │
│   │   ├── transformation/
│   │   │   ├── Transformation.kt       # Interface
│   │   │   ├── BaseTransformation.kt   # Base class
│   │   │   └── CenterCropRoundedCorners.kt
│   │   │
│   │   ├── target/
│   │   │   ├── Target.kt               # Interface
│   │   │   └── ImageViewTarget.kt      # ImageView implementation
│   │   │
│   │   ├── logger/
│   │   │   ├── ImageLoaderLogger.kt    # Logger singleton
│   │   │   ├── LogEntry.kt             # Log data models
│   │   │   └── LogViewer.kt            # Viewer launcher
│   │   │
│   │   ├── ui/
│   │   │   ├── LogViewerActivity.kt    # Logger UI
│   │   │   ├── LogViewerPresentation.kt # UI logic
│   │   │   └── LogAdapter.kt           # RecyclerView adapter
│   │   │
│   │   └── drawable/
│   │       └── ShimmerDrawable.kt      # Loading animation
│   │
│   └── src/test/java/                  # Unit tests
│       └── com/example/imageloader/
│           ├── core/
│           │   ├── EngineTest.kt
│           │   └── ImageLoaderTest.kt
│           ├── cache/
│           │   └── MemoryCacheTest.kt
│           ├── logger/
│           │   └── ImageLoaderLoggerTest.kt
│           └── ui/
│               ├── LogAdapterTest.kt
│               └── LogViewerPresentationTest.kt
│
└── app/                                # Sample application
    ├── src/main/java/com/example/myimageloaderproject/
    │   ├── core/
    │   │   ├── base/
    │   │   │   └── BaseApiService.kt   # Base class cho API services
    │   │   ├── constants/
    │   │   │   └── Constants.kt        # API keys & base URL
    │   │   ├── customView/
    │   │   │   └── FPSOverlay.kt       # FPS overlay widget
    │   │   ├── error/
    │   │   │   └── ErrorHandler.kt     # AppError sealed class + handling logic
    │   │   ├── helpers/
    │   │   │   └── Helpers.kt          # JSON parsing helper
    │   │   └── network/
    │   │       └── NetworkMonitor.kt   # Network status
    │   │
    │   ├── di/
    │   │   └── Injector.kt             # Manual DI (HttpClient injection)
    │   │
    │   ├── network/
    │   │   └── HttpClient.kt           # Custom HTTP client (không dùng Retrofit)
    │   │
    │   ├── ui/
    │   │   └── theme/                  # Compose theme scaffolding
    │   │       ├── Color.kt
    │   │       ├── Theme.kt
    │   │       └── Type.kt
    │   │
    │   ├── modules/
    │   │   ├── home/
    │   │   │   ├── data/
    │   │   │   │   ├── remote/
    │   │   │   │   │   └── UnsplashApi.kt      # Interface + Implementation
    │   │   │   │   ├── repository/
    │   │   │   │   │   └── PhotoRepositoryImpl.kt
    │   │   │   │   └── cache/
    │   │   │   │       ├── JsonBackupManager.kt    # JSON persistence
    │   │   │   │       └── PhotoPreloader.kt       # Preload logic
    │   │   │   │
    │   │   │   ├── domain/
    │   │   │   │   ├── model/
    │   │   │   │   │   └── UnsplashPhoto.kt    # @Serializable model
    │   │   │   │   ├── repository/
    │   │   │   │   │   └── PhotoRepository.kt
    │   │   │   │   └── usecase/
    │   │   │   │       └── GetRandomPhotosUseCase.kt
    │   │   │   │
    │   │   │   └── presentation/
    │   │   │       ├── HomeActivity.kt
    │   │   │       ├── HomeViewModel.kt
    │   │   │       └── adapter/
    │   │   │           └── PhotoAdapter.kt
    │   │   │
    │   │   └── splash/
    │   │       └── SplashActivity.kt
    │
    └── src/main/res/
        ├── layout/
        │   ├── activity_home.xml
        │   ├── activity_splash.xml
        │   ├── bottom_sheet_download.xml
        │   ├── bottom_sheet_settings.xml
        │   ├── item_footer.xml
        │   ├── item_photo.xml
        │   └── view_network_status_bar.xml
        └── values/
            ├── colors.xml
            ├── dimens.xml
            ├── strings.xml
            └── themes.xml
```

### 3.2 Luồng Xử Lý Request

#### **Request Flow Diagram**

#### **Timing Analysis**

**Scenario 1: Active Cache Hit** (Best case)

```
Total time: ~0.1ms
- Active cache lookup: 0.1ms
```

**Scenario 2: Memory Cache Hit**

```
Total time: ~1-2ms
- Active cache miss: 0.1ms
- Memory cache lookup: 1ms
- Put to active: 0.5ms
```

**Scenario 3: Disk Cache Hit**

```
Total time: ~50-100ms
- Cache checks: 1ms
- Disk read: 10-20ms
- Bitmap decode: 30-50ms
- Transformations: 10-20ms
- Memory save: 1ms
```

**Scenario 4: Network Fetch**

```
Total time: ~500-1000ms
- Cache checks: 1ms
- HTTP fetch: 200-500ms
- Bitmap decode: 50-100ms
- Transformations: 20-50ms
- Disk save: 50-100ms
- Memory save: 1ms
```

### 3.3 Chiến Lược Cache

#### **Cache Key Generation**

```kotlin
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
```

**Example Keys:**

```
URL: https://example.com/photo.jpg
Resize: 800x800
Transform: CenterCropRoundedCorners(32)

Key = MD5("https://example.com/photo.jpg#resize=800x800#transforms=CenterCropRoundedCorners(32)#useMemory=true#useDisk=true")
    = "a3f7c2b8d9e1f0c4b5a6e7d8c9f0e1d2"
```

**Benefit:**

- Ảnh cùng URL nhưng khác size/transform → cache riêng biệt
- Tránh cache hit sai

#### **Cache Invalidation**

**Memory Cache Eviction:**

```kotlin
// LruCache tự động evict theo LRU
override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap) {
    if (evicted && oldValue.isMutable && !oldValue.isRecycled) {
        bitmapPool?.put(oldValue) // Return to pool
    }
}
```

**Disk Cache Cleanup:**

```kotlin
// Manual cleanup
fun clearDiskCache() {
    val cacheDir = File(context.externalCacheDir, "image_cache")
    cacheDir.deleteRecursively()
}

// Size-based eviction (TODO)
fun trimDiskCache(maxSizeBytes: Long) {
    // Implement LRU for disk cache
}
```

**Active Resources Cleanup:**

```kotlin
// Auto cleanup khi refCount = 0
fun release() {
    refCount--
    if (refCount == 0) {
        listener.onResourceReleased(key, this)
        // Move to Memory Cache
    }
}
```

---

## 4. Chi Tiết ImageLoader Module

### 4.1 Core Components

#### **4.1.1 ImageLoader (Singleton)**

Entry point của library, quản lý lifecycle của các components chính.

```kotlin
class ImageLoader private constructor(context: Context, useBitmapPool: Boolean) {
    // Memory management
    private val sizes = MemorySizeCalculator.calculate(context, useBitmapPool)
    private val bitmapPool = LruBitmapPool(sizes.bitmapPoolSize.toLong())
    private val memoryCache = MemoryCache(sizes.memoryCacheSize, bitmapPool)

    // Cache layers
    private val diskCache = DiskCache(context)
    private val activeResources = ActiveResources()

    // Network
    private val fetcher = HttpFetcher()

    // Core engine
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
                INSTANCE ?: ImageLoader(context.applicationContext, useBitmapPool)
                    .also { INSTANCE = it }
            }
        }

        fun with(context: Context, useBitmapPool: Boolean = false): RequestBuilder {
            return RequestBuilder(getInstance(context, useBitmapPool).engine)
        }
    }
}
```

**Key Responsibilities:**

- **Singleton pattern**: Đảm bảo chỉ có 1 instance
- **Dependency injection**: Inject dependencies vào Engine
- **Configuration**: Setup bitmap pool, memory cache sizes
- **Thread-safe initialization**: Double-checked locking

#### **4.1.2 RequestBuilder (Fluent API)**

Builder pattern để tạo requests với cú pháp dễ đọc.

```kotlin
class RequestBuilder(private val engine: Engine) {
    private var url: String? = null
    private var resizeWidth: Int? = null
    private var resizeHeight: Int? = null
    private var useMemoryCache: Boolean = true
    private var useDiskCache: Boolean = true
    private var placeholderRes: Int? = null
    private var placeholderColor: Int? = null
    private var errorRes: Int? = null
    private var outHeight: Int? = null
    private var outWidth: Int? = null
    private var enableShimmer: Boolean = false
    private var priority: RequestPriority = RequestPriority.NORMAL
    private val transformations = mutableListOf<Transformation>()

    fun load(url: String): RequestBuilder {
        this.url = url
        return this
    }

    fun resize(width: Int, height: Int): RequestBuilder {
        resizeWidth = width
        resizeHeight = height
        return this
    }

    fun transform(vararg transformations: Transformation): RequestBuilder {
        this.transformations.addAll(transformations)
        return this
    }

    fun placeholder(hex: String?): RequestBuilder {
        placeholderColor = hex?.toColorInt()
        return this
    }

    fun error(resId: Int): RequestBuilder {
        errorRes = resId
        return this
    }

    fun priority(priority: RequestPriority): RequestBuilder {
        this.priority = priority
        return this
    }

    fun into(imageView: ImageView) {
        // 1. Build request object
        val request = Request(
            url ?: throw IllegalArgumentException("URL required"),
            resizeWidth, resizeHeight,
            useMemoryCache, useDiskCache,
            transformations.toList(),
            outWidth, outHeight,
            enableShimmer
        )

        // 2. Create target
        val errorDrawable = errorRes?.let {
            AppCompatResources.getDrawable(imageView.context, it)
        }
        val target = ImageViewTarget(imageView, errorDrawable, enableShimmer)

        // 3. Apply placeholder
        applyPlaceholder(imageView)

        // 4. Check memory cache (sync)
        if (engine.checkMemoryCache(request, target)) {
            RequestManager.track(imageView, null)
            return
        }

        // 5. Load from disk/network (async)
        val reload = { into(imageView) }
        val job = if (!RequestManager.isPaused()) {
            engine.load(request, target, priority)
        } else null

        RequestManager.track(imageView, job, onResume = reload)
    }
}
```

**Design Patterns:**

- **Builder Pattern**: Fluent API
- **Strategy Pattern**: Transformations
- **Observer Pattern**: Target callbacks

#### **4.1.3 Engine (Core Logic)**

Quản lý toàn bộ quá trình load ảnh với priority queues và coroutines.

```kotlin
class Engine(
    private val activeResources: ActiveResources,
    private val memoryCache: MemoryCache,
    private val diskCache: DiskCache,
    private val fetcher: DataFetcher,
    private val bitmapPool: BitmapPool
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Priority queues
    private val highPriorityQueue = Channel<PrioritizedRequest>(Channel.UNLIMITED)
    private val normalPriorityQueue = Channel<PrioritizedRequest>(Channel.UNLIMITED)
    private val lowPriorityQueue = Channel<PrioritizedRequest>(Channel.UNLIMITED)

    @Volatile
    private var isFastScrolling = false

    init {
        // Setup active → memory transition
        activeResources.setOnResourceReleased { key, resource ->
            val bitmap = resource.getBitmap()
            if (!bitmap.isRecycled) {
                memoryCache.put(key, bitmap)
            }
        }

        // Start workers
        startPriorityWorkers()
    }

    private fun startPriorityWorkers() {
        // 2 workers for HIGH priority
        repeat(2) {
            engineScope.launch {
                for (prioritizedReq in highPriorityQueue) {
                    if (!prioritizedReq.job.isCancelled) {
                        executeLoad(prioritizedReq.request, prioritizedReq.target)
                    }
                }
            }
        }

        // 1 worker for NORMAL priority
        engineScope.launch {
            for (prioritizedReq in normalPriorityQueue) {
                if (!prioritizedReq.job.isCancelled) {
                    if (isFastScrolling) delay(50) // Throttle
                    executeLoad(prioritizedReq.request, prioritizedReq.target)
                }
            }
        }

        // 1 worker for LOW priority  
        engineScope.launch {
            for (prioritizedReq in lowPriorityQueue) {
                if (!prioritizedReq.job.isCancelled) {
                    if (isFastScrolling) delay(100) // Heavy throttle
                    executeLoad(prioritizedReq.request, prioritizedReq.target)
                }
            }
        }
    }

    fun setFastScrolling(isFast: Boolean) {
        isFastScrolling = isFast
    }

    fun checkMemoryCache(req: Request, target: Target): Boolean {
        val key = buildKey(req)

        // Check active resources
        activeResources.get(key)?.let { resource ->
            if (!resource.isReleased() && !resource.getBitmap().isRecycled) {
                target.onResourceReady(resource)
                ImageLoaderLogger.log(ImageLoadLog(req.url, LogSource.ACTIVE_CACHE, ...))
                return true
            }
        }

        // Check memory cache
        memoryCache.get(key)?.let { bitmap ->
            if (!bitmap.isRecycled) {
                val res = EngineResource(key, bitmap, activeResources)
                activeResources.put(key, res)
                target.onResourceReady(res)
                ImageLoaderLogger.log(ImageLoadLog(req.url, LogSource.MEMORY_CACHE, ...))
                return true
            }
        }

        return false
    }

    fun load(req: Request, target: Target, priority: RequestPriority): Job {
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

        try {
            // Try disk cache
            diskCache.get(dataKey)?.let { bytes ->
                val bitmap = decodeDiskCacheBytes(bytes, req)
                deliverResult(bitmap, req, target, key, LogSource.DISK_CACHE)
                return
            }

            // Fetch from network
            val result = fetcher.fetch(req.url)
            val bitmap = decodeAndTransform(result.bytes, req)

            // Save to disk
            if (req.useDiskCache) {
                diskCache.put(dataKey, result.bytes, result.contentType)
            }

            deliverResult(bitmap, req, target, key, LogSource.NETWORK)

        } catch (e: Exception) {
            handleLoadError(e, req, target)
        }
    }

    private suspend fun decodeDiskCacheBytes(bytes: ByteArray, req: Request): Bitmap {
        var bitmap = BitmapDecoder.decode(bytes, req.resizeWidth ?: 0, req.resizeHeight ?: 0)

        if (req.transformations.isNotEmpty()) {
            bitmap = withContext(Dispatchers.Default) {
                req.transformations.fold(bitmap) { bmp, transform ->
                    transform.transform(bitmapPool, bmp, req.outWidth ?: 0, req.outHeight ?: 0)
                }
            }
        }

        return bitmap
    }

    private suspend fun deliverResult(
        bitmap: Bitmap,
        req: Request,
        target: Target,
        key: String,
        source: LogSource
    ) {
        val res = EngineResource(key, bitmap, activeResources)
        activeResources.put(key, res)

        withContext(Dispatchers.Main) {
            target.onResourceReady(res)
        }

        ImageLoaderLogger.log(ImageLoadLog(req.url, source, ...))
    }
}
```

**Key Features:**

- **Priority queuing**: 3 separate channels
- **Coroutines**: Non-blocking I/O
- **Fast scroll detection**: Throttling
- **Error handling**: Retry logic
- **Logging**: Performance tracking

#### **4.1.4 EngineResource (Reference Counting)**

Wrapper cho Bitmap với reference counting để quản lý lifecycle.

```kotlin
class EngineResource(
    val key: String,
    private val bitmap: Bitmap,
    private val listener: ResourceListener
) {
    private var refCount = 0
    private val released = AtomicBoolean(false)

    @Synchronized
    fun acquire() {
        check(!released.get()) { "Cannot acquire a released resource" }
        refCount++
    }

    @Synchronized
    fun release() {
        check(refCount > 0) { "Cannot release a resource that is not acquired" }
        refCount--

        if (refCount == 0 && released.compareAndSet(false, true)) {
            listener.onResourceReleased(key, this)
        }
    }

    fun getBitmap(): Bitmap = bitmap

    fun isReleased(): Boolean = released.get()

    fun sizeInBytes(): Int = try {
        bitmap.allocationByteCount
    } catch (t: Throwable) {
        bitmap.byteCount
    }
}
```

**Lifecycle:**

```
Create → acquire() → Display → release() → Move to Memory Cache
         refCount=1          refCount=0
```

**Benefits:**

- Tránh bitmap bị recycle khi còn đang hiển thị
- Tự động cleanup khi không còn reference
- Thread-safe với synchronized

### 4.2 Cache System

#### **4.2.1 ActiveResources**

```kotlin
class ActiveResources : ResourceListener {
    private val activeMap = mutableMapOf<String, EngineResource>()
    private var resourceReleasedCallback: ((String, EngineResource) -> Unit)? = null

    fun setOnResourceReleased(callback: (String, EngineResource) -> Unit) {
        resourceReleasedCallback = callback
    }

    @Synchronized
    fun put(key: String, engineResource: EngineResource) {
        activeMap[key] = engineResource
    }

    @Synchronized
    fun get(key: String): EngineResource? {
        return activeMap[key]
    }

    @Synchronized
    fun remove(key: String) {
        activeMap.remove(key)
    }

    override fun onResourceReleased(key: String, engineResource: EngineResource) {
        synchronized(this) {
            activeMap.remove(key)
        }
        resourceReleasedCallback?.invoke(key, engineResource)
    }
}
```

**Characteristics:**

- **Thread-safe**: synchronized methods
- **Callback pattern**: Notify khi resource released
- **No size limit**: Active resources không bị evict

#### **4.2.2 MemoryCache (LRU)**

```kotlin
class MemoryCache(
    maxBytes: Int,
    private val bitmapPool: BitmapPool? = null
) {
    private var itemCount = 0

    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.safeByteCount()

        override fun entryRemoved(
            evicted: Boolean,
            key: String?,
            oldValue: Bitmap?,
            newValue: Bitmap?
        ) {
            if (evicted) {
                ImageLoaderLogger.d("MemoryCache", "Evicting image")
                itemCount--
            }

            // Return evicted bitmap to pool
            if (evicted && oldValue != null && oldValue.isMutable && !oldValue.isRecycled) {
                bitmapPool?.put(oldValue)
            }
        }
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap): Bitmap? {
        if (bitmap.isRecycled) return null

        val oldBitmap = cache.put(key, bitmap)
        if (oldBitmap == null) {
            itemCount++
        }
        return oldBitmap
    }

    fun remove(key: String): Bitmap? {
        val removed = cache.remove(key)
        if (removed != null) {
            itemCount--
        }
        return removed
    }

    fun clear() {
        cache.evictAll()
        itemCount = 0
    }

    val size: Int get() = cache.size()
}

private fun Bitmap.safeByteCount(): Int {
    return try {
        if (isRecycled) 0 else allocationByteCount
    } catch (_: Throwable) {
        0
    }
}
```

**LRU Algorithm:**

- Access/put → move to head
- Evict từ tail khi đầy
- Size-based eviction (bytes, không phải count)

**Integration với Bitmap Pool:**

- Evicted bitmap → pool (nếu mutable)
- Pool → reuse cho decode mới

#### **4.2.3 DiskCache**

```kotlin
class DiskCache(context: Context) {
    private val cacheDir = File(context.externalCacheDir, "image_cache").apply {
        if (!exists()) mkdirs()
    }

    companion object {
        private const val TAG = "DiskCache"
    }

    fun get(key: String): ByteArray? {
        return try {
            val file = File(cacheDir, key)
            if (file.exists()) {
                ImageLoaderLogger.d(TAG, "Disk cache hit: $key")
                file.readBytes()
            } else {
                ImageLoaderLogger.d(TAG, "Disk cache miss: $key")
                null
            }
        } catch (e: Exception) {
            ImageLoaderLogger.e(TAG, "Failed to read disk cache", e, LogCategory.CACHE)
            null
        }
    }

    fun put(key: String, bytes: ByteArray, contentType: String?) {
        try {
            val file = File(cacheDir, key)
            file.writeBytes(bytes)
            ImageLoaderLogger.d(TAG, "Saved to disk: $key (${bytes.size} bytes)")
        } catch (e: Exception) {
            ImageLoaderLogger.e(TAG, "Failed to write disk cache", e, LogCategory.CACHE)
        }
    }

    fun clear() {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        ImageLoaderLogger.i(TAG, "Disk cache cleared")
    }

    fun size(): Long {
        return cacheDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
}
```

**File Structure:**

```
/storage/emulated/0/Android/data/com.example.app/cache/image_cache/
├── a3f7c2b8d9e1f0c4b5a6e7d8c9f0e1d2
├── b4e8d3c9e0f1a5b6c7d8e9f0a1b2c3d4
└── ...
```

**Filename = MD5(request key)**

**Future Improvements:**

- LRU eviction cho disk cache
- Max size limit
- Metadata file (access time, size, etc.)

#### **4.2.4 LruBitmapPool**

```kotlin
class LruBitmapPool(private val maxSizeBytes: Long) : BitmapPool {
    private data class Key(val width: Int, val height: Int, val config: Bitmap.Config)

    private val map = LinkedHashMap<Key, MutableList<Bitmap>>(0, 0.75f, true)
    private var currentSize = 0L

    // Debug counters
    private var hits = 0
    private var misses = 0
    private var puts = 0
    private var evictions = 0

    @Synchronized
    override fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap? {
        val key = Key(width, height, config)

        // 1. Try exact match
        val bitmap = map[key]?.firstOrNull { isReusable(it) }?.also {
            map[key]?.remove(it)
            if (map[key]?.isEmpty() == true) map.remove(key)
            currentSize -= it.safeByteCount()
            hits++
        }

        if (bitmap != null) {
            bitmap.eraseColor(0) // Clear previous content
            return bitmap
        }

        // 2. Fallback: find larger bitmap and reconfigure
        for ((k, list) in map.entries) {
            if (k.config == config && k.width >= width && k.height >= height) {
                val candidate = list.firstOrNull { isReusable(it) }
                if (candidate != null) {
                    list.remove(candidate)
                    if (list.isEmpty()) map.remove(k)
                    currentSize -= candidate.safeByteCount()

                    candidate.reconfigure(width, height, config)
                    candidate.eraseColor(0)
                    hits++
                    return candidate
                }
            }
        }

        misses++
        return null
    }

    @Synchronized
    override fun put(bitmap: Bitmap) {
        if (!isReusable(bitmap)) return

        val size = bitmap.safeByteCount()
        if (size <= 0 || size > maxSizeBytes / 2) return

        val key = Key(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        map.getOrPut(key) { mutableListOf() }.add(bitmap)
        currentSize += size
        puts++

        trimToSize(maxSizeBytes)
    }

    @Synchronized
    override fun clear() {
        map.values.forEach { list ->
            list.forEach { if (!it.isRecycled) it.recycle() }
        }
        map.clear()
        currentSize = 0L
    }

    private fun trimToSize(maxSize: Long) {
        val iter = map.entries.iterator()
        while (currentSize > maxSize && iter.hasNext()) {
            val entry = iter.next()
            val list = entry.value

            while (list.isNotEmpty() && currentSize > maxSize) {
                val b = list.removeAt(list.size - 1)
                currentSize -= b.safeByteCount()
                if (!b.isRecycled) b.recycle()
                evictions++
            }

            if (list.isEmpty()) iter.remove()
        }
    }

    private fun isReusable(bitmap: Bitmap): Boolean {
        return !bitmap.isRecycled && bitmap.isMutable
    }

    fun dumpStats(): String {
        return "hits=$hits, misses=$misses, puts=$puts, evictions=$evictions, size=$currentSize/$maxSizeBytes"
    }
}
```

**Pool Efficiency Metrics:**

```kotlin
val hitRate = (hits.toFloat() / (hits + misses)) * 100
// Target: > 70% hit rate
```

**Khi nào cần dùng:**

- RecyclerView với ảnh cùng size
- Multiple requests cho cùng dimensions
- Varied image sizes
- Low memory devices

### 4.3 Fetcher & Decoder

#### **4.3.1 HttpFetcher**

```kotlin
class HttpFetcher(
    private val connectionFactory: ConnectionFactory = DefaultConnectionFactory,
    private val maxRetries: Int = 2,
    private val retryDelayMillis: Long = 700,
) : DataFetcher {

    override suspend fun fetch(url: String): HttpResult {
        var lastError: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                connectionFactory.open(url).run {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    requestMethod = "GET"
                    doInput = true
                    connect()

                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw IOException("HTTP $responseCode")
                    }

                    val result = HttpResult(inputStream.use { it.readBytes() }, contentType)
                    disconnect()
                    return result
                }
            } catch (e: Exception) {
                lastError = e
                ImageLoaderLogger.w(
                    TAG,
                    "Retry ${attempt + 1}/$maxRetries: ${e.message}",
                    LogCategory.NETWORK
                )
                if (attempt < maxRetries - 1) {
                    delay(retryDelayMillis * (1L shl attempt))
                }
            }
        }
        throw lastError ?: IOException("Unknown error fetching $url")
    }

    companion object {
        private const val TAG = "HttpFetcher"
    }
}
```

**ConnectionFactory:**

```kotlin
fun interface ConnectionFactory {
    fun open(url: String): HttpURLConnection
}

object DefaultConnectionFactory : ConnectionFactory {
    override fun open(url: String): HttpURLConnection {
        return URL(url).openConnection() as HttpURLConnection
    }
}
```

**Benefits:**

- **Đơn giản, dễ mock**: Interface cho phép inject factory khác khi test
- **Không phụ thuộc OkHttp**: Dùng trực tiếp `HttpURLConnection`
- **Chủ động timeout**: `HttpFetcher` gán `connectTimeout`/`readTimeout` mỗi request

#### **4.3.2 BitmapDecoder**

```kotlin
object BitmapDecoder {
    private var bitmapPool: BitmapPool? = null
    private var useBitmapPool: Boolean = false

    fun setBitmapPool(pool: BitmapPool?) {
        bitmapPool = pool
    }

    fun setUseBitmapPool(use: Boolean) {
        useBitmapPool = use
    }

    fun decode(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            // 1. Decode bounds only
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        // 2. Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(
            options.outWidth, options.outHeight,
            reqWidth, reqHeight
        )

        // 3. Try to get bitmap from pool
        if (useBitmapPool && bitmapPool != null && reqWidth > 0 && reqHeight > 0) {
            options.inMutable = true
            options.inBitmap = bitmapPool?.get(
                options.outWidth / options.inSampleSize,
                options.outHeight / options.inSampleSize,
                Bitmap.Config.ARGB_8888
            )
        }

        // 4. Decode actual bitmap
        options.inJustDecodeBounds = false

        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: throw IOException("Failed to decode bitmap")
        } catch (e: IllegalArgumentException) {
            // inBitmap reuse failed, try again without pool
            options.inBitmap = null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: throw IOException("Failed to decode bitmap")
        }
    }

    private fun calculateInSampleSize(
        width: Int, height: Int,
        reqWidth: Int, reqHeight: Int
    ): Int {
        if (reqWidth <= 0 || reqHeight <= 0) return 1

        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
```

**inSampleSize Examples:**

```
Original: 2048x2048
Request: 512x512

inSampleSize = 4
Decoded: 512x512 (4x downscaling)
Memory saved: 16x
```

**BitmapFactory.Options:**

- **inJustDecodeBounds**: Get size without decoding
- **inSampleSize**: Downsample during decode
- **inBitmap**: Reuse bitmap memory (pool)
- **inMutable**: Allow reconfigure

### 4.4 Transformation System

#### **4.4.1 Transformation Interface**

```kotlin
interface Transformation {
    fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap

    fun key(): String
}
```

**BaseTransformation:**

```kotlin
abstract class BaseTransformation(private val id: String) : Transformation {
    override fun key(): String = id

    protected fun createBitmap(
        width: Int,
        height: Int,
        config: Bitmap.Config
    ): Bitmap {
        return Bitmap.createBitmap(width, height, config)
    }

    override fun equals(other: Any?): Boolean {
        return other is BaseTransformation && other.id == id
    }

    override fun hashCode(): Int = id.hashCode()
}
```

#### **4.4.2 CenterCropRoundedCorners**

Đã phân tích ở phần trước.

#### **4.4.3 Custom Transformation Example**

```kotlin
class GrayscaleTransformation : BaseTransformation("Grayscale") {
    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val width = toTransform.width
        val height = toTransform.height

        val result = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f) // Grayscale
            })
        }

        canvas.drawBitmap(toTransform, 0f, 0f, paint)

        return result
    }
}
```

**Usage:**

```kotlin
ImageLoader.with(context)
    .load(url)
    .transform(GrayscaleTransformation())
    .into(imageView)
```

### 4.5 Logger & Monitoring

Đã phân tích chi tiết ở phần 2.6.

---

## 5. Chi Tiết App Module

### 5.1 Kiến Trúc Clean Architecture

App module áp dụng **Clean Architecture** với 3 layers:

```
Presentation Layer (UI + ViewModel)
       ↓ (uses)
Domain Layer (Use Cases + Models + Repository Interface)
       ↓ (implements)
Data Layer (Repository Impl + API + Cache)
```

**Dependency Rule:**

- Presentation → Domain
- Data → Domain
- Domain không phụ thuộc vào layer nào

**Benefits:**

- **Testability**: Dễ mock dependencies
- **Independence**: UI, database, framework có thể thay đổi
- **Maintainability**: Separation of concerns rõ ràng

### 5.2 Home Module

#### **5.2.1 Domain Layer**

**UnsplashPhoto Model:**

Sử dụng **Kotlinx Serialization** với `@Serializable` annotation:

```kotlin
@Serializable
data class UnsplashPhoto(
    val id: String,
    val created_at: String,
    val width: Int,
    val height: Int,
    val color: String? = "#000000",
    val likes: Int,
    val description: String?,
    val alt_description: String?,
    val urls: UnsplashUrls,
    val links: UnsplashLinks,
    val user: UnsplashUser
)

@Serializable
data class UnsplashUrls(
    val raw: String?,
    val full: String?,
    val regular: String?,
    val small: String?,
    val thumb: String?
)

@Serializable
data class UnsplashLinks(
    val self: String,
    val html: String,
    val download: String,
    val download_location: String
)

@Serializable
data class UnsplashUser(
    val id: String,
    val username: String,
    val name: String,
    val profile_image: ProfileImage?
)

@Serializable
data class ProfileImage(
    val small: String,
    val medium: String,
    val large: String
)
```

**Key Points về Kotlinx Serialization:**

- `@Serializable`: Annotation để generate serializer tự động
- **Type-safe**: Compile-time checking
- **Lightweight**: Nhẹ hơn Gson/Moshi
- **Kotlin-first**: Hỗ trợ tốt cho Kotlin features (default values, nullability)
- **No reflection**: Sử dụng code generation thay vì reflection

**PhotoRepository Interface:**

```kotlin
interface PhotoRepository {
    suspend fun getRandomPhotos(count: Int, page: Int): List<UnsplashPhoto>
}
```

**GetRandomPhotosUseCase:**

```kotlin
class GetRandomPhotosUseCase(private val repository: PhotoRepository) {
    suspend operator fun invoke(count: Int, page: Int): List<UnsplashPhoto> {
        return repository.getRandomPhotos(count, page)
    }
}
```

**Why Use Case?**

- **Single Responsibility**: Mỗi use case = 1 business logic
- **Reusability**: Có thể dùng ở nhiều màn hình
- **Testability**: Dễ test business logic riêng

#### **5.2.2 Data Layer**

**HttpClient (Custom Implementation):**

Thay vì sử dụng Retrofit, project này implement custom HTTP client với `HttpURLConnection`:

```kotlin
class HttpClient(
    private val baseUrl: String,
    private val accessKey: String
) {
    suspend fun get(path: String, query: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val urlStr = buildUrl(path, query)
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Client-ID $accessKey")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Version", "v1")
                connectTimeout = 30_000
                readTimeout = 30_000
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    throw Exception("HTTP $responseCode: $error")
                }
            } finally {
                connection.disconnect()
            }
        }

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val queryString = query.entries.joinToString("&") { "${it.key}=${it.value}" }
        return if (queryString.isNotEmpty()) "$baseUrl$path?$queryString" else "$baseUrl$path"
    }
}
```

**Key Features:**

- **HttpURLConnection**: Native Android HTTP client, không cần thêm dependency
- **Coroutines**: Async operations với `withContext(Dispatchers.IO)`
- **Authorization**: Client-ID header cho Unsplash API
- **Timeout**: 30 giây cho connect và read
- **Error handling**: Parse error stream khi response không thành công

**BaseApiService:**

Base class để xử lý JSON deserialization với Kotlinx Serialization:

```kotlin
open class BaseApiService(
    private val client: HttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true  // Bỏ qua fields không cần thiết
        coerceInputValues = true  // Convert invalid values
    }

    protected suspend fun <T> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        deserializer: DeserializationStrategy<T>
    ): T {
        val response = client.get(path, query)
        return json.decodeFromString(deserializer, response)
    }
}
```

**UnsplashApi Interface & Implementation:**

```kotlin
interface UnsplashApi {
    suspend fun getRandomPhotos(page: Int, perPage: Int = 10): List<UnsplashPhoto>
}

class UnsplashApiImpl(
    client: HttpClient
) : BaseApiService(client), UnsplashApi {

    override suspend fun getRandomPhotos(page: Int, perPage: Int): List<UnsplashPhoto> {
        return get(
            path = "photos",
            query = mapOf(
                "page" to page.toString(),
                "per_page" to perPage.toString()
            ),
            deserializer = ListSerializer(UnsplashPhoto.serializer())
        )
    }
}
```

**PhotoRepositoryImpl:**

```kotlin
class PhotoRepositoryImpl(private val api: UnsplashApi) : PhotoRepository {
    override suspend fun getRandomPhotos(count: Int, page: Int): List<UnsplashPhoto> {
        return api.getRandomPhotos(page = page, perPage = count)
    }
}
```

**Dependency Injection (Injector.kt):**

```kotlin
object Injector {
    private const val BASE_URL = "https://api.unsplash.com/"
    private const val ACCESS_KEY = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

    // HttpClient singleton
    private val httpClient by lazy { HttpClient(BASE_URL, ACCESS_KEY) }

    // API implementation
    private val unsplashApi: UnsplashApi by lazy { UnsplashApiImpl(httpClient) }

    // Repository
    private val photoRepository: PhotoRepository by lazy {
        PhotoRepositoryImpl(unsplashApi)
    }

    // Use case (public để Activity/ViewModel access)
    val getRandomPhotosUseCase: GetRandomPhotosUseCase by lazy {
        GetRandomPhotosUseCase(photoRepository)
    }
}
```

**Tại sao là HttpClient?**

1. **Không phụ thuộc Retrofit/OkHttp stack**: Luồng chính chỉ dùng `HttpURLConnection`
2. **Lightweight**: HttpURLConnection là built-in Android
3. **Full Control**: Tự quản lý connections, headers, timeouts
4. **Learning Purpose**: Hiểu rõ HTTP protocol và request/response handling
5. **Kotlinx Serialization**: Sử dụng `@Serializable` annotation cho response; Gson chỉ phục vụ
   `JsonBackupManager`

**Trade-offs:**

**Lợi ích:**

- Giảm APK size (không cần Retrofit dependencies)
- Hiểu rõ low-level HTTP
- Custom error handling dễ dàng

**Bất lợi:**

- Không có built-in features như Retrofit (converters, adapters)
- Phải tự implement connection pooling nếu cần
- Ít type-safe hơn Retrofit annotations

#### **5.2.3 Cache Layer**

**JsonBackupManager:**
Đã phân tích ở phần 2.7.

**PhotoPreloader:**

```kotlin
class PhotoPreloader(
    private val getPhotosUseCase: GetRandomPhotosUseCase,
    private val scope: CoroutineScope
) {
    private val preloadedPages = mutableMapOf<Int, List<UnsplashPhoto>>()
    private val perPage = 25

    companion object {
        private const val TAG = "PhotoPreloader"
    }

    fun preloadPages(currentPage: Int) {
        scope.launch {
            try {
                // Preload next 2 pages
                for (page in (currentPage + 1)..(currentPage + 2)) {
                    if (!preloadedPages.containsKey(page)) {
                        Log.d(TAG, "Preloading page $page")
                        val photos = getPhotosUseCase(perPage, page)
                        preloadedPages[page] = photos

                        // Preload images với LOW priority
                        photos.forEach { photo ->
                            photo.urls.small?.let { url ->
                                ImageLoader.with(/* context */)
                                    .load(url)
                                    .priority(RequestPriority.LOW)
                                    .resize(400, 400)
                                // Don't set target, just preload to cache
                            }
                        }
                    }
                }

                // Cleanup old pages (keep only 5 pages)
                if (preloadedPages.size > 5) {
                    val oldPages = preloadedPages.keys
                        .filter { it < currentPage - 2 }
                    oldPages.forEach { preloadedPages.remove(it) }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Preload failed", e)
            }
        }
    }

    fun hasPreloadedPage(page: Int): Boolean {
        return preloadedPages.containsKey(page)
    }

    fun getPreloadedPage(page: Int): List<UnsplashPhoto>? {
        return preloadedPages[page]
    }

    fun clear() {
        preloadedPages.clear()
    }
}
```

**Preloading Strategy:**

- Preload 2 pages ahead
- Use LOW priority để không block visible items
- Cleanup old pages để tiết kiệm memory
- Preload cả data lẫn images

### 5.3 Data Layer

#### **5.3.1 Error Handling**

**AppError Sealed Class:**

```kotlin
sealed class AppError(open val message: String) {
    data class NetworkError(override val message: String) : AppError(message)
    data class ServerError(override val message: String) : AppError(message)
    data class UnknownError(override val message: String) : AppError(message)
}
```

**ErrorHandler:**

```kotlin
object ErrorHandler {
    fun handleError(e: Exception): AppError {
        return when (e) {
            is IOException -> AppError.NetworkError(
                e.message ?: "Network connection failed"
            )
            is HttpException -> {
                when (e.code()) {
                    in 500..599 -> AppError.ServerError(
                        "Server error: ${e.message()}"
                    )
                    in 400..499 -> AppError.NetworkError(
                        "Client error: ${e.message()}"
                    )
                    else -> AppError.UnknownError(e.message())
                }
            }
            else -> AppError.UnknownError(
                e.message ?: "Unknown error occurred"
            )
        }
    }

    fun shouldRetry(error: AppError): Boolean {
        return when (error) {
            is AppError.NetworkError -> true
            is AppError.ServerError -> true
            is AppError.UnknownError -> false
        }
    }

    fun getRetryDelay(error: AppError, attemptCount: Int = 0): Long {
        val baseDelay = when (error) {
            is AppError.NetworkError -> 1000L
            is AppError.ServerError -> 2000L
            is AppError.UnknownError -> 0L
        }

        // Exponential backoff with max 32 seconds
        return baseDelay * (1 shl attemptCount.coerceAtMost(5))
    }
}
```

**Retry Delays:**

```
Attempt 1: 1s
Attempt 2: 2s
Attempt 3: 4s
Attempt 4: 8s
Attempt 5: 16s
Attempt 6+: 32s (max)
```

#### **5.3.2 Network Monitoring**

```kotlin
class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isNetworkAvailable(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network)
                ?: return false

            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo?.isConnected == true
        }
    }

    fun registerCallback(callback: ConnectivityManager.NetworkCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(callback)
        }
    }

    fun unregisterCallback(callback: ConnectivityManager.NetworkCallback) {
        connectivityManager.unregisterNetworkCallback(callback)
    }
}
```

**Network Callback Usage:**

```kotlin
class HomeActivity : AppCompatActivity() {
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                // Network available, retry failed requests
                viewModel.retryIfNeeded()
            }
        }

        override fun onLost(network: Network) {
            runOnUiThread {
                // Network lost, show offline UI
                showOfflineSnackbar()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        networkMonitor.registerCallback(networkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        networkMonitor.unregisterCallback(networkCallback)
    }
}
```

### 5.4 Presentation Layer

#### **5.4.1 HomeViewModel**

```kotlin
sealed class HomeUiState {
    object InitLoading : HomeUiState()
    data class InitError(val error: AppError, val isOffline: Boolean, val hasBackupData: Boolean) :
        HomeUiState()
    data class Data(
        val photos: List<UnsplashPhoto>,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val isOffline: Boolean = false,
        val error: AppError? = null
    ) : HomeUiState()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    val uiState: StateFlow<HomeUiState>      // backed by MutableStateFlow
    fun loadPhotos()                         // ưu tiên backup -> network -> preload/save
    fun refresh()                            // clear preload, fetch trang 1, lưu backup
    fun loadMorePhotos(showLoading: Boolean) // ưu tiên dữ liệu preloaded, drop overlap
    fun clearError()                         // reset error trong trạng thái Data
}
```

Quy trình chính:

- **loadPhotos**: phát hiện backup khả dụng, fallback sang network, lưu `currentPage`, kick-off
  `PhotoPreloader`, lưu `JsonBackupManager`.
- **handleLoadError**: ưu tiên trả về dữ liệu backup, bật `isOffline`, lên lịch `scheduleRetry` nếu
  lỗi cho phép (`NetworkError`, `RateLimit`, `Server 503`).
- **scheduleRetry**: sử dụng `ErrorHandler.getRetryDelay()` + `NetworkMonitor.isNetworkAvailable()`
  trước khi gọi lại `loadPhotos()`.
- **refresh**: dừng preload hiện tại, reset trang về 1, ghi đè backup khi fetch thành công.
- **loadMorePhotos**: đọc trước từ `PhotoPreloader`, loại bỏ 3 phần tử đầu để tránh trùng trang, nối
  vào danh sách hiện có, lưu backup mới.
- **onCleared**: hủy `retryJob`, dọn `PhotoPreloader`.

**ViewModel Architecture:**

- **StateFlow**: Reactive UI updates
- **Sealed class UI state**: Type-safe state management
- **Coroutines**: Async operations
- **Error handling**: Comprehensive error states
- **Preloading**: Background data fetch
- **Backup/restore**: Offline support

#### **5.4.2 HomeActivity**

Pseudo flow:

1. `onCreate` → gọi `enableEdgeToEdge()`, `setContentView`, khởi tạo view references bằng
   `findViewById`.
2. Thiết lập `RecyclerView`:

- `GridLayoutManager(spanCount)`
- `PhotoAdapter { spanCount }`
- `itemAnimator = null` để tránh glitch shimmer.

3. `addOnScrollListener`:

- Khi `STATE_SETTLING` → `RequestManager.pauseAll()`
- Khi `STATE_IDLE` → gom các `ImageView` đang hiển thị và `resumeVisibleOnly(...)`
- Khi gần cuối danh sách → `viewModel.loadMorePhotos(showLoading = false/true)`

4. Pinch-to-zoom bằng `ScaleGestureDetector`: thay đổi `spanCount` (1–3 cột) và cập nhật layout.
5. `SwipeRefreshLayout` → `viewModel.refresh()`
6. Nút settings mở `BottomSheetDialog` cho corner radius toggle + chọn số cột.
7. Quan sát `viewModel.uiState` để hiển thị progress, lỗi, footer loading, snackbar; quan sát
   `NetworkMonitor` để bật `view_network_status_bar`.
8. Gắn `FPSOverlay` lên decor view để monitoring hiệu năng.
9. `dispatchTouchEvent` chặn đa chạm để tránh scroll jitter trong lúc zoom.

**Activity Responsibilities:**

- **UI setup**: Dùng `findViewById` + `GridLayoutManager`, pinch-to-zoom thay đổi `spanCount`
- **Request throttling**: Phối hợp với `RequestManager.pauseAll()/resumeVisibleOnly()` khi scroll
  nhanh hoặc idle
- **Data flow**: Collect `StateFlow` để hiển thị loading, error, footer, snackbar, cached trạng thái
- **Offline UX**: Quan sát `NetworkMonitor` để hiển thị `view_network_status_bar` và gợi ý refresh
- **Developer tools**: Bottom sheet settings, logger shortcut, `FPSOverlay` gắn vào root view

#### **5.4.3 PhotoAdapter**

Pseudo highlights:

- `ListAdapter` với `DiffUtil.ItemCallback` theo `photo.id`.
- `onBindViewHolder`:
  ```kotlin
  RequestManager.clear(holder.imgPhoto)
  val priority = when (position) {
      in 0..5 -> RequestPriority.HIGH
      in 6..19 -> RequestPriority.NORMAL
      else -> RequestPriority.LOW
  }
  holder.bind(photo, spanProvider(), priority)
  ```
- `PhotoViewHolder.bind(...)`:

    - Tính toán chiều rộng/chiều cao theo span hiện tại.
    -
  `ImageLoader.with(...).overrideSize().placeholder(hexColor).resize(400, 400).enableShimmer(true)`
    - Áp dụng `CenterCropRoundedCorners` khi người dùng bật từ bottom-sheet.
    - Đặt mô tả và long-press mở `bottom_sheet_download`, xử lý tải ảnh qua `MediaStore` (Android
      10+) hoặc `Downloads` dir (legacy).
- `onViewRecycled` luôn `RequestManager.clear(imageView)` để hủy request dang dở.

**Adapter Features:**

- **ListAdapter**: Efficient diff calculation
- **Priority assignment**: Based on position
- **Dynamic sizing**: Based on span count
- **Download feature**: Long press to download
- **Request cleanup**: onViewRecycled

---

## 6. Hướng Dẫn Sử Dụng

### 6.1 Cài Đặt

**Bước 1: Add module vào project**

```kotlin
// settings.gradle.kts
include(":app")
include(":ImageLoader")
```

**Bước 2: Add dependency**

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":ImageLoader"))
}
```

**Bước 3: Add permissions**

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" /><uses-permission
android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 6.2 Basic Usage

**Load ảnh đơn giản:**

```kotlin
ImageLoader.with(context)
    .load("https://example.com/image.jpg")
    .into(imageView)
```

**Với placeholder và error:**

```kotlin
ImageLoader.with(context)
    .load(imageUrl)
    .placeholder("#E0E0E0")
    .error(R.drawable.ic_error)
    .into(imageView)
```

**Với transformations:**

```kotlin
ImageLoader.with(context)
    .load(imageUrl)
    .resize(800, 800)
    .transform(CenterCropRoundedCorners(32f))
    .into(imageView)
```

### 6.3 Advanced Usage

**Priority và shimmer:**

```kotlin
ImageLoader.with(context)
    .load(imageUrl)
    .priority(RequestPriority.HIGH)
    .enableShimmer(true)
    .resize(400, 400)
    .into(imageView)
```

**Skip cache:**

```kotlin
ImageLoader.with(context)
    .load(imageUrl)
    .skipMemoryCache()
    .skipDiskCache()
    .into(imageView)
```

**Custom transformation:**

```kotlin
class BlurTransformation(private val radius: Float) : BaseTransformation("Blur($radius)") {
    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        // Apply blur effect
        return blurredBitmap
    }
}

ImageLoader.with(context)
    .load(imageUrl)
    .transform(BlurTransformation(10f))
    .into(imageView)
```

### 6.4 Lifecycle Management

**Trong Activity:**

```kotlin
recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
        when (newState) {
            RecyclerView.SCROLL_STATE_SETTLING -> RequestManager.pauseAll()
            RecyclerView.SCROLL_STATE_IDLE -> {
                val visible = rv.children
                    .mapNotNull { it.findViewById<ImageView>(R.id.imgPhoto) }
                    .filter { it.isVisible }
                RequestManager.resumeVisibleOnly(visible)
            }
        }
    }
})
```

### 6.5 Logging

**Mở log viewer:**

```kotlin
LogViewer.open(context)
```

**Custom logs:**

```kotlin
ImageLoaderLogger.d("TAG", "Debug message", LogCategory.CACHE)
ImageLoaderLogger.e("TAG", "Error message", exception, LogCategory.NETWORK)
```

---

## 7. Testing & Quality

### 7.1 Unit Tests

**Run tests:**

```bash
./gradlew :ImageLoader:testDebugUnitTest
```

**Generate coverage:**

```bash
./gradlew :ImageLoader:jacocoTestReport
```

**Coverage report:**

```
ImageLoader/build/reports/jacoco/jacocoTestReport/html/index.html
```

### 7.2 Độ phủ kiểm thử (JaCoCo)

Số liệu lấy từ `ImageLoader/build/reports/jacoco/jacocoTestReport/html/index.html` (sau
`./gradlew :ImageLoader:testDebugUnitTest` + `:ImageLoader:jacocoTestReport`):

- **Toàn dự án**: 62% instructions, 51% branches, 64 lớp được bao phủ ít nhất một phần.
- **Theo package tiêu biểu**
    - `com.example.imageloader.cache`: ~87% instructions, 60% branches
    - `com.example.imageloader.decode`: ~82% instructions, 59% branches
    - `com.example.imageloader.fetcher`: ~75% instructions, 75% branches
    - `com.example.imageloader.logger`: ~83% instructions, 72% branches
    - `com.example.imageloader.transformation`: ~94% instructions, 91% branches
    - `com.example.imageloader.drawable`: ~93% instructions, 71% branches
    - `com.example.imageloader.target`: ~56% instructions, 36% branches
    - `com.example.imageloader.core`: ~49% instructions, 35% branches
    - `com.example.imageloader.ui`: ~37% instructions, 55% branches

**Đánh giá nhanh:**

- Các module thuần xử lý dữ liệu (cache, decode, fetcher, transformation, logger) có độ phủ tốt nhờ
  bộ test đơn vị tại `ImageLoader/src/test/java`.
- Engine, RequestManager và lớp UI mẫu vẫn thiếu test (độ phủ <60%), nên ưu tiên bổ sung test xử lý
  ưu tiên, fast scroll, retry và presenter/UI binding.
- Có thể mở báo cáo HTML để xem chi tiết từng lớp và dòng chưa được bao phủ.

---

## 8. Performance Optimization

### 8.1 Tips

1. **Resize images**: Luôn resize về kích thước cần thiết
2. **Use appropriate format**: RGB_565 cho images không cần alpha
3. **Enable bitmap pool**: Cho RecyclerView với ảnh cùng size
4. **Set priorities**: HIGH cho visible, LOW cho preload
5. **Monitor cache hits**: Dùng LogViewer để check hit rates

### 8.2 Benchmarks for small and medium SplashPhotos

**Memory Cache Hit (~0.8-2ms):**

- Fastest path
- Target: > 80% hit rate

**Disk Cache Hit (~20-100ms):**

- Tốt cho offline access
- Target: > 50% hit rate

**Network Fetch (~500-1000ms):**

- Slowest path
- Cần optimize với preloading

---

## 9. Dependencies & Requirements

### 9.1 Requirements

- **Android SDK**: 24+ (Android 7.0+)
- **Kotlin**: 2.0+
- **Gradle**: 8.0+
- **JVM**: 11+

### 9.2 ImageLoader Module Dependencies

```kotlin
dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.12.2")
}
```

### 9.3 App Module Dependencies

```kotlin
dependencies {
    implementation(project(":ImageLoader"))

    // UI foundation
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Compose (theme & previews)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Async / JSON
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")
    implementation(libs.gson) // dùng cho JsonBackupManager

    // Networking util
    implementation(libs.volley) // placeholder utility, HttpClient dùng HttpURLConnection thuần

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

**Lưu ý:** App module dùng **custom HttpClient** với `HttpURLConnection`. Gson vẫn được dùng cho
backup JSON (`JsonBackupManager`), còn luồng chính parsing API sử dụng **Kotlinx Serialization**.

---

## 10. Tài Liệu Tham Khảo

### 10.1 Inspired By

- [Glide](https://github.com/bumptech/glide) - Image loading framework
- [Picasso](https://github.com/square/picasso) - Simple image loading
- [Coil](https://github.com/coil-kt/coil) - Kotlin-first image loading

### 10.2 Các Khái Niệm Liên Quan

- **LRU Cache**: Least Recently Used eviction policy
- **Reference Counting**: Memory management technique
- **Bitmap Pooling**: Reuse bitmap memory
- **Priority Queue**: Task scheduling
- **Clean Architecture**: Software design pattern
- **MVVM**: Model-View-ViewModel pattern
- **Coroutines**: Kotlin async programming

### 10.3 API Documentation

- [Android Bitmap](https://developer.android.com/reference/android/graphics/Bitmap)
- [OkHttp](https://square.github.io/okhttp/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Unsplash API](https://unsplash.com/documentation)

---

