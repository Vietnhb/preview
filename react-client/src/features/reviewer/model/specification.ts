import { normalizeNumbers, objectRows } from "./jsonRows";

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
