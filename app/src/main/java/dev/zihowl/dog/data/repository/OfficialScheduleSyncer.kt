package dev.zihowl.dog.data.repository

import dev.zihowl.dog.data.remote.CatalogClient
import dev.zihowl.dog.data.session.SessionManager
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
 */
class OfficialScheduleSyncer(
    private val repository: DogRepository,
    private val session: SessionManager,
    private val weekDays: List<String>,
    private val catalogClient: CatalogClient = CatalogClient()
) {
    sealed class Result {
        object Success : Result()
        object NotConfigured : Result()
        data class Error(val message: String) : Result()
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
        }

        val combined = combineStudentSlots(slots, groupId, subgroupId, config)
        repository.syncOfficialSubjects(combined, emptySet(), session.currentOwner(), weekDays)
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
        }
        repository.syncOfficialSubjects(slots, emptySet(), session.currentOwner(), weekDays)
        return Result.Success
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
