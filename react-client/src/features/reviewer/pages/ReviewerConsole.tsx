import { useSearchParams } from "react-router-dom";
import { Badge, Card, Heading, Text } from "@radix-ui/themes";
import { Access } from "../../../shared/ui/OperationsKit";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import { allowedViews, reviewerAccess, reviewerNavigation, type ReviewerView } from "../model/reviewerAccess";
import { ReviewerIcon } from "../components/ReviewerKit";
import { ReviewerOverview } from "../components/ReviewerOverview";
import { ContentModeration } from "../components/ContentModeration";
import { TopicsWorkspace } from "../components/TopicsWorkspace";
import { BenchmarksTab } from "../components/BenchmarksTab";
import "../styles/reviewer.css";

export default function ReviewerConsole() {
  return <Access reviewer><ReviewerPage /></Access>;
}

/** Older links used ?tab=schemas|solvers|modules|library; keep them working. */
const LEGACY: Record<string, ReviewerView> = { library: "moderation", schemas: "topics", solvers: "topics", modules: "topics" };

function ReviewerPage() {
  const [params, setParams] = useSearchParams();
  const user = useSessionStore(state => state.user);
  const access = reviewerAccess(user);
  const groups = reviewerNavigation(access);
  const views = allowedViews(access);
  const requested = params.get("view") ?? LEGACY[params.get("tab") ?? ""] ?? params.get("tab");
  const view: ReviewerView = views.includes(requested as ReviewerView) ? requested as ReviewerView : "overview";
  const go = (next: ReviewerView, extra?: Record<string, string>) => setParams(() => {
    const search = new URLSearchParams({ view: next });
    Object.entries(extra ?? {}).forEach(([key, value]) => search.set(key, value));
    return search;
  });

  if (!access.canEdit && !access.canReview) return <main className="main reviewer-academic reviewer-blocked">
    <Card size="4"><span className="reviewer-empty-icon"><ReviewerIcon name="lock" size={28} /></span>
      <Heading as="h1" size="5" mt="3">Tài khoản chưa được cấp quyền</Heading>
      <Text as="p" color="gray" mt="2">Tài khoản kiểm duyệt cần ít nhất một quyền: <strong>kiểm duyệt nội dung</strong> hoặc <strong>biên soạn dữ liệu chuẩn</strong>. Hãy liên hệ bộ phận vận hành (Manager) để được cấp quyền.</Text>
    </Card>
  </main>;

  return <div className="main reviewer-academic">
    <aside className="reviewer-nav-panel" aria-label="Khu vực kiểm duyệt">
      <div className="reviewer-nav-profile">
        <Text as="div" size="1" color="gray">Xin chào</Text>
        <Text as="div" size="3" weight="bold" className="reviewer-nav-name">{user?.fullName ?? "Chuyên gia"}</Text>
        <div className="reviewer-permission-chips" aria-label="Quyền của bạn">
          {access.isManager ? <Badge color="gray" variant="soft">Vận hành · toàn quyền</Badge> : <>
            {access.canReview && <Badge color="indigo" variant="soft"><ReviewerIcon name="eye" size={12} />Kiểm duyệt</Badge>}
            {access.canEdit && <Badge color="gray" variant="soft"><ReviewerIcon name="edit" size={12} />Biên soạn</Badge>}
          </>}
        </div>
      </div>
      <nav className="reviewer-navigation">
        {groups.map(group => <div key={group.id} className="reviewer-nav-group">
          {group.label && <Text as="div" size="1" weight="medium" className="reviewer-nav-label">{group.label}</Text>}
          {group.items.map(item => <button key={item.id} type="button" className="reviewer-nav-item" aria-current={view === item.id ? "page" : undefined} onClick={() => go(item.id)}>
            <ReviewerIcon name={item.icon} size={18} /><span><strong>{item.label}</strong><small>{item.hint}</small></span>
          </button>)}
        </div>)}
      </nav>
    </aside>
    <section key={view} className="reviewer-workspace">
      {view === "overview" && <ReviewerOverview access={access} name={user?.fullName} onOpen={go} />}
      {view === "moderation" && <ContentModeration />}
      {view === "topics" && <TopicsWorkspace section={params.get("section")} onSection={section => go("topics", { section })} />}
      {view === "benchmarks" && <BenchmarksTab />}
    </section>
  </div>;
}
