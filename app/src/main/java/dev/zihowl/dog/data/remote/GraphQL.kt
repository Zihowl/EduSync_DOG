package dev.zihowl.dog.data.remote

import dev.zihowl.dog.data.sync.SyncStatusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object GraphQL {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return runCatching { java.net.URL(withScheme); withScheme }.getOrNull()
    }

    suspend fun post(
        client: OkHttpClient,
        baseUrl: String,
        query: String,
        variables: JSONObject,
        bearerToken: String? = null
    ): GraphQLResponse = withContext(Dispatchers.IO) {
        val normalized = normalize(baseUrl) ?: error("invalid base url")
        val body = JSONObject()
            .put("query", query)
            .put("variables", variables)
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url("$normalized/graphql")
            .post(body)
            .addHeader("Accept", "application/json")
            .apply {
                if (!bearerToken.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $bearerToken")
                }
            }
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            // Fallo de red/timeout: el servidor no respondió.
            SyncStatusManager.reportServerUnreachable()
            throw e
        }
        // El servidor respondió HTTP: es accesible (aunque la query falle).
        SyncStatusManager.reportServerReachable()
        response.use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (raw.isBlank()) error("empty response (HTTP ${resp.code})")
            val json = JSONObject(raw)
            val data = json.optJSONObject("data")
            val errors = json.optJSONArray("errors") ?: JSONArray()
            GraphQLResponse(data, errors)
        }
    }
}

internal data class GraphQLResponse(
    val data: JSONObject?,
    val errors: JSONArray
) {
    fun firstErrorMessage(): String? {
        if (errors.length() == 0) return null
        return errors.optJSONObject(0)?.optString("message")?.takeIf { it.isNotBlank() }
    }
}
