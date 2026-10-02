/** Formula text ⇄ equation AST used by the schema equation runtime.
 *  The backend evaluates ASTs like ["div", ["sub", "E", "u"], "R"]; reviewers read and type "(E - u) / R". */

export type Ast = number | string | [string, Ast] | [string, Ast, Ast];

export const FUNCTIONS = ["sin", "cos", "sqrt", "exp", "abs", "log", "asin", "acos", "atan"] as const;
const BINARY: Record<string, { symbol: string; level: number }> = {
  add: { symbol: "+", level: 1 }, sub: { symbol: "-", level: 1 },
  mul: { symbol: "*", level: 2 }, div: { symbol: "/", level: 2 },
  pow: { symbol: "^", level: 4 },
};
const NEG_LEVEL = 3;
const ATOM_LEVEL = 5;

export class FormulaError extends Error { }

function level(ast: Ast): number {
  if (typeof ast === "number") return ast < 0 ? NEG_LEVEL : ATOM_LEVEL;
  if (typeof ast === "string") return ATOM_LEVEL;
  if (ast[0] === "neg") return NEG_LEVEL;
  return BINARY[ast[0]]?.level ?? ATOM_LEVEL;
}

/** Renders an AST as a formula; parseFormula(formatFormula(ast)) evaluates identically. */
export function formatFormula(ast: unknown): string {
  if (typeof ast === "number") return String(ast);
  if (typeof ast === "string") return ast;
  if (!Array.isArray(ast) || ast.length < 2 || ast.length > 3 || typeof ast[0] !== "string") throw new FormulaError("Công thức lưu sai định dạng");
  const [op, a, b] = ast as [string, Ast, Ast | undefined];
  const wrap = (node: Ast, minimum: number) => { const text = formatFormula(node); return level(node) < minimum ? `(${text})` : text; };
  if (op === "neg") { if (ast.length !== 2) throw new FormulaError("Công thức lưu sai định dạng"); return `-${wrap(a, NEG_LEVEL + 1)}`; }
  if ((FUNCTIONS as readonly string[]).includes(op)) { if (ast.length !== 2) throw new FormulaError("Công thức lưu sai định dạng"); return `${op}(${formatFormula(a)})`; }
  const binary = BINARY[op];
  if (!binary || b === undefined) throw new FormulaError(`Phép toán không được hỗ trợ: ${op}`);
  // Left-associative operators keep an equal-level right operand in brackets; a power keeps its base in brackets.
  return op === "pow"
    ? `${wrap(a, binary.level + 1)}^${wrap(b, NEG_LEVEL)}`
    : `${wrap(a, binary.level)} ${binary.symbol} ${wrap(b, binary.level + 1)}`;
}

type Token = { kind: "number" | "name" | "symbol"; text: string; at: number };

function tokenize(source: string): Token[] {
  const tokens: Token[] = [];
  const pattern = /\s*(?:(\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|\.\d+(?:[eE][+-]?\d+)?)|([A-Za-z_][A-Za-z0-9_]*)|([-+*/^()]))/y;
  let at = 0;
  while (at < source.length) {
    if (/^\s*$/.test(source.slice(at))) break;
    pattern.lastIndex = at;
    const match = pattern.exec(source);
    if (!match) throw new FormulaError(`Kí tự không hợp lệ tại vị trí ${at + 1}: “${source.slice(at).trim()[0]}”`);
    tokens.push({ kind: match[1] ? "number" : match[2] ? "name" : "symbol", text: match[1] ?? match[2] ?? match[3], at });
    at = pattern.lastIndex;
  }
  return tokens;
}

/** Parses "a * sin(w * t) + 2" into the AST the backend runs. Throws FormulaError with a Vietnamese message. */
export function parseFormula(source: string): Ast {
  const tokens = tokenize(source.replace(/×|·/g, "*").replace(/÷/g, "/").replace(/−/g, "-"));
  if (tokens.length === 0) throw new FormulaError("Công thức đang để trống");
  let index = 0;
  const peek = (text: string) => tokens[index]?.kind === "symbol" && tokens[index].text === text;
  const expect = (text: string) => { if (!peek(text)) throw new FormulaError(`Thiếu “${text}”`); index++; };

  const atom = (): Ast => {
    const token = tokens[index];
    if (!token) throw new FormulaError("Công thức kết thúc giữa chừng");
    index++;
    if (token.kind === "number") return Number(token.text);
    if (token.kind === "name") {
      if (!peek("(")) return token.text;
      if (!(FUNCTIONS as readonly string[]).includes(token.text)) throw new FormulaError(`Hàm không được hỗ trợ: ${token.text}. Dùng: ${FUNCTIONS.join(", ")}`);
      index++;
      const argument = sum();
      expect(")");
      return [token.text, argument];
    }
    if (token.text === "(") { const inner = sum(); expect(")"); return inner; }
    throw new FormulaError(`Không mong đợi “${token.text}”`);
  };
  const power = (): Ast => { const base = atom(); if (!peek("^")) return base; index++; return ["pow", base, unary()]; };
  const unary = (): Ast => {
    if (peek("+")) { index++; return unary(); }
    if (!peek("-")) return power();
    index++;
    const operand = unary();
    return typeof operand === "number" ? -operand : ["neg", operand];
  };
  const product = (): Ast => {
    let left = unary();
    while (peek("*") || peek("/")) { const op = tokens[index++].text === "*" ? "mul" : "div"; left = [op, left, unary()]; }
    return left;
  };
  const sum = (): Ast => {
    let left = product();
    while (peek("+") || peek("-")) { const op = tokens[index++].text === "+" ? "add" : "sub"; left = [op, left, product()]; }
    return left;
  };

  const result = sum();
  if (index < tokens.length) throw new FormulaError(`Không mong đợi “${tokens[index].text}”`);
  return result;
}

/** Quantity names a formula reads. */
export function formulaSymbols(ast: unknown, into: Set<string> = new Set()): Set<string> {
  if (typeof ast === "string") into.add(ast);
  else if (Array.isArray(ast)) ast.slice(1).forEach(part => formulaSymbols(part, into));
  return into;
}

export const FORMULA_PHASES = [
  { key: "initial", label: "Trạng thái ban đầu (t = 0)", hint: "Giá trị đầu của từng biến trạng thái." },
  { key: "rates", label: "Giải số — tốc độ biến thiên (RK4)", hint: "Đạo hàm theo thời gian của từng biến trạng thái; bộ giải số tích phân các công thức này." },
  { key: "outputs", label: "Đại lượng xuất ra", hint: "Tính từ biến trạng thái tại mỗi thời điểm, dùng để vẽ mô phỏng." },
  { key: "closedForm", label: "Giải tích — nghiệm đóng", hint: "Công thức nghiệm chính xác theo t. Nếu có, hệ thống dùng nó để đối chiếu kết quả giải số." },
  { key: "invariants", label: "Bất biến", hint: "Biểu thức phải giữ nguyên giá trị suốt quá trình (ví dụ cơ năng)." },
] as const;

type Capability = Record<string, unknown>;
const record = (value: unknown): Record<string, unknown> => value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
const keysOf = (rows: unknown): string[] => Array.isArray(rows) ? rows.map(row => String(record(row).key ?? "")).filter(Boolean) : [];

export function capabilityMath(capability: Capability): Record<string, Record<string, unknown>> {
  const math = record(record(capability.execution).math);
  return Object.fromEntries(FORMULA_PHASES.map(phase => [phase.key, record(math[phase.key])]));
}

/** Problems the runtime would reject, phrased for a physics reviewer. Empty when the capability can run.
 *  Mirrors SchemaEquationRuntime: every formula name needs a declared unit, closed forms must cover every
 *  output, and each ODE state must also be an output so the residual check can read it. */
export function capabilityProblems(capability: Capability): string[] {
  const problems: string[] = [];
  const math = capabilityMath(capability);
  const inputs = keysOf(capability.canonicalInputs);
  const declared = keysOf(capability.outputs);
  const units = new Set(["t", ...inputs, ...declared]);
  const states = Object.keys(math.initial);
  const produced = Object.keys(math.outputs);
  if (!String(capability.capabilityId ?? "").trim()) problems.push("Chưa có mã bài toán.");
  if (states.length === 0) problems.push("Chưa có trạng thái ban đầu — bộ giải số cần ít nhất một biến trạng thái.");
  for (const state of states) {
    if (!(state in math.rates)) problems.push(`Biến trạng thái “${state}” chưa có công thức tốc độ biến thiên.`);
    if (!produced.includes(state)) problems.push(`Biến trạng thái “${state}” cần có trong Đại lượng xuất ra (ghi công thức là chính nó: ${state}).`);
  }
  for (const rate of Object.keys(math.rates)) if (!states.includes(rate)) problems.push(`Tốc độ biến thiên “${rate}” không ứng với biến trạng thái nào.`);
  if (Object.keys(math.closedForm).length > 0) for (const output of produced) if (!(output in math.closedForm)) problems.push(`Nghiệm giải tích còn thiếu công thức cho “${output}”.`);
  const scope: Record<string, string[]> = { initial: [], rates: states, outputs: states, closedForm: [], invariants: produced };
  for (const phase of FORMULA_PHASES) {
    const known = new Set(["t", ...inputs, ...scope[phase.key], ...Object.keys(math[phase.key])]);
    for (const [name, ast] of Object.entries(math[phase.key])) {
      if (phase.key !== "invariants" && !units.has(name)) problems.push(`“${name}” chưa khai báo đơn vị trong bảng Đại lượng xuất ra.`);
      for (const symbol of formulaSymbols(ast)) {
        if (!known.has(symbol)) problems.push(`${phase.label} → “${name}”: không có đại lượng “${symbol}” ở bước này.`);
        else if (!units.has(symbol)) problems.push(`“${symbol}” chưa khai báo đơn vị trong bảng Đại lượng xuất ra.`);
      }
    }
  }
  return [...new Set(problems)];
}

/** Blank capability that satisfies topic-pack meta-schema 2.0. */
export function newCapability(objectTypes: string[]): Capability {
  return {
    capabilityId: "", title: "", version: "1.0",
    applicability: { objectTypes, quantities: [] },
    canonicalInputs: [], outputs: [], assumptions: [],
    equationSet: { canonical: [], derived: [] },
    execution: {
      primary: "numerical",
      numerical: { solverId: "schema_ast_rk4", method: "rk4" },
      closedForm: null,
      math: { initial: {}, rates: {}, outputs: {} },
    },
    validation: { absoluteTolerance: 0.0001, relativeTolerance: 0.0001, strategy: "step_refinement", invariants: ["finite_output"], benchmarks: [] },
    validityDomain: { durationPositive: true },
    rendererBindings: [],
  };
}

/** Writes one phase back, keeping execution metadata consistent with whether a closed form exists. */
export function withPhase(capability: Capability, phase: string, formulas: Record<string, unknown>): Capability {
  const execution = { ...record(capability.execution) };
  const math = { ...record(execution.math) };
  if (Object.keys(formulas).length === 0 && (phase === "closedForm" || phase === "invariants")) delete math[phase]; else math[phase] = formulas;
  execution.math = math;
  const next: Capability = { ...capability, execution };
  if (phase === "closedForm") {
    const has = Object.keys(formulas).length > 0;
    if (has && !record(execution.closedForm).solverId) execution.closedForm = { solverId: "schema_ast_reference", method: "approved_ast" };
    if (!has) execution.closedForm = null;
    const validation = { ...record(capability.validation) };
    if (has && validation.strategy === "step_refinement") validation.strategy = "closed_form_checkpoints";
    if (!has && validation.strategy === "closed_form_checkpoints") validation.strategy = "step_refinement";
    next.validation = validation;
  }
  return next;
}
