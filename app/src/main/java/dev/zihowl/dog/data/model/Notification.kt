package dev.zihowl.dog.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

/**
 * Notificación de la bandeja in-app (RQF-APP-27/28/29). Se genera cuando una
 * sincronización del horario oficial detecta un cambio de salón, horario o
 * docente en alguna de las materias suscritas del usuario.
 *
 * Se persiste en Room para garantizar la entrega diferida: si el cambio
 * ocurrió mientras el dispositivo estuvo sin servidor, la notificación queda
 * disponible en la bandeja al recuperar el acceso.
 */
@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val owner: String = "",
    val title: String,
    val body: String,
    /**
     * Uno de [TYPE_ROOM], [TYPE_SCHEDULE], [TYPE_TEACHER], [TYPE_ADDED],
     * [TYPE_REMOVED].
     */
    val type: String,
    val subjectName: String,
    val newValue: String,
    /** Valor anterior al cambio; vacío si no aplica o no había valor previo. */
    val oldValue: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
) : Serializable {
    companion object {
        const val TYPE_ROOM = "ROOM"
        const val TYPE_SCHEDULE = "SCHEDULE"
        const val TYPE_TEACHER = "TEACHER"
        /** La materia apareció en el horario publicado (mostrada). */
        const val TYPE_ADDED = "ADDED"
        /** La materia dejó de aparecer en el horario publicado (oculta). */
        const val TYPE_REMOVED = "REMOVED"
        /** Un compañero te compartió una tarea (RQF-APP-46). */
        const val TYPE_TASK_SHARED = "TASK_SHARED"
        /** Un destinatario aceptó o rechazó una tarea que compartiste. */
        const val TYPE_TASK_RESPONSE = "TASK_RESPONSE"
        /** Recibiste un recordatorio (toque) sobre una tarea (RQF-APP-47). */
        const val TYPE_REMINDER = "REMINDER"
    }
}
