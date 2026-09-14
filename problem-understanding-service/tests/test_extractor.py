import asyncio

import pytest

from app.extractor import extract
from app.models import ExtractTextRequest


def test_requires_ai_configuration(monkeypatch):
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    monkeypatch.setenv("OPENROUTER_MODEL", "test-model")

    with pytest.raises(RuntimeError, match="OPENROUTER_API_KEY"):
        asyncio.run(extract(ExtractTextRequest(text="Một bài toán vật lý")))
