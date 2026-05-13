#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Test without ML ranking to diagnose the issue"""

import json
import sys
import os
from pathlib import Path

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

import landmark_model.rag_core as rag_core

# Monkey-patch to disable ranking
original_score = rag_core.score_candidates_with_ranker
def no_ranking(candidates, azimuth, fov):
    # Just return candidates without ML ranking, only basic sort
    return candidates

rag_core.score_candidates_with_ranker = no_ranking

from landmark_model.rag_core import (
    run_rag_query,
    load_landmarks,
    find_nearby,
    check_ollama,
    DEFAULT_MAX_DIST,
    DEFAULT_MAX_RESULTS,
    DEFAULT_FOV,
    fmt_dist,
)

# Clear global index
rag_core._INDEX = None

LAT = 41.4036
LON = 2.1744

print("=" * 80)
print("TEST: Sin ranking de ML (diagnóstico)")
print("=" * 80)

# 1. Check Ollama
print("\n" + "-" * 80)
print("1. Verificando Ollama...")
print("-" * 80)
if not check_ollama():
    print("ERROR: Ollama no está disponible")
    sys.exit(1)

# 2. Find nearby WITHOUT ML ranking
print("\n" + "-" * 80)
print("2. Buscando landmarks cercanos (SIN ranking de ML)...")
print("-" * 80)
nearby = find_nearby(LAT, LON, azimuth=None, fov=DEFAULT_FOV, 
                     max_dist=DEFAULT_MAX_DIST, max_results=DEFAULT_MAX_RESULTS)
print(f"✓ Encontrados {len(nearby)} landmarks:\n")

sagrada_found = False
for i, lm in enumerate(nearby, 1):
    print(f"{i}. {lm['name']}")
    print(f"   Distance: {fmt_dist(lm['distance'])}")
    print(f"   Fame Score: {lm.get('fame_score', 0)}")
    if 'sagrada' in lm['name'].lower() and 'familia' in lm['name'].lower():
        sagrada_found = True
        print("   ✓ SAGRADA FAMILIA!")
    print()

if sagrada_found:
    print("\n✓ Sagrada Familia aparece sin ranking de ML")
else:
    print("\n✗ Sagrada Familia NO aparece aún sin ranking")

# Now restore and test WITH ranking
print("\n" + "=" * 80)
print("Ahora probando CON ranking de ML...")
print("=" * 80)

rag_core.score_candidates_with_ranker = original_score
rag_core._INDEX = None

nearby_with_ranking = find_nearby(LAT, LON, azimuth=None, fov=DEFAULT_FOV, 
                                  max_dist=DEFAULT_MAX_DIST, max_results=DEFAULT_MAX_RESULTS)
print(f"\nEncontrados {len(nearby_with_ranking)} landmarks:\n")

sagrada_with_ranking = False
for i, lm in enumerate(nearby_with_ranking, 1):
    print(f"{i}. {lm['name']}")
    print(f"   Distance: {fmt_dist(lm['distance'])}")
    if 'sagrada' in lm['name'].lower() and 'familia' in lm['name'].lower():
        sagrada_with_ranking = True
        print("   ✓ SAGRADA FAMILIA!")
    print()

print("\n" + "=" * 80)
print("COMPARACIÓN")
print("=" * 80)
print(f"Sin ranking: Sagrada Familia {'ENCONTRADA' if sagrada_found else 'NO ENCONTRADA'}")
print(f"Con ranking: Sagrada Familia {'ENCONTRADA' if sagrada_with_ranking else 'NO ENCONTRADA'}")

if sagrada_found and not sagrada_with_ranking:
    print("\n✗ PROBLEMA: El ranking de ML está filtrando Sagrada Familia!")
    print("  Solución: Revisar/reentrenar el modelo de ranking")
