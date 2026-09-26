import { readFileSync } from "node:fs";

const catalog = JSON.parse(readFileSync(new URL("../../backend/src/main/resources/schemas/catalog.json", import.meta.url), "utf8"));
const templates = Array.isArray(catalog) ? catalog : catalog.schemas;
const errors = [];
const identities = new Set();

if (!Array.isArray(templates) || templates.length === 0) {
  errors.push("The adaptive physics template catalog is empty or invalid.");
} else {
  for (const template of templates) {
    const identity = `${template.schemaId ?? ""}@${template.version ?? ""}`;
    if (!template.schemaId || !template.version || identities.has(identity))
      errors.push(`Missing or duplicate template identity: ${identity}`);
    identities.add(identity);

    const definition = template.definition ?? {};
    if (definition.metaSchemaVersion !== "2.0")
      errors.push(`${identity}: expected the adaptive 2.0 template contract`);
    if (!Array.isArray(definition.objectTypes) || !Array.isArray(definition.relationTypes))
      errors.push(`${identity}: conceptual objects and relations must be data arrays`);
    if (definition.conceptVocabulary?.openWorld !== true
      || definition.conceptVocabulary?.scope !== "high_school_physics")
      errors.push(`${identity}: vocabulary must permit grounded high-school physics descriptions beyond listed concepts`);
    if (definition.visualCapability?.mode !== "generated_scene"
      || Object.hasOwn(definition.visualCapability ?? {}, "assetStrategy"))
      errors.push(`${identity}: visual capability must describe an AI-generated scene without an asset catalog contract`);
    if (!["matter_js", "generated_visual", "unsupported"].includes(definition.simulationCapability?.mode))
      errors.push(`${identity}: simulation capability must state its executable support`);
  }
}

console.log(JSON.stringify({ templates: templates?.length ?? 0, errors }, null, 2));
if (errors.length) process.exitCode = 1;
