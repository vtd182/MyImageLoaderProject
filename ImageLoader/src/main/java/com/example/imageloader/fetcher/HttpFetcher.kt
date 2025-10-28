package com.example.imageloader.fetcher

import com.example.imageloader.logger.ImageLoaderLogger
import com.example.imageloader.logger.LogCategory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection

class HttpFetcher(
    private val maxRetries: Int = 2,
    private val retryDelayMillis: Long = 700,
    private val onRetry: ((attempt: Int, maxRetries: Int, error: Exception) -> Unit)? = null,
    private val connectionFactory: ConnectionFactory = DefaultConnectionFactory
) : DataFetcher {

    companion object {
        private const val TAG = "HttpFetcher"
    }

    override suspend fun fetch(url: String): HttpResult {
        var attempt = 0
        var lastError: Exception? = null

        while (attempt < maxRetries) {
            try {
                val connection = connectionFactory.open(url)
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                connection.doInput = true
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("HTTP ${connection.responseCode}")
                }

                val contentType = connection.contentType
                val inputStream: InputStream = connection.inputStream
                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                inputStream.close()
                connection.disconnect()

                return HttpResult(outputStream.toByteArray(), contentType)
            } catch (e: Exception) {
                lastError = e
                attempt++
                if (attempt < maxRetries) {
                    ImageLoaderLogger.w(
                        TAG,
                        "Retry $attempt/$maxRetries: ${e.message}",
                        category = LogCategory.NETWORK
                    )
                    onRetry?.invoke(attempt, maxRetries, e)
                    val delayTime = retryDelayMillis * (1L shl (attempt - 1))
                    kotlinx.coroutines.delay(delayTime)
                } else {
                    ImageLoaderLogger.e(
                        TAG,
                        "Failed after $maxRetries retries",
                        e,
                        LogCategory.NETWORK
                    )
                }
            }
        }
        throw lastError ?: Exception("Unknown error fetching $url")
    }
}