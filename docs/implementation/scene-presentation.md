# Solver-bound scene presentation

The renderer does not select layouts by lesson ID, component name, or example values.
The solver remains responsible for quantities; the scene declares artwork and bindings;
the kit owns placement, live text, unit formatting, and animation.

- `instruments[].labelAnchor` is an optional point in the apparatus viewBox. Older scenes
  derive an anchor from the drive path midpoint or pivot. This is a geometric fallback,
  not a claim that the renderer has recognized a physical component.
- `annotations[]` declares `{on, label, field, anchor}`. `on` identifies an apparatus by
  its index or field. `field` must exist in the solver timeline, or be empty for a name.
  Numeric solver values belong in the binding, never in static label text.
- Standalone apparatus layout evaluates grid candidates against artwork aspect ratios.
  Callouts measure text, search nearby and available positions, and penalize overlap
  with earlier labels and component anchors. Typography and spacing are shared UI
  policy, not per-exercise coordinates. This remains a heuristic, not an optimal packing
  solver; dense or poorly framed artwork can still require authoring changes.
- Focused HTML readings follow explicit instrument/annotation bindings, deduplicated
  by field. All solver quantities remain accessible in the expandable data panel.
  Intermediate calculations are not implicitly promoted to physical objects.
- The existing cross-check still runs at multiple solver times and after recomputation.
  Bound annotation values additionally match the displayed solver value. Invalid field
  references and anchors outside the apparatus fail rather than fabricating a reading.
  These checks do **not** prove that an arbitrary SVG wire graph matches a circuit's
  topology, or that every image has good composition. Physics and visual quality must
  not be conflated.
- Offscreen rendering uses animation frames when available, with timer fallback and
  a separate heartbeat for background tabs. React receives fewer time updates than
  the drawing loop to reduce chart/readout work.

Existing saved scenes are supported without regeneration. New component annotations
require generation with the updated prompt/schema (and backend restart when prompts
are loaded at startup). Existing SVG artwork is not automatically redesigned.

## Regression checks

Run `node --experimental-strip-types --test tests/pixiRuntime.test.mjs
tests/sceneModel.test.mjs tests/scenePresentation.test.mjs` from `react-client`.

For the browser regression, run Vite on port 5175 and
`node tests/scenePresentation.browser.mjs`. Set `PLAYWRIGHT_MODULE` to a Playwright
module URL if it is not locally installed. `PRESENTATION_TEST_URL` overrides the URL.
The fixture is explicitly unverified sample data, not evidence of a backend physics run.
It checks rendering, recomputation, resize, themes, legacy data, arbitrary field names
and pressure units, plus rejection of unknown fields and out-of-bounds anchors.

At implementation time the focused tests, scoped lint and Vite bundling passed.
The full project still reports existing role/theme test failures, architecture-rule
violations and four TypeScript errors in AdminArea, TeacherSubmissionTable, Library,
and ReviewerOverview. Those checks are not claimed to pass.
