package com.example.myimageloaderproject.modules.home.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto
import com.example.myimageloaderproject.modules.home.domain.usecase.GetRandomPhotosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotoViewModel @Inject constructor(
    private val getRandomPhotosUseCase: GetRandomPhotosUseCase
) : ViewModel() {

    private val _photos = MutableStateFlow<List<UnsplashPhoto>>(emptyList())
    val photos: StateFlow<List<UnsplashPhoto>> = _photos

    private var isLoading = false

    private var currentPage = 1

    fun loadPhotos(count: Int = 20) {
        if (isLoading) return
        isLoading = true
        viewModelScope.launch {
            try {
                val newPhotos = getRandomPhotosUseCase(count, currentPage)
                _photos.value = newPhotos
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMorePhotos(count: Int = 20) {
        if (isLoading) return
        isLoading = true
        viewModelScope.launch {
            try {
                val newPhotos = getRandomPhotosUseCase(count, currentPage + 1)
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


