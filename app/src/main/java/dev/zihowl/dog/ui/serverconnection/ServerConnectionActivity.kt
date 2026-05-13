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

    private lateinit var binding: ActivityServerConnectionBinding
    private val healthClient = ServerHealthClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sessionManager = SessionManager(this)
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
            sessionManager.role = SessionManager.ROLE_ALUMNO
            Toast.makeText(this, "Modo invitado activado", Toast.LENGTH_SHORT).show()
            navigateToMain()
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
