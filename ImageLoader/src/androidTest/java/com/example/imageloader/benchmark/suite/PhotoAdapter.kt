package com.example.imageloader.benchmark.suite

import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.imageloader.benchmark.TestDataGenerator
import com.example.imageloader.core.ImageLoader
import com.example.imageloader.core.RequestManager
import com.example.imageloader.core.enums.RequestPriority
import com.example.imageloader.transformation.CenterCropRoundedCorners

/**
 * PhotoAdapter - RecyclerView Adapter cho MacroBenchmark.
 * 
 * Sử dụng ImageLoader.with() API thật như trong production app.
 * Đây là cách tốt nhất để test real-world behavior.
 */
class PhotoAdapter(
    private val items: List<TestDataGenerator.ImageSpec>
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    companion object {
        private const val ITEM_HEIGHT_DP = 200
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (ITEM_HEIGHT_DP * context.resources.displayMetrics.density).toInt()
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        return PhotoViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val spec = items[position]
        
        // Clear previous request (critical for RecyclerView reuse)
        RequestManager.clear(holder.imageView)
        
        // Generate placeholder color from position (deterministic)
        val colorInt = android.graphics.Color.rgb(
            (position * 37) % 256,
            (position * 73) % 256,
            (position * 149) % 256
        )
        val placeholderHex = String.format("#%06X", 0xFFFFFF and colorInt)
        
        // Load image với ImageLoader API
        ImageLoader.with(holder.imageView.context)
            .load(spec.url)
            .resize(spec.category.width, spec.category.height)
            .overrideSize(400, 400)
            .placeholder(placeholderHex)
            .priority(RequestPriority.NORMAL)
            .transform(CenterCropRoundedCorners(16f))
            .enableShimmer(true)
            .into(holder.imageView)
    }

    override fun getItemCount(): Int = items.size

    /**
     * Clear all pending requests when activity is destroyed.
     */
    fun clearAll() {
        // No-op: RequestManager automatically clears on view detach
    }

    class PhotoViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)
}
