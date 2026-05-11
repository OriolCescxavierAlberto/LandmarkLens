package com.example.landmarklens.data.model

/**
 * Modelo para la respuesta de la API de análisis remoto.
 * Contiene los resultados del análisis de la imagen/ubicación.
 */
data class AnalysisResult(
    val id: String? = null,
    val landmark: String? = null,
    val confidence: Float = 0f,
    val description: String? = null,
    val category: String? = null,
    val historicalInfo: String? = null,
    val estimatedDistance: Float? = null,
    val rawResponse: Map<String, Any> = emptyMap()
) {
    val isSuccessful: Boolean
        get() = landmark != null && confidence > 0f
}

