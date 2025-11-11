package com.example.myimageloaderproject.core.di

import android.content.Context
import com.example.myimageloaderproject.core.platform.AndroidConnectivityProvider
import com.example.myimageloaderproject.core.platform.AndroidFileStorageProvider
import com.example.myimageloaderproject.core.platform.ConnectivityProvider
import com.example.myimageloaderproject.core.platform.FileStorageProvider

class AppContainer(
    context: Context
) {
    val connectivityProvider: ConnectivityProvider by lazy {
        AndroidConnectivityProvider(context)
    }
    
    val fileStorageProvider: FileStorageProvider by lazy {
        AndroidFileStorageProvider(context)
    }
    
    private val networkModule by lazy {
        NetworkModule()
    }
    
    val homeModule by lazy {
        HomeModule(
            httpClient = networkModule.httpClient,
            fileStorageProvider = fileStorageProvider,
            connectivityProvider = connectivityProvider
        )
    }
}
