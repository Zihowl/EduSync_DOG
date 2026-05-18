package dev.zihowl.dog.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.CopyOnWriteArraySet

class SyncStatusManager(context: Context) {

    companion object {
        const val STATUS_SYNCED = "Sincronizado"
        const val STATUS_OFFLINE = "Sin Conexión"
        const val STATUS_ERROR = "Error de Sincronización"

        @Volatile
        private var instance: SyncStatusManager? = null

        /** Reporta que una operación real contactó al servidor con éxito. */
        fun reportServerReachable() {
            instance?.onServerReachable()
        }

        /** Reporta que una operación real no pudo contactar al servidor. */
        fun reportServerUnreachable() {
            instance?.onServerUnreachable()
        }
    }

    private val _syncStatus = MutableLiveData(STATUS_OFFLINE)
    val syncStatus: LiveData<String> = _syncStatus

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Listeners notificados en cuanto Android reporta una interfaz de red con
     * acceso a internet. Permite a la UI disparar una sincronización inmediata
     * sin esperar el ciclo de reconexión del WebSocket.
     */
    private val availableListeners = CopyOnWriteArraySet<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun addOnNetworkAvailableListener(listener: () -> Unit) {
        availableListeners.add(listener)
    }

    fun removeOnNetworkAvailableListener(listener: () -> Unit) {
        availableListeners.remove(listener)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // No se asume "Sincronizado": la señal real sigue viniendo de la
            // primera petición GraphQL exitosa. Aquí solo se avisa a la UI para
            // que dispare la sincronización de inmediato.
            mainHandler.post {
                availableListeners.forEach { runCatching { it() } }
            }
        }

        override fun onLost(network: Network) {
            _syncStatus.postValue(STATUS_OFFLINE)
        }

        override fun onUnavailable() {
            _syncStatus.postValue(STATUS_OFFLINE)
        }
    }

    init {
        instance = this
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        // Estado conservador hasta recibir la primera señal real del servidor.
        _syncStatus.value = STATUS_OFFLINE
    }

    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun hasNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun onServerReachable() {
        _syncStatus.postValue(STATUS_SYNCED)
    }

    private fun onServerUnreachable() {
        _syncStatus.postValue(if (hasNetwork()) STATUS_ERROR else STATUS_OFFLINE)
    }

    fun setError() {
        _syncStatus.postValue(STATUS_ERROR)
    }

    fun setSynced() {
        _syncStatus.postValue(STATUS_SYNCED)
    }
}
