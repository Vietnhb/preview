import api from "../../../api/axios";
import { LoadState } from "../../operations/OperationsKit";
import { useAction, useResource } from "../../operations/operationsData";
import type { ModuleRelease } from "./reviewerTypes";
import { lifecycleClass } from "./reviewerUtils";

export function ModuleApprovalTab() {
  const resource = useResource<ModuleRelease[]>("/reviewer/module-releases");
  const action = useAction();

  const changeLifecycle = async (
    release: ModuleRelease,
    status: ModuleRelease["lifecycleStatus"],
  ) => {
    const ok = await action.run(
      () =>
        api.put(`/reviewer/module-releases/${release.id}/lifecycle`, null, {
          params: { status },
        }),
      `Đã cập nhật module ${release.moduleName} sang ${status}.`,
    );
    if (ok) resource.refresh();
  };

  return (
    <div className="modern-card">
      <div className="modern-card-header">
        <div>
          <h2>Phê duyệt Topic Module (FR-REV-04)</h2>
          <p>
            Module chỉ khả dụng cho giáo viên sau khi schema version tương ứng
            được kiểm tra và phê duyệt.
          </p>
        </div>
        <button
          type="button"
          className="role-switch-pill"
          style={{
            border: "1px solid var(--border-subtle)",
            background: "#ffffff",
          }}
          onClick={resource.refresh}
          disabled={resource.loading}
        >
          Làm mới
        </button>
      </div>
      <LoadState {...resource} />
      {action.feedback}
      <div className="modern-table-wrapper">
        <table className="modern-table">
          <thead>
            <tr>
              <th>Topic</th>
              <th>Module</th>
              <th>Schema</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {(resource.data ?? []).map((release) => (
              <tr key={release.id}>
                <td>{release.topic}</td>
                <td>
                  <strong>{release.moduleName}</strong>
                </td>
                <td>
                  <code>
                    {release.schemaId}@{release.schemaVersion}
                  </code>
                </td>
                <td>
                  <span
                    className={`status-pill ${lifecycleClass(release.lifecycleStatus)}`}
                  >
                    {release.lifecycleStatus}
                  </span>
                </td>
                <td>
                  <div style={{ display: "flex", gap: "6px" }}>
                    {release.lifecycleStatus === "DRAFT" && (
                      <button
                        type="button"
                        className="role-switch-pill"
                        style={{
                          background: "var(--status-pass-bg)",
                          color: "var(--status-pass-text)",
                        }}
                        disabled={action.busy}
                        onClick={() =>
                          void changeLifecycle(release, "APPROVED")
                        }
                      >
                        Phê duyệt
                      </button>
                    )}
                    {release.lifecycleStatus === "APPROVED" && (
                      <button
                        type="button"
                        className="role-switch-pill"
                        style={{
                          background: "var(--status-fail-bg)",
                          color: "var(--status-fail-text)",
                        }}
                        disabled={action.busy}
                        onClick={() => void changeLifecycle(release, "RETIRED")}
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




