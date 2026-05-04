package com.example.landmarklens.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    var currentUser: FirebaseUser? by mutableStateOf(auth.currentUser)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Rellena todos los campos"
            return
        }
        isLoading = true
        errorMessage = null
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                isLoading = false
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && user.isEmailVerified) {
                        // ✅ Email verificado → dejar entrar
                        currentUser = user
                    } else {
                        // ❌ Email no verificado → cerrar sesión y avisar
                        auth.signOut()
                        errorMessage = "Debes verificar tu correo antes de entrar. Revisa tu bandeja de entrada."
                    }
                } else {
                    errorMessage = task.exception?.localizedMessage ?: "Error al iniciar sesión"
                }
            }
    }

    fun register(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Rellena todos los campos"
            return
        }
        if (password.length < 6) {
            errorMessage = "La contraseña debe tener al menos 6 caracteres"
            return
        }
        isLoading = true
        errorMessage = null
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                isLoading = false
                if (task.isSuccessful) {
                    // Enviar email de verificación y cerrar sesión hasta que confirme
                    auth.currentUser?.sendEmailVerification()
                        ?.addOnCompleteListener {
                            auth.signOut()
                            errorMessage = "✉️ Te hemos enviado un email de verificación. Confírmalo y luego inicia sesión."
                        }
                } else {
                    errorMessage = task.exception?.localizedMessage ?: "Error al registrarse"
                }
            }
    }

    fun resendVerificationEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Introduce tu correo y contraseña para reenviar"
            return
        }
        isLoading = true
        errorMessage = null
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                isLoading = false
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null && !user.isEmailVerified) {
                        user.sendEmailVerification()
                            .addOnCompleteListener {
                                auth.signOut()
                                errorMessage = "✉️ Email de verificación reenviado. Revisa tu bandeja."
                            }
                    } else {
                        auth.signOut()
                        errorMessage = "Este correo ya está verificado. Inicia sesión normalmente."
                    }
                } else {
                    errorMessage = task.exception?.localizedMessage ?: "Error al reenviar el email"
                }
            }
    }

    fun logout() {
        auth.signOut()
        currentUser = null
    }

    fun clearError() {
        errorMessage = null
    }
}