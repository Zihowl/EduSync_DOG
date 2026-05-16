package dev.zihowl.dog.data.remote

import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Consulta al servidor el catálogo de grupos y los horarios publicados.
 * Solo los horarios con `isPublished = true` se exponen al alumno/docente
 * (el servidor lo fuerza); los borradores nunca llegan al dispositivo.
 */
class CatalogClient(
    private val client: OkHttpClient = GraphQL.defaultClient
) {

    /** Grupo o subgrupo del catálogo. `parentId == null` => grupo padre. */
    data class RemoteGroup(
        val id: Int,
        val name: String,
        val parentId: Int?,
        val grade: Int?
    )

    /** Un bloque de horario publicado. */
    data class RemoteSlot(
        val groupId: Int,
        val subjectName: String,
        val teacherName: String?,
        val classroomName: String?,
        val dayOfWeek: Int,
        val startTime: String,
        val endTime: String,
        val subgroup: String?
    )

    sealed class GroupsResult {
        data class Success(val groups: List<RemoteGroup>) : GroupsResult()
        data class Error(val cause: Throwable?) : GroupsResult()
    }

    sealed class ScheduleResult {
        data class Success(val slots: List<RemoteSlot>) : ScheduleResult()
        data class Error(val cause: Throwable?) : ScheduleResult()
    }

    suspend fun getGroups(baseUrl: String, accessToken: String): GroupsResult {
        val query = """
            query {
              GetGroupCatalog { id name parentId grade }
            }
        """.trimIndent()
        return runCatching {
            GraphQL.post(client, baseUrl, query, JSONObject(), bearerToken = accessToken)
        }.fold(
            onSuccess = { resp ->
                val msg = resp.firstErrorMessage()
                if (msg != null) {
                    GroupsResult.Error(IllegalStateException(msg))
                } else {
                    val arr = resp.data?.optJSONArray("GetGroupCatalog") ?: JSONArray()
                    GroupsResult.Success(parseGroups(arr))
                }
            },
            onFailure = { GroupsResult.Error(it) }
        )
    }

    suspend fun getPublishedSchedule(
        baseUrl: String,
        accessToken: String,
        groupIds: List<Int>
    ): ScheduleResult {
        if (groupIds.isEmpty()) return ScheduleResult.Success(emptyList())
        val query = """
            query(${'$'}ids: [Int!]!) {
              GetPublishedSchedule(groupIds: ${'$'}ids) {
                groupId dayOfWeek startTime endTime subgroup
                subject { name }
                teacher { name }
                classroom { name }
              }
            }
        """.trimIndent()
        val variables = JSONObject().put("ids", JSONArray(groupIds))
        return fetchSlots(baseUrl, accessToken, query, variables, "GetPublishedSchedule")
    }

    suspend fun getMyTeacherSchedule(baseUrl: String, accessToken: String): ScheduleResult {
        val query = """
            query {
              GetMyTeacherSchedule {
                groupId dayOfWeek startTime endTime subgroup
                subject { name }
                teacher { name }
                classroom { name }
              }
            }
        """.trimIndent()
        return fetchSlots(baseUrl, accessToken, query, JSONObject(), "GetMyTeacherSchedule")
    }

    private suspend fun fetchSlots(
        baseUrl: String,
        accessToken: String,
        query: String,
        variables: JSONObject,
        field: String
    ): ScheduleResult {
        return runCatching {
            GraphQL.post(client, baseUrl, query, variables, bearerToken = accessToken)
        }.fold(
            onSuccess = { resp ->
                val msg = resp.firstErrorMessage()
                if (msg != null) {
                    ScheduleResult.Error(IllegalStateException(msg))
                } else {
                    val arr = resp.data?.optJSONArray(field) ?: JSONArray()
                    ScheduleResult.Success(parseSlots(arr))
                }
            },
            onFailure = { ScheduleResult.Error(it) }
        )
    }

    private fun parseGroups(arr: JSONArray): List<RemoteGroup> {
        val out = mutableListOf<RemoteGroup>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").toIntOrNull() ?: continue
            out.add(
                RemoteGroup(
                    id = id,
                    name = o.optString("name"),
                    parentId = if (o.isNull("parentId")) null else o.optInt("parentId"),
                    grade = if (o.isNull("grade")) null else o.optInt("grade")
                )
            )
        }
        return out
    }

    private fun parseSlots(arr: JSONArray): List<RemoteSlot> {
        val out = mutableListOf<RemoteSlot>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val subjectName = o.optJSONObject("subject")?.optString("name").orEmpty()
            if (subjectName.isBlank()) continue
            out.add(
                RemoteSlot(
                    groupId = o.optInt("groupId"),
                    subjectName = subjectName,
                    teacherName = o.optJSONObject("teacher")
                        ?.optString("name")?.takeIf { it.isNotBlank() },
                    classroomName = o.optJSONObject("classroom")
                        ?.optString("name")?.takeIf { it.isNotBlank() },
                    dayOfWeek = o.optInt("dayOfWeek"),
                    startTime = o.optString("startTime"),
                    endTime = o.optString("endTime"),
                    subgroup = if (o.isNull("subgroup")) null
                    else o.optString("subgroup").takeIf { it.isNotBlank() }
                )
            )
        }
        return out
    }
}
