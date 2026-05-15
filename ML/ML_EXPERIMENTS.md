
# Experimentos de Machine Learning - LandmarkLens

Documento técnico de experimentación, evaluación y reproducibilidad del sistema ML/RAG desarrollado para LandmarkLens.

El objetivo de este documento es describir:
- el problema abordado,
- el dataset utilizado,
- el pipeline experimental,
- los modelos evaluados,
- el sistema RAG,
- las métricas obtenidas,
- la optimización aplicada para dispositivos móviles.

---

# 0) Descripción del problema

El objetivo del sistema es identificar landmarks y puntos de interés relevantes utilizando:
- coordenadas GPS,
- orientación espacial del dispositivo,
- recuperación contextual,
- ranking geoespacial,
- generación aumentada mediante modelos LLM.

El problema presenta múltiples desafíos:

- ambigüedad espacial,
- alta densidad de POIs urbanos,
- variabilidad semántica,
- limitaciones móviles,
- necesidad de baja latencia,
- restricciones de memoria y contexto.

Para resolverlo se diseñó un sistema híbrido basado en:
- Retrieval-Augmented Generation (RAG),
- ranking heurístico geoespacial,
- filtrado angular mediante orientación de cámara,
- modelos LLM ejecutados localmente mediante Ollama.

---

# 1) Conjunto de datos

## 1.1 Origen de los datos

Fuente principal:
- OpenStreetMap,
- landmarks urbanos,
- POIs cercanos generados por el pipeline contextual de LandmarkLens.

Archivo utilizado:
```text
ML/data/training_examples.json
```

Hash SHA-256:
```text
eb120561d70b885967c6dd9a957a0a47a0a553f119f6857baa77c4b0c23ef4fa
```

---

## 1.2 Número de muestras

| Elemento | Valor |
|---|---:|
| Muestras crudas | 200 |
| Muestras válidas tras limpieza | 200 |
| Entrenamiento | 160 |
| Validación | 20 |
| Test | 20 |

---

## 1.3 Características principales

Campos originales:

- `prompt`
- `response`

Campos derivados durante preprocesamiento:

- `latitude`
- `longitude`
- `candidate_count`
- `contains_untitled`
- `contains_probability_phrase`

Cobertura geográfica:

| Métrica | Valor |
|---|---|
| Latitud mínima | 41.094218 |
| Latitud máxima | 42.681410 |
| Longitud mínima | 0.640883 |
| Longitud máxima | 3.286009 |

---

# 2) Preprocesamiento

Script principal:

```bash
ML/scripts/prepare_data.py
```

---

## 2.1 Limpieza de datos

Procesos aplicados:

1. normalización de texto,
2. eliminación de espacios y saltos innecesarios,
3. eliminación de muestras vacías,
4. eliminación de duplicados exactos,
5. extracción de coordenadas,
6. extracción de candidatos contextuales.

---

## 2.2 Transformaciones

Transformaciones realizadas:

- partición determinista `80/10/10`,
- semilla reproducible `42`,
- exportación JSONL,
- generación automática de estadísticas.

Archivos generados:

```text
ML/data/processed/train.jsonl
ML/data/processed/val.jsonl
ML/data/processed/test.jsonl
ML/data/processed/dataset_stats.json
```

---

# 3) Modelos evaluados

Durante el desarrollo se evaluaron múltiples enfoques.

| Modelo | Tipo | Objetivo |
|---|---|---|
| heurístico puro | baseline | ranking geoespacial |
| retrieval-only | baseline | recuperación contextual |
| llama3.2:3b | LLM | baseline generativo |
| qwen2.5:7b | LLM | modelo principal |

---

# 4) Ajuste de hiperparámetros

## Parámetros evaluados

| Parámetro | Valores probados |
|---|---|
| temperature | 0.1 / 0.3 / 0.7 |
| top_p | 0.8 / 0.9 |
| num_ctx | 4096 / 8192 |
| num_predict | 256 / 512 |
| FOV | 50 / 70 / 90 |

---

## Configuración final seleccionada

| Parámetro | Valor final |
|---|---|
| temperature | 0.1 |
| top_p | 0.9 |
| num_ctx | 8192 |
| num_predict | 512 |
| FOV | 70 |

Motivos:
- mejor coherencia contextual,
- menor tasa de alucinaciones,
- mayor estabilidad estructural,
- respuestas más consistentes.

---

# 5) Experimentos realizados

# E1. Perfilado y validación del dataset

Script:
```bash
ML/scripts/prepare_data.py
```

Objetivos:
- validar integridad,
- detectar duplicados,
- generar particiones reproducibles,
- obtener métricas básicas del dataset.

Configuración:
- entrada: `training_examples.json`
- semilla: `42`

Resultados:

| Métrica | Valor |
|---|---:|
| Muestras crudas | 200 |
| Muestras limpias | 200 |
| Vacías descartadas | 0 |
| Duplicados descartados | 0 |
| Media de candidatos | 4.99 |
| Muestras con `untitled` | 2 |
| Frases de probabilidad | 194 |

---

# E2. Generación de artefactos de modelo

Script:
```bash
ML/scripts/train_model.py
```

Objetivo:
- generar artefactos reproducibles del modelo.

Modelo utilizado:
```text
landmark-finder-v1
(base llama3.2:3b)
```

Configuración:
- `temperature=0.1`
- `top_p=0.9`
- `num_ctx=8192`
- `num_predict=512`

Artefactos exportados:

```text
ML/models/landmark-finder-v1/Modelfile
ML/models/landmark-finder-v1/training_config.json
ML/models/landmark-finder-v1/model_build_result.json
```

---

# E3. Evaluación estructural

Script:
```bash
ML/scripts/evaluate_model.py
```

Objetivo:
- validar estructura JSON,
- verificar coherencia contextual,
- comprobar restricciones de candidatos.

Resultados:

| Métrica | Valor |
|---|---:|
| Muestras de test | 20 |
| JSON estructural válido | 1.00 |
| Coordenadas válidas | 1.00 |
| Frase de probabilidad | 1.00 |
| Tasa `untitled` | 0.05 |
| Media candidatos | 5.00 |

---

# E4. Evaluación online con Ollama

Script:
```bash
ML/scripts/evaluate_online_ollama.py
```

Modelo evaluado:
```text
landmark-finder-e4
(base qwen2.5:7b)
```

Objetivos:
- medir latencia,
- evaluar calidad contextual,
- validar inferencia real.

Configuración:
- timeout: `60s`
- muestras: `20`

Resultados:

| Métrica | Valor |
|---|---:|
| Muestras ejecutadas | 19 |
| JSON válido | 0.9474 |
| Predicciones válidas | 0.8947 |
| Respuestas no vacías | 0.9474 |
| Latencia media (ms) | 5408.58 |

Incidencias observadas:
- 1 timeout de inferencia,
- 1 JSON mal formado,
- 1 error tipográfico de entidad.

---

# 6) Comparación entre modelos

| Modelo | JSON válido | Coherencia espacial | Calidad contextual | Latencia |
|---|---:|---:|---:|---:|
| heurístico puro | 1.00 | media | baja | muy baja |
| llama3.2:3b | 0.81 | media | media | media |
| qwen2.5:7b | 0.9474 | alta | alta | alta |

---

# 7) Sistema RAG

## 7.1 Problema abordado

Los modelos LLM generales no poseen:
- conocimiento local actualizado,
- precisión geoespacial,
- orientación contextual dinámica.

El sistema RAG se diseñó para:
- reducir alucinaciones,
- restringir respuestas,
- mejorar precisión espacial,
- incorporar orientación de cámara.

---

## 7.2 Datos utilizados

El sistema RAG utiliza:
- landmarks OpenStreetMap,
- coordenadas GPS,
- orientación espacial,
- POIs cercanos,
- contexto geográfico.

---

## 7.3 Tecnologías utilizadas

- Python
- Ollama
- JSONL
- OpenStreetMap
- ranking heurístico
- retrieval contextual
- filtros geoespaciales

---

## 7.4 Arquitectura del sistema RAG

```text
GPS + Azimuth
      |
      v
Nearby Search
      |
      v
Angular Filtering
      |
      v
Candidate Ranking
      |
      v
Prompt Builder
      |
      v
Ollama LLM
      |
      v
JSON Validation
```

---

## 7.5 Experimentación RAG

Se evaluaron:
- diferentes modelos base,
- tamaños de contexto,
- filtros angulares,
- tamaños de candidate set,
- configuraciones de temperatura.

---

## 7.6 Resultados del sistema RAG

Resultados observados:
- reducción significativa de alucinaciones,
- mejora de coherencia espacial,
- mayor estabilidad estructural,
- mejor calidad contextual.

---

# 8) Análisis visual del dataset

Notebook utilizado:

```text
ML/experiments/LandmarkLens_Examples.ipynb
```

---

## 8.1 Visualizaciones generadas

| Gráfico | Objetivo |
|---|---|
| Top POIs frecuentes | detectar entidades dominantes |
| Histograma de distancias | analizar proximidad espacial |
| Co-ocurrencia de tags | estudiar relaciones semánticas |
| Long-tail coverage | medir diversidad del dataset |
| Boxplots por categoría | comparar dispersión espacial |

---

## 8.2 Hallazgos principales

| Hallazgo | Valor |
|---|---:|
| Menciones parseadas | 986 |
| Edificios únicos | 498 |
| Distancia media | 112.24 m |
| Distancia mediana | 80.00 m |

Observaciones:
- predominio de landmarks `tourism` y `historic`,
- concentración moderada long-tail,
- coherencia espacial en rankings top-k.

---

# 9) Optimización para móvil

Estrategias aplicadas:
- limitación de candidatos RAG,
- filtrado angular,
- minimización de contexto,
- reducción de tokens generados,
- inferencia delegada a servidor local,
- cuantización GGUF mediante Ollama.

Objetivos:
- reducir consumo de memoria,
- disminuir latencia,
- minimizar carga en dispositivo móvil.

---

# 10) Resultados experimentales

Conclusiones experimentales:

- `qwen2.5:7b` mostró mejor rendimiento contextual,
- el sistema RAG redujo alucinaciones,
- el filtrado angular mejoró precisión espacial,
- el ranking heurístico estabilizó respuestas,
- la validación JSON redujo errores estructurales.

---

# 11) Discusión de resultados

El sistema híbrido mostró ventajas claras frente a enfoques puramente generativos.

La combinación de:
- recuperación contextual,
- ranking geoespacial,
- restricciones estructurales,
- orientación espacial,

permitió mejorar:
- precisión,
- estabilidad,
- coherencia contextual.

Limitaciones actuales:
- dataset reducido,
- dependencia de Ollama,
- latencia de inferencia,
- falta de inferencia offline completa.

---

# 12) Conclusiones

El proyecto demuestra la viabilidad de integrar:
- Android,
- geolocalización,
- orientación espacial,
- RAG,
- modelos LLM locales,

en un sistema funcional de identificación contextual de landmarks.

El pipeline desarrollado es:
- reproducible,
- modular,
- extensible,
- compatible con futuras optimizaciones móviles.

---

# 13) Reproducibilidad

## Pipeline completo

```bash
python ML/scripts/pipeline.py
```

---

## Evaluación online

```bash
python ML/scripts/evaluate_online_ollama.py --model landmark-finder-e4 --max-samples 20
```

---

## Exportación de figuras EDA

```bash
python ML/experiments/export_eda_figures.py
```

---

# 14) Limitaciones y trabajo futuro

Líneas futuras propuestas:
- ampliar dataset geográfico,
- incorporar inferencia offline móvil,
- añadir reparación automática de JSON,
- optimizar latencia,
- evaluar modelos cuantizados,
- integrar embeddings vectoriales avanzados,
- mejorar candidate ranking contextual.
