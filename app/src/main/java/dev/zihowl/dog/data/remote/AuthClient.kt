package dev.zihowl.dog.data.remote

import okhttp3.OkHttpClient
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class AuthClient(
    private val client: OkHttpClient = GraphQL.defaultClient
) {

    sealed class LoginResult {
        data class Success(
            val accessToken: String,
            val expiresIn: Long,
            val serverRole: String?,
            val email: String?,
            val fullName: String?
        ) : LoginResult()
        object InvalidCredentials : LoginResult()
        data class Locked(val unlockAtEpochMs: Long) : LoginResult()
        object InactiveAccount : LoginResult()
        object RoleNotAllowed : LoginResult()
        data class Error(val cause: Throwable?) : LoginResult()
    }

    sealed class RegisterResult {
        data class Success(val verificationToken: String, val expiresAtEpochMs: Long) : RegisterResult()
        object DomainNotAllowed : RegisterResult()
        object WeakPassword : RegisterResult()
        object PasswordMismatch : RegisterResult()
        object EmailAlreadyExists : RegisterResult()
        data class Error(val cause: Throwable?) : RegisterResult()
    }

    sealed class VerifyResult {
        data class Success(val accessToken: String?, val serverRole: String?) : VerifyResult()
        object InvalidOrExpired : VerifyResult()
        data class Error(val cause: Throwable?) : VerifyResult()
    }

    sealed class PasswordResetResult {
        data class Success(val verificationToken: String, val expiresAtEpochMs: Long) :
            PasswordResetResult()
        object EmailNotFound : PasswordResetResult()
        data class Error(val cause: Throwable?) : PasswordResetResult()
    }

    sealed class VerifyResetResult {
        object Success : VerifyResetResult()
        object InvalidOrExpired : VerifyResetResult()
        data class Error(val cause: Throwable?) : VerifyResetResult()
    }

    sealed class CompleteResetResult {
        object Success : CompleteResetResult()
        object WeakPassword : CompleteResetResult()
        object PasswordMismatch : CompleteResetResult()
        object SamePassword : CompleteResetResult()
        object InvalidOrExpired : CompleteResetResult()
        data class Error(val cause: Throwable?) : CompleteResetResult()
    }

    suspend fun login(baseUrl: String, email: String, password: String): LoginResult {
        val query = """
            mutation(${'$'}i: LoginInput!) {
              Login(loginInput: ${'$'}i) {
                accessToken
                expiresIn
                user { email fullName role }
              }
            }
        """.trimIndent()
        val variables = JSONObject().put(
            "i",
            JSONObject()
                .put("email", email)
                .put("password", password)
                .put("platform", "MOBILE")
        )
        return runCatching { GraphQL.post(client, baseUrl, query, variables) }.fold(
            onSuccess = { resp ->
                val msg = resp.firstErrorMessage()
                if (msg != null) {
                    classifyLoginError(msg)
                } else {
                    val login = resp.data?.optJSONObject("Login")
                        ?: return LoginResult.Error(IllegalStateException("missing data.Login"))
                    val user = login.optJSONObject("user")
                    LoginResult.Success(
                        accessToken = login.optString("accessToken"),
                        expiresIn = login.optLong("expiresIn", 0L),
                        serverRole = user?.optStringOrNull("role"),
                        email = user?.optStringOrNull("email"),
                        fullName = user?.optStringOrNull("fullName")
                    )
                }
            },
            onFailure = { LoginResult.Error(it) }
        )
    }

    suspend fun register(
        baseUrl: String,
        email: String,
        password: String,
        passwordConfirmation: String
    ): RegisterResult {
        val query = """
            mutation(${'$'}i: RegisterInput!) {
              Register(registerInput: ${'$'}i) {
                verificationToken
                expiresAt
              }
            }
        """.trimIndent()
        val variables = JSONObject().put(
            "i",
            JSONObject()
                .put("email", email)
                .put("password", password)
                .put("passwordConfirmation", passwordConfirmation)
        )
        return runCatching { GraphQL.post(client, baseUrl, query, variables) }.fold(
            onSuccess = { resp ->
                val msg = resp.firstErrorMessage()
                if (msg != null) {
                    classifyRegisterError(msg)
                } else {
                    val reg = resp.data?.optJSONObject("Register")
                        ?: return RegisterResult.Error(IllegalStateException("missing data.Register"))
                    RegisterResult.Success(
                        verificationToken = reg.optString("verificationToken"),
                        expiresAtEpochMs = parseEpochMs(reg.optString("expiresAt"))
                            ?: (System.currentTimeMillis() + 10 * 60 * 1000)
                    )
                }
            },
            onFailure = { RegisterResult.Error(it) }
        )
    }

    suspend fun verifyEmail(
        baseUrl: String,
        verificationToken: String,
        code: String
    ): VerifyResult {
        val query = """
            mutation(${'$'}i: VerifyEmailInput!) {
              VerifyEmail(verifyInput: ${'$'}i) {
                accessToken
                user { role }
              }
            }
        """.trimIndent()
        val variables = JSONObject().put(
            "i",
            JSONObject().put("verificationToken", verificationToken).put("code", code)
        )
        return runCatching { GraphQL.post(client, baseUrl, query, variables) }.fold(
            onSuccess = { resp ->
                val msg = resp.firstErrorMessage()
                if (msg != null) {
                    if (msg.contains("expirado", ignoreCase = true) ||
                        msg.contains("incorrecto", ignoreCase = true) ||
                        msg.contains("inválido", ignoreCase = true)
                    ) {
                        VerifyResult.InvalidOrExpired
                    } else {
                        VerifyResult.Error(IllegalStateException(msg))
                    }
                } else {
                    val v = resp.data?.optJSONObject("VerifyEmail")
                        ?: return VerifyResult.Error(IllegalStateException("missing data.VerifyEmail"))
                    VerifyResult.Success(
                        accessToken = v.optString("accessToken").takeIf { it.isNotBlank() },
                        serverRole = v.optJSONObject("user")?.optString("role")
                    )
                }
            },
            onFailure = { VerifyResult.Error(it) }
        )
    }

    suspend fun requestPasswordReset(baseUrl: String, email: String): PasswordResetResult {
        val query = """
            mutation(${'$'}i: RequestPasswordResetInput!) {
              RequestPasswordReset(input: ${'$'}i) {
                verificationToken
                expiresAt
              }
            }
        """.trimIndent()
        val variables = JSONObject().put("i", JSONObject().put("email", email))
        return runCatching { GraphQL.post(client, baseUrl, query, variables) }.fold(
            onSuccess = { resp ->
                val msg = resp.firstErrorMessage()
                if (msg != null) {
                    val lower = msg.lowercase()
                    if (lower.contains("no existe") || lower.contains("no encontr")) {
                        PasswordResetResult.EmailNotFound
                    } else {
                        PasswordResetResult.Error(IllegalStateException(msg))
                    }
                } else {
                    val r = resp.data?.optJSONObject("RequestPasswordReset")
                        ?: return PasswordResetResult.Error(
                            IllegalStateException("missing data.RequestPasswordReset")
                        )
                    PasswordResetResult.Success(
                        verificationToken = r.optString("verificationToken"),
                        expiresAtEpochMs = parseEpochMs(r.optString("expiresAt"))
                            ?: (System.currentTimeMillis() + 10 * 60 * 1000)
                    )
                }
            },
            onFailure = { PasswordResetResult.Error(it) }
        )
    }

    suspend fun verifyPasswordResetCode(
        baseUrl: String,
        verificationToken: String,
        code: String
    ): VerifyResetResult {
        val query = """
            mutation(${'$'}i: VerifyResetCodeInput!) {
              VerifyPasswordResetCode(input: ${'$'}i)
            }
        """.trimIndent()
        val variables = JSONObject().put(
            "i",
            JSONObject().put("verificationToken", verificationToken).put("code", code)
        )
        return runCatching { GraphQL.post(client, baseUrl, query, variables) }.fold(
            onSuccess = { resp ->
                val msg = resp.firstErrorMessage()
                if (msg != null) {
                    val lower = msg.lowercase()
                    if (lower.contains("incorrecto") || lower.contains("expirado") ||
                        lower.contains("inválido")
                    ) {
                        VerifyResetResult.InvalidOrExpired
                    } else {
                        VerifyResetResult.Error(IllegalStateException(msg))
                    }
                } else {
                    VerifyResetResult.Success
                }
            },
            onFailure = { VerifyResetResult.Error(it) }
        )
    }

    suspend fun completePasswordReset(
        baseUrl: String,
        verificationToken: String,
        newPassword: String,
        newPasswordConfirmation: String
    ): CompleteResetResult {
        val query = """
            mutation(${'$'}i: CompletePasswordResetInput!) {
              CompletePasswordReset(input: ${'$'}i)
            }
        """.trimIndent()
        val variables = JSONObject().put(
            "i",
            JSONObject()
                .put("verificationToken", verificationToken)
                .put("newPassword", newPassword)
                .put("newPasswordConfirmation", newPasswordConfirmation)
        )
        return runCatching { GraphQL.post(client, baseUrl, query, variables) }.fold(
            onSuccess = { resp ->
                val msg = resp.firstErrorMessage()
                if (msg != null) {
                    val lower = msg.lowercase()
                    when {
                        lower.contains("no coinciden") -> CompleteResetResult.PasswordMismatch
                        lower.contains("criterios") -> CompleteResetResult.WeakPassword
                        lower.contains("igual a la anterior") -> CompleteResetResult.SamePassword
                        lower.contains("incorrecto") || lower.contains("expirado") ||
                            lower.contains("verificar el código") ->
                            CompleteResetResult.InvalidOrExpired
                        else -> CompleteResetResult.Error(IllegalStateException(msg))
                    }
                } else {
                    CompleteResetResult.Success
                }
            },
            onFailure = { CompleteResetResult.Error(it) }
        )
    }

    /// Re-emite el token de sesión con el rol actual del usuario.
    suspend fun refreshSession(baseUrl: String, accessToken: String): LoginResult {
        val query = """
            mutation {
              RefreshSession {
                accessToken
                expiresIn
                user { email fullName role }
              }
            }
        """.trimIndent()
        return runCatching {
            GraphQL.post(client, baseUrl, query, JSONObject(), bearerToken = accessToken)
        }.fold(
            onSuccess = { resp ->
                val msg = resp.firstErrorMessage()
                if (msg != null) {
                    classifyLoginError(msg)
                } else {
                    val refresh = resp.data?.optJSONObject("RefreshSession")
                        ?: return LoginResult.Error(IllegalStateException("missing data.RefreshSession"))
                    val user = refresh.optJSONObject("user")
                    LoginResult.Success(
                        accessToken = refresh.optString("accessToken"),
                        expiresIn = refresh.optLong("expiresIn", 0L),
                        serverRole = user?.optStringOrNull("role"),
                        email = user?.optStringOrNull("email"),
                        fullName = user?.optStringOrNull("fullName")
                    )
                }
            },
            onFailure = { LoginResult.Error(it) }
        )
    }

    private fun classifyLoginError(message: String): LoginResult {
        val lower = message.lowercase()
        return when {
            lower.contains("bloqueada") -> {
                val unlock = extractIsoTimestamp(message)?.let { parseEpochMs(it) }
                    ?: (System.currentTimeMillis() + 15_000L)
                LoginResult.Locked(unlock)
            }
            lower.contains("inactiva") -> LoginResult.InactiveAccount
            lower.contains("administrador") || lower.contains("plataforma web") ->
                LoginResult.RoleNotAllowed
            lower.contains("credenciales") || lower.contains("inválid") -> LoginResult.InvalidCredentials
            else -> LoginResult.Error(IllegalStateException(message))
        }
    }

    private fun classifyRegisterError(message: String): RegisterResult {
        val lower = message.lowercase()
        return when {
            lower.contains("dominio") -> RegisterResult.DomainNotAllowed
            lower.contains("criterios") || lower.contains("contraseña") && lower.contains("débil") ->
                RegisterResult.WeakPassword
            lower.contains("no coinciden") -> RegisterResult.PasswordMismatch
            lower.contains("ya existe") || lower.contains("registrado") ->
                RegisterResult.EmailAlreadyExists
            else -> RegisterResult.Error(IllegalStateException(message))
        }
    }

    private fun extractIsoTimestamp(text: String): String? {
        val regex = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})?""")
        return regex.find(text)?.value
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun parseEpochMs(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
