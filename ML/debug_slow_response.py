#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Debug modelo lento o sin respuesta"""

import sys
import os
import time

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

import landmark_model.rag_core as rag_core
rag_core._INDEX = None
rag_core._DB = None

from landmark_model.rag_core import (
    run_rag_query,
    check_ollama,
    query_ollama,
    DEFAULT_MAX_DIST,
    DEFAULT_MAX_RESULTS,
    DEFAULT_FOV,
    find_nearby,
    build_prompt,
)

LAT = 41.3986383
LON = 2.1852483

print("=" * 80)
print("DEBUG: Modelo Lento/Sin Respuesta")
print("=" * 80)
print(f"\nCoordinates: ({LAT}, {LON})")

# 1. Check Ollama
print("\n" + "-" * 80)
print("1. Verificando Ollama...")
print("-" * 80)
start = time.time()
if not check_ollama():
    print("ERROR: Ollama not available")
    sys.exit(1)
elapsed = time.time() - start
print(f"✓ Ollama OK ({elapsed:.2f}s)")

# 2. Find nearby
print("\n" + "-" * 80)
print("2. Buscando landmarks cercanos...")
print("-" * 80)
start = time.time()
nearby = find_nearby(LAT, LON, azimuth=None, fov=DEFAULT_FOV, 
                     max_dist=DEFAULT_MAX_DIST, max_results=DEFAULT_MAX_RESULTS)
elapsed = time.time() - start
print(f"✓ Encontrados {len(nearby)} landmarks ({elapsed:.2f}s)")
for i, lm in enumerate(nearby[:3], 1):
    print(f"  {i}. {lm['name']} - {lm['distance']:.0f}m")

# 3. Build prompt
print("\n" + "-" * 80)
print("3. Construyendo prompt...")
print("-" * 80)
prompt = build_prompt(LAT, LON, nearby, azimuth=None, fov=DEFAULT_FOV)
print(f"Prompt length: {len(prompt)} chars")
print(f"\nFirst 300 chars:\n{prompt[:300]}\n...")

# 4. Query Ollama DIRECTLY
print("\n" + "-" * 80)
print("4. Consultando modelo directamente (timeout 30s)...")
print("-" * 80)
print("Enviando request al modelo...")
start = time.time()
try:
    response = query_ollama(prompt, stream=True, timeout=30, model_name="landmark-finder")
    elapsed = time.time() - start
    print(f"\n✓ Modelo respondió en {elapsed:.2f}s")
    print(f"\nRespuesta (primeros 500 chars):\n{response[:500]}")
except Exception as e:
    elapsed = time.time() - start
    print(f"\n✗ Error después de {elapsed:.2f}s: {e}")
    import traceback
    traceback.print_exc()

# 5. Full query
print("\n" + "-" * 80)
print("5. Query completo con RAG...")
print("-" * 80)
print("Ejecutando run_rag_query (non-streaming)...")
start = time.time()
try:
    result = run_rag_query(LAT, LON, azimuth=None, fov=DEFAULT_FOV, stream=False)
    elapsed = time.time() - start
    print(f"✓ Completado en {elapsed:.2f}s")
    print(f"  JSON valid: {result.validation['is_json_valid']}")
    print(f"  Schema OK: {result.validation['schema_ok']}")
except Exception as e:
    elapsed = time.time() - start
    print(f"✗ Error: {e}")
    import traceback
    traceback.print_exc()
