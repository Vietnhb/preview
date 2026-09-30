import { useState, type FormEvent } from "react";
import { Button, TextArea, TextField } from "@radix-ui/themes";
import api from "../../../api/axios";
import { parseObject, useAction, useResource } from "../../operations/operationsData";
import type { Version } from "./reviewerTypes";
import { ReviewerDialog } from "./ReviewerDialog";
import { ReviewerFormSelect } from "./ReviewerKit";

export function VersionEditorModal({ solver, initial, clone, implementations, onClose, onSaved }: Readonly<{ solver: boolean; initial?: Version; clone: boolean; implementations?: { numerical: string[]; reference: string[] }; onClose: () => void; onSaved: () => void }>) {
  const metaSchema = useResource<Record<string, unknown>>("/schemas/meta-schema");
  const coreTypes = useResource<Record<string, unknown>>("/schemas/core-types");
  const initialDefinition = solver ? initial?.outputDefinition : prepareTopicPack(initial?.definition);
  const [definition, setDefinition] = useState(JSON.stringify(initialDefinition ?? {}, null, 2));
  const action = useAction();
  const isEdit = Boolean(initial && !clone);
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const fields = Object.fromEntries(new FormData(event.currentTarget));
    const ok = await action.run(async () => {
      const json = parseObject(definition);
      const body = solver ? { schemaId: fields.schemaId, version: fields.version, solverId: fields.solverId, outputDefinition: { ...json as object, referenceSolverId: fields.referenceSolverId } } : { ...fields, definition: json };
      const resource = solver ? "solvers" : "schema-versions";
      const endpoint = isEdit ? `/reviewer/${resource}/${initial?.id}` : `/reviewer/${solver ? "solvers" : "schemas"}`;
      await (isEdit ? api.put(endpoint, body) : api.post(endpoint, body));
    }, "Đã lưu phiên bản ở trạng thái bản nháp.");
    if (ok) onSaved();
  };
  return <ReviewerDialog title={isEdit ? "Chỉnh sửa bản nháp" : "Tạo phiên bản mới"} onClose={onClose} wide><form onSubmit={submit}>
    <div className="reviewer-form-row"><label className="reviewer-field"><span>Mã cấu trúc (schema) *</span><TextField.Root size="3" name="schemaId" required maxLength={80} readOnly={isEdit} defaultValue={initial?.schemaId ?? ""} /></label><label className="reviewer-field"><span>Phiên bản *</span><TextField.Root size="3" name="version" required maxLength={16} readOnly={isEdit} defaultValue={clone ? "" : initial?.version ?? ""} placeholder="1.0.0" /></label></div>
    {!solver && <div className="reviewer-form-row"><label className="reviewer-field"><span>Tên cấu trúc *</span><TextField.Root size="3" name="name" required defaultValue={initial?.name ?? ""} /></label><label className="reviewer-field"><span>Chủ đề *</span><TextField.Root size="3" name="topic" required defaultValue={initial?.topic ?? ""} /></label></div>}
    {solver && <div className="reviewer-form-row"><label className="reviewer-field"><span>Bộ giải số *</span><ReviewerFormSelect name="solverId" label="Bộ giải số" required defaultValue={initial?.solverId} placeholder="Chọn bộ giải số" options={(implementations?.numerical ?? []).map((id) => ({ value: id, label: id }))} /></label><label className="reviewer-field"><span>Bộ giải tham chiếu độc lập *</span><ReviewerFormSelect name="referenceSolverId" label="Bộ giải tham chiếu độc lập" required defaultValue={initial?.outputDefinition?.referenceSolverId} placeholder="Chọn bộ giải tham chiếu" options={(implementations?.reference ?? []).map((id) => ({ value: id, label: id }))} /></label></div>}
    {!solver && <details className="reviewer-extraction-details"><summary>Mẫu cấu trúc & kiểu dữ liệu</summary>{metaSchema.loading && <p>Đang tải mẫu cấu trúc…</p>}{metaSchema.error && <p role="alert">{metaSchema.error}</p>}{metaSchema.data && <pre className="reviewer-json">{JSON.stringify(metaSchema.data, null, 2)}</pre>}<h4>Thư viện kiểu dữ liệu</h4>{coreTypes.data && <pre className="reviewer-json">{JSON.stringify(coreTypes.data, null, 2)}</pre>}</details>}
    <label className="reviewer-field"><span>{solver ? "Định nghĩa đầu ra (JSON)" : "Định nghĩa cấu trúc (JSON)"}</span><TextArea size="3" className="reviewer-json" rows={14} spellCheck={false} value={definition} onChange={(event) => setDefinition(event.target.value)} required /></label>{action.feedback}<div className="reviewer-form-footer"><Button type="button" variant="soft" color="gray" size="2" onClick={onClose}>Hủy</Button><Button type="submit" size="3" disabled={action.busy}>{action.busy ? "Đang lưu…" : "Lưu phiên bản"}</Button></div>
  </form></ReviewerDialog>;
}

function prepareTopicPack(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? structuredClone(value as Record<string, unknown>) : {};
}
