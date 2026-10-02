import { lazy, Suspense, type ReactElement } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useSessionStore } from "../shared/auth/sessionStore";
import { isStudentRole, ROLE_NAMES, LEARNING_MANAGER_ROLES, CONTENT_REVIEW_ROLES, APPLICATION_ROLES } from "../shared/auth/roles";
import { canTeach, isDepartmentHead, userHome } from "../shared/auth/permissions";
import RequireAccess from "./RequireAccess";

const AdminDirectory = lazy(() => import("../features/account/pages/AdminDirectory"));
const Admin = lazy(() => import("../features/management/pages/OverviewView").then(module => ({ default: module.OverviewView })));
const AdminSupport = lazy(() => import("../features/support/pages/AdminSupport"));
const AdminLayout = lazy(() => import("../features/management/layout/AdminLayout"));
const UserAccounts = lazy(() => import("../features/account/components/UsersView").then(module => ({ default: module.UsersView })));
const AdminSchools = lazy(() => import("../features/school/pages/SchoolsView").then(module => ({ default: module.SchoolsView })));
const AdminPlans = lazy(() => import("../features/billing/pages/PlansView").then(module => ({ default: module.PlansView })));
const AdminCurriculum = lazy(() => import("../features/curriculum/pages/CurriculumView").then(module => ({ default: module.CurriculumView })));
const AdminValidation = lazy(() => import("../features/management/pages/ValidationView").then(module => ({ default: module.ValidationView })));
const AdminPayments = lazy(() => import("../features/billing/pages/AdminPayments"));
const Home = lazy(() => import("../features/public/pages/Home"));
const Library = lazy(() => import("../features/library/pages/Library"));
const CommunityLibrary = lazy(() => import("../features/library/pages/CommunityLibrary"));
const Login = lazy(() => import("../features/auth/pages/Login"));
const ForcedPasswordChange = lazy(() => import("../features/auth/pages/ForcedPasswordChange"));
const DepartmentWorkspace = lazy(() => import("../features/school/pages/DepartmentWorkspace"));
const reviewerModule = () => import("../features/reviewer/pages/ReviewerConsole");
const Reviewer = lazy(reviewerModule);
const ReviewerHome = lazy(() => reviewerModule().then(module => ({ default: module.ReviewerHomePage })));
const ReviewerModeration = lazy(() => reviewerModule().then(module => ({ default: module.ReviewerModerationPage })));
const ReviewerTopics = lazy(() => reviewerModule().then(module => ({ default: module.ReviewerTopicsPage })));
const ReviewerComplaints = lazy(() => reviewerModule().then(module => ({ default: module.ReviewerComplaintsPage })));
const ReviewerBenchmarks = lazy(() => reviewerModule().then(module => ({ default: module.ReviewerBenchmarksPage })));
const adminAreaModule = () => import("../features/account/pages/AdminArea");
const AdminArea = lazy(adminAreaModule);
const AdminOverview = lazy(() => adminAreaModule().then(module => ({ default: module.AdminOverviewPage })));
const Signup = lazy(() => import("../features/auth/pages/Signup"));
const SchoolPaymentResult = lazy(() => import("../features/billing/pages/SchoolPaymentResult"));
const SchoolBilling = lazy(() => import("../features/billing/pages/SchoolBilling"));
const SchoolClasses = lazy(() => import("../features/school/components/SchoolClasses"));
const SchoolReports = lazy(() => import("../features/school/pages/SchoolReports"));
const SchoolLayout = lazy(() => import("../features/school/layout/SchoolLayout"));
const SchoolDashboard = lazy(() => import("../features/school/pages/SchoolDashboard"));
const Curriculum = lazy(() => import("../features/curriculum/pages/Curriculum"));
const Workspace = lazy(() => import("../features/simulation/pages/SimulationWorkspace"));
const AssignmentWorkspace = lazy(() => import("../features/assignments/pages/AssignmentWorkspace"));
const teacherAreaModule = () => import("../features/assignments/pages/TeacherArea");
const TeacherArea = lazy(teacherAreaModule);
const TeacherThemed = lazy(() => teacherAreaModule().then(module => ({ default: module.TeacherThemed })));
const TeacherComplaints = lazy(() => teacherAreaModule().then(module => ({ default: module.TeacherComplaintsPage })));
const Lab = lazy(() => import("../features/simulation/pages/Lab"));
const studentModule = () => import("../features/assignments/pages/StudentAssignments");
const StudentArea = lazy(studentModule);
const StudentHome = lazy(() => studentModule().then(module => ({ default: module.StudentHomePage })));
const StudentTasks = lazy(() => studentModule().then(module => ({ default: module.StudentAssignmentsPage })));
const StudentCommunity = lazy(() => studentModule().then(module => ({ default: module.StudentCommunityPage })));
const ProfilePage = lazy(() => import("../features/account/pages/ProfilePage"));
const SiteInfo = lazy(() => import("../features/public/pages/SiteInfo"));


export default function AppRoutes({ authReady }: { authReady: boolean }) {
  const { pathname, search } = useLocation();
  const user = useSessionStore(state => state.user);
  const managerBillingOnly = authReady
    && user?.role === ROLE_NAMES.SCHOOL
    && user.billingRequired === true
    && pathname !== "/school/billing"
    && pathname !== "/signup/payment-result";
  // A STAFF account that is only a department head has no teaching workspace.
  const teachingElement = (element: ReactElement) =>
    user?.role === ROLE_NAMES.STAFF && !canTeach(user) ? <Navigate to={userHome(user)} replace /> : element;
  const workspaceElement = (element: ReactElement) =>
    <RequireAccess ready={authReady} roles={LEARNING_MANAGER_ROLES}>{teachingElement(element)}</RequireAccess>;
  const roleElement = (roles: readonly string[], element: ReactElement) => {
    return <RequireAccess ready={authReady} roles={roles}>{element}</RequireAccess>;
  };
  const assignmentsElement =
    roleElement([ROLE_NAMES.STAFF, ROLE_NAMES.STUDENT, ROLE_NAMES.MANAGER],
      isStudentRole(user?.role) ? <Navigate to="/student/assignments" replace /> : teachingElement(<Navigate to={`/assignments/workspace${search}`} replace />));

  if (authReady && user?.mustChangePassword && pathname !== "/change-password") {
    return <Navigate to="/change-password" replace />;
  }
  if (authReady && !user?.mustChangePassword && pathname === "/change-password" && user) {
    return <Navigate to={userHome(user)} replace />;
  }
  if (authReady && user?.role === ROLE_NAMES.MANAGER && (pathname === "/admin" || pathname.startsWith("/admin/"))) {
    return <Navigate to={`${pathname.replace(/^\/admin/, '/manager')}${search}`} replace />;
  }
  if (authReady && user?.role === ROLE_NAMES.ADMIN
      && pathname !== "/" && pathname !== "/admin" && !pathname.startsWith("/admin/") && pathname !== "/profile" && pathname !== "/change-password") {
    return <Navigate to="/admin" replace />;
  }
  if (managerBillingOnly && !user?.mustChangePassword) return <Navigate to="/school/billing" replace />;

  return <Suspense fallback={<main className="route-loading" aria-busy="true" />}>
        <Routes>
          {/* Home is the guest landing page; signed-in accounts go to their own area. */}
          <Route path="/" element={!authReady ? <main className="route-loading" aria-busy="true" /> : user ? <Navigate to={userHome(user)} replace /> : <Home />} />
          <Route path="/change-password" element={<RequireAccess ready={authReady}><ForcedPasswordChange /></RequireAccess>} />
          <Route path="/department" element={roleElement([ROLE_NAMES.STAFF],
            isDepartmentHead(user) && user?.schoolId ? <DepartmentWorkspace /> : <Navigate to="/assignments" replace />)} />
          <Route path="/player" element={<Navigate to={`/workspace${search}`} replace />} />
          <Route path="/workspace" element={workspaceElement(<Workspace />)} />
          <Route
            path="/assignments/workspace"
            element={workspaceElement(<AssignmentWorkspace />)}
          />
          <Route
            path="/models"
            element={
              <Navigate
                to={`/assignments/workspace${search}`}
                replace
              />
            }
          />
          <Route path="/lab" element={workspaceElement(<TeacherArea />)}>
            <Route index element={<Lab />} />
            <Route path="complaints" element={<TeacherComplaints />} />
            <Route path="library" element={<Library />} />
            <Route path="community" element={<TeacherThemed><CommunityLibrary /></TeacherThemed>} />
            <Route path="department" element={isDepartmentHead(user) && user?.schoolId ? <DepartmentWorkspace /> : <Navigate to="/lab" replace />} />
            <Route path="*" element={<Navigate to="/lab" replace />} />
          </Route>
          {/* Teachers use the library inside their management area, next to submissions and complaints. */}
          <Route path="/library" element={<RequireAccess ready={authReady} roles={APPLICATION_ROLES}>{user?.role === ROLE_NAMES.STAFF && canTeach(user) ? <Navigate to="/lab/library" replace /> : <Library />}</RequireAccess>} />
          <Route path="/community" element={authReady ? <CommunityLibrary /> : <main className="route-loading" aria-busy="true" />} />
          <Route path="/assignments" element={assignmentsElement} />
          <Route path="/school" element={roleElement([ROLE_NAMES.SCHOOL], user?.schoolId ? <SchoolLayout /> : <Navigate to="/" replace />)}>
            <Route index element={<SchoolDashboard />} />
            <Route path="users" element={user?.schoolId ? <UserAccounts key={user.schoolId} schoolId={user.schoolId} /> : <Navigate to="/" replace />} />
            <Route path="classes" element={<SchoolClasses />} />
            <Route path="reports" element={<SchoolReports />} />
            <Route path="billing" element={<SchoolBilling />} />
            <Route path="*" element={<Navigate to="/school" replace />} />
          </Route>
          <Route path="/admin" element={roleElement([ROLE_NAMES.ADMIN], <AdminArea />)}>
            <Route index element={<AdminOverview />} />
            <Route path="users" element={<AdminDirectory />} />
            <Route path="*" element={<Navigate to="/admin" replace />} />
          </Route>
          <Route path="/student" element={roleElement([ROLE_NAMES.STUDENT], <StudentArea />)}>
            <Route index element={<StudentHome />} />
            <Route path="assignments" element={<StudentTasks />} />
            <Route path="community" element={<StudentCommunity />} />
            <Route path="*" element={<Navigate to="/student" replace />} />
          </Route>
          <Route path="/manager" element={roleElement([ROLE_NAMES.MANAGER], <AdminLayout />)}>
            <Route index element={<Admin />} />
            <Route path="users" element={<UserAccounts />} />
            <Route path="feedback" element={<AdminSupport kind="FEEDBACK" />} />
            <Route path="messages" element={<AdminSupport kind="MESSAGE" />} />
            <Route path="schools" element={<AdminSchools />} />
            <Route path="plans" element={<AdminPlans />} />
            <Route path="payments" element={<AdminPayments />} />
            <Route path="curriculum" element={<AdminCurriculum />} />
            <Route path="validation" element={<AdminValidation />} />
          </Route>
          <Route path="/reviewer" element={roleElement(CONTENT_REVIEW_ROLES, <Reviewer />)}>
            <Route index element={<ReviewerHome />} />
            <Route path="moderation" element={<ReviewerModeration />} />
            <Route path="topics/:section?" element={<ReviewerTopics />} />
            <Route path="benchmarks" element={<ReviewerBenchmarks />} />
            <Route path="complaints" element={<ReviewerComplaints />} />
            <Route path="*" element={<Navigate to="/reviewer" replace />} />
          </Route>
          <Route path="/curriculum" element={<RequireAccess ready={authReady} roles={APPLICATION_ROLES}><Curriculum /></RequireAccess>} />
          <Route path="/profile" element={<RequireAccess ready={authReady}><ProfilePage /></RequireAccess>} />
          <Route path="/about" element={<SiteInfo kind="about" />} />
          <Route path="/terms" element={<SiteInfo kind="terms" />} />
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
          <Route path="/signup/payment-result" element={<SchoolPaymentResult />} />
        </Routes>

  </Suspense>;
}
