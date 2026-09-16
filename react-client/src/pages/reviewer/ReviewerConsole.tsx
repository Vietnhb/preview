import { useState } from "react";
import { Access } from "../../components/operations/OperationsKit";
import { BenchmarksTab } from "../../components/roles/reviewer/BenchmarksTab";
import { ModuleApprovalTab } from "../../components/roles/reviewer/ModuleApprovalTab";
import { QueueTab } from "../../components/roles/reviewer/QueueTab";
import { VersionsTab } from "../../components/roles/reviewer/VersionsTab";
import "../../styles/modern-roles.css";

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
