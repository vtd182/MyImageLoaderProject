package com.example.myimageloaderproject.core.config

object AppConfig {
    const val BASE_URL = "https://api.unsplash.com/"
    const val ACCESS_KEY = "WisKyjbbno1lYFrCkYyzZUhiffEkjFdEEAC-kQvMs3I"
    
    const val DEFAULT_PAGE_SIZE = 25
    const val PRELOAD_PAGE_COUNT = 3
    const val LOAD_MORE_THRESHOLD = 8
    const val LOAD_MORE_THRESHOLD_EARLY = 2
    
    const val CACHE_EXPIRY_HOURS = 24
    const val MAX_MEMORY_CACHE_SIZE = 5
    
    const val NETWORK_RETRY_DELAY_MS = 3000L
    const val SERVER_RETRY_DELAY_MS = 5000L
    const val RATE_LIMIT_RETRY_DELAY_MS = 60000L
    const val MAX_RETRY_ATTEMPTS = 3
    
    const val OVERLAP_ITEMS = 3
}
