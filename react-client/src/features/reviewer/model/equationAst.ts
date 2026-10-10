import { formulaSymbols } from "../../../shared/lib/equationAst.ts";
export { FUNCTIONS, CALLS, FormulaError, formatFormula, parseFormula, formulaSymbols } from "../../../shared/lib/equationAst.ts";
export type { Ast } from "../../../shared/lib/equationAst.ts";

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
