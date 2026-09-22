import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import ts from "typescript";

const root = fileURLToPath(new URL("../src/", import.meta.url));
const errors = [];
const globalStyles = new Set(["app.css", "app-refresh.css", "modern-roles.css", "admin-console.css"]);

async function visit(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) { await visit(file); continue; }
    if (!/\.(ts|tsx)$/.test(file)) continue;
    const content = await readFile(file, "utf8");
    const source = ts.createSourceFile(file, content, ts.ScriptTarget.Latest, true);
    const relative = path.relative(root, file).replaceAll("\\", "/");
    if (/^(simulation-scene|simulation-renderer)\//.test(relative)
      // Do not mistake capability/property validation such as
      // `node.properties.environment === ...` for a schema-identity dispatch.
      // Only a bare environment identifier is an architectural violation.
      && /(schemaId\s*===|schemaId\s*!==|visualization\??\.scene\s*===|(?<![\w.])environment\s*===)/.test(content)) {
      errors.push(relative + ": rendering must dispatch by declared capabilities, not schema/lesson/environment identity");
    }
    const check = (specifier) => {
      if (!specifier.startsWith(".")) return;
      const target = path.relative(root, path.resolve(path.dirname(file), specifier)).replaceAll("\\", "/");
      if (globalStyles.has(path.basename(target))) {
        errors.push(relative + ": global CSS must be loaded through styles/global.css");
      }
      if (relative.startsWith("shared/") && /^(app|pages|features)\//.test(target)) {
        errors.push(relative + ": shared code cannot depend on " + target);
      }
      if (relative.startsWith("features/") && /^(app|pages|components\/roles)\//.test(target)) {
        errors.push(relative + ": feature cannot depend on a page, app shell or role-specific component");
      }
      if (/^(api|types|utils)\//.test(relative) && /^(app|pages|features)\//.test(target)) {
        errors.push(relative + ": foundational code cannot depend on " + target);
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
