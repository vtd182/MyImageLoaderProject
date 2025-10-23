package com.example.myimageloaderproject.modules.home.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myimageloaderproject.core.error.AppError
import com.example.myimageloaderproject.core.error.ErrorHandler
import com.example.myimageloaderproject.core.network.NetworkMonitor
import com.example.myimageloaderproject.di.Injector
import com.example.myimageloaderproject.modules.home.data.cache.JsonBackupManager
import com.example.myimageloaderproject.modules.home.data.cache.PhotoPreloader
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


sealed class HomeUiState {
    object InitLoading : HomeUiState()
    data class InitError(
        val error: AppError,
        val isOffline: Boolean = false,
        val hasBackupData: Boolean = false
    ) : HomeUiState()
    data class Data(
        val photos: List<UnsplashPhoto>,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val isOffline: Boolean = false,
        val error: AppError? = null
    ) : HomeUiState()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val getRandomPhotosUseCase = Injector.getRandomPhotosUseCase

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.InitLoading)
    val uiState: StateFlow<HomeUiState> = _uiState

    private var currentPage = 1
    private var perPage = 25
    private var isLoading = false
    
    private val photoPreloader = PhotoPreloader(getRandomPhotosUseCase, viewModelScope)
    private val backupManager = JsonBackupManager(application)
    private val networkMonitor = NetworkMonitor(application)
    
    private var retryJob: kotlinx.coroutines.Job? = null

    fun loadPhotos() {
        if (isLoading) return
        isLoading = true
        _uiState.value = HomeUiState.InitLoading
        viewModelScope.launch {
            try {
                val backup = backupManager.loadBackup()
                if (backup != null && backup.photos.isNotEmpty()) {
                    currentPage = backup.currentPage
                    _uiState.value = HomeUiState.Data(backup.photos)
                    isLoading = false
                    
                    viewModelScope.launch {
                        photoPreloader.preloadPages(currentPage)
                    }
                    return@launch
                }
                
                val newPhotos = getRandomPhotosUseCase(perPage, 1)
                
                currentPage = 1
                _uiState.value = HomeUiState.Data(newPhotos)
                
                // Preload in background without blocking
                viewModelScope.launch {
                    photoPreloader.preloadPages(currentPage)
                }
                
                backupManager.saveBackup(newPhotos, currentPage)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to load photos", e)
                handleLoadError(e)
            } finally {
                isLoading = false
            }
        }
    }
    
    private suspend fun handleLoadError(e: Exception) {
        val error = ErrorHandler.handleError(e)
        val isOffline = !networkMonitor.isNetworkAvailable()
        
        // Try to load backup
        val backup = backupManager.loadBackup()
        if (backup != null && backup.photos.isNotEmpty()) {
            currentPage = backup.currentPage
            _uiState.value = HomeUiState.Data(
                photos = backup.photos,
                isOffline = true,
                error = error
            )
            
            // Auto retry if possible
            if (ErrorHandler.shouldRetry(error)) {
                scheduleRetry(error)
            }
        } else {
            _uiState.value = HomeUiState.InitError(
                error = error,
                isOffline = isOffline,
                hasBackupData = false
            )
        }
    }
    
    private fun scheduleRetry(error: AppError) {
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            val delay = ErrorHandler.getRetryDelay(error)
            Log.d("HomeViewModel", "Scheduling retry in ${delay}ms")
            delay(delay)
            
            if (!networkMonitor.isNetworkAvailable()) {
                Log.d("HomeViewModel", "Still offline, skip retry")
                return@launch
            }
            
            Log.d("HomeViewModel", "Auto retrying...")
            loadPhotos()
        }
    }
    
    fun refresh() {
        if (isLoading) return
        isLoading = true
        val current = _uiState.value
        if (current is HomeUiState.Data) {
            _uiState.value = current.copy(isRefreshing = true)
        }
        viewModelScope.launch {
            try {
                photoPreloader.clear()
                
                val newPhotos = getRandomPhotosUseCase(perPage, 1)
                currentPage = 1
                _uiState.value = HomeUiState.Data(newPhotos)
                
                photoPreloader.preloadPages(currentPage)
                
                backupManager.saveBackup(newPhotos, currentPage)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to refresh", e)
                val error = ErrorHandler.handleError(e)
                val isOffline = !networkMonitor.isNetworkAvailable()
                
                // Nếu đang refresh và có data, giữ nguyên data và chỉ show error
                if (current is HomeUiState.Data) {
                    _uiState.value = current.copy(
                        isRefreshing = false,
                        error = error
                    )
                } else {
                    // Nếu chưa có data, show error screen
                    _uiState.value = HomeUiState.InitError(
                        error = error,
                        isOffline = isOffline,
                        hasBackupData = false
                    )
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMorePhotos(showLoading: Boolean = true) {
        if (isLoading) return
        val current = _uiState.value
        if (current !is HomeUiState.Data) return

        isLoading = true
        if (showLoading) {
            _uiState.value = current.copy(isLoadingMore = true)
        }

        viewModelScope.launch {
            try {
                val nextPage = currentPage + 1
                
                val newPhotosRaw = if (photoPreloader.hasPreloadedPage(nextPage)) {
                    photoPreloader.getPreloadedPage(nextPage) ?: getRandomPhotosUseCase(perPage, nextPage)
                } else {
                    getRandomPhotosUseCase(perPage, nextPage)
                }
                
                currentPage = nextPage

                val newPhotos = if (newPhotosRaw.size > 3) {
                    newPhotosRaw.drop(3)
                } else {
                    emptyList()
                }

                val finalList = current.photos.toMutableList()
                finalList.addAll(newPhotos)

                _uiState.value = HomeUiState.Data(
                    photos = finalList,
                    isRefreshing = false,
                    isLoadingMore = false
                )
                
                photoPreloader.preloadPages(currentPage)
                
                backupManager.saveBackup(finalList, currentPage)
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to load more", e)
                val error = ErrorHandler.handleError(e)
                val isOffline = !networkMonitor.isNetworkAvailable()
                _uiState.value = current.copy(
                    isLoadingMore = false,
                    isOffline = isOffline,
                    error = error
                )
            } finally {
                isLoading = false
            }
        }
    }
    
    fun clearError() {
        val current = _uiState.value
        if (current is HomeUiState.Data && current.error != null) {
            _uiState.value = current.copy(error = null)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        photoPreloader.clear()
        retryJob?.cancel()
    }
}
