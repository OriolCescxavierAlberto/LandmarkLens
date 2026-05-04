#!/usr/bin/env python3
"""Run online inference evaluation against a local Ollama model.

This evaluation checks structural quality of model outputs:
- JSON validity
- output latency
- whether predicted landmark names come from provided candidates
"""

from __future__ import annotations

import argparse
import json
import re
import statistics
import time
from pathlib import Path
from typing import Any

import requests

NAME_RE = re.compile(r"(?m)^\d+\.\s+\*\*(.+?)\*\*\s+—")
DIST_RE = re.compile(r"(?m)^\d+\.\s+\*\*(.+?)\*\*\s+—\s+a\s+(\d+)\s+metros")


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows = []
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def parse_candidates(reference_response: str) -> list[dict[str, Any]]:
    candidates = []
    for m in DIST_RE.finditer(reference_response):
        candidates.append({"name": m.group(1).strip(), "distance": int(m.group(2))})

    if not candidates:
        for m in NAME_RE.finditer(reference_response):
            candidates.append({"name": m.group(1).strip(), "distance": None})

    return candidates


def extract_json(text: str) -> Any | None:
    text = text.strip()

    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    start_list = text.find("[")
    end_list = text.rfind("]")
    if start_list != -1 and end_list > start_list:
        chunk = text[start_list : end_list + 1]
        try:
            return json.loads(chunk)
        except json.JSONDecodeError:
            pass

    start_obj = text.find("{")
    end_obj = text.rfind("}")
    if start_obj != -1 and end_obj > start_obj:
        chunk = text[start_obj : end_obj + 1]
        try:
            return json.loads(chunk)
        except json.JSONDecodeError:
            pass

    return None


def predicted_names(parsed: Any) -> list[str]:
    names: list[str] = []
    if isinstance(parsed, list):
        for item in parsed:
            if isinstance(item, dict) and isinstance(item.get("name"), str):
                names.append(item["name"].strip())
    elif isinstance(parsed, dict):
        target = parsed.get("target")
        if isinstance(target, str):
            names.append(target.strip())
        others = parsed.get("others")
        if isinstance(others, list):
            for item in others:
                if isinstance(item, dict) and isinstance(item.get("name"), str):
                    names.append(item["name"].strip())
    return names


def build_prompt(question: str, candidates: list[dict[str, Any]]) -> str:
    lines = []
    for i, c in enumerate(candidates, start=1):
        if c["distance"] is None:
            lines.append(f'{i}. "{c["name"]}"')
        else:
            lines.append(f'{i}. "{c["name"]}" - {c["distance"]}m')

    candidate_block = "\n".join(lines)

    return (
        "You are a strict JSON formatter for LandmarkLens.\n"
        "Use ONLY landmark names from the candidate list.\n"
        "Return ONLY valid JSON array with objects: \n"
        '[{"name":"EXACT candidate name","distance":integer,"confidence":"high|medium|low"}]\n\n'
        f"Question: {question}\n"
        f"Candidates:\n{candidate_block}\n"
    )


def call_ollama(model: str, prompt: str, base_url: str, timeout_sec: float) -> tuple[str, int]:
    t0 = time.perf_counter()
    response = requests.post(
        f"{base_url}/api/generate",
        json={"model": model, "prompt": prompt, "stream": False},
        timeout=timeout_sec,
    )
    elapsed_ms = int((time.perf_counter() - t0) * 1000)
    response.raise_for_status()
    data = response.json()
    return str(data.get("response", "")), elapsed_ms


def main() -> None:
    parser = argparse.ArgumentParser(description="Online Ollama evaluation")
    parser.add_argument("--model", default="landmark-finder-e4")
    parser.add_argument("--test-file", default="ML/data/processed/test.jsonl")
    parser.add_argument("--output", default="ML/experiments/online_eval_report.json")
    parser.add_argument("--base-url", default="http://localhost:11434")
    parser.add_argument("--max-samples", type=int, default=20)
    parser.add_argument("--timeout", type=float, default=60.0)
    args = parser.parse_args()

    rows = read_jsonl(Path(args.test_file))
    rows = rows[: args.max_samples]

    per_sample: list[dict[str, Any]] = []
    latency_values = []
    json_valid_count = 0
    in_candidates_count = 0
    any_prediction_count = 0

    for idx, row in enumerate(rows, start=1):
        question = str(row.get("prompt", "")).strip()
        reference_response = str(row.get("response", "")).strip()
        candidates = parse_candidates(reference_response)

        if not question or not candidates:
            per_sample.append(
                {
                    "index": idx,
                    "skipped": True,
                    "reason": "missing_question_or_candidates",
                }
            )
            continue

        prompt = build_prompt(question, candidates)

        try:
            raw_text, latency_ms = call_ollama(
                model=args.model,
                prompt=prompt,
                base_url=args.base_url,
                timeout_sec=args.timeout,
            )
            latency_values.append(latency_ms)

            parsed = extract_json(raw_text)
            is_json_valid = parsed is not None
            if is_json_valid:
                json_valid_count += 1

            pred = predicted_names(parsed) if parsed is not None else []
            allowed = {c["name"] for c in candidates}
            if pred:
                any_prediction_count += 1
            in_candidates = bool(pred) and all(name in allowed for name in pred)
            if in_candidates:
                in_candidates_count += 1

            per_sample.append(
                {
                    "index": idx,
                    "latency_ms": latency_ms,
                    "json_valid": is_json_valid,
                    "predicted_names": pred,
                    "allowed_candidates": sorted(allowed),
                    "all_predicted_in_candidates": in_candidates,
                    "raw_output_preview": raw_text[:300],
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
            "latency_avg_ms": round(statistics.mean(latency_values), 2) if latency_values else None,
            "latency_p95_ms": round(statistics.quantiles(latency_values, n=20)[18], 2)
            if len(latency_values) >= 20
            else None,
        },
        "samples": per_sample,
    }

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    print("Online evaluation finished")
    print(json.dumps(report["metrics"], indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
