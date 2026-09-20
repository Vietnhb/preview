import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
// Recursively scan production and test Java source so fixtures cannot preserve mojibake.
const sourceRoots = [
  path.join(repositoryRoot, 'backend', 'src', 'main', 'java'),
  path.join(repositoryRoot, 'backend', 'src', 'test', 'java'),
];
// Match common UTF-8-as-Windows-1252/Latin-1 fragments, including double encoding,
// while allowing valid Vietnamese text.
const corruptionMarker = /(?:\u00c3[\u0080-\u00bf\u0192\u2018-\u203a]|\u00c4[\u0080-\u00bf\u2018-\u203a]|\u00c6\u00b0|\u00e1[\u00ba\u00bb][\u0080-\u00bf\u2018-\u203a]|\u00e2(?:\u20ac[\u2018-\u2122]|\u201a\u00ac)|\u00f0\u0178[\u0080-\u00bf\u2018-\u203a]|\u00ef\u00bf\u00bd|\uFFFD)/;

const corruptedSamples = [
  'Ch\u00c6\u00b0a x\u00c3\u00a1c \u00c4\u2018\u00e1\u00bb\u2039nh',
  'Vui l\u00c3\u00b2ng cung c\u1ea5p gi\u00e1 tr\u1ecb',
  '\u00e2\u20ac\u2122',
  '\u00c3\u0192\u00c2',
  '\u00ef\u00bf\u00bd',
  '\uFFFD',
];
const validSamples = [
  'Ch\u01b0a x\u00e1c \u0111\u1ecbnh \u0111\u1ea1i l\u01b0\u1ee3ng',
  'Vui l\u00f2ng cung c\u1ea5p gi\u00e1 tr\u1ecb v\u00e0 \u0111\u01a1n v\u1ecb',
  '\u03bb v\u00e0 \u0394',
];
if (corruptedSamples.some((sample) => !corruptionMarker.test(sample))
    || validSamples.some((sample) => corruptionMarker.test(sample))) {
  throw new Error('Encoding detector self-check failed');
}

async function javaFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) return javaFiles(entryPath);
    return entry.isFile() && entry.name.endsWith('.java') ? [entryPath] : [];
  }));
  return nested.flat();
}

const failures = [];
for (const file of (await Promise.all(sourceRoots.map(javaFiles))).flat()) {
  const contents = await readFile(file, 'utf8');
  for (const [index, line] of contents.split(/\r?\n/).entries()) {
    if (corruptionMarker.test(line)) {
      failures.push(`${path.relative(repositoryRoot, file)}:${index + 1}: ${line.trim()}`);
    }
  }
}

if (failures.length > 0) {
  console.error('Possible mojibake found in backend Java source or tests:');
  console.error(failures.join('\n'));
  process.exitCode = 1;
} else {
  console.log('Backend Java source and test encoding check passed.');
}
