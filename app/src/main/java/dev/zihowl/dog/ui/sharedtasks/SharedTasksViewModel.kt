package dev.zihowl.dog.ui.sharedtasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dev.zihowl.dog.DogApplication
import dev.zihowl.dog.data.model.Notification
import dev.zihowl.dog.data.model.SharedTaskInbox
import dev.zihowl.dog.data.remote.CollaborationClient
import dev.zihowl.dog.data.repository.DogRepository
import dev.zihowl.dog.data.session.SessionManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Pantalla de colaboración de tareas (RQF-APP-45/46/47): bandeja de tareas
 * recibidas (aceptar/rechazar) y tareas enviadas (recordatorios/toques).
 */
class SharedTasksViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DogRepository = runBlocking {
        (application as DogApplication).repository()
    }
    private val session = SessionManager(application)
    private val client = CollaborationClient()

    /** Bandeja de entrada local (espejo del servidor), reactiva por cuenta. */
    val inbox: LiveData<List<SharedTaskInbox>> = repository.sharedTaskInbox

    private val _outbox = MutableLiveData<List<CollaborationClient.OutboxItem>>(emptyList())
    val outbox: LiveData<List<CollaborationClient.OutboxItem>> = _outbox

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    /** ¿La cuenta puede colaborar? Requiere sesión con servidor (no invitado). */
    fun canCollaborate(): Boolean =
        !session.isGuestMode && !session.serverBaseUrl.isNullOrBlank() &&
            !session.accessToken.isNullOrBlank()

    /** Sincroniza la bandeja de entrada y de salida con el servidor. */
    fun refresh() {
        if (!canCollaborate()) return
        val baseUrl = session.serverBaseUrl ?: return
        val token = session.accessToken ?: return
        val owner = session.currentOwner()

        _loading.value = true
        viewModelScope.launch {
            val known = repository.knownSharedTaskIds(owner)
            val remoteInbox = client.inbox(baseUrl, token)
            for (item in remoteInbox) {
                repository.upsertSharedTask(
                    SharedTaskInbox(
                        sharedTaskId = item.sharedTaskId,
                        owner = owner,
                        titlePreview = item.titlePreview,
                        scope = item.scope,
                        status = item.status,
                        ownerUsername = item.ownerUsername,
                        ownerFullName = item.ownerFullName,
                        ciphertext = item.ciphertext,
                        encKey = item.encKey
                    )
                )
                // Notifica solo las tareas compartidas nuevas (RQF-APP-46).
                if (item.sharedTaskId !in known && item.status == "PENDING") {
                    repository.addNotification(
                        Notification(
                            owner = owner,
                            title = "Nueva tarea compartida",
                            body = "${item.ownerFullName} (@${item.ownerUsername}) " +
                                "te compartió: ${item.titlePreview}",
                            type = Notification.TYPE_TASK_SHARED,
                            subjectName = item.titlePreview,
                            newValue = item.titlePreview
                        )
                    )
                }
            }
            _outbox.value = client.outbox(baseUrl, token)
            _loading.value = false
        }
    }

    /** Acepta o rechaza una tarea compartida (RQF-APP-46). */
    fun respond(item: SharedTaskInbox, accept: Boolean) {
        if (!canCollaborate()) {
            _message.value = "Esta función requiere conexión con el servidor"
            return
        }
        val baseUrl = session.serverBaseUrl ?: return
        val token = session.accessToken ?: return
        val owner = session.currentOwner()

        _loading.value = true
        viewModelScope.launch {
            when (val result = client.respondSharedTask(baseUrl, token, item.sharedTaskId, accept)) {
                is CollaborationClient.RespondResult.Success -> {
                    val newStatus = if (accept) SharedTaskInbox.STATUS_ACCEPTED
                    else SharedTaskInbox.STATUS_REJECTED
                    repository.updateSharedTaskStatus(item.sharedTaskId, newStatus)
                    if (accept && result.ciphertext != null && result.encKey != null) {
                        // Crea una copia local independiente de la tarea (RQNF-APP-44).
                        val task = SharedTaskCodec.decrypt(result.ciphertext, result.encKey)
                        if (task != null) {
                            repository.addTask(task.copy(owner = owner))
                            _message.value = "Tarea aceptada y agregada a tus tareas"
                        } else {
                            _message.value = "No se pudo descifrar la tarea compartida"
                        }
                    } else {
                        _message.value = "Tarea rechazada"
                    }
                }
                is CollaborationClient.RespondResult.Error -> {
                    _message.value = result.message ?: "No se pudo procesar la respuesta"
                }
            }
            _loading.value = false
        }
    }

    /** Envía un recordatorio (toque) a un compañero (RQF-APP-47). */
    fun sendReminder(sharedTaskId: String, recipient: CollaborationClient.RecipientStatus) {
        if (!canCollaborate()) {
            _message.value = "Esta función requiere conexión con el servidor"
            return
        }
        val baseUrl = session.serverBaseUrl ?: return
        val token = session.accessToken ?: return

        _loading.value = true
        viewModelScope.launch {
            when (val result = client.sendTaskReminder(baseUrl, token, sharedTaskId, recipient.userId)) {
                is CollaborationClient.ReminderResult.Success ->
                    _message.value = "Recordatorio enviado a ${recipient.fullName}. " +
                        "Te quedan ${result.remaining} hoy."
                is CollaborationClient.ReminderResult.LimitReached ->
                    _message.value = "Límite alcanzado: máximo 3 recordatorios por compañero en 24 h"
                is CollaborationClient.ReminderResult.Error ->
                    _message.value = result.message ?: "No se pudo enviar el recordatorio"
            }
            _outbox.value = client.outbox(baseUrl, token)
            _loading.value = false
        }
    }
}
