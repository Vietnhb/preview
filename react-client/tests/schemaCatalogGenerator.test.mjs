import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const generatorPath = path.join(repositoryRoot, "scripts", "generate-schema-catalog.mjs");

test("generated schema catalog has no drift from topic source modules", () => {
  const result = spawnSync(process.execPath, [generatorPath, "--check"], {
    cwd: repositoryRoot,
    encoding: "utf8",
  });

  assert.equal(result.error, undefined, result.error?.message);
  assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
  assert.match(result.stdout, /Schema catalog is current \(\d+ entries\)\./);
});
