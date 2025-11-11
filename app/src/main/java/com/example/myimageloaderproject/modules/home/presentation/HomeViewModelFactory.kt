package com.example.myimageloaderproject.modules.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myimageloaderproject.core.platform.ConnectivityProvider
import com.example.myimageloaderproject.modules.home.domain.usecase.GetCachedPhotosUseCase
import com.example.myimageloaderproject.modules.home.domain.usecase.LoadInitialPhotosUseCase
import com.example.myimageloaderproject.modules.home.domain.usecase.LoadMorePhotosUseCase
import com.example.myimageloaderproject.modules.home.domain.usecase.PreloadPhotosUseCase
import com.example.myimageloaderproject.modules.home.domain.usecase.RefreshPhotosUseCase

class HomeViewModelFactory(
    private val loadInitialPhotosUseCase: LoadInitialPhotosUseCase,
    private val refreshPhotosUseCase: RefreshPhotosUseCase,
    private val loadMorePhotosUseCase: LoadMorePhotosUseCase,
    private val preloadPhotosUseCase: PreloadPhotosUseCase,
    private val getCachedPhotosUseCase: GetCachedPhotosUseCase,
    private val connectivityProvider: ConnectivityProvider
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(
                loadInitialPhotosUseCase = loadInitialPhotosUseCase,
                refreshPhotosUseCase = refreshPhotosUseCase,
                loadMorePhotosUseCase = loadMorePhotosUseCase,
                preloadPhotosUseCase = preloadPhotosUseCase,
                getCachedPhotosUseCase = getCachedPhotosUseCase,
                connectivityProvider = connectivityProvider
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
