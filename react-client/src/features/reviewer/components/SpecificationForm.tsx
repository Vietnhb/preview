import { Text } from "@radix-ui/themes";
import { AdvancedJson, normalizeNumbers, objectRows, RowsEditor } from "./JsonEditor";

export type Spec = Record<string, unknown>;
export const EMPTY_SPEC: Spec = { objects: [], quantities: [], relations: [] };

export function asSpec(value: unknown): Spec {
  const base = value && typeof value === "object" && !Array.isArray(value) ? structuredClone(value as Spec) : {};
  return { ...EMPTY_SPEC, ...base };
}

/** Normalizes form rows before saving: numeric text → numbers; the SI value the form edits is mirrored to `value` when missing. */
export function finalizeSpec(spec: Spec): Spec {
  // Blank cells and empty rows are form leftovers, not part of the answer.
  const clean = (rows: Record<string, unknown>[]) => rows
    .map(row => Object.fromEntries(Object.entries(row)
      .map(([key, value]) => [key, typeof value === "string" ? value.trim() : value] as const)
      .filter(([, value]) => value !== undefined && value !== null && value !== "")))
    .filter(row => Object.keys(row).length > 0);
  const quantities = normalizeNumbers(clean(objectRows(spec.quantities)), ["normalizedValue", "value"]).map(row => ({
    ...row,
    value: row.value ?? row.normalizedValue,
    confidence: row.confidence ?? 1,
  }));
  return { ...spec, objects: clean(objectRows(spec.objects)), quantities, relations: clean(objectRows(spec.relations)) };
}

/** The "gold" reading of a problem, entered as three small tables instead of JSON. */
export function SpecificationForm({ value, onChange }: Readonly<{ value: Spec; onChange: (next: Spec) => void }>) {
  const set = (key: string, rows: Record<string, unknown>[]) => onChange({ ...value, [key]: rows });
  return <div className="reviewer-form-sections">
    <section className="reviewer-form-section"><Text as="div" size="2" weight="bold">1. Đối tượng trong đề</Text><Text as="p" size="1" color="gray" mb="2">Các vật/hệ xuất hiện trong đề bài. Mã dùng để liên kết ở bảng quan hệ.</Text>
      <RowsEditor rows={objectRows(value.objects)} onChange={rows => set("objects", rows)} addLabel="Thêm đối tượng" empty="Chưa có đối tượng."
        columns={[{ key: "id", label: "Mã", placeholder: "ball", width: "22%" }, { key: "type", label: "Loại", placeholder: "moving_body", width: "28%" }, { key: "label", label: "Tên gọi trong đề", placeholder: "quả bóng" }]} />
    </section>
    <section className="reviewer-form-section"><Text as="div" size="2" weight="bold">2. Đại lượng đã cho</Text><Text as="p" size="1" color="gray" mb="2">Giá trị ghi theo đơn vị SI (m, s, kg, m/s…).</Text>
      <RowsEditor rows={objectRows(value.quantities)} onChange={rows => set("quantities", rows)} addLabel="Thêm đại lượng" empty="Chưa có đại lượng."
        columns={[{ key: "name", label: "Đại lượng", placeholder: "initial_speed", width: "30%" }, { key: "symbol", label: "Kí hiệu", placeholder: "v₀", width: "14%" }, { key: "normalizedValue", label: "Giá trị (SI)", placeholder: "20", type: "number", width: "18%" }, { key: "normalizedUnit", label: "Đơn vị SI", placeholder: "m/s" }]} />
    </section>
    <section className="reviewer-form-section"><Text as="div" size="2" weight="bold">3. Quan hệ giữa các đối tượng</Text><Text as="p" size="1" color="gray" mb="2">Ví dụ: ball — on — ground.</Text>
      <RowsEditor rows={objectRows(value.relations)} onChange={rows => set("relations", rows)} addLabel="Thêm quan hệ" empty="Không có quan hệ (có thể bỏ trống)."
        columns={[{ key: "subject", label: "Đối tượng", placeholder: "ball", width: "30%" }, { key: "type", label: "Quan hệ", placeholder: "on", width: "30%" }, { key: "object", label: "Với đối tượng", placeholder: "ground" }]} />
    </section>
    <AdvancedJson value={value} onChange={onChange} />
  </div>;
}

/** Read-only digest used to compare two annotators side by side. */
export function SpecSummary({ value }: Readonly<{ value: unknown }>) {
  const spec = asSpec(value);
  const objects = objectRows(spec.objects);
  const quantities = objectRows(spec.quantities);
  const relations = objectRows(spec.relations);
  const show = (row: Record<string, unknown>, ...keys: string[]) => keys.map(key => row[key]).filter(item => item !== undefined && item !== null && item !== "").map(String).join(" ");
  return <div className="reviewer-spec-summary">
    <Text as="div" size="1" color="gray">Đối tượng</Text><Text as="p" size="2">{objects.length ? objects.map(row => show(row, "label", "id") || "—").join(", ") : "—"}</Text>
    <Text as="div" size="1" color="gray">Đại lượng</Text>{quantities.length ? <ul>{quantities.map((row, index) => <li key={index}>{show(row, "name", "symbol")} = {show(row, "normalizedValue", "normalizedUnit") || show(row, "value", "unit") || "?"}</li>)}</ul> : <Text as="p" size="2">—</Text>}
    <Text as="div" size="1" color="gray">Quan hệ</Text><Text as="p" size="2">{relations.length ? relations.map(row => show(row, "subject", "type", "object") || "—").join("; ") : "—"}</Text>
  </div>;
}
