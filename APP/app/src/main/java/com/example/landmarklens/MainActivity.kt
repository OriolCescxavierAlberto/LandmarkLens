package com.example.landmarklens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
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
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Activity principal de la app.
 *
 * La app tiene dos funcionalidades:
 * 1. Clasificación de frutas con TensorFlow Lite
 * 2. Chat con Ollama
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

/**
 * Dos pantallas principales dentro del scaffold.
 */
enum class MainScreen {
    FRUIT,
    OLLAMA
}

/**
 * Estructura básica para un mensaje del chat.
 *
 * role:
 * - "user"
 * - "assistant"
 */
data class ChatMessage(
    val role: String,
    val text: String
)

@Composable
fun MainApp() {
    /**
     * Estado que controla qué pantalla se muestra.
     */
    var currentScreen by remember { mutableStateOf(MainScreen.FRUIT) }

    Scaffold(
        containerColor = Color(0xFFF3F3F3),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF39C12))
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                Text(
                    text = if (currentScreen == MainScreen.FRUIT) {
                        "ML with TensorFlowLite"
                    } else {
                        "Ollama Chat"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        bottomBar = {
            /**
             * Barra inferior para navegar entre ambas pantallas.
             */
            NavigationBar(
                containerColor = Color.White
            ) {
                NavigationBarItem(
                    selected = currentScreen == MainScreen.FRUIT,
                    onClick = { currentScreen = MainScreen.FRUIT },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = "Fruit"
                        )
                    },
                    label = { Text("Fruit") }
                )

                NavigationBarItem(
                    selected = currentScreen == MainScreen.OLLAMA,
                    onClick = { currentScreen = MainScreen.OLLAMA },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.QuestionAnswer,
                            contentDescription = "Ollama"
                        )
                    },
                    label = { Text("Ollama") }
                )
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            MainScreen.FRUIT -> FruitClassifierScreen(
                modifier = Modifier.padding(innerPadding)
            )

            MainScreen.OLLAMA -> OllamaChatScreen(
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
fun FruitClassifierScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    /**
     * selectedBitmap:
     * imagen actual que se está clasificando o mostrando en pantalla.
     */
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    /**
     * resultText:
     * nombre de la clase ganadora.
     */
    var resultText by remember { mutableStateOf("Sin clasificar") }

    /**
     * confidenceText:
     * porcentaje asociado a la predicción.
     */
    var confidenceText by remember { mutableStateOf("") }

    /**
     * Instancia del clasificador ML.
     *
     * remember evita recrearla en cada recomposición.
     */
    val classifier = remember {
        FruitClassifier(context)
    }

    /**
     * Cerramos el intérprete cuando este composable desaparece
     * para liberar recursos nativos.
     */
    DisposableEffect(Unit) {
        onDispose {
            classifier.close()
        }
    }

    /**
     * Función local que encapsula el flujo:
     * Bitmap -> inferencia -> resultado en UI
     */
    fun classify(bitmap: Bitmap) {
        val result = classifier.classify(bitmap)
        selectedBitmap = bitmap
        resultText = result.label

        /**
         * result.confidence está en [0, 1].
         * Lo convertimos a porcentaje para mostrarlo al usuario.
         */
        confidenceText = "Confianza: ${"%.2f".format(result.confidence * 100)}%"
    }

    /**
     * Lanzador para tomar una foto desde la cámara.
     * Devuelve un Bitmap pequeño de previsualización.
     */
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let { classify(it) }
    }

    /**
     * Lanzador para elegir una imagen desde galería/selector del sistema.
     */
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = loadBitmapFromUri(context, it)
            bitmap?.let(::classify)
        }
    }

    /**
     * Lanzador para solicitar permiso de cámara.
     */
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                /**
                 * Vista de la imagen actual.
                 */
                if (selectedBitmap != null) {
                    Image(
                        bitmap = selectedBitmap!!.asImageBitmap(),
                        contentDescription = "Imagen seleccionada",
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Selecciona una fruta",
                            color = Color.Gray,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Classified as:",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                /**
                 * Clase predicha por el modelo.
                 */
                Text(
                    text = resultText,
                    color = Color(0xFFC62828),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 120.dp)
                )

                /**
                 * Confianza de la predicción.
                 */
                if (confidenceText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = confidenceText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.DarkGray
                    )
                }
            }
        }

        /**
         * Acciones de entrada de imagen:
         * - cámara
         * - galería
         */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3F3F3))
                .padding(bottom = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val permissionGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (permissionGranted) {
                        cameraLauncher.launch(null)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF39C12),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Take Picture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = {
                    galleryLauncher.launch("image/*")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF39C12),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Launch Gallery",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun OllamaChatScreen(modifier: Modifier = Modifier) {
    /**
     * Historial de conversación.
     */
    val messages = remember { mutableStateListOf<ChatMessage>() }

    /**
     * Modelos disponibles, cargados desde Ollama o desde el mock.
     */
    var availableModels by remember { mutableStateOf(listOf("Loading...")) }

    /**
     * Modelo seleccionado actualmente.
     */
    var selectedModel by remember { mutableStateOf("Loading...") }

    /**
     * Texto que escribe el usuario.
     */
    var question by remember { mutableStateOf("") }

    /**
     * Estado de carga mientras se espera respuesta.
     */
    var isLoading by remember { mutableStateOf(false) }

    /**
     * Control del desplegable de modelos.
     */
    var expanded by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    /**
     * Al entrar en la pantalla, se piden los modelos disponibles.
     */
    LaunchedEffect(Unit) {
        val models = OllamaClient.getModels()
        availableModels = models
        selectedModel = models.firstOrNull() ?: "No models"
    }

    /**
     * Auto-scroll al último mensaje.
     */
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    /**
     * Cuando el usuario envía una pregunta:
     * - se añade al historial
     * - se llama a Ollama
     * - se añade la respuesta al historial
     */
    LaunchedEffect(isLoading, messages.size, selectedModel) {
        if (isLoading && messages.isNotEmpty()) {
            val lastUserMessage = messages.lastOrNull()?.takeIf { it.role == "user" }
            if (lastUserMessage != null) {
                val reply = OllamaClient.askModel(
                    model = selectedModel,
                    prompt = lastUserMessage.text
                )
                messages.add(ChatMessage(role = "assistant", text = reply))
                isLoading = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            text = "Modelo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        /**
         * Selector de modelo.
         */
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(selectedModel)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            selectedModel = model
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Conversación",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        /**
         * Zona principal del chat.
         */
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Todavía no hay mensajes",
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubble(message = msg)
                    }

                    if (isLoading) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.padding(4.dp))
                                Text("Generando respuesta...")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        /**
         * Caja de texto para escribir la pregunta.
         */
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
            label = { Text("Pregunta") },
            placeholder = { Text("Escribe tu pregunta") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        /**
         * Botón de envío.
         */
        Button(
            onClick = {
                val trimmedQuestion = question.trim()
                if (trimmedQuestion.isNotEmpty() && !isLoading) {
                    messages.add(ChatMessage(role = "user", text = trimmedQuestion))
                    question = ""
                    isLoading = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF39C12),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Enviar"
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = "Enviar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

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
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = "Assistant",
                    tint = Color(0xFFF39C12),
                    modifier = Modifier.padding(top = 6.dp, end = 6.dp)
                )
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) Color(0xFFFFE0B2) else Color(0xFFF5F5F5)
                ),
                modifier = Modifier.border(
                    width = 1.dp,
                    color = Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(16.dp)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = if (isUser) "Pregunta" else "Respuesta",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) Color(0xFFBF360C) else Color(0xFF616161)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            if (isUser) {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = "User",
                    tint = Color(0xFF6D4C41),
                    modifier = Modifier.padding(top = 6.dp, start = 6.dp)
                )
            }
        }
    }
}

/**
 * Convierte una URI seleccionada desde galería en un Bitmap.
 *
 * En Android P o superior se usa ImageDecoder.
 * En versiones antiguas se usa MediaStore.
 *
 * Además, cuando se usa ImageDecoder, se fuerza ALLOCATOR_SOFTWARE
 * para evitar problemas posteriores al acceder a los píxeles.
 */
fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}