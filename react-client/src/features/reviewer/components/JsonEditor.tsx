import { useState } from "react";
import { Button, Text, TextArea } from "@radix-ui/themes";

type JsonObject = Record<string, unknown>;

/** Collapsed "advanced" JSON view kept in sync with a visual form.
 *  Edits are applied only when the text parses to an object. */
export function AdvancedJson({ value, onChange, label = "Chỉnh sửa nâng cao (JSON)", open = false, hint = "Dành cho người quen định dạng dữ liệu. Thay đổi ở đây được đồng bộ với biểu mẫu phía trên." }: Readonly<{ value: JsonObject; onChange: (next: JsonObject) => void; label?: string; open?: boolean; hint?: string }>) {
  const serialized = JSON.stringify(value, null, 2);
  const [draft, setDraft] = useState<{ base: string; text: string } | null>(null);
  const [error, setError] = useState("");
  const text = draft && draft.base === serialized ? draft.text : serialized;
  const update = (next: string) => {
    setDraft({ base: serialized, text: next });
    try {
      const parsed: unknown = JSON.parse(next);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) { setError("Nội dung phải là một đối tượng JSON { … }."); return; }
      setError("");
      const nextSerialized = JSON.stringify(parsed, null, 2);
      setDraft({ base: nextSerialized, text: next });
      onChange(parsed as JsonObject);
    } catch (exception) {
      setError(`JSON chưa hợp lệ: ${exception instanceof Error ? exception.message : "lỗi cú pháp"}`);
    }
  };
  return <details className="reviewer-disclosure" open={open || undefined}>
    <summary>{label}</summary>
    <Text as="p" size="1" color="gray" mb="2">{hint}</Text>
    <TextArea size="2" className="reviewer-json" rows={14} spellCheck={false} value={text} onChange={event => update(event.target.value)} aria-invalid={Boolean(error)} />
    <div className="reviewer-json-footer">
      {error ? <Text size="1" color="red" role="alert">{error}</Text> : <Text size="1" color="green">JSON hợp lệ</Text>}
      <Button type="button" size="1" variant="soft" color="gray" onClick={() => { setDraft(null); setError(""); }}>Định dạng lại</Button>
    </div>
  </details>;
}

/** Generic editable table for arrays of flat objects, preserving fields the form does not show. */
export type ColumnSpec = { key: string; label: string; placeholder?: string; type?: "text" | "number" | "list" | "flag"; width?: string };

export function RowsEditor({ rows, columns, onChange, addLabel, empty }: Readonly<{ rows: JsonObject[]; columns: ColumnSpec[]; onChange: (rows: JsonObject[]) => void; addLabel: string; empty: string }>) {
  // The cell being typed in keeps its raw text, so "m/s, " or "0." are not reformatted mid-typing.
  const [editing, setEditing] = useState<{ cell: string; text: string } | null>(null);
  const set = (index: number, column: ColumnSpec, raw: string) => onChange(rows.map((row, i) => {
    if (i !== index) return row;
    const value = column.type === "number" ? numericOrRaw(raw) : column.type === "list" ? raw.split(",").map(part => part.trim()).filter(Boolean) : raw;
    return { ...row, [column.key]: value };
  }));
  const display = (row: JsonObject, column: ColumnSpec) => {
    const value = row[column.key];
    if (Array.isArray(value)) return value.join(", ");
    return value === undefined || value === null ? "" : String(value);
  };
  return <div className="reviewer-rows">
    {rows.length === 0 && <Text as="p" size="2" color="gray" className="reviewer-rows-empty">{empty}</Text>}
    {rows.length > 0 && <table className="reviewer-mini-table reviewer-edit-table"><thead><tr>{columns.map(column => <th key={column.key} style={column.width ? { width: column.width } : undefined}>{column.label}</th>)}<th aria-label="Thao tác" style={{ width: 44 }} /></tr></thead><tbody>
      {rows.map((row, index) => <tr key={index}>{columns.map(column => column.type === "flag"
        ? <td key={column.key}><input type="checkbox" aria-label={column.label} checked={row[column.key] === true} onChange={event => onChange(rows.map((item, i) => {
          if (i !== index) return item;
          const rest = { ...item };
          delete rest[column.key];
          return event.target.checked ? { ...rest, [column.key]: true } : rest;
        }))} /></td>
        : <td key={column.key}><input aria-label={column.label} inputMode={column.type === "number" ? "decimal" : undefined} placeholder={column.placeholder} value={editing?.cell === `${index}:${column.key}` ? editing.text : display(row, column)} onChange={event => { setEditing({ cell: `${index}:${column.key}`, text: event.target.value }); set(index, column, event.target.value); }} onBlur={() => setEditing(null)} /></td>)}
        <td><button type="button" className="reviewer-row-remove" aria-label="Xóa dòng" onClick={() => { setEditing(null); onChange(rows.filter((_, i) => i !== index)); }}>×</button></td></tr>)}
    </tbody></table>}
    <Button type="button" size="1" variant="soft" onClick={() => onChange([...rows, {}])}>+ {addLabel}</Button>
  </div>;
}

export function objectRows(value: unknown): JsonObject[] {
  return Array.isArray(value) ? value.filter((row): row is JsonObject => Boolean(row) && typeof row === "object" && !Array.isArray(row)) : [];
}

/** Keeps half-typed numbers ("0.", "-") as text until they parse cleanly. */
export function numericOrRaw(raw: string): number | string | undefined {
  const normalized = raw.trim().replace(",", ".");
  if (normalized === "") return undefined;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) && String(parsed) === normalized ? parsed : raw;
}

/** Converts leftover numeric text in the given columns to numbers before saving. */
export function normalizeNumbers(rows: JsonObject[], keys: string[]): JsonObject[] {
  return rows.map(row => {
    const next = { ...row };
    for (const key of keys) {
      const value = next[key];
      if (typeof value === "string") { const parsed = Number(value.trim().replace(",", ".")); if (value.trim() !== "" && Number.isFinite(parsed)) next[key] = parsed; }
    }
    return next;
  });
}
