package com.example.myimageloaderproject.modules.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myimageloaderproject.di.Injector
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

class HomeViewModel : ViewModel() {
    private val getRandomPhotosUseCase = Injector.getRandomPhotosUseCase

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.InitLoading)
    val uiState: StateFlow<HomeUiState> = _uiState

    private var currentPage = 1
    private var perPage = 10
    private var isLoading = false

    fun loadPhotos() {
        if (isLoading) return
        isLoading = true
        _uiState.value = HomeUiState.InitLoading
        viewModelScope.launch {
            try {
                val newPhotos = getRandomPhotosUseCase(perPage, 1)
                currentPage = 1
                _uiState.value = HomeUiState.Data(newPhotos)
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
                val newPhotos = getRandomPhotosUseCase(perPage, 1)
                currentPage = 1
                _uiState.value = HomeUiState.Data(newPhotos)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.InitError(e)
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMorePhotos() {
        // Nếu đang load thì không làm gì
        if (isLoading) return
        val current = _uiState.value
        if (current !is HomeUiState.Data) return

        isLoading = true
        _uiState.value = current.copy(isLoadingMore = true)

        viewModelScope.launch {
            try {
                val newPhotosRaw = getRandomPhotosUseCase(perPage, currentPage + 1)
                currentPage++

                val newPhotos = if (newPhotosRaw.size > 3) {
                    newPhotosRaw.drop(3)
                } else {
                    emptyList()
                }

                val finalList = current.photos.toMutableList()
                val chunkSize = 5

                for (chunk in newPhotos.chunked(chunkSize)) {
                    finalList.addAll(chunk)
                    _uiState.value = HomeUiState.Data(
                        photos = finalList.toList(),
                        isRefreshing = false,
                        isLoadingMore = true
                    )
                    kotlinx.coroutines.delay(120)
                }

                _uiState.value = HomeUiState.Data(
                    photos = finalList,
                    isRefreshing = false,
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                _uiState.value = current.copy(isLoadingMore = false)
            } finally {
                isLoading = false
            }
        }
    }
}
