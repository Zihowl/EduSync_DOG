package dev.zihowl.dog.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ServerHealthClient(
    private val client: OkHttpClient = defaultClient
) {
    suspend fun check(baseUrl: String): Result<String> = withContext(Dispatchers.IO) {
        val normalized = normalize(baseUrl)
            ?: return@withContext Result.failure(IllegalArgumentException("invalid url"))
        val request = Request.Builder()
            .url("$normalized/health")
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                val status = JSONObject(body).optString("status")
                if (status != "ok") error("unexpected status: $status")
                normalized
            }
        }
    }

    // Reusa la normalización de GraphQL: un host público se fuerza a https
    // para no caer en el redirect 301 del túnel Cloudflare.
    private fun normalize(raw: String): String? = GraphQL.normalize(raw)

    companion object {
        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }
}
