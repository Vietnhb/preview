import { useState } from "react";
import type { Ambiguity, Specification } from "../../../types/physlive";

type SpecificationDraft = { objects: string; quantities: string; relations: string; endCondition: string };

function parseSpecificationDraft(draft: SpecificationDraft): Pick<Specification, "objects" | "quantities" | "relations" | "endCondition"> {
  const objects = JSON.parse(draft.objects) as Specification["objects"];
  const quantities = JSON.parse(draft.quantities) as Specification["quantities"];
  const relations = JSON.parse(draft.relations) as Specification["relations"];
  const endCondition = draft.endCondition.trim() ? JSON.parse(draft.endCondition) as Specification["endCondition"] : null;
  if (!Array.isArray(objects) || !Array.isArray(quantities) || !Array.isArray(relations)) {
    throw new TypeError("Ba trường specification phải là mảng JSON.");
  }
  if (endCondition !== null && (typeof endCondition !== "object" || Array.isArray(endCondition))) {
    throw new TypeError("endCondition must be a JSON object.");
  }
  return { objects, quantities, relations, endCondition };
}

function applySpecificationDraft(
  draft: SpecificationDraft,
  onError: (message: string) => void,
  onApply: (value: Pick<Specification, "objects" | "quantities" | "relations" | "endCondition">) => void,
) {
  try {
    onApply(parseSpecificationDraft(draft));
    onError("");
  } catch (error) {
    onError(error instanceof Error ? error.message : "JSON specification chưa hợp lệ.");
  }
}

type SpecificationReviewEditorProps = {
  specification: Specification;
  loading: boolean;
  ambiguities: Ambiguity[];
  onSave: (value: Pick<Specification, "objects" | "quantities" | "relations" | "endCondition">) => void;
  onConfirm: (value: Pick<Specification, "objects" | "quantities" | "relations" | "endCondition">) => void;
};

export function SpecificationReviewEditor({ specification, loading, ambiguities, onSave, onConfirm }: Readonly<SpecificationReviewEditorProps>) {
  const [draft, setDraft] = useState<SpecificationDraft>(() => ({
    objects: JSON.stringify(specification.objects ?? [], null, 2),
    quantities: JSON.stringify(specification.quantities ?? [], null, 2),
    relations: JSON.stringify(specification.relations ?? [], null, 2),
    endCondition: JSON.stringify(specification.endCondition ?? null, null, 2),
  }));
  const [error, setError] = useState("");
  const handleChange = (field: keyof SpecificationDraft, value: string) => setDraft(current => ({ ...current, [field]: value }));
  const handleSave = () => applySpecificationDraft(draft, setError, onSave);
  const handleConfirm = () => applySpecificationDraft(draft, setError, onConfirm);

  return <section className="create-chat-specification-review" aria-label="Rà soát specification">
    <div className="create-chat-specification-heading"><div><strong>Rà soát specification</strong><span>Giáo viên có thể sửa trực tiếp objects, quantities và relations trước khi chạy.</span></div><span className={`create-chat-specification-status ${specification.confirmationState === "UNRESOLVED" ? "warning" : "ready"}`}>{specification.confirmationState === "UNRESOLVED" ? "Cần bổ sung" : "Đã đọc"}</span></div>
    <div className="create-chat-specification-grid">{(["objects", "quantities", "relations"] as const).map(field => <label key={field}><span>{field}</span><textarea rows={5} value={draft[field]} onChange={event => handleChange(field, event.target.value)} spellCheck={false} aria-label={`Chỉnh ${field} specification`} /></label>)}</div>
    <label className="create-chat-specification-end-condition"><span>endCondition (optional)</span><textarea rows={5} value={draft.endCondition} onChange={event => handleChange("endCondition", event.target.value)} spellCheck={false} aria-label="Chá»‰nh endCondition specification" /></label>
    {error && <p className="create-chat-specification-error" role="alert">{error}</p>}
    <div className="create-chat-specification-actions"><button type="button" className="create-chat-secondary-action" onClick={handleSave} disabled={loading}>Lưu thay đổi</button>{!ambiguities.length && <button type="button" className="create-chat-specification-confirm" onClick={handleConfirm} disabled={loading}>Xác nhận &amp; chạy mô phỏng</button>}</div>
  </section>;
}
