import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import ts from "typescript";

const root = fileURLToPath(new URL("../src/", import.meta.url));
const errors = [];
const globalStyles = new Set(["tokens.css", "base.css", "modern-roles.css", "admin-console.css"]);
const features = new Set(["account", "assignments", "auth", "billing", "curriculum", "library",
  "management", "public", "reviewer", "school", "simulation", "support"]);
const featureLayers = new Set(["api", "components", "hooks", "layout", "model", "pages", "styles"]);
const sharedLayers = new Set(["api", "auth", "effects", "hooks", "layout", "lib", "theme", "types", "ui"]);
const foundationLayers = new Set(["api", "model", "types", "engine", "auth", "lib", "config"]);
const presentationLayers = new Set(["components", "hooks", "layout", "pages", "effects", "ui"]);
const publicFeatureLayers = new Set(["api", "types", "model", "components"]);

function location(relative) {
  const parts = relative.split("/");
  if (relative === "main.tsx") return { area: "entry", layer: "entry" };
  if (parts[0] === "app") return { area: "app", layer: "app" };
  if (parts[0] === "config") return { area: "config", layer: "config" };
  if (parts[0] === "shared" && sharedLayers.has(parts[1])) return { area: "shared", layer: parts[1] };
  if (parts[0] === "styles") return { area: "styles", layer: "styles" };
  if (parts[0] === "features" && features.has(parts[1])) {
    if (/^types(?:\.ts)?$/.test(parts[2])) return { area: "feature", feature: parts[1], layer: "types" };
    let layer = parts[2];
    if (layer === "dataio" && parts[1] === "school") layer = parts[3];
    if (featureLayers.has(layer) || (layer === "engine" && parts[1] === "simulation"))
      return { area: "feature", feature: parts[1], layer };
  }
  return undefined;
}

async function visit(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) { await visit(file); continue; }
    if (!/\.(ts|tsx)$/.test(file)) continue;
    const content = await readFile(file, "utf8");
    const source = ts.createSourceFile(file, content, ts.ScriptTarget.Latest, true);
    const relative = path.relative(root, file).replaceAll("\\", "/");
    const owner = location(relative);
    if (!owner) errors.push(relative + ": source must belong to app, shared, config or a declared feature layer");
    if (/^features\/simulation\/engine\/(scene|renderer)\//.test(relative)
      // Do not mistake capability/property validation such as
      // `node.properties.environment === ...` for a schema-identity dispatch.
      // Only a bare environment identifier is an architectural violation.
      && /(schemaId\s*===|schemaId\s*!==|visualization\??\.scene\s*===|(?<![\w.])environment\s*===)/.test(content)) {
      errors.push(relative + ": rendering must dispatch by declared capabilities, not schema/lesson/environment identity");
    }
    const check = (specifier) => {
      if (!owner) return;
      if (!specifier.startsWith(".")) {
        if (foundationLayers.has(owner.layer)
          && /^(react(?:\/|$)|react-dom(?:\/|$)|react-router(?:-dom)?(?:\/|$)|@radix-ui\/|motion\/react(?:\/|$))/.test(specifier))
          errors.push(relative + ": API, models and engine code cannot depend on React presentation: " + specifier);
        return;
      }
      const target = path.relative(root, path.resolve(path.dirname(file), specifier.split("?")[0])).replaceAll("\\", "/");
      if (globalStyles.has(path.basename(target))) {
        errors.push(relative + ": global CSS must be loaded through styles/global.css");
      }
      // Raw dependency bundles outside src are third-party imports, not feature dependencies.
      if (target.startsWith("../node_modules/")) return;
      const dependency = location(target);
      if (!dependency) {
        errors.push(relative + ": import points outside the declared source structure: " + target);
        return;
      }
      if (owner.area === "shared" && (dependency.area === "app" || dependency.area === "feature")) {
        errors.push(relative + ": shared code cannot depend on " + target);
      }
      if (owner.area === "feature") {
        if (dependency.area === "app" || dependency.area === "entry")
          errors.push(relative + ": a feature cannot depend on the app shell or entry point: " + target);
        if (owner.layer !== "pages" && dependency.layer === "pages")
          errors.push(relative + ": feature internals cannot depend on a page: " + target);
        if (dependency.area === "feature" && owner.feature !== dependency.feature
          && owner.layer !== "pages" && !publicFeatureLayers.has(dependency.layer))
          errors.push(relative + ": cross-feature imports must use API, types, models or components: " + target);
      }
      if (foundationLayers.has(owner.layer)
        && (presentationLayers.has(dependency.layer) || dependency.area === "app" || dependency.area === "entry")) {
        errors.push(relative + ": foundational code cannot depend on presentation: " + target);
      }
    };
    const walk = (node) => {
      if ((ts.isImportDeclaration(node) || ts.isExportDeclaration(node))
        && node.moduleSpecifier && ts.isStringLiteral(node.moduleSpecifier)) check(node.moduleSpecifier.text);
      if (ts.isCallExpression(node) && node.expression.kind === ts.SyntaxKind.ImportKeyword
        && node.arguments[0] && ts.isStringLiteral(node.arguments[0])) check(node.arguments[0].text);
      ts.forEachChild(node, walk);
    };
    walk(source);
  }
}
await visit(root);
if (errors.length) {
  console.error(errors.join("\n"));
  process.exitCode = 1;
} else {
  console.log("Architecture checks passed.");
}
