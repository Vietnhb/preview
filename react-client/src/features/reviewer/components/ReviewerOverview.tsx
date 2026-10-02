import { useCallback, useEffect, useState } from "react";
import { Button, Card, Heading, Text } from "@radix-ui/themes";
import api from "../../../shared/api/client";
import type { ReviewerAccess, ReviewerView } from "../model/reviewerAccess";
import type { Benchmark, ModuleRelease, Version } from "../model/reviewerTypes";
import { timeAgo } from "../model/reviewerUtils";
import { ReviewerIcon, ReviewerRefresh, type ReviewerIconName } from "./ReviewerKit";

type PendingItem = { createdAt?: string };
type Counts = {
  moderation?: { pending: number; oldest?: string };
  drafts?: { schemas: number; modules: number };
  benchmarks?: { annotate: number; adjudicate: number; draft: number };
};
type Task = { id: string; view: ReviewerView; section?: string; icon: ReviewerIconName; count: number; title: string; detail: string; action: string };

async function settle<T>(request: Promise<{ data: T }>) {
  try { return (await request).data; } catch { return undefined; }
}

function useOverview(access: ReviewerAccess) {
  const [counts, setCounts] = useState<Counts>({});
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const load = useCallback(async () => {
    setLoading(true); setFailed(false);
    const [library, schemas, modules, benchmarks] = await Promise.all([
      access.canReview ? settle(api.get<PendingItem[]>("/reviewer/library", { params: { status: "PENDING" } })) : undefined,
      access.canEdit ? settle(api.get<Version[]>("/reviewer/schemas")) : undefined,
      access.canEdit ? settle(api.get<ModuleRelease[]>("/reviewer/module-releases")) : undefined,
      access.canEdit ? settle(api.get<Benchmark[]>("/reviewer/benchmarks")) : undefined,
    ]);
    const next: Counts = {};
    if (access.canReview && library) {
      const oldest = library.map(item => item.createdAt).filter(Boolean).sort()[0];
      next.moderation = { pending: library.length, oldest };
    }
    if (access.canEdit) {
      next.drafts = {
        schemas: schemas?.filter(item => item.lifecycleStatus === "DRAFT").length ?? 0,
        modules: modules?.filter(item => item.lifecycleStatus === "DRAFT").length ?? 0,
      };
      if (benchmarks) next.benchmarks = {
        annotate: benchmarks.filter(item => item.canAnnotate).length,
        adjudicate: benchmarks.filter(item => item.canAdjudicate).length,
        draft: benchmarks.filter(item => item.status === "DRAFT").length,
      };
    }
    const expected = (access.canReview ? 1 : 0) + (access.canEdit ? 1 : 0);
    setFailed(expected > 0 && !library && !benchmarks);
    setCounts(next); setLoading(false);
  }, [access.canEdit, access.canReview]);
  useEffect(() => { void load(); }, [load]);
  return { counts, loading, failed, refresh: () => void load() };
}

export function ReviewerOverview({ access, name, onOpen }: Readonly<{ access: ReviewerAccess; name?: string; onOpen: (view: ReviewerView, extra?: Record<string, string>) => void }>) {
  const { counts, loading, failed, refresh } = useOverview(access);
  const tasks: Task[] = [];
  if (counts.moderation) tasks.push({ id: "moderation", view: "moderation", icon: "library", count: counts.moderation.pending, title: "Mô phỏng chờ duyệt", detail: counts.moderation.pending ? `Giáo viên gửi lên thư viện công khai${counts.moderation.oldest ? ` · cũ nhất ${timeAgo(counts.moderation.oldest)}` : ""}.` : "Không có mô phỏng nào đang chờ.", action: "Bắt đầu duyệt" });
  if (counts.benchmarks) {
    const work = counts.benchmarks.annotate + counts.benchmarks.adjudicate;
    tasks.push({ id: "benchmarks", view: "benchmarks", icon: "benchmark", count: work, title: "Đề kiểm thử cần bạn", detail: work ? `${counts.benchmarks.annotate} đề cần gán đáp án · ${counts.benchmarks.adjudicate} đề cần phân xử.` : "Không có đề nào đang chờ bạn.", action: "Mở danh sách đề" });
  }
  if (counts.drafts) {
    const drafts = counts.drafts.schemas + counts.drafts.modules;
    tasks.push({ id: "drafts", view: "topics", section: counts.drafts.schemas ? "schemas" : "modules", icon: "schema", count: drafts, title: "Bản nháp chờ phê duyệt", detail: drafts ? `${counts.drafts.schemas} chủ đề · ${counts.drafts.solvers} bộ giải · ${counts.drafts.modules} gói phát hành.` : "Không có bản nháp tồn đọng.", action: "Xem bản nháp" });
  }
  const total = tasks.reduce((sum, task) => sum + task.count, 0);
  const firstName = name?.trim().split(/\s+/).at(-1);

  return <div className="reviewer-stack">
    <header className="reviewer-hero">
      <div>
        <Heading as="h1" size="7">{firstName ? `Chào ${firstName},` : "Chào bạn,"}</Heading>
        <Text as="p" size="3" color="gray" mt="1">{loading ? "Đang kiểm tra công việc…" : total ? <>Hiện có <strong>{total}</strong> việc đang chờ bạn xử lý.</> : "Bạn đã xử lý hết công việc. Tuyệt vời!"}</Text>
      </div>
      <ReviewerRefresh refresh={refresh} loading={loading} />
    </header>
    {failed && <div className="ops-alert" role="alert">Không tải được số liệu. Kiểm tra kết nối máy chủ rồi bấm “Làm mới”.</div>}
    <div className="reviewer-task-grid">
      {loading && tasks.length === 0 && Array.from({ length: (access.canReview ? 1 : 0) + (access.canEdit ? 2 : 0) }, (_, index) => <Card key={index} size="3" className="reviewer-task reviewer-task-skeleton" aria-hidden="true"><span className="reviewer-task-icon" /><span className="reviewer-skeleton-line" /><span className="reviewer-skeleton-line short" /></Card>)}
      {tasks.map(task => <Card key={task.id} size="3" className={`reviewer-task${task.count ? " has-work" : ""}`}>
        <div className="reviewer-task-top"><span className="reviewer-task-icon"><ReviewerIcon name={task.icon} size={22} /></span><span className="reviewer-task-count">{loading ? "—" : task.count}</span></div>
        <Heading as="h2" size="4" mt="3">{task.title}</Heading>
        <Text as="p" size="2" color="gray" mt="1" className="reviewer-task-detail">{loading ? "Đang tải…" : task.detail}</Text>
        <Button mt="4" size="2" variant={task.count ? "solid" : "soft"} color={task.count ? undefined : "gray"} onClick={() => onOpen(task.view, task.section ? { section: task.section } : undefined)}>{task.action}<ReviewerIcon name="arrow" size={15} /></Button>
      </Card>)}
    </div>
    <Card size="3" className="reviewer-guide">
      <Heading as="h2" size="3">Quyền của bạn</Heading>
      <ul>
        {access.canReview && <li><ReviewerIcon name="eye" size={16} /><span><strong>Kiểm duyệt nội dung</strong> — xem mô phỏng giáo viên chia sẻ, phê duyệt, đánh dấu nổi bật, từ chối hoặc gỡ khỏi thư viện công khai.</span></li>}
        {access.canEdit && <li><ReviewerIcon name="edit" size={16} /><span><strong>Biên soạn dữ liệu chuẩn</strong> — quản lý chủ đề vật lý và đề kiểm thử độ chính xác.</span></li>}
      </ul>
      {!access.isManager && (!access.canEdit || !access.canReview) && <Text as="p" size="2" color="gray">Cần thêm quyền? Liên hệ bộ phận vận hành (Manager) để được cấp.</Text>}
    </Card>
  </div>;
}
