import type { ReactNode } from "react";
import { Outlet, useNavigate, useOutletContext } from "react-router-dom";
import { AssignmentList } from "../components/StudentAssignmentList";
import { StudentClassOverview } from "../components/StudentClassOverview";
import { AssignmentWorkbench } from "../components/StudentAssignmentWorkbench";
import { ResourceDiscovery } from "../../library/components/ResourceDiscovery";
import { useAssignmentPractice } from "../hooks/useAssignmentPractice";
import { useStudentResources } from "../hooks/useStudentResources";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import type { User } from "../../../shared/auth/types";
import AppSidebarLayout, { type SidebarGroup } from "../../../shared/layout/AppSidebar";
import "./student-workspace.css";

type StudentContext = {
  user: User | null;
  practice: ReturnType<typeof useAssignmentPractice>;
  resources: ReturnType<typeof useStudentResources>;
};
const useStudent = () => useOutletContext<StudentContext>();

/** One data load for the whole student area; pages read it through the outlet context. */
function StudentArea({ user }: Readonly<{ user: User | null }>) {
  const practice = useAssignmentPractice(user?.id);
  const resources = useStudentResources(user?.id);
  const groups: SidebarGroup[] = [
    { items: [{ to: "/student", label: "Tổng quan", icon: "grid", end: true }] },
    { label: "Học tập", items: [
      { to: "/student/assignments", label: "Bài tập được giao", icon: "book", badge: practice.pending },
      { to: "/student/community", label: "Kho mô phỏng", icon: "atom" },
    ] },
  ];
  return <AppSidebarLayout id="student" subtitle="Học tập" home="/student" groups={groups} contentClassName="student-area">
    <Outlet context={{ user, practice, resources } satisfies StudentContext} />
  </AppSidebarLayout>;
}

export default function StudentLayout() {
  const user = useSessionStore(state => state.user);
  return <StudentArea key={user?.id ?? "guest"} user={user} />;
}

function StudentPage({ layout, children }: Readonly<{ layout: "assigned" | "library"; children: ReactNode }>) {
  return <div className={`main student-main student-workspace student-layout-${layout}`}><div className="modern-container">{children}</div></div>;
}

export function StudentHomePage() {
  const { user, practice, resources } = useStudent();
  const navigate = useNavigate();
  return <StudentPage layout="assigned">
    <StudentClassOverview
      studentName={user?.fullName || "bạn"}
      schoolName={resources.classes[0]?.schoolName ?? user?.schoolName}
      classes={resources.classes}
      loading={resources.classesLoading}
      pendingAssignments={practice.pending}
      completedAssignments={practice.completed}
      sharedResources={resources.discovery.items.length}
      onExplore={() => navigate("/student/community")}
    />
    <AssignmentList {...practice.list} onSelect={assignment => { practice.list.onSelect(assignment); navigate("/student/assignments"); }} />
  </StudentPage>;
}

export function StudentAssignmentsPage() {
  const { practice } = useStudent();
  return <StudentPage layout="assigned">
    {practice.workbench ? <AssignmentWorkbench {...practice.workbench} /> : <AssignmentList {...practice.list} />}
  </StudentPage>;
}

export function StudentCommunityPage() {
  const { practice, resources } = useStudent();
  return <StudentPage layout="library"><ResourceDiscovery {...resources.discovery} vectors={practice.vectors} /></StudentPage>;
}
