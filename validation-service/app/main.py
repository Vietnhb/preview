from typing import Any
from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(title="PhysLive Validation", version="1.0.0")

class ValidationRequest(BaseModel):
    analytical: dict[str, list[float]]
    numerical: dict[str, list[float]]
    tolerance: float = Field(default=1e-3, gt=0)

def validate(analytical: dict[str, list[float]], numerical: dict[str, list[float]], tolerance: float) -> dict[str, Any]:
    checkpoints = []; passed = True
    for name, expected in analytical.items():
        actual = numerical.get(name, []); errors = [abs(a - e) / max(abs(e), 1e-9) for a, e in zip(actual, expected)]; max_error = max(errors, default=float("inf")); item_passed = len(actual) == len(expected) and max_error <= tolerance; passed = passed and item_passed
        checkpoints.append({"field": name, "passed": item_passed, "maxRelativeError": max_error})
    return {"passed": passed, "tolerance": tolerance, "checkpoints": checkpoints}

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": "validation"}

@app.post("/api/v1/validate")
def validate_endpoint(request: ValidationRequest):
    return validate(request.analytical, request.numerical, request.tolerance)
