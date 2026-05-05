package dev.zihowl.dog.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dev.zihowl.dog.databinding.ActivityLoginBinding
import dev.zihowl.dog.ui.main.MainActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener {
            val username = binding.usernameInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (username.isEmpty()) {
                binding.usernameInputLayout.error = getString(dev.zihowl.dog.R.string.error_username_empty)
            } else {
                binding.usernameInputLayout.error = null
            }

            if (password.isEmpty()) {
                binding.passwordInputLayout.error = getString(dev.zihowl.dog.R.string.error_password_empty)
            } else {
                binding.passwordInputLayout.error = null
            }

            if (username.isNotEmpty() && password.isNotEmpty()) {
                Toast.makeText(this, "Iniciando sesión...", Toast.LENGTH_SHORT).show()
                navigateToMain()
            }
        }

    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
