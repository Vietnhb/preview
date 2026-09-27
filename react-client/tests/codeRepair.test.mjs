import test from "node:test";
import assert from "node:assert/strict";
import { repairGeneratedCode, diagnoseGeneratedCode } from "../src/simulation/codeRepair.ts";

const compiles = code => { try { new Function('"use strict"; return (' + code + "\n);"); return true; } catch { return false; } };

test("await inside a non-async callback is made async; reassigned const becomes let", () => {
  const code = `async function(PIXI,app,api){
  const base=api.kit.standardScene({bodies:false});
  base.backdrop(layout=>{
    const svg=\`<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 \${layout.width} \${layout.height}'>\`;
    for(let i=1;i<=5;i++){ svg+=\`<circle cx='\${i*10}' cy='5' r='\${layout.theme==='DARK'?8:9}'/>\`; }
    return svg+\`</svg>\`;
  });
  api.kit.participants().forEach((p,i)=>{
    base.attach(p.id,new PIXI.Sprite(await api.svgTexture(art(i))),{rotate:'link'});
  });
  const obj={a:1}; obj.a=2;
  return {update(frame){base.update(frame);}};
}`;
  assert.equal(compiles(code), false);
  const { code: fixed, fixes } = repairGeneratedCode(code);
  assert.equal(compiles(fixed), true, fixed);
  assert.match(fixed, /forEach\(async \(p,i\)=>/);
  assert.match(fixed, /let svg=/);
  assert.match(fixed, /const obj=/);
  assert.equal(fixes.length, 2);
});
test("valid code is left untouched", () => {
  const code = `async function(PIXI, app, api) { const base = api.kit.standardScene(); for (const p of api.kit.participants()) base.attach(p.id, new PIXI.Sprite(await api.svgTexture('<svg/>'))); return { update(f) { base.update(f); } }; }`;
  assert.deepEqual(repairGeneratedCode(code), { code, fixes: [] });
});
test("methods, function expressions and nested templates are recognised", () => {
  const code = "async function(PIXI, app, api) { const o = { load(x) { return await x; } }; const f = function named() { await 1; }; const s = `a${`b${1}`}c`; return { update() {} }; }";
  const { code: fixed } = repairGeneratedCode(code);
  assert.match(fixed, /async load\(x\)/);
  assert.match(fixed, /async function named/);
  assert.equal(compiles(fixed), true);
});
test("diagnostics point at old Pixi API, line comments and unbalanced delimiters", () => {
  const issues = diagnoseGeneratedCode("async function(){\n g.beginFill(1); // note\n return {update(){}\n}");
  assert.ok(issues.some(i => i.line === 2 && /v7/.test(i.message)));
  assert.ok(issues.some(i => i.line === 2 && /line comment/.test(i.message)));
  assert.ok(issues.some(i => /never closed|unbalanced/.test(i.message)));
});
