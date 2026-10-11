package com.storepos.app.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException

/** Checks *validated* internet, not just Wi-Fi/LAN connectivity. */
object HybridConnectivity {
    fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Business-rule and HTTP validation failures must never be queued as if offline. */
    fun isConnectionFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth++ < 12) {
            if (current is IOException) return true
            val name = current.javaClass.name.lowercase()
            if ("timeout" in name || "connectionexception" in name) return true
            val message = current.message.orEmpty().lowercase()
            if (listOf(
                    "unable to resolve host", "failed to connect",
                    "connection refused", "connection reset", "network is unreachable",
                    "socket closed", "network timeout", "timed out",
                    "no address associated with hostname"
                ).any { it in message }
            ) return true
            current = current.cause
        }
        return false
    }
}
