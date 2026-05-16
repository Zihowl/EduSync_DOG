package dev.zihowl.dog.data.backup

import android.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Deriva la clave de cifrado del respaldo a partir de la contraseña del usuario.
 *
 * El respaldo es *zero-knowledge*: la clave nunca sale del dispositivo y el
 * servidor solo almacena texto cifrado. Ni el administrador puede descifrarlo.
 *
 * La derivación es determinística (salt = correo del usuario), de modo que la
 * misma contraseña en otro dispositivo produce la misma clave y permite
 * recuperar el respaldo.
 */
object BackupCrypto {

    private const val ITERATIONS = 210_000
    private const val KEY_LENGTH_BITS = 256

    /** Deriva una clave AES-256 (32 bytes) desde [password] y [email] (salt). */
    fun deriveKey(password: String, email: String): ByteArray {
        val salt = ("dog-backup:" + email.trim().lowercase()).toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun encodeKey(key: ByteArray): String = Base64.encodeToString(key, Base64.NO_WRAP)

    fun decodeKey(encoded: String): ByteArray = Base64.decode(encoded, Base64.NO_WRAP)
}
