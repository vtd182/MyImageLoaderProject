package com.example.myimageloaderproject.network

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpClient(
    private val baseUrl: String,
    private val accessKey: String
) {
    suspend fun get(path: String, query: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val urlStr = buildUrl(path, query)
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Client-ID $accessKey")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Version", "v1")
                connectTimeout = 30_000
                readTimeout = 30_000
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    throw Exception("HTTP $responseCode: $error")
                }
            } finally {
                connection.disconnect()
            }
        }

    private fun buildUrl(path: String, query: Map<String, String>): String {
        val queryString = query.entries.joinToString("&") { "${it.key}=${it.value}" }
        return if (queryString.isNotEmpty()) "$baseUrl$path?$queryString" else "$baseUrl$path"
    }
}

