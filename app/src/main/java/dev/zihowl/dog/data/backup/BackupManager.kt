package dev.zihowl.dog.data.backup

import dev.zihowl.dog.data.model.ManualEvent
import dev.zihowl.dog.data.model.Note
import dev.zihowl.dog.data.model.Subject
import dev.zihowl.dog.data.model.Task
import dev.zihowl.dog.data.remote.BackupClient
import dev.zihowl.dog.data.repository.DogRepository
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.utils.Aes256Crypto
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * Respalda y recupera los datos personales del usuario contra el servidor.
 *
 * El JSON con tareas/notas/materias se cifra en el dispositivo con AES-256-GCM
 * usando una clave derivada de la contraseña ([BackupCrypto]); el servidor solo
 * almacena el texto cifrado. Las materias oficiales no se respaldan: provienen
 * del catálogo y se re-sincronizan al iniciar sesión.
 */
class BackupManager(
    private val repository: DogRepository,
    private val session: SessionManager,
    private val backupClient: BackupClient = BackupClient()
) {

    sealed class Result {
        data object Success : Result()
        /** No había nada que respaldar / restaurar; se considera éxito silencioso. */
        data object Empty : Result()
        data class Error(val message: String) : Result()
    }

    /** Cifra y sube al servidor todos los datos locales de [owner]. */
    suspend fun uploadBackup(owner: String): Result {
        val baseUrl = session.serverBaseUrl ?: return Result.Error("Sin servidor")
        val token = session.accessToken ?: return Result.Error("Sin sesión")
        val key = backupKey() ?: return Result.Error("Sin clave de cifrado")

        val subjects = repository.getAllSubjectsList(owner).filter { !it.isOfficial }
        val tasks = repository.getAllTasksList(owner)
        val notes = repository.getAllNotesList(owner)
        val events = repository.getAllManualEventsList(owner)

        if (subjects.isEmpty() && tasks.isEmpty() && notes.isEmpty() && events.isEmpty()) {
            return Result.Empty
        }

        val json = serialize(subjects, tasks, notes, events)
        val ciphertext = Aes256Crypto.encrypt(json, key)
            ?: return Result.Error("No se pudo cifrar el respaldo")

        return when (val r = backupClient.uploadBackup(baseUrl, token, ciphertext)) {
            is BackupClient.UploadResult.Success -> Result.Success
            is BackupClient.UploadResult.Error ->
                Result.Error(r.cause?.message ?: "Error de red al respaldar")
        }
    }

    /**
     * Descarga el respaldo del servidor, lo descifra e inserta los registros
     * bajo [owner]. No duplica: pensado para ejecutarse tras un login.
     */
    suspend fun downloadBackup(owner: String): Result {
        val baseUrl = session.serverBaseUrl ?: return Result.Error("Sin servidor")
        val token = session.accessToken ?: return Result.Error("Sin sesión")
        val key = backupKey() ?: return Result.Error("Sin clave de cifrado")

        val ciphertext = when (val r = backupClient.downloadBackup(baseUrl, token)) {
            is BackupClient.DownloadResult.Success -> r.ciphertext ?: return Result.Empty
            is BackupClient.DownloadResult.Error ->
                return Result.Error(r.cause?.message ?: "Error de red al recuperar")
        }

        val json = Aes256Crypto.decrypt(ciphertext, key)
            ?: return Result.Error("No se pudo descifrar el respaldo")

        val parsed = runCatching { deserialize(json, owner) }.getOrNull()
            ?: return Result.Error("Respaldo corrupto")

        repository.restoreRecords(parsed.subjects, parsed.tasks, parsed.notes, parsed.events)
        return Result.Success
    }

    private fun backupKey(): ByteArray? =
        session.backupKeyBase64?.let { runCatching { BackupCrypto.decodeKey(it) }.getOrNull() }

    // --- Serialización ---------------------------------------------------

    private data class Parsed(
        val subjects: List<Subject>,
        val tasks: List<Task>,
        val notes: List<Note>,
        val events: List<ManualEvent>
    )

    private fun serialize(
        subjects: List<Subject>,
        tasks: List<Task>,
        notes: List<Note>,
        events: List<ManualEvent>
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("subjects", JSONArray().apply {
            subjects.forEach { s ->
                put(JSONObject().apply {
                    put("name", s.name)
                    put("professorName", s.professorName)
                    put("schedule", s.schedule)
                })
            }
        })
        root.put("tasks", JSONArray().apply {
            tasks.forEach { t ->
                put(JSONObject().apply {
                    put("title", t.title)
                    put("description", t.description)
                    put("dueDate", t.dueDate?.time)
                    put("status", t.status)
                    put("subjectName", t.subjectName)
                    put("priority", t.priority)
                })
            }
        })
        root.put("notes", JSONArray().apply {
            notes.forEach { n ->
                put(JSONObject().apply {
                    put("title", n.title)
                    put("content", n.content)
                    put("subjectName", n.subjectName)
                })
            }
        })
        root.put("events", JSONArray().apply {
            events.forEach { e ->
                put(JSONObject().apply {
                    put("title", e.title)
                    put("location", e.location)
                    put("startTime", e.startTime)
                    put("endTime", e.endTime)
                    put("frequencyType", e.frequencyType)
                    put("dayOfWeek", e.dayOfWeek)
                    put("date", e.date?.time)
                })
            }
        })
        return root.toString()
    }

    private fun deserialize(json: String, owner: String): Parsed {
        val root = JSONObject(json)
        val subjects = root.optJSONArray("subjects").map { o ->
            Subject(
                name = o.getString("name"),
                professorName = o.optStringOrNull("professorName"),
                schedule = o.optStringOrNull("schedule"),
                owner = owner
            )
        }
        val tasks = root.optJSONArray("tasks").map { o ->
            Task(
                title = o.getString("title"),
                description = o.optStringOrNull("description"),
                dueDate = o.optLongOrNull("dueDate")?.let { Date(it) },
                status = o.optString("status", Task.STATUS_PENDING),
                subjectName = o.optStringOrNull("subjectName"),
                owner = owner,
                priority = o.optString("priority", "MEDIUM")
            )
        }
        val notes = root.optJSONArray("notes").map { o ->
            Note(
                title = o.getString("title"),
                content = o.optStringOrNull("content"),
                subjectName = o.optStringOrNull("subjectName"),
                owner = owner
            )
        }
        val events = root.optJSONArray("events").map { o ->
            ManualEvent(
                title = o.getString("title"),
                location = o.optStringOrNull("location"),
                startTime = o.getString("startTime"),
                endTime = o.getString("endTime"),
                frequencyType = o.getString("frequencyType"),
                dayOfWeek = o.optIntOrNull("dayOfWeek"),
                date = o.optLongOrNull("date")?.let { Date(it) },
                owner = owner
            )
        }
        return Parsed(subjects, tasks, notes, events)
    }

    private fun <T> JSONArray?.map(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (i in 0 until length()) {
            optJSONObject(i)?.let { out.add(transform(it)) }
        }
        return out
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key)
}
