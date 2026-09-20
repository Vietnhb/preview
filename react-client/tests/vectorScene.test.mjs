import test from 'node:test';
import assert from 'node:assert/strict';
import { validateVectorScene } from '../src/simulation-scene/VectorScene.ts';
import { drawVectorScene } from '../src/simulation-renderer/VectorSceneRenderer.ts';
import { BindingResolver } from '../src/simulation-scene/BindingResolver.ts';
import { prepareSimulationData } from '../src/simulation-runtime/SimulationData.ts';

const check = value => typeof value === 'number' && Number.isFinite(value) || value === 'positions.x' ? undefined : 'invalid binding';

test('declarative geometry validates bindings and rejects malformed paths', () => {
  const scene = { viewBox: [0, 0, 100, 100], shapes: [{ kind: 'path', commands: [{ op: 'M', args: [0, 0] }, { op: 'L', args: ['positions.x', 10] }] }] };
  assert.deepEqual(validateVectorScene(scene, check), []);
  scene.shapes[0].commands[1].args = [1];
  assert.ok(validateVectorScene(scene, check).includes('invalid path command'));
  assert.ok(validateVectorScene({ viewBox: [0, 0, 0, 10], shapes: [] }, check).length);
});

test('nested groups animate from bindings and preserve viewport aspect ratio', () => {
  const calls = [];
  const ctx = new Proxy({}, { get: (_, key) => (...args) => calls.push([key, ...args]), set: () => true });
  const input = {
    frame: { ctx, width: 400, height: 200, runtime: { time: 2, index: 1 } },
    node: { properties: { vector: { viewBox: [0, 0, 100, 100], shapes: [
      { kind: 'group', x: 10, children: [{ kind: 'rect', x: 'positions.x', width: 5, height: 6, fill: 'red' }] },
    ] } } },
    resolver: { resolve: (binding, time) => binding === 'positions.x' ? time * 3 : binding },
  };
  drawVectorScene(input);
  assert.ok(calls.some(call => JSON.stringify(call) === JSON.stringify(['translate', 100, 0])));
  assert.ok(calls.some(call => JSON.stringify(call) === JSON.stringify(['scale', 2, 2])));
  assert.ok(calls.some(call => JSON.stringify(call) === JSON.stringify(['translate', 6, 0])));
  assert.equal(calls.filter(call => call[0] === 'save').length, calls.filter(call => call[0] === 'restore').length);
  calls.length = 0;
  input.frame.runtime.time = 4;
  drawVectorScene(input);
  assert.ok(calls.some(call => JSON.stringify(call) === JSON.stringify(['translate', 12, 0])));
});

test('resource limits reject excessively nested schema', () => {
  let shape = { kind: 'rect' };
  for (let i = 0; i < 40; i++) shape = { kind: 'group', children: [shape] };
  assert.ok(validateVectorScene({ viewBox: [0, 0, 100, 100], shapes: [shape] }, check).some(error => error.includes('limit')));
});

test('safe expressions derive visual coordinates from simulation series', () => {
  const simulation = {
    simulationId: 's', runId: 'r', specificationId: 'p', schemaId: 'x', valid: true, ready: true,
    time: [0, 1], positions: { x: [2, 4] }, velocities: {}, accelerations: {}, values: {}, parameters: {},
    visualization: { scene: 'x', controls: [], series: [] }, validation: { passed: true, tolerance: 0, checkpoints: [] }, elapsedMilliseconds: 0,
  };
  const resolver = new BindingResolver(prepareSimulationData(simulation));
  const expression = { source: 'expression', operator: 'clamp', args: [
    { source: 'expression', operator: 'multiply', args: ['positions.x', 3] }, 0, 10,
  ] };
  assert.equal(resolver.resolve(expression, 0, 0), 6);
  assert.equal(resolver.resolve(expression, 1, 1), 10);
});
