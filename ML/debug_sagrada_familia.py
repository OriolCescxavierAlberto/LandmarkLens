#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Debug script para probar la API con Sagrada Familia"""

import json
import sys
import os

# Add parent directory to path
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from landmark_model.rag_core import (
    run_rag_query,
    load_landmarks,
    find_nearby,
    check_ollama,
    build_context,
    DEFAULT_MAX_DIST,
    DEFAULT_MAX_RESULTS,
    DEFAULT_FOV,
    fmt_dist,
)

# Coordenadas de Sagrada Familia Barcelona
LAT = 41.4036
LON = 2.1744

print("=" * 80)
print("DEBUG: Probando API con Sagrada Familia")
print("=" * 80)
print(f"\nCoordenadas: ({LAT}, {LON})")
print(f"Max Distance: {DEFAULT_MAX_DIST}m")
print(f"Max Results: {DEFAULT_MAX_RESULTS}")
print(f"FOV: {DEFAULT_FOV}deg")

# 1. Check Ollama
print("\n" + "-" * 80)
print("1. Verificando Ollama...")
print("-" * 80)
if not check_ollama():
    print("ERROR: Ollama no está disponible")
    sys.exit(1)

# 2. Load landmarks
print("\n" + "-" * 80)
print("2. Cargando landmarks...")
print("-" * 80)
try:
    index = load_landmarks()
    print(f"✓ {index.total} landmarks cargados")
except Exception as e:
    print(f"ERROR: {e}")
    sys.exit(1)

# 3. Find nearby without azimuth (what the API does initially)
print("\n" + "-" * 80)
print("3. Buscando landmarks cercanos (sin azimuth)...")
print("-" * 80)
try:
    nearby = find_nearby(LAT, LON, azimuth=None, fov=DEFAULT_FOV, 
                         max_dist=DEFAULT_MAX_DIST, max_results=DEFAULT_MAX_RESULTS)
    print(f"✓ Encontrados {len(nearby)} landmarks")
    print("\nDetalles de los landmarks encontrados:")
    for i, lm in enumerate(nearby, 1):
        print(f"\n{i}. {lm['name']}")
        print(f"   Distance: {fmt_dist(lm['distance'])}")
        print(f"   Bearing: {lm['bearing_deg']}deg ({lm.get('direction', 'N/A')})")
        print(f"   Fame Score: {lm.get('fame_score', 0)}")
        print(f"   Categories: {', '.join(lm.get('categories', []))}")
        if 'wikipedia' in lm:
            print(f"   Wikipedia: Yes")
except Exception as e:
    print(f"ERROR: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

# 4. Run the RAG query
print("\n" + "-" * 80)
print("4. Ejecutando RAG query...")
print("-" * 80)
try:
    result = run_rag_query(LAT, LON, azimuth=None, fov=DEFAULT_FOV, stream=True)
    
    print("\n" + "=" * 80)
    print("RESULTADOS DE LA CONSULTA")
    print("=" * 80)
    
    print(f"\nRAW RESPONSE:\n{result.raw_text}\n")
    
    print(f"\nVALIDATION:")
    print(f"  - JSON válido: {result.validation['is_json_valid']}")
    print(f"  - Schema OK: {result.validation['schema_ok']}")
    print(f"  - Parsed: {json.dumps(result.validation['parsed'], indent=2, ensure_ascii=False)}")
    print(f"  - Predicted names: {result.validation['predicted_names']}")
    print(f"  - All predicted in candidates: {result.validation['all_predicted_in_candidates']}")
    print(f"  - Issues: {result.validation['issues']}")
    
    # 5. Verify predictions
    print("\n" + "-" * 80)
    print("5. Verificando predicciones...")
    print("-" * 80)
    predicted = result.validation['predicted_names']
    candidate_names = {lm['name'] for lm in nearby}
    
    for pred_name in predicted:
        if pred_name in candidate_names:
            print(f"✓ '{pred_name}' está en los landmarks cercanos")
        else:
            print(f"✗ '{pred_name}' NO está en los landmarks cercanos")
            # Find closest match
            closest = None
            closest_dist = float('inf')
            for cand_name in candidate_names:
                # Simple Levenshtein-like distance
                dist = sum(1 for a, b in zip(pred_name, cand_name) if a != b) + abs(len(pred_name) - len(cand_name))
                if dist < closest_dist:
                    closest_dist = dist
                    closest = cand_name
            if closest:
                print(f"  Posible coincidencia: '{closest}'")
    
    # 6. Check if Sagrada Familia is in the nearby landmarks
    print("\n" + "-" * 80)
    print("6. Buscando 'Sagrada Familia' en los landmarks...")
    print("-" * 80)
    sagrada_familia_found = False
    for lm in nearby:
        if 'sagrada' in lm['name'].lower() and 'familia' in lm['name'].lower():
            sagrada_familia_found = True
            print(f"✓ Encontrada: {lm['name']}")
            print(f"  Distance: {fmt_dist(lm['distance'])}")
            print(f"  Distance (raw): {lm['distance']}m")
            print(f"  Bearing: {lm['bearing_deg']}deg")
            print(f"  Fame Score: {lm.get('fame_score', 0)}")
            break
    
    if not sagrada_familia_found:
        print("✗ 'Sagrada Familia' NO encontrada en los landmarks cercanos")
        print("Esto es un PROBLEMA - el landmark debería estar incluido")
        
except Exception as e:
    print(f"ERROR: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)

print("\n" + "=" * 80)
print("FIN DEL DEBUG")
print("=" * 80)
