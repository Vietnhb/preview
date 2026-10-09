import test from 'node:test';
import assert from 'node:assert/strict';
import { presentedFields } from '../src/features/simulation/model/scenePresentation.ts';

const scene = { fields: {
  'step.power': { label: 'Power' }, 'step.current': { label: 'Current' },
  'step.voltage': { label: 'Voltage' }, 'branch.current': { label: 'Branch current' },
}, participants: [], durationSeconds: 1 };

test('focus follows the fields shown in the apparatus, not solver output order', () => {
  assert.deepEqual(presentedFields(scene, { instruments: [{ field: 'step.voltage', label: 'Voltage drop' }],
    annotations: [{ field: 'branch.current', label: 'Branch' }] }), [
    { key: 'step.voltage', label: 'Voltage drop' }, { key: 'branch.current', label: 'Branch' },
  ]);
});

test('legacy scenes, empty artwork, repeated and unknown bindings do not fabricate readings', () => {
  assert.deepEqual(presentedFields(scene, null), []);
  assert.deepEqual(presentedFields(scene, { instruments: [null, { field: '' }, { field: 'invented' },
    { field: 'step.voltage' }], annotations: [{ field: 'step.voltage', label: 'Duplicate' }] }),
  [{ key: 'step.voltage', label: 'Voltage' }]);
});
