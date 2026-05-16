package dev.zihowl.dog.ui.serverconnection

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.zihowl.dog.R
import dev.zihowl.dog.data.remote.ServerHealthClient
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.databinding.ActivityServerConnectionBinding
import dev.zihowl.dog.ui.login.LoginActivity
import dev.zihowl.dog.ui.main.MainActivity
import kotlinx.coroutines.launch

class ServerConnectionActivity : AppCompatActivity() {

    companion object {
        /** Fuerza mostrar esta pantalla aunque ya haya servidor/sesión (botón "Cambiar servidor"). */
        const val EXTRA_FORCE_CONFIG = "dev.zihowl.dog.extra.FORCE_CONFIG"
    }

    private lateinit var binding: ActivityServerConnectionBinding
    private val healthClient = ServerHealthClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        val forceConfig = intent?.getBooleanExtra(EXTRA_FORCE_CONFIG, false) ?: false
        if (!forceConfig && routeExistingSession(sessionManager)) {
            return
        }

        binding = ActivityServerConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager.serverBaseUrl?.let { binding.urlInput.setText(it) }

        binding.connectButton.setOnClickListener {
            val url = binding.urlInput.text.toString().trim()
            when {
                url.isEmpty() -> {
                    binding.urlInputLayout.error = getString(R.string.error_url_empty)
                }
                !isLikelyUrl(url) -> {
                    binding.urlInputLayout.error = getString(R.string.error_url_invalid)
                }
                else -> {
                    binding.urlInputLayout.error = null
                    verifyAndContinue(sessionManager, url)
                }
            }
        }

        binding.guestModeButton.setOnClickListener {
            sessionManager.isGuestMode = true
            sessionManager.isLoggedIn = false
            sessionManager.username = "Invitado"
            // Sin cuenta: los datos quedan bajo GUEST_KEY, aislados de cuentas previas.
            sessionManager.accountKey = null
            sessionManager.accessToken = null
            sessionManager.role = SessionManager.ROLE_ALUMNO
            Toast.makeText(this, "Modo invitado activado", Toast.LENGTH_SHORT).show()
            navigateToMain()
        }
    }

    /**
     * Si ya existe una sesión utilizable, redirige sin mostrar esta pantalla.
     * Devuelve true si redirigió (la Activity ya terminó).
     */
    private fun routeExistingSession(sessionManager: SessionManager): Boolean {
        val now = System.currentTimeMillis()
        return when {
            sessionManager.isGuestMode -> {
                navigateToMain(); true
            }
            sessionManager.isLoggedIn && sessionManager.tokenExpiresAt > now -> {
                navigateToMain(); true
            }
            // Sesión expirada o sin sesión pero con servidor configurado: a login.
            (sessionManager.isLoggedIn || !sessionManager.serverBaseUrl.isNullOrBlank()) -> {
                navigateToLogin(); true
            }
            else -> false
        }
    }

    private fun verifyAndContinue(sessionManager: SessionManager, url: String) {
        setLoading(true)
        lifecycleScope.launch {
            val result = healthClient.check(url)
            setLoading(false)
            result
                .onSuccess { normalized ->
                    sessionManager.serverBaseUrl = normalized
                    navigateToLogin()
                }
                .onFailure {
                    binding.urlInputLayout.error = getString(R.string.error_server_unreachable)
                }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.connectProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.connectButton.isEnabled = !loading
        binding.urlInput.isEnabled = !loading
        binding.guestModeButton.isEnabled = !loading
    }

    private fun isLikelyUrl(value: String): Boolean {
        val candidate = if (value.startsWith("http://") || value.startsWith("https://")) {
            value
        } else {
            "http://$value"
        }
        return Patterns.WEB_URL.matcher(candidate).matches()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
