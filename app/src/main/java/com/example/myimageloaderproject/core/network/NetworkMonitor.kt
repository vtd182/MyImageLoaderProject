package com.example.myimageloaderproject.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

sealed class NetworkStatus {
    object Available : NetworkStatus()
    object Unavailable : NetworkStatus()
    object Losing : NetworkStatus()
    object Lost : NetworkStatus()
}

class NetworkMonitor(private val context: Context) {
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    val networkStatus: Flow<NetworkStatus> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("NetworkMonitor", "Network Available")
                trySend(NetworkStatus.Available)
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                Log.d("NetworkMonitor", "Network Losing")
                trySend(NetworkStatus.Losing)
            }

            override fun onLost(network: Network) {
                Log.d("NetworkMonitor", "Network Lost")
                trySend(NetworkStatus.Lost)
            }

            override fun onUnavailable() {
                Log.d("NetworkMonitor", "Network Unavailable")
                trySend(NetworkStatus.Unavailable)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)
        
        trySend(getCurrentNetworkStatus())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged()

    fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun getCurrentNetworkStatus(): NetworkStatus {
        return if (isNetworkAvailable()) {
            NetworkStatus.Available
        } else {
            NetworkStatus.Unavailable
        }
    }
}
