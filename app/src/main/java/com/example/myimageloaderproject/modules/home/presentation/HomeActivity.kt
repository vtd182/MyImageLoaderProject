package com.example.myimageloaderproject.modules.home.presentation

import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.myimageloaderproject.R
import com.example.myimageloaderproject.core.customView.FPSOverlay
import com.example.myimageloaderproject.modules.home.presentation.adapter.PhotoAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PhotoAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var errorLayout: View
    private lateinit var btnRetry: Button
    private lateinit var btnToggleCorner: Button
    private var spanCount = 2
    private lateinit var layoutManager: GridLayoutManager
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private lateinit var footerLoading: View

    private lateinit var scaleGestureDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        errorLayout = findViewById(R.id.errorLayout)
        btnRetry = findViewById(R.id.btnRetry)
        btnToggleCorner = findViewById(R.id.btnToggleCorner)

        adapter = PhotoAdapter { spanCount }

        layoutManager = GridLayoutManager(this, spanCount)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        footerLoading = findViewById(R.id.footerLoading)

        // Scale gesture detector
        scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

                private var accumulatedScale = 1f

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    accumulatedScale *= detector.scaleFactor
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    if (accumulatedScale > 1.2f && spanCount > 1) {
                        spanCount--
                        updateSpanCount()
                    } else if (accumulatedScale < 0.8f && spanCount < 3) {
                        spanCount++
                        updateSpanCount()
                    }
                    accumulatedScale = 1f
                }
            }
        )

        btnRetry.setOnClickListener { viewModel.loadPhotos() }
        btnToggleCorner.setOnClickListener {
            Toast.makeText(this, "Toggle corner function (TODO)", Toast.LENGTH_SHORT).show()
        }

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        observeData()
        viewModel.loadPhotos()

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= adapter.itemCount - 3) {
                    viewModel.loadMorePhotos()
                }
            }
        })

        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            private val space = (8 * resources.displayMetrics.density).toInt()
            override fun getItemOffsets(
                outRect: android.graphics.Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.set(space, space, space, space)
            }
        })

        addFpsOverlay()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(ev)
        return if (ev.pointerCount > 1) true else super.dispatchTouchEvent(ev)
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is HomeUiState.InitLoading -> {
                        progressBar.visibility = View.VISIBLE
                        errorLayout.visibility = View.GONE
                    }

                    is HomeUiState.InitError -> {
                        progressBar.visibility = View.GONE
                        errorLayout.visibility = View.VISIBLE
                    }

                    is HomeUiState.Data -> {
                        progressBar.visibility = View.GONE
                        errorLayout.visibility = View.GONE
                        adapter.submitList(state.photos)
                        swipeRefresh.isRefreshing = state.isRefreshing
                        footerLoading.visibility =
                            if (state.isLoadingMore) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun updateSpanCount() {
        layoutManager.spanCount = spanCount
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }


    private fun addFpsOverlay() {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        val fpsOverlay = FPSOverlay(this)
        val size = resources.displayMetrics.density * 48
        val params = ViewGroup.LayoutParams(size.toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        fpsOverlay.layoutParams = params
        rootView.addView(fpsOverlay)
    }
}


