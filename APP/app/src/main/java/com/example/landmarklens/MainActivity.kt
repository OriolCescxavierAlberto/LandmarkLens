package com.example.landmarklens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.BufferedReader
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// ── Pantallas de navegación ─────────────────────────────────────────
enum class MainScreen { MAP, CAMERA, CHAT }

// ── Modelo de mensaje de chat ───────────────────────────────────────
data class ChatMessage(val role: String, val text: String)

// ── Activity principal ──────────────────────────────────────────────
class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var mapViewRef: MapView? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permisos comprobados inline al construir el mapa */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configurar OSMDroid
        val osmConfig = Configuration.getInstance()
        osmConfig.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        osmConfig.userAgentValue = packageName
        osmConfig.osmdroidTileCache = File(filesDir, "osmdroid_tiles")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color(0xFFF3F3F3)) {
                    MainApp(
                        fusedLocationClient = fusedLocationClient,
                        onMapViewCreated = { mv -> mapViewRef = mv },
                        onLocationCallbackCreated = { cb -> locationCallback = cb }
                    )
                }
            }
        }
    }

    override fun onResume()  { super.onResume();  mapViewRef?.onResume() }
    override fun onPause()   { super.onPause();   mapViewRef?.onPause(); locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) } }
    override fun onDestroy() { super.onDestroy(); mapViewRef?.onDetach() }
}

// ── Scaffold principal ──────────────────────────────────────────────
@Composable
fun MainApp(
    fusedLocationClient: FusedLocationProviderClient,
    onMapViewCreated: (MapView) -> Unit,
    onLocationCallbackCreated: (LocationCallback) -> Unit
) {
    var currentScreen by remember { mutableStateOf(MainScreen.MAP) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color(0xFFF3F3F3),
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color(0xFF1E88E5))
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                Text(
                    text = when (currentScreen) {
                        MainScreen.MAP    -> "LandmarkLens — Mapa"
                        MainScreen.CAMERA -> "LandmarkLens — Cámara"
                        MainScreen.CHAT   -> "LandmarkLens — Chat"
                    },
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White) {
                NavigationBarItem(
                    selected = currentScreen == MainScreen.MAP,
                    onClick  = { currentScreen = MainScreen.MAP },
                    icon     = { Icon(Icons.Outlined.Map, "Mapa") },
                    label    = { Text("Mapa") }
                )
                NavigationBarItem(
                    selected = currentScreen == MainScreen.CAMERA,
                    onClick  = { currentScreen = MainScreen.CAMERA },
                    icon     = { Icon(Icons.Outlined.CameraAlt, "Cámara") },
                    label    = { Text("Cámara") }
                )
                NavigationBarItem(
                    selected = currentScreen == MainScreen.CHAT,
                    onClick  = { currentScreen = MainScreen.CHAT },
                    icon     = { Icon(Icons.Outlined.QuestionAnswer, "Chat") },
                    label    = { Text("Chat") }
                )
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            MainScreen.MAP -> MapScreen(
                modifier = Modifier.padding(innerPadding),
                fusedLocationClient = fusedLocationClient,
                onMapViewCreated = onMapViewCreated,
                onLocationCallbackCreated = onLocationCallbackCreated
            )
            MainScreen.CAMERA -> CameraScreen(modifier = Modifier.padding(innerPadding))
            MainScreen.CHAT   -> OllamaChatScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

// ── Pantalla de mapa ────────────────────────────────────────────────
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    fusedLocationClient: FusedLocationProviderClient,
    onMapViewCreated: (MapView) -> Unit,
    onLocationCallbackCreated: (LocationCallback) -> Unit
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val mapView = MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                controller.setZoom(15.0)
                controller.setCenter(GeoPoint(41.3851, 2.1734))
            }

            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), mapView)
            locationOverlay.enableMyLocation()
            locationOverlay.enableFollowLocation()
            locationOverlay.runOnFirstFix {
                (ctx as? android.app.Activity)?.runOnUiThread {
                    mapView.controller.animateTo(locationOverlay.myLocation)
                    mapView.controller.setZoom(17.0)
                }
            }
            mapView.overlays.add(locationOverlay)

            val hasPermission = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                val request  = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
                    .setMinUpdateDistanceMeters(10f).build()
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {}
                }
                onLocationCallbackCreated(callback)
                try { fusedLocationClient.requestLocationUpdates(request, callback, ctx.mainLooper) }
                catch (_: SecurityException) {}
            }

            loadLandmarkMarkers(ctx, mapView)
            onMapViewCreated(mapView)
            mapView
        }
    )
}

// ── Carga marcadores desde assets/landmarks.json ───────────────────
private fun loadLandmarkMarkers(context: Context, mapView: MapView) {
    try {
        val json      = context.assets.open("landmarks.json").bufferedReader().use { it.readText() }
        val landmarks = JSONObject(json).getJSONArray("landmarks")
        for (i in 0 until landmarks.length()) {
            val lm  = landmarks.getJSONObject(i)
            val lat = lm.optDouble("lat", Double.NaN)
            val lon = lm.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            val marker = Marker(mapView).apply {
                position = GeoPoint(lat, lon)
                title    = lm.optString("name", "Landmark")
                snippet  = buildSnippet(lm)
                icon     = landmarkIcon(lm.optInt("fame_score", 0))
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    } catch (_: Exception) {}
}

private fun buildSnippet(lm: JSONObject): String {
    val parts = mutableListOf<String>()
    lm.optString("architect").takeIf { it.isNotEmpty() }?.let { parts.add("Arq: $it") }
    lm.optString("year").takeIf { it.isNotEmpty() }?.let { parts.add("Año: $it") }
    lm.optString("address").takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    lm.optJSONArray("categories")?.takeIf { it.length() > 0 }?.let { parts.add(it.getString(0)) }
    return parts.joinToString(" · ").ifEmpty { "Punto de interés" }
}

private fun landmarkIcon(fameScore: Int): Drawable = CircleDrawable(
    when { fameScore >= 5 -> Color.parseColor("#E53935"); fameScore >= 3 -> Color.parseColor("#FB8C00"); else -> Color.parseColor("#1E88E5") }
)

private class CircleDrawable(private val fillColor: Int) : Drawable() {
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor; style = Paint.Style.FILL }
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 6f }
    override fun draw(c: Canvas) { val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY(); val r = minOf(bounds.width(), bounds.height()) / 2f - 4f; c.drawCircle(cx, cy, r, fp); c.drawCircle(cx, cy, r, sp) }
    override fun setAlpha(a: Int) { fp.alpha = a }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { fp.colorFilter = cf }
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth()  = 48
    override fun getIntrinsicHeight() = 48
}

// ── Pantalla de cámara ───────────────────────────────────────────────
@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusText     by remember { mutableStateOf("Haz una foto o selecciona una imagen") }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        bmp?.let { selectedBitmap = it; statusText = "Imagen capturada" }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadBitmapFromUri(context, it)?.let { bmp -> selectedBitmap = bmp; statusText = "Imagen cargada" } }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null) else statusText = "Permiso de cámara denegado"
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        // Recuadro de foto
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (selectedBitmap != null) {
                Image(
                    bitmap = selectedBitmap!!.asImageBitmap(),
                    contentDescription = "Imagen seleccionada",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .border(2.dp, androidx.compose.ui.graphics.Color(0xFFBDBDBD), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.CameraAlt, contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(0xFFBDBDBD),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(statusText, color = androidx.compose.ui.graphics.Color.Gray,
                            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                        cameraLauncher.launch(null)
                    else permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1E88E5), contentColor = androidx.compose.ui.graphics.Color.White)
            ) { Text("Hacer foto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

            Button(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1E88E5), contentColor = androidx.compose.ui.graphics.Color.White)
            ) { Text("Abrir galería", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        }
    }
}

// ── Pantalla de chat Ollama ──────────────────────────────────────────
@Composable
fun OllamaChatScreen(modifier: Modifier = Modifier) {
    val messages        = remember { mutableStateListOf<ChatMessage>() }
    var availableModels by remember { mutableStateOf(listOf("Loading...")) }
    var selectedModel   by remember { mutableStateOf("Loading...") }
    var question        by remember { mutableStateOf("") }
    var isLoading       by remember { mutableStateOf(false) }
    var expanded        by remember { mutableStateOf(false) }
    val listState       = rememberLazyListState()

    LaunchedEffect(Unit) { val m = OllamaClient.getModels(); availableModels = m; selectedModel = m.firstOrNull() ?: "No models" }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    LaunchedEffect(isLoading, messages.size, selectedModel) {
        if (isLoading && messages.isNotEmpty()) {
            val last = messages.lastOrNull()?.takeIf { it.role == "user" }
            if (last != null) { messages.add(ChatMessage("assistant", OllamaClient.askModel(selectedModel, last.text))); isLoading = false }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Text("Modelo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text(selectedModel) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                availableModels.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { selectedModel = it; expanded = false }) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Conversación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Todavía no hay mensajes", color = androidx.compose.ui.graphics.Color.Gray) }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(messages) { ChatBubble(it) }
                    if (isLoading) { item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.padding(4.dp)); Text("Generando respuesta...") } } }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = question, onValueChange = { question = it }, modifier = Modifier.fillMaxWidth().height(90.dp),
            label = { Text("Pregunta") }, placeholder = { Text("Escribe tu pregunta") }, singleLine = true, shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { val q = question.trim(); if (q.isNotEmpty() && !isLoading) { messages.add(ChatMessage("user", q)); question = ""; isLoading = true } },
            modifier = Modifier.fillMaxWidth().height(80.dp).navigationBarsPadding(), shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1E88E5), contentColor = androidx.compose.ui.graphics.Color.White)
        ) { Icon(Icons.Default.Send, "Enviar"); Spacer(Modifier.padding(4.dp)); Text("Enviar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
    }
}

// ── Burbuja de chat ──────────────────────────────────────────────────
@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.widthIn(max = 300.dp)) {
            if (!isUser) Icon(Icons.Default.SmartToy, "Assistant", tint = androidx.compose.ui.graphics.Color(0xFF1E88E5), modifier = Modifier.padding(top = 6.dp, end = 6.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isUser) androidx.compose.ui.graphics.Color(0xFFBBDEFB) else androidx.compose.ui.graphics.Color(0xFFF5F5F5)),
                modifier = Modifier.border(1.dp, androidx.compose.ui.graphics.Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(if (isUser) "Pregunta" else "Respuesta", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = if (isUser) androidx.compose.ui.graphics.Color(0xFF1565C0) else androidx.compose.ui.graphics.Color(0xFF616161))
                    Spacer(Modifier.height(4.dp))
                    Text(message.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (isUser) Icon(Icons.Default.Android, "User", tint = androidx.compose.ui.graphics.Color(0xFF1565C0), modifier = Modifier.padding(top = 6.dp, start = 6.dp))
        }
    }
}

// ── Cliente Ollama ───────────────────────────────────────────────────
object OllamaClient {
    private const val BASE_URL = "http://10.0.2.2:11434" // emulador; usa tu IP para dispositivo físico

    suspend fun getModels(): List<String> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("$BASE_URL/api/tags").openConnection() as HttpURLConnection).apply { requestMethod = "GET"; connectTimeout = 5000; readTimeout = 5000 }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext listOf("Error loading models")
            val arr = JSONObject(conn.inputStream.bufferedReader().use(BufferedReader::readText)).optJSONArray("models") ?: JSONArray()
            val models = (0 until arr.length()).map { arr.getJSONObject(it).optString("name", "unknown") }
            if (models.isEmpty()) listOf("No models found") else models
        } catch (_: Exception) { listOf("Connection error") }
    }

    suspend fun askModel(model: String, prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("$BASE_URL/api/generate").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 60000; doOutput = true; setRequestProperty("Content-Type", "application/json")
            }
            OutputStreamWriter(conn.outputStream).use { it.write(JSONObject().apply { put("model", model); put("prompt", prompt); put("stream", false) }.toString()) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            JSONObject(stream.bufferedReader().use(BufferedReader::readText)).optString("response", "No response from model")
        } catch (e: Exception) { "Error conectando con Ollama: ${e.message}" }
    }
}

// ── URI → Bitmap ─────────────────────────────────────────────────────
fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
    } else {
        @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
} catch (_: Exception) { null }