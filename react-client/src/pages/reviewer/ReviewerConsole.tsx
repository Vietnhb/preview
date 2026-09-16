import { useState, type FormEvent } from "react";
import api from "../../api/axios";
import { Access, LoadState } from "../../components/operations/OperationsKit";
import {
  downloadJson,
  parseObject,
  useAction,
  useResource,
} from "../../components/operations/operationsData";
import type { Specification } from "../../types/physlive";
import "../../styles/modern-roles.css";

type ReviewItem = {
  id: string;
  specificationId: string;
  question: string;
  fieldPath: string;
  code: string;
  options: string[];
  problemText: string;
  topic: string;
  quantities: unknown;
  relations: unknown;
};

type Version = {
  id: string;
  schemaId: string;
  version: string;
  lifecycleStatus: string;
  name?: string;
  topic?: string;
  definition?: unknown;
  solverId?: string;
  outputDefinition?: { referenceSolverId?: string };
  createdAt: string;
};

type ModuleRelease = {
  id: string;
  topic: string;
  moduleName: string;
  schemaId: string;
  schemaVersion: string;
  lifecycleStatus: "DRAFT" | "APPROVED" | "RETIRED";
};

type Benchmark = {
  id: string;
  problemText: string;
  topic: string;
  gradeScope: string;
  sourceCategory: string;
  status: string;
  annotationCount: number;
  canAnnotate: boolean;
  canAdjudicate: boolean;
  annotations: { actor: string; specification: unknown }[];
  goldSpecification: unknown;
};

type Evaluation = {
  benchmarkCount: number;
  precision: number;
  recall: number;
  f1: number;
  kappa: number;
  incorrectRate: number;
};

const TABS = [
  { id: "queue", label: "Hàng đợi Phân xử Extraction (Queue)" },
  { id: "schemas", label: "Topic Schemas" },
  { id: "solvers", label: "Reference Solvers" },
  { id: "modules", label: "Module Approval" },
  { id: "benchmarks", label: "Benchmark & Đánh giá Nghiên cứu" },
];

export default function ReviewerConsole() {
  return (
    <Access reviewer>
      <ReviewerPage />
    </Access>
  );
}

function ReviewerPage() {
  const [tab, setTab] = useState("queue");

  return (
    <div className="main reviewer-main">
      <aside className="reviewer-sidebar" aria-label="Khu vực kiểm duyệt">
        <div className="reviewer-sidebar-brand">
          <span className="reviewer-sidebar-kicker">PHYSLIVE</span>
          <strong>Kiểm duyệt</strong>
          <p>Quản lý nội dung và kiểm chứng vật lý.</p>
        </div>
        <nav className="reviewer-sidebar-nav" aria-label="Các khu vực review">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              className={`reviewer-sidebar-button ${tab === t.id ? "active" : ""}`}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </nav>
      </aside>
      <section className="reviewer-content">
        {tab === "queue" && <QueueTab />}
        {tab === "schemas" && <VersionsTab solver={false} />}
        {tab === "solvers" && <VersionsTab solver={true} />}
        {tab === "modules" && <ModuleApprovalTab />}
        {tab === "benchmarks" && <BenchmarksTab />}
      </section>
    </div>
  );
}

// --------------------------------------------------------------------------
// TAB 1: ADJUDICATION QUEUE
// --------------------------------------------------------------------------
function QueueTab() {
  const resource = useResource<ReviewItem[]>("/reviewer/ambiguities");
  const [selectedId, setSelectedId] = useState("");
  const [query, setQuery] = useState("");

  const items = (resource.data ?? []).filter((i) =>
    `${i.topic} ${i.question} ${i.problemText}`
      .toLowerCase()
      .includes(query.toLowerCase()),
  );
  const selected = items.find((i) => i.id === selectedId) ?? items[0];

  return (
    <section className="modern-card reviewer-queue-card">
      <div className="modern-card-header">
        <div>
          <h2>Hàng đợi Phân xử Extraction (FR-REV-03)</h2>
          <p>
            Các trường hợp trích xuất có dữ kiện hoặc hướng chuyển động chưa đủ
            tin cậy cần chuyên gia giải quyết.
          </p>
        </div>
        <button
          type="button"
          className="role-switch-pill"
          style={{
            border: "1px solid var(--border-subtle)",
            background: "#ffffff",
            padding: "8px 16px",
          }}
          onClick={resource.refresh}
          disabled={resource.loading}
        >
          Làm mới
        </button>
      </div>

      <LoadState {...resource} />

      <div className="reviewer-queue-search">
        <input
          placeholder="Tìm theo nội dung đề bài, câu hỏi, chủ đề…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </div>

      {!resource.loading && !resource.error && items.length === 0 ? (
        <div
          style={{
            padding: "40px",
            textAlign: "center",
            color: "var(--text-muted)",
          }}
        >
          <span
            style={{ fontSize: "28px", display: "block", marginBottom: "8px" }}
          >
            ✨
          </span>
          <strong>Hàng đợi trống</strong>
          <p style={{ margin: "4px 0 0 0", fontSize: "13px" }}>
            Không có extraction nào đang chờ phân xử.
          </p>
        </div>
      ) : (
        <div className="reviewer-queue-layout">
          {/* Left: Ambiguity Items List */}
          <aside
            className="reviewer-queue-list"
            aria-label="Danh sách ca cần review"
          >
            {items.map((item) => (
              <button
                key={item.id}
                type="button"
                className={`reviewer-queue-item${selected?.id === item.id ? " active" : ""}`}
                onClick={() => setSelectedId(item.id)}
              >
                <div className="reviewer-queue-item-meta">
                  <span className="status-pill draft">
                    {item.topic || "Vật lý"}
                  </span>
                  <small>{item.fieldPath}</small>
                </div>
                <strong>{item.question}</strong>
                <p>{item.problemText?.slice(0, 100)}…</p>
              </button>
            ))}
          </aside>

          {/* Right: Resolution Workspace */}
          <main className="reviewer-queue-detail">
            {selected && (
              <ResolutionForm
                key={selected.id}
                item={selected}
                onResolved={resource.refresh}
              />
            )}
          </main>
        </div>
      )}
    </section>
  );
}

function ResolutionForm({
  item,
  onResolved,
}: Readonly<{ item: ReviewItem; onResolved: () => void }>) {
  const [answer, setAnswer] = useState("");
  const [comment, setComment] = useState("");
  const action = useAction();

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    await action.run(async () => {
      const response = await api.post<Specification>(
        `/reviewer/ambiguities/${item.id}/resolve`,
        {
          answer: answer.trim(),
          comment,
        },
      );
      const open =
        response.data.ambiguityCases ?? response.data.ambiguities ?? [];
      if (open.some((a) => a.code === item.code && a.status === "OPEN")) {
        throw new Error(
          "Câu trả lời đã gửi nhưng dữ kiện vẫn chưa đủ rõ. Hãy bổ sung giá trị hoặc hướng chuẩn.",
        );
      }
      onResolved();
    }, "Đã cập nhật đặc tả specification với phân xử chuyên môn thành công.");
  };

  return (
    <article className="reviewer-resolution-form">
      <h3 className="reviewer-detail-title">Đề bài gốc</h3>
      <div className="reviewer-original-problem">
        {item.problemText || "(Chưa có văn bản đề bài)"}
      </div>

      <details className="reviewer-extraction-details">
        <summary>
          Xem thực thể & quan hệ đã bóc tách (Quantities & Relations)
        </summary>
        <pre>
          {JSON.stringify(
            { quantities: item.quantities, relations: item.relations },
            null,
            2,
          )}
        </pre>
      </details>

      <form onSubmit={submit}>
        <div className="form-group reviewer-question-group">
          <p className="reviewer-review-question">
            ❓ {item.question}
          </p>
          <textarea
            rows={3}
            required
            placeholder="Nhập câu trả lời phân xử chính xác có kèm đại lượng, đơn vị hoặc hướng..."
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
          />
        </div>

        {Array.isArray(item.options) && item.options.length > 0 && (
          <div className="reviewer-suggestions">
            <small className="reviewer-suggestion-label">
              Gợi ý nhanh từ pipeline:
            </small>
            <div className="reviewer-suggestion-list">
              {item.options.map((opt) => (
                <button
                  key={opt}
                  type="button"
                  className="role-switch-pill"
                  onClick={() => setAnswer(opt)}
                >
                  {opt}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="form-group reviewer-comment-group">
          <label htmlFor="review-comment">
            Căn cứ chuyên môn / Ghi chú thẩm định:
          </label>
          <input
            id="review-comment"
            placeholder="Ví dụ: Lấy g = 9.8 m/s² theo giả định sách giáo khoa hiện hành..."
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
        </div>

        {action.feedback}

        <button
          type="submit"
          className="prediction-submit-btn reviewer-resolution-submit"
          disabled={action.busy || !answer.trim()}
        >
          {action.busy
            ? "Đang cập nhật đặc tả…"
            : "Gửi kết luận phân xử chuyên môn"}
        </button>
      </form>
    </article>
  );
}

// --------------------------------------------------------------------------
// TAB 2 & 3: SCHEMAS AND REFERENCE SOLVERS
// --------------------------------------------------------------------------
function VersionsTab({ solver }: Readonly<{ solver: boolean }>) {
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

function lifecycleClass(status: string) {
  if (status === "APPROVED") return "pass";
  if (status === "DRAFT") return "draft";
  return "fail";
}

function ModuleApprovalTab() {
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

function VersionEditorModal({
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
                placeholder="kinematics-1d"
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

// --------------------------------------------------------------------------
// TAB 4: BENCHMARK & EVALUATION
// --------------------------------------------------------------------------
function BenchmarksTab() {
  const resource = useResource<Benchmark[]>("/reviewer/benchmarks");
  const action = useAction();
  const [creating, setCreating] = useState(false);
  const [evaluation, setEvaluation] = useState<Evaluation | null>(null);
  const [reviewing, setReviewing] = useState<{
    id: string;
    mode: "annotations" | "adjudication";
  } | null>(null);
  const [reviewJson, setReviewJson] = useState(
    '{\n  "objects": [],\n  "quantities": [],\n  "relations": []\n}',
  );

  const create = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const body = Object.fromEntries(new FormData(e.currentTarget));
    const ok = await action.run(() => api.post("/reviewer/benchmarks", body));
    if (ok) {
      setCreating(false);
      resource.refresh();
    }
  };

  const runEvaluationPipeline = async () => {
    await action.run(async () => {
      const res = await api.post<Evaluation>("/evaluations/run");
      setEvaluation(res.data);
    }, "Đã chạy pipeline đánh giá comparative evaluation trên benchmark corpus.");
  };

  const submitReview = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    let specification: unknown;
    try {
      specification = parseObject(reviewJson);
    } catch (error) {
      await action.run(async () => {
        throw error;
      });
      return;
    }
    const ok = await action.run(
      () =>
        api.post(`/reviewer/benchmarks/${reviewing?.id}/${reviewing?.mode}`, {
          specification,
        }),
      "Đã lưu thẩm định benchmark.",
    );
    if (ok) {
      setReviewing(null);
      resource.refresh();
    }
  };

  return (
    <div>
      {/* KPI Cards when evaluation is run */}
      {evaluation && (
        <div className="modern-kpis">
          <div className="modern-kpi-card">
            <div className="modern-kpi-title">Precision (Độ chính xác)</div>
            <div
              className="modern-kpi-value"
              style={{ color: "var(--role-student)" }}
            >
              {(evaluation.precision * 100).toFixed(1)}%
            </div>
            <div className="modern-kpi-sub">Field-level precision</div>
          </div>
          <div className="modern-kpi-card">
            <div className="modern-kpi-title">Recall (Độ bao phủ)</div>
            <div
              className="modern-kpi-value"
              style={{ color: "var(--role-teacher)" }}
            >
              {(evaluation.recall * 100).toFixed(1)}%
            </div>
            <div className="modern-kpi-sub">Field-level recall</div>
          </div>
          <div className="modern-kpi-card">
            <div className="modern-kpi-title">F1 Score</div>
            <div
              className="modern-kpi-value"
              style={{ color: "var(--role-reviewer)" }}
            >
              {(evaluation.f1 * 100).toFixed(1)}%
            </div>
            <div className="modern-kpi-sub">F1 harmonic mean</div>
          </div>
          <div className="modern-kpi-card">
            <div className="modern-kpi-title">
              Cohen's Kappa (Độ đồng thuận)
            </div>
            <div className="modern-kpi-value">
              {evaluation.kappa.toFixed(3)}
            </div>
            <div className="modern-kpi-sub">Inter-annotator agreement</div>
          </div>
        </div>
      )}

      <div className="modern-card">
        <div className="modern-card-header">
          <div>
            <h2>Tập đề Benchmark & Đánh giá Nghiên cứu (FR-REV-06)</h2>
            <p>
              Quy trình hai chuyên gia độc lập annotate và chuyên gia thứ ba
              phân xử tạo Gold Specification.
            </p>
          </div>
          <div style={{ display: "flex", gap: "8px" }}>
            <button
              type="button"
              className="role-switch-pill"
              style={{
                border: "1px solid var(--border-subtle)",
                background: "#ffffff",
                padding: "8px 14px",
              }}
              disabled={!resource.data?.some((b) => b.goldSpecification)}
              onClick={() =>
                downloadJson(
                  resource.data?.filter((b) => b.goldSpecification),
                  "physlive-gold-corpus.json",
                )
              }
            >
              Xuất Gold Corpus JSON
            </button>
            <button
              type="button"
              className="prediction-submit-btn"
              style={{
                background: "var(--role-reviewer)",
                padding: "8px 16px",
                fontSize: "13px",
              }}
              disabled={action.busy}
              onClick={() => void runEvaluationPipeline()}
            >
              {action.busy ? "Đang tính toán…" : "Chạy Đánh giá Nghiên cứu ⚡"}
            </button>
            <button
              type="button"
              className="role-switch-pill"
              style={{
                border: "1px solid var(--border-subtle)",
                background: "#ffffff",
                padding: "8px 14px",
              }}
              onClick={() => setCreating(true)}
            >
              + Thêm bài Benchmark
            </button>
          </div>
        </div>

        <LoadState {...resource} />
        {action.feedback}

        {creating && (
          <dialog
            open
            className="modern-modal-overlay"
            onPointerDown={(event) => {
              if (event.target === event.currentTarget) setCreating(false);
            }}
          >
            <div className="modern-modal-content">
              <div className="modern-modal-header">
                <h3>Thêm đề bài vào Benchmark Corpus</h3>
                <button
                  type="button"
                  className="modern-modal-close"
                  onClick={() => setCreating(false)}
                >
                  ✕
                </button>
              </div>
              <form onSubmit={create}>
                <div className="form-group" style={{ marginBottom: "14px" }}>
                  <label htmlFor="benchmark-problem-text">
                    Nội dung đề bài *
                  </label>
                  <textarea
                    id="benchmark-problem-text"
                    name="problemText"
                    rows={4}
                    required
                    placeholder="Ví dụ: Một ô tô bắt đầu chuyển động thẳng nhanh dần đều..."
                  />
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label htmlFor="benchmark-topic">Chủ đề *</label>
                    <select id="benchmark-topic" name="topic">
                      <option value="Kinematics">Kinematics (Động học)</option>
                      <option value="Dynamics">Dynamics (Động lực học)</option>
                      <option value="Circuits">Circuits (Mạch điện)</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label htmlFor="benchmark-grade">Khối lớp THPT *</label>
                    <select id="benchmark-grade" name="gradeScope">
                      <option value="10">Lớp 10</option>
                      <option value="11">Lớp 11</option>
                      <option value="12">Lớp 12</option>
                    </select>
                  </div>
                </div>
                <div className="form-group" style={{ marginBottom: "18px" }}>
                  <label htmlFor="benchmark-source">
                    Nguồn đề / Quyền sử dụng *
                  </label>
                  <input
                    id="benchmark-source"
                    name="sourceCategory"
                    required
                    placeholder="SGK Vật lý 10 Kết nối tri thức, Tr. 32"
                  />
                </div>
                <div
                  style={{
                    display: "flex",
                    justifyContent: "flex-end",
                    gap: "10px",
                  }}
                >
                  <button
                    type="button"
                    className="role-switch-pill"
                    style={{
                      border: "1px solid var(--border-subtle)",
                      padding: "8px 16px",
                    }}
                    onClick={() => setCreating(false)}
                  >
                    Hủy
                  </button>
                  <button
                    type="submit"
                    className="prediction-submit-btn"
                    style={{ background: "var(--role-reviewer)" }}
                    disabled={action.busy}
                  >
                    Thêm vào Corpus
                  </button>
                </div>
              </form>
            </div>
          </dialog>
        )}

        {reviewing && (
          <dialog
            open
            className="modern-modal-overlay"
            onPointerDown={(event) => {
              if (event.target === event.currentTarget) setReviewing(null);
            }}
          >
            <div className="modern-modal-content">
              <div className="modern-modal-header">
                <h3>
                  {reviewing.mode === "annotations"
                    ? "Annotate benchmark"
                    : "Phân xử benchmark"}
                </h3>
                <button
                  type="button"
                  className="modern-modal-close"
                  onClick={() => setReviewing(null)}
                >
                  ✕
                </button>
              </div>
              <p>
                Nhập specification JSON. Backend sẽ kiểm tra đủ objects,
                quantities, relations và quy tắc độc lập annotator.
              </p>
              <form onSubmit={submitReview}>
                <textarea
                  id="review-json"
                  aria-label="Specification JSON"
                  className="ops-code"
                  rows={16}
                  spellCheck={false}
                  value={reviewJson}
                  onChange={(e) => setReviewJson(e.target.value)}
                  required
                />
                <div
                  style={{
                    display: "flex",
                    justifyContent: "flex-end",
                    gap: "10px",
                    marginTop: "14px",
                  }}
                >
                  <button
                    type="button"
                    className="role-switch-pill"
                    onClick={() => setReviewing(null)}
                  >
                    Hủy
                  </button>
                  <button
                    type="submit"
                    className="prediction-submit-btn"
                    disabled={action.busy}
                  >
                    {action.busy ? "Đang lưu…" : "Lưu thẩm định"}
                  </button>
                </div>
              </form>
            </div>
          </dialog>
        )}

        <div className="modern-table-wrapper">
          <table className="modern-table">
            <thead>
              <tr>
                <th>Đề bài Benchmark</th>
                <th>Chủ đề / Khối</th>
                <th>Nguồn bài</th>
                <th>Trạng thái Gold</th>
                <th>Annotations</th>
              </tr>
            </thead>
            <tbody>
              {resource.data?.map((b) => (
                <tr key={b.id}>
                  <td style={{ maxWidth: "340px" }}>
                    <span style={{ fontSize: "13px" }}>{b.problemText}</span>
                  </td>
                  <td>
                    <strong>{b.topic}</strong>
                    <small
                      style={{ display: "block", color: "var(--text-muted)" }}
                    >
                      Lớp {b.gradeScope}
                    </small>
                  </td>
                  <td>
                    <small style={{ color: "var(--text-secondary)" }}>
                      {b.sourceCategory}
                    </small>
                  </td>
                  <td>
                    <span
                      className={`status-pill ${b.goldSpecification ? "pass" : "draft"}`}
                    >
                      {b.goldSpecification ? "Gold Ready" : "Chờ thẩm định"}
                    </span>
                  </td>
                  <td>
                    <small style={{ color: "var(--text-muted)" }}>
                      {b.annotationCount} chuyên gia đã chú giải
                    </small>
                    <div
                      style={{ display: "flex", gap: "6px", marginTop: "8px" }}
                    >
                      {b.canAnnotate && (
                        <button
                          type="button"
                          className="role-switch-pill"
                          onClick={() => {
                            setReviewing({ id: b.id, mode: "annotations" });
                            setReviewJson(
                              '{\n  "objects": [],\n  "quantities": [],\n  "relations": []\n}',
                            );
                          }}
                        >
                          Annotate
                        </button>
                      )}
                      {b.canAdjudicate && (
                        <button
                          type="button"
                          className="prediction-submit-btn"
                          style={{ padding: "5px 9px", fontSize: "11px" }}
                          onClick={() => {
                            setReviewing({ id: b.id, mode: "adjudication" });
                            setReviewJson(
                              JSON.stringify(
                                b.annotations[0]?.specification ?? {
                                  objects: [],
                                  quantities: [],
                                  relations: [],
                                },
                                null,
                                2,
                              ),
                            );
                          }}
                        >
                          Phân xử
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
    </div>
  );
}
