package com.mobisentinel.app.monitoring.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

interface CellularNetworkRequester {
    interface Callback {
        fun onAvailable()

        fun onCapabilitiesChanged(validated: Boolean)

        fun onUnavailable()
    }

    fun request(callback: Callback)

    fun unregister(callback: Callback)
}

class AndroidCellularNetworkRequester(
    private val connectivityManager: ConnectivityManager,
) : CellularNetworkRequester {
    private val callbacks =
        ConcurrentHashMap<CellularNetworkRequester.Callback, ConnectivityManager.NetworkCallback>()
    private val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    override fun request(callback: CellularNetworkRequester.Callback) {
        val platformCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                callback.onAvailable()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                callback.onCapabilitiesChanged(
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
            }

            override fun onUnavailable() {
                callback.onUnavailable()
            }
        }
        check(callbacks.putIfAbsent(callback, platformCallback) == null)
        try {
            connectivityManager.requestNetwork(request, platformCallback)
        } catch (failure: Throwable) {
            callbacks.remove(callback, platformCallback)
            throw failure
        }
    }

    override fun unregister(callback: CellularNetworkRequester.Callback) {
        val platformCallback = callbacks.remove(callback) ?: return
        try {
            connectivityManager.unregisterNetworkCallback(platformCallback)
        } catch (_: IllegalArgumentException) {
            // Registration may have failed or Android may already have removed it.
        }
    }
}

class AndroidCellularValidationProbe(
    private val requester: CellularNetworkRequester,
    private val timeoutMillis: Long = 15_000L,
) : CellularValidationProbe {
    init {
        require(timeoutMillis > 0)
    }

    override suspend fun validate(): CellularValidationResult {
        val networkSeen = AtomicBoolean(false)
        val released = AtomicBoolean(false)
        var callback: CellularNetworkRequester.Callback? = null

        fun releaseOnce() {
            val registeredCallback = callback ?: return
            if (released.compareAndSet(false, true)) {
                try {
                    requester.unregister(registeredCallback)
                } catch (_: RuntimeException) {
                    // Cleanup must not replace the connectivity result.
                }
            }
        }

        return try {
            val completed = withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine<CellularValidationResult> { continuation ->
                    val callbackCompleted = AtomicBoolean(false)

                    fun completeOnce(result: CellularValidationResult) {
                        if (callbackCompleted.compareAndSet(false, true)) {
                            continuation.resume(result)
                        }
                    }

                    callback = object : CellularNetworkRequester.Callback {
                        override fun onAvailable() {
                            networkSeen.set(true)
                        }

                        override fun onCapabilitiesChanged(validated: Boolean) {
                            networkSeen.set(true)
                            if (validated) {
                                completeOnce(CellularValidationResult.Validated)
                            }
                        }

                        override fun onUnavailable() {
                            completeOnce(CellularValidationResult.Unavailable)
                        }
                    }
                    continuation.invokeOnCancellation {
                        callbackCompleted.set(true)
                        releaseOnce()
                    }
                    try {
                        requester.request(checkNotNull(callback))
                    } catch (failure: Throwable) {
                        completeOnce(CellularValidationResult.Failure(failure))
                    }
                }
            }
            completed ?: if (networkSeen.get()) {
                CellularValidationResult.Unvalidated
            } else {
                CellularValidationResult.Unavailable
            }
        } finally {
            releaseOnce()
        }
    }
}
