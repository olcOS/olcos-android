package io.olcos.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

/**
 * What a change of the phone's network means for a session, as the iOS app
 * reads its path updates (TunnelManager.pathDecision). The rooms' WebRTC paths
 * die with the network they were set up on, so a network that came back, or
 * another one that took over (Wi-Fi to cellular), joins the rooms again after a
 * short settle (ReconnectPolicy.networkSettleMs); with none, nothing is tried.
 * The first update while online is only the baseline.
 */
class NetworkPath {
    enum class Action { NONE, HOLD, REJOIN }

    private var seen = false
    private var offline = false
    private var last: String? = null

    /** [transport]: "wifi", "cellular", "ethernet", "vpn", "other"; null with no network. */
    fun changed(available: Boolean, transport: String?): Action {
        val first = !seen
        seen = true
        if (!available) {
            val was = offline
            offline = true
            return if (was) Action.NONE else Action.HOLD
        }
        // The device's own VPN is the default network of the other apps, not of this one's sockets.
        if (transport == "vpn") return Action.NONE
        val previous = last
        last = transport
        if (offline) {
            offline = false
            return Action.REJOIN
        }
        return if (first || transport == previous) Action.NONE else Action.REJOIN
    }
}

/**
 * Reports the default network's changes to [onAction] on the main thread, and the network
 * itself to [onNetwork] (a VPN service names it as its underlying one).
 */
class NetworkWatch(
    context: Context,
    private val onAction: (NetworkPath.Action) -> Unit,
    private val onNetwork: (Network?) -> Unit = {},
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val path = NetworkPath()
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = update(network, cm?.getNetworkCapabilities(network))
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update(network, caps)
        override fun onLost(network: Network) {
            main.post {
                onNetwork(null)
                onAction(path.changed(available = false, transport = null))
            }
        }
    }

    private fun update(network: Network, caps: NetworkCapabilities?) {
        val usable = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val transport = when {
            caps == null -> null
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        main.post {
            if (usable && transport != "vpn") onNetwork(network)
            onAction(path.changed(available = usable, transport = transport))
        }
    }

    fun start() {
        if (registered || cm == null) return
        registered = runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onFailure { AppLog.w("olcos.network", "no default-network callback: ${it.message}") }.isSuccess
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { cm?.unregisterNetworkCallback(callback) }
    }
}
