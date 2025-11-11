package com.example.myimageloaderproject.core.di

import com.example.myimageloaderproject.core.config.AppConfig
import com.example.myimageloaderproject.network.HttpClient

class NetworkModule {
    
    val httpClient: HttpClient by lazy {
        HttpClient(
            baseUrl = AppConfig.BASE_URL,
            accessKey = AppConfig.ACCESS_KEY
        )
    }
}
