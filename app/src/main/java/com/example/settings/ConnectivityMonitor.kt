package com.example.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectivityMonitor(context: Context) {
    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isOnline = MutableStateFlow(checkNow())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { _isOnline.value = checkNow() }
        override fun onLost(network: Network) { _isOnline.value = checkNow() }
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _isOnline.value = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

    init {
        runCatching {
            manager.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                callback
            )
        }
    }

    private fun checkNow(): Boolean {
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun close() { runCatching { manager.unregisterNetworkCallback(callback) } }
}

class PendingSyncStore(context: Context) {
    private val prefs = context.getSharedPreferences("tahalil_pending_sync_v28", Context.MODE_PRIVATE)
    private val _count = MutableStateFlow(prefs.getInt("pending_count", 0))
    val count: StateFlow<Int> = _count.asStateFlow()

    fun increment() {
        val next = (_count.value + 1).coerceAtMost(999)
        prefs.edit().putInt("pending_count", next).apply()
        _count.value = next
    }

    fun clearAll() {
        prefs.edit().putInt("pending_count", 0).apply()
        _count.value = 0
    }
}
