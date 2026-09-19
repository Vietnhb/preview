import { lazy, Suspense, useEffect, useState, type ReactElement } from "react";
import axios from "axios";
import { Route, Routes, useLocation, Navigate } from "react-router-dom";
import { getMe, getLicenseStatus, type LicenseStatus } from "./api/userApi";
import NavBar from "./components/common/NavBar";
import { usePhysliveStore } from "./store/usePhysliveStore";
import { clearToken, getToken } from "./utils/token";
import { isTokenExpired } from "./utils/jwt";
import {
  canManageLearning,
  hasRole,
  isStudentRole,
  ROLE_NAMES,
  LEARNING_MANAGER_ROLES,
  CONTENT_REVIEW_ROLES,
} from "./types/roles";
import "./styles/app.css";
import "./styles/app-refresh.css";
import "./styles/admin-console.css";

// Keep the shell small. Page code and its page-specific CSS are fetched only
// when the matching route is rendered.
const SchoolManager = lazy(() => import("./components/roles/admin/AdminRoleViews").then(module => ({ default: module.SchoolManagerView })));
const Admin = lazy(() => import("./pages/admin/AdminConsole"));
const AdminSupport = lazy(() => import("./pages/admin/AdminSupport"));
const AdminLayout = lazy(() => import("./pages/admin/AdminLayout"));
const AdminUsers = lazy(() => import("./components/roles/admin/AdminRoleViews").then(module => ({ default: module.UsersView })));
const AdminSchools = lazy(() => import("./components/roles/admin/AdminRoleViews").then(module => ({ default: module.SchoolsView })));
const AdminPlans = lazy(() => import("./components/roles/admin/AdminRoleViews").then(module => ({ default: module.PlansView })));
const AdminCurriculum = lazy(() => import("./components/roles/admin/AdminRoleViews").then(module => ({ default: module.CurriculumView })));
const AdminValidation = lazy(() => import("./components/roles/admin/AdminRoleViews").then(module => ({ default: module.ValidationView })));
const AdminPayments = lazy(() => import("./pages/admin/AdminPayments"));
const Assignments = lazy(() => import("./pages/teacher/Assignments"));
const Home = lazy(() => import("./pages/Home"));
const Library = lazy(() => import("./pages/library/Library"));
const Login = lazy(() => import("./pages/auth/Login"));
const Reviewer = lazy(() => import("./pages/reviewer/ReviewerConsole"));
const Signup = lazy(() => import("./pages/auth/Signup"));
const SchoolPaymentResult = lazy(() => import("./pages/auth/SchoolPaymentResult"));
const SchoolBilling = lazy(() => import("./pages/school/SchoolBilling"));
const SchoolClasses = lazy(() => import("./pages/school/SchoolClasses"));
const SchoolReports = lazy(() => import("./pages/school/SchoolReports"));
const SchoolLayout = lazy(() => import("./pages/school/SchoolLayout"));
const SchoolDashboard = lazy(() => import("./pages/school/SchoolDashboard"));
const Curriculum = lazy(() => import("./pages/curriculum/Curriculum"));
const Workspace = lazy(() => import("./pages/teacher/Workspace"));
const AssignmentWorkspace = lazy(() => import("./pages/teacher/AssignmentWorkspace"));
const Lab = lazy(() => import("./pages/Lab"));
const StudentAssignments = lazy(() => import("./pages/student/StudentAssignments"));
const ProfilePage = lazy(() => import("./pages/profile/ProfilePage"));
const SiteInfo = lazy(() => import("./pages/SiteInfo"));

function App() {
  const { pathname, search } = useLocation();
  const isFullPage =
    pathname === "/player" ||
    pathname === "/workspace" ||
    pathname === "/assignments/workspace" ||
    pathname === "/models" ||
    pathname === "/lab" ||
    pathname.startsWith("/signup") ||
    pathname.startsWith("/admin") ||
    pathname.startsWith("/school");

  const isReviewerPage = pathname === "/reviewer";
  const setUser = usePhysliveStore((state) => state.setUser);
  const user = usePhysliveStore((state) => state.user);
  const [authReady, setAuthReady] = useState(false);
  const [license, setLicense] = useState<LicenseStatus | null>(null);
  const [dismissedLicenseNoticeKey, setDismissedLicenseNoticeKey] = useState("");
  useEffect(() => {
    if (!user?.schoolId) return;
    let active = true;
    void getLicenseStatus().then(value => { if (active) setLicense(value); }).catch(() => { if (active) setLicense(null); });
    return () => { active = false; };
  }, [user?.id, user?.schoolId, pathname]);
  const effectiveLicense = user?.schoolId ? license : null;
  const licenseNoticeKey = effectiveLicense
    ? `${user?.id}:${user?.schoolId}:${effectiveLicense.canPerformWriteOperations ? `renewal:${effectiveLicense.daysUntilExpiry}` : "inactive"}`
    : "";
  useEffect(() => {
    const token = getToken();
    if (!token || isTokenExpired(token)) {
      if (token) clearToken();
      setUser(null);
      void Promise.resolve().then(() => setAuthReady(true));
      return;
    }
    let active = true;
    void getMe()
      .then((user) => {
        if (!active) return;
        if (getToken() === token) setUser(user);
        setAuthReady(true);
      })
      .catch((error) => {
        if (!active) return;
        if (getToken() === token && axios.isAxiosError(error) && error.response?.status === 401) {
          clearToken();
          setUser(null);
        }
        setAuthReady(true);
      });
    return () => {
      active = false;
    };
  }, [setUser]);
  const canAccessWorkspace = canManageLearning(user?.role);
  const workspaceElement = (element: ReactElement) =>
    !authReady
      ? <main className="route-loading" aria-busy="true" />
      : !canAccessWorkspace
        ? <Navigate to="/" replace />
        : element;
  const roleElement = (roles: readonly string[], element: ReactElement) => {
    if (!authReady) return <main className="route-loading" aria-busy="true" />;
    return hasRole(user?.role, roles) ? (
      element
    ) : (
      <Navigate to="/" replace />
    );
  };
  const assignmentsElement =
    !authReady
      ? <main className="route-loading" aria-busy="true" />
      : user && !hasRole(user.role, [ROLE_NAMES.TEACHER, ROLE_NAMES.STUDENT, ROLE_NAMES.ADMIN])
        ? <Navigate to="/" replace />
        : isStudentRole(user?.role)
          ? <StudentAssignments />
          : <Assignments />;
  return (
    <div
      className={
        isFullPage ? "full-shell" : isReviewerPage ? "shell reviewer-shell" : "shell"
      }
    >
      {!isFullPage && <NavBar />}
      {user?.schoolId && effectiveLicense && (!effectiveLicense.canPerformWriteOperations || effectiveLicense.showRenewalBanner) && licenseNoticeKey !== dismissedLicenseNoticeKey &&
        <div role="status" className="license-notice">
          <p>
            {!effectiveLicense.canPerformWriteOperations
              ? "Trường chưa có license hiệu lực. Bạn có thể xem dữ liệu; vui lòng liên hệ quản lý trường để gia hạn."
              : `License của trường còn ${effectiveLicense.daysUntilExpiry} ngày.`}
          </p>
          <button
            type="button"
            aria-label="Đóng thông báo license"
            title="Đóng thông báo"
            onClick={() => setDismissedLicenseNoticeKey(licenseNoticeKey)}
          >
            <span aria-hidden="true">×</span>
          </button>
        </div>}
      <Suspense fallback={<main className="route-loading" aria-busy="true" />}>
        <Routes>
          <Route path="/" element={<Home />} />
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
          <Route path="/library" element={<Library />} />
          <Route path="/assignments" element={assignmentsElement} />
          <Route path="/school" element={roleElement([ROLE_NAMES.SCHOOL_MANAGER], user?.schoolId ? <SchoolLayout /> : <Navigate to="/" replace />)}>
            <Route index element={<SchoolDashboard />} />
            <Route path="users" element={user?.schoolId ? <SchoolManager key={user.schoolId} schoolId={user.schoolId} /> : <Navigate to="/" replace />} />
            <Route path="classes" element={<SchoolClasses />} />
            <Route path="reports" element={<SchoolReports />} />
            <Route path="billing" element={<SchoolBilling />} />
            <Route path="*" element={<Navigate to="/school" replace />} />
          </Route>
          <Route path="/admin" element={roleElement([ROLE_NAMES.ADMIN], <AdminLayout />)}>
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
          <Route path="/curriculum" element={<Curriculum />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/about" element={<SiteInfo kind="about" />} />
          <Route path="/terms" element={<SiteInfo kind="terms" />} />
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
          <Route path="/signup/payment-result" element={<SchoolPaymentResult />} />
        </Routes>
      </Suspense>
    </div>
  );
}
export default App;
