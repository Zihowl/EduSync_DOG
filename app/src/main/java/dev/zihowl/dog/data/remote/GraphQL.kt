package dev.zihowl.dog.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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
        variables: JSONObject
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
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (raw.isBlank()) error("empty response (HTTP ${response.code})")
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
