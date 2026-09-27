/**
 * Language-level safety net for generated rendering code.
 *
 * Small/free models frequently make the same *JavaScript* mistakes regardless of
 * the physics being drawn: `await` inside a callback that is not `async`, or
 * re-assigning a `const`. Both are mechanical to fix without changing intent, so
 * they are repaired before the code reaches the sandbox. Anything else is
 * reported as a precise, line-numbered hint that is appended to the runtime error
 * and forwarded to the AI repair request.
 *
 * Pure string analysis: nothing here evaluates the code. No knowledge of scenes,
 * objects or lessons is involved.
 */
type Token = { type: "id" | "punct" | "str" | "num"; value: string; start: number; end: number };

const KEYWORD_BLOCKS = new Set(["if", "for", "while", "switch", "catch", "with", "return", "typeof", "new", "else", "do", "try", "finally"]);
const ASSIGN = new Set(["=", "+=", "-=", "*=", "/=", "%=", "**=", "||=", "&&=", "??=", "<<=", ">>=", ">>>=", "&=", "|=", "^="]);
const PUNCT = [">>>=", "...", "===", "!==", "**=", "<<=", ">>=", "&&=", "||=", "??=", ">>>", "=>", "==", "!=", "<=", ">=", "&&", "||", "??",
  "?.", "++", "--", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "**", "<<", ">>"];

/** Tokenise JS, treating template literal text as strings and `${…}` as code. */
function tokenize(code: string, lineComments?: number[]): Token[] {
  const tokens: Token[] = [];
  const templateDepth: number[] = []; // brace depth at which each open `${` resumes its template
  let depth = 0, i = 0;
  const readTemplate = (from: number) => {
    // from is just after ` or after the } closing a ${…}
    let j = from;
    while (j < code.length) {
      if (code[j] === "\\") { j += 2; continue; }
      if (code[j] === "`") { tokens.push({ type: "str", value: "`", start: from, end: j + 1 }); return j + 1; }
      if (code[j] === "$" && code[j + 1] === "{") {
        tokens.push({ type: "str", value: "`", start: from, end: j });
        templateDepth.push(depth); depth++;
        return j + 2;
      }
      j++;
    }
    return j;
  };
  while (i < code.length) {
    const c = code[i];
    if (/\s/.test(c)) { i++; continue; }
    if (c === "/" && code[i + 1] === "/") { lineComments?.push(i); while (i < code.length && code[i] !== "\n") i++; continue; }
    if (c === "/" && code[i + 1] === "*") { const e = code.indexOf("*/", i + 2); i = e < 0 ? code.length : e + 2; continue; }
    if (c === "'" || c === '"') {
      let j = i + 1;
      while (j < code.length && code[j] !== c && code[j] !== "\n") j += code[j] === "\\" ? 2 : 1;
      tokens.push({ type: "str", value: c, start: i, end: j + 1 }); i = j + 1; continue;
    }
    if (c === "`") { i = readTemplate(i + 1); continue; }
    if (c === "}" && templateDepth.length && templateDepth[templateDepth.length - 1] === depth - 1) {
      templateDepth.pop(); depth--; i = readTemplate(i + 1); continue;
    }
    if (/[A-Za-z_$]/.test(c)) {
      let j = i + 1; while (j < code.length && /[\w$]/.test(code[j])) j++;
      tokens.push({ type: "id", value: code.slice(i, j), start: i, end: j }); i = j; continue;
    }
    if (/[0-9]/.test(c) || (c === "." && /[0-9]/.test(code[i + 1] ?? ""))) {
      let j = i + 1; while (j < code.length && /[\w.]/.test(code[j])) j++;
      tokens.push({ type: "num", value: code.slice(i, j), start: i, end: j }); i = j; continue;
    }
    const op = PUNCT.find(p => code.startsWith(p, i)) ?? c;
    if (op === "{") depth++;
    if (op === "}") depth--;
    tokens.push({ type: "punct", value: op, start: i, end: i + op.length }); i += op.length;
  }
  return tokens;
}

function matchBack(tokens: Token[], close: number, open: string, closeCh: string) {
  let d = 0;
  for (let k = close; k >= 0; k--) {
    if (tokens[k].type !== "punct") continue;
    if (tokens[k].value === closeCh) d++;
    else if (tokens[k].value === open && --d === 0) return k;
  }
  return -1;
}

type Fn = { async: boolean; insertAt: number; line: number };

export type CodeIssue = { line: number; message: string };
export type RepairResult = { code: string; fixes: CodeIssue[] };

const lineOf = (code: string, index: number) => code.slice(0, index).split("\n").length;

/** Describe the function whose body starts at tokens[k] ('{'), or null for a plain block. */
function functionAt(tokens: Token[], k: number, code: string): Fn | null {
  const prev = tokens[k - 1];
  if (!prev) return null;
  const fn = (asyncToken: Token | undefined, insertToken: Token): Fn =>
    ({ async: asyncToken?.value === "async", insertAt: insertToken.start, line: lineOf(code, insertToken.start) });
  if (prev.value === "=>") {
    const before = tokens[k - 2];
    if (!before) return null;
    if (before.value === ")") {
      const open = matchBack(tokens, k - 2, "(", ")");
      return open < 0 ? null : fn(tokens[open - 1], tokens[open]);
    }
    return fn(tokens[k - 3], before);
  }
  if (prev.value === ")") {
    const open = matchBack(tokens, k - 1, "(", ")");
    if (open < 1) return null;
    const head = tokens[open - 1];
    if (head.value === "function") return fn(tokens[open - 2], head);
    if (head.type === "id" && tokens[open - 2]?.value === "function") return fn(tokens[open - 3], tokens[open - 2]);
    if (head.type === "id" && !KEYWORD_BLOCKS.has(head.value)) {
      const before = tokens[open - 2];
      // method shorthand inside an object literal / class body: `name(args) {`
      if (!before || ["{", ",", "}", ";", "async", "get", "set", "static"].includes(before.value))
        return fn(before, head);
    }
  }
  return null;
}

/**
 * Apply mechanical, intent-preserving fixes:
 *  - a function (declaration, expression, arrow or method) that directly contains
 *    `await` is made `async`;
 *  - a `const` binding that is later re-assigned becomes `let`.
 */
export function repairGeneratedCode(source: string): RepairResult {
  let tokens: Token[];
  try { tokens = tokenize(source); } catch { return { code: source, fixes: [] }; }
  const stack: Array<Fn | null> = [];
  const pending = new Map<number, Fn>();
  for (let k = 0; k < tokens.length; k++) {
    const t = tokens[k];
    if (t.value === "{" && t.type === "punct") stack.push(functionAt(tokens, k, source));
    else if (t.value === "}" && t.type === "punct") stack.pop();
    else if (t.type === "id" && t.value === "await" && tokens[k - 1]?.value !== ".") {
      const owner = [...stack].reverse().find(Boolean);
      if (owner && !owner.async) pending.set(owner.insertAt, owner);
    }
  }
  // const → let for re-assigned bindings (property writes like obj.x = … are not re-assignments)
  const consts = new Map<string, Token>();
  const reassigned = new Set<string>();
  for (let k = 0; k < tokens.length; k++) {
    const t = tokens[k];
    if (t.value === "const" && tokens[k + 1]?.type === "id") consts.set(tokens[k + 1].value, t);
    if (t.type !== "id" || !consts.has(t.value) || tokens[k - 1]?.value === "." || tokens[k - 1]?.value === "?.") continue;
    const declared = ["const", "let", "var"].includes(tokens[k - 1]?.value ?? "");
    const next = tokens[k + 1]?.value ?? "", prev = tokens[k - 1]?.value ?? "";
    if (!declared && (ASSIGN.has(next) || next === "++" || next === "--" || prev === "++" || prev === "--")) reassigned.add(t.value);
  }
  const edits: Array<{ at: number; remove: number; text: string; fix: CodeIssue }> = [];
  for (const fn of pending.values())
    edits.push({ at: fn.insertAt, remove: 0, text: "async ", fix: { line: fn.line, message: "made the function containing `await` async" } });
  for (const name of reassigned) {
    const decl = consts.get(name)!;
    edits.push({ at: decl.start, remove: 5, text: "let", fix: { line: lineOf(source, decl.start), message: "`const " + name + "` is re-assigned; declared with let" } });
  }
  edits.sort((a, b) => b.at - a.at);
  let code = source;
  for (const edit of edits) code = code.slice(0, edit.at) + edit.text + code.slice(edit.at + edit.remove);
  return { code, fixes: edits.map(edit => edit.fix).reverse() };
}

/** Line-numbered hints for common generated-code mistakes that cannot be fixed mechanically. */
export function diagnoseGeneratedCode(source: string): CodeIssue[] {
  const issues: CodeIssue[] = [];
  const lines = source.split("\n");
  lines.forEach((text, index) => {
    const line = index + 1;
    if (/\.(beginFill|endFill|drawRect|drawCircle|drawEllipse|drawPolygon|drawRoundedRect|lineStyle)\s*\(/.test(text))
      issues.push({ line, message: "PixiJS v7 Graphics API; use v8 g.rect()/circle()/poly().fill({color}) and moveTo().lineTo().stroke({color,width})" });
  });
  let tokens: Token[] = [];
  const comments: number[] = [];
  try { tokens = tokenize(source, comments); } catch { /* reported below */ }
  for (const at of comments.slice(0, 3))
    issues.push({ line: lineOf(source, at), message: "`//` line comment; if whitespace is flattened it comments out the rest of the code, use /* */" });
  const pairs: Record<string, string> = { ")": "(", "]": "[", "}": "{" };
  const open: Token[] = [];
  for (const t of tokens) {
    if (t.type !== "punct") continue;
    if ("([{".includes(t.value)) open.push(t);
    else if (pairs[t.value]) {
      const top = open.pop();
      if (!top || top.value !== pairs[t.value]) {
        issues.push({ line: lineOf(source, t.start), message: "unbalanced `" + t.value + "`" + (top ? " (opened with `" + top.value + "` on line " + lineOf(source, top.start) + ")" : "") });
        break;
      }
    }
  }
  if (open.length) issues.push({ line: lineOf(source, open[open.length - 1].start), message: "`" + open[open.length - 1].value + "` is never closed" });
  return issues;
}

export const formatIssues = (issues: CodeIssue[]) => issues.slice(0, 8).map(issue => "line " + issue.line + ": " + issue.message).join("\n");
