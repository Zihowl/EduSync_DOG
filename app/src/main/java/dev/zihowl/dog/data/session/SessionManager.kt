package dev.zihowl.dog.data.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

class SessionManager(context: Context) {
    companion object {
        private const val PREFS_NAME = "dog_prefs"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE = "role"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val KEY_SYNC_AES_KEY = "sync_aes_key"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_IS_GUEST_MODE = "is_guest_mode"
        private const val KEY_SERVER_URL = "server_base_url"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val KEY_PENDING_VERIFICATION_TOKEN = "pending_verification_token"
        private const val KEY_PENDING_VERIFICATION_EXPIRES_AT = "pending_verification_expires_at"
        private const val KEY_PENDING_VERIFICATION_EMAIL = "pending_verification_email"
        const val ROLE_ALUMNO = "alumno"
        const val ROLE_DOCENTE = "docente"
        const val ROLE_UNSUPPORTED = "unsupported"
        private const val DEFAULT_ROLE = ROLE_ALUMNO
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var username: String
        get() = prefs.getString(KEY_USERNAME, "Alumno") ?: "Alumno"
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var role: String
        get() = prefs.getString(KEY_ROLE, DEFAULT_ROLE) ?: DEFAULT_ROLE
        set(value) = prefs.edit().putString(KEY_ROLE, value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var isGuestMode: Boolean
        get() = prefs.getBoolean(KEY_IS_GUEST_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_GUEST_MODE, value).apply()

    var serverBaseUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var tokenExpiresAt: Long
        get() = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_TOKEN_EXPIRES_AT, value).apply()

    var lockoutUntilEpochMs: Long
        get() = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_LOCKOUT_UNTIL, value).apply()

    var pendingVerificationToken: String?
        get() = prefs.getString(KEY_PENDING_VERIFICATION_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_PENDING_VERIFICATION_TOKEN, value).apply()

    var pendingVerificationExpiresAt: Long
        get() = prefs.getLong(KEY_PENDING_VERIFICATION_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_PENDING_VERIFICATION_EXPIRES_AT, value).apply()

    var pendingVerificationEmail: String?
        get() = prefs.getString(KEY_PENDING_VERIFICATION_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_PENDING_VERIFICATION_EMAIL, value).apply()

    fun getDbPassphrase(): ByteArray {
        var passphrase = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (passphrase == null) {
            passphrase = generatePassphrase()
            prefs.edit().putString(KEY_DB_PASSPHRASE, passphrase).apply()
        }
        return net.sqlcipher.database.SQLiteDatabase.getBytes(passphrase.toCharArray())
    }

    fun getSyncAesKey(): ByteArray {
        val stored = prefs.getString(KEY_SYNC_AES_KEY, null)
        if (stored != null) {
            return Base64.decode(stored, Base64.NO_WRAP)
        }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_SYNC_AES_KEY, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        return key
    }

    private fun generatePassphrase(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
        return (1..32)
            .map { chars.random() }
            .joinToString("")
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
