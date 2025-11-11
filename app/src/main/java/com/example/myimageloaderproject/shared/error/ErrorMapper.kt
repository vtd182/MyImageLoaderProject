package com.example.myimageloaderproject.shared.error

import com.example.myimageloaderproject.core.error.AppError
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMapper {
    
    fun mapError(throwable: Throwable): AppError {
        return when (throwable) {
            is UnknownHostException -> {
                AppError.NetworkError("Không thể kết nối đến server. Vui lòng kiểm tra kết nối Internet.")
            }
            
            is SocketTimeoutException -> {
                AppError.NetworkError("Kết nối bị timeout. Vui lòng thử lại.")
            }
            
            is IOException -> {
                AppError.NetworkError("Lỗi kết nối mạng. Vui lòng thử lại.")
            }
            
            else -> {
                parseErrorMessage(throwable)
            }
        }
    }
    
    private fun parseErrorMessage(throwable: Throwable): AppError {
        val message = throwable.message ?: "Đã xảy ra lỗi không xác định"
        
        return when {
            message.contains("403") || message.contains("rate limit", ignoreCase = true) -> {
                AppError.RateLimitError()
            }
            
            message.contains("500") -> {
                AppError.ServerError(500, "Server đang gặp sự cố. Vui lòng thử lại sau.")
            }
            
            message.contains("502") -> {
                AppError.ServerError(502, "Bad Gateway. Vui lòng thử lại sau.")
            }
            
            message.contains("503") -> {
                AppError.ServerError(503, "Service Unavailable. Vui lòng thử lại sau.")
            }
            
            message.contains("401") -> {
                AppError.ServerError(401, "Unauthorized. Vui lòng kiểm tra API key.")
            }
            
            message.contains("404") -> {
                AppError.ServerError(404, "Resource not found.")
            }
            
            else -> {
                AppError.UnknownError(message)
            }
        }
    }
}
