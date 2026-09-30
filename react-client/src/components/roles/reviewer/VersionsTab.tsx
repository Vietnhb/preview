import { useState } from "react";
import { Button, Card, Table } from "@radix-ui/themes";
import api from "../../../api/axios";
import { LoadState } from "../../operations/OperationsKit";
import { useAction, useResource } from "../../operations/operationsData";
import { VersionEditorModal } from "./VersionEditorModal";
import { ReviewerDialog } from "./ReviewerDialog";
import type { Version } from "./reviewerTypes";
import { reviewerStatusLabel } from "./reviewerUtils";
import { ReviewerFilter, ReviewerHeader, ReviewerIcon, ReviewerRefresh, ReviewerSearch, ReviewStatus } from "./ReviewerKit";

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
    const ok = await action.run(() => api.put(`/reviewer/${solver ? "solvers" : "schema-versions"}/${decision.version.id}/lifecycle`, null, { params: { status: decision.status } }), `Đã chuyển sang trạng thái ${reviewerStatusLabel(decision.status).toLowerCase()}.`);
    if (ok) { setDecision(null); resource.refresh(); }
  };

  return <Card size="3" className="reviewer-panel">
    <ReviewerHeader title={solver ? "Bộ giải tham chiếu" : "Cấu trúc dữ liệu"} icon={solver ? "solver" : "schema"} count={resource.data?.length} actions={<Button type="button" size="3" onClick={() => setEditor({ clone: false })}><ReviewerIcon name="add" size={18} />Tạo bản nháp</Button>} />
    <LoadState {...resource} />{action.feedback}

    {decision && <ReviewerDialog title={decision.status === "APPROVED" ? "Phê duyệt phiên bản" : "Ngừng sử dụng phiên bản"} onClose={() => setDecision(null)}><p>Chuyển <strong>{decision.version.schemaId}@{decision.version.version}</strong> sang trạng thái <strong>{reviewerStatusLabel(decision.status).toLowerCase()}</strong>?</p><div className="reviewer-form-footer"><Button type="button" variant="soft" color="gray" size="2" onClick={() => setDecision(null)}>Hủy</Button><Button type="button" size="2" disabled={action.busy} onClick={() => void changeLifecycle()}>{action.busy ? "Đang cập nhật…" : "Xác nhận"}</Button></div></ReviewerDialog>}

    {selected && <ReviewerDialog title={`${selected.schemaId} · v${selected.version}`} wide onClose={() => setSelected(null)}><dl className="reviewer-evidence-grid"><div><dt>Trạng thái</dt><dd>{reviewerStatusLabel(selected.lifecycleStatus)}</dd></div><div><dt>Lần cập nhật</dt><dd>{selected.recordVersion ?? 0}</dd></div><div><dt>Mã kiểm tra định nghĩa</dt><dd><code>{selected.definitionChecksum ?? "Chưa có"}</code></dd></div><div><dt>Mã kiểm tra liên kết</dt><dd><code>{selected.bindingChecksum ?? "Chưa có"}</code></dd></div><div><dt>Bộ giải số</dt><dd><code>{selected.solverId ?? "—"}</code></dd></div><div><dt>Bộ giải tham chiếu</dt><dd><code>{selected.outputDefinition?.referenceSolverId ?? "—"}</code></dd></div></dl><details open className="reviewer-extraction-details"><summary>{solver ? "Định nghĩa đầu ra" : "Định nghĩa cấu trúc dữ liệu"}</summary><pre className="reviewer-json">{JSON.stringify(solver ? selected.outputDefinition : selected.definition, null, 2)}</pre></details></ReviewerDialog>}

    {editor && <VersionEditorModal solver={solver} initial={editor.version} clone={editor.clone} implementations={implementations.data} onClose={() => setEditor(null)} onSaved={() => { setEditor(null); resource.refresh(); }} />}

    <div className="reviewer-toolbar"><ReviewerSearch aria-label="Tìm phiên bản" placeholder={solver ? "Tìm bộ giải hoặc phiên bản" : "Tìm tên, mã cấu trúc hoặc phiên bản"} value={query} onChange={(event) => setQuery(event.target.value)} /><ReviewerFilter label="Lọc trạng thái phiên bản" value={filter} onChange={setFilter} statuses={["DRAFT", "APPROVED", "RETIRED"]} /><ReviewerRefresh refresh={resource.refresh} loading={resource.loading} /></div>
    <div className="reviewer-table-scroll"><Table.Root variant="surface" size="2"><Table.Header><Table.Row><Table.ColumnHeaderCell>{solver ? "Bộ giải" : "Tên cấu trúc / chủ đề"}</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Phiên bản</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Chi tiết</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Thao tác</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>{rows.map((version) => <Table.Row key={version.id}><Table.Cell><strong>{solver ? version.solverId : version.name || version.schemaId}</strong><small>{version.schemaId}{version.topic ? ` · ${version.topic}` : ""}</small>{solver && <small>Tham chiếu: <code>{version.outputDefinition?.referenceSolverId ?? "—"}</code></small>}</Table.Cell><Table.Cell><code>v{version.version}</code><small>{new Date(version.createdAt).toLocaleDateString("vi-VN")}</small></Table.Cell><Table.Cell><ReviewStatus status={version.lifecycleStatus} /></Table.Cell><Table.Cell><Button type="button" variant="soft" color="gray" size="2" onClick={() => setSelected(version)}>Xem định nghĩa</Button></Table.Cell><Table.Cell><div className="reviewer-actions"><Button type="button" variant="soft" color="gray" size="2" onClick={() => setEditor({ version, clone: version.lifecycleStatus !== "DRAFT" })}>{version.lifecycleStatus === "DRAFT" ? "Chỉnh sửa" : "Tạo bản sao"}</Button>{version.lifecycleStatus === "DRAFT" && <Button type="button" variant="soft" color="gray" size="2" onClick={() => setDecision({ version, status: "APPROVED" })}>Phê duyệt</Button>}{version.lifecycleStatus === "APPROVED" && <Button type="button" variant="soft" color="gray" size="2" onClick={() => setDecision({ version, status: "RETIRED" })}>Ngừng sử dụng</Button>}</div></Table.Cell></Table.Row>)}{!resource.loading && !resource.error && rows.length === 0 && <Table.Row><Table.Cell colSpan={5} className="reviewer-table-empty">{query || filter ? "Không có phiên bản phù hợp." : "Chưa có phiên bản. Tạo bản nháp để bắt đầu."}</Table.Cell></Table.Row>}</Table.Body></Table.Root></div>
  </Card>;
}
