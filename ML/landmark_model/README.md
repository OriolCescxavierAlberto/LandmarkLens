# LandmarkLens - Sistema ML de Identificación de Puntos de Interés en España

Sistema inteligente de identificación de landmarks españoles basado en:
- **Coordenadas GPS** del usuario
- **Orientación de cámara** (brújula/azimuth)
- **Base de datos geoespacial** de OpenStreetMap

Utiliza **Llama 3.2 3B** optimizado para GPU con memoria limitada (RTX 3060 6GB VRAM).

---

## Inicio Rápido

### 1. Requisitos Previos

**Software:**
- Python 3.9+
- [Ollama](https://ollama.ai) instalado y ejecutándose
- Git (opcional)

**Hardware Recomendado:**
- GPU: NVIDIA RTX 3060 (6GB VRAM) o similar
- RAM: 16GB DDR4 mínimo
- Almacenamiento: 5GB para modelo + datos

### 2. Instalación

```bash
# Clonar o descargar el proyecto
cd landmark_model

# Instalar dependencias Python
pip install -r requirements.txt

# Ejecutar setup completo (automatizado)
python setup.py
```

**Nota:** `setup.py` ejecutará automáticamente:
1. Instalación de dependencias
2. Extracción de landmarks de OSM
3. Generación de base de conocimiento
4. Entrenamiento y exportación del modelo para la app
5. Descarga de modelo llama3.2:3b (si no existe)
6. Registro del modelo en Ollama

**Tiempo estimado:** 20-30 minutos en primera ejecución

### 3. Verificar Instalación

```bash
# Verificar que Ollama está corriendo
ollama list

# Debe aparecer:
# landmark-finder   latest    2.1 GB

# Consulta de prueba
python query_model.py 41.38 2.17  # Barcelona: Sagrada Familia
```

---

## Uso

### Consultar sin Orientación (todos los landmarks cercanos)

```bash
python query_model.py <latitud> <longitud>
```

**Ejemplo:**
```bash
python query_model.py 40.416 -3.703  # Madrid
```

**Salida:**
```json
[
  {
    "name": "Palacio Real",
    "distance": 850,
    "confidence": "high"
  },
  {
    "name": "Catedral de la Almudena",
    "distance": 1200,
    "confidence": "high"
  }
]
```

### Consultar con Orientación de Cámara (azimuth)

```bash
python query_model.py <latitud> <longitud> <azimuth>
```

**Ejemplo:**
```bash
python query_model.py 41.38 2.17 45  # Barcelona, mirando NE
```

**Salida (con orientación):**
```json
{
  "target": "Sagrada Familia",
  "target_distance": 1200,
  "confidence": "high",
  "others": [
    {
      "name": "Park Güell",
      "distance": 3500
    },
    {
      "name": "Casa Batlló",
      "distance": 780
    }
  ]
}
```

### Usar Campo de Visión Personalizado (FOV)

```bash
python query_model.py <latitud> <longitud> <azimuth> <fov>
```

**Ejemplo con FOV = 90°:**
```bash
python query_model.py 41.38 2.17 45 90
```

---

## Scripts Disponibles

### `setup.py` — Setup Automatizado
Instalación y configuración completa del sistema.

```bash
python setup.py
```

**Qué hace:**
- Instala dependencias de `requirements.txt`
- Ejecuta `extract_landmarks.py`
- Ejecuta `generate_knowledge.py`
- Ejecuta `train_models.py`
- Exporta el modelo final en `artifacts/selected_model_bundle.joblib`
- Descarga llama3.2:3b (si no existe)
- Crea modelo en Ollama
- Valida la configuración

---

### `extract_landmarks.py` — Extracción de Datos OSM

Extrae landmarks de archivos OpenStreetMap.

```bash
python extract_landmarks.py [región]
```

**Parámetros:**
- `región` (opcional): "all", "catalonia", "valencia", "basque_country", "madrid"

**Salida:**
- `data/landmarks.json` (base de landmarks)

**Categorías extraídas:**
- Históricos: castillos, monumentos, catedrales
- Turismo: museos, galerías, atracciones
- Religiosos: iglesias, sinagogas, mezquitas
- Especiales: estadios, palacios, fortalezas

---

### `generate_knowledge.py` — Generación de Base de Conocimiento

Prepara datos para RAG (Retrieval-Augmented Generation).

```bash
python generate_knowledge.py
```

**Entrada:**
- `data/landmarks.json`
- `data/system_prompt.txt`

**Salida:**
- `data/training_examples.json` (ejemplos sintéticos)

**Características:**
- Genera variaciones de queries con diferentes ángulos
- Crea ejemplos para múltiples radios de búsqueda
- Prepara datos estructurados en JSON

---

### `train_models.py` — Entrenamiento y Exportación

Entrena varios modelos ligeros, ajusta hiperparámetros y exporta el mejor.

```bash
python train_models.py
```

**Salida:**
- `artifacts/selected_model_bundle.joblib` - bundle serializado para la app
- `artifacts/model_comparison.csv` - comparación entre modelos
- `artifacts/experiment_summary.json` - resumen del experimento
- `data/experiment_results.json` - copia del resumen para documentación

**Características:**
- Divide por `query_id` para evitar fuga de información
- Compara varios modelos ligeros
- Exporta el modelo final seleccionado con sus hiperparámetros

---

### `query_model.py` — Consulta del Modelo

Interfaz principal para consultar landmarks.

```bash
python query_model.py <lat> <lon> [azimuth] [fov]
```

**Argumentos:**
| Argumento | Tipo | Rango | Default |
|-----------|------|-------|---------|
| `lat` | Float | [-90, 90] | Requerido |
| `lon` | Float | [-180, 180] | Requerido |
| `azimuth` | Int | [0, 360] | None |
| `fov` | Int | [5, 180] | 70° |

**Características:**
- Índice espacial con grid para búsqueda O(1)
- Filtrado por campo de visión (FOV)
- Usa el bundle exportado en `artifacts/selected_model_bundle.joblib` si existe
- Cálculo de confianza por distancia
- Respuesta en JSON puro (sin hallucinations)

---

## Configuración Avanzada

### Modelfile — Parámetros del Modelo

Editar `Modelfile` para ajustar parámetros:

```
PARAMETER temperature 0.1      # Determinístico (bajo hallucination)
PARAMETER top_p 0.9            # Diversidad controlada
PARAMETER num_ctx 8192         # Contexto máximo
PARAMETER num_predict 512      # Longitud máxima respuesta
```

**Efectos de parámetros:**
- `temperature` ↑ → Respuestas más creativas (↑ alucinaciones)
- `temperature` ↓ → Respuestas determinísticas (seguro)
- `top_p` ↑ → Más diversidad
- `top_p` ↓ → Más conservador

### System Prompt — Instrucciones del Modelo

Editar `data/system_prompt.txt` para cambiar comportamiento del modelo.

**Regla crítica:**
```
1. NEVER invent landmarks. Use ONLY names from the provided list.
```

---

## Ejemplos de Uso

### Ejemplo 1: Explorar Barcelona

```bash
# ¿Qué hay cerca de la Sagrada Familia?
python query_model.py 41.4036 2.1744

# ¿Qué veo si miro al norte?
python query_model.py 41.4036 2.1744 0
```

### Ejemplo 2: Identificar en Madrid

```bash
# Consulta en Plaza Mayor
python query_model.py 40.4155 -3.6870 90
```

### Ejemplo 3: Búsqueda Amplia

```bash
# Búsqueda con FOV muy amplio (180°)
python query_model.py 41.38 2.17 45 180
```

---

## Troubleshooting

### Error: "Connection refused" a Ollama

**Causa:** Ollama no está ejecutándose

```bash
# Windows
# Abrir Ollama desde Applications

# macOS
brew services start ollama

# Linux
systemctl start ollama
```

### Error: Modelo "landmark-finder" no encontrado

**Causa:** Setup no completado

```bash
# Reconstruir modelo
python setup.py

# O crear manualmente
ollama create landmark-finder -f Modelfile
```

### Error: Latencia muy alta (>2s)

**Causa:** GPU saturada o contexto muy grande

**Soluciones:**
- Reducir FOV (ej: 45° en lugar de 180°)
- Reducir radio de búsqueda
- Cerrar otras aplicaciones de GPU

### Error: JSON inválido en respuesta

**Esto no debe ocurrir.** Si sucede:

```bash
# Validar que system_prompt.txt contiene las reglas
cat data/system_prompt.txt | grep "NEVER invent"

# Recrear el modelo con setup.py
python setup.py
```

---

## Monitoreo de Rendimiento

### Ver Uso de VRAM

```bash
# Windows (NVIDIA)
nvidia-smi

# Linux
nvidia-smi -l 1  # Refresco cada 1 segundo
```

**Esperado:**
- Baseline: ~2.1 GB
- Con contexto (200+ landmarks): ~4.2 GB
- Máximo observado: 5.8 GB (margen seguro con 6GB)

### Medir Latencia

```bash
# Agregar timestamp a query
import time
start = time.time()
python query_model.py 41.38 2.17
elapsed = time.time() - start
print(f"Latencia: {elapsed:.2f}s")
```

**Rangos típicos:**
- Sin azimuth: 250-350ms
- Con azimuth: 200-300ms
- Con contexto grande (>500 landmarks): 800ms-1.2s

---

## Documentación Completa

Para detalles técnicos y resultados de experimentación, ver:

**[ML_EXPERIMENTS.md](ML_EXPERIMENTS.md)**
- Definición del problema y RAG
- Dataset y preprocesamiento
- Modelos evaluados y tuning
- Exportación del modelo final
- Resultados experimentales reproducibles

---

## Próximos Pasos

- [ ] Expandir cobertura a más regiones españolas
- [ ] Fine-tuning con ejemplos reales de usuarios
- [ ] Implementar caché de respuestas frecuentes
- [ ] Soporte multiidioma
- [ ] API REST wrapper para integraciones

---

## Licencia y Créditos

**Datos:** OpenStreetMap (ODbL License)  
**Modelo:** Meta Llama 3.2 (Community License)  
**Sistema:** LandmarkLens  

---

## Soporte

Para problemas, consulta:
1. [ML_EXPERIMENTS.md](ML_EXPERIMENTS.md) — Documentación técnica
2. Logs de Ollama: `~/.ollama/logs`
3. Este README — Troubleshooting

---

