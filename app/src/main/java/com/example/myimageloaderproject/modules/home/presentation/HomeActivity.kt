package com.example.myimageloaderproject.modules.home.presentation

import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.example.imageloader.logger.LogViewer
import com.example.myimageloaderproject.MyApplication
import com.example.myimageloaderproject.R
import com.example.myimageloaderproject.core.customView.FPSOverlay
import com.example.myimageloaderproject.core.error.AppError
import com.example.myimageloaderproject.core.error.ErrorHandler
import com.example.myimageloaderproject.core.platform.NetworkStatus
import com.example.myimageloaderproject.core.ui.base.BaseActivity
import com.example.myimageloaderproject.databinding.ActivityHomeBinding
import com.example.myimageloaderproject.modules.home.presentation.adapter.PhotoAdapter
import com.example.myimageloaderproject.modules.home.presentation.components.PhotoGridManager
import com.example.myimageloaderproject.modules.home.presentation.components.ScrollLoadMoreHandler
import com.example.myimageloaderproject.modules.home.presentation.components.SettingsBottomSheetHelper
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeActivity : BaseActivity() {

    private val viewModel: HomeViewModel by viewModels {
        (application as MyApplication).appContainer.homeModule.createHomeViewModelFactory()
    }

    private lateinit var binding: ActivityHomeBinding
    private lateinit var photoAdapter: PhotoAdapter
    private lateinit var gridManager: PhotoGridManager
    private lateinit var scrollHandler: ScrollLoadMoreHandler
    private lateinit var settingsHelper: SettingsBottomSheetHelper

    private lateinit var networkStatusBarView: android.widget.LinearLayout
    private lateinit var networkStatusText: android.widget.TextView

    private var adapterCornerEnabled = false
    private var wasOffline = false
    private var lastHandledNetworkStatus: NetworkStatus? = null
    private var lastShownError: AppError? = null

    private var tapCount = 0
    private var lastTapTime = 0L
    private val TAP_TIMEOUT = 500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupObservers()

        viewModel.handleIntent(HomeIntent.LoadInitial)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (gridManager.handleTouchEvent(ev)) {
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupUI() {
        networkStatusBarView = findViewById(R.id.networkStatusBar)
        networkStatusText = networkStatusBarView.findViewById(R.id.networkStatusText)

        photoAdapter = PhotoAdapter { gridManager.getSpanCount() }

        gridManager = PhotoGridManager(this, binding.recyclerView)
        gridManager.initialize()
        gridManager.setOnSpanCountChangedListener { spanCount ->
            photoAdapter.notifyItemRangeChanged(0, photoAdapter.itemCount)
        }
        binding.recyclerView.adapter = photoAdapter
        binding.recyclerView.itemAnimator = null

        scrollHandler = ScrollLoadMoreHandler(
            recyclerView = binding.recyclerView,
            onLoadMore = { viewModel.handleIntent(HomeIntent.LoadMore) }
        )
        scrollHandler.attach()

        settingsHelper = SettingsBottomSheetHelper(this)

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.handleIntent(HomeIntent.Refresh)
        }

        binding.btnRetry.setOnClickListener {
            viewModel.handleIntent(HomeIntent.LoadInitial)
        }

        binding.btnSettings.setOnClickListener {
            showSettingsBottomSheet()
        }

        binding.tvTitleHome.setOnClickListener {
            handleTitleTap()
        }

        addFpsOverlay()
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is HomeUiState.Loading -> showLoading()
                    is HomeUiState.Content -> {
                        showContent(state)
                        handleNetworkStatus(state.networkStatus)
                    }
                    is HomeUiState.Error -> {
                        showError(state)
                        handleNetworkStatus(state.networkStatus)
                    }
                }
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorLayout.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
    }

    private fun showContent(state: HomeUiState.Content) {
        binding.progressBar.visibility = View.GONE
        binding.errorLayout.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = state.isRefreshing
        binding.footerLoading.visibility = if (state.isLoadingMore) View.VISIBLE else View.GONE

        photoAdapter.submitList(state.photos)

        state.error?.let { error ->
            if (lastShownError != error) {
                lastShownError = error
                showErrorSnackbar(error)
                viewModel.handleIntent(HomeIntent.ClearError)
            }
        }
        
        if (state.error == null && lastShownError != null) {
            lastShownError = null
        }
    }

    private fun showError(state: HomeUiState.Error) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false

        if (state.hasBackupData) {
            binding.errorLayout.visibility = View.GONE
            if (lastShownError != state.error) {
                lastShownError = state.error
                showErrorSnackbar(state.error)
            }
        } else {
            binding.errorLayout.visibility = View.VISIBLE
            if (lastShownError != state.error) {
                lastShownError = state.error
                showErrorMessage(state.error)
            }
        }
    }

    private fun showErrorMessage(error: AppError) {
        val message = ErrorHandler.getErrorMessage(error)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showErrorSnackbar(error: AppError) {
        val message = ErrorHandler.getErrorMessage(error)
        val snackbar = Snackbar.make(
            binding.root,
            message,
            if (error is AppError.RateLimitError) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT
        )

        if (ErrorHandler.shouldRetry(error) && error !is AppError.RateLimitError) {
            snackbar.setAction("Thử lại") {
                viewModel.handleIntent(HomeIntent.Refresh)
            }
        }

        snackbar.show()
    }

    private fun handleNetworkStatus(status: NetworkStatus) {
        if (lastHandledNetworkStatus == status) {
            return
        }
        lastHandledNetworkStatus = status
        
        when (status) {
            NetworkStatus.Available -> {
                networkStatusBarView.setBackgroundColor("#4CAF50".toColorInt())
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
                networkStatusBarView.setBackgroundColor("#FF5252".toColorInt())
                networkStatusText.text = getString(R.string.network_disconnected)
                animateNetworkStatusBar(show = true)
                wasOffline = true
            }

            NetworkStatus.Losing -> {
                networkStatusBarView.setBackgroundColor("#FF9800".toColorInt())
                networkStatusText.text = getString(R.string.network_unstable)
                animateNetworkStatusBar(show = true)
            }
        }
    }

    private fun animateNetworkStatusBar(show: Boolean) {
        if (show) {
            networkStatusBarView.visibility = View.VISIBLE
            val slideDown = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
            networkStatusBarView.startAnimation(slideDown)
        } else {
            val slideUp = AnimationUtils.loadAnimation(this, android.R.anim.slide_out_right)
            slideUp.setAnimationListener(object :
                android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    networkStatusBarView.visibility = View.GONE
                }

                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })
            networkStatusBarView.startAnimation(slideUp)
        }
    }

    private fun showRefreshSuggestion() {
        Snackbar.make(
            binding.root,
            getString(R.string.network_reconnected),
            Snackbar.LENGTH_LONG
        ).setAction(getString(R.string.refresh)) {
            viewModel.handleIntent(HomeIntent.Refresh)
        }.show()
    }

    private fun showSettingsBottomSheet() {
        settingsHelper.show(
            currentSpanCount = gridManager.getSpanCount(),
            isCornerEnabled = adapterCornerEnabled,
            onSpanCountChanged = { newSpanCount ->
                gridManager.setSpanCount(newSpanCount)
            },
            onCornerToggled = { isEnabled ->
                adapterCornerEnabled = isEnabled
                photoAdapter.setCornerEnabled(isEnabled)
                photoAdapter.notifyItemRangeChanged(0, photoAdapter.itemCount)
            }
        )
    }

    private fun handleTitleTap() {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastTapTime > TAP_TIMEOUT) {
            tapCount = 0
        }

        tapCount++
        lastTapTime = currentTime

        if (tapCount == 5) {
            tapCount = 0
            LogViewer.open(this)
            Toast.makeText(this, "Opening Logger", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addFpsOverlay() {
        val rootView = window.decorView as ViewGroup
        val fpsOverlay = FPSOverlay(this)
        val size = resources.displayMetrics.density * 48 * 2
        val params = FrameLayout.LayoutParams(size.toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.TOP or Gravity.END
        val statusBarHeight = resources.getDimensionPixelSize(
            resources.getIdentifier("status_bar_height", "dimen", "android")
        )
        params.topMargin = statusBarHeight
        fpsOverlay.layoutParams = params
        rootView.addView(fpsOverlay)
    }
}
