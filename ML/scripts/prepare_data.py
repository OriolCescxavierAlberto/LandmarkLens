#!/usr/bin/env python3
"""Prepare LandmarkLens dataset for ML experiments.

This script validates, normalizes, deduplicates and splits the dataset into
train/validation/test JSONL files.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import random
import re
from pathlib import Path
from typing import Dict, List, Tuple

COORD_RE = re.compile(r"-?\d+(?:\.\d+)?")
CANDIDATE_RE = re.compile(r"(?m)^\d+\.")


def load_samples(path: Path) -> List[Dict[str, str]]:
    with path.open("r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, list):
        raise ValueError("Expected a JSON array of samples")
    return data


def normalize_text(text: str) -> str:
    lines = [line.rstrip() for line in text.replace("\r\n", "\n").split("\n")]
    # Keep paragraph boundaries but collapse noisy blank tails.
    while lines and not lines[-1].strip():
        lines.pop()
    return "\n".join(lines).strip()


def extract_coords(prompt: str) -> Tuple[float, float] | Tuple[None, None]:
    numbers = [float(m.group(0)) for m in COORD_RE.finditer(prompt)]
    if len(numbers) < 2:
        return (None, None)
    return (numbers[0], numbers[1])


def split_data(items: List[Dict[str, str]], seed: int) -> Dict[str, List[Dict[str, str]]]:
    rng = random.Random(seed)
    shuffled = list(items)
    rng.shuffle(shuffled)

    n = len(shuffled)
    n_train = int(n * 0.8)
    n_val = int(n * 0.1)
    n_test = n - n_train - n_val

    return {
        "train": shuffled[:n_train],
        "val": shuffled[n_train : n_train + n_val],
        "test": shuffled[n_train + n_val : n_train + n_val + n_test],
    }


def write_jsonl(path: Path, items: List[Dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8") as f:
        for item in items:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser(description="Prepare LandmarkLens JSON dataset")
    parser.add_argument(
        "--input",
        default="ml/data/training_examples.json",
        help="Path to raw JSON dataset",
    )
    parser.add_argument(
        "--output-dir",
        default="ml/data/processed",
        help="Output directory for processed files",
    )
    parser.add_argument("--seed", type=int, default=42, help="Random seed for split")
    args = parser.parse_args()

    input_path = Path(args.input)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    raw_text = input_path.read_text(encoding="utf-8")
    raw_sha256 = hashlib.sha256(raw_text.encode("utf-8")).hexdigest()

    raw_samples = load_samples(input_path)

    cleaned: List[Dict[str, str]] = []
    seen = set()
    dropped_empty = 0
    dropped_duplicates = 0

    for item in raw_samples:
        prompt = normalize_text(str(item.get("prompt", "")))
        response = normalize_text(str(item.get("response", "")))
        if not prompt or not response:
            dropped_empty += 1
            continue

        key = (prompt, response)
        if key in seen:
            dropped_duplicates += 1
            continue
        seen.add(key)

        lat, lon = extract_coords(prompt)
        cleaned.append(
            {
                "prompt": prompt,
                "response": response,
                "latitude": lat,
                "longitude": lon,
                "candidate_count": len(CANDIDATE_RE.findall(response)),
                "contains_untitled": "**untitled**" in response,
                "contains_probability_phrase": "Es muy probable" in response,
            }
        )

    splits = split_data(cleaned, args.seed)

    for split_name, items in splits.items():
        write_jsonl(output_dir / f"{split_name}.jsonl", items)

    latitudes = [x["latitude"] for x in cleaned if x["latitude"] is not None]
    longitudes = [x["longitude"] for x in cleaned if x["longitude"] is not None]
    candidate_counts = [x["candidate_count"] for x in cleaned]

    stats = {
        "input_path": str(input_path),
        "input_size_bytes": len(raw_text.encode("utf-8")),
        "input_sha256": raw_sha256,
        "raw_samples": len(raw_samples),
        "clean_samples": len(cleaned),
        "dropped_empty": dropped_empty,
        "dropped_duplicates": dropped_duplicates,
        "split_sizes": {k: len(v) for k, v in splits.items()},
        "feature_summary": {
            "lat_range": [min(latitudes), max(latitudes)] if latitudes else None,
            "lon_range": [min(longitudes), max(longitudes)] if longitudes else None,
            "avg_candidates_per_sample": round(sum(candidate_counts) / len(candidate_counts), 3)
            if candidate_counts
            else None,
            "samples_with_untitled": sum(1 for x in cleaned if x["contains_untitled"]),
            "samples_with_probability_phrase": sum(
                1 for x in cleaned if x["contains_probability_phrase"]
            ),
        },
    }

    stats_path = output_dir / "dataset_stats.json"
    stats_path.write_text(json.dumps(stats, indent=2, ensure_ascii=False), encoding="utf-8")

    print("Dataset prepared successfully")
    print(json.dumps(stats, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
