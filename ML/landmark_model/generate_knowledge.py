#!/usr/bin/env python3
"""Generate the lightweight RAG assets used by LandmarkLens."""

from __future__ import annotations

import os
import sys


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PARENT_DIR = os.path.dirname(SCRIPT_DIR)
if PARENT_DIR not in sys.path:
    sys.path.insert(0, PARENT_DIR)

from landmark_model.rag_core import (  # noqa: E402
    MODEL_NAME,
    MODFILE_PATH,
    RAG_MANIFEST_PATH,
    SYSTEM_PROMPT_PATH,
    build_modelfile,
    build_rag_manifest,
    write_text,
)


def main():
    if not os.path.exists(os.path.join(SCRIPT_DIR, "data", "landmarks.json")):
        print("❌ No se encuentra landmarks.json")
        print("   Ejecuta primero: python extract_landmarks.py")
        sys.exit(1)

    manifest = build_rag_manifest()
    print(f"📝 System prompt guardado: {SYSTEM_PROMPT_PATH}")
    print(f"📝 Modelfile guardado: {MODFILE_PATH}")
    print(f"📝 RAG manifest guardado: {RAG_MANIFEST_PATH}")
    print(f"\n📊 Landmarks disponibles para RAG:")
    print(f"   Total: {manifest['dataset']['landmarks_total']}")
    print(f"   Con coordenadas: {manifest['dataset']['landmarks_with_coordinates']}")
    if manifest['dataset']['training_examples'] is not None:
        print(f"   Ejemplos sinteticos: {manifest['dataset']['training_examples']}")
    print(f"\n✅ Ahora ejecuta:")
    print(f"   ollama create {MODEL_NAME} -f Modelfile")


if __name__ == '__main__':
    main()
