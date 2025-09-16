package com.example.imageloader.fetcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class OkHttpFetcher(private val client: OkHttpClient = OkHttpClient()) : DataFetcher {
    override suspend fun fetch(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        response.body?.bytes() ?: throw IOException("Empty response body")
    }
}
