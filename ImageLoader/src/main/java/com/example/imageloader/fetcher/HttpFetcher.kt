package com.example.imageloader.fetcher

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpFetcher(
    private val maxRetries: Int = 5,
    private val retryDelayMillis: Long = 1000,
    private val onRetry: ((attempt: Int, maxRetries: Int, error: Exception) -> Unit)? = null
) : DataFetcher {

    override suspend fun fetch(url: String): ByteArray {
        var attempt = 0
        var lastError: Exception? = null

        while (attempt < maxRetries) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                connection.doInput = true
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP ${connection.responseCode}")
                }

                val inputStream: InputStream = connection.inputStream
                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                inputStream.close()
                connection.disconnect()

                return outputStream.toByteArray()
            } catch (e: Exception) {
                Log.e("HttpFetcher", "Error fetching $url: ${e.message}")
                lastError = e
                attempt++
                if (attempt < maxRetries) {
                    onRetry?.invoke(attempt, maxRetries, e)
                    val delayTime = retryDelayMillis * (1L shl (attempt - 1))
                    kotlinx.coroutines.delay(delayTime)
                }
            }
        }
        throw lastError ?: Exception("Unknown error fetching $url")
    }
}