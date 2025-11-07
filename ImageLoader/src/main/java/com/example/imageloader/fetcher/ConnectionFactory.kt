package com.example.imageloader.fetcher

import java.net.HttpURLConnection
import java.net.URL

/**
 * ConnectionFactory - Functional interface tạo HttpURLConnection.
 * Cho phép inject mock connections để testing HttpFetcher.
 */
fun interface ConnectionFactory {
    fun open(url: String): HttpURLConnection
}

/**
 * DefaultConnectionFactory - Default implementation dùng URL.openConnection().
 */
object DefaultConnectionFactory : ConnectionFactory {
    override fun open(url: String): HttpURLConnection {
        return URL(url).openConnection() as HttpURLConnection
    }
}
