package dev.zihowl.dog.data.repository

import android.content.Context
import dev.zihowl.dog.data.model.Notification
import dev.zihowl.dog.data.model.Subject
import dev.zihowl.dog.data.remote.CatalogClient
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.notifications.NotificationHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Personalización del horario del alumno, serializada en
 * [SessionManager.scheduleConfigJson].
 *
 * @param discarded materias oficiales que el alumno adelantó/descartó.
 * @param dragged materias arrastradas de otros grupos/subgrupos (semestres
 *   anteriores reprobados), cada una con el grupo del que proviene.
 */
data class ScheduleConfig(
    val discarded: Set<String> = emptySet(),
    val dragged: List<DraggedSubject> = emptyList()
) {
    data class DraggedSubject(val groupId: Int, val name: String)

    fun toJson(): String {
        val draggedArr = JSONArray()
        dragged.forEach {
            draggedArr.put(JSONObject().put("groupId", it.groupId).put("name", it.name))
        }
        return JSONObject()
            .put("discarded", JSONArray(discarded.toList()))
            .put("dragged", draggedArr)
            .toString()
    }

    companion object {
        fun fromJson(raw: String?): ScheduleConfig {
            if (raw.isNullOrBlank()) return ScheduleConfig()
            return runCatching {
                val o = JSONObject(raw)
                val discarded = mutableSetOf<String>()
                o.optJSONArray("discarded")?.let { arr ->
                    for (i in 0 until arr.length()) discarded.add(arr.optString(i))
                }
                val dragged = mutableListOf<DraggedSubject>()
                o.optJSONArray("dragged")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val d = arr.optJSONObject(i) ?: continue
                        dragged.add(DraggedSubject(d.optInt("groupId"), d.optString("name")))
                    }
                }
                ScheduleConfig(discarded, dragged)
            }.getOrDefault(ScheduleConfig())
        }
    }
}

/**
 * Descarga del servidor los horarios publicados y materializa las materias
 * oficiales (solo lectura) en la base de datos local. Reutilizado por la
 * pantalla "Mi grupo y materias" (alumno) y por la importación automática del
 * docente.
 *
 * Además detecta los cambios de salón, horario o docente entre el estado
 * local previo y el recién descargado, y genera las notificaciones
 * correspondientes (RQF-APP-27/28/29). Como el estado previo vive persistido
 * en Room, la detección es offline-first: los cambios ocurridos mientras el
 * dispositivo estuvo sin servidor se notifican en la primera sincronización
 * exitosa tras recuperar el acceso.
 *
 * @param appContext contexto de aplicación para emitir la notificación del
 *   sistema. Si es `null` solo se persiste la entrada en la bandeja in-app.
 */
class OfficialScheduleSyncer(
    private val repository: DogRepository,
    private val session: SessionManager,
    private val weekDays: List<String>,
    private val catalogClient: CatalogClient = CatalogClient(),
    private val appContext: Context? = null
) {
    sealed class Result {
        object Success : Result()
        object NotConfigured : Result()
        data class Error(val message: String) : Result()
        /** El servidor rechazó la sesión: la cuenta ya no es válida. */
        object SessionInvalid : Result()
    }

    /** Sincroniza el horario del alumno según su configuración guardada. */
    suspend fun syncForStudent(): Result {
        val baseUrl = session.serverBaseUrl ?: return Result.Error("Sin servidor")
        val token = session.accessToken ?: return Result.Error("Sin sesión")
        val groupId = session.selectedGroupId
        val subgroupId = session.selectedSubgroupId
        if (groupId <= 0 || subgroupId <= 0) return Result.NotConfigured

        val config = ScheduleConfig.fromJson(session.scheduleConfigJson)
        val groupIds = (listOf(groupId, subgroupId) + config.dragged.map { it.groupId })
            .distinct()

        val slots = when (val r = catalogClient.getPublishedSchedule(baseUrl, token, groupIds)) {
            is CatalogClient.ScheduleResult.Success -> r.slots
            is CatalogClient.ScheduleResult.Error ->
                return Result.Error(r.cause?.message ?: "Error de red")
            CatalogClient.ScheduleResult.Unauthorized -> return Result.SessionInvalid
        }

        val combined = combineStudentSlots(slots, groupId, subgroupId, config)
        // Se pasan las materias descartadas para no notificar como "eliminada
        // por el servidor" una materia que el propio alumno ocultó.
        applyAndNotify(combined, config.discarded)
        return Result.Success
    }

    /** Importa automáticamente el horario publicado del docente autenticado. */
    suspend fun syncForTeacher(): Result {
        val baseUrl = session.serverBaseUrl ?: return Result.Error("Sin servidor")
        val token = session.accessToken ?: return Result.Error("Sin sesión")

        val slots = when (val r = catalogClient.getMyTeacherSchedule(baseUrl, token)) {
            is CatalogClient.ScheduleResult.Success -> r.slots
            is CatalogClient.ScheduleResult.Error ->
                return Result.Error(r.cause?.message ?: "Error de red")
            CatalogClient.ScheduleResult.Unauthorized -> return Result.SessionInvalid
        }
        applyAndNotify(slots, emptySet())
        return Result.Success
    }

    /**
     * Materializa las materias oficiales y, comparando contra el estado
     * previo, genera notificaciones por cada cambio de salón, horario o
     * docente detectado.
     */
    private suspend fun applyAndNotify(
        slots: List<CatalogClient.RemoteSlot>,
        discardedNames: Set<String>
    ) {
        val owner = session.currentOwner()
        val before = repository.getOfficialSubjects(owner).associateBy { it.name }
        repository.syncOfficialSubjects(slots, discardedNames, owner, weekDays)
        val after = repository.getOfficialSubjects(owner).associateBy { it.name }
        detectChanges(before, after, owner, discardedNames)
    }

    /**
     * Compara el estado previo y el recién descargado para emitir
     * notificaciones precisas: materias añadidas/eliminadas del horario
     * publicado y cambios de salón, docente u horario.
     */
    private suspend fun detectChanges(
        before: Map<String, Subject>,
        after: Map<String, Subject>,
        owner: String,
        discardedNames: Set<String>
    ) {
        // Primera materialización: no hay estado previo con qué comparar, así
        // que la importación inicial no genera avisos.
        if (before.isEmpty()) return

        for ((name, newSubject) in after) {
            val oldSubject = before[name]
            if (oldSubject == null) {
                buildPresence(owner, name, Notification.TYPE_ADDED)
                continue
            }
            buildChange(owner, name, Notification.TYPE_TEACHER, oldSubject.professorName, newSubject.professorName)
            buildChange(owner, name, Notification.TYPE_SCHEDULE, oldSubject.schedule, newSubject.schedule)
            buildChange(owner, name, Notification.TYPE_ROOM, oldSubject.classroom, newSubject.classroom)
        }
        for (name in before.keys) {
            if (name in after) continue
            // Excluye las materias que el propio alumno descartó: su
            // desaparición no es un cambio del servidor.
            if (name in discardedNames) continue
            buildPresence(owner, name, Notification.TYPE_REMOVED)
        }
    }

    /** Notificación de materia añadida o eliminada del horario publicado. */
    private suspend fun buildPresence(owner: String, subjectName: String, type: String) {
        val title: String
        val body: String
        if (type == Notification.TYPE_ADDED) {
            title = "Materia añadida"
            body = "Se añadió la materia \"$subjectName\" a tu horario."
        } else {
            title = "Materia eliminada"
            body = "La materia \"$subjectName\" ya no aparece en tu horario."
        }
        val notification = Notification(
            owner = owner,
            title = title,
            body = body,
            type = type,
            subjectName = subjectName,
            newValue = subjectName
        )
        val inserted = repository.recordNotificationIfNew(notification)
        if (inserted) {
            appContext?.let { NotificationHelper.notifyScheduleChange(it, notification) }
        }
    }

    private suspend fun buildChange(
        owner: String,
        subjectName: String,
        type: String,
        oldValue: String?,
        newValue: String?
    ) {
        val current = newValue?.takeIf { it.isNotBlank() } ?: return
        val previous = oldValue.orEmpty()
        if (previous == current) return

        val title: String
        val body: String
        when (type) {
            Notification.TYPE_ROOM -> {
                title = "Cambio de salón"
                body = if (previous.isBlank()) {
                    "$subjectName: nuevo salón → $current"
                } else {
                    "$subjectName: el salón cambió de $previous a $current"
                }
            }
            Notification.TYPE_TEACHER -> {
                title = "Cambio de docente"
                body = if (previous.isBlank()) {
                    "$subjectName: nuevo docente → $current"
                } else {
                    "$subjectName: el docente cambió de $previous a $current"
                }
            }
            else -> {
                title = "Cambio de horario"
                body = if (previous.isBlank()) {
                    "$subjectName: nuevo horario →\n$current"
                } else {
                    // Solo se reportan los bloques que realmente cambiaron, no
                    // todo el horario de la materia.
                    val oldBlocks = previous.split("\n").filter { it.isNotBlank() }
                    val newBlocks = current.split("\n").filter { it.isNotBlank() }
                    val removed = oldBlocks - newBlocks.toSet()
                    val added = newBlocks - oldBlocks.toSet()
                    buildString {
                        append("$subjectName: el horario cambió.")
                        if (removed.isNotEmpty()) {
                            append("\nAntes: ")
                            append(removed.joinToString(" · "))
                        }
                        if (added.isNotEmpty()) {
                            append("\nAhora: ")
                            append(added.joinToString(" · "))
                        }
                    }
                }
            }
        }

        val notification = Notification(
            owner = owner,
            title = title,
            body = body,
            type = type,
            subjectName = subjectName,
            newValue = current,
            oldValue = previous
        )
        // recordNotificationIfNew deduplica contra el último valor conocido:
        // reconexiones repetidas no repiten el aviso.
        val inserted = repository.recordNotificationIfNew(notification)
        if (inserted) {
            appContext?.let { NotificationHelper.notifyScheduleChange(it, notification) }
        }
    }

    /**
     * Combina los bloques del grupo/subgrupo principal (excluyendo materias
     * descartadas) con los bloques de las materias arrastradas.
     */
    fun combineStudentSlots(
        slots: List<CatalogClient.RemoteSlot>,
        groupId: Int,
        subgroupId: Int,
        config: ScheduleConfig
    ): List<CatalogClient.RemoteSlot> {
        val primary = slots.filter {
            (it.groupId == groupId || it.groupId == subgroupId) &&
                it.subjectName !in config.discarded
        }
        val draggedKeys = config.dragged.map { it.groupId to it.name }.toSet()
        val dragged = slots.filter { (it.groupId to it.subjectName) in draggedKeys }
        return primary + dragged
    }
}
