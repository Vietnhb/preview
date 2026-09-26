import { parse } from "acorn";

type SyntaxNode = {
  type: string;
  [key: string]: unknown;
};

const MAX_CODE_LENGTH = 120_000;
const blockedProperties = new Set([
  "constructor", "prototype", "__proto__", "caller", "callee", "arguments",
  "apply", "call", "bind", "eval", "toString", "valueOf",
]);
const matterCalls = new Set([
  "Engine.create",
  "Bodies.rectangle", "Bodies.circle",
  "Body.setPosition", "Body.setVelocity", "Body.setAngle", "Body.setMass",
  "Body.setAngularVelocity", "Body.setStatic", "Body.applyForce",
  "Composite.add", "Composite.remove",
  "Constraint.create",
  "World.add", "World.remove",
]);
const mathCalls = new Set(["abs", "min", "max", "sqrt", "sin", "cos", "tan", "atan2", "floor", "ceil"]);
const forbiddenNodeTypes = new Set([
  "AwaitExpression", "YieldExpression", "ImportExpression", "NewExpression",
  "ThisExpression", "Super", "MetaProperty", "TaggedTemplateExpression",
  "FunctionDeclaration", "FunctionExpression", "ArrowFunctionExpression",
  "ClassDeclaration", "ClassExpression", "ForStatement", "ForInStatement",
  "ForOfStatement", "WhileStatement", "DoWhileStatement", "TryStatement",
  "WithStatement", "SwitchStatement", "UpdateExpression", "SequenceExpression",
]);
const roots = new Set(["Matter", "Math", "params", "width", "height"]);
const engineProperties = new Set([
  "world", "world.gravity", "world.gravity.x", "world.gravity.y",
  "gravity", "gravity.x", "gravity.y", "gravity.scale",
]);
const bodyProperties = new Set([
  "position", "position.x", "position.y", "velocity", "velocity.x", "velocity.y",
  "mass", "angle", "angularVelocity",
]);

function isNode(value: unknown): value is SyntaxNode {
  return typeof value === "object" && value !== null && "type" in value
    && typeof (value as { type: unknown }).type === "string";
}

function memberPath(node: SyntaxNode): string[] | null {
  if (node.type === "Identifier") return [String(node.name)];
  if (node.type !== "MemberExpression" || node.optional || node.computed) return null;
  const object = node.object;
  const property = node.property;
  if (!isNode(object) || !isNode(property)) return null;
  const head = memberPath(object);
  if (!head) return null;
  let name: string;
  if (property.type === "Identifier") name = String(property.name);
  else return null;
  if (blockedProperties.has(name)) return null;
  return [...head, name];
}

function visit(node: SyntaxNode, visitor: (node: SyntaxNode, parent: SyntaxNode | null, key: string) => void,
  parent: SyntaxNode | null = null, key = "") {
  visitor(node, parent, key);
  for (const [childKey, value] of Object.entries(node)) {
    if (childKey === "start" || childKey === "end" || childKey === "loc") continue;
    if (isNode(value)) visit(value, visitor, node, childKey);
    else if (Array.isArray(value)) {
      for (const child of value) if (isNode(child)) visit(child, visitor, node, childKey);
    }
  }
}

/** Repeat the backend gate before inserting generated code into an opaque sandbox. */
export function validateMatterCode(code: string, parameterNames: readonly string[]): string | null {
  if (!code.trim() || code.length > MAX_CODE_LENGTH) return "Generated simulation code is empty or too large.";
  if (/<\s*\/\s*script|<!--|-->/i.test(code)) return "Generated simulation code contains HTML markup.";
  let program: SyntaxNode;
  try {
    program = parse(`function simulation(Matter, params, width, height) {\n${code}\n}`, {
      ecmaVersion: "latest",
      sourceType: "script",
    }) as unknown as SyntaxNode;
  } catch {
    return "Generated simulation code has invalid JavaScript syntax.";
  }
  const outer = (program.body as SyntaxNode[])[0];
  const body = outer.body as SyntaxNode;
  const locals = new Set<string>();
  const engines = new Set<string>();
  const bodies = new Set<string>();
  let error: string | null = null;
  visit(body, (node) => {
    if (error || node.type !== "VariableDeclarator") return;
    const id = node.id;
    if (!isNode(id) || id.type !== "Identifier" || roots.has(String(id.name))) {
      error = "Generated code must use simple local variables.";
      return;
    }
    const localName = String(id.name);
    if (locals.has(localName)) { error = "Generated code redeclares a local variable."; return; }
    locals.add(localName);
    if (isNode(node.init) && node.init.type === "CallExpression" && isNode(node.init.callee)) {
      const callee = memberPath(node.init.callee);
      if (callee?.join(".") === "Matter.Engine.create") engines.add(localName);
      if (callee?.[0] === "Matter" && callee[1] === "Bodies"
        && (callee[2] === "circle" || callee[2] === "rectangle")) bodies.add(localName);
    }
  });
  if (error) return error;
  const statements = Array.isArray(body.body) ? body.body as SyntaxNode[] : [];
  const last = statements.at(-1);
  const returned = isNode(last?.argument) && last.argument.type === "Identifier"
    ? String(last.argument.name) : null;
  if (engines.size !== 1 || last?.type !== "ReturnStatement" || !returned || !engines.has(returned))
    return "Generated code must return its one locally created Matter engine.";
  const parameterSet = new Set(parameterNames);
  if (parameterSet.size > 40) return "Generated simulation has too many parameters.";
  let callCount = 0;
  visit(body, (node, parent, key) => {
    if (error) return;
    if (forbiddenNodeTypes.has(node.type)) {
      error = `Generated code uses a forbidden JavaScript construct: ${node.type}.`;
      return;
    }
    if (node.type === "MemberExpression") {
      const path = memberPath(node);
      if (!path) {
        error = "Generated code uses dynamic or unsafe property access.";
        return;
      }
      if (path[0] === "params" && path.length > 1 && !parameterSet.has(path[1]))
        error = `Generated code references an undeclared parameter: ${path[1]}.`;
      if (path[0] === "params" && path.length !== 2)
        error = "Generated code must access parameters by their declared names.";
      if (engines.has(path[0]) && !engineProperties.has(path.slice(1).join(".")))
        error = "Generated code accesses an unsupported engine property.";
      if (bodies.has(path[0]) && !bodyProperties.has(path.slice(1).join(".")))
        error = "Generated code accesses an unsupported body property.";
      if (locals.has(path[0]) && !engines.has(path[0]) && !bodies.has(path[0]))
        error = "Generated code accesses an unsupported local property.";
    }
    if (node.type === "CallExpression") {
      callCount += 1;
      if (callCount > 400) { error = "Generated simulation makes too many API calls."; return; }
      const callee = node.callee;
      const path = isNode(callee) ? memberPath(callee) : null;
      const allowed = path && ((path[0] === "Matter" && matterCalls.has(path.slice(1).join(".")))
        || (path[0] === "Math" && path.length === 2 && mathCalls.has(path[1])));
      if (!allowed) error = "Generated code calls an API outside the Matter.js allow-list.";
    }
    if (node.type === "Literal" && typeof node.value === "number"
      && (!Number.isFinite(node.value) || Math.abs(node.value) > 1_000_000))
      error = "Generated code contains an out-of-range number.";
    if (node.type === "AssignmentExpression") {
      const left = node.left;
      const path = isNode(left) ? memberPath(left) : null;
      if (!path || !locals.has(path[0])) error = "Generated code assigns to nonlocal state.";
    }
    if (node.type === "Identifier") {
      if ((parent?.type === "MemberExpression" && key === "property" && !parent.computed)
        || (parent?.type === "Property" && key === "key" && !parent.computed)
        || (parent?.type === "VariableDeclarator" && key === "id")) return;
      const name = String(node.name);
      if (!roots.has(name) && !locals.has(name)) error = `Generated code accesses an unapproved name: ${name}.`;
    }
  });
  return error;
}
