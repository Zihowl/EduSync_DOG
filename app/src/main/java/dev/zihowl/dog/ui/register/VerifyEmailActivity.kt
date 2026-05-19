package dev.zihowl.dog.ui.register

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.zihowl.dog.R
import dev.zihowl.dog.data.remote.AuthClient
import dev.zihowl.dog.data.session.RoleMapper
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.databinding.ActivityVerifyEmailBinding
import dev.zihowl.dog.ui.login.LoginActivity
import dev.zihowl.dog.ui.main.MainActivity
import kotlinx.coroutines.launch
import java.util.Locale

class VerifyEmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerifyEmailBinding
    private lateinit var session: SessionManager
    private val authClient = AuthClient()
    private var expirationTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        val token = session.pendingVerificationToken
        val expiresAt = session.pendingVerificationExpiresAt
        val email = session.pendingVerificationEmail.orEmpty()

        if (token.isNullOrBlank() || expiresAt <= System.currentTimeMillis()) {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
            return
        }

        binding.subtitleText.text = getString(R.string.verify_email_subtitle, email)
        binding.verifyButton.setOnClickListener { attemptVerify(token) }

        startExpirationCountdown(expiresAt - System.currentTimeMillis())
    }

    override fun onDestroy() {
        expirationTimer?.cancel()
        super.onDestroy()
    }

    private fun attemptVerify(token: String) {
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
            val result = authClient.verifyEmail(baseUrl, token, code)
            setLoading(false)
            handleResult(result)
        }
    }

    private fun handleResult(result: AuthClient.VerifyResult) {
        when (result) {
            is AuthClient.VerifyResult.Success -> {
                session.pendingVerificationToken = null
                session.pendingVerificationExpiresAt = 0L
                val email = session.pendingVerificationEmail
                session.pendingVerificationEmail = null
                val token = result.accessToken
                val role = RoleMapper.fromServer(result.serverRole)
                if (!token.isNullOrBlank() && role != SessionManager.ROLE_UNSUPPORTED) {
                    session.accessToken = token
                    session.role = role
                    // El alumno no tiene nombre: se identifica con su @username.
                    session.username = result.fullName?.takeIf { it.isNotBlank() }
                        ?: result.username?.takeIf { it.isNotBlank() }
                        ?: email.orEmpty()
                    session.accountUsername = result.username?.takeIf { it.isNotBlank() }
                    session.accountKey = email.orEmpty().trim().lowercase()
                    session.isLoggedIn = true
                    session.isGuestMode = false
                    startActivity(Intent(this, MainActivity::class.java))
                } else {
                    startActivity(Intent(this, LoginActivity::class.java))
                }
                finish()
            }
            is AuthClient.VerifyResult.InvalidOrExpired ->
                showError(getString(R.string.error_verification_code_invalid))
            is AuthClient.VerifyResult.Error ->
                showError(getString(R.string.error_server_unreachable))
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.verifyProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.verifyButton.isEnabled = !loading
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
                binding.verifyButton.isEnabled = false
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
