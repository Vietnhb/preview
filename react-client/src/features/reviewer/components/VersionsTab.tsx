import { useState } from "react";
import { Badge, Button, Card, Heading, SegmentedControl, Table, Text } from "@radix-ui/themes";
import api from "../../../shared/api/client";
import { LoadState } from "../../../shared/ui/OperationsKit";
import { useAction, useResource } from "../../../shared/hooks/operationsData";
import { VersionEditorModal } from "./VersionEditorModal";
import { ReviewerDialog } from "./ReviewerDialog";
import type { Version } from "../model/reviewerTypes";
import { formatDate, matches } from "../model/reviewerUtils";
import { ReviewerIcon, ReviewerRefresh, ReviewerSearch, ReviewStatus } from "./ReviewerKit";
import { objectRows } from "../model/jsonRows";
import { CapabilitiesSummary } from "./CapabilitiesEditor";

type Lifecycle = "DRAFT" | "APPROVED" | "RETIRED";
const ORDER = ["DRAFT", "APPROVED", "RETIRED"];
const gradeOf = (version: Version) => { const grade = (version.definition as { grade?: unknown } | undefined)?.grade; return grade ? `Lớp ${String(grade)}` : ""; };

export function VersionsTab() {
  const resource = useResource<Version[]>("/reviewer/schemas");
  const action = useAction();
  const [filter, setFilter] = useState<"ALL" | Lifecycle>("ALL");
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<Version | null>(null);
  const [editor, setEditor] = useState<{ version?: Version; clone: boolean } | null>(null);
  const [decision, setDecision] = useState<{ version: Version; status: "APPROVED" | "RETIRED" } | null>(null);
  const all = resource.data ?? [];
  const count = (status: Lifecycle) => all.filter(version => version.lifecycleStatus === status).length;
  const rows = all.filter(version => (filter === "ALL" || version.lifecycleStatus === filter) && matches(query, version.schemaId, version.name, version.topic, version.version))
    .sort((a, b) => ORDER.indexOf(a.lifecycleStatus) - ORDER.indexOf(b.lifecycleStatus));
  const noun = "chủ đề";

  const changeLifecycle = async () => {
    if (!decision) return;
    const ok = await action.run(() => api.put(`/reviewer/schema-versions/${decision.version.id}/lifecycle`, null, { params: { status: decision.status } }), decision.status === "APPROVED" ? "Đã phê duyệt. Phiên bản này bắt đầu được sử dụng." : "Đã ngừng sử dụng phiên bản.");
    if (ok) { setDecision(null); resource.refresh(); }
  };

  return <Card size="3" className="reviewer-panel">
    <div className="reviewer-toolbar">
      <SegmentedControl.Root size="2" value={filter} onValueChange={value => setFilter(value as typeof filter)} aria-label="Lọc trạng thái">
        <SegmentedControl.Item value="ALL">Tất cả ({all.length})</SegmentedControl.Item>
        <SegmentedControl.Item value="DRAFT">Bản nháp ({count("DRAFT")})</SegmentedControl.Item>
        <SegmentedControl.Item value="APPROVED">Đang dùng ({count("APPROVED")})</SegmentedControl.Item>
        <SegmentedControl.Item value="RETIRED">Ngừng dùng ({count("RETIRED")})</SegmentedControl.Item>
      </SegmentedControl.Root>
      <ReviewerSearch aria-label={`Tìm ${noun}`} placeholder="Tìm tên hoặc mã chủ đề" value={query} onChange={event => setQuery(event.target.value)} />
      <ReviewerRefresh refresh={resource.refresh} loading={resource.loading} />
      <Button type="button" size="2" onClick={() => setEditor({ clone: false })}><ReviewerIcon name="add" size={16} />Thêm chủ đề</Button>
    </div>
    <LoadState {...resource} />{action.feedback}

    <div className="reviewer-table-scroll"><Table.Root variant="surface" size="2">
      <Table.Header><Table.Row><Table.ColumnHeaderCell>Chủ đề</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Phiên bản</Table.ColumnHeaderCell><Table.ColumnHeaderCell>Trạng thái</Table.ColumnHeaderCell><Table.ColumnHeaderCell className="reviewer-col-actions">Thao tác</Table.ColumnHeaderCell></Table.Row></Table.Header>
      <Table.Body>
        {rows.map(version => <Table.Row key={version.id}>
          <Table.Cell><button type="button" className="reviewer-link" onClick={() => setSelected(version)}>{version.name || version.schemaId}</button>{gradeOf(version) && <small>{gradeOf(version)}</small>}</Table.Cell>
          <Table.Cell>v{version.version}<small>{formatDate(version.createdAt)}</small></Table.Cell>
          <Table.Cell><ReviewStatus status={version.lifecycleStatus} /></Table.Cell>
          <Table.Cell><div className="reviewer-actions reviewer-actions-end">
            {version.lifecycleStatus === "DRAFT" && <><Button type="button" variant="soft" color="gray" size="2" onClick={() => setEditor({ version, clone: false })}>Sửa</Button><Button type="button" size="2" onClick={() => setDecision({ version, status: "APPROVED" })}>Phê duyệt</Button></>}
            {version.lifecycleStatus === "APPROVED" && <><Button type="button" variant="soft" size="2" onClick={() => setEditor({ version, clone: true })}>Tạo phiên bản mới</Button><Button type="button" variant="soft" color="gray" size="2" onClick={() => setDecision({ version, status: "RETIRED" })}>Ngừng dùng</Button></>}
            {version.lifecycleStatus === "RETIRED" && <Button type="button" variant="soft" color="gray" size="2" onClick={() => setEditor({ version, clone: true })}>Nhân bản</Button>}
          </div></Table.Cell>
        </Table.Row>)}
        {!resource.loading && !resource.error && rows.length === 0 && <Table.Row><Table.Cell colSpan={4} className="reviewer-table-empty">{query || filter !== "ALL" ? `Không có ${noun} phù hợp.` : `Chưa có ${noun} nào. Bấm “Thêm chủ đề” để bắt đầu.`}</Table.Cell></Table.Row>}
      </Table.Body>
    </Table.Root></div>

    {decision && <ReviewerDialog title={decision.status === "APPROVED" ? "Phê duyệt phiên bản?" : "Ngừng sử dụng phiên bản?"} onClose={() => setDecision(null)}>
      <Text as="p" size="3"><strong>{decision.version.name || decision.version.schemaId}</strong> · v{decision.version.version}</Text>
      <ul className="reviewer-consequences">{decision.status === "APPROVED" ? <>
        <li>Hệ thống sẽ dùng phiên bản này cho các mô phỏng <strong>mới</strong>. Mô phỏng cũ giữ nguyên phiên bản đã dùng.</li>
        <li>Sau khi phê duyệt, nội dung <strong>không sửa được</strong> — muốn thay đổi phải tạo phiên bản mới.</li>
      </> : <>
        <li>Mô phỏng mới sẽ không dùng phiên bản này nữa.</li>
        <li>Mô phỏng và bài tập đã tạo trước đó vẫn hoạt động bình thường.</li>
      </>}</ul>
      {action.feedback}
      <div className="reviewer-form-footer"><Button type="button" variant="soft" color="gray" size="2" onClick={() => setDecision(null)}>Hủy</Button><Button type="button" size="2" color={decision.status === "RETIRED" ? "red" : undefined} disabled={action.busy} onClick={() => void changeLifecycle()}>{action.busy ? "Đang cập nhật…" : decision.status === "APPROVED" ? "Phê duyệt" : "Ngừng sử dụng"}</Button></div>
    </ReviewerDialog>}

    {selected && <VersionDetail version={selected} onClose={() => setSelected(null)} />}
    {editor && <VersionEditorModal initial={editor.version} clone={editor.clone} onClose={() => setEditor(null)} onSaved={() => { setEditor(null); resource.refresh(); }} />}
  </Card>;
}

function VersionDetail({ version, onClose }: Readonly<{ version: Version; onClose: () => void }>) {
  const definition = (version.definition && typeof version.definition === "object" ? version.definition : {}) as Record<string, unknown>;
  const objects = objectRows(definition.objectTypes);
  const quantities = objectRows(definition.quantityDefinitions);
  const capabilities = objectRows(definition.capabilities);
  const limitations = Array.isArray(definition.limitations) ? definition.limitations.map(String) : [];
  return <ReviewerDialog title={`${version.name || version.schemaId} · v${version.version}`} wide onClose={onClose}>
    <div className="reviewer-detail-meta"><ReviewStatus status={version.lifecycleStatus} /><Text size="2" color="gray">Tạo ngày {formatDate(version.createdAt)}</Text></div>
    <div className="reviewer-summary">
      {typeof definition.description === "string" && <Text as="p" size="2" className="reviewer-context-text">{definition.description}</Text>}
      <Heading as="h3" size="2">Đối tượng ({objects.length})</Heading>
      <div className="reviewer-chips">{objects.length ? objects.map((item, index) => <Badge key={index} color="gray" variant="soft" size="2">{String(item.label ?? item.type ?? "—")}</Badge>) : <Text size="2" color="gray">Chưa khai báo.</Text>}</div>
      <Heading as="h3" size="2">Đại lượng ({quantities.length})</Heading>
      <div className="reviewer-chips">{quantities.length ? quantities.map((item, index) => <Badge key={index} color="indigo" variant="soft" size="2">{String(item.label ?? item.key ?? "—")}{item.symbol ? ` (${String(item.symbol)})` : ""}</Badge>) : <Text size="2" color="gray">Chưa khai báo.</Text>}</div>
      <Heading as="h3" size="2">Bài toán mô phỏng và công thức ({capabilities.length})</Heading>
      <CapabilitiesSummary capabilities={capabilities} />
      {limitations.length > 0 && <><Heading as="h3" size="2">Giới hạn</Heading><ul className="reviewer-consequences">{limitations.map(item => <li key={item}>{item}</li>)}</ul></>}
    </div>
    <details className="reviewer-disclosure"><summary>Thông tin kỹ thuật</summary>
      <dl className="reviewer-evidence-grid"><div><dt>Mã chủ đề</dt><dd><code>{version.schemaId}</code></dd></div><div><dt>Số lần chỉnh sửa</dt><dd>{version.recordVersion ?? 0}</dd></div><div><dt>Mã kiểm tra nội dung</dt><dd><code>{version.definitionChecksum ?? "—"}</code></dd></div></dl>
      <pre className="reviewer-json">{JSON.stringify(version.definition, null, 2)}</pre>
    </details>
  </ReviewerDialog>;
}
