# Schema-routing retrieval evaluation

The fixture and report in this directory are generated from the latest approved
entries in `backend/src/main/resources/schemas/catalog.json`.

```text
node scripts/evaluate-schema-routing-retrieval.mjs --write
node scripts/evaluate-schema-routing-retrieval.mjs --check
```

The fixture contains one catalog-derived answerable case for every active
schema identity, a deterministic calibration/held-out split, and synthetic
ambiguous/out-of-scope challenge cases. It is labelled synthetic and does not
claim official textbook wording or universal retrieval accuracy.

The offline evaluator exercises BM25, deterministic fake embeddings, cosine
ordering, RRF, and catalog-derived contract evidence. The reported offline
thresholds are algorithm regression evidence only. A separate provider gate is
required with the configured real embedding provider, recorded latency and
held-out labels. The evaluator reports that gate as `BLOCKED_EXTERNAL_PROVIDER`
when credentials and provider evidence are unavailable; `--release` therefore
fails until that evidence is supplied.
