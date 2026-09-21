import { useState, type FormEvent } from "react";
import api from "../../../api/axios";
import { parseObject, useAction } from "../../operations/operationsData";
import type { Version } from "./reviewerTypes";

export function VersionEditorModal({ solver, initial, clone, implementations, onClose, onSaved }: Readonly<{ solver: boolean; initial?: Version; clone: boolean; implementations?: { numerical: string[]; reference: string[] }; onClose: () => void; onSaved: () => void }>) {
  const [definition, setDefinition] = useState(JSON.stringify((solver ? initial?.outputDefinition : initial?.definition) ?? {}, null, 2));
  const action = useAction();
  const isEdit = Boolean(initial && !clone);
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const fields = Object.fromEntries(new FormData(event.currentTarget));
    const json = parseObject(definition);
    const body = solver ? { schemaId: fields.schemaId, version: fields.version, solverId: fields.solverId, outputDefinition: { ...json as object, referenceSolverId: fields.referenceSolverId } } : { ...fields, definition: json };
    const resource = solver ? "solvers" : "schema-versions";
    const endpoint = isEdit ? `/reviewer/${resource}/${initial?.id}` : `/reviewer/${solver ? "solvers" : "schemas"}`;
    const ok = await action.run(() => isEdit ? api.put(endpoint, body) : api.post(endpoint, body), "Đã lưu phiên bản ở trạng thái bản nháp.");
    if (ok) onSaved();
  };
  return <dialog open className="modern-modal-overlay" onPointerDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><div className="modern-modal-content" style={{ maxWidth: "720px" }}><div className="modern-modal-header"><h3>{isEdit ? "Chỉnh sửa bản nháp" : "Tạo phiên bản mới"}</h3><button type="button" className="modern-modal-close" onClick={onClose}>×</button></div><form onSubmit={submit}>
    <div className="form-row"><label className="ops-field"><span>Schema ID *</span><input name="schemaId" required maxLength={80} readOnly={isEdit} defaultValue={initial?.schemaId ?? ""} /></label><label className="ops-field"><span>Phiên bản *</span><input name="version" required maxLength={16} readOnly={isEdit} defaultValue={clone ? "" : initial?.version ?? ""} placeholder="1.0.0" /></label></div>
    {!solver && <div className="form-row"><label className="ops-field"><span>Tên schema *</span><input name="name" required defaultValue={initial?.name ?? ""} /></label><label className="ops-field"><span>Chủ đề *</span><input name="topic" required defaultValue={initial?.topic ?? "Kinematics"} /></label></div>}
    {solver && <div className="form-row"><label className="ops-field"><span>Numerical solver *</span><select name="solverId" required defaultValue={initial?.solverId ?? ""}><option value="">-- Chọn numerical solver --</option>{implementations?.numerical.map((id) => <option key={id} value={id}>{id}</option>)}</select></label><label className="ops-field"><span>Reference solver độc lập *</span><select name="referenceSolverId" required defaultValue={initial?.outputDefinition?.referenceSolverId ?? ""}><option value="">-- Chọn reference solver --</option>{implementations?.reference.map((id) => <option key={id} value={id}>{id}</option>)}</select></label></div>}
    <label className="ops-field"><span>{solver ? "Output binding JSON" : "Định nghĩa schema JSON"}</span><textarea className="ops-code" rows={14} value={definition} onChange={(event) => setDefinition(event.target.value)} required /></label>{action.feedback}<div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}><button type="button" className="role-switch-pill" onClick={onClose}>Hủy</button><button type="submit" className="prediction-submit-btn" disabled={action.busy}>{action.busy ? "Đang lưu…" : "Lưu phiên bản"}</button></div>
  </form></div></dialog>;
}
