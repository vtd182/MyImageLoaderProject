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

Image Loader là một thư viện tải và cache ảnh được phát triển cho nền tảng Android với các mục tiêu

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

        // 1 worker cho NORMAL priority
        engineScope.launch {
            for (request in normalPriorityQueue) {
                executeLoad(request)
            }
        }

        // 1 worker cho LOW priority (preloading)
        engineScope.launch {
            for (request in lowPriorityQueue) {
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

#### **Cache Invalidation (Summary)**

- **MemoryCache**: Dựa vào `LruCache` → eviction auto; bitmap evicted được trả về `BitmapPool`.
- **ActiveResources**: Khi `EngineResource.release()` làm refCount = 0 → callback chuyển xuống
  Memory cache.
- **DiskCache**: `trimCacheAsync()` chạy nền để xóa file cũ nhất theo `lastModified`, `clear()` dùng
  khi người dùng chọn “Clear cache”.

---

## 4. Chi Tiết ImageLoader Module

### 4.1 Core Components

#### **4.1.1 ImageLoader (Singleton)**

`ImageLoader/src/main/java/com/example/imageloader/core/ImageLoader.kt`

- Khởi tạo duy nhất engine + toàn bộ cache layers thông qua double-checked locking.
- Tính toán kích thước `MemoryCache`/`BitmapPool` dựa trên device rồi bơm vào `Engine`.
- Expose API `with(context)` trả về `RequestBuilder`.

```kotlin
ImageLoader.with(context)
    .load(url)
    .enableShimmer()
    .into(imageView)
```

#### **4.1.2 RequestBuilder (Fluent API)**

- Quản lý toàn bộ cấu hình mutable (resize, transformations, priority, shimmer...) trước khi tạo
  `Request`.
- `into(imageView)` sẽ apply placeholder, kiểm tra cache đồng bộ, nếu miss mới queue request vào
  engine.
- Kết hợp với `RequestManager` để pause/resume khi RecyclerView scroll.

```kotlin
ImageLoader.with(context)
    .load(photo.urls.small!!)
    .resize(400, 400)
    .transform(CenterCropRoundedCorners(32f))
    .priority(RequestPriority.HIGH)
    .enableShimmer()
    .into(holder.imgPhoto)
```

#### **4.1.3 Engine (Core Logic)**

`ImageLoader/src/main/java/com/example/imageloader/core/Engine.kt`

- 3 hàng đợi ưu tiên (HIGH/NORMAL/LOW) → số lượng worker khác nhau.
- Bước đầu tiên luôn kiểm tra `ActiveResources` rồi `MemoryCache` để tránh I/O tốn kém.
- Nếu miss cache: đọc disk → nếu fail → fetch network → decode → transform → deliver về `Target`.
- Mọi callback UI (`onResourceReady`, `onLoadStarted`, `onLoadFailed`) đều post về Main thread.

```kotlin
private fun startPriorityWorkers() {
    startWorkerForQueue(highPriorityQueue, workerCount = 2)
    startWorkerForQueue(normalPriorityQueue, workerCount = 1)
    startWorkerForQueue(lowPriorityQueue, workerCount = 1)
}
```

#### **4.1.4 EngineResource (Reference Counting)**

- Gói `Bitmap` kèm `refCount`; khi `release()` đưa về memory cache thông qua callback.
- Đảm bảo bitmap không bị recycle khi view vẫn đang sử dụng.

```kotlin
@Synchronized
fun release() {
    check(refCount > 0)
    if (--refCount == 0 && released.compareAndSet(false, true)) {
        listener.onResourceReleased(key, this)
    }
}
```

### 4.2 Cache System

#### **4.2.1 ActiveResources**

`ImageLoader/src/main/java/com/example/imageloader/cache/ActiveResources.kt`

- Map `key → EngineResource` cho các bitmap đang xuất hiện trên UI (được acquire).
- Khi `EngineResource.release()` gọi `resourceReleasedCallback`, cache sẽ move bitmap xuống
  `MemoryCache`.

#### **4.2.2 MemoryCache (LRU)**

`ImageLoader/src/main/java/com/example/imageloader/cache/MemoryCache.kt`

- Dựa trên `LruCache<String, Bitmap>` với `sizeOf = allocationByteCount`.
- Khi eviction xảy ra, bitmap mutable sẽ được đẩy sang `BitmapPool` để tái sử dụng.
- API chính: `get`, `put`, `remove`, `clear`, `size`.

```kotlin
override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
    if (evicted && oldValue?.isMutable == true && !oldValue.isRecycled) {
        bitmapPool?.put(oldValue)
    }
}
```

#### **4.2.3 DiskCache**

`ImageLoader/src/main/java/com/example/imageloader/cache/DiskCache.kt`

- Lưu raw bytes với phần mở rộng dựa trên `contentType`, chỉ ghi file mới khi chưa tồn tại.
- Trước khi ghi, `ensureSizeInitialized()` cập nhật tổng size và `trimCacheAsync()` dọn file cũ nhất
  nếu vượt 150 MB mặc định.
- Ghi dữ liệu theo pattern `temp → rename` để đảm bảo atomic write, nên nếu app crash giữa chừng
  cache vẫn không corrupt.

```kotlin
@Synchronized
fun put(key: String, data: ByteArray, contentType: String?): Boolean {
    ensureSizeInitialized()
    val file = File(cacheDir, "$key${extensionOf(contentType)}")
    if (file.exists()) return true

    if (currentSize + data.size > maxSizeBytes) {
        trimCacheAsync(currentSize + data.size - maxSizeBytes)
    }

    val temp = File(cacheDir, "${file.name}.tmp")
    return try {
        FileOutputStream(temp).use { it.write(data) }
        temp.renameTo(file).also { success ->
            if (success) currentSize += data.size else temp.delete()
        }
    } catch (ioe: IOException) {
        temp.delete(); false
    }
}
```

**File organization:** `key = MD5(dataKey)` kết hợp extension (`.jpg/.png/.webp/.avif/.dat`).
Trimming dựa trên `lastModified` nên file ít dùng nhất bị xóa trước.

#### **4.2.4 LruBitmapPool**

`ImageLoader/src/main/java/com/example/imageloader/core/LruBitmapPool.kt`

- Lưu bitmap mutable trong `LinkedHashMap` (LRU) để tái sử dụng thông qua `BitmapFactory.inBitmap`.
- `get()` ưu tiên exact match, nếu không tìm thấy sẽ lấy bitmap lớn hơn và `reconfigure()` về kích
  thước mới.
- `put()` bỏ qua bitmap quá lớn (chiếm >50% pool) để tránh nghẽn.
- `trimToSize()` recycle bớt khi vượt ngưỡng.

```kotlin
override fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap? {
    val exact = map[key(width, height, config)]?.removeFirstOrNull { it.canReuse() }
    if (exact != null) return exact.also { currentSize -= it.safeByteCount() }

    return map.entries
        .firstOrNull { (k, _) -> k.config == config && k.width >= width && k.height >= height }
        ?.value
        ?.removeFirstOrNull { it.canReuse() }
        ?.apply { reconfigure(width, height, config) }
}
```

**Khi nào cần dùng:**

- RecyclerView với ảnh cùng size
- Multiple requests cho cùng dimensions
- Varied image sizes
- Low memory devices

### 4.3 Fetcher & Decoder

#### **4.3.1 HttpFetcher**

`ImageLoader/src/main/java/com/example/imageloader/fetcher/HttpFetcher.kt`

- `ConnectionFactory` (functional interface) giúp mock `HttpURLConnection` trong unit test.
- Retry tối đa 2 lần với exponential delay (`700ms`, `1400ms`).
- Ghi log qua `ImageLoaderLogger` mỗi lần retry.

```kotlin
override suspend fun fetch(url: String): HttpResult {
    var lastError: Exception? = null
    repeat(maxRetries) { attempt ->
        runCatching {
            connectionFactory.open(url).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                requestMethod = "GET"
                doInput = true
                connect()
            }.run {
                if (responseCode != HttpURLConnection.HTTP_OK) error("HTTP $responseCode")
                return HttpResult(
                    inputStream.use { it.readBytes() },
                    contentType
                ).also { disconnect() }
            }
        }.onFailure {
            lastError = it
            if (attempt < maxRetries - 1) delay(retryDelayMillis * (1L shl attempt))
        }
    }
    throw lastError ?: IOException("Unknown error fetching $url")
}
```

#### **4.3.2 BitmapDecoder**

`ImageLoader/src/main/java/com/example/imageloader/decode/BitmapDecoder.kt`

- Đọc bounds trước (`inJustDecodeBounds = true`) để tính `inSampleSize` → tránh decode bitmap quá
  lớn.
- Khi `useBitmapPool` bật, truyền `inBitmap` từ pool để reuse memory; nếu `IllegalArgumentException`
  xảy ra thì retry không dùng pool.
- Trích xuất dominant color cho placeholder thông qua `Palette` (dùng bản decode nhỏ 10×10).

```kotlin
fun decode(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, reqW, reqH)
    opts.inJustDecodeBounds = false
    if (useBitmapPool) opts.inBitmap = bitmapPool?.get(
        opts.outWidth / opts.inSampleSize,
        opts.outHeight / opts.inSampleSize,
        Bitmap.Config.ARGB_8888
    )
    return try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (_: IllegalArgumentException) {
        opts.inBitmap = null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
```

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

`app/src/main/java/com/example/myimageloaderproject/modules/home/data/cache/PhotoPreloader.kt`

- Giữ map `preloadedPages` và song song `preloadJobs` để không tải trùng lặp.
- Gọi `preloadPages(currentPage)` sẽ queue tối đa 3 trang kế tiếp trên `Dispatchers.IO`.
- `cleanupOldPages()` drop mọi trang `< currentPage - 1` và hủy job tương ứng để hạn chế
  RAM/network.

```kotlin
fun preloadPages(currentPage: Int) {
    ((currentPage + 1)..(currentPage + PRELOAD_AHEAD)).forEach { page ->
        if (page !in preloadedPages && page !in preloadJobs) {
            preloadJobs[page] = scope.launch(Dispatchers.IO) {
                runCatching { getRandomPhotosUseCase(perPage, page) }
                    .onSuccess { preloadedPages[page] = it }
                    .also { preloadJobs.remove(page) }
            }
        }
    }
    cleanupOldPages(currentPage)
}
```

**Preloading Strategy Highlights**

1. Data-only caching: chỉ lưu `UnsplashPhoto` để `HomeViewModel` render ngay, ảnh thật vẫn do
   `ImageLoader` xử lý với priority thấp.
2. `hasPreloadedPage()`/`getPreloadedPage()` giúp `loadMorePhotos()` đọc dữ liệu nóng; nếu miss thì
   fallback sang API.
3. `clear()` hủy mọi coroutine khi người dùng refresh hoặc ViewModel bị clear.

### 5.3 Data Layer

#### **5.3.1 Error Handling**

`app/src/main/java/com/example/myimageloaderproject/core/error/ErrorHandler.kt`

- `AppError` gom các tình huống: `NetworkError`, `RateLimitError`, `ServerError`, `UnknownError`.
- `handleError()` map exception → error UX-friendly, đồng bộ với thông điệp tiếng Việt trong UI.
- `shouldRetry()` chỉ true cho lỗi mạng, rate-limit và HTTP 503.
- `getRetryDelay()` trả về hằng số (3s mạng, 5s 503, `retryAfter` cho rate-limit).

```kotlin
fun shouldRetry(error: AppError) = when (error) {
    is AppError.NetworkError,
    is AppError.RateLimitError -> true
    is AppError.ServerError -> error.code == 503
    else -> false
}
```

**Retry Strategy Recap**

1. Network errors: auto retry sau 3s nếu thiết bị đã online trở lại (
   `NetworkMonitor.isNetworkAvailable()` check trước khi gọi lại API).
2. Rate limit: chờ `retryAfter` (default 60 s) rồi mới trigger `loadPhotos()` lần nữa.
3. Server 503: retry sau 5 s; các HTTP khác hiển thị snackbar và chờ người dùng tương tác.
4. Unknown errors: không retry tự động để tránh vòng lặp vô hạn, chỉ hiển thị thông báo.

#### **5.3.2 Network Monitoring**

`app/src/main/java/com/example/myimageloaderproject/core/network/NetworkMonitor.kt`

- Exposes a cold `Flow<NetworkStatus>` built via `callbackFlow` +
  `ConnectivityManager.NetworkCallback`.
- Emits `Available/Losing/Lost/Unavailable` states and deduplicates via `distinctUntilChanged()`.
- Provides synchronous `isNetworkAvailable()` helper for ViewModel retry checks.

**Collecting Network Status in UI**

```kotlin
private fun observeNetworkStatus() {
    lifecycleScope.launch {
        networkMonitor.networkStatus.collectLatest { status ->
            when (status) {
                NetworkStatus.Available -> showOnlineBanner()
                NetworkStatus.Unavailable,
                NetworkStatus.Lost -> showOfflineBanner()
                NetworkStatus.Losing -> showUnstableBanner()
            }
        }
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
  priority queues, retry logic và presenter/UI binding.
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
