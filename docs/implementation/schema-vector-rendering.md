# Schema vector rendering

The `vectorScene` primitive consumes `properties.vector`. Geometry uses an SVG-style
viewBox with aspect-preserving viewport fitting. Each shape has its own local
translation, rotation (radians), scale and opacity; children inherit transforms.
Supported shapes: path (M/L/Q/C/Z), ellipse, rect, text, group.
Numeric attributes and path coordinates accept the existing constant, series,
quantity and entity bindings. Text can insert a numeric binding at `{value}`.
Bindings may use safe nested expressions: `add`, `subtract`, `multiply`,
`divide`, `min`, `max`, `abs`, `negate`, `sin`, `cos`, and `clamp`. They cannot
execute JavaScript or access browser APIs.

Example sceneGraph node (positions.x must exist in solver output):

```json
{
  "id": "experiment",
  "type": "vectorScene",
  "layer": "dynamic",
  "properties": {
    "vector": {
      "viewBox": [-10, -5, 20, 10],
      "shapes": [
        {"kind":"path","stroke":"#64748b","lineWidth":0.03,"commands":[{"op":"M","args":[-10,1]},{"op":"L","args":[10,1]}]},
        {"kind":"group","x":"positions.x","children":[
          {"kind":"rect","x":-0.5,"y":0,"width":1,"height":1,"fill":"#38bdf8"}
        ]}
      ]
    }
  }
}
```

AI authoring must emit this JSON contract using actual solver output keys, not
executable JavaScript or raw SVG. A schema author can compose arbitrary vector
silhouettes without adding a lesson-specific renderer. This contract describes
presentation, not a new physics solver. Runtime validation rejects invalid path
arity, nonfinite literal values, missing data bindings and excessive nesting.

The extraction AI selects an approved schema; the selected versioned schema is
the visual-authoring contract returned with the simulation. It cannot inject
raw SVG or JavaScript. Reviewers can author `vectorScene` geometry in a schema,
and the same validation runs when a draft is approved. All catalog schemas pass
the scene-contract gate. The magnifier, microscope and telescope now use
declarative vector geometry in schema version 1.1. Their 1.0 `lens`/`ray`
primitive renderer remains read-only compatibility for saved simulations and
cannot pass approval for a new schema. Schemas without an explicit scene graph are
rendered from their declared actors and series by the generic fallback compiler.
