package com.example.imageloader.core.enums

/**
 * RequestPriority - Enum định nghĩa các mức độ ưu tiên cho image load requests.
 *
 * ## Mục đích:
 * Priority system giúp tối ưu user experience bằng cách:
 * - Load ảnh quan trọng trước (visible items)
 * - Trì hoãn ảnh ít quan trọng (off-screen items)
 * - Cải thiện perceived performance
 *
 * ## Engine behavior:
 * - **HIGH**: 2 worker threads xử lý song song → throughput cao
 * - **NORMAL**: 1 worker thread → balanced
 * - **LOW**: 1 worker thread, được xử lý sau HIGH và NORMAL
 *
 * ## Use cases:
 *
 * ### HIGH Priority:
 * - Ảnh trong viewport, đang visible
 * - Hero images, banners chính
 * - User action triggered (tap vào xem detail)
 * - Ảnh critical cho UX
 *
 * ```kotlin
 * ImageLoader.with(context)
 *     .load(heroImageUrl)
 *     .priority(RequestPriority.HIGH)
 *     .into(heroImageView)
 * ```
 *
 * ### NORMAL Priority (default):
 * - Ảnh trong viewport nhưng không urgent
 * - Ảnh sắp vào viewport (next items trong list)
 * - General content images
 *
 * ```kotlin
 * ImageLoader.with(context)
 *     .load(imageUrl)
 *     // .priority(RequestPriority.NORMAL) // default, có thể bỏ qua
 *     .into(imageView)
 * ```
 *
 * ### LOW Priority:
 * - Prefetch cho off-screen items
 * - Thumbnails xa viewport
 * - Background/decorative images
 * - Non-critical content
 *
 * ```kotlin
 * ImageLoader.with(context)
 *     .load(thumbnailUrl)
 *     .priority(RequestPriority.LOW)
 *     .into(thumbnailView)
 * ```
 *
 * ## RecyclerView best practice:
 * ```kotlin
 * override fun onBindViewHolder(holder: ViewHolder, position: Int) {
 *     val priority = if (isItemVisible(position)) {
 *         RequestPriority.HIGH
 *     } else {
 *         RequestPriority.LOW
 *     }
 *
 *     ImageLoader.with(context)
 *         .load(item.imageUrl)
 *         .priority(priority)
 *         .into(holder.imageView)
 * }
 * ```
 *
 * ## Performance impact:
 * ```
 * Không có Priority:
 * [Ảnh 1][Ảnh 2][Ảnh 3][Ảnh 4]... → load tuần tự → user thấy delay
 *
 * Có Priority:
 * HIGH: [Ảnh visible 1][Ảnh visible 2] → load nhanh
 * LOW:  [Ảnh off-screen]... → load sau → smooth UX
 * ```
 *
 * @see com.example.imageloader.core.Engine
 * @see com.example.imageloader.core.RequestBuilder.priority
 */
enum class RequestPriority {
    /** Ưu tiên cao nhất - 2 workers xử lý song song */
    HIGH,
    
    /** Ưu tiên thường - 1 worker, default priority */
    NORMAL,
    
    /** Ưu tiên thấp - 1 worker, xử lý sau cùng */
    LOW
}