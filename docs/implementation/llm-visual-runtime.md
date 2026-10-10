# LLM-authored visual runtime

The backend computes the signed physics plan with the existing schema AST/RK4 runtime. The visual LLM receives the original description and explanation, the complete signed plan (including model input bindings and additional context), the complete approved topic schema, raw solver metadata, full solver timeline and validation results. The generation payload does not filter fields, round values or resample the timeline. Large timelines therefore increase provider context usage; provider errors must be reported rather than silently dropping data. It returns `visualProgram` with `design`, `description`, `code`, and a positive `playbackRate` (simulated seconds per real second).

`code` is an `async function(PIXI, app, api)` that returns a synchronous `update(frame)` and optional synchronous `setData()`, `resize(width, height)`, `pointer(event)`, and `destroy()` methods. The LLM owns artwork, layout, screen transforms, labels, connections, visual mappings, and responsive behaviour. There are no stage templates or automatic apparatus inference.

The renderer supplies immutable backend fields, parameters, metadata, ranges and timeline samples. It calls the authored lifecycle and renders the Pixi stage. It never adds ropes/springs, joins participants, changes their positions, fits a camera, places labels, changes their colours, infers playback speed, or substitutes a reference scene. Chart/readout metadata outside the stage does not influence the authored canvas.

The runtime still isolates generated code in a worker inside an opaque-origin iframe with blocked network access, a watchdog, and code/texture budgets. SVG sanitisation removes executable content and external resources; it preserves authored text, colours and geometry. SVG framing must be supplied by the author. Failed or invisible rendering produces diagnostics for LLM repair rather than a frontend redesign.

Backend verification status refers to the numerical result. It does not verify the LLM's artwork or its visual mappings; those require visual review/evaluation.

## Existing saved scenes

Old programs that depend on `api.kit` or only supply the former declarative `scene` contract require regeneration through the existing LLM repair/redesign flow. The old scene is passed as diagnostic context. Opening it does not silently reinterpret it or rewrite the stored physics plan. Existing raw PixiJS lifecycle programs using the documented API continue to run.

## Implementation

- `backend/src/main/resources/prompts/simulation-visual-system.txt`: authoring and runtime contract.
- `backend/src/main/resources/prompts/simulation-response-schema.json`: executable response contract.
- `backend/src/main/java/com/example/backend/system/simulation/service/AIService.java`: require code and validate transport budget; computation/signing unchanged.
- `react-client/src/features/simulation/engine/pixiRuntime.ts`: worker/asset transport and isolation.
- `react-client/src/features/simulation/components/SvgPixiScene.tsx`: host, playback controls, diagnostic reporting and numeric panels.
- `react-client/src/features/simulation/model/sceneModel.ts`: numeric-panel metadata only, without apparatus inference.

The local `react-client/tests/llm-renderer/` fixture exercises independent objects, authored SVG text/layout, parameter changes, old scene diagnostics, and recovery from generated-code errors. Its programs and data are test fixtures, not generated physics lessons or evidence of LLM design quality.
