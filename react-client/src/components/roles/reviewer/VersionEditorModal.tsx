import { useState, type FormEvent } from "react";
import api from "../../../api/axios";
import { parseObject, useAction } from "../../operations/operationsData";
import type { Version } from "./reviewerTypes";

export function VersionEditorModal({
  solver,
  initial,
  clone,
  implementations,
  onClose,
  onSaved,
}: Readonly<{
  solver: boolean;
  initial?: Version;
  clone: boolean;
  implementations?: { numerical: string[]; reference: string[] };
  onClose: () => void;
  onSaved: () => void;
}>) {
  const [definition, setDefinition] = useState(
    JSON.stringify(
      (solver ? initial?.outputDefinition : initial?.definition) ?? {},
      null,
      2,
    ),
  );
  const action = useAction();
  const isEdit = Boolean(initial && !clone);

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fields = Object.fromEntries(new FormData(e.currentTarget));
    const json = parseObject(definition);

    const body = solver
      ? {
          schemaId: fields.schemaId,
          version: fields.version,
          solverId: fields.solverId,
          outputDefinition: {
            ...json,
            referenceSolverId: fields.referenceSolverId,
          },
        }
      : { ...fields, definition: json };

    const editResource = solver ? "solvers" : "schema-versions";
    const createResource = solver ? "solvers" : "schemas";
    const endpoint = isEdit
      ? `/reviewer/${editResource}/${initial?.id}`
      : `/reviewer/${createResource}`;
    const request = isEdit
      ? () => api.put(endpoint, body)
      : () => api.post(endpoint, body);
    const ok = await action.run(request);
    if (ok) onSaved();
  };

  return (
    <dialog
      open
      className="modern-modal-overlay"
      onPointerDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="modern-modal-content" style={{ maxWidth: "680px" }}>
        <div className="modern-modal-header">
          <h3>{isEdit ? "Chỉnh sửa bản nháp" : "Tạo phiên bản mới"}</h3>
          <button
            type="button"
            className="modern-modal-close"
            onClick={onClose}
          >
            ✕
          </button>
        </div>

        <form onSubmit={submit}>
          <div className="form-row">
            <div className="form-group">
              <label htmlFor="review-schema-id">Schema ID *</label>
              <input
                id="review-schema-id"
                name="schemaId"
                required
                maxLength={80}
                readOnly={isEdit}
                defaultValue={initial?.schemaId ?? ""}
                placeholder="kinematics"
              />
            </div>
            <div className="form-group">
              <label htmlFor="review-schema-version">Phiên bản *</label>
              <input
                id="review-schema-version"
                name="version"
                required
                maxLength={16}
                readOnly={isEdit}
                defaultValue={clone ? "" : (initial?.version ?? "")}
                placeholder="1.0.0"
              />
            </div>
          </div>

          {!solver && (
            <div className="form-row">
              <div className="form-group">
                <label htmlFor="review-schema-name">Tên Schema *</label>
                <input
                  id="review-schema-name"
                  name="name"
                  required
                  defaultValue={initial?.name ?? ""}
                  placeholder="Chuyển động thẳng biến đổi đều"
                />
              </div>
              <div className="form-group">
                <label htmlFor="review-schema-topic">Chủ đề *</label>
                <select
                  id="review-schema-topic"
                  name="topic"
                  defaultValue={initial?.topic ?? "Kinematics"}
                >
                  <option value="Kinematics">Kinematics (Động học)</option>
                  <option value="Dynamics">Dynamics (Động lực học)</option>
                  <option value="Circuits">Circuits (Mạch điện)</option>
                </select>
              </div>
            </div>
          )}

          {solver && (
            <div className="form-row">
              <div className="form-group">
                <label htmlFor="review-solver-id">Numerical Module *</label>
                <select
                  id="review-solver-id"
                  name="solverId"
                  required
                  defaultValue={initial?.solverId ?? ""}
                >
                  <option value="">-- Chọn numerical solver --</option>
                  {implementations?.numerical.map((id) => (
                    <option key={id} value={id}>
                      {id}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label htmlFor="review-reference-solver-id">
                  Independent Reference Solver *
                </label>
                <select
                  id="review-reference-solver-id"
                  name="referenceSolverId"
                  required
                  defaultValue={
                    initial?.outputDefinition?.referenceSolverId ?? ""
                  }
                >
                  <option value="">-- Chọn reference solver --</option>
                  {implementations?.reference.map((id) => (
                    <option key={id} value={id}>
                      {id}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          <div className="form-group" style={{ marginBottom: "18px" }}>
            <label htmlFor="review-definition">
              Định nghĩa JSON (Schema / Output definition)
            </label>
            <textarea
              id="review-definition"
              rows={8}
              style={{ fontFamily: "monospace", fontSize: "12.5px" }}
              value={definition}
              onChange={(e) => setDefinition(e.target.value)}
            />
          </div>

          {action.feedback}

          <div
            style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}
          >
            <button
              type="button"
              className="role-switch-pill"
              style={{
                border: "1px solid var(--border-subtle)",
                padding: "8px 16px",
              }}
              onClick={onClose}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="prediction-submit-btn"
              style={{ background: "var(--role-reviewer)" }}
              disabled={action.busy}
            >
              {action.busy ? "Đang lưu…" : "Lưu phiên bản"}
            </button>
          </div>
        </form>
      </div>
    </dialog>
  );
}




