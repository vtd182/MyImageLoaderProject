package com.example.imageloader.fetcher

interface DataFetcher {
    suspend fun fetch(url: String): ByteArray
}
