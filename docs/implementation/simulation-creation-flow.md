# Simulation ownership and execution

The backend owns the physics contract: topic selection, approved executable laws, input bindings, parameter values and domains, participant relationships, stages, observables, solver results and physics validation. The frontend does not derive missing physical data or choose an alternative model.

## Request flow

1. The frontend submits the user's text or image to `/api/simulation/understand`. Recognition and interpretation come from the backend. The frontend displays the returned recognition, clarification or explanation.
2. Corrections go to `/api/simulation/revise` as `{intent, text}`. The backend checks an explained plan's signature and composes the interpretation context. The frontend does not rewrite the request into planning instructions.
3. Confirmation submits the backend intent to `/api/simulation/generate`. The backend checks its signature, computes the approved model and generates its PixiJS presentation from the contract and solver timeline.
4. The frontend runs that presentation in an isolated worker and displays backend samples, metadata and validation. Playback, seeking, responsive rendering, number formatting and sample interpolation are presentation operations.
5. Parameter changes submit exact numeric values and the complete original contract to `/api/simulation/compute`. The backend enforces the domains and returns new samples and validation. The previous rendered parameter values remain paired with their timeline until the response arrives.

Each parameter has its own control. Equal initial values or similar input names do not create a relationship between independent parameters. Missing bounds do not cause the frontend to invent a domain. Focusing and leaving an input does not round or resend its underlying value. Request errors are separate from backend physics-validation results.

## Visual failures

The worker isolates authored JavaScript and prevents mutation of backend samples. SVG resources are parsed and checked without deleting tags or attributes. Unsupported content produces an `SVG_CONTRACT` error identifying the rejected element or attribute; accepted local references and inline styles are preserved. Raster dimensions may change when the authored program explicitly requests them.

Renderer failures are submitted to `/api/simulation/render-diagnostics`. The backend verifies the signed plan and records `SIMULATION_RENDER_FAILURE` with the contract identity, original program checksum and error message. This endpoint does not recompute physics or choose a retry policy. The frontend does not silently regenerate a scene. An explicit repair action sends the original program and rendering feedback to generation.

## Historical records

The library, assignments and reviewer previews render a stored backend-authored program when available. A record containing only legacy numeric samples displays its declared readings. The client does not infer apparatus, contacts, wires, forces, topic layouts or a replacement animation. The old Canvas scene synthesis, topic-specific renderer and local projectile calculator have been removed.

Saving and reopening retain the signed plan, backend timeline and stored presentation. The schema still limits which physical models can be computed; removing frontend inference does not expand that catalog.
