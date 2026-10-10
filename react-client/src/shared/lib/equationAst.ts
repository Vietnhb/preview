/** Formula text ⇄ equation AST used by the schema equation runtime.
 *  The backend evaluates ASTs like ["div", ["sub", "E", "u"], "R"]; reviewers read and type "(E - u) / R". */

export type Ast = number | string | [string, ...Ast[]];

export const FUNCTIONS = ["sin", "cos", "tan", "sqrt", "exp", "abs", "log", "asin", "acos", "atan", "sign", "floor"] as const;
/** Functions of several values, with how many each takes: if(condition, value when positive, value otherwise). */
export const CALLS: Record<string, number> = { min: 2, max: 2, mod: 2, atan2: 2, if: 3 };
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
  if (!Array.isArray(ast) || ast.length < 2 || ast.length > 4 || typeof ast[0] !== "string") throw new FormulaError("Công thức lưu sai định dạng");
  const [op, a, b] = ast as [string, Ast, Ast | undefined];
  if (op in CALLS) {
    if (ast.length !== CALLS[op] + 1) throw new FormulaError("Công thức lưu sai định dạng");
    return `${op}(${ast.slice(1).map(formatFormula).join(", ")})`;
  }
  if (ast.length > 3) throw new FormulaError("Công thức lưu sai định dạng");
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
  const pattern = /\s*(?:(\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|\.\d+(?:[eE][+-]?\d+)?)|([A-Za-z_][A-Za-z0-9_]*)|([-+*/^(),]))/y;
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
      const takes = token.text in CALLS ? CALLS[token.text] : (FUNCTIONS as readonly string[]).includes(token.text) ? 1 : 0;
      if (!takes) throw new FormulaError(`Hàm không được hỗ trợ: ${token.text}. Dùng: ${[...FUNCTIONS, ...Object.keys(CALLS)].join(", ")}`);
      index++;
      const values = [sum()];
      while (peek(",")) { index++; values.push(sum()); }
      expect(")");
      if (values.length !== takes) throw new FormulaError(`Hàm ${token.text} cần ${takes} giá trị, đang có ${values.length}`);
      return [token.text, ...values];
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

