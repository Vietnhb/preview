import test from "node:test";
import assert from "node:assert/strict";
import { selectAsset, tokenizeAssetHint } from "../src/simulation-assets/AssetSelector.ts";

const definitions = [
  {
    id: "cart",
    kind: "actor",
    tags: ["vehicle", "dynamics"],
    variants: [
      { id: "cart-blue", tags: ["blue", "cool"], aliases: ["object.cart.blue"] },
      { id: "cart-orange", tags: ["orange", "warm"], aliases: ["object.cart.orange"] },
    ],
  },
  {
    id: "projectile",
    kind: "actor",
    tags: ["particle"],
    variants: [{ id: "projectile-energy", tags: ["energy", "blue"], aliases: ["projectile.energy"] }],
  },
];

test("tokenization makes descriptive dotted asset hints searchable", () => {
  assert.deepEqual(tokenizeAssetHint("Vehicle.Cart.Blue"), ["vehicle", "cart", "blue"]);
});

test("tokenization keeps Vietnamese semantic asset hints searchable", () => {
  assert.deepEqual(tokenizeAssetHint("xe ô tô · lò xo"), ["xe", "o", "to", "lo", "xo"]);
});

test("selector honors semantic aliases instead of requiring exact registry keys", () => {
  const selected = selectAsset("vehicle.cart.orange", "actor", "body-2", definitions);
  assert.equal(selected?.variant.id, "cart-orange");
});

test("selector is deterministic for one actor and can diversify other actors", () => {
  const first = selectAsset("cart", "actor", "body-1", definitions);
  const repeat = selectAsset("cart", "actor", "body-1", definitions);
  assert.equal(first?.variant.id, repeat?.variant.id);
  const variants = new Set(["body-1", "body-2", "body-3", "body-4", "body-5"].map(seed => selectAsset("cart", "actor", seed, definitions)?.variant.id));
  assert.ok(variants.size > 1);
});

test("unknown semantic categories remain unresolved for teacher confirmation", () => {
  assert.equal(selectAsset("laboratory.spring", "actor", "mass", definitions), null);
  assert.equal(selectAsset("blue", "actor", "mass", definitions), null);
});
