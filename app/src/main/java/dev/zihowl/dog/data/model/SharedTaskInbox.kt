package dev.zihowl.dog.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

/**
 * Tarea compartida por un compañero (RQF-APP-45/46). Es un espejo local de la
 * bandeja de entrada del servidor: se persiste para que la pantalla de
 * colaboración funcione sin conexión y para detectar tareas nuevas.
 *
 * `ciphertext`/`encKey` traen el contenido cifrado AES-256; al aceptar la
 * tarea (RQF-APP-46) la app lo descifra y crea una copia local independiente
 * en la tabla `tasks` (RQNF-APP-44).
 */
@Entity(tableName = "shared_task_inbox")
data class SharedTaskInbox(
    @PrimaryKey
    val sharedTaskId: String,
    /** Cuenta local propietaria de esta bandeja (clave de cuenta = correo). */
    val owner: String = "",
    val titlePreview: String = "",
    val scope: String = "SELECTED",
    /** PENDING | ACCEPTED | REJECTED. */
    val status: String = STATUS_PENDING,
    val ownerUsername: String = "",
    val ownerFullName: String = "",
    val ciphertext: String = "",
    val encKey: String = "",
    val createdAt: Long = System.currentTimeMillis()
) : Serializable {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_ACCEPTED = "ACCEPTED"
        const val STATUS_REJECTED = "REJECTED"
    }
}
