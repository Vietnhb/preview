import { parse } from "acorn";

export type VisualProgram = { init: string; step: string; draw: string };
type Node = { type: string; [key: string]: unknown };
type Phase = keyof VisualProgram;
type Binding = { constant: boolean; loop: boolean; owned: boolean; callable?: boolean; numeric?: boolean };
type Environment = { parent: Environment | null; bindings: Map<string, Binding> };
const maxPartLength = 50_000;
const blockedNames = new Set([
  "constructor", "prototype", "__proto__", "caller", "callee", "arguments",
  "apply", "call", "bind", "eval", "toString", "valueOf", "then",
  "__visualWork", "__visualDepth", "__visualIndex", "__visualArray", "__visualArgs", "__visualValue",
]);
const mathCalls = new Set([
  "abs", "min", "max", "sqrt", "pow", "sin", "cos", "tan", "atan2",
  "asin", "acos", "exp", "log", "floor", "ceil", "round", "hypot",
  "random", "log10", "log2", "cbrt", "sign", "trunc", "expm1", "log1p",
]);
const paintCalls = new Set(["background", "circle", "rect", "line", "arrow", "text",
  "strokeCircle", "strokeRect", "roundRect", "arc", "dashedLine",
  "gradientRect", "gradientCircle", "polygon",
  "svg", "defs", "svgPath"]);
const formatCalls = new Set(["toFixed", "toPrecision"]);
const arrayCalls = new Set(["push", "pop", "shift", "unshift", "slice"]);
const signatures: Record<Phase, string> = {
  init: "params,width,height", step: "state,dt,params,width,height",
  draw: "state,paint,params,width,height",
};
const allowedNodes = new Set([
  "BlockStatement", "EmptyStatement", "ExpressionStatement", "VariableDeclaration",
  "VariableDeclarator", "ReturnStatement", "IfStatement", "ForStatement", "CallExpression",
  "MemberExpression", "ObjectExpression", "Property", "ArrayExpression", "Literal", "Identifier",
  "BinaryExpression", "LogicalExpression", "UnaryExpression", "ConditionalExpression",
  "AssignmentExpression", "UpdateExpression",
  "FunctionDeclaration", "FunctionExpression", "ArrowFunctionExpression", "TemplateLiteral",
  "TemplateElement", "WhileStatement", "DoWhileStatement", "ForOfStatement", "BreakStatement", "ContinueStatement", "ObjectPattern",
]);
const allowedBinary = new Set([
  "+", "-", "*", "/", "%", "<", "<=", ">", ">=", "===", "!==", "==", "!=",
]);
function node(value: unknown): value is Node {
  return !!value && typeof value === "object" && "type" in value
    && typeof (value as { type: unknown }).type === "string";
}
function walk(current: Node, visit: (item: Node, parent: Node | null, key: string) => void,
  parent: Node | null = null, key = "") {
  visit(current, parent, key);
  for (const [childKey, value] of Object.entries(current)) {
    if (childKey === "start" || childKey === "end" || childKey === "loc") continue;
    if (node(value)) walk(value, visit, current, childKey);
    else if (Array.isArray(value)) {
      for (const child of value) if (node(child)) walk(child, visit, current, childKey);
    }
  }
}
function path(current: Node): string[] | null {
  if (current.type === "Identifier") return [String(current.name)];
  if (current.type !== "MemberExpression" || current.optional || !node(current.object)
    || !node(current.property)) return null;
  const head = path(current.object);
  if (!head) return null;
  if (current.computed) return head;
  return current.property.type === "Identifier" ? [...head, String(current.property.name)] : null;
}
function identifier(current: unknown): string | null {
  return node(current) && current.type === "Identifier" ? String(current.name) : null;
}
function lookup(env: Environment, name: string): Binding | undefined {
  return env.bindings.get(name) ?? (env.parent ? lookup(env.parent, name) : undefined);
}
function scope(parent: Environment | null): Environment { return { parent, bindings: new Map() }; }
function owned(current: unknown, env: Environment): boolean {
  if (!node(current)) return false;
  if (current.type === "ObjectExpression" || current.type === "ArrayExpression") return true;
  if (current.type === "CallExpression" && node(current.callee)) {
    const local = lookup(env, identifier(current.callee) ?? "");
    if (local?.callable) return true;
    if (current.callee.type === "MemberExpression" && ["slice", "pop", "shift"].includes(identifier(current.callee.property) ?? ""))
      return owned(current.callee.object, env);
  }
  const root = path(current)?.[0];
  return root === "state" || !!root && lookup(env, root)?.owned === true;
}
function numericIndex(current: unknown, env: Environment): boolean {
  if (!node(current)) return false;
  if (current.type === "Literal") return typeof current.value === "number"
    && Number.isFinite(current.value);
  if (current.type === "Identifier") {
    if(["width","height","dt"].includes(String(current.name))) return true;
    const binding = lookup(env, String(current.name));
    return !!binding && (binding.loop || binding.numeric === true);
  }
  if (current.type === "MemberExpression" && !current.computed
    && identifier(current.property) === "length") return owned(current.object, env);
  if(current.type === "MemberExpression" && !current.computed
    && identifier(current.object) === "params") return true;
  if (current.type === "UnaryExpression" && ["+", "-"].includes(String(current.operator)))
    return numericIndex(current.argument, env);
  if (current.type === "BinaryExpression" && ["+", "-", "*", "/", "%"].includes(String(current.operator)))
    return numericIndex(current.left, env) && numericIndex(current.right, env);
  if (current.type === "CallExpression" && node(current.callee)) {
    const target = path(current.callee);
    return !!target && target[0] === "Math" && target.length === 2 && mathCalls.has(target[1]);
  }
  return false;
}
function literalIndex(current: unknown): boolean {
  if (!node(current)) return false;
  if(current.type === "Literal") return typeof current.value === "number"
    && Number.isInteger(current.value) && Math.abs(current.value) <= 10_000;
  return current.type === "UnaryExpression" && ["+", "-"].includes(String(current.operator))
    && literalIndex(current.argument);
}
function arrayMethod(current: Node, env: Environment): boolean {
  return current.type === "MemberExpression" && !current.computed && !current.optional
    && arrayCalls.has(identifier(current.property) ?? "") && owned(current.object, env);
}
function formatReceiver(current: unknown, env: Environment): boolean {
  if (!node(current)) return false;
  if (current.type === "Literal") return typeof current.value === "number";
  if (current.type === "BinaryExpression" || current.type === "UnaryExpression"
    || current.type === "ConditionalExpression" || current.type === "CallExpression") return true;
  const target = path(current);
  return !!target && (target[0] === "state" || target[0] === "params" && target.length === 2
    || target[0] === "Math" && target.length === 2 && ["PI", "E"].includes(target[1])
    || ["width", "height", "dt"].includes(target[0]) || !!lookup(env, target[0]));
}
function formatting(current: Node, env: Environment): boolean {
  if (current.type !== "MemberExpression" || current.computed || current.optional) return false;
  return formatCalls.has(identifier(current.property) ?? "") && formatReceiver(current.object, env);
}
function verifyPhase(code: string, phase: Phase): string | null {
  if (phase !== "step" && !code.trim() || code.length > maxPartLength) return `${phase} code is empty or too large.`;
  let body: Node;
  try {
    const program = parse(`function __visual(${signatures[phase]}) {\n"use strict";\n${code}\n}`, {
      ecmaVersion: 2022, sourceType: "script",
    }) as unknown as Node;
    const statements = program.body as Node[];
    if (statements.length !== 1 || statements[0].type !== "FunctionDeclaration")
      return "Visual program must be a function body.";
    body = statements[0].body as Node;
  } catch { return `${phase} code has invalid JavaScript syntax.`; }
  const roots = new Set(phase === "init" ? ["params", "width", "height", "Math", "undefined"]
    : phase === "step" ? ["state", "dt", "params", "width", "height", "Math", "undefined"]
      : ["state", "paint", "params", "width", "height", "Math", "undefined"]);
  const scopes = new Map<Node, Environment>();
  let error: string | null = null;
  const mark = (statement: Node, env: Environment) => walk(statement, (item) => scopes.set(item, env));
  const prepareFunctions = (expression: Node, env: Environment): void => {
    const scan = (value: Node): void => {
      if (["FunctionDeclaration", "FunctionExpression", "ArrowFunctionExpression"].includes(value.type)) {
        const inner = scope(env);
        mark(value, inner);
        if (value.async || value.generator) { error = "Async and generator functions are unavailable."; return; }
        const self = identifier(value.id);
        if (self) {
          if (!/^[A-Za-z_][A-Za-z0-9_]{0,50}$/.test(self) || roots.has(self) || blockedNames.has(self)) {
            error="Invalid local helper name."; return;
          }
          inner.bindings.set(self, {constant:true,loop:false,owned:false,callable:true});
        }
        for (const parameter of value.params as Node[]) {
          const name = identifier(parameter);
          if (!name || !/^[A-Za-z_][A-Za-z0-9_]{0,50}$/.test(name)
            || ["Math","paint","params","undefined"].includes(name) || blockedNames.has(name) || inner.bindings.has(name)) {
            error = "Helper parameters must be distinct local names."; return;
          }
          inner.bindings.set(name,{constant:false,loop:false,owned:true,numeric:true});
        }
        prepare(value.body as Node,inner,1);
        return;
      }
      for (const child of Object.values(value)) {
        if (node(child)) scan(child);
        else if (Array.isArray(child)) for (const item of child) if (node(item)) scan(item);
      }
    };
    scan(expression);
  };
  const prepare = (statement: Node, env: Environment, multiplier: number): void => {
    if (error) return;
    mark(statement, env);
    if (statement.type === "BlockStatement") {
      const block = scope(env);
      for(const child of statement.body as Node[]) if(child.type === "FunctionDeclaration") {
        const name = identifier(child.id);
        if(!name || blockedNames.has(name) || roots.has(name) || block.bindings.has(name)) {
          error = "Invalid local helper declaration."; return;
        }
        block.bindings.set(name,{constant:true,loop:false,owned:false,callable:true});
      }
      for (const child of statement.body as Node[]) prepare(child, block, multiplier);
    } else if (statement.type === "ForStatement") {
      const loop = scope(env);
      mark(statement, loop);
      const init = statement.init as Node;
      const declaration = init?.type === "VariableDeclaration" ? (init.declarations as Node[])?.[0] : null;
      const name = declaration ? identifier(declaration.id) : null;
      const test = statement.test as Node;
      const update = statement.update as Node;
      if (init?.kind !== "let" || (init.declarations as Node[])?.length !== 1
        || !name || !numericIndex(declaration?.init,loop) || test?.type !== "BinaryExpression"
        || !["<", "<=", ">", ">="].includes(String(test.operator)) || identifier(test.left) !== name
        || !numericIndex(test.right,loop)
        || update?.type !== "UpdateExpression" || !["++", "--"].includes(String(update.operator))
        || identifier(update.argument) !== name) {
        error = "For loops require a scoped numeric index and numeric bound."; return;
      }
      prepare(init, loop, multiplier);
      const binding = lookup(loop, name);
      if (!binding) return;
      binding.loop = true;
      prepare(statement.body as Node, loop, multiplier);
    } else if (statement.type === "ForOfStatement") {
      const loop = scope(env);
      mark(statement, loop);
      const left = statement.left as Node;
      const right = statement.right as Node;
      const declaration = left?.type === "VariableDeclaration" ? left : null;
      const declarators = declaration?.declarations as Node[] | undefined;
      const item = declarators?.length === 1 ? declarators[0] : null;
      const name = item ? identifier(item.id) : null;
      if (statement.await || !declaration || !["let", "const"].includes(String(declaration.kind))
        || !item || !name || !/^[A-Za-z_][A-Za-z0-9_]{0,50}$/.test(name)
        || roots.has(name) || blockedNames.has(name) || !owned(right, env)) {
        error = "For-of loops require one safe local binding over owned array data.";
        return;
      }
      // The worker budget instruments each iteration. The binding is marked as
      // owned because it is an element of an already-owned state/local array.
      loop.bindings.set(name, {
        constant: declaration.kind === "const", loop: true, owned: true,
      });
      prepareFunctions(right, env);
      prepare(statement.body as Node, loop, multiplier);
    } else if (statement.type === "VariableDeclaration") {
      if (statement.kind !== "let" && statement.kind !== "const") {
        error = "Only local let and const declarations are allowed."; return;
      }
      for (const declaration of statement.declarations as Node[]) {
        if(node(declaration.id) && declaration.id.type === "ObjectPattern") {
          const sourceParams=identifier(declaration.init) === "params";
          if(!sourceParams && !owned(declaration.init,env)) {
            error="Object destructuring requires declared parameters or owned data.";return;
          }
          for(const property of declaration.id.properties as Node[]) {
            const field=identifier(property.key) ?? (node(property.key) && typeof property.key.value === "string" ? property.key.value : null);
            const name=identifier(property.value);
            if(property.type !== "Property" || property.computed || property.method || property.kind !== "init"
              || !field || !/^[A-Za-z_][A-Za-z0-9_]{0,50}$/.test(field) || blockedNames.has(field)
              || !name || !/^[A-Za-z_][A-Za-z0-9_]{0,50}$/.test(name)
              || roots.has(name) || blockedNames.has(name) || env.bindings.has(name)) {
              error="Object destructuring requires safe fields and distinct flat local names.";return;
            }
            env.bindings.set(name,{constant:statement.kind === "const",loop:false,owned:!sourceParams,numeric:true});
          }
          continue;
        }
        const name = identifier(declaration.id);
        if (!name || statement.kind === "const" && !node(declaration.init) || !/^[A-Za-z_][A-Za-z0-9_]{0,50}$/.test(name)
          || roots.has(name) || blockedNames.has(name) || env.bindings.has(name)) {
          error = "Visual code has an invalid local variable."; return;
        }
        env.bindings.set(name, { constant: statement.kind === "const", loop: false,
          owned: owned(declaration.init, env), numeric:numericIndex(declaration.init,env),
          callable:node(declaration.init) && ["FunctionExpression","ArrowFunctionExpression"].includes(declaration.init.type) });
        if(node(declaration.init)) prepareFunctions(declaration.init,env);
      }
    } else if (statement.type === "IfStatement") {
      prepare(statement.consequent as Node, env, multiplier);
      if (node(statement.alternate)) prepare(statement.alternate, env, multiplier);
    } else if (["WhileStatement","DoWhileStatement"].includes(statement.type)) {
      prepare(statement.body as Node,scope(env),multiplier);
      if(node(statement.test)) prepareFunctions(statement.test,env);
    } else if(statement.type === "FunctionDeclaration") {
      prepareFunctions(statement,env);
    } else {
      prepareFunctions(statement,env);
    }
  };
  const base = scope(null);
  mark(body, base);
  prepare(body,base,1);
  if (error) return error;
  let calls = 0;
  walk(body, (item, parent, key) => {
    if (error) return;
    const env = scopes.get(item) ?? base;
    if (!allowedNodes.has(item.type)) { error = `Forbidden JavaScript syntax: ${item.type}.`; return; }
    if (item.type === "CallExpression") {
      const callee = item.callee as Node;
      const target = path(callee);
      const format = formatting(callee, env);
      const array = arrayMethod(callee,env);
      const local = callee.type === "Identifier" && lookup(env,String(callee.name))?.callable;
      const args = item.arguments as Node[];
      if (format) {
        const digits = args[0];
        const minimum = identifier(callee.property) === "toPrecision" ? 1 : 0;
        if (args.length !== 1 || digits?.type !== "Literal" || typeof digits.value !== "number"
          || !Number.isInteger(digits.value) || digits.value < minimum || digits.value > 100)
          error = "Numeric formatting requires a bounded literal precision.";
      } else if(array) {
        const method=identifier(callee.property);
        if (["pop","shift"].includes(method ?? "") && args.length !== 0)
          error="Array removal methods take no arguments.";
        if(method === "slice" && (args.length > 2 || args.some((argument) => !literalIndex(argument))))
          error="Array slice requires bounded numeric literal indexes.";
      } else if (!local && (!target || !((target[0] === "Math" && target.length === 2 && mathCalls.has(target[1]))
        || (phase === "draw" && target[0] === "paint" && target.length === 2
          && paintCalls.has(target[1]))))) error = "Visual code calls an unapproved API.";
      if (++calls > 800) error = "Visual code has too many API calls.";
    }
    if (item.type === "MemberExpression") {
      if (formatting(item, env)) return;
      const target = path(item);
      if (!target || target.some((part) => blockedNames.has(part))) {
        error = "Visual code uses an unsafe property."; return;
      }
      const head = target[0];
      if (!roots.has(head) && !lookup(env, head)) { error = "Visual code reads an unknown object."; return; }
      if (item.computed) {
        const index = item.property as Node;
        if (!numericIndex(index,env)) {
          error = "Visual array access needs a bounded index."; return;
        }
        if (head !== "state" && !lookup(env, head)) {
          error = "Visual code indexes an unapproved value."; return;
        }
      }
      if (head === "Math" && !(target.length === 2
        && (mathCalls.has(target[1]) || target[1] === "PI" || target[1] === "E")))
        error = "Visual code uses an unapproved Math member.";
      if (head === "paint" && !(target.length === 2 && paintCalls.has(target[1])))
        error = "Visual code uses an unapproved drawing member.";
      if (head === "params" && target.length !== 2)
        error = "Visual code reads an unsafe parameter path.";
      if ((head === "paint" || head === "Math" && mathCalls.has(target[1]))
        && !(parent?.type === "CallExpression" && key === "callee"))
        error = "Host methods may only be called directly.";
    }
    if (item.type === "Property") {
      const name = identifier(item.key) ?? (node(item.key) && typeof item.key.value === "string" ? item.key.value : null);
      if (item.computed || item.kind !== "init" || item.method || !name
        || !/^[A-Za-z_][A-Za-z0-9_]{0,50}$/.test(name) || blockedNames.has(name))
        error = "Visual code has an unsafe state field.";
    }
    if (["FunctionExpression","ArrowFunctionExpression"].includes(item.type)
      && !(parent?.type === "VariableDeclarator" && key === "init"))
      error="Helper functions must be declared as local bindings.";
    if(item.type === "TemplateElement" && typeof (item.value as {raw?:string})?.raw === "string"
      && ((item.value as {raw:string}).raw.length > 500)) error="Visual code has an excessive string.";
    if (["BreakStatement","ContinueStatement"].includes(item.type) && item.label)
      error="Labeled control flow is unavailable.";
    if (item.type === "Literal") {
      if (typeof item.value === "number" && !Number.isFinite(item.value))
        error = "Visual code has an excessive number.";
      else if (typeof item.value === "string" && item.value.length > 500)
        error = "Visual code has an excessive string.";
      else if (item.regex || item.bigint || typeof item.value === "symbol")
        error = "Visual code uses an unsupported literal.";
    }
    if (item.type === "ArrayExpression" && (item.elements as unknown[]).length > 1000)
      error = "Visual code has an excessive array.";
    if (item.type === "BinaryExpression" && !allowedBinary.has(String(item.operator)))
      error = "Visual code uses an unsupported operator.";
    if (item.type === "LogicalExpression" && item.operator !== "&&" && item.operator !== "||")
      error = "Visual code uses an unsupported logical operator.";
    if (item.type === "UnaryExpression" && !["+", "-", "!"].includes(String(item.operator)))
      error = "Visual code uses an unsupported unary operator.";
    if (item.type === "UpdateExpression") {
      const argument = item.argument as Node;
      const target = path(argument);
      const binding = target ? lookup(env, target[0]) : undefined;
      const loopUpdate = parent?.type === "ForStatement" && key === "update"
        && ["++", "--"].includes(String(item.operator)) && binding?.loop;
      const localUpdate = argument.type === "Identifier" && binding && !binding.constant && !binding.loop && !binding.callable;
      const memberUpdate = argument.type === "MemberExpression" && owned(argument.object, env);
      if (!loopUpdate && !((item.operator === "++" || item.operator === "--") && (localUpdate || memberUpdate)))
        error = "Visual code updates a protected or unbounded value.";
    }
    if (item.type === "AssignmentExpression") {
      const left = item.left as Node;
      const binding = lookup(env, identifier(left) ?? "");
      const localWrite = left.type === "Identifier" && binding && !binding.constant && !binding.loop && !binding.callable;
      const memberWrite = left.type === "MemberExpression" && owned(left.object, env);
      if (!["=", "+=", "-=", "*=", "/="].includes(String(item.operator)) || !localWrite && !memberWrite)
        error = "Visual code may mutate only local values or its state.";
      if (localWrite && binding) {
        binding.owned = item.operator === "=" && owned(item.right, env);
        binding.numeric = (item.operator === "=" || binding.numeric === true) && numericIndex(item.right, env);
      }
    }
    if (item.type === "Identifier") {
      if (parent?.type === "MemberExpression" && key === "property" && !parent.computed) return;
      if (parent?.type === "Property" && key === "key" && !parent.computed) return;
      if (parent?.type === "VariableDeclarator" && key === "id") return;
      if (["FunctionDeclaration","FunctionExpression","ArrowFunctionExpression"].includes(parent?.type ?? "")
        && (key === "params" || key === "id")) return;
      const name = String(item.name);
      if (!roots.has(name) && !lookup(env, name)) error = `Visual code reads an undeclared name: ${name}.`;
      if (["params", "Math", "paint"].includes(name)
        && !(parent?.type === "MemberExpression" && key === "object")
        && !(name === "params" && parent?.type === "VariableDeclarator" && key === "init"
          && node(parent.id) && parent.id.type === "ObjectPattern"))
        error = "Host capability objects may not be captured or mutated.";
      if(lookup(env,name)?.callable && !(parent?.type === "CallExpression" && key === "callee"))
        error="Local helpers may only be called by direct lexical name.";
    }
  });
  return error;
}
export function validateVisualProgram(program: VisualProgram, parameterNames: readonly string[]): string | null {
  if (!program || typeof program.init !== "string" || typeof program.step !== "string"
    || typeof program.draw !== "string") return "Visual program is incomplete.";
  const names = new Set(parameterNames);
  if (names.size !== parameterNames.length || names.size > 30
    || [...names].some((name) => !/^[A-Za-z_][A-Za-z0-9_]{0,50}$/.test(name))) return "Visual parameters are invalid.";
  for (const phase of ["init", "step", "draw"] as const) {
    const error = verifyPhase(program[phase], phase);
    if (error) return error;
  }
  return null;
}
