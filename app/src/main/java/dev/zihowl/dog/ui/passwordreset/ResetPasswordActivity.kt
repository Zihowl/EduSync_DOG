package dev.zihowl.dog.ui.passwordreset

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.zihowl.dog.R
import dev.zihowl.dog.data.remote.AuthClient
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.databinding.ActivityResetPasswordBinding
import dev.zihowl.dog.ui.login.LoginActivity
import dev.zihowl.dog.util.PasswordPolicy
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Pantalla de restablecimiento de contraseña (RQF-APP-18 a RQF-APP-23).
 * Primero valida el código de 6 dígitos y solo entonces habilita el formulario
 * de nueva contraseña (RQF-APP-20).
 */
class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding
    private lateinit var session: SessionManager
    private val authClient = AuthClient()
    private var expirationTimer: CountDownTimer? = null
    private var codeVerified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        val token = session.pendingResetToken
        val expiresAt = session.pendingResetExpiresAt
        val email = session.pendingResetEmail.orEmpty()

        if (token.isNullOrBlank() || expiresAt <= System.currentTimeMillis()) {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
            finish()
            return
        }

        binding.subtitleText.text = getString(R.string.reset_password_subtitle, email)
        binding.verifyCodeButton.setOnClickListener { attemptVerifyCode(token) }
        binding.resetPasswordButton.setOnClickListener { attemptResetPassword(token) }

        startExpirationCountdown(expiresAt - System.currentTimeMillis())
    }

    override fun onDestroy() {
        expirationTimer?.cancel()
        super.onDestroy()
    }

    private fun attemptVerifyCode(token: String) {
        val code = binding.codeInput.text.toString().trim()
        binding.errorText.visibility = View.GONE
        binding.codeInputLayout.error = null

        if (code.length != 6 || code.any { !it.isDigit() }) {
            binding.codeInputLayout.error = getString(R.string.error_verification_code_invalid)
            return
        }

        val baseUrl = session.serverBaseUrl
        if (baseUrl.isNullOrBlank()) {
            showError(getString(R.string.error_server_unreachable))
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = authClient.verifyPasswordResetCode(baseUrl, token, code)
            setLoading(false)
            when (result) {
                is AuthClient.VerifyResetResult.Success -> onCodeVerified()
                is AuthClient.VerifyResetResult.InvalidOrExpired ->
                    binding.codeInputLayout.error =
                        getString(R.string.error_verification_code_invalid)
                is AuthClient.VerifyResetResult.Error ->
                    showError(getString(R.string.error_server_unreachable))
            }
        }
    }

    private fun onCodeVerified() {
        codeVerified = true
        binding.codeInputLayout.isEnabled = false
        binding.verifyCodeButton.isEnabled = false
        binding.newPasswordInputLayout.isEnabled = true
        binding.newPasswordInput.isEnabled = true
        binding.confirmPasswordInputLayout.isEnabled = true
        binding.confirmPasswordInput.isEnabled = true
        binding.resetPasswordButton.isEnabled = true
        Toast.makeText(this, R.string.reset_code_verified, Toast.LENGTH_SHORT).show()
    }

    private fun attemptResetPassword(token: String) {
        if (!codeVerified) return

        val newPassword = binding.newPasswordInput.text.toString()
        val confirmation = binding.confirmPasswordInput.text.toString()
        binding.errorText.visibility = View.GONE
        binding.newPasswordInputLayout.error = null
        binding.confirmPasswordInputLayout.error = null

        when (PasswordPolicy.validate(newPassword)) {
            is PasswordPolicy.Result.Ok -> Unit
            else -> {
                binding.newPasswordInputLayout.error = getString(R.string.error_password_weak)
                return
            }
        }
        if (newPassword != confirmation) {
            binding.confirmPasswordInputLayout.error =
                getString(R.string.error_password_mismatch)
            return
        }

        val baseUrl = session.serverBaseUrl
        if (baseUrl.isNullOrBlank()) {
            showError(getString(R.string.error_server_unreachable))
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result =
                authClient.completePasswordReset(baseUrl, token, newPassword, confirmation)
            setLoading(false)
            handleCompleteResult(result)
        }
    }

    private fun handleCompleteResult(result: AuthClient.CompleteResetResult) {
        when (result) {
            is AuthClient.CompleteResetResult.Success -> {
                session.pendingResetToken = null
                session.pendingResetExpiresAt = 0L
                session.pendingResetEmail = null
                Toast.makeText(this, R.string.reset_password_success, Toast.LENGTH_LONG).show()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            is AuthClient.CompleteResetResult.WeakPassword ->
                binding.newPasswordInputLayout.error = getString(R.string.error_password_weak)
            is AuthClient.CompleteResetResult.PasswordMismatch ->
                binding.confirmPasswordInputLayout.error =
                    getString(R.string.error_password_mismatch)
            is AuthClient.CompleteResetResult.SamePassword ->
                binding.newPasswordInputLayout.error =
                    getString(R.string.error_password_same_as_previous)
            is AuthClient.CompleteResetResult.InvalidOrExpired ->
                showError(getString(R.string.verification_expired))
            is AuthClient.CompleteResetResult.Error ->
                showError(getString(R.string.error_server_unreachable))
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.verifyCodeButton.isEnabled = !loading && !codeVerified
        binding.resetPasswordButton.isEnabled = !loading && codeVerified
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }

    private fun startExpirationCountdown(durationMs: Long) {
        expirationTimer?.cancel()
        expirationTimer = object : CountDownTimer(durationMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.expirationText.text = getString(
                    R.string.verification_expires_in,
                    formatMmSs(millisUntilFinished)
                )
            }

            override fun onFinish() {
                binding.expirationText.text = getString(R.string.verification_expired)
                binding.verifyCodeButton.isEnabled = false
                binding.resetPasswordButton.isEnabled = false
            }
        }.start()
    }

    private fun formatMmSs(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
