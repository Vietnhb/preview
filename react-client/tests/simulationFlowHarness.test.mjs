import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { parse } from "acorn";

test("simulation flow browser E2E harness traces API and render stages", async () => {
  const html = await readFile(new URL("./simulationFlow.browser.html", import.meta.url), "utf8");
  const match = html.match(/<script type="module">([\s\S]*?)<\/script>/);
  assert.ok(match, "browser harness must contain a module script");
  parse(match[1], { ecmaVersion: 2022, sourceType: "module" });
  for (const stage of ["normalizeMatterText", "confirmMatterInput", "confirmMatterExplanation", "VisualSandbox"]) {
    assert.match(match[1], new RegExp(`\\b${stage}\\b`), `missing ${stage} stage`);
  }
  assert.match(match[1], /errorDetails/);
  assert.match(match[1], /visual-safety-and-render/);
});

test("simulation surface inherits the workspace light or dark theme", async () => {
  const css = await readFile(new URL("../src/styles/matter-pipeline.css", import.meta.url), "utf8");
  const visualSandbox = await readFile(new URL("../src/matter-flow/VisualSandbox.tsx", import.meta.url), "utf8");
  assert.match(css, /--simulation-surface:/);
  assert.match(css, /data-theme-effective="dark"\]\s+\.matter-sandbox/);
  assert.match(css, /background:\s*var\(--simulation-surface\)/);
  assert.match(visualSandbox, /background:transparent/);
  assert.match(visualSandbox, /hexColor\(color, 'transparent'\)/);
});
