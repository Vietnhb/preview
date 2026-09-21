#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const root = path.resolve("backend/src/main/resources/schemas");
const write = process.argv.includes("--write");
const verbose = process.argv.includes("--verbose");
const files = [];

function collect(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) collect(file);
    else if (entry.name.endsWith(".json")) files.push(file);
  }
}

collect(root);

const cp1252 = {
  0x20ac: 0x80, 0x201a: 0x82, 0x192: 0x83, 0x201e: 0x84, 0x2026: 0x85,
  0x2020: 0x86, 0x2021: 0x87, 0x2c6: 0x88, 0x2030: 0x89, 0x160: 0x8a,
  0x2039: 0x8b, 0x152: 0x8c, 0x17d: 0x8e, 0x2018: 0x91, 0x2019: 0x92,
  0x201c: 0x93, 0x201d: 0x94, 0x2022: 0x95, 0x2013: 0x96, 0x2014: 0x97,
  0x2dc: 0x98, 0x2122: 0x99, 0x161: 0x9a, 0x203a: 0x9b, 0x153: 0x9c,
  0x17e: 0x9e, 0x178: 0x9f
};

const suspicious = /(?:[ÃÂÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞß][^\x00-\x7f]|[áàâãäåæçèéêëìíîïðñòóôõö÷øùúûüýþÿ][º»¼½¾µ][^\x00-\x7f]?|â[‚€™œ¦†‡ˆ‰Š‹ŒŽ“”•–—˜š›œžŸ][^\x00-\x7f]?|ï¿½|�)/gu;
const score = value => (value.match(suspicious) ?? []).length;

function repairOnce(value) {
  const bytes = [];
  for (const character of value) {
    const codePoint = character.codePointAt(0);
    if (codePoint <= 0xff) bytes.push(codePoint);
    else if (cp1252[codePoint] !== undefined) bytes.push(cp1252[codePoint]);
    else return null;
  }
  const candidate = Buffer.from(bytes).toString("utf8");
  return score(candidate) < score(value) ? candidate : null;
}

function repair(value) {
  let result = value;
  for (let attempt = 0; attempt < 4; attempt += 1) {
    let changed = false;
    result = result.replace(suspicious, segment => {
      const repaired = repairOnce(segment);
      if (repaired) {
        changed = true;
        return repaired;
      }
      return segment;
    });
    if (!changed) break;
  }
  return result;
}

function collectReplacements(value, replacements) {
  if (typeof value === "string") {
    const repaired = repair(value);
    if (repaired !== value) replacements.set(value, repaired);
    return;
  }
  if (Array.isArray(value)) {
    value.forEach(item => collectReplacements(item, replacements));
    return;
  }
  if (value && typeof value === "object") {
    Object.values(value).forEach(item => collectReplacements(item, replacements));
  }
}

for (const file of files) {
  const original = fs.readFileSync(file, "utf8");
  const parsed = JSON.parse(original);
  const replacements = new Map();
  collectReplacements(parsed, replacements);
  let repaired = original;
  for (const [from, to] of replacements) repaired = repaired.split(from).join(to);
  if (file.endsWith(`${path.sep}catalog.json`)) {
    repaired = repaired.replace("ac_rlc_reference_solver_v2", "ac_rlc_reference_v2");
  }
  JSON.parse(repaired);
  if (repaired === original) continue;
  console.log(`${path.relative(process.cwd(), file)}: ${replacements.size} replacement groups`);
  if (verbose) for (const [from, to] of replacements) console.log(`  ${JSON.stringify(from)} -> ${JSON.stringify(to)}`);
  if (write) fs.writeFileSync(file, repaired, "utf8");
}

if (!write) console.log("Dry run only. Re-run with --write to persist the repairs.");
