import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { parseFormula, formatFormula, FormulaError, capabilityProblems, capabilityMath, newCapability, withPhase } from '../src/features/reviewer/model/equationAst.ts';

function evaluate(ast, env) {
  if (typeof ast === 'number') return ast;
  if (typeof ast === 'string') return env[ast];
  const [op, a, b] = ast; const x = evaluate(a, env); const y = b === undefined ? 0 : evaluate(b, env);
  return { add: x + y, sub: x - y, mul: x * y, div: x / y, pow: Math.pow(x, y), neg: -x, sin: Math.sin(x), cos: Math.cos(x), sqrt: Math.sqrt(x), exp: Math.exp(x), abs: Math.abs(x), log: Math.log(x), asin: Math.asin(x), acos: Math.acos(x), atan: Math.atan(x) }[op];
}

test('parses precedence, associativity and unary minus', () => {
  assert.deepEqual(parseFormula('a + b * c'), ['add', 'a', ['mul', 'b', 'c']]);
  assert.deepEqual(parseFormula('a - b - c'), ['sub', ['sub', 'a', 'b'], 'c']);
  assert.deepEqual(parseFormula('a / b / c'), ['div', ['div', 'a', 'b'], 'c']);
  assert.deepEqual(parseFormula('-x^2'), ['neg', ['pow', 'x', 2]]);
  assert.deepEqual(parseFormula('a^b^c'), ['pow', 'a', ['pow', 'b', 'c']]);
  assert.deepEqual(parseFormula('2 * -3'), ['mul', 2, -3]);
  assert.deepEqual(parseFormula('1e-9 + .5'), ['add', 1e-9, 0.5]);
  assert.deepEqual(parseFormula('E + (U0 - E) * exp(-t / (R * C))'), ['add', 'E', ['mul', ['sub', 'U0', 'E'], ['exp', ['div', ['neg', 't'], ['mul', 'R', 'C']]]]]);
});

test('formats with only the brackets that are needed', () => {
  assert.equal(formatFormula(['div', ['sub', 'E', ['div', 'q', 'C']], 'R']), '(E - q / C) / R');
  assert.equal(formatFormula(['sub', 'a', ['sub', 'b', 'c']]), 'a - (b - c)');
  assert.equal(formatFormula(['pow', ['neg', 'x'], 2]), '(-x)^2');
  assert.equal(formatFormula(['pow', -2, 2]), '(-2)^2');
  assert.equal(formatFormula(['neg', ['add', 'a', 'b']]), '-(a + b)');
  assert.equal(formatFormula(['mul', 'a', ['neg', 'b']]), 'a * -b');
});

test('rejects bad input with a readable message', () => {
  for (const bad of ['', 'a +', '(a', 'a b', 'tan(x)', 'a $ b', 'sin x', '2 *']) assert.throws(() => parseFormula(bad), FormulaError, bad);
});

test('new capability is flagged until it has formulas, then passes', () => {
  let capability = newCapability(['body']);
  assert.ok(capabilityProblems(capability).length > 0);
  capability = { ...capability, capabilityId: 'decay', canonicalInputs: [{ key: 'n0', unit: '1' }, { key: 'k', unit: '1/s' }], outputs: [{ key: 'n', unit: '1' }] };
  capability = withPhase(capability, 'initial', { n: 'n0' });
  capability = withPhase(capability, 'rates', { n: parseFormula('-k * n') });
  capability = withPhase(capability, 'outputs', { n: 'n' });
  assert.deepEqual(capabilityProblems(capability), []);
  capability = withPhase(capability, 'closedForm', { n: parseFormula('n0 * exp(-k * t)') });
  assert.equal(capability.validation.strategy, 'closed_form_checkpoints');
  assert.equal(capability.execution.closedForm.solverId, 'schema_ast_reference');
  assert.deepEqual(capabilityProblems(capability), []);
  capability = withPhase(capability, 'closedForm', {});
  assert.equal(capability.execution.closedForm, null);
  assert.equal(capability.validation.strategy, 'step_refinement');
  assert.ok(capabilityProblems(withPhase(capability, 'rates', { n: parseFormula('-k * m') })).some(p => p.includes('“m”')));
});

const LIBRARY = process.env.CAPABILITY_LIBRARY;
test('every shipped capability round-trips and has no reported problem', { skip: !LIBRARY || !existsSync(LIBRARY) }, () => {
  const files = readdirSync(LIBRARY, { recursive: true }).filter(name => String(name).endsWith('.json'));
  assert.ok(files.length > 0);
  let formulas = 0;
  for (const file of files) {
    const capability = JSON.parse(readFileSync(join(LIBRARY, String(file)), 'utf8'));
    assert.deepEqual(capabilityProblems(capability), [], String(file));
    for (const [phase, map] of Object.entries(capabilityMath(capability))) for (const [name, ast] of Object.entries(map)) {
      const reparsed = parseFormula(formatFormula(ast));
      const env = new Proxy({}, { get: (_, key) => 0.37 + String(key).length * 0.11 });
      const before = evaluate(ast, env), after = evaluate(reparsed, env);
      assert.ok(Object.is(before, after) || Math.abs(before - after) <= 1e-12 * Math.abs(before), `${file} ${phase}.${name}`);
      formulas++;
    }
  }
  console.log(`checked ${files.length} capabilities, ${formulas} formulas`);
});
