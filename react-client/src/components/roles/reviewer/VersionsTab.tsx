import { useState } from "react";
import api from "../../../api/axios";
import { LoadState } from "../../operations/OperationsKit";
import { useAction, useResource } from "../../operations/operationsData";
import { VersionEditorModal } from "./VersionEditorModal";
import type { Version } from "./reviewerTypes";
import { lifecycleClass } from "./reviewerUtils";

export function VersionsTab({ solver }: Readonly<{ solver: boolean }>) {
  const resource = useResource<Version[]>(
    solver ? "/reviewer/solvers" : "/reviewer/schemas",
  );
  const implementations = useResource<{
    numerical: string[];
    reference: string[];
  }>("/reviewer/solver-implementations");
  const action = useAction();

  const [filter, setFilter] = useState("");
  const [query, setQuery] = useState("");
  const [editor, setEditor] = useState<{
    version?: Version;
    clone: boolean;
  } | null>(null);
  const [decision, setDecision] = useState<{
    version: Version;
    status: string;
  } | null>(null);

  const rows = (resource.data ?? []).filter(
    (v) =>
      (!filter || v.lifecycleStatus === filter) &&
      `${v.schemaId} ${v.name ?? ""} ${v.version}`
        .toLowerCase()
        .includes(query.toLowerCase()),
  );

  return (
    <div className="modern-card">
      <div className="modern-card-header">
        <div>
          <h2>
            {solver
              ? "Reference Solvers & Module Bindings (FR-REV-02)"
              : "Topic Schemas & Phê duyệt Module (FR-REV-01)"}
          </h2>
          <p>
            {solver
              ? "Liên kết numerical solver module với independent closed-form reference solver đã cài trên server."
              : "Quản lý đặc tả chủ đề thuộc phạm vi THPT: Bản nháp (DRAFT) → Phê duyệt (APPROVED) → Ngừng dùng (RETIRED)."}
          </p>
        </div>
        <button
          type="button"
          className="prediction-submit-btn"
          style={{
            background: "var(--role-reviewer)",
            padding: "8px 16px",
            fontSize: "13px",
          }}
          onClick={() => setEditor({ clone: false })}
        >
          + Tạo bản nháp mới
        </button>
      </div>

      <LoadState {...resource} />
      {action.feedback}

      {/* Lifecycle Decision Dialog */}
      {decision && (
        <dialog
          open
          className="modern-modal-overlay"
          onPointerDown={(event) => {
            if (event.target === event.currentTarget) setDecision(null);
          }}
        >
          <div className="modern-modal-content" style={{ maxWidth: "480px" }}>
            <div className="modern-modal-header">
              <h3>
                {decision.status === "APPROVED"
                  ? "Phê duyệt phát hành"
                  : "Ngừng sử dụng"}
              </h3>
              <button
                type="button"
                className="modern-modal-close"
                onClick={() => setDecision(null)}
              >
                ✕
              </button>
            </div>
            <p style={{ fontSize: "14px", lineHeight: "1.5" }}>
              Bạn có chắc chắn muốn chuyển trạng thái{" "}
              <strong>{decision.version.schemaId}</strong> @
              {decision.version.version} sang <strong>{decision.status}</strong>
              ?
            </p>
            <div
              style={{
                display: "flex",
                justifyContent: "flex-end",
                gap: "10px",
                marginTop: "20px",
              }}
            >
              <button
                type="button"
                className="role-switch-pill"
                style={{
                  border: "1px solid var(--border-subtle)",
                  padding: "8px 16px",
                }}
                onClick={() => setDecision(null)}
              >
                Hủy
              </button>
              <button
                type="button"
                className="prediction-submit-btn"
                style={{
                  background:
                    decision.status === "APPROVED"
                      ? "var(--role-student)"
                      : "#dc2626",
                }}
                disabled={action.busy}
                onClick={() =>
                  void action
                    .run(
                      () =>
                        api.put(
                          `/reviewer/${solver ? "solvers" : "schema-versions"}/${decision.version.id}/lifecycle`,
                          null,
                          {
                            params: { status: decision.status },
                          },
                        ),
                      `Đã cập nhật trạng thái sang ${decision.status}`,
                    )
                    .then((ok) => {
                      if (ok) {
                        setDecision(null);
                        resource.refresh();
                      }
                    })
                }
              >
                Xác nhận
              </button>
            </div>
          </div>
        </dialog>
      )}

      {/* Editor Modal */}
      {editor && (
        <VersionEditorModal
          solver={solver}
          initial={editor.version}
          clone={editor.clone}
          implementations={implementations.data}
          onClose={() => setEditor(null)}
          onSaved={() => {
            setEditor(null);
            resource.refresh();
          }}
        />
      )}

      {/* Filter bar */}
      <div
        style={{
          display: "flex",
          gap: "12px",
          marginBottom: "16px",
          flexWrap: "wrap",
        }}
      >
        <input
          style={{
            flex: 1,
            minWidth: "240px",
            border: "1px solid var(--border-strong)",
            borderRadius: "8px",
            padding: "8px 12px",
            fontSize: "13.5px",
          }}
          placeholder="Tìm schema ID, tên hoặc phiên bản…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <select
          style={{
            border: "1px solid var(--border-strong)",
            borderRadius: "8px",
            padding: "8px 12px",
            fontSize: "13.5px",
          }}
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
        >
          <option value="">Tất cả trạng thái</option>
          {["DRAFT", "APPROVED", "RETIRED"].map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
        <button
          type="button"
          className="role-switch-pill"
          style={{
            border: "1px solid var(--border-subtle)",
            background: "#ffffff",
            padding: "8px 16px",
          }}
          onClick={resource.refresh}
        >
          Làm mới
        </button>
      </div>

      {/* Table */}
      <div className="modern-table-wrapper">
        <table className="modern-table">
          <thead>
            <tr>
              <th>{solver ? "Solver / Schema" : "Schema / Chủ đề"}</th>
              <th>Phiên bản</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((v) => (
              <tr key={v.id}>
                <td>
                  <strong>{solver ? v.solverId : v.name}</strong>
                  <small
                    style={{ display: "block", color: "var(--text-muted)" }}
                  >
                    {v.schemaId} {v.topic ? `· ${v.topic}` : ""}
                  </small>
                  {solver && (
                    <small style={{ color: "var(--role-reviewer)" }}>
                      Reference solver:{" "}
                      <code>
                        {v.outputDefinition?.referenceSolverId || "closed-form"}
                      </code>
                    </small>
                  )}
                </td>
                <td>
                  <code>v{v.version}</code>
                  <small
                    style={{ display: "block", color: "var(--text-muted)" }}
                  >
                    {new Date(v.createdAt).toLocaleDateString("vi-VN")}
                  </small>
                </td>
                <td>
                  <span
                    className={`status-pill ${lifecycleClass(v.lifecycleStatus)}`}
                  >
                    {v.lifecycleStatus}
                  </span>
                </td>
                <td>
                  <div style={{ display: "flex", gap: "6px" }}>
                    <button
                      type="button"
                      className="role-switch-pill"
                      style={{
                        border: "1px solid var(--border-subtle)",
                        background: "#ffffff",
                      }}
                      onClick={() =>
                        setEditor({
                          version: v,
                          clone: v.lifecycleStatus !== "DRAFT",
                        })
                      }
                    >
                      {v.lifecycleStatus === "DRAFT" ? "Sửa" : "Nhân bản"}
                    </button>
                    {v.lifecycleStatus === "DRAFT" && (
                      <button
                        type="button"
                        className="role-switch-pill"
                        style={{
                          background: "var(--status-pass-bg)",
                          color: "var(--status-pass-text)",
                        }}
                        onClick={() =>
                          setDecision({ version: v, status: "APPROVED" })
                        }
                      >
                        Phê duyệt
                      </button>
                    )}
                    {v.lifecycleStatus === "APPROVED" && (
                      <button
                        type="button"
                        className="role-switch-pill"
                        style={{
                          background: "var(--status-fail-bg)",
                          color: "var(--status-fail-text)",
                        }}
                        onClick={() =>
                          setDecision({ version: v, status: "RETIRED" })
                        }
                      >
                        Ngừng dùng
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}




