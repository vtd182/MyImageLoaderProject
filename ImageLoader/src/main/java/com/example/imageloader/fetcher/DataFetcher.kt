package com.example.imageloader.fetcher

/**
 * DataFetcher - Interface định nghĩa contract cho việc fetch dữ liệu từ remote sources.
 *
 * ## Mục đích:
 * Abstraction layer cho data fetching operations:
 * - Decouple Engine khỏi network implementation details
 * - Cho phép test với mock fetchers
 * - Hỗ trợ nhiều loại data sources (HTTP, File, Assets, etc.)
 * - Flexible để thay đổi network implementation
 *
 * ## Implementation:
 * Thư viện cung cấp `HttpFetcher` - default implementation dùng HttpURLConnection.
 *
 * ## Use cases:
 *
 * ### Production:
 * ```kotlin
 * val fetcher = HttpFetcher(
 *     maxRetries = 2,
 *     retryDelayMillis = 700
 * )
 * ```
 *
 * ### Testing:
 * ```kotlin
 * class MockFetcher : DataFetcher {
 *     override suspend fun fetch(url: String): HttpResult {
 *         return HttpResult(mockBytes, "image/jpeg")
 *     }
 * }
 * ```
 *
 * ### Custom implementation:
 * ```kotlin
 * class OkHttpFetcher(private val client: OkHttpClient) : DataFetcher {
 *     override suspend fun fetch(url: String): HttpResult {
 *         val response = client.newCall(Request.Builder().url(url).build()).execute()
 *         return HttpResult(response.body?.bytes() ?: byteArrayOf(), response.header("Content-Type"))
 *     }
 * }
 * ```
 *
 * ## Thread-safety:
 * - Implementation phải suspend-safe
 * - Có thể được gọi từ multiple coroutines đồng thời
 *
 * @see com.example.imageloader.fetcher.HttpFetcher
 * @see com.example.imageloader.fetcher.HttpResult
 */
interface DataFetcher {
    /**
     * Fetch dữ liệu từ URL.
     *
     * ## Responsibilities:
     * - Download raw bytes từ URL
     * - Detect Content-Type từ response headers
     * - Handle errors và throw exception nếu fail
     *
     * ## Error handling:
     * Implementation nên:
     * - Throw meaningful exceptions
     * - Include error details (HTTP code, message)
     * - Support retry logic (nếu cần)
     *
     * ## Performance:
     * - Typical time: 100-2000ms (network dependent)
     * - Should support cancellation (coroutine cancellation)
     *
     * @param url URL của resource cần fetch
     * @return HttpResult chứa bytes và contentType
     * @throws Exception nếu fetch failed (network error, timeout, HTTP error)
     */
    suspend fun fetch(url: String): HttpResult
}
