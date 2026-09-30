import { lazy, Suspense, type ReactElement } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { usePhysliveStore } from "../store/usePhysliveStore";
import { isStudentRole, ROLE_NAMES, LEARNING_MANAGER_ROLES, CONTENT_REVIEW_ROLES, APPLICATION_ROLES, roleHome } from "../types/roles";
import RequireAccess from "./RequireAccess";
const SchoolManager = lazy(() => import("../components/roles/admin/AdminRoleViews").then(module => ({ default: module.SchoolManagerView })));
const AdminDirectory = lazy(() => import("../pages/admin/AdminDirectory"));
const Admin = lazy(() => import("../pages/admin/AdminConsole"));
const AdminSupport = lazy(() => import("../pages/admin/AdminSupport"));
const AdminLayout = lazy(() => import("../pages/admin/AdminLayout"));
const AdminUsers = lazy(() => import("../components/roles/admin/AdminRoleViews").then(module => ({ default: module.UsersView })));
const AdminSchools = lazy(() => import("../components/roles/admin/AdminRoleViews").then(module => ({ default: module.SchoolsView })));
const AdminPlans = lazy(() => import("../components/roles/admin/AdminRoleViews").then(module => ({ default: module.PlansView })));
const AdminCurriculum = lazy(() => import("../components/roles/admin/AdminRoleViews").then(module => ({ default: module.CurriculumView })));
const AdminValidation = lazy(() => import("../components/roles/admin/AdminRoleViews").then(module => ({ default: module.ValidationView })));
const AdminPayments = lazy(() => import("../pages/admin/AdminPayments"));
const Home = lazy(() => import("../pages/Home"));
const Library = lazy(() => import("../pages/library/Library"));
const CommunityLibrary = lazy(() => import("../pages/community/CommunityLibrary"));
const Login = lazy(() => import("../pages/auth/Login"));
const ForcedPasswordChange = lazy(() => import("../pages/auth/ForcedPasswordChange"));
const DepartmentWorkspace = lazy(() => import("../pages/department/DepartmentWorkspace"));
const Reviewer = lazy(() => import("../pages/reviewer/ReviewerConsole"));
const Signup = lazy(() => import("../pages/auth/Signup"));
const SchoolPaymentResult = lazy(() => import("../pages/auth/SchoolPaymentResult"));
const SchoolBilling = lazy(() => import("../pages/school/SchoolBilling"));
const SchoolClasses = lazy(() => import("../pages/school/SchoolClasses"));
const SchoolReports = lazy(() => import("../pages/school/SchoolReports"));
const SchoolLayout = lazy(() => import("../pages/school/SchoolLayout"));
const SchoolDashboard = lazy(() => import("../pages/school/SchoolDashboard"));
const Curriculum = lazy(() => import("../pages/curriculum/Curriculum"));
const Workspace = lazy(() => import("../pages/teacher/SimulationWorkspace"));
const AssignmentWorkspace = lazy(() => import("../pages/teacher/AssignmentWorkspace"));
const Lab = lazy(() => import("../pages/Lab"));
const StudentAssignments = lazy(() => import("../pages/student/StudentAssignments"));
const ProfilePage = lazy(() => import("../pages/profile/ProfilePage"));
const SiteInfo = lazy(() => import("../pages/SiteInfo"));


export default function AppRoutes({ authReady }: { authReady: boolean }) {
  const { pathname, search } = useLocation();
  const user = usePhysliveStore(state => state.user);
  const managerBillingOnly = authReady
    && user?.role === ROLE_NAMES.SCHOOL
    && user.billingRequired === true
    && pathname !== "/school/billing"
    && pathname !== "/signup/payment-result";
  const workspaceElement = (element: ReactElement) =>
    <RequireAccess ready={authReady} roles={LEARNING_MANAGER_ROLES}>{element}</RequireAccess>;
  const roleElement = (roles: readonly string[], element: ReactElement) => {
    return <RequireAccess ready={authReady} roles={roles}>{element}</RequireAccess>;
  };
  const assignmentsElement =
    roleElement([ROLE_NAMES.STAFF, ROLE_NAMES.STUDENT, ROLE_NAMES.MANAGER],
      isStudentRole(user?.role) ? <StudentAssignments /> : <AssignmentWorkspace />);

  if (authReady && user?.mustChangePassword && pathname !== "/change-password") {
    return <Navigate to="/change-password" replace />;
  }
  if (authReady && !user?.mustChangePassword && pathname === "/change-password" && user) {
    return <Navigate to={roleHome(user.role, user.billingRequired)} replace />;
  }
  if (authReady && user?.role === ROLE_NAMES.MANAGER && (pathname === "/admin" || pathname.startsWith("/admin/"))) {
    return <Navigate to={`${pathname.replace(/^\/admin/, '/manager')}${search}`} replace />;
  }
  if (authReady && user?.role === ROLE_NAMES.ADMIN
      && pathname !== "/admin" && pathname !== "/admin/users" && pathname !== "/profile" && pathname !== "/change-password") {
    return <Navigate to="/admin/users" replace />;
  }
  if (managerBillingOnly && !user?.mustChangePassword) return <Navigate to="/school/billing" replace />;

  return <Suspense fallback={<main className="route-loading" aria-busy="true" />}>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/change-password" element={<RequireAccess ready={authReady}><ForcedPasswordChange /></RequireAccess>} />
          <Route path="/department" element={roleElement([ROLE_NAMES.STAFF],
            user?.staffType === "DEPARTMENT_HEAD" && user.schoolId ? <DepartmentWorkspace /> : <Navigate to="/assignments" replace />)} />
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
          <Route path="/lab" element={roleElement(LEARNING_MANAGER_ROLES, <Lab />)} />
          <Route path="/library" element={<RequireAccess ready={authReady} roles={APPLICATION_ROLES}><Library /></RequireAccess>} />
          <Route path="/community" element={authReady ? <CommunityLibrary /> : <main className="route-loading" aria-busy="true" />} />
          <Route path="/assignments" element={assignmentsElement} />
          <Route path="/school" element={roleElement([ROLE_NAMES.SCHOOL], user?.schoolId ? <SchoolLayout /> : <Navigate to="/" replace />)}>
            <Route index element={<SchoolDashboard />} />
            <Route path="users" element={user?.schoolId ? <SchoolManager key={user.schoolId} schoolId={user.schoolId} /> : <Navigate to="/" replace />} />
            <Route path="classes" element={<SchoolClasses />} />
            <Route path="reports" element={<SchoolReports />} />
            <Route path="billing" element={<SchoolBilling />} />
            <Route path="*" element={<Navigate to="/school" replace />} />
          </Route>
          <Route path="/admin" element={roleElement([ROLE_NAMES.ADMIN], <Navigate to="/admin/users" replace />)} />
          <Route path="/admin/users" element={roleElement([ROLE_NAMES.ADMIN], <AdminDirectory />)} />
          <Route path="/manager" element={roleElement([ROLE_NAMES.MANAGER], <AdminLayout />)}>
            <Route index element={<Admin />} />
            <Route path="users" element={<AdminUsers />} />
            <Route path="feedback" element={<AdminSupport kind="FEEDBACK" />} />
            <Route path="messages" element={<AdminSupport kind="MESSAGE" />} />
            <Route path="schools" element={<AdminSchools />} />
            <Route path="plans" element={<AdminPlans />} />
            <Route path="payments" element={<AdminPayments />} />
            <Route path="curriculum" element={<AdminCurriculum />} />
            <Route path="validation" element={<AdminValidation />} />
          </Route>
          <Route
            path="/reviewer"
            element={roleElement(CONTENT_REVIEW_ROLES, <Reviewer />)}
          />
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
