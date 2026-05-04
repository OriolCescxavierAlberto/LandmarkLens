package com.example.landmarklens.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.landmarklens.ui.screens.LoginScreen
import com.example.landmarklens.ui.screens.MainApp
import com.example.landmarklens.ui.theme.LandmarkLensTheme
import com.example.landmarklens.ui.viewmodel.AuthViewModel
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "LandmarkLens/1.0"

        enableEdgeToEdge()
        setContent {
            LandmarkLensTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (authViewModel.currentUser == null) {
                        LoginScreen(authViewModel = authViewModel)
                    } else {
                        MainApp(onLogout = { authViewModel.logout() })
                    }
                }
            }
        }
    }
}