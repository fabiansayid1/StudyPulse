package com.example.basededatos.view

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.basededatos.R
import com.example.basededatos.databinding.ActivityMainBinding
import com.example.basededatos.network.UserProfile
import com.example.basededatos.viewmodel.AppViewModelFactory
import com.example.basededatos.viewmodel.AuthViewModel
import com.google.android.material.snackbar.Snackbar
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var authViewModel: AuthViewModel

    private val registerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.toProfile()?.let(::openHome)
        }
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private lateinit var btnGoogle: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)
        btnGoogle = binding.btnGoogle

        authViewModel = ViewModelProvider(
            this,
            AppViewModelFactory(application)
        )[AuthViewModel::class.java]

        authViewModel.state.observe(this) { state ->
            binding.progressLogin.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            binding.btnIngresar.isEnabled = !state.isLoading
            state.errorMessage?.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() }
            state.profile?.let(::openHome)
        }

        binding.btnIngresar.setOnClickListener {
            val email = binding.etCorreo.text?.toString()?.trim().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            if (email.isBlank() || password.isBlank()) {
                Snackbar.make(binding.root, "Escribe tu correo y contraseña.", Snackbar.LENGTH_LONG).show()
            } else {
                authViewModel.login(email, password)
            }
        }

        btnGoogle.setOnClickListener {
            iniciarSesionGoogle()
        }

        binding.tvCrearCuenta.setOnClickListener {
            registerLauncher.launch(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun openHome(profile: UserProfile) {
        startActivity(Intent(this, HomeActivity::class.java).putProfile(profile))
        finish()
    }

    // 3. MÉTODO PARA AUTENTICAR EN FIREBASE
    private fun autenticarFirebase(idToken: String) {
        val credencialFirebase = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credencialFirebase).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                // Éxito
            } else {
                Snackbar.make(binding.root, "Error de autenticación: ${task.exception?.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    // 4. MÉTODO PARA PROCESAR LA CREDENCIAL
    private fun procesarCredencial(credential: Credential) {
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                autenticarFirebase(googleIdTokenCredential.idToken)
            } catch (e: GoogleIdTokenParsingException) {
                // Manejar error de parsing
            }
        }
    }

    // 5. MÉTODO PARA INICIAR SESIÓN
    private fun iniciarSesionGoogle() {
        val inicioGoogle = GetSignInWithGoogleOption.Builder(
            getString(R.string.default_web_client_id)
        ).build()

        val solicitud = GetCredentialRequest.Builder()
            .addCredentialOption(inicioGoogle)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = solicitud,
                    context = this@MainActivity
                )
                procesarCredencial(result.credential)
            } catch (e: GetCredentialException) {
                // Manejar error de Credential Manager
            }
        }
    }
}

private fun Intent.toProfile(): UserProfile? {
    val name = getStringExtra(ProfileExtras.NAME) ?: return null
    val university = getStringExtra(ProfileExtras.UNIVERSITY) ?: return null
    val email = getStringExtra(ProfileExtras.EMAIL) ?: return null
    val subjects = getStringArrayListExtra(ProfileExtras.SUBJECTS).orEmpty()
    return UserProfile(name, university, email, subjects)
}

fun Intent.putProfile(profile: UserProfile): Intent = apply {
    putExtra(ProfileExtras.NAME, profile.name)
    putExtra(ProfileExtras.UNIVERSITY, profile.university)
    putExtra(ProfileExtras.EMAIL, profile.email)
    putStringArrayListExtra(ProfileExtras.SUBJECTS, ArrayList(profile.subjects))
}
