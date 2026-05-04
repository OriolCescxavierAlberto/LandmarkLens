# Documentacion de Experimentacion ML - LandmarkLens

## 1. Problema

El objetivo del sistema es identificar puntos de interes visibles desde una posicion GPS dada y, opcionalmente, una orientacion de camara. El problema se divide en dos partes:

1. Recuperacion local de candidatos cercanos al usuario.
2. Ranking de esos candidatos para seleccionar el landmark mas probable.

El sistema debe funcionar con baja latencia, evitar alucinaciones y ser reproducible con los datos del repositorio.

---

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

---

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

---

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
```

---

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
