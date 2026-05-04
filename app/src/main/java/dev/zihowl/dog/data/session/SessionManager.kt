package dev.zihowl.dog.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {
    companion object {
        private const val PREFS_NAME = "dog_prefs"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE = "role"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val DEFAULT_ROLE = "alumno"
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

    fun getDbPassphrase(): ByteArray {
        var passphrase = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (passphrase == null) {
            passphrase = generatePassphrase()
            prefs.edit().putString(KEY_DB_PASSPHRASE, passphrase).apply()
        }
        return net.sqlcipher.database.SQLiteDatabase.getBytes(passphrase.toCharArray())
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
