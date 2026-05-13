package dev.zihowl.dog.ui.register

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.zihowl.dog.R
import dev.zihowl.dog.data.remote.AuthClient
import dev.zihowl.dog.data.session.SessionManager
import dev.zihowl.dog.databinding.ActivityRegisterBinding
import dev.zihowl.dog.util.PasswordPolicy
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var session: SessionManager
    private val authClient = AuthClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        session = SessionManager(this)

        binding.registerButton.setOnClickListener { attemptRegister() }
        binding.goToLogin.setOnClickListener { finish() }
    }

    private fun attemptRegister() {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString()
        val confirm = binding.passwordConfirmInput.text.toString()

        binding.errorText.visibility = View.GONE
        binding.emailInputLayout.error = null
        binding.passwordInputLayout.error = null
        binding.passwordConfirmInputLayout.error = null

        var hasError = false
        if (email.isEmpty()) {
            binding.emailInputLayout.error = getString(R.string.error_username_empty)
            hasError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = getString(R.string.error_email_invalid)
            hasError = true
        }
        if (password.isEmpty()) {
            binding.passwordInputLayout.error = getString(R.string.error_password_empty)
            hasError = true
        } else {
            when (PasswordPolicy.validate(password)) {
                PasswordPolicy.Result.Ok -> Unit
                else -> {
                    binding.passwordInputLayout.error = getString(R.string.error_password_weak)
                    hasError = true
                }
            }
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
            val result = authClient.register(baseUrl, email, password, confirm)
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
