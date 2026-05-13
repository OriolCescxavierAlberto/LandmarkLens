# Informe de Debugging y Fixes - LandmarkLens API

## Problemas Identificados

### 1. **Sagrada Familia No Aparecía en Búsquedas**

**Síntoma:** Cuando se consultaba la API con coordenadas de Sagrada Familia (41.4036, 2.1744), el modelo devolvía torres individuales en lugar del monumento principal.

**Causa Raíz:** 
- El landmark "Basílica de la Sagrada Família" existía en el dataset pero **NO TENÍA COORDENADAS** (mark como `needs_geocoding: true`)
- El sistema solo encontraba las partes individuales del monumento (torres, fachadas, museo) que SÍ tenían coordenadas
- El algoritmo de búsqueda ordenaba **SOLO por distancia**, sin considerar la "fama" del landmark

**Soluciones Aplicadas:**

1. **Agregadas coordenadas a Basílica:**
   - Archivo: `landmark_model/data/landmarks.json`
   - Cambio: Agregadas `lat: 41.4036, lon: 2.1744` a "Basílica de la Sagrada Família"
   - Removido: Campo `"needs_geocoding": true`

2. **Mejorado algoritmo de búsqueda en base de datos:**
   - Archivo: `landmark_model/database.py`
   - Antes: `ORDER BY distance_km ASC`
   - Ahora: `ORDER BY distance_km - (fame_score * 0.005) ASC`
   - Esto prioriza landmarks famosos incluso si están un poco más lejos

3. **Regenerada base de datos:**
   - Ejecutado: `python landmark_model/migrate_to_db.py`
   - Resultado: 50,521 landmarks cargados correctamente

---

### 2. **JSON Inválido del Modelo**

**Síntoma:** El modelo respondía con JSON mal formado:
```json
{"landmarks":[{"name":"Torre de Jesús",distance:4,magnitude":"high"},..."]}
```

**Problemas específicos:**
- Faltaban comillas en `distance:4` (debería ser `"distance": 4`)
- Usaba `magnitude` en lugar de `confidence`
- Envolvía el array en un objeto `{"landmarks": [...]}`

**Causa:** El system prompt era poco específico y el modelo no seguía las instrucciones correctamente.

**Soluciones Aplicadas:**

1. **Mejorado SYSTEM_PROMPT:**
   - Archivo: `landmark_model/rag_core.py`
   - Cambios:
     - Agregadas instrucciones explícitas sobre comillas: `"name": "value"` vs `name: value`
     - Aclarado que todas las claves JSON deben tener comillas
     - Añadido ejemplo correcto de respuesta
     - Especificado que NO se debe wrappear el array en un objeto
     - Clarificadas las diferencias entre respuesta con/sin azimuth

2. **Mejorado build_prompt:**
   - Cambios: Añadidas instrucciones más claras y ejemplos en la estructura
   - Enfatizado: "with proper quotes on all keys"

3. **Regenerado modelo Ollama:**
   - Ejecutado: `python landmark_model/generate_knowledge.py`
   - Creado nuevo modelo: `ollama create landmark-finder -f Modelfile`

---

## Cambios Realizados en Archivos

### 1. `landmark_model/data/landmarks.json`
```python
# Agregadas coordenadas a:
{
    "name": "Basílica de la Sagrada Família",
    "lat": 41.4036,      # ✓ NUEVO
    "lon": 2.1744,       # ✓ NUEVO
    "osm_type": "relation",
    "osm_id": 9194723,
    "categories": [...],
    "fame_score": 12,
    "wikipedia": "ca:Temple Expiatori de la Sagrada Família",
    "wikidata": "Q48435",
    "architect": "Antoni Gaudí i Cornet",
    "region": "Cataluña"
    # "needs_geocoding": true  # ✓ REMOVIDO
}
```

### 2. `landmark_model/database.py`
```sql
-- Antes:
ORDER BY distance_km ASC

-- Ahora:
ORDER BY distance_km - (fame_score * 0.005) ASC
```

### 3. `landmark_model/rag_core.py`
- **SYSTEM_PROMPT mejorado** con instrucciones muy explícitas sobre JSON
- **build_prompt mejorado** con mejor guidance

---

## Verificación

✓ **Sagrada Familia aparece en búsquedas**
- Posición: #1 en resultados (distance: 0m)
- Fame score: 12

✓ **JSON válido**
- Respuestas bien formadas con comillas correctas
- Schema validado correctamente
- No hay errores de parsing

✓ **API funcionando**
- Health check: OK
- Query sin azimuth: OK (devuelve array)
- Query con azimuth: OK (devuelve object con target)

---

## Cómo Probar

```bash
# 1. Iniciar API
python -m landmark_model.api

# 2. En otra terminal, consultar Sagrada Familia:
curl -X POST http://localhost:8000/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"lat": 41.4036, "lon": 2.1744}'

# 3. Debería devolver: Basílica de la Sagrada Família en los primeros resultados
```

---

## Resumen

| Problema | Causa | Solución | Estado |
|----------|-------|----------|--------|
| Sagrada Familia no aparece | Sin coordenadas | Agregadas coords + mejorado sorting | ✓ FIJO |
| JSON inválido | Prompt débil | Mejorado system prompt | ✓ FIJO |
| Modelo confundido | Instrucciones vagas | Regenerado con nuevo prompt | ✓ FIJO |

