package dev.zihowl.dog.ui.passwordreset

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.zihowl.dog.R
import dev.zihowl.dog.data.remote.AuthClient
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.databinding.ActivityForgotPasswordBinding
import kotlinx.coroutines.launch

/**
 * Pantalla para solicitar el restablecimiento de contraseña (RQF-APP-16).
 * Recibe el correo institucional y, si existe una cuenta, dispara el envío de
 * un código de verificación de 6 dígitos.
 */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private lateinit var session: SessionManager
    private val authClient = AuthClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        binding.sendCodeButton.setOnClickListener { attemptRequest() }
    }

    private fun attemptRequest() {
        val email = binding.emailInput.text.toString().trim()
        binding.errorText.visibility = View.GONE
        binding.emailInputLayout.error = null

        if (email.isEmpty()) {
            binding.emailInputLayout.error = getString(R.string.error_username_empty)
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = getString(R.string.error_email_invalid)
            return
        }

        val baseUrl = session.serverBaseUrl
        if (baseUrl.isNullOrBlank()) {
            showError(getString(R.string.error_server_unreachable))
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = authClient.requestPasswordReset(baseUrl, email)
            setLoading(false)
            handleResult(result, email)
        }
    }

    private fun handleResult(result: AuthClient.PasswordResetResult, email: String) {
        when (result) {
            is AuthClient.PasswordResetResult.Success -> {
                session.pendingResetToken = result.verificationToken
                session.pendingResetExpiresAt = result.expiresAtEpochMs
                session.pendingResetEmail = email
                startActivity(Intent(this, ResetPasswordActivity::class.java))
                finish()
            }
            is AuthClient.PasswordResetResult.EmailNotFound ->
                binding.emailInputLayout.error = getString(R.string.error_reset_email_not_found)
            is AuthClient.PasswordResetResult.Error ->
                showError(getString(R.string.error_server_unreachable))
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.sendCodeButton.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }
}
