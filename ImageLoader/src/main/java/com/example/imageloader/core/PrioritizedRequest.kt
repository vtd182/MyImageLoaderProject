package com.example.imageloader.core

import com.example.imageloader.core.enums.RequestPriority
import com.example.imageloader.target.Target
import kotlinx.coroutines.Job

/**
 * PrioritizedRequest - Wrapper kết hợp Request với metadata để xử lý trong priority queue.
 *
 * Data class này đóng gói tất cả thông tin cần thiết để Engine xử lý một request:
 * - Request config (URL, transformations, etc.)
 * - Target để deliver kết quả
 * - Priority level để quyết định xử lý nhanh/chậm
 * - Job để có thể cancel request
 *
 * ## Use case:
 * ```
 * User scroll RecyclerView:
 * - Visible items → HIGH priority → xử lý ngay
 * - Off-screen items → LOW priority → xử lý sau
 * - User scroll lên → cancel LOW priority requests → tránh lãng phí
 * ```
 *
 * @param request Request chứa URL và config
 * @param target Target nhận kết quả (ImageView, callback, etc.)
 * @param priority Mức ưu tiên (HIGH, NORMAL, LOW)
 * @param job Coroutine Job để cancel request nếu cần
 */
data class PrioritizedRequest(
    val request: Request,
    val target: Target,
    val priority: RequestPriority,
    val job: Job
)