package com.example.imageloader.core

import com.example.imageloader.transformation.Transformation

/**
 * Request - Immutable data class chứa tất cả config cho một image load request.
 *
 * Request được tạo bởi RequestBuilder và truyền qua Engine để xử lý.
 * Tất cả fields đều immutable để đảm bảo thread-safety.
 *
 * ## Key Concepts:
 * - **resize**: Kích thước decode (downsampling khi decode để tiết kiệm RAM)
 * - **outWidth/Height**: Kích thước output sau transform (cho transformations)
 * - **transformations**: Chain of transformations (crop, blur, round corners, etc.)
 *
 * ## Example:
 * ```kotlin
 * Request(
 *     url = "https://example.com/image.jpg",
 *     resizeWidth = 1000,
 *     resizeHeight = 1000,
 *     transformations = listOf(CenterCropTransformation(), RoundedCornersTransformation(16f)),
 *     useMemoryCache = true,
 *     useDiskCache = true
 * )
 * ```
 *
 * @param url URL của ảnh cần load
 * @param resizeWidth Width để decode (downsampling), null = giữ nguyên
 * @param resizeHeight Height để decode (downsampling), null = giữ nguyên
 * @param useMemoryCache Có cache trong RAM hay không (mặc định: true)
 * @param useDiskCache Có cache trên disk hay không (mặc định: true)
 * @param transformations Danh sách transformations áp dụng lên bitmap
 * @param outWidth Target width cho transformations
 * @param outHeight Target height cho transformations
 * @param enableShimmer Có hiển thị shimmer effect khi loading hay không
 */
data class Request(
    val url: String,
    val resizeWidth: Int? = null,
    val resizeHeight: Int? = null,
    val useMemoryCache: Boolean = true,
    val useDiskCache: Boolean = true,
    val transformations: List<Transformation> = emptyList(),
    val outWidth: Int? = null,
    val outHeight: Int? = null,
    val enableShimmer: Boolean = false,
)
