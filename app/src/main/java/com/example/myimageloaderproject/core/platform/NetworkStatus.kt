package com.example.myimageloaderproject.core.platform

sealed class NetworkStatus {
    object Available : NetworkStatus()
    object Unavailable : NetworkStatus()
    object Losing : NetworkStatus()
    object Lost : NetworkStatus()
}
