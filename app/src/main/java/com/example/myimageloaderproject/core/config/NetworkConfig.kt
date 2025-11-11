package com.example.myimageloaderproject.core.config

data class NetworkConfig(
    val connectTimeout: Long = 30_000,
    val readTimeout: Long = 30_000,
    val writeTimeout: Long = 30_000,
    val retryOnConnectionFailure: Boolean = true,
    val maxRetries: Int = AppConfig.MAX_RETRY_ATTEMPTS
)
