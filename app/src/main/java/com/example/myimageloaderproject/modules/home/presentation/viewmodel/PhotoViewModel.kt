package com.example.myimageloaderproject.modules.home.presentation.viewmodel

import UnsplashPhoto
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myimageloaderproject.modules.home.domain.usecase.GetRandomPhotosUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotoViewModel @Inject constructor(
    private val getRandomPhotosUseCase: GetRandomPhotosUseCase
) : ViewModel() {

    private val _photos = MutableStateFlow<List<UnsplashPhoto>>(emptyList())
    val photos: StateFlow<List<UnsplashPhoto>> = _photos

    fun loadPhotos(count: Int = 10) {
        viewModelScope.launch {
            try {
                _photos.value = getRandomPhotosUseCase(count)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

