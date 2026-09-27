import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { sampleTimeline } from '../src/simulation/svgScene.ts';

const source = readFileSync(new URL('../src/simulation/SvgPixiScene.tsx', import.meta.url), 'utf8');
const worker = source.match(/const WORKER = String.raw`([\s\S]*?)`;/)[1];
const timeline = {durationSeconds: 1, frames: [
  {t: 0, values: {'arbitrary.position': 0}}, {t: 1, values: {'arbitrary.position': 10}},
]};
function runtime() {
  const messages = []; let tick;
  const app = {screen: {width: 600, height: 400}, stage: {children: [], addChild(item) {this.children.push(item);}},
    init: async () => {}, render() {}, renderer: {resize(width, height) {app.screen = {width, height};}}};
  const context = vm.createContext({Proxy, PIXI: {Application: function() {return app;}, Texture: {from: bitmap => bitmap}},
    performance: {now: () => 0}, postMessage: message => messages.push(message), close() {},
    setInterval: callback => {tick = callback;}});
  vm.runInContext(sampleTimeline.toString() + '\nconst createStageKit = () => ({});\n' + vm.runInNewContext('String.raw`' + worker + '`'), context);
  return {app, messages, send: data => context.onmessage({data}), tick: () => tick()};
}
test('runs generated PixiJS functions without a predefined object or scene inventory', async () => {
  const host = runtime();
  await host.send({type: 'start', timeline, parameters: {}, code: `async function(PIXI, app, api) {
    const visuals = []; for (let i = 0; i < 7; i++) {const item = {i}; visuals.push(item); app.stage.addChild(item);}
    return {update(frame) { for (const item of visuals) item.x = frame.fields['arbitrary.position'] + item.i; }};
  }`});
  await host.send({type: 'seek', t: 0.5}); host.tick();
  assert.equal(host.app.stage.children.length, 7);
  assert.deepEqual(host.app.stage.children.map(item => item.x), [5,6,7,8,9,10,11]);
  assert.equal(host.messages.some(item => item.type === 'ready'), true);
});
test('parameter edits replace backend fields and ranges without regenerating visual code', async () => {
  const host = runtime();
  await host.send({type: 'start', timeline, parameters: {}, code: `async function(PIXI, app, api) {
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
  await host.send({type: 'start', timeline, parameters: {}, code: `async function(PIXI, app, api) {
    return {update(frame) { app.stage.x = frame.fields['motion1.position']; }};
  }`});
  assert.equal(host.messages.at(-1).type, 'error');
  assert.match(host.messages.at(-1).message, /Unknown solver field "motion1.position".*arbitrary.position/);
});
test('playback stops at the end of the backend timeline and restarts on play', async () => {
  const host = runtime();
  await host.send({type: 'start', timeline, parameters: {}, code: `async function(PIXI, app, api) {
    return {update(frame) { app.stage.t = frame.t; }};
  }`});
  await host.send({type: 'seek', t: 0.5}); host.tick();
  assert.equal(host.messages.some(item => item.type === 'ended'), false);
  await host.send({type: 'seek', t: 1}); host.tick();
  assert.equal(host.messages.some(item => item.type === 'ended'), true);
  await host.send({type: 'play', playing: true}); host.tick();
  assert.equal(host.app.stage.t, 0);
});
