package com.rmediatech.sovereign.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Sovereign Connectivity Sentinel (OJP Single Responsibility <= 300 lines)
 * 100% Zero-Cloud, Real-Time Hardware Network & Ingress Capability Sentinel.
 * Emancipates Android clients from blind socket polling by providing instant,
 * reactive classification of transport status (WIFI, CELLULAR, ETHERNET, OFFLINE).
 */
enum class SovereignTransportType {
    WIFI,
    CELLULAR,
    ETHERNET,
    NONE
}

data class SovereignNetworkState(
    val transport: SovereignTransportType = SovereignTransportType.NONE,
    val isConnected: Boolean = false,
    val hasValidatedInternet: Boolean = false,
    val isMetered: Boolean = false
) {
    val isLanEligible: Boolean
        get() = isConnected && (transport == SovereignTransportType.WIFI || transport == SovereignTransportType.ETHERNET)

    val isCellularWanOnly: Boolean
        get() = isConnected && transport == SovereignTransportType.CELLULAR

    val isCompletelyOffline: Boolean
        get() = !isConnected || transport == SovereignTransportType.NONE
}

class SovereignConnectivitySentinel(context: Context) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /**
     * Synchronous 0ms current network classification snapshot.
     * Use before initiating any network operation to eliminate blind socket waits.
     */
    fun currentNetworkState(): SovereignNetworkState {
        val cm = connectivityManager ?: return SovereignNetworkState()
        val activeNetwork: Network? = cm.activeNetwork
        val caps: NetworkCapabilities? = activeNetwork?.let { cm.getNetworkCapabilities(it) }

        if (activeNetwork == null || caps == null) {
            return SovereignNetworkState(
                transport = SovereignTransportType.NONE,
                isConnected = false,
                hasValidatedInternet = false,
                isMetered = false
            )
        }

        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> SovereignTransportType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> SovereignTransportType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> SovereignTransportType.ETHERNET
            else -> SovereignTransportType.NONE
        }

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        return SovereignNetworkState(
            transport = transport,
            isConnected = hasInternet,
            hasValidatedInternet = isValidated,
            isMetered = isMetered
        )
    }

    /**
     * Cold Kotlin Flow emitting real-time network capability transitions.
     * Automatically triggers immediate LAN <-> WAN cellular failover upon roaming.
     */
    fun observeNetworkState(): Flow<SovereignNetworkState> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            trySend(SovereignNetworkState())
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentNetworkState())
            }

            override fun onLost(network: Network) {
                trySend(currentNetworkState())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(currentNetworkState())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, callback)
        trySend(currentNetworkState())

        awaitClose {
            cm.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
