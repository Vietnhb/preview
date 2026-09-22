import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { createAssetLookup } from "../src/simulation-assets/AssetLookup.ts";
import { SUPPORTED_EFFECTS } from "../src/simulation-scene/PrimitiveCapabilities.ts";

const { version, assets } = JSON.parse(readFileSync(new URL("../../backend/src/main/resources/assets/svg-catalog.json", import.meta.url), "utf8"));
const svgDirectory = new URL("../src/simulation-assets/svg/", import.meta.url);
const lookup = createAssetLookup(assets);

test("catalog entries point to actual SVGs with matching geometry and supported effects", () => {
  assert.equal(version, 1);
  assert.equal(new Set(assets.map(asset => asset.id)).size, assets.length);
  assert.deepEqual(new Set(assets.map(asset => asset.file)), new Set(readdirSync(svgDirectory).filter(name => name.endsWith(".svg"))));
  for (const asset of assets) {
    assert.match(asset.file, /^[a-z0-9-]+\.svg$/);
    assert.ok(asset.label && asset.description && asset.family);
    assert.ok(["actor", "prop"].includes(asset.kind));
    assert.ok(["center", "right", "topRight"].includes(asset.anchor));
    assert.ok(asset.effects.every(effect => SUPPORTED_EFFECTS.includes(effect)), asset.id);
    const svg = readFileSync(new URL(asset.file, svgDirectory), "utf8");
    const viewBox = /viewBox="([^"]+)"/.exec(svg)?.[1].split(/\s+/).map(Number);
    assert.deepEqual(asset.viewBox, viewBox, asset.id);
    assert.ok(asset.viewBox[2] > 0 && asset.viewBox[3] > 0);
  }
});

test("saved variants and historical aliases resolve to the exact same SVG", () => {
  for (const asset of assets) {
    assert.equal(lookup(asset.id, asset.kind), asset);
    assert.equal(lookup(asset.id, asset.kind === "actor" ? "prop" : "actor"), undefined);
    for (const alias of asset.aliases) assert.equal(lookup(alias, asset.kind), asset);
  }
});

test("descriptions, family hints and unknown IDs never select a substitute", () => {
  for (const hint of ["cart", "vehicle.cart.orange", "blue", "xe ô tô", "mass-block", "unavailable-object", " sport-blue "]) {
    assert.equal(lookup(hint), undefined, hint);
  }
});

test("conflicting aliases fail instead of rendering a different object", () => {
  assert.throws(() => createAssetLookup([
    { id: "one", kind: "actor", aliases: ["shared"] },
    { id: "two", kind: "actor", aliases: ["shared"] },
  ]), /Duplicate/);
  assert.throws(() => createAssetLookup([
    { id: "one", kind: "actor", aliases: ["two"] },
    { id: "two", kind: "actor", aliases: [] },
  ]), /Duplicate/);
});

test("vehicle-specific effects are supported only by SVGs representing cars", () => {
  for (const asset of assets.filter(asset => asset.effects.some(effect => effect.startsWith("vehicle.")))) {
    assert.equal(asset.family, "sport-car");
  }
  assert.equal(lookup("block-amber").effects.some(effect => effect.startsWith("vehicle.")), false);
});
