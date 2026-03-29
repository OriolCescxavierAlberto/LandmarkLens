package com.example.landmarklens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

// ─── Modelos de datos ─────────────────────────────────────────────────────────
enum class AppTab { CAMERA, MAP, CHAT, ML }

data class ChatMessage(val role: String, val text: String)

// ─── Activity ─────────────────────────────────────────────────────────────────
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

// ─── Raíz de la UI ───────────────────────────────────────────────────────────
@Composable
fun MainApp(vm: LandmarkViewModel = viewModel()) {

    // Todo el estado viene del ViewModel — sobrevive rotaciones
    val currentTab = vm.currentTab

    val needsSensors = currentTab == AppTab.CAMERA || currentTab == AppTab.MAP
    DisposableEffect(needsSensors) {
        if (needsSensors) vm.startSensors()
        onDispose { vm.stopSensors() }
    }

    Scaffold(
        containerColor = Color(0xFFF3F3F3),
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = currentTab == AppTab.CAMERA,
                    onClick = { vm.setTab(AppTab.CAMERA) },
                    icon = { Icon(Icons.Default.PhotoCamera, "Explorar") },
                    label = { Text("Explorar") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.MAP,
                    onClick = { vm.setTab(AppTab.MAP) },
                    icon = { Icon(Icons.Default.LocationOn, "Mapa") },
                    label = { Text("Mapa") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.CHAT,
                    onClick = { vm.setTab(AppTab.CHAT) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, "IA") },
                    label = { Text("Guía IA") }
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.ML,
                    onClick = { vm.setTab(AppTab.ML) },
                    icon = { Icon(Icons.Default.Memory, "ML") },
                    label = { Text("Offline") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                AppTab.CAMERA -> CameraLandmarkScreen(vm)
                AppTab.MAP    -> MapTab(vm)
                AppTab.CHAT   -> OllamaChatScreen(vm)
                AppTab.ML     -> MLOfflineScreen()
            }
        }
    }
}

// ─── Pantalla Cámara ─────────────────────────────────────────────────────────
@Composable
fun CameraLandmarkScreen(vm: LandmarkViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val TAG = "CameraLandmarkScreen"

    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: false
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (hasLocationPermission) {
            vm.updateLocationBalanced()
            Log.d(TAG, "Permiso de ubicación otorgado")
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else if (hasLocationPermission) {
            vm.updateLocationBalanced()
        }
    }

    if (vm.showResult) {
        CaptureResultScreen(vm)
        return
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFFF3F3F3)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(Icons.Default.Camera, contentDescription = null,
                    modifier = Modifier.size(64.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Permisos requeridos", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Se necesitan permisos de cámara y ubicación para usar esta función.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.Gray
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF39C12))
                ) {
                    Text("Otorgar Permisos", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { preview ->
                    previewView = preview
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val cameraPreview = Preview.Builder().build()
                            cameraPreview.setSurfaceProvider(preview.surfaceProvider)
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview
                            )
                            Log.d(TAG, "Cámara iniciada")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error cámara: ${e.message}", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay GPS + brújula
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text("Latitud: ${"%.4f".format(vm.lat)}", color = Color.White,
                style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Longitud: ${"%.4f".format(vm.lon)}", color = Color.White,
                style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Acimut: ${"%.1f".format(vm.azimuth)}°", color = Color.White,
                style = MaterialTheme.typography.labelMedium)
        }

        FloatingActionButton(
            onClick = {
                previewView?.bitmap?.let { bitmap ->
                    // FileUtils.saveBitmap movido aquí — es la única parte de UI que
                    // necesita el context del Composable; todo lo demás va al ViewModel.
                    FileUtils.saveBitmap(context, bitmap, vm.lat, vm.lon, vm.azimuth)
                        .also { Log.d(TAG, "Foto guardada en: $it") }

                    if (hasLocationPermission) {
                        vm.captureWithHighAccuracyLocation(bitmap)
                    } else {
                        vm.onPhotoCaptured(bitmap)
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            containerColor = Color(0xFFF39C12)
        ) {
            Icon(Icons.Default.Camera, contentDescription = "Capturar", tint = Color.White)
        }
    }
}

// ─── Pantalla Resultado de Captura ───────────────────────────────────────────
@Composable
fun CaptureResultScreen(vm: LandmarkViewModel) {
    val context = LocalContext.current

    // PlacesService sigue instanciándose aquí porque necesita Context,
    // pero la lógica de red (coroutine + estado) vive en el ViewModel.
    val placesService = remember { PlacesService(context) }

    LaunchedEffect(vm.showResult) {
        if (vm.showResult) vm.fetchLocationInfo(placesService)
    }

    BackHandler { vm.resetCapture() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Monumento Detectado", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = Color(0xFFF39C12))
            Spacer(modifier = Modifier.height(16.dp))

            vm.capturedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto capturada",
                    modifier = Modifier.fillMaxWidth().height(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, Color(0xFFF39C12), RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                vm.isLoadingLocation -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                            .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(40.dp),
                                color = Color(0xFFF39C12))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Buscando lugar...", color = Color.Gray)
                        }
                    }
                }
                vm.locationError != null -> {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("⚠️ Error", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                            Text(vm.locationError!!, style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7F0000))
                        }
                    }
                }
                else -> {
                    MapDisplay(
                        latitude = vm.capturedLat,
                        longitude = vm.capturedLon,
                        locationName = vm.identifiedLocation?.name ?: "Ubicación capturada"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📍 Información del Lugar", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = Color(0xFFF39C12))
                    Spacer(modifier = Modifier.height(12.dp))
                    if (vm.identifiedLocation != null) {
                        Text("Nombre: ${vm.identifiedLocation!!.name}",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tipo: ${vm.identifiedLocation!!.type}",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (vm.identifiedLocation!!.address.isNotEmpty()) {
                            Text("Ubicación: ${vm.identifiedLocation!!.address}",
                                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else if (!vm.isLoadingLocation) {
                        Text("No se identificó el lugar específico",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Metadatos de Captura", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Latitud:", fontWeight = FontWeight.SemiBold,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        Text("%.6f".format(vm.capturedLat),
                            fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Longitud:", fontWeight = FontWeight.SemiBold,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        Text("%.6f".format(vm.capturedLon),
                            fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Acimut:", fontWeight = FontWeight.SemiBold,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        Text("%.1f°".format(vm.capturedAzimuth),
                            fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { vm.resetCapture() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF39C12))
            ) {
                Text("Volver a la cámara", fontWeight = FontWeight.Bold)
            }
        }

        IconButton(
            onClick = { vm.resetCapture() },
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)
                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar")
        }
    }
}

// ─── Pestaña Mapa ────────────────────────────────────────────────────────────
@Composable
fun MapTab(vm: LandmarkViewModel) {
    val context = LocalContext.current

    val hasLocationPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) {
        if (hasLocationPermission) vm.updateLocationBalanced()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Mi Ubicación", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = Color(0xFFF39C12))
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Latitud", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("%.6f".format(vm.lat), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Longitud", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("%.6f".format(vm.lon), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Acimut", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text("%.1f°".format(vm.azimuth), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!hasLocationPermission) {
            Card(modifier = Modifier.fillMaxWidth().height(350.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sin permiso de ubicación", color = Color.Gray)
                }
            }
        } else if (vm.lat == 0.0 && vm.lon == 0.0) {
            Card(modifier = Modifier.fillMaxWidth().height(350.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFF39C12))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Obteniendo ubicación GPS...", color = Color.Gray)
                    }
                }
            }
        } else {
            OsmMapView(
                latitude = vm.lat,
                longitude = vm.lon,
                locationName = "Mi posición",
                modifier = Modifier.fillMaxWidth().height(400.dp).clip(RoundedCornerShape(16.dp))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { if (hasLocationPermission) vm.updateLocationBalanced() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF39C12)),
            enabled = hasLocationPermission
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.padding(4.dp))
            Text("Actualizar ubicación", fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Pestaña ML ──────────────────────────────────────────────────────────────
@Composable
fun MLOfflineScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Construction, contentDescription = null,
            modifier = Modifier.size(64.dp), tint = Color.Gray)
        Spacer(Modifier.height(16.dp))
        Text("Próximamente: Análisis ML offline",
            style = MaterialTheme.typography.titleMedium, color = Color.Gray)
    }
}

// ─── Pestaña Chat ─────────────────────────────────────────────────────────────
@Composable
fun OllamaChatScreen(vm: LandmarkViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Carga modelos si aún no se han cargado — lógica en el ViewModel
    LaunchedEffect(Unit) {
        vm.loadModelsIfNeeded()
    }

    // Scroll automático al último mensaje
    LaunchedEffect(vm.chatMessages.size) {
        if (vm.chatMessages.isNotEmpty()) listState.animateScrollToItem(vm.chatMessages.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Modelo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !vm.isChatLoading
            ) {
                Text(vm.selectedModel)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                vm.availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = { vm.selectedModel = model; expanded = false }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Conversación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            if (vm.chatMessages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Todavía no hay mensajes", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(vm.chatMessages) { msg -> ChatBubble(message = msg) }
                    if (vm.isChatLoading) {
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
            value = vm.chatQuestion,
            onValueChange = { vm.chatQuestion = it },
            modifier = Modifier.fillMaxWidth().height(80.dp),
            label = { Text("Pregunta") },
            placeholder = { Text("Escribe tu pregunta") },
            shape = RoundedCornerShape(12.dp),
            enabled = !vm.isChatLoading,
            maxLines = 3
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { vm.sendChatMessage(vm.chatQuestion) },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF39C12),
                contentColor = Color.White
            ),
            enabled = !vm.isChatLoading
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
            Spacer(modifier = Modifier.padding(4.dp))
            Text("Enviar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Burbuja de chat ──────────────────────────────────────────────────────────
@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.widthIn(max = 300.dp)) {
            if (!isUser) {
                Icon(Icons.Default.SmartToy, "Assistant", tint = Color(0xFFF39C12),
                    modifier = Modifier.padding(top = 6.dp, end = 6.dp))
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) Color(0xFFFFE0B2) else Color(0xFFF5F5F5)
                ),
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
                Icon(Icons.Default.Android, "User", tint = Color(0xFF6D4C41),
                    modifier = Modifier.padding(top = 6.dp, start = 6.dp))
            }
        }
    }
}