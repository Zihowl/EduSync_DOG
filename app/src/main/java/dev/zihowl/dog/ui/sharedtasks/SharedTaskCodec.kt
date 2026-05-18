package dev.zihowl.dog.ui.sharedtasks

import android.util.Base64
import dev.zihowl.dog.data.model.Task
import dev.zihowl.dog.utils.Aes256Crypto
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Date

/**
 * Cifra/descifra el contenido de una tarea compartida (RQF-APP-45). Cada
 * tarea compartida usa su propia clave AES-256 aleatoria; el servidor solo
 * almacena el texto cifrado (RQNF-APP-41/44).
 */
object SharedTaskCodec {

    /** Resultado de cifrar una tarea: texto cifrado y clave (ambos Base64). */
    data class Encrypted(val ciphertext: String, val encKey: String)

    /** Serializa y cifra una tarea con una clave nueva generada al azar. */
    fun encrypt(task: Task): Encrypted? {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val json = JSONObject()
            .put("title", task.title)
            .put("description", task.description ?: JSONObject.NULL)
            .put("dueDate", task.dueDate?.time ?: JSONObject.NULL)
            .put("subjectName", task.subjectName ?: JSONObject.NULL)
            .put("priority", task.priority)
            .toString()
        val ciphertext = Aes256Crypto.encrypt(json, key) ?: return null
        return Encrypted(ciphertext, Base64.encodeToString(key, Base64.NO_WRAP))
    }

    /**
     * Descifra una tarea compartida y la devuelve como una [Task] local nueva
     * (sin id ni owner; el llamador asigna su propia cuenta — RQNF-APP-44).
     */
    fun decrypt(ciphertext: String, encKey: String): Task? {
        return try {
            val key = Base64.decode(encKey, Base64.NO_WRAP)
            val plain = Aes256Crypto.decrypt(ciphertext, key) ?: return null
            val json = JSONObject(plain)
            Task(
                title = json.optString("title"),
                description = json.optString("description").takeIf {
                    it.isNotBlank() && it != "null"
                },
                dueDate = json.optLong("dueDate", 0L).takeIf { it > 0L }?.let { Date(it) },
                status = Task.STATUS_PENDING,
                subjectName = json.optString("subjectName").takeIf {
                    it.isNotBlank() && it != "null"
                },
                priority = json.optString("priority").takeIf { it.isNotBlank() }
                    ?: Task.PRIORITY_MEDIUM
            )
        } catch (e: Exception) {
            null
        }
    }
}
