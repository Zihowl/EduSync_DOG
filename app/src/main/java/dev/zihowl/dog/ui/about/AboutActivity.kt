package dev.zihowl.dog.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.zihowl.dog.R
import dev.zihowl.dog.databinding.ActivityAboutBinding

/**
 * Pantalla "Acerca de" / créditos (easter egg). Se abre al tocar el ícono de la
 * app en el header del Navigation Drawer. Muestra créditos del equipo, un
 * resumen de la licencia AGPL-3.0 y el enlace al repositorio del proyecto.
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.aboutToolbar.setNavigationOnClickListener { finish() }
        binding.aboutLicenseButton.setOnClickListener {
            openUrl(getString(R.string.about_license_url))
        }
        binding.aboutGithubButton.setOnClickListener {
            openUrl(getString(R.string.about_github_url))
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
