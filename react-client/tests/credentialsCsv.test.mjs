import test from 'node:test';
import assert from 'node:assert/strict';
import { credentialsCsv } from '../src/features/school-import/credentialsCsv.ts';

// Decode CSV through a separate reader to check delivered credential values.
function readRows(csv) {
  const rows = [], row = [];
  let field = '', quoted = false;
  for (let i = 1; i < csv.length; i++) {
    const character = csv[i];
    if (quoted && character === '"' && csv[i + 1] === '"') { field += '"'; i++; }
    else if (character === '"') quoted = !quoted;
    else if (!quoted && character === ',') { row.push(field); field = ''; }
    else if (!quoted && character === '\r' && csv[i + 1] === '\n') { row.push(field); rows.push([...row]); row.length = 0; field = ''; i++; }
    else field += character;
  }
  return rows;
}

test('credential delivery preserves every password character including formula prefixes and whitespace', () => {
  const passwords = ['  Initial2026!  ', '=Initial2026!', '+Initial2026!', '@Initial2026!', '-Initial2026!', 'Quote"comma,2026!', 'Two\nlines2026!'];
  const accounts = passwords.map((initialPassword, index) => ({ fullName: '=Tên học sinh', dateOfBirth: '2010-09-15', email: `student${index}@example.test`, initialPassword, role: 'STUDENT', classCode: '10A1', schoolYear: '2026-2027' }));
  const decoded = readRows(credentialsCsv(accounts));
  assert.equal(decoded.length, accounts.length + 1);
  assert.deepEqual(decoded.slice(1).map(row => row[3]), passwords);
  assert.ok(decoded.slice(1).every(row => row[0] === "'=Tên học sinh"));
});
