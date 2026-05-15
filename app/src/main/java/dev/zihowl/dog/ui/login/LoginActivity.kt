package dev.zihowl.dog.ui.login

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.zihowl.dog.R
import dev.zihowl.dog.data.remote.AuthClient
import dev.zihowl.dog.data.session.RoleMapper
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.databinding.ActivityLoginBinding
import dev.zihowl.dog.ui.main.MainActivity
import dev.zihowl.dog.ui.passwordreset.ForgotPasswordActivity
import dev.zihowl.dog.ui.register.RegisterActivity
import kotlinx.coroutines.launch
import java.util.Locale

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager
    private val authClient = AuthClient()
    private var lockoutTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        binding.loginButton.setOnClickListener { attemptLogin() }
        binding.goToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.forgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        val now = System.currentTimeMillis()
        if (session.lockoutUntilEpochMs > now) {
            startLockoutCountdown(session.lockoutUntilEpochMs - now)
        }
    }

    override fun onDestroy() {
        lockoutTimer?.cancel()
        super.onDestroy()
    }

    private fun attemptLogin() {
        val email = binding.usernameInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()

        binding.errorText.visibility = View.GONE
        binding.usernameInputLayout.error = null
        binding.passwordInputLayout.error = null

        var hasFieldError = false
        if (email.isEmpty()) {
            binding.usernameInputLayout.error = getString(R.string.error_username_empty)
            hasFieldError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.usernameInputLayout.error = getString(R.string.error_email_invalid)
            hasFieldError = true
        }
        if (password.isEmpty()) {
            binding.passwordInputLayout.error = getString(R.string.error_password_empty)
            hasFieldError = true
        }
        if (hasFieldError) return

        val now = System.currentTimeMillis()
        if (session.lockoutUntilEpochMs > now) {
            startLockoutCountdown(session.lockoutUntilEpochMs - now)
            return
        }

        val baseUrl = session.serverBaseUrl
        if (baseUrl.isNullOrBlank()) {
            showError(getString(R.string.error_server_unreachable))
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = authClient.login(baseUrl, email, password)
            setLoading(false)
            handleLoginResult(result, email)
        }
    }

    private fun handleLoginResult(result: AuthClient.LoginResult, email: String) {
        when (result) {
            is AuthClient.LoginResult.Success -> {
                val mappedRole = RoleMapper.fromServer(result.serverRole)
                if (mappedRole == SessionManager.ROLE_UNSUPPORTED) {
                    showError(getString(R.string.error_role_unsupported))
                    return
                }
                session.accessToken = result.accessToken
                session.tokenExpiresAt = System.currentTimeMillis() + result.expiresIn * 1000
                session.role = mappedRole
                session.username = result.fullName?.takeIf { it.isNotBlank() } ?: email
                session.isLoggedIn = true
                session.isGuestMode = false
                session.lockoutUntilEpochMs = 0L
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            is AuthClient.LoginResult.InvalidCredentials ->
                showError(getString(R.string.error_invalid_credentials))
            is AuthClient.LoginResult.Locked -> {
                session.lockoutUntilEpochMs = result.unlockAtEpochMs
                val remaining = result.unlockAtEpochMs - System.currentTimeMillis()
                if (remaining > 0) startLockoutCountdown(remaining)
            }
            is AuthClient.LoginResult.InactiveAccount ->
                showError(getString(R.string.error_account_inactive))
            is AuthClient.LoginResult.RoleNotAllowed ->
                showError(getString(R.string.error_admin_no_mobile))
            is AuthClient.LoginResult.Error ->
                showError(getString(R.string.error_server_unreachable))
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.loginProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.loginButton.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }

    private fun startLockoutCountdown(durationMs: Long) {
        lockoutTimer?.cancel()
        binding.loginButton.isEnabled = false
        binding.lockoutTimerText.visibility = View.VISIBLE
        lockoutTimer = object : CountDownTimer(durationMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.lockoutTimerText.text = getString(
                    R.string.lockout_message,
                    formatMmSs(millisUntilFinished)
                )
            }

            override fun onFinish() {
                session.lockoutUntilEpochMs = 0L
                binding.lockoutTimerText.visibility = View.GONE
                binding.loginButton.isEnabled = true
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
