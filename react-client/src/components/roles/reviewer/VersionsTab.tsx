import { useState } from "react";
import api from "../../../api/axios";
import { LoadState } from "../../operations/OperationsKit";
import { useAction, useResource } from "../../operations/operationsData";
import { VersionEditorModal } from "./VersionEditorModal";
import type { Version } from "./reviewerTypes";
import { lifecycleClass } from "./reviewerUtils";

export function VersionsTab({ solver }: Readonly<{ solver: boolean }>) {
  const resource = useResource<Version[]>(solver ? "/reviewer/solvers" : "/reviewer/schemas");
  const implementations = useResource<{ numerical: string[]; reference: string[] }>("/reviewer/solver-implementations");
  const action = useAction();
  const [filter, setFilter] = useState("");
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<Version | null>(null);
  const [editor, setEditor] = useState<{ version?: Version; clone: boolean } | null>(null);
  const [decision, setDecision] = useState<{ version: Version; status: "APPROVED" | "RETIRED" } | null>(null);
  const rows = (resource.data ?? []).filter((version) => (!filter || version.lifecycleStatus === filter) && `${version.schemaId} ${version.name ?? ""} ${version.version} ${version.solverId ?? ""}`.toLowerCase().includes(query.toLowerCase()));

  const changeLifecycle = async () => {
    if (!decision) return;
    const ok = await action.run(() => api.put(`/reviewer/${solver ? "solvers" : "schema-versions"}/${decision.version.id}/lifecycle`, null, { params: { status: decision.status } }), `Đã chuyển sang ${decision.status}.`);
    if (ok) { setDecision(null); resource.refresh(); }
  };

  return <div className="modern-card">
    <div className="modern-card-header"><div><h2>{solver ? "Solver versions và reference evidence" : "Schema versions và phê duyệt"}</h2><p>{solver ? "Kiểm tra numerical solver, closed-form/reference solver độc lập và checksum binding trước khi approve." : "Schema đã approve là immutable; thay đổi phải đi qua một version mới."}</p></div><button type="button" className="prediction-submit-btn" onClick={() => setEditor({ clone: false })}>+ Tạo bản nháp</button></div>
    <LoadState {...resource} />{action.feedback}

    {decision && <dialog open className="modern-modal-overlay"><div className="modern-modal-content" style={{ maxWidth: "520px" }}><div className="modern-modal-header"><h3>Xác nhận lifecycle</h3><button type="button" className="modern-modal-close" onClick={() => setDecision(null)}>×</button></div><p>Chuyển <strong>{decision.version.schemaId}@{decision.version.version}</strong> sang <strong>{decision.status}</strong>?</p><p>Backend sẽ chạy validation contract, solver binding và checksum gate.</p><div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}><button type="button" className="role-switch-pill" onClick={() => setDecision(null)}>Hủy</button><button type="button" className="prediction-submit-btn" disabled={action.busy} onClick={() => void changeLifecycle()}>Xác nhận</button></div></div></dialog>}

    {selected && <dialog open className="modern-modal-overlay"><div className="modern-modal-content" style={{ maxWidth: "820px" }}><div className="modern-modal-header"><h3>Evidence: {selected.schemaId}@{selected.version}</h3><button type="button" className="modern-modal-close" onClick={() => setSelected(null)}>×</button></div><div className="ops-evidence-grid"><p><strong>Lifecycle:</strong> {selected.lifecycleStatus}</p><p><strong>Record version:</strong> {selected.recordVersion ?? 0}</p><p><strong>Definition checksum:</strong> <code>{selected.definitionChecksum ?? "chưa có"}</code></p><p><strong>Binding checksum:</strong> <code>{selected.bindingChecksum ?? "chưa có"}</code></p><p><strong>Numerical:</strong> <code>{selected.solverId ?? "—"}</code></p><p><strong>Reference:</strong> <code>{selected.outputDefinition?.referenceSolverId ?? "—"}</code></p></div><details open><summary>Output/definition contract</summary><pre className="ops-code" style={{ maxHeight: "360px", overflow: "auto" }}>{JSON.stringify(solver ? selected.outputDefinition : selected.definition, null, 2)}</pre></details></div></dialog>}

    {editor && <VersionEditorModal solver={solver} initial={editor.version} clone={editor.clone} implementations={implementations.data} onClose={() => setEditor(null)} onSaved={() => { setEditor(null); resource.refresh(); }} />}

    <div style={{ display: "flex", gap: "12px", marginBottom: "16px", flexWrap: "wrap" }}><input style={{ flex: 1, minWidth: "240px", padding: "8px 12px" }} placeholder="Tìm schema ID, tên, solver hoặc phiên bản…" value={query} onChange={(event) => setQuery(event.target.value)} /><select value={filter} onChange={(event) => setFilter(event.target.value)}><option value="">Tất cả trạng thái</option>{["DRAFT", "APPROVED", "RETIRED"].map((status) => <option key={status} value={status}>{status}</option>)}</select><button type="button" className="role-switch-pill" onClick={resource.refresh}>Làm mới</button></div>
    <div className="modern-table-wrapper"><table className="modern-table"><thead><tr><th>{solver ? "Solver / schema" : "Schema / chủ đề"}</th><th>Phiên bản</th><th>Trạng thái</th><th>Evidence</th><th>Thao tác</th></tr></thead><tbody>{rows.map((version) => <tr key={version.id}><td><strong>{solver ? version.solverId : version.name}</strong><small style={{ display: "block" }}>{version.schemaId}{version.topic ? ` · ${version.topic}` : ""}</small>{solver && <small>Reference: <code>{version.outputDefinition?.referenceSolverId ?? "—"}</code></small>}</td><td><code>v{version.version}</code><small style={{ display: "block" }}>{new Date(version.createdAt).toLocaleDateString("vi-VN")}</small></td><td><span className={`status-pill ${lifecycleClass(version.lifecycleStatus)}`}>{version.lifecycleStatus}</span></td><td><button type="button" className="role-switch-pill" onClick={() => setSelected(version)}>Xem bằng chứng</button></td><td><div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}><button type="button" className="role-switch-pill" onClick={() => setEditor({ version, clone: version.lifecycleStatus !== "DRAFT" })}>{version.lifecycleStatus === "DRAFT" ? "Sửa" : "Nhân bản"}</button>{version.lifecycleStatus === "DRAFT" && <button type="button" className="role-switch-pill" onClick={() => setDecision({ version, status: "APPROVED" })}>Approve</button>}{version.lifecycleStatus === "APPROVED" && <button type="button" className="role-switch-pill" onClick={() => setDecision({ version, status: "RETIRED" })}>Retire</button>}</div></td></tr>)}</tbody></table></div>
  </div>;
}
