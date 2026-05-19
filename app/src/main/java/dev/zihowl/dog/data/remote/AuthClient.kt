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
            val fullName: String?,
            val username: String?
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
        object UsernameTaken : RegisterResult()
        object InvalidUsername : RegisterResult()
        data class Error(val cause: Throwable?) : RegisterResult()
    }

    /// Resultado de consultar la disponibilidad de un nombre de usuario.
    /// `Unknown` distingue un fallo de red/servidor de una respuesta real:
    /// nunca debe interpretarse como "el usuario ya está en uso".
    enum class UsernameStatus { AVAILABLE, TAKEN, UNKNOWN }

    sealed class VerifyResult {
        data class Success(
            val accessToken: String?,
            val serverRole: String?,
            val fullName: String?,
            val username: String?
        ) : VerifyResult()
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
                user { email username fullName role }
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
                        fullName = user?.optStringOrNull("fullName"),
                        username = user?.optStringOrNull("username")
                    )
                }
            },
            onFailure = { LoginResult.Error(it) }
        )
    }

    suspend fun register(
        baseUrl: String,
        email: String,
        username: String,
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
                // El alumno no registra nombre y el del docente lo define el
                // catálogo CAT: el servidor ignora este campo.
                .put("fullName", "")
                .put("username", username)
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
                user { role username fullName }
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
                    val user = v.optJSONObject("user")
                    VerifyResult.Success(
                        accessToken = v.optString("accessToken").takeIf { it.isNotBlank() },
                        serverRole = user?.optStringOrNull("role"),
                        fullName = user?.optStringOrNull("fullName"),
                        username = user?.optStringOrNull("username")
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
                user { email username fullName role }
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
                        fullName = user?.optStringOrNull("fullName"),
                        username = user?.optStringOrNull("username")
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
                // El servidor es la fuente autoritativa del tiempo de desbloqueo:
                // comunica los segundos restantes en el propio mensaje de error.
                val unlock = extractLockoutSeconds(message)
                        ?.let { System.currentTimeMillis() + it * 1000L }
                    ?: extractIsoTimestamp(message)?.let { parseEpochMs(it) }
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
            lower.contains("nombre de usuario ya") || lower.contains("usuario ya está en uso") ->
                RegisterResult.UsernameTaken
            lower.contains("nombre de usuario debe") -> RegisterResult.InvalidUsername
            lower.contains("dominio") -> RegisterResult.DomainNotAllowed
            lower.contains("criterios") || lower.contains("contraseña") && lower.contains("débil") ->
                RegisterResult.WeakPassword
            lower.contains("no coinciden") -> RegisterResult.PasswordMismatch
            lower.contains("ya existe") || lower.contains("registrado") ->
                RegisterResult.EmailAlreadyExists
            else -> RegisterResult.Error(IllegalStateException(message))
        }
    }

    /// Consulta si un nombre de usuario está disponible para registrarse.
    /// Un fallo de red o de servidor devuelve `UNKNOWN`, nunca `TAKEN`: así
    /// la UI no acusa de "usuario en uso" un nombre que en realidad está libre.
    suspend fun usernameAvailable(baseUrl: String, username: String): UsernameStatus {
        val query = """
            query(${'$'}u: String!) { UsernameAvailable(username: ${'$'}u) }
        """.trimIndent()
        val variables = JSONObject().put("u", username)
        return runCatching { GraphQL.post(client, baseUrl, query, variables) }.fold(
            onSuccess = { resp ->
                when {
                    resp.firstErrorMessage() != null -> UsernameStatus.UNKNOWN
                    resp.data?.has("UsernameAvailable") != true -> UsernameStatus.UNKNOWN
                    resp.data.optBoolean("UsernameAvailable", false) -> UsernameStatus.AVAILABLE
                    else -> UsernameStatus.TAKEN
                }
            },
            onFailure = { UsernameStatus.UNKNOWN }
        )
    }

    /// Extrae los segundos de bloqueo del mensaje del servidor
    /// ("…Intenta de nuevo en {N} segundos"), fuente autoritativa del desbloqueo.
    private fun extractLockoutSeconds(text: String): Long? =
        Regex("""(\d+)\s*segundos""").find(text)?.groupValues?.get(1)?.toLongOrNull()

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
