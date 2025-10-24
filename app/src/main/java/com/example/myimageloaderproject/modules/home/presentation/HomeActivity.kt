package com.example.myimageloaderproject.modules.home.presentation

import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.imageloader.core.RequestManager
import com.example.myimageloaderproject.R
import com.example.myimageloaderproject.core.customView.FPSOverlay
import com.example.myimageloaderproject.core.error.AppError
import com.example.myimageloaderproject.core.error.ErrorHandler
import com.example.myimageloaderproject.core.network.NetworkMonitor
import com.example.myimageloaderproject.core.network.NetworkStatus
import com.example.myimageloaderproject.modules.home.presentation.adapter.PhotoAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {
    private var adapterCornerEnabled = false
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PhotoAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var errorLayout: View
    private lateinit var btnRetry: Button
    private lateinit var btnSettings: ImageView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var footerLoading: View
    private lateinit var networkStatusBar: LinearLayout
    private lateinit var networkStatusText: TextView

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var spanCount = 2
    private lateinit var layoutManager: GridLayoutManager

    private lateinit var networkMonitor: NetworkMonitor
    private var wasOffline = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        errorLayout = findViewById(R.id.errorLayout)
        btnRetry = findViewById(R.id.btnRetry)
        btnSettings = findViewById(R.id.btnSettings)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        footerLoading = findViewById(R.id.footerLoading)
        networkStatusBar = findViewById(R.id.networkStatusBar)
        networkStatusText = networkStatusBar.findViewById(R.id.networkStatusText)

        networkMonitor = NetworkMonitor(this)

        adapter = PhotoAdapter { spanCount }

        layoutManager = GridLayoutManager(this, spanCount)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

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
        btnSettings.setOnClickListener {
            showSettingsBottomSheet()
        }

        swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        observeData()
        observeNetworkStatus()
        viewModel.loadPhotos()

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var lastScrollTime = 0L
            private var lastDy = 0

            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)

                val currentTime = System.currentTimeMillis()
                lastScrollTime = currentTime
                lastDy = dy

                val lastVisible = layoutManager.findLastVisibleItemPosition()

                if (lastVisible >= adapter.itemCount - 8) {
                    viewModel.loadMorePhotos(showLoading = false)
                }

                if (lastVisible >= adapter.itemCount - 2) {
                    viewModel.loadMorePhotos(showLoading = true)
                }
            }

            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                super.onScrollStateChanged(rv, newState)
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING,
                    RecyclerView.SCROLL_STATE_SETTLING -> {
                        RequestManager.pauseAll()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        val visibleViews = mutableListOf<ImageView>()
                        for (i in 0 until rv.childCount) {
                            val child = rv.getChildAt(i)
                            val img = child?.findViewById<ImageView>(R.id.imgPhoto)
                            if (img != null && child.isVisible) {
                                visibleViews.add(img)
                            }
                        }
                        RequestManager.resumeVisibleOnly(visibleViews)
                    }
                }
            }
        })

        recyclerView.itemAnimator = null


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

                        if (state.hasBackupData) {
                            errorLayout.visibility = View.GONE
                            showErrorSnackbar(state.error)
                        } else {
                            errorLayout.visibility = View.VISIBLE
                            showErrorMessage(state.error)
                        }
                    }

                    is HomeUiState.Data -> {
                        progressBar.visibility = View.GONE
                        errorLayout.visibility = View.GONE
                        adapter.submitList(state.photos)
                        swipeRefresh.isRefreshing = state.isRefreshing
                        footerLoading.visibility =
                            if (state.isLoadingMore) View.VISIBLE else View.GONE

                        if (state.isOffline && state.photos.isNotEmpty()) {
                            Toast.makeText(
                                this@HomeActivity,
                                getString(R.string.offline_data),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        state.error?.let { error ->
                            showErrorSnackbar(error)
                            viewModel.clearError()
                        }
                    }
                }
            }
        }
    }

    private fun observeNetworkStatus() {
        lifecycleScope.launch {
            networkMonitor.networkStatus.collectLatest { status ->
                when (status) {
                    NetworkStatus.Available -> {
                        networkStatusBar.setBackgroundColor("#4CAF50".toColorInt())
                        networkStatusText.text = getString(R.string.network_connected)

                        if (wasOffline) {
                            animateNetworkStatusBar(show = true)

                            lifecycleScope.launch {
                                kotlinx.coroutines.delay(1500)
                                animateNetworkStatusBar(show = false)
                                showRefreshSuggestion()
                            }
                            wasOffline = false
                        }
                    }

                    NetworkStatus.Lost, NetworkStatus.Unavailable -> {
                        networkStatusBar.setBackgroundColor("#FF5252".toColorInt())
                        networkStatusText.text = getString(R.string.network_disconnected)
                        animateNetworkStatusBar(show = true)
                        wasOffline = true
                    }

                    NetworkStatus.Losing -> {
                        networkStatusBar.setBackgroundColor("#FF9800".toColorInt())
                        networkStatusText.text = getString(R.string.network_unstable)
                        animateNetworkStatusBar(show = true)
                    }
                }
            }
        }
    }

    private fun animateNetworkStatusBar(show: Boolean) {
        if (show) {
            networkStatusBar.visibility = View.VISIBLE
            val slideDown = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
            networkStatusBar.startAnimation(slideDown)
        } else {
            val slideUp = AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right)
            slideUp.setAnimationListener(object :
                android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    networkStatusBar.visibility = View.GONE
                }

                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })
            networkStatusBar.startAnimation(slideUp)
        }
    }

    private fun showRefreshSuggestion() {
        Snackbar.make(
            findViewById(android.R.id.content),
            getString(R.string.network_reconnected),
            Snackbar.LENGTH_LONG
        ).setAction(getString(R.string.refresh)) {
            viewModel.refresh()
        }.show()
    }

    private fun showErrorMessage(error: AppError) {
        val message = ErrorHandler.getErrorMessage(error)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showErrorSnackbar(error: AppError) {
        val message = ErrorHandler.getErrorMessage(error)
        val snackbar = Snackbar.make(
            findViewById(android.R.id.content),
            message,
            if (error is AppError.RateLimitError) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT
        )

        if (ErrorHandler.shouldRetry(error) && error !is AppError.RateLimitError) {
            snackbar.setAction("Thử lại") {
                viewModel.refresh()
            }
        }

        snackbar.show()
    }

    private fun showSettingsBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_settings, 
            findViewById(android.R.id.content), false)
        bottomSheetDialog.setContentView(view)

        val switchCorner = view.findViewById<SwitchMaterial>(R.id.switchCorner)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupColumns)
        val btnClose = view.findViewById<Button>(R.id.btnClose)

        // Set current states
        switchCorner.isChecked = adapterCornerEnabled
        when (spanCount) {
            1 -> chipGroup.check(R.id.chip1Column)
            2 -> chipGroup.check(R.id.chip2Columns)
            3 -> chipGroup.check(R.id.chip3Columns)
        }

        // Corner toggle listener
        switchCorner.setOnCheckedChangeListener { _, isChecked ->
            adapterCornerEnabled = isChecked
            adapter.setCornerEnabled(isChecked)
            adapter.notifyItemRangeChanged(0, adapter.itemCount)

            val msg =
                if (isChecked) getString(R.string.corner_enabled) else getString(R.string.corner_disabled)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Columns selection listener
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val newSpanCount = when (checkedIds.firstOrNull()) {
                R.id.chip1Column -> 1
                R.id.chip2Columns -> 2
                R.id.chip3Columns -> 3
                else -> spanCount
            }

            if (newSpanCount != spanCount) {
                spanCount = newSpanCount
                updateSpanCount()
                Toast.makeText(
                    this,
                    getString(R.string.columns_changed, spanCount),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
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
