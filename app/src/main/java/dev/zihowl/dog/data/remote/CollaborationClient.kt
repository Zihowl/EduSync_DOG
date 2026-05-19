package dev.zihowl.dog.data.remote

import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Identidad mostrable de un usuario: su nombre si lo tiene (docentes) o su
 * `@usuario` si no (alumnos, que ya no registran nombre).
 */
fun displayIdentity(fullName: String, username: String): String =
    fullName.takeIf { it.isNotBlank() } ?: "@$username"

/**
 * Cliente GraphQL de la colaboración de tareas (RQF-APP-45/46/47): publica el
 * perfil académico, lista compañeros candidatos, comparte tareas, responde y
 * envía recordatorios.
 */
class CollaborationClient(
    private val client: OkHttpClient = GraphQL.defaultClient
) {

    data class ShareCandidate(
        val userId: String,
        val username: String,
        val fullName: String,
        val role: String
    )

    data class InboxItem(
        val sharedTaskId: String,
        val ciphertext: String,
        val encKey: String,
        val scope: String,
        val titlePreview: String,
        val status: String,
        val ownerUsername: String,
        val ownerFullName: String
    )

    data class RecipientStatus(
        val userId: String,
        val username: String,
        val fullName: String,
        val status: String,
        val remindersSent24h: Int
    )

    data class OutboxItem(
        val sharedTaskId: String,
        val scope: String,
        val titlePreview: String,
        val recipients: List<RecipientStatus>
    )

    sealed class ShareResult {
        data class Success(val sharedTaskId: String, val recipientCount: Int) : ShareResult()
        data class Error(val message: String?) : ShareResult()
    }

    sealed class RespondResult {
        data class Success(val accepted: Boolean, val ciphertext: String?, val encKey: String?) :
            RespondResult()
        data class Error(val message: String?) : RespondResult()
    }

    sealed class ReminderResult {
        data class Success(val remaining: Int) : ReminderResult()
        /** Se alcanzó el límite de 3 recordatorios en 24 h (RQNF-APP-45). */
        object LimitReached : ReminderResult()
        data class Error(val message: String?) : ReminderResult()
    }

    /** Publica el grupo/subgrupo del alumno para derivar compañeros (RQNF-APP-43). */
    suspend fun setAcademicProfile(
        baseUrl: String,
        accessToken: String,
        groupId: Int?,
        subgroupId: Int?
    ): Boolean {
        val query = """
            mutation(${'$'}i: AcademicProfileInput!) { SetAcademicProfile(input: ${'$'}i) }
        """.trimIndent()
        val input = JSONObject()
        if (groupId != null && groupId > 0) input.put("groupId", groupId)
        if (subgroupId != null && subgroupId > 0) input.put("subgroupId", subgroupId)
        val variables = JSONObject().put("i", input)
        return runCatching {
            GraphQL.post(client, baseUrl, query, variables, bearerToken = accessToken)
        }.fold(
            onSuccess = { it.firstErrorMessage() == null },
            onFailure = { false }
        )
    }

    suspend fun shareCandidates(
        baseUrl: String,
        accessToken: String,
        search: String?
    ): List<ShareCandidate> {
        val query = """
            query(${'$'}s: String) {
              ShareCandidates(search: ${'$'}s) { userId username fullName role }
            }
        """.trimIndent()
        val variables = JSONObject()
        if (!search.isNullOrBlank()) variables.put("s", search.trim())
        return runCatching {
            GraphQL.post(client, baseUrl, query, variables, bearerToken = accessToken)
        }.fold(
            onSuccess = { resp ->
                val arr = resp.data?.optJSONArray("ShareCandidates") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let {
                        ShareCandidate(
                            userId = it.optString("userId"),
                            username = it.optString("username"),
                            fullName = it.optString("fullName"),
                            role = it.optString("role")
                        )
                    }
                }
            },
            onFailure = { emptyList() }
        )
    }

    suspend fun shareTask(
        baseUrl: String,
        accessToken: String,
        ciphertext: String,
        encKey: String,
        scope: String,
        titlePreview: String,
        recipientIds: List<String>
    ): ShareResult {
        val query = """
            mutation(${'$'}i: ShareTaskInput!) {
              ShareTask(input: ${'$'}i) { sharedTaskId recipientCount }
            }
        """.trimIndent()
        val input = JSONObject()
            .put("ciphertext", ciphertext)
            .put("encKey", encKey)
            .put("scope", scope)
            .put("titlePreview", titlePreview)
            .put("recipientIds", JSONArray(recipientIds))
        val variables = JSONObject().put("i", input)
        return runCatching {
            GraphQL.post(client, baseUrl, query, variables, bearerToken = accessToken)
        }.fold(
            onSuccess = { resp ->
                val msg = resp.firstErrorMessage()
                if (msg != null) {
                    ShareResult.Error(msg)
                } else {
                    val r = resp.data?.optJSONObject("ShareTask")
                    if (r == null) ShareResult.Error(null)
                    else ShareResult.Success(
                        r.optString("sharedTaskId"),
                        r.optInt("recipientCount", 0)
                    )
                }
            },
            onFailure = { ShareResult.Error(it.message) }
        )
    }

    suspend fun respondSharedTask(
        baseUrl: String,
        accessToken: String,
        sharedTaskId: String,
        accept: Boolean
    ): RespondResult {
        val query = """
            mutation(${'$'}i: RespondSharedTaskInput!) {
              RespondSharedTask(input: ${'$'}i) { accepted ciphertext encKey }
            }
        """.trimIndent()
        val input = JSONObject()
            .put("sharedTaskId", sharedTaskId)
            .put("accept", accept)
        val variables = JSONObject().put("i", input)
        return runCatching {
            GraphQL.post(client, baseUrl, query, variables, bearerToken = accessToken)
        }.fold(
            onSuccess = { resp ->
                val msg = resp.firstErrorMessage()
                if (msg != null) {
                    RespondResult.Error(msg)
                } else {
                    val r = resp.data?.optJSONObject("RespondSharedTask")
                    if (r == null) RespondResult.Error(null)
                    else RespondResult.Success(
                        accepted = r.optBoolean("accepted", false),
                        ciphertext = r.optString("ciphertext").takeIf { it.isNotBlank() },
                        encKey = r.optString("encKey").takeIf { it.isNotBlank() }
                    )
                }
            },
            onFailure = { RespondResult.Error(it.message) }
        )
    }

    suspend fun sendTaskReminder(
        baseUrl: String,
        accessToken: String,
        sharedTaskId: String,
        recipientId: String
    ): ReminderResult {
        val query = """
            mutation(${'$'}i: SendTaskReminderInput!) {
              SendTaskReminder(input: ${'$'}i) { remaining }
            }
        """.trimIndent()
        val input = JSONObject()
            .put("sharedTaskId", sharedTaskId)
            .put("recipientId", recipientId)
        val variables = JSONObject().put("i", input)
        return runCatching {
            GraphQL.post(client, baseUrl, query, variables, bearerToken = accessToken)
        }.fold(
            onSuccess = { resp ->
                val msg = resp.firstErrorMessage()
                when {
                    msg == null -> {
                        val remaining = resp.data
                            ?.optJSONObject("SendTaskReminder")
                            ?.optInt("remaining", 0) ?: 0
                        ReminderResult.Success(remaining)
                    }
                    msg.contains("límite", ignoreCase = true) ||
                        msg.contains("limite", ignoreCase = true) -> ReminderResult.LimitReached
                    else -> ReminderResult.Error(msg)
                }
            },
            onFailure = { ReminderResult.Error(it.message) }
        )
    }

    suspend fun inbox(baseUrl: String, accessToken: String): List<InboxItem> {
        val query = """
            query {
              SharedTaskInbox {
                sharedTaskId ciphertext encKey scope titlePreview status
                ownerUsername ownerFullName
              }
            }
        """.trimIndent()
        return runCatching {
            GraphQL.post(client, baseUrl, query, JSONObject(), bearerToken = accessToken)
        }.fold(
            onSuccess = { resp ->
                val arr = resp.data?.optJSONArray("SharedTaskInbox") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let {
                        InboxItem(
                            sharedTaskId = it.optString("sharedTaskId"),
                            ciphertext = it.optString("ciphertext"),
                            encKey = it.optString("encKey"),
                            scope = it.optString("scope"),
                            titlePreview = it.optString("titlePreview"),
                            status = it.optString("status"),
                            ownerUsername = it.optString("ownerUsername"),
                            ownerFullName = it.optString("ownerFullName")
                        )
                    }
                }
            },
            onFailure = { emptyList() }
        )
    }

    suspend fun outbox(baseUrl: String, accessToken: String): List<OutboxItem> {
        val query = """
            query {
              SharedTaskOutbox {
                sharedTaskId scope titlePreview
                recipients { userId username fullName status remindersSent24h }
              }
            }
        """.trimIndent()
        return runCatching {
            GraphQL.post(client, baseUrl, query, JSONObject(), bearerToken = accessToken)
        }.fold(
            onSuccess = { resp ->
                val arr = resp.data?.optJSONArray("SharedTaskOutbox") ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    val recArr = obj.optJSONArray("recipients") ?: JSONArray()
                    val recipients = (0 until recArr.length()).mapNotNull { j ->
                        recArr.optJSONObject(j)?.let {
                            RecipientStatus(
                                userId = it.optString("userId"),
                                username = it.optString("username"),
                                fullName = it.optString("fullName"),
                                status = it.optString("status"),
                                remindersSent24h = it.optInt("remindersSent24h", 0)
                            )
                        }
                    }
                    OutboxItem(
                        sharedTaskId = obj.optString("sharedTaskId"),
                        scope = obj.optString("scope"),
                        titlePreview = obj.optString("titlePreview"),
                        recipients = recipients
                    )
                }
            },
            onFailure = { emptyList() }
        )
    }
}
