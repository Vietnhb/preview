import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { renderToStaticMarkup } from "react-dom/server";
import ts from "typescript";

const require = createRequire(import.meta.url);
const catalog = new Map([
  ["cart-blue", { markup: '<svg xmlns="http://www.w3.org/2000/svg"><rect fill="blue" /></svg>' }],
  ["block-amber", { markup: '<svg xmlns="http://www.w3.org/2000/svg"><rect fill="orange" /></svg>' }],
]);
const source = readFileSync(new URL("../src/components/workspace/create-simulation/AssetSelectionReview.tsx", import.meta.url), "utf8");
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true },
}).outputText;
const module = { exports: {} };
new Function("require", "module", "exports", compiled)(specifier => {
  if (specifier.endsWith("SvgAssetManifest")) return { getSvgAsset: id => catalog.get(id) };
  if (specifier.endsWith(".css")) return {};
  return require(specifier);
}, module, module.exports);
const { AssetSelectionReview } = module.exports;
const choice = (entityId, assetId) => ({ targetId: entityId, entityId, entityLabel: `Vật ${entityId}`, assetId, assetLabel: assetId, match: "SUBSTITUTE", requiresConfirmation: true });
const selection = { id: "plan-1", status: "NEEDS_CONFIRMATION", choices: [choice("1", "cart-blue"), choice("2", "block-amber")] };
const render = (plan, props = {}) => AssetSelectionReview({ selection: plan, loading: false, onDecision() {}, onRetry() {}, onReset() {}, ...props });
function buttons(element) {
  if (!element || typeof element !== "object") return [];
  if (Array.isArray(element)) return element.flatMap(buttons);
  return [...(element.type === "button" ? [element] : []), ...buttons(element.props?.children)];
}

test("confirmation previews each selected SVG and sends only the explicit teacher decision", () => {
  let decision;
  const element = render(selection, { onDecision: value => { decision = value; } });
  const html = renderToStaticMarkup(element);
  for (const asset of catalog.values()) assert.ok(html.includes(encodeURIComponent(asset.markup)));
  assert.equal((html.match(/<img /g) ?? []).length, 2);
  const actions = buttons(element);
  assert.equal(actions[0].props.disabled, false);
  actions[0].props.onClick();
  assert.equal(decision, true);
  actions[1].props.onClick();
  assert.equal(decision, false);
});

test("a missing SVG or an in-flight request prevents approval", () => {
  const unknown = { ...selection, choices: [choice("1", "missing")] };
  assert.equal(buttons(render(unknown))[0].props.disabled, true);
  assert.ok(buttons(render(selection, { loading: true })).every(button => button.props.disabled));
});

test("rejected, unsupported and missing plans offer no run or approval action", () => {
  for (const plan of [null, { ...selection, status: "REJECTED" }, { ...selection, status: "UNSUPPORTED" }]) {
    let reset = false;
    const actions = buttons(render(plan, { onReset: () => { reset = true; } }));
    assert.equal(actions.length, 1);
    actions[0].props.onClick();
    assert.equal(reset, true);
  }
});

test("an approved plan retries execution without requesting another approval", () => {
  let retried = false;
  const actions = buttons(render({ ...selection, status: "READY" }, { onRetry: () => { retried = true; } }));
  assert.equal(actions.length, 2);
  actions[0].props.onClick();
  assert.equal(retried, true);
});
