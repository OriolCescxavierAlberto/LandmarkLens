from typing import Optional

import uvicorn
from fastapi import FastAPI, HTTPException, Response
from pydantic import BaseModel

import query_model
from landmark_model.rag_core import build_rag_manifest


app = FastAPI(title="LandmarkLens ML API", description="API to query the LandmarkLens model")


@app.on_event("startup")
def startup_event():
    # Load landmarks and initialize the index.
    query_model.load_landmarks()
    if not query_model.check_ollama():
        print("Warning: Ollama is not running or the model is missing.")

@app.get("/")
def read_root():
    return {"message": "Welcome to LandmarkLens ML API. Visit /docs for documentation."}

@app.get("/favicon.ico", include_in_schema=False)
def favicon():
    return Response(status_code=204)

class QueryRequest(BaseModel):
    lat: float
    lon: float
    azimuth: Optional[float] = None
    fov: float = query_model.DEFAULT_FOV

@app.post("/api/v1/query")
def query_landmarks(request: QueryRequest):
    try:
        result = query_model.run_rag_query(request.lat, request.lon, request.azimuth, request.fov, stream=False)

        if result.raw_text is None:
            raise HTTPException(status_code=502, detail="Failed to query the model or invalid response.")

        if result.validation.get("schema_ok"):
            return {"status": "success", "data": result.validation.get("parsed"), "validation": result.validation}

        if result.validation.get("is_json_valid"):
            return {
                "status": "degraded",
                "data": result.validation.get("parsed"),
                "raw_response": result.raw_text,
                "validation": result.validation,
            }

        return {"status": "degraded", "raw_response": result.raw_text, "validation": result.validation}

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/v1/health")
def health_check():
    ollama_ok = query_model.check_ollama()
    return {"status": "ok" if ollama_ok else "degraded", "ollama_connected": ollama_ok}


@app.get("/api/v1/rag/manifest")
def rag_manifest():
    return build_rag_manifest()

if __name__ == "__main__":
    uvicorn.run("api:app", host="0.0.0.0", port=8000, reload=True)
