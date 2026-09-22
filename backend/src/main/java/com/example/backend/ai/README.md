# AI module

This package owns external AI integration and the conversion of unstructured
physics problems into validated specification documents.

- `client`: provider transport only; no extraction or OCR business rules.
- `extraction`: extraction orchestration and provider contracts.
- `extraction/model`: typed AI input/output documents.
- `ocr`: image-to-text contracts and implementations.
- `normalization`: deterministic normalization applied after AI output.

Application services depend on `ExtractionProvider` and `OcrProvider`, never on
an OpenAI-compatible implementation. Provider-specific HTTP details stay in `client`.
Prompts belong in `src/main/resources/prompts`, with resource locations supplied
through environment-backed configuration.
