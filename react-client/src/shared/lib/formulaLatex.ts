/** Presentation only. Never evaluates expressions or writes schema mathematics. */
import { FormulaError, parseFormula } from "./equationAst.ts";

const GREEK = new Set("alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi pi rho sigma tau upsilon phi chi psi omega Gamma Delta Theta Lambda Xi Pi Sigma Upsilon Phi Psi Omega".split(" "));
const PRECEDENCE: Record<string, number> = { add: 1, sub: 1, mul: 2, div: 2, neg: 3, pow: 4 };
const FUNCTIONS: Record<string, string> = { sin: "sin", cos: "cos", tan: "tan", exp: "exp", log: "ln", asin: "arcsin", acos: "arccos", atan: "arctan", min: "min", max: "max" };
const MAX_LENGTH = 12000;
const MAX_DEPTH = 64;
export function symbolLatex(name: string): string {
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)) throw new FormulaError("Tên đại lượng không hợp lệ");
  const atom = (part: string): string => {
    if (GREEK.has(part)) return `\\${part}`;
    const delta = part.match(/^Delta([A-Z][A-Za-z]*)(\d*)$/);
    if (delta) return `\\Delta ${atom(delta[1] + delta[2])}`;
    const numbered = part.match(/^([A-Za-z]+)(\d+)$/);
    if (numbered) return `${atom(numbered[1])}_{${numbered[2]}}`;
    if (/^\d+$/.test(part)) return part;
    return part.length === 1 ? part : `\\mathrm{${part || "\\_"}}`;
  };
  const [base, ...subscript] = name.split("_");
  const numberedBase = subscript.length && base.match(/^([A-Za-z]+)(\d+)$/);
  if (numberedBase) return `${atom(numberedBase[1])}_{${[numberedBase[2], ...subscript].map(atom).join("\\,")}}`;
  return atom(base) + (subscript.length ? `_{${subscript.map(atom).join("\\,")}}` : "");
}

/** Walk the actual AST; fractions, signs and powers keep their original grouping. */
export function astLatex(ast: unknown): string {
  let nodes = 0;
  const walk = (node: unknown, depth: number): string => {
    if (++nodes > 2000 || depth > MAX_DEPTH) throw new FormulaError("Công thức quá phức tạp để hiển thị");
    if (typeof node === "number") {
      if (!Number.isFinite(node)) throw new FormulaError("Giá trị không hữu hạn");
      return String(node).replace(/e([+-]?\d+)$/i, "\\times 10^{$1}");
    }
    if (typeof node === "string") return symbolLatex(node);
    if (!Array.isArray(node) || node.length < 2 || node.length > 4) throw new FormulaError("Công thức lưu sai định dạng");
    const [op, a, b, c] = node;
    const priority = (value: unknown): number => typeof value === "number" && value < 0 ? 3 : Array.isArray(value) ? PRECEDENCE[value[0]] ?? 5 : 5;
    const wrap = (value: unknown, minimum: number) => {
      const text = walk(value, depth + 1);
      return priority(value) < minimum ? `\\left(${text}\\right)` : text;
    };
    if (node.length === 2) {
      if (op === "neg") return `-${wrap(a, 4)}`;
      if (op === "sqrt") return `\\sqrt{${walk(a, depth + 1)}}`;
      if (op === "abs") return `\\left|${walk(a, depth + 1)}\\right|`;
      if (op === "floor") return `\\left\\lfloor ${walk(a, depth + 1)}\\right\\rfloor`;
      if (op === "sign") return `\\operatorname{sgn}\\left(${walk(a, depth + 1)}\\right)`;
      if (FUNCTIONS[op]) return `\\${FUNCTIONS[op]}\\left(${walk(a, depth + 1)}\\right)`;
    } else if (node.length === 4) {
      if (op === "if") return `\\begin{cases} ${walk(b, depth + 1)} & \\text{khi } ${walk(a, depth + 1)} > 0 \\\\ ${walk(c, depth + 1)} & \\text{còn lại} \\end{cases}`;
    } else {
      if (op === "min" || op === "max") return `\\${op}\\left(${walk(a, depth + 1)},\\, ${walk(b, depth + 1)}\\right)`;
      if (op === "mod") return `${wrap(a, 3)} \\bmod ${wrap(b, 3)}`;
      if (op === "atan2") return `\\operatorname{atan2}\\left(${walk(a, depth + 1)},\\, ${walk(b, depth + 1)}\\right)`;
      if (op === "div") return `\\frac{${walk(a, depth + 1)}}{${walk(b, depth + 1)}}`;
      if (op === "pow") return `${wrap(a, 5)}^{${walk(b, depth + 1)}}`;
      if (op === "add") return `${wrap(a, 1)} + ${wrap(b, 2)}`;
      if (op === "sub") return `${wrap(a, 1)} - ${wrap(b, 2)}`;
      if (op === "mul") return `${wrap(a, 2)} \\cdot ${wrap(b, 3)}`;
    }
    throw new FormulaError(`Phép toán hiển thị chưa hỗ trợ: ${String(op)}`);
  };
  return walk(ast, 0);
}

export function astEquationLatex(name: string, ast: unknown, derivative = false): string {
  const variable = symbolLatex(name);
  return `${derivative ? `\\frac{\\mathrm{d}${variable}}{\\mathrm{d}t}` : variable} = ${astLatex(ast)}`;
}

type Token = { text: string; kind: "name" | "number" | "symbol" };
type DisplayNode = { text: string; priority: number };
/** Additional textbook notation is display-only: calls, derivatives, bars and relations.
 * The existing schema parser is not relaxed and receives none of these nodes. */
function displayExpression(source: string): string {
  if (source.length > MAX_LENGTH) throw new FormulaError("Công thức quá dài");
  const tokens: Token[] = [];
  const pattern = /\s*(?:(\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|\.\d+(?:[eE][+-]?\d+)?)|([A-Za-z_][A-Za-z0-9_]*)|(\.\.\.|<=|>=|!=|[-+*/^()|,'=~<>]))/y;
  let position = 0;
  while (position < source.length && source.slice(position).trim()) {
    pattern.lastIndex = position;
    const match = pattern.exec(source);
    if (!match || tokens.length > 2000) throw new FormulaError("Dòng này chứa lời giải thích hoặc cú pháp chưa hỗ trợ");
    tokens.push({ text: match[1] ?? match[2] ?? match[3], kind: match[1] ? "number" : match[2] ? "name" : "symbol" });
    position = pattern.lastIndex;
  }
  let index = 0, depth = 0;
  const peek = (text: string) => tokens[index]?.text === text;
  const expect = (text: string) => { if (!peek(text)) throw new FormulaError(`Thiếu ${text}`); index++; };
  const wrap = (node: DisplayNode, minimum: number) => node.priority < minimum ? `\\left(${node.text}\\right)` : node.text;
  const primary = (): DisplayNode => {
    if (++depth > MAX_DEPTH) throw new FormulaError("Công thức quá sâu");
    const token = tokens[index++];
    if (!token) throw new FormulaError("Công thức chưa hoàn chỉnh");
    let text: string;
    let priority = 5;
    if (token.kind === "number") text = astLatex(Number(token.text));
    else if (token.text === "...") text = "\\cdots";
    else if (token.text === "(") { text = relation(); expect(")"); text = `\\left(${text}\\right)`; }
    else if (token.text === "|") { text = sum().text; expect("|"); text = `\\left|${text}\\right|`; }
    else if (token.kind === "name") {
      const derivative = token.text.match(/^d(\d*)([A-Za-z_][A-Za-z0-9_]*)$/);
      // Conventional differential notation only; an ordinary identifier such as
      // "distance / dt" must not become the derivative of a made-up "istance".
      const differential = derivative && (derivative[1] || GREEK.has(derivative[2]) || /^[A-Za-z](?:\d+|_[A-Za-z0-9_]+)?$/.test(derivative[2]));
      if (differential && derivative && peek("/") && tokens[index + 1]?.text === `dt${derivative[1]}`) {
        index += 2;
        const order = derivative[1];
        text = `\\frac{\\mathrm{d}${order ? `^{${order}}` : ""}${symbolLatex(derivative[2])}}{\\mathrm{d}t${order ? `^{${order}}` : ""}}`;
      } else if (token.text === "Delta" && tokens[index]?.kind === "name") text = `\\Delta ${symbolLatex(tokens[index++].text)}`;
      else if (peek("(")) {
        index++;
        const args = [relation()];
        while (peek(",")) { index++; args.push(relation()); }
        expect(")");
        if (token.text === "sqrt" && args.length === 1) text = `\\sqrt{${args[0]}}`;
        else if (token.text === "abs" && args.length === 1) text = `\\left|${args[0]}\\right|`;
        else text = `${FUNCTIONS[token.text] ? `\\${FUNCTIONS[token.text]}` : symbolLatex(token.text)}\\left(${args.join(", ")}\\right)`;
      } else text = symbolLatex(token.text);
      let primes = 0;
      while (peek("'")) { index++; primes++; }
      if (primes) { text += `^{${"\\prime ".repeat(primes).trim()}}`; priority = 4; }
    } else throw new FormulaError("Cú pháp không được hỗ trợ");
    depth--;
    return { text, priority };
  };
  const power = (): DisplayNode => {
    const base = primary();
    if (!peek("^")) return base;
    index++;
    return { text: `${wrap(base, 5)}^{${unary().text}}`, priority: 4 };
  };
  const unary = (): DisplayNode => {
    if (!peek("-") && !peek("+")) return power();
    const sign = tokens[index++].text;
    // Each recursive sign/power consumes a token. Bound input before recursion.
    if (index > MAX_DEPTH && (peek("-") || peek("+"))) throw new FormulaError("Quá nhiều dấu lồng nhau");
    const operand = unary();
    return { text: `${sign}${wrap(operand, 4)}`, priority: 3 };
  };
  const product = (): DisplayNode => {
    let left = unary();
    while (peek("*") || peek("/")) {
      const op = tokens[index++].text, right = unary();
      left = { text: op === "/" ? `\\frac{${left.text}}{${right.text}}` : `${wrap(left, 2)} \\cdot ${wrap(right, 3)}`, priority: 2 };
    }
    return left;
  };
  const sum = (): DisplayNode => {
    let left = product();
    while (peek("+") || peek("-")) {
      const op = tokens[index++].text, right = product();
      left = { text: `${wrap(left, 1)} ${op} ${wrap(right, 2)}`, priority: 1 };
    }
    return left;
  };
  const relation = (): string => {
    let text = sum().text;
    const signs: Record<string, string> = { "=": "=", "~": "\\sim", "<=": "\\le", ">=": "\\ge", "!=": "\\ne", "<": "<", ">": ">" };
    while (signs[tokens[index]?.text]) { const sign = signs[tokens[index++].text]; text += ` ${sign} ${sum().text}`; }
    return text;
  };
  const text = relation();
  if (index !== tokens.length) throw new FormulaError("Không đọc được toàn bộ công thức");
  return text;
}

export type PresentedEquation = { latex: string; before?: string; after?: string };
export function equationLatex(source: string): PresentedEquation {
  const normalized = source.replace(/×|·/g, "*").replace(/÷/g, "/").replace(/−/g, "-").trim();
  if (!normalized || normalized.length > MAX_LENGTH) throw new FormulaError("Công thức trống hoặc quá dài");
  // Reuse the established schema grammar for ordinary expressions.
  const convert = (text: string): string => {
    try { return astLatex(parseFormula(text)); } catch { return displayExpression(text); }
  };
  try { return { latex: convert(normalized) }; } catch { /* Try separating prose without changing mathematics. */ }
  const colon = normalized.indexOf(":");
  if (colon >= 0) {
    const rest = equationLatex(normalized.slice(colon + 1));
    return { ...rest, before: normalized.slice(0, colon + 1) };
  }
  // A fully parsed prefix plus a clearly textual suffix is an annotation.
  // Never drop an unparsed mathematical operator or a lone symbol.
  for (const boundary of [...normalized.matchAll(/\s+/g)].reverse()) {
    const tail = normalized.slice(boundary.index! + boundary[0].length);
    if (!/^(?:\([A-Za-zÀ-ỹ]|[A-Za-zÀ-ỹ])/.test(tail) || !/[À-ỹ]|[A-Za-z]+[ ,]+[A-Za-z]+/.test(tail)) continue;
    try { return { latex: convert(normalized.slice(0, boundary.index)), after: tail }; } catch { /* Try another prose boundary. */ }
  }
  throw new FormulaError("Giữ nguyên dòng công thức chưa hỗ trợ");
}
