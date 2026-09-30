import { useSearchParams } from "react-router-dom";
import { Button, Card, Heading, Badge } from "@radix-ui/themes";
import { motion, useReducedMotion } from "motion/react";
import { Access } from "../../components/operations/OperationsKit";
import { BenchmarksTab } from "../../components/roles/reviewer/BenchmarksTab";
import { ModuleApprovalTab } from "../../components/roles/reviewer/ModuleApprovalTab";
import { QueueTab } from "../../components/roles/reviewer/QueueTab";
import { VersionsTab } from "../../components/roles/reviewer/VersionsTab";
import { SharedLibraryTab } from "../../components/roles/reviewer/SharedLibraryTab";
import { ReviewerIcon, type ReviewerIconName } from "../../components/roles/reviewer/ReviewerKit";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import "./reviewer.css";

const TABS = [
  { id: "queue", label: "Dữ kiện cần xác minh", icon: "queue" },
  { id: "schemas", label: "Cấu trúc dữ liệu", icon: "schema" },
  { id: "solvers", label: "Bộ giải tham chiếu", icon: "solver" },
  { id: "modules", label: "Phê duyệt module", icon: "module" },
  { id: "benchmarks", label: "Bộ đề & đánh giá", icon: "benchmark" },
  { id: "library", label: "Thư viện chia sẻ", icon: "library" },
];

export default function ReviewerConsole() {
  return <Access reviewer><ReviewerPage /></Access>;
}

function ReviewerPage() {
  const [params, setParams] = useSearchParams();
  const reduceMotion = useReducedMotion();
  const user = usePhysliveStore(state => state.user);
  const canEdit = user?.role === "MANAGER" || user?.reviewerCanEdit === true;
  const canReview = user?.role === "MANAGER" || user?.reviewerCanReview === true;
  const tabs = TABS.filter(item => item.id === "library" ? canReview : canEdit);
  const requestedTab = params.get("tab");
  const tab = tabs.some((item) => item.id === requestedTab) ? requestedTab : tabs[0]?.id;
  const activeTab = tabs.find((item) => item.id === tab);
  const setTab = (value: string) => setParams((previous) => { const next = new URLSearchParams(previous); next.set("tab", value); return next; });
  if (!activeTab) return <main className="main reviewer-academic"><Card>Tài khoản chưa được cấp quyền chỉnh sửa hoặc kiểm duyệt.</Card></main>;
  return <div className="main reviewer-academic">
    <aside className="reviewer-nav-panel" aria-label="Khu vực kiểm duyệt">
      <Card size="2"><div className="reviewer-nav-brand"><span className="reviewer-nav-mark"><ReviewerIcon name="check" size={24} /></span><Heading as="h2" size="4">Kiểm duyệt</Heading><Badge color="indigo" size="1">REVIEWER</Badge></div>
      <nav className="reviewer-navigation" aria-label="Nghiệp vụ kiểm duyệt">
{tabs.map((item) => <Button key={item.id} asChild variant={tab === item.id ? "soft" : "ghost"} color={tab === item.id ? "indigo" : "gray"} size="3"><motion.button type="button" aria-current={tab === item.id ? "page" : undefined} className="reviewer-nav-button" onClick={() => setTab(item.id)} whileHover={reduceMotion ? undefined : { x: 3 }} transition={{ duration: .18 }}><ReviewerIcon name={item.icon as ReviewerIconName} size={18} /><span>{item.label}</span>{tab === item.id && <ReviewerIcon name="arrow" size={15} />}</motion.button></Button>)}
      </nav>
      </Card>
    </aside>
    <motion.section key={tab} className="reviewer-workspace" aria-label={activeTab.label} initial={reduceMotion ? false : { opacity: 0, y: 7 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: reduceMotion ? 0 : .18 }}>
      {tab === "queue" && <QueueTab />}
      {tab === "schemas" && <VersionsTab key="schemas" solver={false} />}
      {tab === "solvers" && <VersionsTab key="solvers" solver />}
      {tab === "modules" && <ModuleApprovalTab />}
      {tab === "benchmarks" && <BenchmarksTab />}
      {tab === "library" && <SharedLibraryTab />}
    </motion.section>
  </div>;
}
