import test from "node:test";
import assert from "node:assert/strict";
import { validateVisualProgram } from "../src/matter-flow/visualCodeSafety.ts";

const program = {
  init: "return { elapsed: 0, markers: [{ x: 100, y: 120 }] };",
  step: "state.elapsed += dt; for (let i = 0; i < 1; i++) { state.markers[i].x += params.rate * dt; } state.elapsed++;",
  draw: "paint.circle(state.markers[0].x, state.markers[0].y, 8, '#63b7ff'); paint.text(100, 30, 'state', '#ffffff', 16);",
};

test("visual model accepts bounded state updates and primitive drawing", () => {
  assert.equal(validateVisualProgram(program, ["rate"]), null);
  const init = new Function("params", "width", "height", program.init);
  const step = new Function("state", "dt", "params", "width", "height", program.step);
  const state = init({ rate: 6 }, 960, 540);
  step(state, 0.5, { rate: 6 }, 960, 540);
  assert.equal(state.markers[0].x, 103);
});

test("visual model denies host access and dynamic execution", () => {
  for (const bad of [
    "fetch('https://example.com'); return { elapsed: 0 };",
    "document.body.textContent = 'x'; return { elapsed: 0 };",
    "return { value: ({}).constructor.constructor('return 1')() };",
    "return { value: params['rate'] };",
  ]) assert.notEqual(validateVisualProgram({ ...program, init: bad }, ["rate"]), null);
  assert.notEqual(validateVisualProgram({ ...program, step: "params.rate = 99;" }, ["rate"]), null);
  assert.notEqual(validateVisualProgram({ ...program, draw: "paint.constructor('x')();" }, ["rate"]), null);
});

test("visual code preserves lexical scopes and independent loops", () => {
  assert.equal(validateVisualProgram({...program,
    step:"state.elapsed=Math.random()+Math.log10(10)+Math.log2(2)+Math.cbrt(8)"
      + "+Math.sign(dt)+Math.trunc(dt)+Math.expm1(dt)+Math.log1p(dt);"},["rate"]),null);
  assert.equal(validateVisualProgram({...program,
    init:"if(params.rate>0){return {elapsed:0};}else{return {elapsed:1};}"},["rate"]),null);
  const candidate = {
    ...program,
    init: "const values=[]; for(let i=0;i<50;i++){values[i]=i;} "
      + "for(let i=0;i<50;i++){values[i]+=1;} const config={scale:1}; "
      + "config.scale=2; return {elapsed:0,values,config};",
    step: "if(state.elapsed>1){const value=2;state.elapsed=value;} "
      + "else {const value=1;state.elapsed=value;} "
      + "const values=state.values; for(let i=0;i<50;i++){values[i]+=dt;} "
      + "for(let i=0;i<50;i++){state.values[i]+=dt;}",
    draw: ";if(state.elapsed<0)return; "
      + "paint.text(100,30,state.elapsed.toFixed(2),'#ffffff'); "
      + "paint.text(100,60,(state.elapsed*2).toPrecision(3),'#ffffff');",
  };
  assert.equal(validateVisualProgram(candidate, ["rate"]), null);
  const state = new Function("params", "width", "height", candidate.init)({},960,540);
  new Function("state", "dt", "params", "width", "height", candidate.step)(state,0.5,{},960,540);
  assert.equal(state.values[49],51);
  assert.equal(state.config.scale,2);
  assert.notEqual(validateVisualProgram({...program,
    step:"if(state.elapsed>1){const hidden=2;}state.elapsed=hidden;"}, ["rate"]),null);
  assert.equal(validateVisualProgram({...program,
    step:"for(let i=0;i<50;i++){for(let j=0;j<50;j++){state.elapsed+=dt;}}"}, ["rate"]),null);
});

test("visual code accepts lexical helpers templates and owned array operations", () => {
  const candidate = {
    init: "function entry(x){return {value:x};} const copy=(values)=>values.slice(-2); "
      + "const values=[];values.push(entry(1),entry(2));values.unshift(entry(0)); "
      + "const trimmed=copy(values);trimmed.pop();trimmed.shift(); "
      + "return {elapsed:0,values,scale:1e100};",
    step: "const increment=function(value){return value+dt;}; "
      + "function addAt(i){state.values[i].value=increment(state.values[i].value);} "
      + "for(let i=1;i<state.values.length;i++){addAt(i);state.values[i-1].value+=dt;} "
      + "let remaining=2;while(remaining>0){remaining--;if(remaining===1)continue;state.elapsed++;} "
      + "do{state.elapsed++;break;}while(true); "
      + "state.values[state.values.length-1].value+=dt;",
    draw: "const label=(x)=>{paint.text(10,x,`Value=${state.values[0].value.toFixed(2)}`,'#fff');};label(20);",
  };
  assert.equal(validateVisualProgram(candidate,["rate"]),null);
  const state=new Function("params","width","height",candidate.init)({},960,540);
  new Function("state","dt","params","width","height",candidate.step)(state,0.5,{},960,540);
  assert.deepEqual(state.values.map((entry)=>entry.value),[0.5,2,3]);
  assert.equal(state.elapsed,2);
  assert.equal(validateVisualProgram({...program,step:""},["rate"]),null);
  assert.equal(validateVisualProgram({...program,
    step:"let index=0;index+=1;state.markers[index].x+=dt;"},["rate"]),null);
  // Every loop and helper entry is guarded by the worker's separate work budget.
  for(const step of ["while(true){}", "function recurse(){recurse();}recurse();",
    "for(let i=0;i<201;i++){state.elapsed+=dt;}"])
    assert.equal(validateVisualProgram({...program,step},["rate"]),null);
});

test("visual code accepts flat declared-data destructuring and numeric local loop bounds",()=>{
  const candidate={
    init:"const {rate:r}=params;const values=[];const N=Math.min(200,Math.floor(width)); "
      + "for(let i=0;i<=N;i++){values.push({x:i,y:r});}return {elapsed:0,values};",
    step:"const {elapsed:before,values:points}=state;const {rate}=params; "
      + "const N=Math.min(200,points.length-1);for(let i=N;i>=0;i--){points[i].y+=rate*dt;} "
      + "state.elapsed=before+dt;",
    draw:"const {x:pos,y}=state.values[0];paint.circle(pos,y,2,'#fff');",
  };
  assert.equal(validateVisualProgram(candidate,["rate"]),null);
  const state=new Function("params","width","height",candidate.init)({rate:2},960,540);
  new Function("state","dt","params","width","height",candidate.step)(state,0.5,{rate:2},960,540);
  assert.equal(state.values.length,201);
  assert.equal(state.values[200].y,3);
  for(const init of [
    "const {sin:fn}=Math;return {};",
    "const {rate:paint}=params;return {};", "const {rate=1}=params;return {};",
    "const {constructor:bad}={};return {};", "const {rate,...rest}=params;return {};",
    "const {rate:{nested}}=params;return {};",
  ])assert.notEqual(validateVisualProgram({...program,init},["rate"]),null,init);
  for(const draw of ["const {circle:fn}=paint;fn(1,2,3);", "const {prototype:p}=state;"])
    assert.notEqual(validateVisualProgram({...program,draw},["rate"]),null,draw);
});

test("visual code accepts bounded for-of over owned state arrays",()=>{
  const candidate={
    ...program,
    init:"return {elapsed:0,markers:[{x:10,y:20},{x:30,y:40}]};",
    step:"for(const marker of state.markers){marker.x+=dt;marker.y+=params.rate*dt;}state.elapsed+=dt;",
    draw:"for(const marker of state.markers){paint.circle(marker.x,marker.y,5,'#fff');}",
  };
  assert.equal(validateVisualProgram(candidate,["rate"]),null);
  const state=new Function("params","width","height",candidate.init)({},960,540);
  new Function("state","dt","params","width","height",candidate.step)(state,0.5,{rate:2},960,540);
  assert.deepEqual(state.markers.map((marker)=>[marker.x,marker.y]),[[10.5,21],[30.5,41]]);
  assert.notEqual(validateVisualProgram({...program,
    step:"for(const item of params.rate){state.elapsed+=item;}"},["rate"]),null);
  assert.notEqual(validateVisualProgram({...program,
    step:"for(const item of state.markers){item.constructor=1;}"},["rate"]),null);
});

test("visual helpers cannot capture or expose host capabilities", () => {
  for(const draw of [
    "function expose(){return paint;}const result=expose();",
    "function use(value){value.circle(1,2,3,'red');}use(paint);",
    "const host=Math.sin;function use(){return host(1);}use();",
    "function request(){fetch('https://example.com');}request();",
    "function helper(){return 1;}const captured=helper;",
    "const helper=()=>1;state.callback=helper;",
    "const helper=(paint)=>paint.circle(1,2,3,'red');helper(state);",
    "const helper=function Math(){return 1;};helper();",
    "const __visualWork=1;paint.text(1,2,'text');",
    "const helper=(__visualIndex)=>1;helper(1);",
    "const helper=(__visualValue)=>1;helper(1);",
    "const values=state.values;values.slice(Math.floor(1));",
    "state.values['constructor'];",
  ]) assert.notEqual(validateVisualProgram({...program,draw},["rate"]),null,draw);
});

test("undefined is a read-only primitive available to optional parameter comparisons",()=>{
  assert.equal(validateVisualProgram({...program,
    step:"state.elapsed=params.rate!==undefined?params.rate:1;"},["rate"]),null);
  const optional={...program,
    init:"const {extra:optional}=params;return {elapsed:optional===undefined?1:optional,markers:[]};",
    step:"state.elapsed=params.durationSeconds===undefined?state.elapsed:params.durationSeconds;"};
  assert.equal(validateVisualProgram(optional,["rate"]),null);
  const state=new Function("params","width","height",optional.init)({rate:2},960,540);
  assert.equal(state.elapsed,1);
  for(const step of ["params.extra=1;", "params.constructor;", "params.extra();", "params.extra.nested;"])
    assert.notEqual(validateVisualProgram({...program,step},["rate"]),null,step);
  for(const step of ["undefined=1;", "const undefined=1;state.elapsed=undefined;",
    "function helper(undefined){return undefined;}state.elapsed=helper(1);"])
    assert.notEqual(validateVisualProgram({...program,step},["rate"]),null,step);
});

test("visual code denies wrapper escape and mutation through host aliases", () => {
  for (const init of [
    "return {elapsed:0};}\nfetch('https://example.com');\nfunction extra(){",
    "const alias=params;alias.rate=2;return {elapsed:0};",
    "const holder={host:Math};holder.host.PI=2;return {elapsed:0};",
    "return {elapsed:0,method:Math.sin};",
  ]) assert.notEqual(validateVisualProgram({...program,init},["rate"]),null);
  for(const step of ["params.rate=2;","Math.PI=2;","const holder={};holder.constructor=1;",
    "for(let i=0;i<5;i++){i=0;state.elapsed+=dt;}"])
    assert.notEqual(validateVisualProgram({...program,step},["rate"]),null);
  assert.notEqual(validateVisualProgram({...program,
    draw:"paint.circle=state.elapsed;"},["rate"]),null);
  assert.notEqual(validateVisualProgram({...program,
    draw:"paint.text(100,30,state.elapsed.toFixed(101),'#ffffff');"},["rate"]),null);
});

test("visual code accepts SVG methods defs, svg and svgPath", () => {
  const svgProgram = {
    ...program,
    draw: `
      paint.background('#102030');
      paint.defs('<filter id="shadow"><feDropShadow dx="0" dy="6" stdDeviation="5"/></filter>');
      paint.svg('<g filter="url(#shadow)"><path d="M 0 30 L 100 30 Z" fill="#3b82f6"/></g>', 100, 200);
      paint.svgPath('M 10 20 L 30 40 Z', '#3b82f6', '#ffffff', 2, '#000000', 5, 3);
    `,
  };
  assert.equal(validateVisualProgram(svgProgram, ["rate"]), null);
});
