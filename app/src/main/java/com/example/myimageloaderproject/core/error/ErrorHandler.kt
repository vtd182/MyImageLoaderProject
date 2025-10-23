package com.example.myimageloaderproject.core.error

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class AppError {
    data class NetworkError(val message: String) : AppError()
    data class RateLimitError(val retryAfter: Long = 60000) : AppError() // Default 60s
    data class ServerError(val code: Int, val message: String) : AppError()
    data class UnknownError(val message: String) : AppError()
}

object ErrorHandler {

    fun handleError(throwable: Throwable): AppError {
        return when (throwable) {
            is UnknownHostException,
            is SocketTimeoutException -> {
                AppError.NetworkError("Không thể kết nối đến server. Vui lòng kiểm tra kết nối Internet.")
            }

            is IOException -> {
                AppError.NetworkError("Lỗi kết nối mạng. Vui lòng thử lại.")
            }

            else -> {
                val message = throwable.message ?: "Đã xảy ra lỗi không xác định"

                // Check for rate limit in message
                if (message.contains("403") || message.contains("rate limit", ignoreCase = true)) {
                    AppError.RateLimitError()
                } else if (message.contains("500") || message.contains("502") || message.contains("503")) {
                    AppError.ServerError(500, "Server đang gặp sự cố. Vui lòng thử lại sau.")
                } else {
                    AppError.UnknownError(message)
                }
            }
        }
    }

    fun getErrorMessage(error: AppError): String {
        return when (error) {
            is AppError.NetworkError -> error.message
            is AppError.RateLimitError -> "Đã vượt giới hạn request. Vui lòng đợi ${error.retryAfter / 1000}s."
            is AppError.ServerError -> error.message
            is AppError.UnknownError -> error.message
        }
    }

    fun shouldRetry(error: AppError): Boolean {
        return when (error) {
            is AppError.NetworkError -> true
            is AppError.RateLimitError -> true
            is AppError.ServerError -> error.code == 503 // Service Unavailable
            is AppError.UnknownError -> false
        }
    }

    fun getRetryDelay(error: AppError): Long {
        return when (error) {
            is AppError.NetworkError -> 3000L // 3 seconds
            is AppError.RateLimitError -> error.retryAfter
            is AppError.ServerError -> 5000L // 5 seconds
            is AppError.UnknownError -> 0L
        }
    }
}
