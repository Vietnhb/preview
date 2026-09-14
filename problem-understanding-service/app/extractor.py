import json
import os
from base64 import b64encode
from pathlib import Path

import httpx

from .models import ExtractResponse, ExtractTextRequest, Specification

SYSTEM_PROMPT = """
You are the PhysLive Problem Understanding Engine.
Read the Vietnamese or English physics problem semantically. Do not use keyword matching.
Return one JSON object with: schema_version, schema_id, topic, objects, quantities, relations,
confidence and ambiguities. Every quantity must include name, symbol, value, original_unit,
normalized_value, normalized_unit, confidence and source_text. Use SI normalized values.
Choose schema_id only from the supplied approved schema catalog and use its canonical quantity keys.
Never invent missing facts. Represent each uncertainty as an ambiguity with a stable code,
concrete field, concise Vietnamese question and physically meaningful options when possible.
Return JSON only.
""".strip()


async def extract(request: ExtractTextRequest) -> ExtractResponse:
    return await _complete([
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": request.text.strip()},
    ])


async def extract_image(content_type: str, content: bytes, supplied_text: str | None = None) -> ExtractResponse:
    data_url = f"data:{content_type};base64,{b64encode(content).decode('ascii')}"
    context = supplied_text.strip() if supplied_text else "Read the complete physics problem from this image."
    return await _complete([
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": [
            {"type": "text", "text": context},
            {"type": "image_url", "image_url": {"url": data_url}},
        ]},
    ])


async def _complete(messages: list[dict]) -> ExtractResponse:
    key = os.getenv("OPENROUTER_API_KEY", "").strip()
    if not key:
        raise RuntimeError("OPENROUTER_API_KEY is required for problem understanding")

    model = os.getenv("OPENROUTER_MODEL", "").strip()
    if not model:
        raise RuntimeError("OPENROUTER_MODEL is required for problem understanding")

    configured_catalog = os.getenv("PHYSLIVE_SCHEMA_CATALOG", "").strip()
    catalog_path = Path(configured_catalog) if configured_catalog else Path(__file__).parents[2] / "backend" / "src" / "main" / "resources" / "schemas" / "catalog.json"
    if not catalog_path.is_file():
        raise RuntimeError("PHYSLIVE_SCHEMA_CATALOG must point to the approved schema catalog")
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    effective_messages = list(messages)
    effective_messages[0] = {**effective_messages[0], "content": effective_messages[0]["content"] + "\n\nApproved schema catalog:\n" + json.dumps(catalog, ensure_ascii=False)}

    base_url = os.getenv("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1").rstrip("/")
    timeout_seconds = float(os.getenv("OPENROUTER_TIMEOUT_SECONDS", "60"))
    max_tokens = int(os.getenv("OPENROUTER_MAX_TOKENS", "6000"))
    app_url = os.getenv("OPENROUTER_APP_URL", "http://localhost:5173")
    app_name = os.getenv("OPENROUTER_APP_NAME", "PhysLive")
    async with httpx.AsyncClient(timeout=timeout_seconds) as client:
        response = await client.post(
            f"{base_url}/chat/completions",
            headers={
                "Authorization": f"Bearer {key}",
                "Content-Type": "application/json",
                "HTTP-Referer": app_url,
                "X-OpenRouter-Title": app_name,
            },
            json={
                "model": model,
                "messages": effective_messages,
                "temperature": 0,
                "max_tokens": max_tokens,
                "response_format": {"type": "json_object"},
            },
        )
        response.raise_for_status()

    content = response.json()["choices"][0]["message"]["content"].strip()
    if content.startswith("```"):
        content = content.removeprefix("```json").removeprefix("```").removesuffix("```").strip()
    specification = Specification.model_validate(json.loads(content))
    return ExtractResponse(specification=specification, provider="openrouter")
