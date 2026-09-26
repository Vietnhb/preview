# Adaptive topic packs

The active topic-pack sources are in
`backend/src/main/resources/schemas/source/<TOPIC>/`. There is one `2.0` pack
per curriculum topic. `scripts/generate-schema-catalog.mjs` combines them into
`backend/src/main/resources/schemas/catalog.json`:

```powershell
node scripts/generate-schema-catalog.mjs --write
node scripts/generate-schema-catalog.mjs --check
```

Source filenames use `<order>__<schemaId>__<version>.json`. The generator checks
identity and order and detects catalog drift. The pack definition follows
`schemas/topic-pack.meta-schema-2.0.json`. It contains conceptual object,
relation, quantity, and law vocabulary, application requirements, an open-world
high-school physics vocabulary policy, and physical and visual capability
declarations. The listed concepts are routing and interpretation guides. New
concepts must be grounded in the confirmed description and an established
high-school law. It contains no exercise examples, formulas,
Matter.js code, ODE execution settings, time-series output, or solver bindings.

The classpath catalog is a declarative capability vocabulary. The AI extraction
and matter-flow runtimes derive their plan from the confirmed user description;
there is no template registry or fixed asset catalog in the runtime. Visual
elements are generated procedurally from the confirmed setup. A generated visual
must be labeled explanatory unless an independently validated numerical runtime
is available.

Optional database bootstrap publishes approved topic-pack definitions without
creating numerical solver rows. Published versions are immutable: make a new
version for a later change.
