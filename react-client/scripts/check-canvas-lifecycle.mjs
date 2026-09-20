import assert from 'node:assert/strict';
import { build } from 'esbuild';

// Execute the component's effect setup/cleanup/setup sequence with a controlled
// RAF queue. This isolates the StrictMode regression without a browser or API.
const effects = [];
const draws = [];
const queue = new Map();
let nextId = 0;
globalThis.requestAnimationFrame = callback => { queue.set(++nextId, callback); return nextId; };
globalThis.cancelAnimationFrame = id => queue.delete(id);
globalThis.document = { documentElement: { dataset: {} } };
globalThis.MutationObserver = class { observe() {} disconnect() {} };
globalThis.ResizeObserver = class { observe() {} disconnect() {} };
globalThis.__canvasTest = {
  effects, draws,
  useRef: value => ({ current: value }),
  useMemo: fn => fn(),
  useCallback: fn => fn,
  useState: value => [typeof value === 'function' ? value() : value, () => {}],
  useEffect: fn => effects.push(fn),
  jsx: (type, props) => {
    if (props.ref) props.ref.current = type === 'canvas'
      ? { width: 0, height: 0, getContext: () => ({ setTransform() {} }) }
      : { getBoundingClientRect: () => ({ width: 900, height: 420 }) };
    return { type, props };
  },
};
const result = await build({
  entryPoints: ['src/components/simulation-canvas/CanvasPhysicsScene.tsx'],
  bundle: true, write: false, format: 'esm', platform: 'node', jsx: 'automatic',
  plugins: [{ name: 'lifecycle-test', setup(builder) {
    builder.onResolve({ filter: /^react(?:\/jsx-runtime)?$/ }, args => ({ path: args.path, namespace: 'test' }));
    builder.onLoad({ filter: /.*/, namespace: 'test' }, () => ({ contents:
      'export const {useRef,useMemo,useCallback,useState,useEffect,jsx}=globalThis.__canvasTest; export const jsxs=jsx;' }));
    builder.onLoad({ filter: /simulation-renderer[\\/]CanvasRenderer\.ts$/ }, () => ({ contents:
      'export const createCanvasRendererCache=()=>({}); export class CanvasRenderer {render(frame){globalThis.__canvasTest.draws.push(frame);}}' }));
  } }],
});
const { default: Canvas } = await import(`data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString('base64')}`);
Canvas({ simulation: {
  simulationId: 'test', time: [0, 1], positions: { x: [0, 1] },
  velocities: {}, accelerations: {}, values: {}, parameters: {},
  visualization: { scene: 'motion' },
}, index: 0, playing: false, overlays: { grid: true, trajectory: true, velocity: false, acceleration: false } });
let cleanup = effects.map(setup => setup());
cleanup.forEach(fn => fn?.());
assert.equal(queue.size, 0, 'cleanup must cancel pending frames');
cleanup = effects.map(setup => setup());
assert.ok(queue.size > 0, 'effect replay must schedule a fresh frame without Play');
draws.length = 0;
const pending = [...queue.values()]; queue.clear();
pending.forEach(callback => callback(performance.now()));
assert.ok(draws.length > 0, 'paused canvas must redraw after effect replay');
assert.equal(draws.at(-1).runtime.time, 0);
cleanup.forEach(fn => fn?.());
assert.equal(queue.size, 0);
console.log('PASS: paused canvas redraws after setup/cleanup/setup; no RAF remains after cleanup.');
