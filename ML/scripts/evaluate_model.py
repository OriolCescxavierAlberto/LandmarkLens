#!/usr/bin/env python3
"""Evaluate dataset and artifact readiness for LandmarkLens ML."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def read_jsonl(path: Path) -> list[dict]:
    rows = []
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate prepared dataset quality")
    parser.add_argument("--test-file", default="ml/data/processed/test.jsonl")
    parser.add_argument("--output", default="ml/experiments/evaluation_report.json")
    args = parser.parse_args()

    test_file = Path(args.test_file)
    rows = read_jsonl(test_file)
    if not rows:
        raise RuntimeError("Test split is empty. Run prepare_data.py first.")

    total = len(rows)
    candidate_counts = [row.get("candidate_count", 0) for row in rows]
    has_5_candidates = sum(1 for c in candidate_counts if c == 5)
    has_coords = sum(
        1 for row in rows if row.get("latitude") is not None and row.get("longitude") is not None
    )
    has_prob_phrase = sum(1 for row in rows if row.get("contains_probability_phrase"))
    untitled = sum(1 for row in rows if row.get("contains_untitled"))

    report = {
        "test_samples": total,
        "metrics": {
            "five_candidates_rate": round(has_5_candidates / total, 4),
            "coordinates_available_rate": round(has_coords / total, 4),
            "probability_phrase_rate": round(has_prob_phrase / total, 4),
            "untitled_rate": round(untitled / total, 4),
            "avg_candidates": round(sum(candidate_counts) / total, 4),
        },
    }

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    print("Evaluation completed")
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
