package com.example.landmarklens

/**
 * Resultado estructurado de una predicción del modelo.
 *
 * Qué representa cada campo:
 * - label: clase predicha con mayor probabilidad.
 * - confidence: probabilidad asociada a la clase ganadora, en rango [0, 1].
 * - allConfidences: lista completa de clases y sus probabilidades.
 *
 * Esto es útil porque en ML no solo interesa "qué clase ha ganado",
 * sino también "con qué seguridad" y "cómo quedaron las demás clases".
 */
data class ClassificationResult(
    val label: String,
    val confidence: Float,
    val allConfidences: List<Pair<String, Float>>
)