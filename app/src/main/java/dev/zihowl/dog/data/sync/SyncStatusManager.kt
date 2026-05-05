package dev.zihowl.dog.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class SyncStatusManager(context: Context) {

    companion object {
        const val STATUS_SYNCED = "Sincronizado"
        const val STATUS_OFFLINE = "Sin Conexión"
        const val STATUS_ERROR = "Error de Sincronización"
    }

    private val _syncStatus = MutableLiveData(STATUS_OFFLINE)
    val syncStatus: LiveData<String> = _syncStatus

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _syncStatus.postValue(STATUS_SYNCED)
        }

        override fun onLost(network: Network) {
            _syncStatus.postValue(STATUS_OFFLINE)
        }

        override fun onUnavailable() {
            _syncStatus.postValue(STATUS_OFFLINE)
        }
    }

    init {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        val currentNetwork = connectivityManager.activeNetwork
        val hasInternet = currentNetwork?.let {
            val caps = connectivityManager.getNetworkCapabilities(it)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        } ?: false
        _syncStatus.value = if (hasInternet) STATUS_SYNCED else STATUS_OFFLINE
    }

    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: IllegalArgumentException) {
        }
    }

    fun setError() {
        _syncStatus.postValue(STATUS_ERROR)
    }

    fun setSynced() {
        _syncStatus.postValue(STATUS_SYNCED)
    }
}
