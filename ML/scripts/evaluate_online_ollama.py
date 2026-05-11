#!/usr/bin/env python3
"""Run online end-to-end evaluation against the local RAG query flow.

This evaluation checks structural quality of model outputs:
- JSON validity
- output latency
- whether predicted landmark names come from the retrieved candidates
"""

from __future__ import annotations

import argparse
import json
import os
import re
import statistics
import sys
import time
from pathlib import Path
from typing import Any

# Agregar parent directory al path para que encuentre landmark_model
SCRIPT_DIR = Path(__file__).resolve().parent
PARENT_DIR = SCRIPT_DIR.parent
if str(PARENT_DIR) not in sys.path:
    sys.path.insert(0, str(PARENT_DIR))

try:
    from landmark_model import query_model
except ImportError:
    # Fallback: import desde landmark_model directamente si está en el mismo directorio
    import importlib.util
    spec = importlib.util.spec_from_file_location("query_model", PARENT_DIR / "landmark_model" / "query_model.py")
    query_model = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(query_model)


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows = []
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def reference_target_name(reference_response: str) -> str | None:
    parsed = query_model._validate_response(reference_response)
    if isinstance(parsed, list) and parsed:
        first_item = parsed[0]
        if isinstance(first_item, dict):
            name = first_item.get("name")
            if isinstance(name, str):
                return name.strip()
    if isinstance(parsed, dict):
        target = parsed.get("target")
        if isinstance(target, str):
            return target.strip()
    return None


def main() -> None:
    parser = argparse.ArgumentParser(description="Online Ollama evaluation")
    parser.add_argument("--model", default=query_model.MODEL_NAME)
    parser.add_argument("--test-file", default="data/processed/test.jsonl")
    parser.add_argument("--output", default="experiments/online_eval_report.json")
    parser.add_argument("--max-samples", type=int, default=20)
    args = parser.parse_args()

    # Resolver rutas relativas a la carpeta ML (parent de scripts)
    test_file = Path(args.test_file)
    if not test_file.is_absolute():
        test_file = PARENT_DIR / test_file
    
    output_file = Path(args.output)
    if not output_file.is_absolute():
        output_file = PARENT_DIR / output_file
    
    rows = read_jsonl(test_file)
    rows = rows[: args.max_samples]

    per_sample: list[dict[str, Any]] = []
    latency_values = []
    json_valid_count = 0
    in_candidates_count = 0
    any_prediction_count = 0
    top1_count = 0

    for idx, row in enumerate(rows, start=1):
        question = str(row.get("prompt", "")).strip()
        reference_response = str(row.get("response", "")).strip()
        target_name = reference_target_name(reference_response)

        lat = row.get("latitude")
        lon = row.get("longitude")
        if lat is None or lon is None:
            per_sample.append(
                {
                    "index": idx,
                    "skipped": True,
                    "reason": "missing_coordinates",
                }
            )
            continue

        if not question:
            per_sample.append(
                {
                    "index": idx,
                    "skipped": True,
                    "reason": "missing_question",
                }
            )
            continue

        try:
            t0 = time.perf_counter()
            result = query_model.run_rag_query(float(lat), float(lon), stream=False, model_name=args.model)
            latency_ms = int((time.perf_counter() - t0) * 1000)
            latency_values.append(latency_ms)

            parsed = result.validation.get("parsed")
            is_json_valid = bool(result.validation.get("is_json_valid"))
            if is_json_valid:
                json_valid_count += 1

            pred = result.validation.get("predicted_names", [])
            allowed = {c["name"] for c in result.nearby if c.get("name")}
            if pred:
                any_prediction_count += 1
            in_candidates = bool(pred) and all(name in allowed for name in pred)
            if in_candidates:
                in_candidates_count += 1
            if target_name and pred and pred[0] == target_name:
                top1_count += 1

            per_sample.append(
                {
                    "index": idx,
                    "latency_ms": latency_ms,
                    "json_valid": is_json_valid,
                    "predicted_names": pred,
                    "allowed_candidates": sorted(allowed),
                    "all_predicted_in_candidates": in_candidates,
                    "top1_matches_reference": bool(target_name and pred and pred[0] == target_name),
                    "reference_target": target_name,
                    "raw_output_preview": (result.raw_text or "")[:300],
                }
            )
        except Exception as exc:
            per_sample.append(
                {
                    "index": idx,
                    "error": str(exc),
                }
            )

    total = len(rows)
    successful_calls = sum(1 for s in per_sample if "latency_ms" in s)

    report = {
        "model": args.model,
        "samples_requested": total,
        "samples_called": successful_calls,
        "metrics": {
            "json_valid_rate": round(json_valid_count / successful_calls, 4)
            if successful_calls
            else 0.0,
            "predictions_in_candidates_rate": round(in_candidates_count / successful_calls, 4)
            if successful_calls
            else 0.0,
            "non_empty_prediction_rate": round(any_prediction_count / successful_calls, 4)
            if successful_calls
            else 0.0,
            "top1_match_rate": round(top1_count / successful_calls, 4) if successful_calls else 0.0,
            "latency_avg_ms": round(statistics.mean(latency_values), 2) if latency_values else None,
            "latency_p95_ms": round(statistics.quantiles(latency_values, n=20)[18], 2)
            if len(latency_values) >= 20
            else None,
        },
        "samples": per_sample,
    }

    output_file.parent.mkdir(parents=True, exist_ok=True)
    output_file.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    print("Online evaluation finished")
    print(json.dumps(report["metrics"], indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
