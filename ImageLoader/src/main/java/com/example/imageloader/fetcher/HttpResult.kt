package com.example.imageloader.fetcher

/**
 * HttpResult - Data class chứa kết quả của một HTTP fetch operation.
 *
 * ## Components:
 * - **bytes**: Raw image data (JPEG, PNG, WebP, etc.)
 * - **contentType**: MIME type từ HTTP response header
 *
 * ## Use cases:
 *
 * ### Engine sử dụng:
 * ```kotlin
 * val result = fetcher.fetch(url)
 * val bytes = result.bytes           // Raw data để decode
 * val contentType = result.contentType // "image/jpeg", "image/png", etc.
 * 
 * // Decode
 * val bitmap = BitmapDecoder.decode(bytes, reqW, reqH)
 * 
 * // Cache với extension từ contentType
 * diskCache.put(key, bytes, contentType)
 * ```
 *
 * ## ContentType importance:
 * ContentType được dùng để:
 * 1. Determine file extension khi save vào Disk Cache
 * 2. Debug/logging purposes
 * 3. Future: Smart decode strategy based on format
 *
 * ## ByteArray override:
 * Data class với ByteArray cần custom equals/hashCode:
 * - Default equals so sánh reference, không so sánh content
 * - Override dùng `contentEquals()` để so sánh actual data
 * - Quan trọng cho testing và data comparison
 *
 * @param bytes Raw image data bytes
 * @param contentType MIME type từ HTTP Content-Type header (nullable)
 *
 * @see com.example.imageloader.fetcher.DataFetcher
 * @see com.example.imageloader.fetcher.HttpFetcher
 */
data class HttpResult(
    val bytes: ByteArray,
    val contentType: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HttpResult

        if (!bytes.contentEquals(other.bytes)) return false
        if (contentType != other.contentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + (contentType?.hashCode() ?: 0)
        return result
    }
}