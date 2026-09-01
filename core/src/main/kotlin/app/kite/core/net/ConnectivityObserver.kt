package app.kite.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * Observes whether the device has validated internet (not just a connected-but-captive
 * network). Both apps show an offline banner from this so the user understands that
 * network-only features are unavailable while offline — enforcement itself never depends
 * on it (offline-first, CLAUDE.md). Requires ACCESS_NETWORK_STATE.
 */
class ConnectivityObserver(context: Context) {
    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun currentlyOnline(): Boolean {
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private val onlineFlow: Flow<Boolean> = callbackFlow {
        trySend(currentlyOnline())
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    trySend(
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    )
                }

                override fun onLost(network: Network) {
                    // Another network may still be up; re-check rather than assume offline.
                    trySend(currentlyOnline())
                }

                override fun onUnavailable() = Unit
            }
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /** Hot state, shared so multiple collectors keep a single callback registered. */
    fun online(scope: CoroutineScope): StateFlow<Boolean> =
        onlineFlow.stateIn(scope, SharingStarted.WhileSubscribed(5_000), currentlyOnline())
}
