<<<<<<< HEAD
# Documentacion de Experimentacion ML - LandmarkLens

## 1. Problema

El objetivo del sistema es identificar puntos de interes visibles desde una posicion GPS dada y, opcionalmente, una orientacion de camara. El problema se divide en dos partes:

1. Recuperacion local de candidatos cercanos al usuario.
2. Ranking de esos candidatos para seleccionar el landmark mas probable.

El sistema debe funcionar con baja latencia, evitar alucinaciones y ser reproducible con los datos del repositorio.
=======
# Experimentos de ML - LandmarkLens

Este documento resume la experimentación de Machine Learning realizada con el conjunto de datos JSON actual del repositorio, con foco en reproducibilidad, trazabilidad y lectura clara.

## 1) Conjunto de datos
>>>>>>> 4a903c46492e74a8c95bf378c17b2dde2e9d247d

### 1.1 Origen de los datos

<<<<<<< HEAD
## 2. Dataset utilizado

La fuente principal es OpenStreetMap en formato `.osm.pbf` para las regiones incluidas en el repositorio:

- Cataluña
- Madrid
- Valencia
- Pais Vasco

Los archivos se convierten a `data/landmarks_*.json` y luego se fusionan en `data/landmarks.json`.

### Contenido del dataset

Cada landmark contiene, cuando existe:

- nombre
- latitud y longitud
- `fame_score`
- categorias OSM
- descripcion
- Wikipedia / Wikidata
- arquitecto, ano, estilo y direccion

### Datos generados para entrenamiento

El script `train_models.py` crea ejemplos sinteticos de ranking a partir de `data/landmarks.json`:

- se seleccionan landmarks reales con coordenadas
- se generan queries sinteticas alrededor del landmark objetivo
- se construyen candidatos cercanos con el indice espacial
- se etiquetan como positivos o negativos segun si el candidato es el objetivo

La corrida de validacion reproducible incluida en este repositorio genero:

- 20 landmarks objetivo
- 40 queries sinteticas
- 94 filas de entrenamiento/evaluacion
=======
- Fuente principal: landmarks de OpenStreetMap y contexto de POIs cercanos generado por el pipeline de LandmarkLens.
- Archivo utilizado: `ML/data/training_examples.json`.
- Hash de trazabilidad (SHA-256): `eb120561d70b885967c6dd9a957a0a47a0a553f119f6857baa77c4b0c23ef4fa`.

### 1.2 Número de muestras

| Elemento | Valor |
|---|---:|
| Muestras crudas | 200 |
| Muestras válidas tras limpieza | 200 |
| Entrenamiento | 160 |
| Validación | 20 |
| Test | 20 |
>>>>>>> 4a903c46492e74a8c95bf378c17b2dde2e9d247d

### 1.3 Características principales

<<<<<<< HEAD
## 3. Preprocesamiento

El preprocesamiento se realiza en dos niveles.

### 3.1 Extraccion geoespacial

`extract_landmarks.py` filtra nodos y ways OSM relevantes usando tags como `historic`, `tourism`, `amenity`, `building`, `leisure` y `man_made`.

### 3.2 Feature engineering para ranking

Cada candidato se transforma en un vector con estas variables:

- distancia en metros y kilometros
- seno y coseno del bearing
- desviacion angular respecto al centro de la camara
- flag de presencia de azimuth
- FOV usado en la query
- `fame_score`
- numero de categorias
- banderas de descripcion, Wikipedia y Wikidata

Estas features son ligeras y faciles de recomputar en la app.
=======
Campos base por muestra:

- `prompt`: consulta en lenguaje natural con coordenadas GPS.
- `response`: respuesta esperada con landmarks ordenados por relevancia.

Campos derivados en preprocesamiento:

- `latitude`, `longitude`.
- `candidate_count`.
- `contains_untitled`.
- `contains_probability_phrase`.
>>>>>>> 4a903c46492e74a8c95bf378c17b2dde2e9d247d

Cobertura geográfica detectada:

<<<<<<< HEAD
## 4. Modelos evaluados

Se evaluaron tres enfoques:

1. `heuristic_ranker`: baseline manual basado en distancia, fama y angulo.
2. `linear_logistic`: ranker lineal entrenado con perdida logistica.
3. `linear_hinge`: ranker lineal entrenado con perdida hinge.

El modelo final seleccionado fue `linear_hinge`.

---

## 5. Proceso de entrenamiento

El entrenamiento se ejecuta con `train_models.py`.

### Flujo

1. Carga `data/landmarks.json`.
2. Genera queries sinteticas y candidatos locales.
3. Divide los ejemplos por `query_id` para evitar fuga de informacion.
4. Ajusta hiperparametros por modelo.
5. Compara resultados en validacion y test.
6. Reentrena el mejor modelo con train + validation.
7. Exporta el bundle final a `artifacts/selected_model_bundle.joblib`.

### Hiperparametros ajustados

- `heuristic_ranker`:
  - `distance_weight`
  - `fame_weight`
  - `angle_weight`
  - `azimuth_weight`

- `linear_logistic` y `linear_hinge`:
  - `learning_rate`
  - `epochs`
  - `l2`

---

## 6. Metricas utilizadas

Las metricas calculadas son:

- precision
- recall
- F1
- accuracy
- average precision
- top-1 por query

La metrica mas importante para el caso de uso es `query_top1`, porque mide si el primer candidato devuelto es el landmark correcto.

---

## 7. Comparacion entre modelos

Resultados de la corrida reproducible registrada en `artifacts/experiment_summary.json`:

| Modelo | Validation top-1 | Validation F1 | Test top-1 | Test F1 |
|---|---:|---:|---:|---:|
| heuristic_ranker | 0.875 | 0.500 | 0.750 | 0.200 |
| linear_logistic | 0.875 | 0.556 | 0.625 | 0.462 |
| linear_hinge | 0.875 | 0.556 | 0.750 | 0.364 |

### Modelo final seleccionado

- Modelo: `linear_hinge`
- Parametros: `learning_rate=0.01`, `epochs=8`, `l2=0.0`
- Motivo: empata en validation top-1 con el resto y obtiene el mejor balance para el ranking final exportado.

---

## 8. Exportacion del modelo para la aplicacion

El modelo final se exporta como un bundle serializado en:

- `landmark_model/artifacts/selected_model_bundle.joblib`

Contenido del bundle:

- el ranker seleccionado
- sus hiperparametros
- lista de features usadas

La aplicacion consulta este bundle desde `query_model.py` para reordenar los candidatos locales antes de construir el prompt para Ollama.

---

## 9. Optimizacion para movilidad

La optimizacion para uso en movil se basa en tres decisiones:

1. El ranking final es un modelo lineal compacto, sin dependencias pesadas de inferencia.
2. El coste de calcular features es bajo y solo se aplica a candidatos cercanos.
3. El LLM se usa solo como capa de generacion de JSON, mientras que la seleccion geografica se resuelve localmente.

Esto reduce memoria, latencia y complejidad de despliegue.

---

## 10. RAG para la aplicacion

### Problema y abordaje

El problema RAG consiste en evitar que el modelo invente landmarks y, al mismo tiempo, darle suficiente contexto espacial para responder con precision.

El abordaje actual es:

1. recuperar landmarks cercanos con un indice espacial
2. rankearlos con el modelo exportado
3. construir un contexto compacto
4. enviar ese contexto a Ollama para producir la respuesta JSON final

### Datos usados o generados en el RAG

- `data/landmarks.json` como base de conocimiento
- `data/system_prompt.txt` como instruccion estricta
- `data/training_examples.json` como ejemplos sinteticos de contexto/respuesta
- `artifacts/selected_model_bundle.joblib` como ranker local

### Estructura de carpetas

- `landmark_model/`
  - `extract_landmarks.py`
  - `generate_knowledge.py`
  - `train_models.py`
  - `query_model.py`
  - `setup.py`
  - `Modelfile`
- `landmark_model/data/`
  - `landmarks.json`
  - `landmarks_*.json`
  - `training_examples.json`
  - `system_prompt.txt`
  - `experiment_results.json`
- `landmark_model/artifacts/`
  - `selected_model_bundle.joblib`
  - `model_comparison.csv`
  - `experiment_summary.json`

### Tecnologia usada

- Python
- OpenStreetMap / osmium
- Ollama
- modelo base Llama 3.2 3B
- serializacion con `pickle`

### Experimentacion RAG

La experimentacion del RAG consiste en verificar que:

- el indice espacial devuelva candidatos cercanos
- el ranking local ordene correctamente el contexto
- el prompt final use nombres completos y validos
- la respuesta de Ollama sea JSON valido y sin inventar landmarks

### Comparacion entre modelos usando el RAG

En esta version del proyecto, la comparacion relevante es la del ranker local que alimenta el RAG. Los tres enfoques evaluados fueron:

- baseline heuristico
- ranker lineal logistico
- ranker lineal hinge

### Resultados experimentales

La corrida de validacion generada por el repositorio produce:

- 0 queries perdidas en la muestra pequena validada
- 0.875 de top-1 en validacion para los tres enfoques
- mejor generalizacion test para `linear_hinge` y `heuristic_ranker` en top-1

### Discusion de resultados

- El baseline heuristico es fuerte porque la distancia domina el problema.
- El modelo lineal agrega una decision mas flexible para escenarios densos o ambiguos.
- `linear_hinge` quedo seleccionado por su balance entre simplicidad, rendimiento y portabilidad.

---

## 11. Reproducibilidad

Para reproducir el flujo completo:

```bash
python extract_landmarks.py
python generate_knowledge.py
python train_models.py
python setup.py
```

Para probar una consulta:

```bash
python query_model.py 41.4036 2.1744
python query_model.py 41.4036 2.1744 0
=======
- Latitud: `41.094218` a `42.681410`
- Longitud: `0.640883` a `3.286009`

## 2) Preprocesamiento

Script: `ML/scripts/prepare_data.py`

### 2.1 Limpieza

1. Normalización de texto (saltos de línea y espacios finales).
2. Eliminación de filas con prompt/respuesta vacíos.
3. Eliminación de duplicados exactos por `(prompt, response)`.
4. Extracción de coordenadas y número de candidatos.

### 2.2 Transformaciones

- Partición determinista `80/10/10` con semilla `42`.
- Exportación a JSONL:
  - `ML/data/processed/train.jsonl`
  - `ML/data/processed/val.jsonl`
  - `ML/data/processed/test.jsonl`
- Estadísticas en:
  - `ML/data/processed/dataset_stats.json`

## 3) Experimentos

### E1. Perfilado del conjunto de datos y línea base de calidad

- Script: `ML/scripts/prepare_data.py`
- Objetivo: validar integridad y generar particiones reproducibles.
- Configuración:
  - entrada: `ML/data/training_examples.json`
  - salida: `ML/data/processed`
  - semilla: `42`

| Métrica | Valor |
|---|---:|
| Muestras crudas | 200 |
| Muestras limpias | 200 |
| Vacías descartadas | 0 |
| Duplicados descartados | 0 |
| Media de candidatos por muestra | 4.99 |
| Muestras con `untitled` | 2 |
| Muestras con frase de probabilidad | 194 |

### E2. Generación de artefactos de modelo

- Script: `ML/scripts/train_model.py`
- Objetivo: generar el primer artefacto de modelo y su configuración de entrenamiento.
- Configuración:
  - modelo base: `llama3.2:3b`
  - nombre del modelo: `landmark-finder-v1`
  - parámetros de inferencia:
    - `temperature=0.1`
    - `top_p=0.9`
    - `num_ctx=8192`
    - `num_predict=512`

Artefactos generados:

- `ML/models/landmark-finder-v1/Modelfile`
- `ML/models/landmark-finder-v1/training_config.json`
- `ML/models/landmark-finder-v1/model_build_result.json`

### E3. Evaluación estructural en test

- Script: `ML/scripts/evaluate_model.py`
- Objetivo: verificar calidad estructural en la partición de test.
- Configuración:
  - entrada: `ML/data/processed/test.jsonl`
  - salida: `ML/experiments/evaluation_report.json`

| Métrica | Valor |
|---|---:|
| Muestras de test | 20 |
| Tasa de muestras con 5 candidatos | 1.00 |
| Tasa de muestras con coordenadas | 1.00 |
| Tasa de frase de probabilidad | 1.00 |
| Tasa de `untitled` | 0.05 |
| Media de candidatos | 5.00 |

### E4. Evaluación de inferencia en línea con Ollama

- Script: `ML/scripts/evaluate_online_ollama.py`
- Objetivo: medir calidad de salida y latencia en inferencia real.
- Configuración:
  - modelo: `landmark-finder-e4` (base `qwen2.5:7b`)
  - entrada: `ML/data/processed/test.jsonl`
  - muestras solicitadas: `20`
  - timeout por muestra: `60s`
  - salida: `ML/experiments/online_eval_report.json`

| Métrica | Valor |
|---|---:|
| Muestras solicitadas | 20 |
| Muestras ejecutadas correctamente | 19 |
| Tasa de JSON válido | 0.9474 |
| Tasa de predicciones dentro de candidatos | 0.8947 |
| Tasa de predicciones no vacías | 0.9474 |
| Latencia media (ms) | 5408.58 |

Incidencias observadas:

- 1 muestra superó el timeout de 60 segundos.
- 1 salida JSON incluyó un campo numérico mal formado (`distance:79` sin comillas).
- 1 respuesta incluyó un error tipográfico en nombre de entidad.

## 4) Comparación de resultados

| Métrica | E1 (conjunto de datos limpio completo) | E3 (test) | E4 (inferencia en línea) |
|---|---:|---:|---:|
| Media de candidatos por muestra | 4.99 | 5.00 | 5.00 (candidatos de entrada) |
| Tasa de frase de probabilidad | 0.97 | 1.00 | no aplica |
| Tasa de `untitled` | 0.01 | 0.05 | 0.00 en predicciones |
| Tasa de JSON válido | no aplica | no aplica | 0.9474 |
| Tasa de predicciones restringidas a candidatos | no aplica | no aplica | 0.8947 |
| Latencia media (ms) | no aplica | no aplica | 5408.58 |

## 5) Análisis visual del conjunto de datos crudo

El análisis visual está en `ML/experiments/LandmarkLens_Examples.ipynb` (secciones 15 y 16) sobre `ML/data/training_examples.json`.

### 5.1 Gráficos incluidos

| Gráfico | Qué aporta |
|---|---|
| Top 15 edificios/POIs por frecuencia | Detecta entidades dominantes y repetición. |
| Agrupación por familia de tags | Mide composición temática (`tourism`, `historic`, etc.). |
| Histograma de distancias | Describe el rango de proximidad de landmarks. |
| Completitud de atributos (%) | Evalúa disponibilidad de metadatos por mención. |
| Distribución de direcciones | Valida balance direccional para escenarios con azimut. |
| Distancia mediana por posición (top-k) | Comprueba coherencia de ranking por cercanía. |
| Co-ocurrencia de tags | Detecta solapamientos semánticos entre categorías. |
| Boxplots de distancia por tag | Compara dispersión y mediana por familia de tag. |
| Curva long-tail acumulada | Mide concentración de menciones vs diversidad. |

### 5.2 Hallazgos clave

| Hallazgo | Valor |
|---|---:|
| Muestras crudas | 200 |
| Menciones parseadas | 986 |
| Edificios únicos | 498 |
| Media de menciones por muestra | 4.93 |
| Distancia media (m) | 112.24 |
| Distancia mediana (m) | 80.00 |

Agregación por familia de tag:

| Familia de tag | Menciones |
|---|---:|
| `tourism` | 512 |
| `historic` | 311 |
| `building` | 266 |
| `amenity` | 150 |
| `leisure` | 90 |
| `man_made` | 33 |

Concentración long-tail:

- El 50% de las menciones se cubre con 169 edificios.
- Eso representa el 33.94% del total de edificios únicos (498).

Interpretación resumida:

- El conjunto de datos presenta concentración moderada: existe una cabeza relevante, pero conserva cobertura en cola.
- `tourism` y `historic` dominan el perfil semántico.
- La distancia mediana aumenta con la posición del ranking, coherente con ordenación por cercanía.

### 5.3 Galería de figuras (PNG exportados)

Las imágenes se exportan en `ML/experiments/figures/` y pueden usarse directamente en informes.

#### Vista general de datos crudos

![Resumen básico EDA](experiments/figures/eda_basic_overview.png)

#### Co-ocurrencia y dispersión de distancias

![Co-ocurrencia y boxplot avanzados](experiments/figures/eda_advanced_cooccurrence_boxplot.png)

#### Curva de concentración long-tail

![Cobertura acumulada long-tail](experiments/figures/eda_long_tail_coverage.png)

## 6) Reproducibilidad

Pipeline completo:

```bash
python ML/scripts/pipeline.py
>>>>>>> 4a903c46492e74a8c95bf378c17b2dde2e9d247d
```

Evaluación en línea con Ollama:

<<<<<<< HEAD
## 12. Conclusiones

El proyecto ya cubre el flujo final esperado:

- extraccion y fusion de landmarks
- generacion de ejemplos sinteticos
- entrenamiento de varios modelos
- ajuste de hiperparametros
- exportacion del modelo final
- uso del modelo exportado en la aplicacion
- RAG local con contexto compacto y respuesta JSON

El resultado es un sistema reproducible, ligero y orientado a despliegue en una aplicacion movil o de baja memoria.
=======
```bash
python ML/scripts/evaluate_online_ollama.py --model landmark-finder-e4 --max-samples 20
```

Exportación de figuras EDA:

```bash
python ML/experiments/export_eda_figures.py
```

## 7) Limitaciones y próximos pasos

- Ampliar el conjunto de datos con más regiones y casos límite.
- Corregir automáticamente respuestas con `untitled` en curación de datos.
- Añadir reintentos y reparación de JSON en evaluación en línea.
- Mantener evaluación periódica sobre modelos alternativos de Ollama.
>>>>>>> 4a903c46492e74a8c95bf378c17b2dde2e9d247d
