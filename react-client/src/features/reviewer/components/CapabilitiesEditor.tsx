import { useState } from "react";
import { Badge, Button, Text, TextArea, TextField } from "@radix-ui/themes";
import { normalizeNumbers, objectRows, RowsEditor } from "./JsonEditor";
import { capabilityMath, capabilityProblems, FORMULA_PHASES, formatFormula, FormulaError, newCapability, parseFormula, withPhase } from "../model/equationAst";

type Capability = Record<string, unknown>;
type Formulas = Record<string, unknown>;

const record = (value: unknown): Record<string, unknown> => value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
const safeFormat = (ast: unknown) => { try { return formatFormula(ast); } catch { return JSON.stringify(ast); } };
const titleOf = (capability: Capability) => String(capability.title || capability.capabilityId || "Bài toán chưa đặt tên");

function SolverBadges({ capability }: Readonly<{ capability: Capability }>) {
  const math = capabilityMath(capability);
  const analytic = Object.keys(math.closedForm).length > 0;
  return <>
    <Badge color="indigo" variant="soft" size="1">Giải số RK4</Badge>
    <Badge color={analytic ? "green" : "gray"} variant="soft" size="1">{analytic ? "Có nghiệm giải tích" : "Không có nghiệm giải tích"}</Badge>
  </>;
}

/** Read-only list shown in the topic detail dialog. */
export function CapabilitiesSummary({ capabilities }: Readonly<{ capabilities: Capability[] }>) {
  if (capabilities.length === 0) return <Text size="2" color="gray">Chưa có bài toán mô phỏng nào.</Text>;
  return <div className="reviewer-capabilities">{capabilities.map((capability, index) => {
    const math = capabilityMath(capability);
    const validation = record(capability.validation);
    return <details key={index} className="reviewer-capability">
      <summary><span className="reviewer-capability-title">{titleOf(capability)}</span><SolverBadges capability={capability} /></summary>
      <Text as="p" size="1" color="gray">Mã: <code>{String(capability.capabilityId ?? "—")}</code> · Sai số cho phép: tuyệt đối {String(validation.absoluteTolerance ?? "—")}, tương đối {String(validation.relativeTolerance ?? "—")}</Text>
      <QuantityLine label="Đầu vào" rows={objectRows(capability.canonicalInputs)} />
      <QuantityLine label="Xuất ra" rows={objectRows(capability.outputs)} />
      {FORMULA_PHASES.filter(phase => Object.keys(math[phase.key]).length > 0).map(phase => <div key={phase.key} className="reviewer-formula-group">
        <Text as="div" size="2" weight="medium">{phase.label}</Text>
        <dl className="reviewer-formulas">{Object.entries(math[phase.key]).map(([name, ast]) => <div key={name}><dt>{phase.key === "rates" ? `d(${name})/dt` : name}</dt><dd>{safeFormat(ast)}</dd></div>)}</dl>
      </div>)}
      {Array.isArray(capability.assumptions) && capability.assumptions.length > 0 && <Text as="p" size="1" color="gray">Giả thiết: {capability.assumptions.map(String).join(" ")}</Text>}
    </details>;
  })}</div>;
}

function QuantityLine({ label, rows }: Readonly<{ label: string; rows: Record<string, unknown>[] }>) {
  if (rows.length === 0) return null;
  return <Text as="p" size="1" color="gray">{label}: {rows.map(row => `${String(row.key)} (${String(row.unit ?? "?")})`).join(", ")}</Text>;
}

/** Add / edit / remove the simulation problems of a topic, including their numerical and analytical formulas. */
export function CapabilitiesEditor({ capabilities, objectTypes, onChange }: Readonly<{ capabilities: Capability[]; objectTypes: string[]; onChange: (next: Capability[]) => void }>) {
  const [open, setOpen] = useState<number | null>(null);
  const replace = (index: number, next: Capability) => onChange(capabilities.map((item, i) => i === index ? next : item));
  return <div className="reviewer-capabilities">
    {capabilities.length === 0 && <Text as="p" size="2" color="gray" className="reviewer-rows-empty">Chưa có bài toán mô phỏng nào.</Text>}
    {capabilities.map((capability, index) => {
      const problems = capabilityProblems(capability);
      return <details key={index} className="reviewer-capability" open={open === index} onToggle={event => { if (event.currentTarget.open) setOpen(index); else if (open === index) setOpen(null); }}>
        <summary><span className="reviewer-capability-title">{titleOf(capability)}</span><SolverBadges capability={capability} />{problems.length > 0 && <Badge color="red" variant="soft" size="1">{problems.length} lỗi</Badge>}</summary>
        {open === index && <CapabilityForm capability={capability} problems={problems} onChange={next => replace(index, next)}
          onRemove={() => { setOpen(null); onChange(capabilities.filter((_, i) => i !== index)); }} />}
      </details>;
    })}
    <Button type="button" size="1" variant="soft" onClick={() => { onChange([...capabilities, newCapability(objectTypes)]); setOpen(capabilities.length); }}>+ Thêm bài toán mô phỏng</Button>
  </div>;
}

function CapabilityForm({ capability, problems, onChange, onRemove }: Readonly<{ capability: Capability; problems: string[]; onChange: (next: Capability) => void; onRemove: () => void }>) {
  const [confirming, setConfirming] = useState(false);
  const set = (key: string, value: unknown) => onChange({ ...capability, [key]: value });
  const math = capabilityMath(capability);
  const validation = record(capability.validation);
  const setTolerance = (key: string, raw: string) => { const parsed = Number(raw.replace(",", ".")); set("validation", { ...validation, [key]: raw.trim() !== "" && Number.isFinite(parsed) ? parsed : raw }); };
  const assumptions = Array.isArray(capability.assumptions) ? capability.assumptions.map(String).join("\n") : "";
  return <div className="reviewer-capability-body">
    <div className="reviewer-form-row">
      <label className="reviewer-field"><span>Tên bài toán</span><TextField.Root size="2" value={String(capability.title ?? "")} placeholder="Nạp / xả tụ điện qua điện trở" onChange={event => set("title", event.target.value)} /></label>
      <label className="reviewer-field"><span>Mã bài toán</span><TextField.Root size="2" value={String(capability.capabilityId ?? "")} placeholder="rc_circuit" onChange={event => set("capabilityId", event.target.value.trim())} /></label>
    </div>

    <Text as="div" size="2" weight="medium">Đại lượng đầu vào</Text>
    <RowsEditor rows={objectRows(capability.canonicalInputs)} onChange={rows => set("canonicalInputs", normalizeNumbers(rows, ["min", "max", "defaultValue"]))} addLabel="Thêm đầu vào" empty="Chưa có đại lượng đầu vào."
      columns={[{ key: "key", label: "Mã đại lượng", placeholder: "resistance", width: "34%" }, { key: "unit", label: "Đơn vị chuẩn", placeholder: "ohm", width: "18%" }, { key: "min", label: "Nhỏ nhất", type: "number" }, { key: "max", label: "Lớn nhất", type: "number" }, { key: "defaultValue", label: "Mặc định", type: "number" }]} />

    <Text as="div" size="2" weight="medium">Đại lượng xuất ra</Text>
    <Text as="p" size="1" color="gray">Mọi tên dùng trong công thức (kể cả biến trạng thái) phải có đơn vị ở đây để hệ thống kiểm tra thứ nguyên.</Text>
    <RowsEditor rows={objectRows(capability.outputs)} onChange={rows => set("outputs", rows)} addLabel="Thêm đại lượng xuất ra" empty="Chưa có đại lượng xuất ra."
      columns={[{ key: "key", label: "Mã đại lượng", placeholder: "voltage", width: "50%" }, { key: "unit", label: "Đơn vị chuẩn", placeholder: "V" }]} />

    <Text as="p" size="1" color="gray" className="reviewer-formula-help">Viết công thức như trên giấy: <code>+ - * / ^</code>, ngoặc tròn, <code>t</code> là thời gian. Hàm dùng được: sin, cos, sqrt, exp, abs, log, asin, acos, atan (góc tính bằng radian).</Text>
    {FORMULA_PHASES.map(phase => <FormulaGroup key={phase.key} label={phase.label} hint={phase.hint} rate={phase.key === "rates"} formulas={math[phase.key]} onChange={formulas => onChange(withPhase(capability, phase.key, formulas))} />)}

    <div className="reviewer-form-row">
      <label className="reviewer-field"><span>Sai số tuyệt đối cho phép</span><TextField.Root size="2" inputMode="decimal" value={String(validation.absoluteTolerance ?? "")} onChange={event => setTolerance("absoluteTolerance", event.target.value)} /></label>
      <label className="reviewer-field"><span>Sai số tương đối cho phép</span><TextField.Root size="2" inputMode="decimal" value={String(validation.relativeTolerance ?? "")} onChange={event => setTolerance("relativeTolerance", event.target.value)} /></label>
    </div>
    <label className="reviewer-field"><span>Giả thiết (mỗi dòng một ý)</span><TextArea size="2" rows={2} defaultValue={assumptions} placeholder="Bỏ qua điện trở dây nối…" onChange={event => set("assumptions", event.target.value.split("\n").map(line => line.trim()).filter(Boolean))} /></label>

    {problems.length > 0 && <div className="reviewer-note reviewer-note-danger" role="alert"><strong>Bài toán này chưa chạy được:</strong><ul>{problems.map(problem => <li key={problem}>{problem}</li>)}</ul></div>}
    <div className="reviewer-capability-footer">
      {confirming
        ? <><Text size="2">Xóa bài toán này khỏi chủ đề?</Text><Button type="button" size="1" color="red" onClick={onRemove}>Xóa</Button><Button type="button" size="1" variant="soft" color="gray" onClick={() => setConfirming(false)}>Giữ lại</Button></>
        : <Button type="button" size="1" variant="soft" color="red" onClick={() => setConfirming(true)}>Xóa bài toán</Button>}
    </div>
  </div>;
}

type Row = { id: number; name: string; text: string };
let nextRowId = 0;
const toRows = (formulas: Formulas): Row[] => Object.entries(formulas).map(([name, ast]) => ({ id: nextRowId++, name, text: safeFormat(ast) }));

function parseRows(rows: Row[]): { formulas: Formulas; errors: Record<number, string> } {
  const formulas: Formulas = {};
  const errors: Record<number, string> = {};
  for (const row of rows) {
    const name = row.name.trim();
    if (!name && !row.text.trim()) continue;
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)) { errors[row.id] = "Tên chỉ gồm chữ không dấu, số và dấu gạch dưới."; continue; }
    if (name in formulas) { errors[row.id] = `Trùng tên “${name}”.`; continue; }
    try { formulas[name] = parseFormula(row.text); } catch (exception) { errors[row.id] = exception instanceof FormulaError ? exception.message : "Công thức không hợp lệ."; }
  }
  return { formulas, errors };
}

/** One formula per row. Rows keep the reviewer's own text; the stored AST is updated only while every row is valid. */
function FormulaGroup({ label, hint, rate, formulas, onChange }: Readonly<{ label: string; hint: string; rate: boolean; formulas: Formulas; onChange: (next: Formulas) => void }>) {
  const serialized = JSON.stringify(formulas);
  const [state, setState] = useState(() => ({ rows: toRows(formulas), base: serialized }));
  // Formulas changed elsewhere (advanced JSON): drop the local text so a later keystroke cannot overwrite that edit.
  const rows = state.base === serialized ? state.rows : toRows(formulas);
  const { errors } = parseRows(rows);
  const setRows = (next: Row[], base = serialized) => setState({ rows: next, base });
  const update = (next: Row[]) => {
    const parsed = parseRows(next);
    if (Object.keys(parsed.errors).length > 0) { setRows(next); return; }
    setRows(next, JSON.stringify(parsed.formulas));
    onChange(parsed.formulas);
  };
  const edit = (id: number, patch: Partial<Row>) => update(rows.map(row => row.id === id ? { ...row, ...patch } : row));
  return <div className="reviewer-formula-group">
    <Text as="div" size="2" weight="medium">{label}</Text>
    <Text as="p" size="1" color="gray">{hint}</Text>
    {rows.map(row => <div key={row.id} className="reviewer-formula-row">
      <input aria-label="Tên đại lượng" className="reviewer-formula-name" placeholder="charge" value={row.name} onChange={event => edit(row.id, { name: event.target.value })} />
      <span className="reviewer-formula-eq">{rate ? "′ =" : "="}</span>
      <input aria-label="Công thức" className="reviewer-formula-text" spellCheck={false} placeholder="(E - q / C) / R" value={row.text} aria-invalid={Boolean(errors[row.id])} ref={element => element?.setCustomValidity(errors[row.id] ?? "")} onChange={event => edit(row.id, { text: event.target.value })} />
      <button type="button" className="reviewer-row-remove" aria-label="Xóa công thức" onClick={() => update(rows.filter(item => item.id !== row.id))}>×</button>
      {errors[row.id] && <Text size="1" color="red" role="alert" className="reviewer-formula-error">{errors[row.id]}</Text>}
    </div>)}
    <Button type="button" size="1" variant="ghost" onClick={() => setRows([...rows, { id: nextRowId++, name: "", text: "" }])}>+ Thêm công thức</Button>
  </div>;
}
