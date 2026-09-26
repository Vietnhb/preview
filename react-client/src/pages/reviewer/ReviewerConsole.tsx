import { useSearchParams } from "react-router-dom";
import { Access } from "../../components/operations/OperationsKit";
import { BenchmarksTab } from "../../components/roles/reviewer/BenchmarksTab";
import { ModuleApprovalTab } from "../../components/roles/reviewer/ModuleApprovalTab";
import { QueueTab } from "../../components/roles/reviewer/QueueTab";
import { VersionsTab } from "../../components/roles/reviewer/VersionsTab";
import { SharedLibraryTab } from "../../components/roles/reviewer/SharedLibraryTab";

const TABS = [
  { id: "queue", label: "Hàng đợi ambiguity" },
  { id: "schemas", label: "Topic packs" },
  { id: "solvers", label: "Solver versions" },
  { id: "modules", label: "Module approval" },
  { id: "benchmarks", label: "Benchmark & evaluation" },
  { id: "library", label: "Shared library" },
];

export default function ReviewerConsole() {
  return <Access reviewer><ReviewerPage /></Access>;
}

function ReviewerPage() {
  const [params, setParams] = useSearchParams();
  const tab = params.get("tab") ?? "queue";
  const setTab = (value: string) => setParams({ tab: value });
  return <div className="main reviewer-main">
    <aside className="reviewer-sidebar" aria-label="Khu vực kiểm duyệt">
      <div className="reviewer-sidebar-brand"><span className="reviewer-sidebar-kicker">PHYSLIVE</span><strong>Kiểm duyệt</strong><p>Quản lý nội dung và kiểm chứng vật lý.</p></div>
      <nav className="reviewer-sidebar-nav" aria-label="Các khu vực review">
        {TABS.map((item) => <button key={item.id} type="button" className={`reviewer-sidebar-button ${tab === item.id ? "active" : ""}`} onClick={() => setTab(item.id)}>{item.label}</button>)}
      </nav>
    </aside>
    <section className="reviewer-content">
      {tab === "queue" && <QueueTab />}
      {tab === "schemas" && <VersionsTab solver={false} />}
      {tab === "solvers" && <VersionsTab solver />}
      {tab === "modules" && <ModuleApprovalTab />}
      {tab === "benchmarks" && <BenchmarksTab />}
      {tab === "library" && <SharedLibraryTab />}
    </section>
  </div>;
}
