package com.example.myimageloaderproject.modules.home.presentation

import com.example.myimageloaderproject.core.error.AppError
import com.example.myimageloaderproject.modules.home.domain.model.UnsplashPhoto

sealed interface HomeUiStateV2 {
    object Loading : HomeUiStateV2
    
    data class Content(
        val photos: List<UnsplashPhoto>,
        val currentPage: Int = 1,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val isFromCache: Boolean = false,
        val error: AppError? = null
    ) : HomeUiStateV2
    
    data class Error(
        val error: AppError,
        val hasBackupData: Boolean = false
    ) : HomeUiStateV2
}
