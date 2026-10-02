import { useState } from "react";
import { Badge, SegmentedControl } from "@radix-ui/themes";
import { AssignmentList } from "../components/StudentAssignmentList";
import { StudentClassOverview } from "../components/StudentClassOverview";
import { AssignmentWorkbench } from "../components/StudentAssignmentWorkbench";
import { ResourceDiscovery } from "../../library/components/ResourceDiscovery";
import { useAssignmentPractice } from "../hooks/useAssignmentPractice";
import { useStudentResources } from "../hooks/useStudentResources";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import type { User } from "../../../shared/auth/types";
import "./student-workspace.css";

type Tab = "assigned" | "library";

function StudentWorkspace({ initialTab, user }: Readonly<{ initialTab: Tab; user: User | null }>) {
  const [activeTab, setActiveTab] = useState<Tab>(initialTab);
  const practice = useAssignmentPractice(user?.id);
  const resources = useStudentResources(user?.id);
  const chooseTab = (value: string) => {
    setActiveTab(value as Tab);
    practice.close();
    resources.close();
  };

  return <div className={`main student-main student-workspace student-layout-${activeTab}`}>
    <div className="modern-container">
      {!practice.workbench && activeTab === "assigned" && <StudentClassOverview
        studentName={user?.fullName || "bạn"}
        schoolName={resources.classes[0]?.schoolName ?? user?.schoolName}
        classes={resources.classes}
        loading={resources.classesLoading}
        pendingAssignments={practice.pending}
        completedAssignments={practice.completed}
        sharedResources={resources.discovery.items.length}
        onExplore={() => setActiveTab("library")}
      />}
      {!practice.workbench && <div className="student-page-tabs">
        <SegmentedControl.Root size="3" value={activeTab} aria-label="Khu vực học tập" onValueChange={chooseTab}>
          <SegmentedControl.Item value="assigned">Bài tập được giao<Badge variant="soft">{practice.assignments.length}</Badge></SegmentedControl.Item>
          <SegmentedControl.Item value="library">Cộng đồng<Badge color="cyan" variant="soft">{resources.discovery.items.length}</Badge></SegmentedControl.Item>
        </SegmentedControl.Root>
      </div>}
      {activeTab === "assigned" && (practice.workbench
        ? <AssignmentWorkbench {...practice.workbench} />
        : <AssignmentList {...practice.list} />)}
      {activeTab === "library" && <ResourceDiscovery {...resources.discovery} vectors={practice.vectors} />}
    </div>
  </div>;
}

export default function StudentAssignments({ initialTab = "assigned" }: Readonly<{ initialTab?: Tab }>) {
  const user = useSessionStore(state => state.user);
  return <StudentWorkspace key={user?.id ?? "guest"} user={user} initialTab={initialTab} />;
}