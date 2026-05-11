#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""RAG local + Ollama + camera orientation query helper."""

from __future__ import annotations

import os
import sys


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PARENT_DIR = os.path.dirname(SCRIPT_DIR)
if PARENT_DIR not in sys.path:
    sys.path.insert(0, PARENT_DIR)

from landmark_model.rag_core import (  # noqa: E402
    DEFAULT_FOV,
    MODEL_BUNDLE_PATH,
    MODEL_NAME,
    OLLAMA_URL,
    RAG_MANIFEST_PATH,
    build_context,
    build_modelfile,
    build_prompt,
    build_rag_manifest,
    candidate_feature_rows,
    check_ollama,
    direction,
    extract_json,
    find_nearby,
    fmt_dist,
    haversine,
    load_landmarks,
    load_ranker_bundle,
    predicted_names,
    query_ollama,
    run_rag_query,
    score_candidates_with_ranker,
    validate_response,
    angle_diff,
    bearing,
)


def query(lat, lon, azimuth=None, fov=DEFAULT_FOV, stream=True, model_name=MODEL_NAME):
    result = run_rag_query(lat, lon, azimuth=azimuth, fov=fov, stream=stream, model_name=model_name)
    return result.raw_text


def _validate_response(raw):
    validation = validate_response(raw)
    if validation.get("schema_ok"):
        return validation.get("parsed")
    return validation.get("parsed") if validation.get("is_json_valid") else None


def interactive():
    print("=" * 60)
    print("LANDMARK FINDER - RAG + brujula")
    print("=" * 60)
    print("Formatos:")
    print("  lat, lon                -> busca en todas direcciones")
    print("  lat, lon, azimuth       -> filtra por direccion camara")
    print("  lat, lon, azimuth, fov  -> direccion + FOV custom")
    print()
    print("Azimuth: 0=N, 90=E, 180=S, 270=O")
    print("FOV: campo de vision en grados (default 70)")
    print()
    print("Escribe 'q' para salir.")
    print("=" * 60)

    while True:
        entry = input("lat, lon [, azimuth [, fov]]: ").strip()
        if entry.lower() in ("q", "quit", "salir", "exit"):
            break
        if not entry:
            continue

        try:
            parts = entry.replace(",", " ").split()
            lat, lon = float(parts[0]), float(parts[1])
            azimuth = float(parts[2]) % 360 if len(parts) >= 3 else None
            fov = float(parts[3]) if len(parts) >= 4 else DEFAULT_FOV
        except (ValueError, IndexError):
            print("Formato: lat, lon [, azimuth [, fov]]")
            continue

        query(lat, lon, azimuth=azimuth, fov=fov, stream=True)


def main():
    if not check_ollama():
        sys.exit(1)
    load_landmarks()

    if len(sys.argv) >= 3:
        try:
            lat = float(sys.argv[1])
            lon = float(sys.argv[2])
            azimuth = float(sys.argv[3]) % 360 if len(sys.argv) >= 4 else None
            fov = float(sys.argv[4]) if len(sys.argv) >= 5 else DEFAULT_FOV
            query(lat, lon, azimuth=azimuth, fov=fov, stream=True)
        except ValueError:
            print("Uso: python query_model.py <lat> <lon> [azimuth] [fov]")
            sys.exit(1)
    else:
        interactive()


if __name__ == "__main__":
    main()