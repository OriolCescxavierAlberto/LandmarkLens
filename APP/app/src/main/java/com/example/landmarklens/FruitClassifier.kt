package com.example.landmarklens

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp

/**
 * Clase encargada de toda la parte de inferencia con TensorFlow Lite.
 *
 * Flujo ML completo que implementa:
 * 1. Cargar modelo (.tflite)
 * 2. Cargar etiquetas (labels.txt)
 * 3. Preprocesar la imagen de entrada
 * 4. Ejecutar inferencia con TFLite
 * 5. Convertir logits a probabilidades mediante softmax
 * 6. Devolver la clase más probable
 */
class FruitClassifier(
    context: Context,
    private val modelName: String = "model.tflite",
    labelsFileName: String = "labels.txt"
) {

    companion object {
        /**
         * IMAGE_SIZE:
         * Tamaño esperado por el modelo en entrada.
         *
         * En este caso, el modelo fue entrenado con imágenes 32x32.
         * Por eso cualquier imagen capturada o cargada desde galería
         * debe redimensionarse a este tamaño antes de la inferencia.
         */
        private const val IMAGE_SIZE = 32

        /**
         * PIXEL_SIZE:
         * Número de canales por píxel.
         *
         * RGB = 3 canales:
         * - Red
         * - Green
         * - Blue
         */
        private const val PIXEL_SIZE = 3

        /**
         * BYTES_PER_CHANNEL:
         * Cada valor float ocupa 4 bytes.
         *
         * Como el modelo recibe floats, reservamos memoria
         * suponiendo 4 bytes por canal.
         */
        private const val BYTES_PER_CHANNEL = 4
    }

    /**
     * Interpreter:
     * Es el motor que ejecuta el modelo TensorFlow Lite en Android.
     */
    private val interpreter: Interpreter

    /**
     * Lista de clases en el mismo orden en que el modelo devuelve la salida.
     * Ejemplo típico:
     * [Apple, Banana, Orange]
     */
    private val labels: List<String>

    init {
        /**
         * Al construir la clase:
         * - se carga el modelo desde assets
         * - se cargan las etiquetas
         *
         * Esto se hace una sola vez para no penalizar rendimiento
         * cada vez que clasificamos una imagen.
         */
        interpreter = Interpreter(loadModelFile(context, modelName))
        labels = loadLabels(context, labelsFileName)
    }

    /**
     * Método principal de clasificación.
     *
     * Recibe un Bitmap y devuelve un ClassificationResult.
     */
    fun classify(bitmap: Bitmap): ClassificationResult {

        /**
         * Algunos bitmaps pueden venir con configuración HARDWARE,
         * lo que impide acceder a sus píxeles directamente en algunos casos.
         *
         * Por eso, si el bitmap es HARDWARE, lo convertimos a ARGB_8888.
         * Esto garantiza que luego podamos leer píxeles correctamente.
         */
        val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        /**
         * Paso de preprocesado 1:
         * Redimensionar la imagen al tamaño de entrada del modelo.
         *
         * Aunque la imagen original sea grande, el modelo espera 32x32.
         */
        val scaledBitmap = Bitmap.createScaledBitmap(softwareBitmap, IMAGE_SIZE, IMAGE_SIZE, true)

        /**
         * Paso de preprocesado 2:
         * Convertir la imagen a ByteBuffer.
         *
         * TensorFlow Lite no recibe directamente un Bitmap,
         * sino un buffer binario con los valores numéricos de entrada.
         */
        val inputBuffer = convertBitmapToByteBuffer(scaledBitmap)

        /**
         * Reservamos el tensor de salida.
         *
         * Shape esperado:
         * [1, número_de_clases]
         *
         * Si tenemos 3 clases, será algo equivalente a:
         * [1, 3]
         */
        val output = Array(1) { FloatArray(labels.size) }

        /**
         * Ejecución de la inferencia.
         *
         * El modelo procesa la imagen y escribe el resultado en 'output'.
         */
        interpreter.run(inputBuffer, output)

        /**
         * La salida del modelo son logits.
         *
         * Importante:
         * - Un logit NO es todavía una probabilidad.
         * - Puede valer más de 1, menos de 0, etc.
         *
         * Por eso no debemos mostrar estos valores como porcentaje directamente.
         */
        val logits = output[0]

        /**
         * Postprocesado:
         * Convertimos logits a probabilidades usando softmax.
         *
         * Tras este paso:
         * - todas las salidas estarán entre 0 y 1
         * - la suma total será aproximadamente 1
         */
        val probabilities = softmax(logits)

        /**
         * Buscamos el índice de la clase con mayor probabilidad.
         */
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0

        /**
         * Obtenemos:
         * - la etiqueta ganadora
         * - la confianza asociada
         */
        val bestLabel = labels.getOrElse(maxIndex) { "Desconocido" }
        val bestConfidence = probabilities[maxIndex]

        /**
         * Devolvemos también todas las probabilidades por clase,
         * útil para depuración, visualización o explicación en clase.
         */
        return ClassificationResult(
            label = bestLabel,
            confidence = bestConfidence,
            allConfidences = labels.mapIndexed { index, label ->
                label to probabilities[index]
            }
        )
    }

    /**
     * Liberación de recursos.
     *
     * El Interpreter ocupa memoria nativa, por lo que conviene cerrarlo
     * cuando ya no se necesita.
     */
    fun close() {
        interpreter.close()
    }

    /**
     * Carga el fichero .tflite desde la carpeta assets.
     *
     * Se utiliza un memory map para acceder al modelo de forma eficiente,
     * sin cargarlo de manera innecesaria en estructuras intermedias.
     */
    private fun loadModelFile(context: Context, modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Carga las etiquetas desde labels.txt.
     *
     * Cada línea del fichero corresponde a una clase del modelo.
     * El orden debe coincidir exactamente con la salida del modelo.
     */
    private fun loadLabels(context: Context, fileName: String): List<String> {
        return context.assets.open(fileName).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).readLines()
                .filter { it.isNotBlank() }
        }
    }

    /**
     * Convierte el Bitmap en el tensor de entrada que espera el modelo.
     *
     * Explicación del proceso:
     * - se reserva un ByteBuffer del tamaño adecuado
     * - se extraen los píxeles
     * - de cada píxel se separan R, G y B
     * - se insertan los valores como float en el buffer
     *
     * Este modelo concreto ya incorpora internamente una capa:
     * Rescaling(1./255)
     *
     * Eso significa que aquí NO debemos normalizar manualmente a [0,1].
     * Debemos pasar los valores en rango 0..255.
     */
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(
            IMAGE_SIZE * IMAGE_SIZE * PIXEL_SIZE * BYTES_PER_CHANNEL
        ).apply {
            /**
             * Usamos el orden nativo del sistema para evitar problemas
             * de interpretación binaria.
             */
            order(ByteOrder.nativeOrder())
        }

        /**
         * Array auxiliar para guardar todos los píxeles de la imagen.
         */
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)

        bitmap.getPixels(
            pixels,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height
        )

        /**
         * Recorremos la imagen píxel a píxel.
         */
        var pixelIndex = 0
        for (i in 0 until IMAGE_SIZE) {
            for (j in 0 until IMAGE_SIZE) {
                val pixelValue = pixels[pixelIndex++]

                /**
                 * Extraemos canales RGB del entero ARGB:
                 * - Red   = bits 16..23
                 * - Green = bits 8..15
                 * - Blue  = bits 0..7
                 *
                 * Se ignora el canal alpha porque el modelo trabaja con RGB.
                 */
                val red = ((pixelValue shr 16) and 0xFF).toFloat()
                val green = ((pixelValue shr 8) and 0xFF).toFloat()
                val blue = (pixelValue and 0xFF).toFloat()

                /**
                 * Se añaden al buffer en el orden RGB.
                 *
                 * El orden es importante porque el modelo espera esa disposición.
                 */
                buffer.putFloat(red)
                buffer.putFloat(green)
                buffer.putFloat(blue)
            }
        }

        /**
         * Reposicionamos el puntero al inicio para que TFLite lea el buffer
         * desde el primer byte.
         */
        buffer.rewind()
        return buffer
    }

    /**
     * Softmax:
     * Convierte logits en probabilidades.
     *
     * Fórmula conceptual:
     * p_i = e^(x_i) / suma(e^(x_j))
     *
     * Pequeño detalle importante:
     * restamos el máximo logit antes de hacer exp()
     * para mejorar estabilidad numérica y evitar overflow.
     */
    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp((it - maxLogit).toDouble()) }
        val sum = exps.sum()
        return exps.map { (it / sum).toFloat() }.toFloatArray()
    }
}