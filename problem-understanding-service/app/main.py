import os

from fastapi import FastAPI, File, Form, UploadFile
from fastapi.middleware.cors import CORSMiddleware

from .extractor import extract, extract_image
from .models import ExtractTextRequest

app = FastAPI(title="PhysLive Problem Understanding", version="1.0.0")
allowed_origins = [value.strip() for value in os.getenv("ALLOWED_ORIGINS", "http://localhost:5173").split(",") if value.strip()]
app.add_middleware(CORSMiddleware, allow_origins=allowed_origins, allow_methods=["*"], allow_headers=["*"])


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": "problem-understanding", "engine": "openrouter"}


@app.post("/api/v1/extract/text")
async def extract_text(request: ExtractTextRequest):
    return await extract(request)


@app.post("/api/v1/extract/image")
async def understand_image(file: UploadFile = File(...), text: str | None = Form(None)):
    content = await file.read()
    return await extract_image(file.content_type or "application/octet-stream", content, text)
