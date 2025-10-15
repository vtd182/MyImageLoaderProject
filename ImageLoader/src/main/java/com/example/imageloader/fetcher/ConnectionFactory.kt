package com.example.imageloader.fetcher

import java.net.HttpURLConnection
import java.net.URL

fun interface ConnectionFactory {
    fun open(url: String): HttpURLConnection
}

object DefaultConnectionFactory : ConnectionFactory {
    override fun open(url: String): HttpURLConnection {
        return URL(url).openConnection() as HttpURLConnection
    }
}
