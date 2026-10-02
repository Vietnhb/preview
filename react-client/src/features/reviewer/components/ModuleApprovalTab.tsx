import { useState } from "react";
import { Button, Card, SegmentedControl, Table, Text } from "@radix-ui/themes";
import api from "../../../shared/api/client";
import { LoadState } from "../../../shared/ui/OperationsKit";
import { useAction, useResource } from "../../../shared/hooks/operationsData";
import type { ModuleRelease } from "../model/reviewerTypes";
import { matches } from "../model/reviewerUtils";
import { ReviewerDialog } from "./ReviewerDialog";
import { ReviewerRefresh, ReviewerSearch, ReviewStatus } from "./ReviewerKit";

type Lifecycle = ModuleRelease["lifecycleStatus"];

export function ModuleApprovalTab() {
  const resource = useResource<ModuleRelease[]>("/reviewer/module-releases");
  const action = useAction();
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<"ALL" | Lifecycle>("ALL");
  const [decision, setDecision] = useState<{ release: ModuleRelease; status: Lifecycle } | null>(null);
  const all = resource.data ?? [];
  const count = (status: Lifecycle) => all.filter(release => release.lifecycleStatus === status).length;
  const rows = all.filter(release => (filter === "ALL" || release.lifecycleStatus === filter) && matches(query, release.topic, release.moduleName, release.schemaId))
    .sort((a, b) => ["DRAFT", "APPROVED", "RETIRED"].indexOf(a.lifecycleStatus) - ["DRAFT", "APPROVED", "RETIRED"].indexOf(b.lifecycleStatus));
  const confirm = async () => {
    if (!decision) return;
    const ok = await action.run(() => api.put(`/reviewer/module-releases/${decision.release.id}/lifecycle`, null, { params: { status: decision.status } }), decision.status === "APPROVED" ? `Đã phát hành gói ${decision.release.moduleName}.` : `Đã ngừng gói ${decision.release.moduleName}.`);
    if (ok) { setDecision(null); resource.refresh(); }
  };
  return <Card size="3" className="reviewer-panel">
    <div className="reviewer-toolbar">
      <SegmentedControl.Root size="2" value={filter} onValueChange={value => setFilter(value as typeof filter)} aria-label="Lọc trạng thái">
        <SegmentedControl.Item value="ALL">Tất cả ({all.length})</SegmentedControl.Item>
        <SegmentedControl.Item value="DRAFT">Chờ phát hành ({count("DRAFT")})</SegmentedControl.Item>
        <SegmentedControl.Item value="APPROVED">Đang dùng ({count("APPROVED")})</SegmentedControl.Item>
        <SegmentedControl.Item value="RETIRED">Ngừng dùng ({count("RETIRED")})</SegmentedControl.Item>
      </SegmentedControl.Root>
      <ReviewerSearch aria-label="Tìm gói" placeholder="Tìm gói hoặc chủ đề" value={query} onChange={event => setQuery(event.target.value)} />
      <ReviewerRefresh refresh={resource.refresh} loading={resource.loading} />
    </div>
    <LoadState {...resource} />{action.feedback}
    <div className="reviewer-table-scroll"><Table.Root variant="surface" size="2">
      <Table.Header><Table.Row><Table.ColumnHeaderCell>Gói phát hành</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Dùng chủ đề</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell className="reviewer-col-actions">Thao tác</Table.ColumnHeaderCell></Table.Row></Table.Header>
      <Table.Body>
        {rows.map(release => <Table.Row key={release.id}>
          <Table.Cell><strong>{release.moduleName}</strong><small>{release.topic}</small></Table.Cell>
          <Table.Cell>{release.schemaId}<small>v{release.schemaVersion}</small></Table.Cell>
          <Table.Cell><ReviewStatus status={release.lifecycleStatus} /></Table.Cell>
          <Table.Cell><div className="reviewer-actions reviewer-actions-end">
            {release.lifecycleStatus === "DRAFT" && <Button type="button" size="2" disabled={action.busy} onClick={() => setDecision({ release, status: "APPROVED" })}>Phát hành</Button>}
            {release.lifecycleStatus === "APPROVED" && <Button type="button" variant="soft" color="gray" size="2" disabled={action.busy} onClick={() => setDecision({ release, status: "RETIRED" })}>Ngừng dùng</Button>}
            {release.lifecycleStatus === "RETIRED" && <Text size="2" color="gray">—</Text>}
          </div></Table.Cell>
        </Table.Row>)}
        {!resource.loading && !resource.error && rows.length === 0 && <Table.Row><Table.Cell colSpan={4} className="reviewer-table-empty">{query || filter !== "ALL" ? "Không có gói phù hợp." : "Chưa có gói phát hành nào."}</Table.Cell></Table.Row>}
      </Table.Body>
    </Table.Root></div>
    {decision && <ReviewerDialog title={decision.status === "APPROVED" ? "Phát hành gói?" : "Ngừng dùng gói?"} onClose={() => setDecision(null)}>
      <Text as="p" size="3"><strong>{decision.release.moduleName}</strong> · chủ đề {decision.release.schemaId} v{decision.release.schemaVersion}</Text>
      <ul className="reviewer-consequences">{decision.status === "APPROVED"
        ? <><li>Giáo viên sẽ tạo được mô phỏng thuộc gói này.</li><li>Định nghĩa chủ đề phải hợp lệ; nếu chưa, hệ thống sẽ báo lỗi.</li></>
        : <><li>Giáo viên sẽ không tạo mô phỏng mới thuộc gói này.</li><li>Mô phỏng đã tạo vẫn dùng được.</li></>}</ul>
      {action.feedback}
      <div className="reviewer-form-footer"><Button type="button" variant="soft" color="gray" size="2" onClick={() => setDecision(null)}>Hủy</Button><Button type="button" size="2" color={decision.status === "RETIRED" ? "red" : undefined} disabled={action.busy} onClick={() => void confirm()}>{action.busy ? "Đang cập nhật…" : decision.status === "APPROVED" ? "Phát hành" : "Ngừng dùng"}</Button></div>
    </ReviewerDialog>}
  </Card>;
}
