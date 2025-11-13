package com.example.imageloader.benchmark.suite

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.imageloader.benchmark.TestDataGenerator

/**
 * BenchmarkTestActivity - Activity với RecyclerView cho REAL benchmark testing.
 *
 * Cung cấp:
 * - RecyclerView thật với LinearLayoutManager
 * - PhotoAdapter sử dụng ImageLoader.with() API
 * - Giả lập real-world scroll behavior
 *
 * Note: Sử dụng Activity thay vì AppCompatActivity để tránh theme requirement.
 */
class BenchmarkTestActivity : Activity() {

    lateinit var recyclerView: RecyclerView
        private set

    lateinit var adapter: PhotoAdapter
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create RecyclerView
        recyclerView = RecyclerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(this@BenchmarkTestActivity)
        }

        setContentView(recyclerView)

        // Load data from intent or generate default
        val itemCount = intent.getIntExtra("ITEM_COUNT", 500)
        val imageSpecs = TestDataGenerator.generateMixedDataset(itemCount)

        // Setup adapter
        adapter = PhotoAdapter(imageSpecs)
        recyclerView.adapter = adapter

        // Mark ready for testing
        window.decorView.postDelayed({
            window.decorView.contentDescription = "READY"
        }, 100)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup handled by adapter's onViewRecycled
    }
}
