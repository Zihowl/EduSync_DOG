package dev.zihowl.dog.ui.serverconnection

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.databinding.ActivityServerConnectionBinding
import dev.zihowl.dog.ui.login.LoginActivity
import dev.zihowl.dog.ui.main.MainActivity

class ServerConnectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServerConnectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.connectButton.setOnClickListener {
            val url = binding.urlInput.text.toString().trim()
            if (url.isEmpty()) {
                binding.urlInputLayout.error = getString(dev.zihowl.dog.R.string.error_url_empty)
            } else {
                binding.urlInputLayout.error = null
                Toast.makeText(this, "Conectando a: $url", Toast.LENGTH_SHORT).show()
                // TODO: Implementar conexión real al servidor
                navigateToLogin()
            }
        }

        binding.guestModeButton.setOnClickListener {
            val sessionManager = SessionManager(this)
            sessionManager.isGuestMode = true
            sessionManager.isLoggedIn = false
            sessionManager.username = "Invitado"
            sessionManager.role = SessionManager.ROLE_ALUMNO
            Toast.makeText(this, "Modo invitado activado", Toast.LENGTH_SHORT).show()
            navigateToMain()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
