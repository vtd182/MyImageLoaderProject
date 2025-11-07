package com.example.imageloader.target

import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.view.doOnAttach
import androidx.core.view.doOnDetach
import com.example.imageloader.core.EngineResource
import com.example.imageloader.drawable.ShimmerDrawable
import com.example.imageloader.logger.ImageLoaderLogger
import com.example.imageloader.logger.LogCategory

/**
 * ImageViewTarget:
 * ----------------
 * Target dùng cho ImageView, chịu trách nhiệm gán tài nguyên (EngineResource)
 * vào ImageView và quản lý vòng đời của resource theo vòng đời thực tế của View.
 *
 * Tính năng:
 *  - Hiển thị bitmap khi load thành công.
 *  - Hiển thị shimmer trong lúc load (nếu bật).
 *  - Hiển thị error drawable + retry khi load thất bại.
 *  - QUẢN LÝ BỘ NHỚ: release() resource đúng thời điểm dựa vào lifecycle của View.
 *
 * ## TẠI SAO PHẢI QUẢN LÝ VÒNG ĐỜI RESOURCE?
 *
 * EngineResource là tài nguyên có **đếm số tham chiếu** (reference counting):
 * - `acquire()` khi ImageView bắt đầu sử dụng bitmap
 * - `release()` khi ImageView không còn hiển thị bitmap nữa
 *
 * Nếu quên gọi release():
 * - rò rỉ bộ nhớ (memory leak)
 * - hoặc reuse sai bitmap → crash “Canvas: trying to use a recycled bitmap”
 *
 *
 * ## Vì sao phải dùng kỹ thuật “lồng doOnAttach → doOnDetach”?
 *
 * Đây là kỹ thuật QUAN TRỌNG khi làm ImageLoader trong RecyclerView.
 *
 * - `doOnDetach {}` **CHỈ kích hoạt nếu View đang ở trạng thái attached** tại thời điểm đăng ký.
 * - Nhưng trong RecyclerView:
 *      - ViewHolder được bind sớm → view CHƯA ATTACH
 *      - onResourceReady() chạy khi view vẫn chưa attach
 *      - Nếu gọi doOnDetach lúc này → KHÔNG BAO GIỜ đăng ký → KHÔNG BAO GIỜ release()
 *
 * Kết quả: leak bộ nhớ hoặc crash.
 *
 * ✅ Giải pháp đúng:
 *
 * ```
 * imageView.doOnAttach {
 *     it.doOnDetach {
 *         current?.release()
 *     }
 * }
 * ```
 *
 * Ý nghĩa:
 * - `doOnAttach` đảm bảo code bên trong chỉ chạy khi view đã attach.
 * - Lúc đó, `doOnDetach` đăng ký thành công listener.
 * - Khi view bị tách khỏi window (detach → recycled) → release().
 *
 *
 * @param imageView ImageView được gán ảnh.
 * @param errorDrawable Drawable sẽ hiển thị khi load thất bại.
 * @param enableShimmer Bật shimmer placeholder khi đang load.
 */
class ImageViewTarget(
    private val imageView: ImageView,
    private val errorDrawable: Drawable? = null,
    private val enableShimmer: Boolean = false
) : Target {

    /** EngineResource hiện đang gán vào ImageView. */
    private var current: EngineResource? = null

    /** Hàm gọi lại khi user nhấn retry. */
    private var retryCallback: (() -> Unit)? = null

    /** Hỗ trợ delay loading indicator. */
    private val loadingHandler = Handler(Looper.getMainLooper())
    private var loadingRunnable: Runnable? = null

    private var progressBar: ProgressBar? = null
    private var shimmerDrawable: ShimmerDrawable? = null

    companion object {
        private const val TAG = "ImageViewTarget"
        private const val LOADING_DELAY_MS = 500L
    }

    /**
     * Gọi khi bắt đầu load.
     * - Xoá state loading cũ.
     * - Hiển thị shimmer nếu bật.
     */
    override fun onLoadStarted() {
        loadingRunnable?.let { loadingHandler.removeCallbacks(it) }

        if (enableShimmer) {
            showShimmer()
        } else {
            imageView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    /**
     * Gọi khi load thành công.
     *
     * Quy trình:
     * 1. Tắt shimmer.
     * 2. release() resource cũ.
     * 3. acquire() resource mới.
     * 4. Gán bitmap vào ImageView.
     * 5. ĐĂNG KÝ CLEANUP THEO LIFECYCLE (doOnAttach → doOnDetach).
     */
    override fun onResourceReady(engineResource: EngineResource) {
        hideShimmer()

        // Xoá drawable cũ
        imageView.setImageDrawable(null)

        // Giải phóng old resource
        current?.release()

        // Lưu và acquire new resource
        current = engineResource
        current?.acquire()

        val bitmap = engineResource.getBitmap()
        if (bitmap.isRecycled) {
            ImageLoaderLogger.w(TAG, "Bitmap đã bị recycle", category = LogCategory.ENGINE)
            return
        }

        try {
            imageView.setImageBitmap(bitmap)
            imageView.setOnClickListener(null)
        } catch (e: Exception) {
            ImageLoaderLogger.e(TAG, "Set bitmap thất bại", e, LogCategory.ENGINE)
        }

        /**
         * ✅ QUẢN LÝ VÒNG ĐỜI RESOURCE
         *
         * Đây là phần quan trọng nhất:
         * - Không được gọi doOnDetach trực tiếp.
         * - Phải đợi view ATTACH rồi mới đăng ký detach listener.
         *
         * Nếu không → RecyclerView bind view khi view chưa attach → doOnDetach bị bỏ qua.
         */
        imageView.doOnAttach { view ->
            view.doOnDetach {
                current?.release()
                current = null
            }
        }
    }

    /**
     * Gọi khi load thất bại:
     * - Hiển thị error drawable
     * - Cho phép retry nếu cung cấp callback
     */
    override fun onLoadFailed(onRetry: (() -> Unit)?) {
        retryCallback = onRetry
        imageView.setImageDrawable(errorDrawable)

        // Màu vàng → debug error dễ thấy
        imageView.setBackgroundColor(0xFFFFEB3B.toInt())

        if (onRetry != null && errorDrawable != null) {
            imageView.setOnClickListener {
                imageView.setImageDrawable(null)
                imageView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                onRetry.invoke()
            }
        } else {
            imageView.setOnClickListener(null)
        }
    }

    /** Hiển thị loading spinner giữa FrameLayout. */
    private fun showLoading() {
        val parent = imageView.parent as? FrameLayout ?: return

        if (progressBar == null) {
            progressBar = ProgressBar(imageView.context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
                isIndeterminate = true
                indeterminateDrawable?.setColorFilter(
                    android.graphics.Color.BLACK,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
        }

        if (progressBar?.parent == null) {
            parent.addView(progressBar)
        }
        progressBar?.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadingRunnable?.let { loadingHandler.removeCallbacks(it) }
        loadingRunnable = null
        progressBar?.visibility = View.GONE
    }

    /**
     * Tạo shimmer overlay bằng cách đặt một LayerDrawable chồng lên drawable hiện tại.
     */
    private fun showShimmer() {
        val currentDrawable = imageView.drawable

        if (currentDrawable == null || currentDrawable is ShimmerDrawable) return

        var cornerRadius = 0f
        var placeholderColor: Int? = null

        if (currentDrawable is android.graphics.drawable.GradientDrawable) {
            try {
                cornerRadius = currentDrawable.cornerRadii?.get(0)
                    ?: currentDrawable.cornerRadius
                placeholderColor = currentDrawable.color?.defaultColor
            } catch (_: Exception) {
            }
        }

        shimmerDrawable = ShimmerDrawable(placeholderColor, cornerRadius)
        val layer = LayerDrawable(arrayOf(currentDrawable, shimmerDrawable!!))
        imageView.setImageDrawable(layer)
        shimmerDrawable?.start()
    }

    /** Tắt shimmer. */
    private fun hideShimmer() {
        shimmerDrawable?.stop()
        shimmerDrawable = null
    }

    override fun onPlaceholderColor(color: Int) {
        imageView.setBackgroundColor(color)
    }

    /** Kiểm tra key của resource hiện tại còn hợp lệ không. */
    override fun isValidFor(key: String): Boolean {
        return current?.key == key
    }

    /**
     * Dọn sạch ImageView và release() resource ngay lập tức.
     * Thường dùng khi ViewHolder bị reset.
     */
    fun clear() {
        current?.release()
        current = null
        imageView.setImageDrawable(null)
    }
}
