package com.example.myimageloaderproject.shared.result

sealed interface Result<out T, out E> {
    data class Success<T>(val data: T) : Result<T, Nothing>
    data class Error<E>(val error: E) : Result<Nothing, E>
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }
    
    fun errorOrNull(): E? = when (this) {
        is Success -> null
        is Error -> error
    }
}

inline fun <T, E, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> {
    return when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
    }
}

inline fun <T, E, R> Result<T, E>.flatMap(transform: (T) -> Result<R, E>): Result<R, E> {
    return when (this) {
        is Result.Success -> transform(data)
        is Result.Error -> this
    }
}

inline fun <T, E> Result<T, E>.onSuccess(action: (T) -> Unit): Result<T, E> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T, E> Result<T, E>.onError(action: (E) -> Unit): Result<T, E> {
    if (this is Result.Error) action(error)
    return this
}

fun <T, E> Result<T, E>.getOrThrow(): T {
    return when (this) {
        is Result.Success -> data
        is Result.Error -> throw IllegalStateException("Result is Error: $error")
    }
}

fun <T, E> Result<T, E>.getOrElse(default: T): T {
    return when (this) {
        is Result.Success -> data
        is Result.Error -> default
    }
}
