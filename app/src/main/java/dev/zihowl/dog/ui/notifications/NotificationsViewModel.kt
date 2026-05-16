package dev.zihowl.dog.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dev.zihowl.dog.DogApplication
import dev.zihowl.dog.data.model.Notification
import dev.zihowl.dog.data.repository.DogRepository
import dev.zihowl.dog.data.session.SessionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Bandeja in-app de notificaciones de cambios de horario (RQF-APP-27/28/29).
 * Lee las entradas persistidas en Room para el usuario activo; al abrirse las
 * marca como leídas.
 */
class NotificationsViewModel(application: Application) : AndroidViewModel(application) {

    // El repositorio ya está inicializado cuando se abre cualquier pantalla.
    private val repository: DogRepository = runBlocking {
        (application as DogApplication).repository()
    }
    private val session = SessionManager(application)

    val notifications: LiveData<List<Notification>> = repository.notifications

    /** Marca como leídas todas las notificaciones del usuario activo. */
    fun markAllRead() {
        viewModelScope.launch {
            repository.markNotificationsRead(session.currentOwner())
        }
    }
}
