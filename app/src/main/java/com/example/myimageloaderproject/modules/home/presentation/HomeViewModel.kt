package com.example.myimageloaderproject.modules.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myimageloaderproject.core.config.AppConfig
import com.example.myimageloaderproject.modules.home.domain.usecase.GetCachedPhotosUseCase
import com.example.myimageloaderproject.modules.home.domain.usecase.LoadInitialPhotosUseCase
import com.example.myimageloaderproject.modules.home.domain.usecase.LoadMorePhotosUseCase
import com.example.myimageloaderproject.modules.home.domain.usecase.PreloadPhotosUseCase
import com.example.myimageloaderproject.modules.home.domain.usecase.RefreshPhotosUseCase
import com.example.myimageloaderproject.shared.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val loadInitialPhotosUseCase: LoadInitialPhotosUseCase,
    private val refreshPhotosUseCase: RefreshPhotosUseCase,
    private val loadMorePhotosUseCase: LoadMorePhotosUseCase,
    private val preloadPhotosUseCase: PreloadPhotosUseCase,
    private val getCachedPhotosUseCase: GetCachedPhotosUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var currentPage = 1
    private var isLoading = false
    
    fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.LoadInitial -> loadInitialPhotos()
            HomeIntent.Refresh -> refreshPhotos()
            HomeIntent.LoadMore -> loadMorePhotos()
            HomeIntent.ClearError -> clearError()
        }
    }
    
    private fun loadInitialPhotos() {
        if (isLoading) return
        isLoading = true
        _uiState.value = HomeUiState.Loading
        
        viewModelScope.launch {
            when (val result = loadInitialPhotosUseCase()) {
                is Result.Success -> {
                    currentPage = result.data.currentPage
                    _uiState.value = HomeUiState.Content(
                        photos = result.data.photos,
                        currentPage = currentPage,
                        isFromCache = result.data.isFromCache
                    )
                    
                    launch { preloadPhotosUseCase(currentPage) }
                }
                is Result.Error -> {
                    val cachedResult = getCachedPhotosUseCase()
                    if (cachedResult is Result.Success && cachedResult.data.isNotEmpty()) {
                        _uiState.value = HomeUiState.Content(
                            photos = cachedResult.data,
                            currentPage = 1,
                            isFromCache = true,
                            error = result.error
                        )
                    } else {
                        _uiState.value = HomeUiState.Error(
                            error = result.error,
                            hasBackupData = false
                        )
                    }
                }
            }
            isLoading = false
        }
    }
    
    private fun refreshPhotos() {
        val currentState = _uiState.value
        if (currentState !is HomeUiState.Content) return
        
        _uiState.value = currentState.copy(isRefreshing = true, error = null)
        
        viewModelScope.launch {
            when (val result = refreshPhotosUseCase()) {
                is Result.Success -> {
                    currentPage = 1
                    _uiState.value = HomeUiState.Content(
                        photos = result.data,
                        currentPage = currentPage,
                        isRefreshing = false
                    )
                    
                    launch { preloadPhotosUseCase(currentPage) }
                }
                is Result.Error -> {
                    _uiState.value = currentState.copy(
                        isRefreshing = false,
                        error = result.error
                    )
                }
            }
        }
    }
    
    private fun loadMorePhotos() {
        if (isLoading) return
        val currentState = _uiState.value
        if (currentState !is HomeUiState.Content) return
        
        isLoading = true
        _uiState.value = currentState.copy(isLoadingMore = true, error = null)
        
        viewModelScope.launch {
            val nextPage = currentPage + 1
            
            when (val result = loadMorePhotosUseCase(nextPage)) {
                is Result.Success -> {
                    currentPage = nextPage
                    
                    val updatedPhotos = currentState.photos.toMutableList().apply {
                        addAll(result.data)
                    }
                    
                    _uiState.value = HomeUiState.Content(
                        photos = updatedPhotos,
                        currentPage = currentPage,
                        isLoadingMore = false
                    )
                    
                    launch { preloadPhotosUseCase(currentPage) }
                }
                is Result.Error -> {
                    _uiState.value = currentState.copy(
                        isLoadingMore = false,
                        error = result.error
                    )
                }
            }
            isLoading = false
        }
    }
    
    private fun clearError() {
        val currentState = _uiState.value
        if (currentState is HomeUiState.Content && currentState.error != null) {
            _uiState.value = currentState.copy(error = null)
        }
    }
}
