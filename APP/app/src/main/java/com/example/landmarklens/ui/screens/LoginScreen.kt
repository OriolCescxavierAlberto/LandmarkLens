package com.example.landmarklens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.landmarklens.ui.viewmodel.AuthViewModel

@Composable
fun LoginScreen(authViewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isRegisterMode by remember { mutableStateOf(false) }

    val isLoading = authViewModel.isLoading
    val errorMessage = authViewModel.errorMessage

    // Mostrar botón de reenvío solo cuando el error es por email no verificado
    val showResendButton = !isRegisterMode &&
            errorMessage != null &&
            errorMessage.contains("verificar", ignoreCase = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "🏛️",
                fontSize = 64.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "LandmarkLens",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                if (isRegisterMode) "Crear cuenta" else "Inicia sesión para explorar",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 40.dp)
            )

            // Email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; authViewModel.clearError() },
                label = { Text("Correo electrónico") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Contraseña
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; authViewModel.clearError() },
                label = { Text("Contraseña") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Mensaje de error / confirmación
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = if (errorMessage.startsWith("✉️"))
                        MaterialTheme.colorScheme.primary  // azul informativo
                    else
                        MaterialTheme.colorScheme.error,   // rojo error
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Botón principal login / registro
            Button(
                onClick = {
                    if (isRegisterMode) authViewModel.register(email, password)
                    else authViewModel.login(email, password)
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (isRegisterMode) "Crear cuenta" else "Entrar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // Botón reenviar verificación (aparece automáticamente si el error es por email no verificado)
            if (showResendButton) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { authViewModel.resendVerificationEmail(email, password) },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "Reenviar email de verificación",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cambiar entre login / registro
            TextButton(onClick = {
                isRegisterMode = !isRegisterMode
                authViewModel.clearError()
            }) {
                Text(
                    if (isRegisterMode) "¿Ya tienes cuenta? Inicia sesión"
                    else "¿No tienes cuenta? Regístrate",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}