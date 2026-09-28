# Saving generated simulations

The existing understand → confirm → generate → compute flow is unchanged. The workspace now offers **Lưu mô phỏng** after generation. Teachers choose a title, an existing personal folder (or create one through the existing folder API), and a curriculum lesson within the simulation's topic.

`POST /api/simulation/saved` receives the generated result, current parameter overrides, title, folder ID and lesson ID. It verifies the signed physics plan, recomputes the timeline with the existing solver, and accepts verified analytical/numerical results only. A transaction creates the existing submission/specification/simulation/run records and delegates the library item creation to `LibraryService`, retaining its folder ownership checks and personal visibility. No migration or AI request is required.

The saved run contains the generated SVG/Pixi program, signed original plan, verified timeline and validation, effective parameter values, and available explanation/formulas. The original signed parameter defaults remain unchanged, allowing subsequent `/compute` requests after reopening.

`GET /api/simulation/saved/{simulationId}` reads the owner's stored snapshot. Selecting an item in the workspace folder pane or following its title from the personal library restores that snapshot without calling AI. Rendering errors on an opened snapshot require manual repair. Older library records without a generated visual program return an explanatory error without replacing the currently open simulation.

Saving again creates another personal library entry; it does not overwrite an older saved run. Existing library rename, move and delete operations continue to apply.

Validation: storage unit tests cover exact scene/parameter restoration, replacement of client timeline with server results, ownership isolation, wrong-topic rejection and unverified-result rejection. Existing solver tests remain applicable. An authenticated browser/database round trip is still required to verify deployed persistence end to end.
