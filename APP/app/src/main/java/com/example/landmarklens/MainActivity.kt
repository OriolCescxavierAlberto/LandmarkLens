/**
 * LandmarkLens - Aplicación de identificación de monumentos mediante IA
 *
 * Estructura:
 * - Pestaña 1 (Cámara): Captura de fotos con metadatos de GPS y brújula
 * - Pestaña 2 (IA Ollama): Chat con IA local para descripción de monumentos
 * - Pestaña 3 (ML Local): Reservado para análisis ML offline futuro
 *
 * Tecnologías utilizadas:
 * - Jetpack Compose para UI
 * - CameraX para captura de fotos
 * - FusedLocationProviderClient para GPS
 * - SensorManager para Acimut (brújula)
 * - MVVM pattern con ViewModel
 */

package com.example.landmarklens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * Actividad principal de LandmarkLens
 * Configura la UI principal y habilita el diseño edge-to-edge
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF3F3F3)
                ) {
                    MainApp()
                }
            }
        }
    }
}

enum class AppTab { CAMERA, CHAT, ML }

data class ChatMessage(
    val role: String,
    val text: String
)

@Composable
fun MainApp(landmarkViewModel: LandmarkViewModel = viewModel()) {
    var currentTab by remember { mutableStateOf(AppTab.CAMERA) }
    val context = LocalContext.current

    // Chat states persisted
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var availableModels by remember { mutableStateOf(listOf("Loading...")) }
    var selectedModel by remember { mutableStateOf("Loading...") }
    var question by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        landmarkViewModel.startSensors(context)
        onDispose { landmarkViewModel.stopSensors() }
    }

    Scaffold(
        containerColor = Color(0xFFF3F3F3),
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = currentTab == AppTab.CAMERA,
                    onClick = { currentTab = AppTab.CAMERA },
                    icon = { Icon(Icons.Default.PhotoCamera, "Explorar") },
                    label = { Text("Explorar") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.CHAT,
                    onClick = { currentTab = AppTab.CHAT },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, "IA") },
                    label = { Text("Guía IA") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.ML,
                    onClick = { currentTab = AppTab.ML },
                    icon = { Icon(Icons.Default.Memory, "ML") },
                    label = { Text("Offline") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                AppTab.CAMERA -> CameraLandmarkScreen(landmarkViewModel)
                AppTab.CHAT -> OllamaChatScreen(
                    messages = messages,
                    availableModels = availableModels,
                    onModelsChange = { availableModels = it },
                    selectedModel = selectedModel,
                    onModelChange = { selectedModel = it },
                    question = question,
                    onQuestionChange = { question = it },
                    isLoading = isLoading,
                    onLoadingChange = { isLoading = it }
                )
                AppTab.ML -> MLOfflineScreen()
            }
        }
    }
}

/**
 * Pantalla principal de cámara para capturar monumentos
 * Gestiona:
 * - Solicitud de permisos en tiempo de ejecución
 * - Vista previa de cámara con CameraX
 * - Captura de foto estática
 * - Actualización de metadatos (GPS y Acimut)
 * - Muestra de resultado tras captura
 */
@Composable
fun CameraLandmarkScreen(viewModel: LandmarkViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val TAG = "CameraLandmarkScreen"
    
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var hasPermissions by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: false
        val hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        
        hasPermissions = hasCameraPermission && hasLocationPermission
        
        if (hasLocationPermission) {
            viewModel.updateLocation(context)
            Log.d(TAG, "Permisos de ubicación otorgados")
        }
        
        if (hasCameraPermission) {
            Log.d(TAG, "Permisos de cámara otorgados")
        }
    }

    // Solicitar permisos al cargar la pantalla
    LaunchedEffect(Unit) {
        // Verificar si los permisos ya están concedidos
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasCameraPermission && hasLocationPermission) {
            hasPermissions = true
            viewModel.updateLocation(context)
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    if (viewModel.showResult) {
        CaptureResultScreen(viewModel)
    } else {
        if (hasPermissions) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Vista previa de cámara con CameraX
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { preview ->
                            previewView = preview
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val cameraPreview = Preview.Builder().build()
                                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                    
                                    cameraPreview.setSurfaceProvider(preview.surfaceProvider)
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, cameraPreview)
                                    Log.d(TAG, "Cámara iniciada correctamente")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error al configurar cámara: ${e.message}", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay con metadatos en tiempo real
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Latitud: ${"%.4f".format(viewModel.lat)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Longitud: ${"%.4f".format(viewModel.lon)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Acimut: ${"%.1f".format(viewModel.azimuth)}°",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Botón FAB para capturar foto
                FloatingActionButton(
                    onClick = {
                        viewModel.updateLocation(context)
                        previewView?.bitmap?.let { bitmap ->
                            viewModel.onPhotoCaptured(bitmap)
                            // Guardar foto localmente
                            val filePath = FileUtils.saveBitmap(
                                context,
                                bitmap,
                                viewModel.lat,
                                viewModel.lon,
                                viewModel.azimuth
                            )
                            Log.d(TAG, "Foto guardada en: $filePath")
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    containerColor = Color(0xFFF39C12)
                ) {
                    Icon(Icons.Default.Camera, contentDescription = "Capturar", tint = Color.White)
                }
            }
        } else {
            // Pantalla de error si no hay permisos
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF3F3F3)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Permisos requeridos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Se necesitan permisos de cámara y ubicación para usar esta función.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF39C12))
                    ) {
                        Text("Otorgar Permisos", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Pantalla de resultado de captura
 * Muestra:
 * - La foto capturada
 * - Metadatos exactos de la captura (Lat, Lon, Acimut)
 * - Botón para volver a la cámara
 */
@Composable
fun CaptureResultScreen(viewModel: LandmarkViewModel) {
    BackHandler {
        viewModel.resetCapture()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Monumento Detectado",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF39C12)
            )

            Spacer(modifier = Modifier.height(16.dp))

            viewModel.capturedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto capturada",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, Color(0xFFF39C12), RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Metadatos de la Captura",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Latitud:", fontWeight = FontWeight.SemiBold)
                        Text(text = "%.6f".format(viewModel.capturedLat))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Longitud:", fontWeight = FontWeight.SemiBold)
                        Text(text = "%.6f".format(viewModel.capturedLon))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Acimut:", fontWeight = FontWeight.SemiBold)
                        Text(text = "%.1f°".format(viewModel.capturedAzimuth))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.resetCapture() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF39C12))
            ) {
                Text(text = "Volver a la cámara", fontWeight = FontWeight.Bold)
            }
        }

        IconButton(
            onClick = { viewModel.resetCapture() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar")
        }
    }
}

/**
 * Pantalla de Machine Learning offline
 * Reservada para análisis ML local futuro
 * Por ahora muestra un placeholder con información de próximas características
 */
@Composable
fun MLOfflineScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Construction,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.Gray
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Próximamente: Análisis ML offline",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Gray
        )
    }
}

/**
 * Pantalla de chat con Ollama IA local
 * Permite interacción con modelos LLM disponibles localmente
 * Características:
 * - Selección dinámica de modelos disponibles
 * - Historial de conversación
 * - Indicador de carga durante procesamiento
 */
@Composable
fun OllamaChatScreen(
    messages: SnapshotStateList<ChatMessage>,
    availableModels: List<String>,
    onModelsChange: (List<String>) -> Unit,
    selectedModel: String,
    onModelChange: (String) -> Unit,
    question: String,
    onQuestionChange: (String) -> Unit,
    isLoading: Boolean,
    onLoadingChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (availableModels.size <= 1 && availableModels.firstOrNull() == "Loading...") {
            val models = OllamaClient.getModels()
            onModelsChange(models)
            if (selectedModel == "Loading...") {
                onModelChange(models.firstOrNull() ?: "No models")
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(text = "Modelo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                Text(selectedModel)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            onModelChange(model)
                            expanded = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Conversación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Todavía no hay mensajes", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { msg -> ChatBubble(message = msg) }
                    if (isLoading) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.padding(4.dp))
                                Text("Generando respuesta...")
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = question,
            onValueChange = { onQuestionChange(it) },
            modifier = Modifier.fillMaxWidth().height(80.dp),
            label = { Text("Pregunta") },
            placeholder = { Text("Escribe tu pregunta") },
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading,
            maxLines = 3
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = {
                val trimmedQuestion = question.trim()
                if (trimmedQuestion.isNotEmpty() && !isLoading) {
                    messages.add(ChatMessage(role = "user", text = trimmedQuestion))
                    onQuestionChange("")
                    onLoadingChange(true)
                    scope.launch {
                        try {
                            val reply = OllamaClient.askModel(selectedModel, trimmedQuestion)
                            messages.add(ChatMessage(role = "assistant", text = reply))
                        } catch (e: Exception) {
                            messages.add(ChatMessage(role = "assistant", text = "Error: ${e.message}"))
                        } finally {
                            onLoadingChange(false)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF39C12), contentColor = Color.White),
            enabled = !isLoading
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
            Spacer(modifier = Modifier.padding(4.dp))
            Text(text = "Enviar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Burbuja de chat para mostrar mensajes en la conversación
 * Diferencia visualmente entre mensajes del usuario y del asistente
 */
@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            if (!isUser) {
                Icon(Icons.Default.SmartToy, "Assistant", tint = Color(0xFFF39C12), modifier = Modifier.padding(top = 6.dp, end = 6.dp))
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isUser) Color(0xFFFFE0B2) else Color(0xFFF5F5F5)),
                modifier = Modifier.border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isUser) "Pregunta" else "Respuesta",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) Color(0xFFBF360C) else Color(0xFF616161)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = message.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (isUser) {
                Icon(Icons.Default.Android, "User", tint = Color(0xFF6D4C41), modifier = Modifier.padding(top = 6.dp, start = 6.dp))
            }
        }
    }
}


