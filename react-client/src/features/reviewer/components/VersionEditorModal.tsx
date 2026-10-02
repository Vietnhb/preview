import { useState, type FormEvent } from "react";
import { Button, Text, TextField } from "@radix-ui/themes";
import api from "../../../shared/api/client";
import { useAction } from "../../../shared/hooks/operationsData";
import type { Version } from "../model/reviewerTypes";
import { ReviewerDialog } from "./ReviewerDialog";
import { TopicDefinitionForm } from "./TopicDefinitionForm";

type Definition = Record<string, unknown>;

function plainObject(value: unknown): Definition {
  return value && typeof value === "object" && !Array.isArray(value) ? structuredClone(value as Definition) : {};
}

/** 1.0 → 1.1, 2.3.4 → 2.3.5; anything else is left for the reviewer to type. */
export function nextVersion(version?: string) {
  if (!version) return "";
  const parts = version.split(".");
  const last = Number(parts.at(-1));
  if (!Number.isInteger(last)) return "";
  parts[parts.length - 1] = String(last + 1);
  return parts.join(".");
}

export function VersionEditorModal({ initial, clone, onClose, onSaved }: Readonly<{ initial?: Version; clone: boolean; onClose: () => void; onSaved: () => void }>) {
  const [definition, setDefinition] = useState<Definition>(() => plainObject(initial?.definition));
  const action = useAction();
  const isEdit = Boolean(initial && !clone);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const fields = Object.fromEntries(new FormData(event.currentTarget)) as Record<string, string>;
    const ok = await action.run(async () => {
      const body = { schemaId: fields.schemaId, version: fields.version, name: fields.name, topic: fields.topic, definition };
      const endpoint = isEdit ? `/reviewer/schema-versions/${initial?.id}` : "/schemas";
      await (isEdit ? api.put(endpoint, body) : api.post(endpoint, body));
    }, "Đã lưu bản nháp. Bản nháp cần được phê duyệt trước khi hệ thống sử dụng.");
    if (ok) onSaved();
  };

  const title = isEdit ? "Chỉnh sửa bản nháp" : clone ? "Tạo phiên bản mới" : "Thêm chủ đề mới";
  return <ReviewerDialog title={title} onClose={onClose} wide><form onSubmit={submit}>
    {clone && initial && <div className="reviewer-note"><strong>Phiên bản đang dùng không sửa trực tiếp được.</strong> Bạn đang tạo bản nháp mới dựa trên v{initial.version}; bản cũ vẫn hoạt động cho tới khi bản mới được phê duyệt.</div>}
    <div className="reviewer-form-row">
      <label className="reviewer-field"><span>Mã chủ đề *</span>
        <TextField.Root size="3" name="schemaId" required maxLength={80} readOnly={isEdit || (clone && Boolean(initial))} defaultValue={initial?.schemaId ?? ""} placeholder="thpt_kinematics" />
        {!isEdit && !clone && <Text size="1" color="gray">Viết thường, không dấu, nối bằng dấu gạch dưới.</Text>}
      </label>
      <label className="reviewer-field"><span>Phiên bản *</span><TextField.Root size="3" name="version" required maxLength={16} readOnly={isEdit} defaultValue={clone ? nextVersion(initial?.version) : initial?.version ?? "1.0"} placeholder="1.0" /></label>
    </div>
      <div className="reviewer-form-row"><label className="reviewer-field"><span>Tên chủ đề *</span><TextField.Root size="3" name="name" required defaultValue={initial?.name ?? ""} placeholder="Động học" /></label><label className="reviewer-field"><span>Nhóm chủ đề *</span><TextField.Root size="3" name="topic" required defaultValue={initial?.topic ?? ""} placeholder="KINEMATICS" /></label></div>
      <TopicDefinitionForm value={definition} onChange={setDefinition} />
    {action.feedback}
    <div className="reviewer-form-footer"><Button type="button" variant="soft" color="gray" size="2" onClick={onClose}>Hủy</Button><Button type="submit" size="3" disabled={action.busy}>{action.busy ? "Đang lưu…" : "Lưu bản nháp"}</Button></div>
  </form></ReviewerDialog>;
}
