package com.example.modicanalyzer.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes network connectivity changes and provides a Flow of connectivity states.
 * 
 * This class monitors:
 * - Wi-Fi connectivity
 * - Cellular data connectivity
 * - Ethernet connectivity
 * - VPN connectivity
 * 
 * Features:
 * - Real-time connectivity updates
 * - Reactive Flow API for easy integration
 * - Automatic network type detection
 * - Battery-efficient implementation
 * 
 * Usage:
 * ```
 * networkObserver.observe().collect { isOnline ->
 *     if (isOnline) {
 *         // Trigger sync
 *     } else {
 *         // Show offline UI
 *     }
 * }
 * ```
 */
@Singleton
class NetworkConnectivityObserver @Inject constructor(
    private val context: Context
) {
    
    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    /**
     * Observe network connectivity changes as a Flow.
     * 
     * @return Flow<Boolean> - emits true when online, false when offline
     */
    fun observe(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            /**
             * Called when network becomes available.
             */
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                trySend(true)
            }
            
            /**
             * Called when network is lost.
             */
            override fun onLost(network: Network) {
                super.onLost(network)
                trySend(false)
            }
            
            /**
             * Called when network capabilities change.
             */
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val isOnline = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                ) && networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )
                trySend(isOnline)
            }
        }
        
        // Build network request for all transport types
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        
        // Register callback
        connectivityManager.registerNetworkCallback(request, callback)
        
        // Send initial state
        trySend(isCurrentlyConnected())
        
        // Unregister callback when flow is cancelled
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged() // Only emit when connectivity state actually changes
    
    /**
     * Check current connectivity status synchronously.
     * 
     * @return true if device is currently online, false otherwise
     */
    fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Get the current network type.
     * 
     * @return NetworkType enum value
     */
    fun getCurrentNetworkType(): NetworkType {
        val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(network) 
            ?: return NetworkType.NONE
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            else -> NetworkType.UNKNOWN
        }
    }
    
    /**
     * Check if device has metered connection (cellular data).
     * Useful for avoiding large syncs on cellular networks.
     * 
     * @return true if connection is metered (cellular), false otherwise
     */
    fun isMeteredConnection(): Boolean {
        return connectivityManager.isActiveNetworkMetered
    }
}

/**
 * Enum representing different network connection types.
 */
enum class NetworkType {
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    UNKNOWN,
    NONE
}
