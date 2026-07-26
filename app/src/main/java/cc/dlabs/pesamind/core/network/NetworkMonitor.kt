package cc.dlabs.pesamind.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

// ─── Network Monitor ──────────────────────────────────────────────────────────
//
//  Mirrors iOS NetworkMonitor (NWPathMonitor) exactly:
//    • isConnected    — cold Flow<Boolean> (replaces @Published var isConnected)
//    • isConnectedNow — synchronous snapshot (used in ViewModel guards)
//
//  Lifecycle is managed by the OS; no manual start/cancel needed because
//  ConnectivityManager.registerNetworkCallback + awaitClose handles teardown
//  automatically when the collecting coroutine is cancelled.
//

@Singleton
class NetworkMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // ── Synchronous snapshot (mirrors Swift `monitor.currentPath.status`) ──────
        //
        //  Used by ViewModel to gate network calls without suspending:
        //    if (networkMonitor.isConnectedNow) fetchFromNetwork()
        //
        val isConnectedNow: Boolean
            get() {
                val network = connectivityManager.activeNetwork ?: return false
                val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
                return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }

        // ── Reactive flow (mirrors Swift `@Published var isConnected`) ────────────
        //
        //  Emits `true` when a validated internet-capable network is available,
        //  `false` when all networks are lost.
        //
        //  • callbackFlow bridges the callback-based API into coroutines
        //  • conflate drops intermediate values if the collector is slow
        //  • distinctUntilChanged mirrors iOS's `.removeDuplicates()` operator
        //
        val isConnected: Flow<Boolean> =
            callbackFlow {

                // Seed with current state so collectors get an immediate value,
                // matching iOS where `isConnected` starts as `true` and the path
                // update handler fires once on registration.
                trySend(isConnectedNow)

                val request =
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        .build()

                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        // A new validated network came online
                        override fun onAvailable(network: Network) {
                            trySend(true)
                        }

                        // Capabilities changed — re-check in case VALIDATED was added/removed
                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            val validated =
                                networkCapabilities.hasCapability(
                                    NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                                )
                            trySend(validated)
                        }

                        // The last network was lost
                        override fun onLost(network: Network) {
                            trySend(false)
                        }
                    }

                connectivityManager.registerNetworkCallback(request, callback)

                // awaitClose mirrors iOS's deinit { monitor.cancel() }
                // Runs when the collecting coroutine is cancelled (e.g. ViewModel cleared)
                awaitClose {
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            }.distinctUntilChanged().conflate()
    }
