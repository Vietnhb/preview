import api from "../../../api/axios";
import { useState } from "react";
import { Button, Card, Table } from "@radix-ui/themes";
import { LoadState } from "../../operations/OperationsKit";
import { useAction, useResource } from "../../operations/operationsData";
import type { ModuleRelease } from "./reviewerTypes";
import { reviewerStatusLabel } from "./reviewerUtils";
import { ReviewerFilter, ReviewerHeader, ReviewerRefresh, ReviewerSearch, ReviewStatus } from "./ReviewerKit";

export function ModuleApprovalTab() {
  const resource = useResource<ModuleRelease[]>("/reviewer/module-releases");
  const action = useAction();
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("");
  const rows = (resource.data ?? []).filter((release) => (!filter || release.lifecycleStatus === filter) && `${release.topic} ${release.moduleName} ${release.schemaId}`.toLowerCase().includes(query.toLowerCase()));
  const changeLifecycle = async (release: ModuleRelease, status: ModuleRelease["lifecycleStatus"]) => {
    const ok = await action.run(() => api.put(`/reviewer/module-releases/${release.id}/lifecycle`, null, { params: { status } }), `Đã cập nhật module ${release.moduleName} sang trạng thái ${reviewerStatusLabel(status).toLowerCase()}.`);
    if (ok) resource.refresh();
  };
  return <Card size="3" className="reviewer-panel"><ReviewerHeader title="Phê duyệt module" icon="module" count={resource.data?.length} actions={<ReviewerRefresh refresh={resource.refresh} loading={resource.loading} />} /><LoadState {...resource} />{action.feedback}<div className="reviewer-toolbar"><ReviewerSearch aria-label="Tìm module" placeholder="Tìm module hoặc chủ đề" value={query} onChange={(event) => setQuery(event.target.value)} /><ReviewerFilter label="Lọc trạng thái module" value={filter} onChange={setFilter} statuses={["DRAFT", "APPROVED", "RETIRED"]} /></div><div className="reviewer-table-scroll"><Table.Root variant="surface" size="2"><Table.Header><Table.Row><Table.ColumnHeaderCell>Module</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Chủ đề</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Cấu trúc dữ liệu</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Thao tác</Table.ColumnHeaderCell></Table.Row></Table.Header><Table.Body>{rows.map((release) => <Table.Row key={release.id}><Table.Cell><strong>{release.moduleName}</strong></Table.Cell><Table.Cell>{release.topic}</Table.Cell><Table.Cell><code>{release.schemaId}@{release.schemaVersion}</code></Table.Cell><Table.Cell><ReviewStatus status={release.lifecycleStatus} /></Table.Cell><Table.Cell><div className="reviewer-actions">{release.lifecycleStatus === "DRAFT" && <Button type="button" variant="soft" color="gray" size="2" disabled={action.busy} onClick={() => void changeLifecycle(release, "APPROVED")}>Phê duyệt</Button>}{release.lifecycleStatus === "APPROVED" && <Button type="button" variant="soft" color="gray" size="2" disabled={action.busy} onClick={() => void changeLifecycle(release, "RETIRED")}>Ngừng sử dụng</Button>}{release.lifecycleStatus === "RETIRED" && <span className="reviewer-muted">—</span>}</div></Table.Cell></Table.Row>)}{!resource.loading && !resource.error && rows.length === 0 && <Table.Row><Table.Cell colSpan={5} className="reviewer-table-empty">{query || filter ? "Không có module phù hợp." : "Chưa có module cần phê duyệt."}</Table.Cell></Table.Row>}</Table.Body></Table.Root></div></Card>;
}
