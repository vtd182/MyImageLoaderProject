package com.example.myimageloaderproject.modules.home.presentation

sealed interface HomeIntent {
    object LoadInitial : HomeIntent
    object Refresh : HomeIntent
    object LoadMore : HomeIntent
    object ClearError : HomeIntent
}
