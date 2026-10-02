import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { sampleTimeline } from '../src/features/simulation/model/svgScene.ts';

const source = readFileSync(new URL('../src/features/simulation/components/SvgPixiScene.tsx', import.meta.url), 'utf8');
const worker = source.match(/const WORKER = String.raw`([\s\S]*?)`;/)[1];
const timeline = {durationSeconds: 1, frames: [
  {t: 0, values: {'arbitrary.position': 0}}, {t: 1, values: {'arbitrary.position': 10}},
]};
// Minimal kit stand-in: standardScene() registers trusted internals with the runtime, like the real kit.
const KIT = `const createStageKit = (PIXI, app, host) => ({palette: () => ({ink: "#123456"}), format: (value, unit) => value + " " + unit,
standardScene() {
  const calls = globalThis.kitCalls = {begin: 0, finish: 0};
  host.register({root: {}, begin() { calls.begin++; }, finish() { calls.finish++; }, refresh() {},
    verify: (run, times) => { for (const time of times) run(time); return globalThis.kitIssues || []; }});
  return {update() {}};
}});`;
function runtime() {
  const messages = []; let tick;
  const app = {screen: {width: 600, height: 400}, stage: {children: [], addChild(item) {this.children.push(item);}},
    init: async () => {}, render() {}, renderer: {resize(width, height) {app.screen = {width, height};}}};
  const context = vm.createContext({Proxy, PIXI: {Application: function() {return app;}, Texture: {from: bitmap => bitmap}},
    performance: {now: () => 0}, postMessage: message => messages.push(message), close() {},
    setInterval: callback => {tick = callback;}});
  vm.runInContext(sampleTimeline.toString() + '\n' + KIT + '\n' + vm.runInNewContext('String.raw`' + worker + '`'), context);
  return {app, messages, context, send: data => context.onmessage({data}), tick: () => tick()};
}
test('runs generated PixiJS functions without a predefined object or scene inventory', async () => {
  const host = runtime();
  await host.send({type: 'start', timeline, parameters: {}, code: `async function(PIXI, app, api) { api.kit.standardScene();
    const visuals = []; for (let i = 0; i < 7; i++) {const item = {i}; visuals.push(item); app.stage.addChild(item);}
    return {update(frame) { for (const item of visuals) item.x = frame.fields['arbitrary.position'] + item.i; }};
  }`});
  await host.send({type: 'seek', t: 0.5}); host.tick();
  assert.equal(host.app.stage.children.length, 7);
  assert.deepEqual(host.app.stage.children.map(item => item.x), [5,6,7,8,9,10,11]);
  assert.equal(host.messages.some(item => item.type === 'ready'), true);
});

test('generated code can use top-level palette and a detached format alias', async () => {
  const host = runtime();
  await host.send({type: 'start', timeline, parameters: {}, code: `async function(PIXI, app, api) { api.kit.standardScene();
    const item = {colour: api.palette().ink}; app.stage.addChild(item);
    const fmt = api.format;
    return {update(frame) { item.text = fmt(frame.fields['arbitrary.position'], 'm'); }};
  }`});
  assert.equal(host.messages.some(item => item.type === 'ready'), true);
  assert.equal(host.app.stage.children[0].colour, '#123456');
  assert.equal(host.app.stage.children[0].text, '0 m');
  await host.send({type: 'seek', t: 0.5}); host.tick();
  assert.equal(host.app.stage.children[0].text, '5 m');
  assert.equal(host.messages.some(item => item.type === 'error'), false);
});
test('parameter edits replace backend fields and ranges without regenerating visual code', async () => {
  const host = runtime();
  await host.send({type: 'start', timeline, parameters: {}, code: `async function(PIXI, app, api) { api.kit.standardScene();
    const item = {}; app.stage.addChild(item);
    return {update(frame) {item.x = frame.fields['arbitrary.position']; item.range = api.getFieldRanges()['arbitrary.position'].max;},
      resize() {item.layoutRange = api.getFieldRanges()['arbitrary.position'].max;}};
  }`});
  const revised = {durationSeconds: 1, frames: [{t:0,values:{'arbitrary.position':20}}, {t:1,values:{'arbitrary.position':40}}]};
  await host.send({type:'data', timeline:revised, parameters:{any:3}}); host.tick();
  assert.equal(host.app.stage.children[0].x, 20);
  assert.equal(host.app.stage.children[0].range, 40);
  assert.equal(host.app.stage.children[0].layoutRange, 40);
});

test('generated scenes can convert HSV colours through the legacy Pixi utils API', async () => {
  const host = runtime();
  await host.send({type: 'start', timeline, parameters: {}, code: `async function(PIXI, app, api) { api.kit.standardScene();
    const marker = {}; app.stage.addChild(marker);
    return {update(frame) {
      marker.colour = PIXI.utils.rgb2hex(PIXI.utils.hsv2rgb([frame.t / 3, 1, 1]));
      marker.white = PIXI.utils.rgb2hex(PIXI.utils.hsv2rgb([0, 0, 1]));
      marker.blue = PIXI.utils.rgb2hex(PIXI.utils.hsv2rgb([-1 / 3, 1, 1]));
    }};
  }`});
  assert.equal(host.messages.some(item => item.type === 'ready'), true);
  assert.equal(host.app.stage.children[0].colour, 0xff0000);
  assert.equal(host.app.stage.children[0].white, 0xffffff);
  assert.equal(host.app.stage.children[0].blue, 0x0000ff);
  await host.send({type: 'seek', t: 1}); host.tick();
  assert.equal(host.app.stage.children[0].colour, 0x00ff00);
  assert.equal(host.messages.some(item => item.type === 'error'), false);
});
test('reports invalid generated lifecycle as a rendering error, not physics verification', async () => {
  const host = runtime();
  await host.send({type:'start',timeline,parameters:{},code:'async function() { return {}; }'});
  assert.equal(host.messages.at(-1).type, 'error');
  assert.match(host.messages.at(-1).message, /update/);
  assert.equal(host.messages.some(item => item.type === 'VERIFIED'), false);
});
test('runtime isolates code with an opaque iframe, blocked network and worker watchdog', () => {
  assert.match(source, /sandbox="allow-scripts"/);
  assert.doesNotMatch(source, /allow-same-origin/);
  assert.match(source, /connect-src/);
  assert.match(source, /worker\?\.terminate\(\)/);
  assert.match(source, /lastHeartbeat > limits.timeoutMs/);
});
test('unknown solver field keys fail loudly instead of silently drawing nothing', async () => {
  const host = runtime();
  await host.send({type: 'start', timeline, parameters: {}, code: `async function(PIXI, app, api) { api.kit.standardScene();
    return {update(frame) { app.stage.x = frame.fields['motion1.position']; }};
  }`});
  assert.equal(host.messages.at(-1).type, 'error');
  assert.match(host.messages.at(-1).message, /Unknown solver field "motion1.position".*arbitrary.position/);
});
test('playback stops at the end of the backend timeline and restarts on play', async () => {
  const host = runtime();
  await host.send({type: 'start', timeline, parameters: {}, code: `async function(PIXI, app, api) { api.kit.standardScene();
    return {update(frame) { app.stage.t = frame.t; }};
  }`});
  await host.send({type: 'seek', t: 0.5}); host.tick();
  assert.equal(host.messages.some(item => item.type === 'ended'), false);
  await host.send({type: 'seek', t: 1}); host.tick();
  assert.equal(host.messages.some(item => item.type === 'ended'), true);
  await host.send({type: 'play', playing: true}); host.tick();
  assert.equal(host.app.stage.t, 0);
});
test('programs that draw their own stage are rejected: nothing on it can be cross-checked with the solver', async () => {
  const host = runtime();
  await host.send({type: 'start', timeline, parameters: {}, code: `async function(PIXI, app, api) {
    const item = {}; app.stage.addChild(item);
    return {update(frame) { item.x = frame.fields['arbitrary.position']; }};
  }`});
  assert.equal(host.messages.at(-1).type, 'error');
  assert.match(host.messages.at(-1).message, /Visual cross-check/);
  assert.match(host.messages.at(-1).message, /illustratedScene/);
  assert.equal(host.messages.some(item => item.type === 'ready'), false);
});
test('visual cross-check problems reported by the kit block the program before it is shown', async () => {
  const host = runtime();
  host.context.kitIssues = ['Graphics near (40, 60) moves by 120 px between t = 0 s and t = 0.5 s'];
  await host.send({type: 'start', timeline, parameters: {}, code: `async function(PIXI, app, api) { api.kit.standardScene();
    return {update() {}};
  }`});
  assert.equal(host.messages.at(-1).type, 'error');
  assert.match(host.messages.at(-1).message, /cross-check against the verified solver data failed[\s\S]*moves by 120 px/);
});
test('the kit re-applies solver-bound placements after every generated update', async () => {
  const host = runtime();
  await host.send({type: 'start', timeline, parameters: {}, code: `async function(PIXI, app, api) { api.kit.standardScene();
    return {update() {}};
  }`});
  assert.equal(host.messages.at(-1).type, 'ready');
  const before = host.context.kitCalls.finish;
  host.tick(); host.tick();
  assert.equal(host.context.kitCalls.finish, before + 2);
  assert.equal(host.context.kitCalls.begin, host.context.kitCalls.finish);
});
test('a recomputed solver timeline (parameter edit) is cross-checked again before it is shown', async () => {
  const host = runtime();
  await host.send({type: 'start', timeline, parameters: {}, code: `async function(PIXI, app, api) { api.kit.standardScene();
    return {update() {}};
  }`});
  assert.equal(host.messages.at(-1).type, 'ready');
  host.context.kitIssues = ['p1: drawn at p1.position = 3 but the solver gives 4 at t = 0.5 s.'];
  const revised = {durationSeconds: 1, frames: [{t: 0, values: {'arbitrary.position': 20}}, {t: 1, values: {'arbitrary.position': 40}}]};
  await host.send({type: 'data', timeline: revised, parameters: {any: 3}});
  assert.equal(host.messages.at(-1).type, 'error');
  assert.match(host.messages.at(-1).message, /cross-check against the verified solver data failed[\s\S]*solver gives 4/);
});
