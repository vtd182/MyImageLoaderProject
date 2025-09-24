package com.example.myimageloaderproject.core.base

import com.example.myimageloaderproject.network.HttpClient

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

open class BaseApiService(
    private val client: HttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }


    protected suspend fun <T> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        deserializer: DeserializationStrategy<T>
    ): T {
        val response = client.get(path, query)
        return json.decodeFromString(deserializer, response)
    }
}
