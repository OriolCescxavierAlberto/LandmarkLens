import requests
import json

url = "http://localhost:8000/api/v1/query"
data = {"lat": 41.4036, "lon": 2.1744}

print("Testing API endpoint: POST /api/v1/query")
print(f"URL: {url}")
print(f"Data: {data}\n")

try:
    response = requests.post(url, json=data)
    print(f"Status Code: {response.status_code}")
    print(f"Response:\n{json.dumps(response.json(), indent=2, ensure_ascii=False)}")
except Exception as e:
    print(f"Error: {e}")
