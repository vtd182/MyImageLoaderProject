package com.example.myimageloaderproject.modules.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myimageloaderproject.di.Injector
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel: ViewModel() {
    private val getRandomPhotosUseCase = Injector.getRandomPhotosUseCase

    private val _photos = MutableStateFlow<List<UnsplashPhoto>>(emptyList())
    val photos: StateFlow<List<UnsplashPhoto>> = _photos

    private var isLoading = false

    private var currentPage = 1

    private var perPage = 10

    fun loadPhotos() {
        if (isLoading) return
        isLoading = true
        viewModelScope.launch {
            try {
                val newPhotos = getRandomPhotosUseCase(perPage, currentPage)
                _photos.value = newPhotos
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMorePhotos() {
        if (isLoading) return
        isLoading = true
        viewModelScope.launch {
            try {
                val newPhotos = getRandomPhotosUseCase(perPage, currentPage + 1)
                _photos.update { currentList ->
                    currentList.toMutableList().apply { addAll(newPhotos) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
                currentPage++
            }
        }
    }
}