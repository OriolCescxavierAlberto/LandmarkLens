#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Test API again after fixes"""

import json
import sys
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

# Force reload of rag_core to get updated data
import importlib
import landmark_model.rag_core as rag_core
importlib.reload(rag_core)

from landmark_model.rag_core import (
    run_rag_query,
    load_landmarks,
    find_nearby,
    check_ollama,
    DEFAULT_MAX_DIST,
    DEFAULT_MAX_RESULTS,
    DEFAULT_FOV,
    fmt_dist,
    _INDEX,  # Clear the global index
)

# Clear global index
import landmark_model.rag_core
landmark_model.rag_core._INDEX = None

# Coordenadas de Sagrada Familia Barcelona
LAT = 41.4036
LON = 2.1744

print("=" * 80)
print("TEST: API después de fixes")
print("=" * 80)
print(f"\nCoordenadas: ({LAT}, {LON})")

# 1. Check Ollama
print("\n" + "-" * 80)
print("1. Verificando Ollama...")
print("-" * 80)
if not check_ollama():
    print("ERROR: Ollama no está disponible")
    sys.exit(1)

# 2. Find nearby with updated data
print("\n" + "-" * 80)
print("2. Buscando landmarks cercanos...")
print("-" * 80)
nearby = find_nearby(LAT, LON, azimuth=None, fov=DEFAULT_FOV, 
                     max_dist=DEFAULT_MAX_DIST, max_results=DEFAULT_MAX_RESULTS)
print(f"✓ Encontrados {len(nearby)} landmarks:\n")

# Check if Sagrada Familia is there
sagrada_found = False
for i, lm in enumerate(nearby, 1):
    print(f"{i}. {lm['name']}")
    print(f"   Distance: {fmt_dist(lm['distance'])}")
    if 'sagrada' in lm['name'].lower() and 'familia' in lm['name'].lower():
        sagrada_found = True
        print("   ✓ SAGRADA FAMILIA ENCONTRADA!")
    print()

if not sagrada_found:
    print("✗ WARNING: Sagrada Familia aún no aparece en top 8")
else:
    print("✓ SUCCESS: Sagrada Familia está en los resultados")

# 3. Run RAG query
print("\n" + "-" * 80)
print("3. Ejecutando RAG query...")
print("-" * 80)
result = run_rag_query(LAT, LON, azimuth=None, fov=DEFAULT_FOV, stream=False)

print("\n" + "=" * 80)
print("RESULTADOS")
print("=" * 80)

print(f"\nMODEL RAW RESPONSE:\n{result.raw_text}\n")

print(f"\nVALIDATION:")
print(f"  JSON válido: {result.validation['is_json_valid']}")
print(f"  Schema OK: {result.validation['schema_ok']}")
print(f"  Predicted names: {result.validation['predicted_names'][:3]}..." if len(result.validation['predicted_names']) > 3 else f"  Predicted names: {result.validation['predicted_names']}")
print(f"  All in candidates: {result.validation['all_predicted_in_candidates']}")
print(f"  Issues: {result.validation['issues']}")

if result.validation['is_json_valid'] and result.validation['schema_ok']:
    print("\n✓ JSON VÁLIDO Y SCHEMA CORRECTO!")
else:
    print("\n✗ JSON O SCHEMA INVÁLIDO")
    if result.validation['parsed']:
        print(f"  Parsed (parsed content): {json.dumps(result.validation['parsed'], indent=2, ensure_ascii=False)}")
