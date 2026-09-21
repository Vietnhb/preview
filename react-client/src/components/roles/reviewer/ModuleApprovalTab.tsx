import api from "../../../api/axios";
import { LoadState } from "../../operations/OperationsKit";
import { useAction, useResource } from "../../operations/operationsData";
import type { ModuleRelease } from "./reviewerTypes";
import { lifecycleClass } from "./reviewerUtils";

export function ModuleApprovalTab() {
  const resource = useResource<ModuleRelease[]>("/reviewer/module-releases");
  const action = useAction();
  const changeLifecycle = async (release: ModuleRelease, status: ModuleRelease["lifecycleStatus"]) => {
    const ok = await action.run(() => api.put(`/reviewer/module-releases/${release.id}/lifecycle`, null, { params: { status } }), `Đã cập nhật module ${release.moduleName} sang ${status}.`);
    if (ok) resource.refresh();
  };
  return <div className="modern-card"><div className="modern-card-header"><div><h2>Phê duyệt module</h2><p>Module chỉ được dùng khi schema version tương ứng đã qua validation và dependency gate.</p></div><button type="button" className="role-switch-pill" onClick={resource.refresh} disabled={resource.loading}>Làm mới</button></div><LoadState {...resource} />{action.feedback}<div className="modern-table-wrapper"><table className="modern-table"><thead><tr><th>Chủ đề</th><th>Module</th><th>Schema</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{resource.data?.map((release) => <tr key={release.id}><td>{release.topic}</td><td><strong>{release.moduleName}</strong></td><td><code>{release.schemaId}@{release.schemaVersion}</code></td><td><span className={`status-pill ${lifecycleClass(release.lifecycleStatus)}`}>{release.lifecycleStatus}</span></td><td><div style={{ display: "flex", gap: "6px" }}>{release.lifecycleStatus === "DRAFT" && <button type="button" className="role-switch-pill" disabled={action.busy} onClick={() => void changeLifecycle(release, "APPROVED")}>Approve</button>}{release.lifecycleStatus === "APPROVED" && <button type="button" className="role-switch-pill" disabled={action.busy} onClick={() => void changeLifecycle(release, "RETIRED")}>Retire</button>}</div></td></tr>)}</tbody></table></div></div>;
}
