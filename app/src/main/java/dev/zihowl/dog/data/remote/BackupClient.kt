package dev.zihowl.dog.data.remote

import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * Sube y descarga el respaldo cifrado del usuario autenticado. El servidor solo
 * recibe y devuelve texto cifrado (`ciphertext`); nunca datos en claro.
 */
class BackupClient(
    private val client: OkHttpClient = GraphQL.defaultClient
) {

    sealed class UploadResult {
        data object Success : UploadResult()
        data class Error(val cause: Throwable?) : UploadResult()
    }

    sealed class DownloadResult {
        /** [ciphertext] es null si el usuario aún no tiene respaldo en servidor. */
        data class Success(val ciphertext: String?) : DownloadResult()
        data class Error(val cause: Throwable?) : DownloadResult()
    }

    suspend fun uploadBackup(
        baseUrl: String,
        accessToken: String,
        ciphertext: String
    ): UploadResult {
        val query = """
            mutation(${'$'}ciphertext: String!) {
              uploadBackup(ciphertext: ${'$'}ciphertext) { updatedAt }
            }
        """.trimIndent()
        val variables = JSONObject().put("ciphertext", ciphertext)
        return runCatching {
            GraphQL.post(client, baseUrl, query, variables, bearerToken = accessToken)
        }.fold(
            onSuccess = { resp ->
                val msg = resp.firstErrorMessage()
                if (msg != null) UploadResult.Error(IllegalStateException(msg))
                else UploadResult.Success
            },
            onFailure = { UploadResult.Error(it) }
        )
    }

    suspend fun downloadBackup(baseUrl: String, accessToken: String): DownloadResult {
        val query = """
            query {
              myBackup { ciphertext }
            }
        """.trimIndent()
        return runCatching {
            GraphQL.post(client, baseUrl, query, JSONObject(), bearerToken = accessToken)
        }.fold(
            onSuccess = { resp ->
                val msg = resp.firstErrorMessage()
                if (msg != null) {
                    DownloadResult.Error(IllegalStateException(msg))
                } else {
                    val obj = resp.data?.optJSONObject("myBackup")
                    val ciphertext = obj?.optString("ciphertext")?.takeIf { it.isNotBlank() }
                    DownloadResult.Success(ciphertext)
                }
            },
            onFailure = { DownloadResult.Error(it) }
        )
    }
}
