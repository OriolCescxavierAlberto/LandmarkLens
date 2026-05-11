import json
import os
import glob

data_dir = os.path.join(os.path.dirname(__file__), 'landmark_model', 'data')
files = sorted(glob.glob(os.path.join(data_dir, 'landmarks_*.json')))

for f in files:
    try:
        with open(f, 'r', encoding='utf-8') as file:
            data = json.load(file)
            count = len(data.get('landmarks', []))
            print(f"{os.path.basename(f)}: {count} landmarks")
    except Exception as e:
        print(f"{os.path.basename(f)}: ERROR - {e}")

# Also check merged file
merged = os.path.join(data_dir, 'landmarks.json')
if os.path.exists(merged):
    with open(merged, 'r', encoding='utf-8') as f:
        data = json.load(f)
        count = len(data.get('landmarks', []))
        print(f"\nlandmarks.json (merged): {count} landmarks")
