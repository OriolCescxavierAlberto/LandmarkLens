#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Summary of fixes and verification"""

import sys
import os
import json

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

print("=" * 80)
print("RESUMEN DE FIXES REALIZADOS")
print("=" * 80)

print("\n1. PROBLEMA: Sagrada Familia no aparecía en búsquedas")
print("   SOLUCIÓN APLICADA:")
print("   - Agregadas coordenadas a 'Basílica de la Sagrada Família' (41.4036, 2.1744)")
print("   - Regenerada base de datos SQLite con nuevos datos")
print("   - Modificado algoritmo de búsqueda para considerar fame_score")
print("     (antes: ORDER BY distance_km)")
print("     (ahora: ORDER BY distance_km - (fame_score * 0.005))")

print("\n2. PROBLEMA: JSON inválido del modelo")
print("   SOLUCIÓN APLICADA:")
print("   - Mejorado SYSTEM_PROMPT para ser más explícito sobre formato JSON")
print("   - Añadidas instrucciones claras sobre comillas y structure")
print("   - Actualizado build_prompt con ejemplos y reglas")
print("   - Regenerado modelo con nuevo system prompt")

print("\n3. VERIFICACIÓN:")
print("   Consultando la base de datos directamente...")

try:
    from landmark_model.database import LandmarksDB
    db = LandmarksDB()
    
    # Check if Sagrada Familia exists
    conn = db._get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "SELECT name, lat, lon, fame_score FROM landmarks WHERE name LIKE '%Sagrada%Fam%' LIMIT 5"
    )
    results = cursor.fetchall()
    
    if results:
        print(f"   ✓ Encontradas {len(results)} entradas de Sagrada Familia:")
        for row in results:
            print(f"     - {row[0]} at ({row[1]}, {row[2]}) [fame: {row[3]}]")
    else:
        print("   ✗ Sagrada Familia not found in database")
    
    # Test find_nearby
    print("\n   Probando find_nearby en BD...")
    nearby = db.find_nearby(41.4036, 2.1744, radius_km=1.0, max_results=8)
    print(f"   ✓ find_nearby retornó {len(nearby)} resultados")
    
    sagrada_found = any('Sagrada' in r['name'] and 'Família' in r['name'] for r in nearby)
    if sagrada_found:
        print("   ✓ Sagrada Familia está en los resultados de BD")
    else:
        print("   ✗ Sagrada Familia NO está en resultados de BD")
        if nearby:
            print(f"     Primer resultado: {nearby[0]['name']}")
            
except Exception as e:
    print(f"   ✗ Error: {e}")

print("\n" + "=" * 80)
print("PRÓXIMOS PASOS:")
print("=" * 80)
print("1. Reiniciar la API: python -m landmark_model.api")
print("2. Probar con curl o Postman:")
print("   POST http://localhost:8000/api/v1/query")
print('   {"lat": 41.4036, "lon": 2.1744}')
print("3. Verificar que devuelva Sagrada Familia en los resultados")
print("4. Verificar que el JSON sea válido y tenga el esquema correcto")
