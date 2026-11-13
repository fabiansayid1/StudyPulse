package com.example.basededatos.view

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.example.basededatos.databinding.ActivityProfileBinding
import com.example.basededatos.databinding.DialogDeleteAccountBinding
import com.example.basededatos.viewmodel.AppViewModelFactory
import com.example.basededatos.viewmodel.StudyViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ProfileActivity : AppCompatActivity() {

    private val photoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) saveProfilePhoto(uri)
    }

    private lateinit var binding: ActivityProfileBinding
    private lateinit var studyViewModel: StudyViewModel
    private var name = ""
    private var university = ""
    private var email = ""
    private var subjects: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        name = intent.getStringExtra(ProfileExtras.NAME).orEmpty()
        university = intent.getStringExtra(ProfileExtras.UNIVERSITY).orEmpty()
        email = intent.getStringExtra(ProfileExtras.EMAIL).orEmpty()
        subjects = intent.getStringArrayListExtra(ProfileExtras.SUBJECTS).orEmpty()
        binding.tvInicialPerfil.text = name.firstOrNull()?.uppercase() ?: "S"
        binding.tvNombrePerfil.text = name.ifBlank { "Estudiante" }
        binding.tvCorreoPerfil.text = email
        binding.tvUniversidadPerfil.text = university.ifBlank { "Universidad no especificada" }
        binding.tvMateriasPerfil.text = subjects.ifEmpty { listOf("Aún no agregas materias") }
            .joinToString(" · ")
        loadProfilePhoto()

        studyViewModel = ViewModelProvider(
            this,
            AppViewModelFactory(application)
        )[StudyViewModel::class.java]
        studyViewModel.state.observe(this) { state ->
            binding.progressPerfil.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            if (state.subjects.isNotEmpty() || state.assignments.isEmpty()) {
                subjects = state.subjects
                binding.tvMateriasPerfil.text = subjects.ifEmpty { listOf("Aún no agregas materias") }
                    .joinToString(" · ")
            }
            state.message?.let { message ->
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                studyViewModel.clearMessage()
            }
            if (state.accountDeleted) {
                getSharedPreferences(PREF_PROFILE_PHOTOS, MODE_PRIVATE).edit()
                    .remove(email.trim().lowercase()).apply()
                returnToLogin()
            }
        }

        binding.btnVolverPerfil.setOnClickListener { finish() }
        binding.tvCambiarFotoPerfil.setOnClickListener { photoPicker.launch(arrayOf("image/*")) }
        binding.btnCerrarSesion.setOnClickListener { confirmSignOut() }
        binding.btnEliminarCuenta.setOnClickListener { confirmAccountDeletion() }
        studyViewModel.loadForUser(email)
    }

    private fun confirmSignOut() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cerrar sesión")
            .setMessage("Volverás al inicio de sesión. Tu cuenta y tus datos se conservarán.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar sesión") { _, _ -> returnToLogin() }
            .show()
    }

    private fun confirmAccountDeletion() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar cuenta")
            .setMessage("Se borrarán tu perfil, materias, asignaciones y libros favoritos. Esta acción no se puede deshacer.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar") { _, _ -> requestDeletePassword() }
            .show()
    }

    private fun requestDeletePassword() {
        val dialogBinding = DialogDeleteAccountBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Confirmar eliminación")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar cuenta", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = dialogBinding.etConfirmarPassword.text?.toString().orEmpty()
                if (password.isBlank()) {
                    dialogBinding.etConfirmarPassword.error = "Escribe tu contraseña."
                } else {
                    studyViewModel.deleteAccount(password)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun returnToLogin() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        )
        finish()
    }

    private fun saveProfilePhoto(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some document providers grant temporary access only; show the selected image for this session.
        }
        getSharedPreferences(PREF_PROFILE_PHOTOS, MODE_PRIVATE).edit()
            .putString(email.trim().lowercase(), uri.toString())
            .apply()
        binding.ivFotoPerfil.setImageURI(uri)
        binding.ivFotoPerfil.visibility = View.VISIBLE
        binding.tvInicialPerfil.visibility = View.GONE
    }

    private fun loadProfilePhoto() {
        val savedPhoto = getSharedPreferences(PREF_PROFILE_PHOTOS, MODE_PRIVATE)
            .getString(email.trim().lowercase(), null) ?: return
        try {
            binding.ivFotoPerfil.setImageURI(Uri.parse(savedPhoto))
            binding.ivFotoPerfil.visibility = View.VISIBLE
            binding.tvInicialPerfil.visibility = View.GONE
        } catch (_: Exception) {
            getSharedPreferences(PREF_PROFILE_PHOTOS, MODE_PRIVATE).edit()
                .remove(email.trim().lowercase()).apply()
        }
    }

    private companion object {
        const val PREF_PROFILE_PHOTOS = "profile_photos"
    }
}
