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

`MemorySizeCalculator` tự động tính toán kích thước cache dựa trên:

**Các yếu tố ảnh hưởng:**

- **Device RAM**: Low RAM devices (< 1GB) → cache nhỏ hơn
- **Screen resolution**: 1080p vs 4K → cache lớn hơn tương ứng
- **Bitmap format**: ARGB_8888 (4 bytes/pixel) vs RGB_565 (2 bytes/pixel)

**Công thức:**

- Memory cache: 2-4 screens worth of pixels
- Bitmap pool: 2x memory cache size (nếu enabled)

#### **Bitmap Pooling**

Bitmap Pool là kỹ thuật **reuse memory** để giảm GC (Garbage Collection):

**Mechanism:**

- Lưu trữ bitmaps đã dùng trong LRU cache
- Ưu tiên exact match (width, height, config)
- Fallback: Lấy bitmap lớn hơn và reconfigure

**Benefits:**

- Giảm memory allocations → ít GC hơn
- Tăng performance khi decode nhiều ảnh liên tục
- Trade-off: Tốn thêm RAM để giữ pool

**Khi nào dùng:**

- RecyclerView với nhiều ảnh cùng size
- Gallery với thumbnails
- Low RAM devices

### 2.3 Request Priority System

Engine sử dụng **3 priority queues** với worker counts khác nhau:

**Worker Allocation:**

- **HIGH**: 2 workers (ảnh đang hiển thị)
- **NORMAL**: 1 worker (ảnh gần viewport)
- **LOW**: 1 worker (preloading)

**Priority Rules trong PhotoAdapter:**

- Position 0-5: `HIGH` (First screen)
- Position 6-19: `NORMAL` (Near viewport)
- Position 20+: `LOW` (Preloading)

### 2.4 Lifecycle Awareness

#### **Request Tracking System**

`RequestManager` quản lý lifecycle của image loading requests:

**Key APIs:**

- `track()`: Track request cho ImageView
- `clear()`: Clear request khi view recycled
- `pauseAll()`: Pause tất cả requests (khi scroll nhanh)
- `resumeVisibleOnly()`: Resume chỉ các views đang hiển thị

**Benefits:**

- Điều tiết request theo FPS hiện tại (adaptive delay)
- Chỉ load ảnh đang hiển thị
- Tránh memory leaks và cancel old requests
- Debounce 150ms trước khi resume

### 2.5 Image Transformations

#### **Transformation System**

`Transformation` interface cho phép xử lý bitmap trước khi hiển thị:

**Built-in Transformations:**

- `CenterCropRoundedCorners`: Center crop + bo góc
- Custom transformations: Extend `BaseTransformation`

**Key Features:**

- **Scale calculation**: Cover toàn bộ output (CSS `background-size: cover`)
- **Bitmap pooling**: Reuse bitmap để giảm allocations
- **Cache key**: Transform params được include trong cache key
- **Pipeline**: Transformations applied theo thứ tự

**Usage Example:**

```kotlin
ImageLoader.with(context)
    .load(url)
    .resize(800, 800)
    .transform(CenterCropRoundedCorners(32f))
    .into(imageView)
```

### 2.6 Real-time Logging System

#### **Architecture**

`ImageLoaderLogger` cung cấp logging system với UI viewer để debug và monitor:

**Log Entry Types:**

- **ImageLoadLog**: Track image loading metrics (source, timings, errors)
    - Source: MEMORY, DISK, NETWORK
    - Metrics: fetch time, decode time, transform time, total time
- **MessageLog**: General logging với levels và categories
    - Levels: VERBOSE, DEBUG, INFO, WARNING, ERROR
    - Categories: CACHE, NETWORK, IMAGE, DECODE, TRANSFORM

**LogViewer UI Features:**

- Real-time log updates với listener pattern
- Filter by categories
- Statistics dashboard với cache hit rates
- Performance metrics visualization

**Key Statistics:**

- **Cache Hit Rates**: Active + Memory + Disk cache hits
- **Performance Metrics**: Average timings per cache layer
- **Performance Insights**:
    - Memory hit rate > 80% → Cache size đủ lớn
    - Network average > 500ms → Network chậm
    - Decode time > 100ms → Ảnh quá lớn

**Access:** Tap Home title 5 times để mở LogViewer

### 2.7 Hệ Thống Disk Cache cho Photo Data (Multi-Page)

**Chiến lược lưu trữ:**

- **Storage**: File JSON trong external cache directory (`photo_backup.json`)
- **Structure**: `Map<Int, List<UnsplashPhoto>>` (page number → photos)
- **Expiration**: Tự động xóa sau 24 giờ (configurable via `AppConfig.CACHE_EXPIRY_HOURS`)
- **Data**: Map of pages + timestamp
- **Incremental saving**: Mỗi page mới được merge vào existing cache

**Luồng xử lý:**

1. **Initial Load**: Kiểm tra disk cache → Load tất cả pages đã lưu → Flatten thành list → Hiển thị ngay
2. **Load More**: Fetch network → Save page to disk incrementally → Accumulate pages
3. **Preload**: Background fetch → Save to memory cache + disk cache
4. **App Restart Offline**: Load tất cả pages từ disk (VD: scroll đến page 10 → restart offline → vẫn hiển thị đủ 10 pages)
5. **Refresh**: Clear cả memory + disk cache → Fetch page 1 → Save to disk

**Logging & Monitoring:**

- **ImageLoaderLogger integration**: Track số photos, số pages, max page
- **Load logs**: "Loaded from disk: X photos across Y pages (max page: Z)"
- **Save logs**: "Saved page N (M photos). Total cached: X photos across Y pages"
- **Stats**: `jsonPhotoCount` và `jsonCurrentPage` trong LogViewer statistics

**Lợi ích:**

- **Progressive offline support**: Scroll đến page 10 → offline → vẫn xem được 10 pages (không chỉ page 1)
- **Fast startup**: Load tức thì từ disk với toàn bộ pages đã scroll
- **Data persistence**: Tồn tại khi app bị kill
- **Bandwidth saving**: Giảm API calls không cần thiết
- **Seamless UX**: Smooth scroll experience với preloaded pages + disk cache fallback

### 2.8 Network Monitoring & Auto-Retry

#### **ConnectivityProvider**

Cung cấp monitoring trạng thái mạng với reactive Flow:

**Tính năng:**

- Cập nhật trạng thái mạng real-time
- States: Available, Unavailable, Losing, Lost
- Tích hợp trong ViewModel (không phải Activity)

#### **Error Handling System**

**Các loại AppError:**

- `NetworkError`: Lỗi kết nối (timeout, mất internet)
- `RateLimitError`: Vượt giới hạn API (mặc định retry sau 60s)
- `ServerError`: Lỗi server (500, 502, 503)
- `UnknownError`: Lỗi không xác định

**ErrorMapper**: Chuyển đổi exceptions → AppError types

#### **Chiến Lược Auto-Retry**

**Quy tắc retry:**

- **NetworkError**: Retry sau 3s (nếu đã online)
- **RateLimitError**: Retry sau thời gian `retryAfter`
- **ServerError 503**: Retry sau 5s
- **Lỗi khác**: Không auto-retry (hiển thị snackbar)

**Cài đặt:** ViewModel lên lịch retry với coroutine delay, kiểm tra network trước khi retry

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
    │   │   ├── config/
    │   │   │   ├── AppConfig.kt        # App configuration
    │   │   │   └── NetworkConfig.kt    # Network configuration
    │   │   ├── constants/
    │   │   │   └── Constants.kt        # API keys & base URL
    │   │   ├── customView/
    │   │   │   ├── FPSOverlay.kt       # FPS overlay widget
    │   │   │   └── RatioImageView.kt   # Aspect ratio image view
    │   │   ├── di/
    │   │   │   ├── AppContainer.kt     # Root DI container
    │   │   │   ├── NetworkModule.kt    # Network dependencies
    │   │   │   └── HomeModule.kt       # Home feature dependencies
    │   │   ├── error/
    │   │   │   └── ErrorHandler.kt     # Legacy error handler
    │   │   ├── helpers/
    │   │   │   └── Helpers.kt          # Utility helpers
    │   │   ├── platform/
    │   │   │   ├── ConnectivityProvider.kt     # Interface + Android impl
    │   │   │   ├── FileStorageProvider.kt      # Interface + Android impl
    │   │   │   └── NetworkStatus.kt            # Network status enum
    │   │   └── ui/
    │   │       └── base/
    │   │           ├── BaseActivity.kt         # Base activity
    │   │           └── BaseViewModel.kt        # Base ViewModel
    │   │
    │   ├── network/
    │   │   └── HttpClient.kt           # Custom HTTP client (không dùng Retrofit)
    │   │
    │   ├── shared/
    │   │   ├── result/
    │   │   │   └── Result.kt           # Result<T, E> wrapper
    │   │   └── error/
    │   │       └── ErrorMapper.kt      # Exception → AppError mapper
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
    │   │   │   │   ├── model/          # DTOs (Data Transfer Objects)
    │   │   │   │   │   ├── UnsplashPhotoDTO.kt
    │   │   │   │   │   ├── UnsplashUrlsDTO.kt
    │   │   │   │   │   ├── UnsplashLinksDTO.kt
    │   │   │   │   │   └── UnsplashUserDTO.kt
    │   │   │   │   ├── mapper/
    │   │   │   │   │   └── PhotoMapper.kt      # DTO → Domain mapper
    │   │   │   │   ├── source/
    │   │   │   │   │   ├── remote/
    │   │   │   │   │   │   └── UnsplashRemoteDataSource.kt
    │   │   │   │   │   └── local/
    │   │   │   │   │       ├── PhotoDiskCache.kt       # JSON file cache
    │   │   │   │   │       ├── PhotoMemoryCache.kt     # Preload cache
    │   │   │   │   │       └── PhotoLocalDataSource.kt # Facade
    │   │   │   │   └── repository/
    │   │   │   │       └── PhotoRepositoryImpl.kt
    │   │   │   │
    │   │   │   ├── domain/
    │   │   │   │   ├── model/
    │   │   │   │   │   ├── UnsplashPhoto.kt    # Pure Kotlin model
    │   │   │   │   │   ├── UnsplashUrls.kt
    │   │   │   │   │   ├── UnsplashLinks.kt
    │   │   │   │   │   ├── UnsplashUser.kt
    │   │   │   │   │   └── LoadPhotoResult.kt  # Use case result wrapper
    │   │   │   │   ├── repository/
    │   │   │   │   │   └── PhotoRepository.kt
    │   │   │   │   └── usecase/
    │   │   │   │       ├── LoadInitialPhotosUseCase.kt
    │   │   │   │       ├── RefreshPhotosUseCase.kt
    │   │   │   │       ├── LoadMorePhotosUseCase.kt
    │   │   │   │       ├── PreloadPhotosUseCase.kt
    │   │   │   │       └── GetCachedPhotosUseCase.kt
    │   │   │   │
    │   │   │   └── presentation/
    │   │   │       ├── HomeActivity.kt
    │   │   │       ├── HomeViewModel.kt
    │   │   │       ├── HomeViewModelFactory.kt
    │   │   │       ├── HomeIntent.kt           # MVI intents
    │   │   │       ├── HomeUiState.kt          # MVI UI states
    │   │   │       ├── adapter/
    │   │   │       │   └── PhotoAdapter.kt
    │   │   │       └── components/
    │   │   │           ├── PhotoGridManager.kt
    │   │   │           ├── ScrollLoadMoreHandler.kt
    │   │   │           └── SettingsBottomSheetHelper.kt
    │   │   │
    │   │   └── splash/
    │   │       └── SplashActivity.kt
    │   │
    │   └── MyApplication.kt            # Application class
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

**Cài đặt:** `LruCache<String, Bitmap>` với `sizeOf = allocationByteCount`

**Tính năng chính:**

- Tự động eviction khi đầy
- Evicted bitmaps → BitmapPool (tái sử dụng)
- Thread-safe operations

#### **4.2.3 DiskCache**

**Chiến lược lưu trữ:**

- Raw bytes với extension theo `contentType`
- Đặt tên file: `MD5(key).{jpg|png|webp|avif|dat}`
- Kích thước tối đa: 150 MB (mặc định)
- Atomic write: pattern `temp → rename`

**Trimming:** Tự động dọn dẹp file cũ theo `lastModified` khi vượt limit

#### **4.2.4 LruBitmapPool**

**Chiến lược tái sử dụng:**

- Lưu trữ mutable bitmaps trong `LinkedHashMap` (LRU)
- Ưu tiên: Exact match → Bitmap lớn hơn + reconfigure
- Bỏ qua bitmaps quá lớn (>50% pool)

**Trường hợp sử dụng:** RecyclerView, galleries, thiết bị RAM thấp

### 4.3 Fetcher & Decoder

#### **4.3.1 HttpFetcher**

**Tính năng:**

- `ConnectionFactory` interface (có thể test)
- Retry: Tối đa 2 lần với exponential backoff (700ms, 1400ms)
- Timeout: 5s connect + 5s read
- Logging qua `ImageLoaderLogger`

#### **4.3.2 BitmapDecoder**

**Chiến lược decode:**

- Đọc bounds trước (`inJustDecodeBounds`) → Tính `inSampleSize`
- Tái sử dụng `inBitmap` từ pool (fallback nếu thất bại)
- Trích xuất dominant color cho placeholder (Palette API)

**Downsampling:** Tự động giảm kích thước để tránh OOM

### 4.4 Transformation System

**Transformation Interface:** Biến đổi bitmap trước khi deliver

**Built-in:**

- `CenterCropRoundedCorners`: Scale + crop + bo góc
- `BaseTransformation`: Base class cho custom transforms

**Ví dụ custom:** Extend `BaseTransformation`, implement `transform()` và `key()`

**Yêu cầu chính:**

- `key()` duy nhất cho cache differentiation
- Sử dụng `BitmapPool` để tiết kiệm bộ nhớ
- Xử lý bitmap recycling đúng cách

### 4.5 Logger & Monitoring

Chi tiết xem Section 2.6 - Real-time Logging System

---

## 5. Chi Tiết App Module

### 5.1 Kiến Trúc Clean Architecture + MAD

App module áp dụng **Clean Architecture** kết hợp **Modern Android Development (MAD)** với 3 layers
rõ ràng:

```
Presentation Layer (Activity + ViewModel + State)
       ↓ (uses)
Domain Layer (Use Cases + Models + Repository Interface)
       ↓ (implements)
Data Layer (Repository Impl + DataSources + Mappers)
```

**Dependency Rule:**

- Presentation → Domain (chỉ biết Use Cases và Models)
- Data → Domain (implement Repository Interface)
- Domain hoàn toàn độc lập, không phụ thuộc bất kỳ layer nào

**Key Architectural Principles:**

- **Single Source of Truth**: UI state được quản lý tập trung trong ViewModel
- **Unidirectional Data Flow**: UI → Intent → ViewModel → State → UI
- **Separation of Concerns**: Mỗi layer có trách nhiệm riêng biệt
- **Dependency Inversion**: Layers phụ thuộc vào abstractions (interfaces), không phụ thuộc vào
  concrete implementations

**Benefits:**

- **Testability**: Dễ dàng mock dependencies cho unit tests
- **Independence**: UI, database, network có thể thay đổi mà không ảnh hưởng domain logic
- **Maintainability**: Code rõ ràng, dễ maintain và scale
- **MAD Compliance**: Tuân thủ các best practices của Google Android

### 5.2 Dependency Injection Architecture

#### **5.2.1 Cấu Trúc DI Container**

Project sử dụng **Manual Dependency Injection** với module-based approach:

**AppContainer** (Root):

- Platform providers (ConnectivityProvider, FileStorageProvider)
- NetworkModule (HttpClient)
- Feature modules (HomeModule)

**HomeModule** cung cấp:

- Mappers (PhotoMapper, ErrorMapper)
- Data sources (Remote + Local)
- Repository implementation
- 5 Use cases
- ViewModelFactory

**Lợi ích:**

- Cô lập scope theo module
- Khởi tạo lazy
- Type-safe tại compile-time
- Dễ test (swap implementations)
- Không có overhead của reflection

### 5.3 Domain Layer

#### **5.3.1 Domain Models**

Domain layer sử dụng **pure Kotlin data classes**:

**Nguyên tắc chính:**

- Không có framework annotations (`@Serializable`, `@Json`)
- Immutable (properties dùng `val`)
- Xử lý null rõ ràng
- Độc lập với platform

**Models:** `UnsplashPhoto`, `UnsplashUrls`, `UnsplashLinks`, `UnsplashUser`, `LoadPhotoResult`

#### **5.3.2 Repository Interface**

```kotlin
interface PhotoRepository {
    suspend fun loadInitialPhotos(): Result<LoadPhotoResult, AppError>
    suspend fun refreshPhotos(): Result<List<UnsplashPhoto>, AppError>
    suspend fun loadMorePhotos(page: Int): Result<List<UnsplashPhoto>, AppError>
    suspend fun preloadPhotos(page: Int): Result<Unit, AppError>
    suspend fun getCachedPhotos(): Result<List<UnsplashPhoto>, AppError>
}
```

**Result Type:**

```kotlin
sealed class Result<out T, out E> {
    data class Success<T>(val data: T) : Result<T, Nothing>()
    data class Error<E>(val error: E) : Result<Nothing, E>()
}
```

**LoadPhotoResult:**

```kotlin
data class LoadPhotoResult(
    val photos: List<UnsplashPhoto>,
    val currentPage: Int,
    val isFromCache: Boolean
)
```

#### **5.3.3 Use Cases**

Mỗi use case = 1 business operation theo **Single Responsibility Principle**:

| Use Case              | Chiến lược                    | Sử dụng          |
|-----------------------|-------------------------------|------------------|
| **LoadInitialPhotos** | Disk cache → Network          | Load ban đầu     |
| **RefreshPhotos**     | Force network, bỏ qua cache   | Pull-to-refresh  |
| **LoadMorePhotos**    | Preloaded cache → Network     | Phân trang       |
| **PreloadPhotos**     | Silent network → Memory cache | Prefetch nền     |
| **GetCachedPhotos**   | Chỉ disk cache                | Fallback offline |

**Lợi ích:** Đơn trách nhiệm, có thể test, tái sử dụng, composable

### 5.4 Data Layer

#### **5.4.1 Data Models (DTOs)**

Data layer sử dụng DTOs với `@Serializable` cho network deserialization:

```kotlin
@Serializable
data class UnsplashPhotoDTO(
    val id: String,
    val created_at: String,
    val width: Int,
    val height: Int,
    val color: String? = null,
    val likes: Int = 0,
    val description: String? = null,
    val urls: UnsplashUrlsDTO,
    val links: UnsplashLinksDTO,
    val user: UnsplashUserDTO
)

@Serializable
data class UnsplashUrlsDTO(
    val raw: String? = null,
    val full: String? = null,
    val regular: String? = null,
    val small: String? = null,
    val thumb: String? = null,
    val medium: String? = null,
    val large: String? = null
)
```

**DTOs vs Domain Models:**

- **DTOs**: Chứa `@Serializable`, nullable với defaults, dùng cho network layer
- **Domain Models**: Pure Kotlin, immutable, không có framework dependencies
- **Mapper**: Convert DTO → Domain model

#### **5.4.2 PhotoMapper**

```kotlin
class PhotoMapper {
    fun toDomain(dto: UnsplashPhotoDTO): UnsplashPhoto {
        return UnsplashPhoto(
            id = dto.id,
            created_at = dto.created_at,
            width = dto.width,
            height = dto.height,
            color = dto.color,
            likes = dto.likes,
            description = dto.description,
            alt_description = null,
            urls = toDomain(dto.urls),
            links = toDomain(dto.links),
            user = toDomain(dto.user)
        )
    }

    fun toDomainList(dtos: List<UnsplashPhotoDTO>): List<UnsplashPhoto> {
        return dtos.map { toDomain(it) }
    }
}
```

**Benefits:**

- **Separation**: Network models tách biệt với domain models
- **Null safety**: DTOs có defaults, domain models explicit nullability
- **Evolution**: Có thể thay đổi API response mà không ảnh hưởng domain

#### **5.4.3 Data Sources Pattern**

**UnsplashRemoteDataSource:**

```kotlin
class UnsplashRemoteDataSource(
    private val httpClient: HttpClient
) : BaseApiService(httpClient) {

    suspend fun getPhotos(page: Int, perPage: Int): List<UnsplashPhotoDTO> {
        return get(
            path = "photos",
            query = mapOf(
                "page" to page.toString(),
                "per_page" to perPage.toString()
            ),
            deserializer = ListSerializer(UnsplashPhotoDTO.serializer())
        )
    }
}
```

**PhotoLocalDataSource:**

```kotlin
class PhotoLocalDataSource(
    private val diskCache: PhotoDiskCache,
    private val memoryCache: PhotoMemoryCache
) {
    /**
     * Load tất cả cached photos từ disk.
     * Returns CachedPhotoData với pages map.
     */
    suspend fun getCachedPhotos(): CachedPhotoData? {
        return diskCache.loadBackup()
    }
    
    /**
     * Save một page vào disk cache (incremental).
     */
    suspend fun savePage(page: Int, photos: List<UnsplashPhoto>) {
        diskCache.savePage(page, photos)
    }
    
    suspend fun clearDiskCache() {
        diskCache.clearBackup()
    }
    
    // Memory cache (preload) operations
    fun getPreloadedPage(page: Int): List<UnsplashPhoto>? {
        return memoryCache.getPage(page)
    }
    
    fun savePreloadedPage(page: Int, photos: List<UnsplashPhoto>) {
        memoryCache.savePage(page, photos)
    }
    
    fun hasPreloadedPage(page: Int): Boolean {
        return memoryCache.hasPage(page)
    }
    
    fun clearMemoryCache() {
        memoryCache.clear()
    }
    
    fun clearOldPreloadedPages(currentPage: Int) {
        memoryCache.clearOldPages(currentPage)
    }
}
```

**PhotoDiskCache:**

```kotlin
class PhotoDiskCache(
    private val fileStorageProvider: FileStorageProvider
) {
    private val gson = Gson()
    
    companion object {
        private const val BACKUP_FILE_NAME = "photo_backup.json"
        private const val TAG = "PhotoDiskCache"
    }
    
    /**
     * Save hoặc update một page vào disk cache.
     * Merge với data hiện có (nếu có).
     */
    suspend fun savePage(page: Int, photos: List<UnsplashPhoto>) {
        withContext(Dispatchers.IO) {
            try {
                val existing = loadBackupInternal()
                val pagesMap = existing?.pages?.toMutableMap() ?: mutableMapOf()
                
                pagesMap[page] = photos
                
                val backup = CachedPhotoData(
                    pages = pagesMap,
                    timestamp = System.currentTimeMillis()
                )
                
                val json = gson.toJson(backup)
                fileStorageProvider.writeTextFile(BACKUP_FILE_NAME, json)
                
                val totalPhotos = pagesMap.values.sumOf { it.size }
                ImageLoaderLogger.d(TAG, "Saved page $page (${photos.size} photos). Total cached: $totalPhotos photos across ${pagesMap.size} pages")
            } catch (e: Exception) {
                ImageLoaderLogger.e(TAG, "Failed to save page $page", e)
            }
        }
    }
    
    /**
     * Load tất cả pages từ disk cache.
     * Returns CachedPhotoData với pages map.
     */
    suspend fun loadBackup(): CachedPhotoData? {
        return withContext(Dispatchers.IO) {
            try {
                val backup = loadBackupInternal() ?: return@withContext null
                
                if (isCacheExpired(backup.timestamp)) {
                    ImageLoaderLogger.d(TAG, "Cache expired, clearing")
                    fileStorageProvider.deleteFile(BACKUP_FILE_NAME)
                    return@withContext null
                }
                
                val totalPhotos = backup.pages.values.sumOf { it.size }
                val maxPage = backup.pages.keys.maxOrNull() ?: 0
                
                ImageLoaderLogger.jsonPhotoCount = totalPhotos
                ImageLoaderLogger.jsonCurrentPage = maxPage
                
                ImageLoaderLogger.i(TAG, "Loaded from disk: $totalPhotos photos across ${backup.pages.size} pages (max page: $maxPage)")
                
                backup
            } catch (e: Exception) {
                ImageLoaderLogger.e(TAG, "Failed to load backup", e)
                null
            }
        }
    }
    
    private fun isCacheExpired(timestamp: Long): Boolean {
        val expiryTime = AppConfig.CACHE_EXPIRY_HOURS * 60 * 60 * 1000
        return System.currentTimeMillis() - timestamp > expiryTime
    }
}

/**
 * CachedPhotoData - Structure lưu trữ pages trong disk cache.
 * 
 * @param pages Map từ page number -> list photos
 * @param timestamp Thời điểm cache được tạo (để check expiry)
 */
data class CachedPhotoData(
    val pages: Map<Int, List<UnsplashPhoto>>,
    val timestamp: Long
) {
    /**
     * Flatten tất cả pages thành single sorted list.
     */
    fun getAllPhotos(): List<UnsplashPhoto> {
        return pages.toSortedMap().values.flatten()
    }
}
```

**PhotoMemoryCache:**

```kotlin
class PhotoMemoryCache {
    private val cache = mutableMapOf<Int, List<UnsplashPhoto>>()

    fun put(page: Int, photos: List<UnsplashPhoto>) {
        synchronized(cache) {
            cache[page] = photos
            cleanupOldPages(page)
        }
    }

    fun get(page: Int): List<UnsplashPhoto>? {
        return synchronized(cache) {
            cache[page]
        }
    }

    private fun cleanupOldPages(currentPage: Int) {
        val keysToRemove = cache.keys.filter { it < currentPage - 1 }
        keysToRemove.forEach { cache.remove(it) }
    }
}
```

**Benefits of Data Sources Pattern:**

- **Single Responsibility**: Remote chỉ lo network, Local chỉ lo cache
- **Testability**: Dễ mock từng data source
- **Flexibility**: Có thể swap implementations (e.g., Room database)

#### **5.4.4 PhotoRepositoryImpl**

```kotlin
class PhotoRepositoryImpl(
    private val remoteDataSource: UnsplashRemoteDataSource,
    private val localDataSource: PhotoLocalDataSource,
    private val photoMapper: PhotoMapper,
    private val errorMapper: ErrorMapper
) : PhotoRepository {

    override suspend fun loadInitialPhotos(): Result<LoadPhotoResult, AppError> {
        return try {
            // Try disk cache first (load all cached pages)
            val cachedData = localDataSource.getCachedPhotos()
            if (cachedData != null && cachedData.pages.isNotEmpty()) {
                return Result.Success(
                    LoadPhotoResult(
                        photos = cachedData.getAllPhotos(), // Flatten all pages
                        currentPage = cachedData.pages.keys.maxOrNull() ?: 1,
                        isFromCache = true
                    )
                )
            }

            // Fetch from network
            val photosDTO = remoteDataSource.getPhotos(page = 1, perPage = AppConfig.PER_PAGE)
            val photos = photoMapper.toDomainList(photosDTO)

            // Save page 1 to disk cache
            localDataSource.savePage(page = 1, photos = photos)

            Result.Success(
                LoadPhotoResult(
                    photos = photos,
                    currentPage = 1,
                    isFromCache = false
                )
            )
        } catch (e: Exception) {
            Result.Error(errorMapper.mapError(e))
        }
    }

    override suspend fun loadMorePhotos(page: Int): Result<List<UnsplashPhoto>, AppError> {
        return try {
            // Check memory cache (preloaded) first
            val cachedPhotos = localDataSource.getPreloadedPage(page)
            if (cachedPhotos != null) {
                return Result.Success(cachedPhotos)
            }

            // Fetch from network
            val photosDTO = remoteDataSource.getPhotos(page, AppConfig.PER_PAGE)
            val photos = photoMapper.toDomainList(photosDTO)

            // Save page to disk for offline support
            localDataSource.savePage(page = page, photos = photos)

            Result.Success(photos)
        } catch (e: Exception) {
            Result.Error(errorMapper.mapError(e))
        }
    }

    override suspend fun preloadPhotos(page: Int): Result<Unit, AppError> {
        return try {
            // Skip if already preloaded in memory
            if (localDataSource.hasPreloadedPage(page)) {
                return Result.Success(Unit)
            }
            
            val photosDTO = remoteDataSource.getPhotos(page, AppConfig.PER_PAGE)
            val photos = photoMapper.toDomainList(photosDTO)
            
            localDataSource.savePreloadedPage(page, photos)
            localDataSource.clearOldPreloadedPages(page)
            
            // Also save to disk for offline support
            localDataSource.savePage(page = page, photos = photos)
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(errorMapper.mapError(e))
        }
    }
}
```

**Repository Strategy:**

- **loadInitialPhotos**: Disk cache (all pages) → Network → Save page 1 to disk
- **loadMorePhotos**: Memory cache (preload) → Network → **Save page to disk incrementally**
- **refreshPhotos**: Force network → Clear memory + disk → Save page 1 to disk
- **preloadPhotos**: Silent network → Memory cache + **Save page to disk**
- **getCachedPhotos**: Disk cache only (offline) → Returns flattened list of all cached pages

#### **5.4.5 HttpClient & BaseApiService**

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

**Why HttpClient?**

1. **Không phụ thuộc Retrofit/OkHttp stack**: Luồng chính chỉ dùng `HttpURLConnection`
2. **Lightweight**: HttpURLConnection là built-in Android
3. **Full Control**: Tự quản lý connections, headers, timeouts
4. **Learning Purpose**: Hiểu rõ HTTP protocol và request/response handling
5. **No external dependencies**: Giảm APK size

#### **5.4.6 Error Handling System**

**AppError Types:**

```kotlin
sealed class AppError {
    data class NetworkError(val message: String) : AppError()
    data class RateLimitError(val retryAfter: Long = 60_000) : AppError()
    data class ServerError(val code: Int, val message: String) : AppError()
    data class UnknownError(val message: String) : AppError()
}
```

**ErrorMapper:**

```kotlin
class ErrorMapper {
    fun mapError(throwable: Throwable): AppError {
        return when (throwable) {
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
    }
}
```

### 5.5 Presentation Layer (MVI Pattern)

#### **5.5.1 MVI Architecture**

App module áp dụng **MVI (Model-View-Intent)** pattern:

```
User Action → Intent → ViewModel → State → UI Render
     ↑                                            ↓
     └──────────────── User sees result ──────────┘
```

**Benefits:**

- **Unidirectional data flow**: Dễ debug và trace
- **Predictable state**: State là single source of truth
- **Testability**: Dễ test state transitions
- **Time travel debugging**: Có thể replay states

#### **5.5.2 HomeIntent**

```kotlin
sealed class HomeIntent {
    object LoadInitial : HomeIntent()
    object Refresh : HomeIntent()
    object LoadMore : HomeIntent()
    object ClearError : HomeIntent()
}
```

**Intent Types:**

- **LoadInitial**: Load trang đầu tiên (from cache or network)
- **Refresh**: Pull-to-refresh gesture
- **LoadMore**: Scroll to bottom pagination
- **ClearError**: User acknowledged error (dismiss snackbar)

#### **5.5.3 HomeUiState**

```kotlin
sealed interface HomeUiState {
    object Loading : HomeUiState

    data class Content(
        val photos: List<UnsplashPhoto>,
        val currentPage: Int = 1,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val isFromCache: Boolean = false,
        val error: AppError? = null,
        val networkStatus: NetworkStatus = NetworkStatus.Available
    ) : HomeUiState

    data class Error(
        val error: AppError,
        val hasBackupData: Boolean = false,
        val networkStatus: NetworkStatus = NetworkStatus.Available
    ) : HomeUiState
}
```

**State Hierarchy:**

- **Loading**: Initial loading state (showing progress)
- **Content**: Success state with data (can have temporary error)
- **Error**: Fatal error state (no data to show)

**Content State Properties:**

- `photos`: Current photo list
- `currentPage`: Current pagination page
- `isRefreshing`: Pull-to-refresh in progress
- `isLoadingMore`: Pagination loading
- `isFromCache`: Data loaded from disk cache
- `error`: Temporary error (snackbar)
- `networkStatus`: Current network status

#### **5.5.4 HomeViewModel**

```kotlin
class HomeViewModel(
    private val loadInitialPhotosUseCase: LoadInitialPhotosUseCase,
    private val refreshPhotosUseCase: RefreshPhotosUseCase,
    private val loadMorePhotosUseCase: LoadMorePhotosUseCase,
    private val preloadPhotosUseCase: PreloadPhotosUseCase,
    private val getCachedPhotosUseCase: GetCachedPhotosUseCase,
    private val connectivityProvider: ConnectivityProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentPage = 1
    private var isLoading = false
    private var currentNetworkStatus: NetworkStatus = NetworkStatus.Available

    init {
        observeNetworkStatus()
    }

    fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.LoadInitial -> loadInitialPhotos()
            HomeIntent.Refresh -> refreshPhotos()
            HomeIntent.LoadMore -> loadMorePhotos()
            HomeIntent.ClearError -> clearError()
        }
    }

    private fun observeNetworkStatus() {
        viewModelScope.launch {
            connectivityProvider.observeNetworkStatus().collect { status ->
                if (currentNetworkStatus != status) {
                    currentNetworkStatus = status
                    updateNetworkStatusInState(status)
                }
            }
        }
    }
}
```

**Key Features:**

- **Single entry point**: `handleIntent()` for all user actions
- **Network monitoring**: Observe connectivity in ViewModel
- **State deduplication**: Only emit state when changed
- **Coroutine management**: ViewModelScope for auto cleanup

**State Transition Examples:**

```
LoadInitial Intent:
Loading → Content(photos, isFromCache=true) [if cache hit]
Loading → Content(photos, isFromCache=false) [if network]
Loading → Error(error, hasBackupData=true) [if error + cache]
Loading → Error(error, hasBackupData=false) [if error + no cache]

Refresh Intent:
Content → Content(isRefreshing=true) → Content(new photos, isRefreshing=false)

LoadMore Intent:
Content → Content(isLoadingMore=true) → Content(photos + new, isLoadingMore=false)
```

#### **5.5.5 HomeViewModelFactory**

```kotlin
class HomeViewModelFactory(
    private val loadInitialPhotosUseCase: LoadInitialPhotosUseCase,
    private val refreshPhotosUseCase: RefreshPhotosUseCase,
    private val loadMorePhotosUseCase: LoadMorePhotosUseCase,
    private val preloadPhotosUseCase: PreloadPhotosUseCase,
    private val getCachedPhotosUseCase: GetCachedPhotosUseCase,
    private val connectivityProvider: ConnectivityProvider
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(
                loadInitialPhotosUseCase,
                refreshPhotosUseCase,
                loadMorePhotosUseCase,
                preloadPhotosUseCase,
                getCachedPhotosUseCase,
                connectivityProvider
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
```

#### **5.5.6 HomeActivity Integration**

**Activity Setup:**

```kotlin
class HomeActivity : AppCompatActivity() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var adapter: PhotoAdapter
    private lateinit var gridManager: PhotoGridManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // DI setup
        val appContainer = (application as MyApplication).appContainer
        val factory = appContainer.homeModule.createHomeViewModelFactory()
        viewModel = ViewModelProvider(this, factory)[HomeViewModel::class.java]

        // UI setup
        setupRecyclerView()
        setupSwipeRefresh()
        setupObservers()

        // Initial load
        viewModel.handleIntent(HomeIntent.LoadInitial)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is HomeUiState.Loading -> showLoading()
                    is HomeUiState.Content -> showContent(state)
                    is HomeUiState.Error -> showError(state)
                }
            }
        }
    }

    private fun showContent(state: HomeUiState.Content) {
        adapter.submitList(state.photos)
        swipeRefresh.isRefreshing = state.isRefreshing

        // Show error snackbar if present
        state.error?.let { error ->
            showErrorSnackbar(error)
            viewModel.handleIntent(HomeIntent.ClearError)
        }

        // Update network status bar
        updateNetworkStatusBar(state.networkStatus)
    }
}
```

**Key Integration Points:**

1. **ViewModel Creation**: Sử dụng `HomeViewModelFactory` từ `AppContainer.homeModule`
2. **Intent Handling**: User actions → `viewModel.handleIntent()`
3. **State Observation**: Collect `StateFlow` và render UI tương ứng
4. **Lifecycle Aware**: Sử dụng `lifecycleScope` cho coroutines

**RecyclerView Integration:**

```kotlin
private fun setupRecyclerView() {
    adapter = PhotoAdapter { currentSpanCount }

    recyclerView.apply {
        layoutManager = GridLayoutManager(context, currentSpanCount)
        adapter = this@HomeActivity.adapter
        itemAnimator = null // Tránh glitch shimmer
    }

    // Scroll listener for load more
    ScrollLoadMoreHandler(recyclerView) {
        viewModel.handleIntent(HomeIntent.LoadMore)
    }

    // Request throttling
    recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
            when (newState) {
                RecyclerView.SCROLL_STATE_SETTLING -> RequestManager.pauseAll()
                RecyclerView.SCROLL_STATE_IDLE -> {
                    val visibleViews = collectVisibleImageViews()
                    RequestManager.resumeVisibleOnly(visibleViews)
                }
            }
        }
    })
}
```

**Refresh Integration:**

```kotlin
private fun setupSwipeRefresh() {
    swipeRefresh.setOnRefreshListener {
        viewModel.handleIntent(HomeIntent.Refresh)
    }
}
```

**Activity Responsibilities:**

- **ViewModel integration**: Factory creation từ DI container
- **Intent dispatching**: User actions → Intent → ViewModel
- **State rendering**: StateFlow collection → UI updates
- **Image loading optimization**: RequestManager throttling
- **Network status**: Display connectivity bar
- **Developer tools**: FPSOverlay, LogViewer access

#### **5.5.7 PhotoAdapter**

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
