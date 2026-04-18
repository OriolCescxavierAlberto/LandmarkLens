#!/usr/bin/env python3
"""Create reproducible model artifacts for LandmarkLens.

This script writes a versioned Modelfile and a training configuration snapshot.
It can optionally call Ollama to create the model locally.
"""

from __future__ import annotations

import argparse
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path


def build_modelfile(system_prompt: str, base_model: str) -> str:
    return f"""# LandmarkLens model artifact
# Generated automatically by ml/scripts/train_model.py

FROM {base_model}

PARAMETER temperature 0.1
PARAMETER top_p 0.9
PARAMETER num_ctx 8192
PARAMETER num_predict 512
PARAMETER stop \"<|eot_id|>\"

SYSTEM \"\"\"{system_prompt}\"\"\"
"""


def main() -> None:
    parser = argparse.ArgumentParser(description="Create model artifacts")
    parser.add_argument("--run-name", default="landmark-finder-v1")
    parser.add_argument("--base-model", default="llama3.2:3b")
    parser.add_argument("--model-name", default="landmark-finder-v1")
    parser.add_argument("--system-prompt", default="ml/data/system_prompt.txt")
    parser.add_argument("--stats", default="ml/data/processed/dataset_stats.json")
    parser.add_argument("--output-dir", default="ml/models")
    parser.add_argument("--create-model", action="store_true")
    args = parser.parse_args()

    output_dir = Path(args.output_dir) / args.run_name
    output_dir.mkdir(parents=True, exist_ok=True)

    system_prompt = Path(args.system_prompt).read_text(encoding="utf-8").strip()
    stats = {}
    stats_path = Path(args.stats)
    if stats_path.exists():
        stats = json.loads(stats_path.read_text(encoding="utf-8"))

    modelfile = build_modelfile(system_prompt=system_prompt, base_model=args.base_model)
    modelfile_path = output_dir / "Modelfile"
    modelfile_path.write_text(modelfile, encoding="utf-8")

    config = {
        "run_name": args.run_name,
        "model_name": args.model_name,
        "base_model": args.base_model,
        "created_at_utc": datetime.now(timezone.utc).isoformat(),
        "dataset": {
            "stats_file": str(stats_path),
            "raw_samples": stats.get("raw_samples"),
            "clean_samples": stats.get("clean_samples"),
            "split_sizes": stats.get("split_sizes"),
        },
        "inference_parameters": {
            "temperature": 0.1,
            "top_p": 0.9,
            "num_ctx": 8192,
            "num_predict": 512,
        },
    }
    (output_dir / "training_config.json").write_text(
        json.dumps(config, indent=2, ensure_ascii=False), encoding="utf-8"
    )

    create_result = {
        "attempted": args.create_model,
        "success": False,
        "command": None,
        "error": None,
    }

    if args.create_model:
        command = ["ollama", "create", args.model_name, "-f", str(modelfile_path)]
        create_result["command"] = command
        try:
            completed = subprocess.run(command, check=True, capture_output=True, text=True)
            create_result["success"] = True
            create_result["stdout"] = completed.stdout
        except Exception as exc:
            create_result["error"] = str(exc)

    (output_dir / "model_build_result.json").write_text(
        json.dumps(create_result, indent=2, ensure_ascii=False), encoding="utf-8"
    )

    print(f"Model artifact created in: {output_dir}")
    if args.create_model:
        print(json.dumps(create_result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
