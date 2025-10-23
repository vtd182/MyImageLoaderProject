package com.example.myimageloaderproject.modules.home.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myimageloaderproject.di.Injector
import com.example.myimageloaderproject.modules.home.data.cache.JsonBackupManager
import com.example.myimageloaderproject.modules.home.data.cache.PhotoPreloader
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


sealed class HomeUiState {
    object InitLoading : HomeUiState()
    data class InitError(val throwable: Throwable) : HomeUiState()
    data class Data(
        val photos: List<UnsplashPhoto>,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false
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
                _uiState.value = HomeUiState.InitError(e)
            } finally {
                isLoading = false
            }
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
                _uiState.value = HomeUiState.InitError(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMorePhotos() {
        if (isLoading) return
        val current = _uiState.value
        if (current !is HomeUiState.Data) return

        isLoading = true
        _uiState.value = current.copy(isLoadingMore = true)

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
                _uiState.value = current.copy(isLoadingMore = false)
            } finally {
                isLoading = false
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        photoPreloader.clear()
    }
}
