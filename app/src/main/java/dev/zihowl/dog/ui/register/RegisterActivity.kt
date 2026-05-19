package dev.zihowl.dog.ui.register

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import dev.zihowl.dog.R
import dev.zihowl.dog.data.remote.AuthClient
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.databinding.ActivityRegisterBinding
import dev.zihowl.dog.util.PasswordPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var session: SessionManager
    private val authClient = AuthClient()

    /** Regex de username: minúsculas, dígitos, punto y guion bajo, 3-30. */
    private val usernameRegex = Regex("^[a-z0-9._]{3,30}$")

    /** Trabajo en curso de validación en vivo, cancelable al re-teclear. */
    private var usernameCheckJob: Job? = null

    /** El último username consultado y el estado devuelto por el servidor. */
    private var lastUsernameChecked: String? = null
    private var lastUsernameStatus = AuthClient.UsernameStatus.UNKNOWN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        binding.registerButton.setOnClickListener { attemptRegister() }
        binding.goToLogin.setOnClickListener { finish() }

        // Validación en vivo del nombre de usuario (formato + disponibilidad).
        binding.usernameInput.doAfterTextChanged { scheduleUsernameCheck() }
    }

    private fun scheduleUsernameCheck() {
        val raw = binding.usernameInput.text.toString().trim().lowercase()
        usernameCheckJob?.cancel()
        lastUsernameChecked = null
        binding.usernameInputLayout.error = null

        if (raw.isEmpty()) {
            binding.usernameInputLayout.helperText = getString(R.string.helper_username)
            return
        }
        if (!usernameRegex.matches(raw)) {
            binding.usernameInputLayout.error = getString(R.string.error_username_invalid)
            return
        }

        val baseUrl = session.serverBaseUrl ?: return
        binding.usernameInputLayout.helperText = getString(R.string.username_checking)
        usernameCheckJob = lifecycleScope.launch {
            delay(450) // debounce
            val status = authClient.usernameAvailable(baseUrl, raw)
            lastUsernameChecked = raw
            lastUsernameStatus = status
            when (status) {
                AuthClient.UsernameStatus.AVAILABLE -> {
                    binding.usernameInputLayout.error = null
                    binding.usernameInputLayout.helperText =
                        getString(R.string.username_available)
                }
                AuthClient.UsernameStatus.TAKEN -> {
                    binding.usernameInputLayout.helperText = null
                    binding.usernameInputLayout.error =
                        getString(R.string.error_username_taken)
                }
                AuthClient.UsernameStatus.UNKNOWN -> {
                    // No se pudo verificar: no acusar de "en uso" un nombre
                    // que podría estar libre; el servidor decidirá al registrar.
                    binding.usernameInputLayout.error = null
                    binding.usernameInputLayout.helperText =
                        getString(R.string.username_check_unavailable)
                }
            }
        }
    }

    private fun attemptRegister() {
        val email = binding.emailInput.text.toString().trim()
        val username = binding.usernameInput.text.toString().trim().lowercase()
        val password = binding.passwordInput.text.toString()
        val confirm = binding.passwordConfirmInput.text.toString()

        binding.errorText.visibility = View.GONE
        binding.emailInputLayout.error = null
        binding.usernameInputLayout.error = null
        binding.passwordInputLayout.error = null
        binding.passwordConfirmInputLayout.error = null

        var hasError = false
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = getString(R.string.error_email_invalid)
            hasError = true
        }
        if (!usernameRegex.matches(username)) {
            binding.usernameInputLayout.error = getString(R.string.error_username_invalid)
            hasError = true
        } else if (username == lastUsernameChecked &&
            lastUsernameStatus == AuthClient.UsernameStatus.TAKEN) {
            binding.usernameInputLayout.error = getString(R.string.error_username_taken)
            hasError = true
        }
        if (password.isEmpty()) {
            binding.passwordInputLayout.error = getString(R.string.error_password_empty)
            hasError = true
        } else if (PasswordPolicy.validate(password) != PasswordPolicy.Result.Ok) {
            binding.passwordInputLayout.error = getString(R.string.error_password_weak)
            hasError = true
        }
        if (confirm != password) {
            binding.passwordConfirmInputLayout.error = getString(R.string.error_password_mismatch)
            hasError = true
        }
        if (hasError) return

        val baseUrl = session.serverBaseUrl
        if (baseUrl.isNullOrBlank()) {
            showError(getString(R.string.error_server_unreachable))
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = authClient.register(baseUrl, email, username, password, confirm)
            setLoading(false)
            handleResult(result, email)
        }
    }

    private fun handleResult(result: AuthClient.RegisterResult, email: String) {
        when (result) {
            is AuthClient.RegisterResult.Success -> {
                session.pendingVerificationToken = result.verificationToken
                session.pendingVerificationExpiresAt = result.expiresAtEpochMs
                session.pendingVerificationEmail = email
                startActivity(Intent(this, VerifyEmailActivity::class.java))
                finish()
            }
            is AuthClient.RegisterResult.DomainNotAllowed ->
                showError(getString(R.string.error_domain_not_allowed))
            is AuthClient.RegisterResult.WeakPassword ->
                showError(getString(R.string.error_password_weak))
            is AuthClient.RegisterResult.PasswordMismatch ->
                showError(getString(R.string.error_password_mismatch))
            is AuthClient.RegisterResult.EmailAlreadyExists ->
                showError(getString(R.string.error_email_already_exists))
            is AuthClient.RegisterResult.UsernameTaken -> {
                binding.usernameInputLayout.error = getString(R.string.error_username_taken)
                showError(getString(R.string.error_username_taken))
            }
            is AuthClient.RegisterResult.InvalidUsername -> {
                binding.usernameInputLayout.error = getString(R.string.error_username_invalid)
                showError(getString(R.string.error_username_invalid))
            }
            is AuthClient.RegisterResult.Error ->
                showError(getString(R.string.error_server_unreachable))
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.registerProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.registerButton.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }
}
