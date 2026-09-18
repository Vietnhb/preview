import { lazy, Suspense, useEffect, useState, type ReactElement } from "react";
import axios from "axios";
import { Route, Routes, useLocation, Navigate } from "react-router-dom";
import { getMe, getLicenseStatus, type LicenseStatus } from "./api/userApi";
import NavBar from "./components/common/NavBar";
import { usePhysliveStore } from "./store/usePhysliveStore";
import { clearToken, getToken } from "./utils/token";
import { isTokenExpired } from "./utils/jwt";
import "./styles/app.css";
import "./styles/app-refresh.css";
import "./styles/admin-console.css";

// Keep the shell small. Page code and its page-specific CSS are fetched only
// when the matching route is rendered.
const SchoolManager = lazy(() => import("./components/roles/admin/AdminRoleViews").then(module => ({ default: module.SchoolManagerView })));
const Admin = lazy(() => import("./pages/admin/AdminConsole"));
const AdminPlaceholder = lazy(() => import("./pages/admin/AdminConsole").then(module => ({ default: module.AdminPlaceholderView })));
const AdminLayout = lazy(() => import("./pages/admin/AdminLayout"));
const AdminUsers = lazy(() => import("./components/roles/admin/AdminRoleViews").then(module => ({ default: module.UsersView })));
const AdminSchools = lazy(() => import("./components/roles/admin/AdminRoleViews").then(module => ({ default: module.SchoolsView })));
const AdminCurriculum = lazy(() => import("./components/roles/admin/AdminRoleViews").then(module => ({ default: module.CurriculumView })));
const AdminValidation = lazy(() => import("./components/roles/admin/AdminRoleViews").then(module => ({ default: module.ValidationView })));
const Assignments = lazy(() => import("./pages/teacher/Assignments"));
const Home = lazy(() => import("./pages/Home"));
const Library = lazy(() => import("./pages/library/Library"));
const Login = lazy(() => import("./pages/auth/Login"));
const Reviewer = lazy(() => import("./pages/reviewer/ReviewerConsole"));
const Signup = lazy(() => import("./pages/auth/Signup"));
const Curriculum = lazy(() => import("./pages/curriculum/Curriculum"));
const Workspace = lazy(() => import("./pages/teacher/Workspace"));
const AssignmentWorkspace = lazy(() => import("./pages/teacher/AssignmentWorkspace"));
const Lab = lazy(() => import("./pages/Lab"));
const StudentAssignments = lazy(() => import("./pages/student/StudentAssignments"));
const ProfilePage = lazy(() => import("./pages/profile/ProfilePage"));
const SiteInfo = lazy(() => import("./pages/SiteInfo"));

function App() {
  const pathname = useLocation().pathname;
  const isFullPage =
    pathname === "/player" ||
    pathname === "/workspace" ||
    pathname === "/assignments/workspace" ||
    pathname === "/models" ||
    pathname === "/lab" ||
    pathname.startsWith("/admin") ||
    pathname === "/school";

  const isReviewerPage = pathname === "/reviewer";
  const setUser = usePhysliveStore((state) => state.setUser);
  const user = usePhysliveStore((state) => state.user);
  const [authReady, setAuthReady] = useState(false);
  const [license, setLicense] = useState<LicenseStatus | null>(null);
  useEffect(() => {
    if (!user?.schoolId) {
      setLicense(null);
      return;
    }
    let active = true;
    void getLicenseStatus().then(value => { if (active) setLicense(value); }).catch(() => { if (active) setLicense(null); });
    return () => { active = false; };
  }, [user?.id, user?.schoolId, pathname]);
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
        if (active && getToken() === token) {
          setUser(user);
          setAuthReady(true);
        }
      })
      .catch((error) => {
        if (!active || getToken() !== token) return;
        if (axios.isAxiosError(error) && error.response?.status === 401) {
          clearToken();
          setUser(null);
        }
        setAuthReady(true);
      });
    return () => {
      active = false;
    };
  }, [setUser]);
  const canAccessWorkspace = ["TEACHER", "ADMIN"].includes(
    user?.role ?? "",
  );
  const assignmentsElement =
    user?.role === "STUDENT" ? <StudentAssignments /> : <Assignments />;
  const workspaceElement = (element: ReactElement) =>
    authReady && !canAccessWorkspace ? <Navigate to="/" replace /> : element;
  const roleElement = (roles: string[], element: ReactElement) => {
    if (!authReady) return <main className="route-loading" aria-busy="true" />;
    return roles.includes(user?.role ?? "") ? (
      element
    ) : (
      <Navigate to="/" replace />
    );
  };
  return (
    <div
      className={
        isFullPage ? "full-shell" : isReviewerPage ? "shell reviewer-shell" : "shell"
      }
    >
      {!isFullPage && <NavBar />}
      {user?.schoolId && license && (!license.canPerformWriteOperations || license.showRenewalBanner) &&
        <div role="status" className="license-notice">
          {!license.canPerformWriteOperations
            ? "Trường chưa có license hiệu lực. Bạn có thể xem dữ liệu; vui lòng liên hệ quản lý trường để gia hạn."
            : `License của trường còn ${license.daysUntilExpiry} ngày.`}
        </div>}
      <Suspense fallback={<main className="route-loading" aria-busy="true" />}>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/player" element={<Navigate to="/workspace" replace />} />
          <Route path="/workspace" element={workspaceElement(<Workspace />)} />
          <Route
            path="/assignments/workspace"
            element={workspaceElement(<AssignmentWorkspace />)}
          />
          <Route
            path="/models"
            element={
              <Navigate
                to={`/assignments/workspace${globalThis.location.search}`}
                replace
              />
            }
          />
          <Route path="/lab" element={roleElement(["TEACHER"], <Lab />)} />
          <Route path="/library" element={<Library />} />
          <Route path="/assignments" element={assignmentsElement} />
          <Route path="/school" element={roleElement(["SCHOOL_MANAGER"], user?.schoolId ? <SchoolManager key={user.schoolId} schoolId={user.schoolId} /> : <Navigate to="/" replace />)} />
          <Route path="/admin" element={roleElement(["ADMIN"], <AdminLayout />)}>
            <Route index element={<Admin />} />
            <Route path="users" element={<AdminUsers />} />
            <Route path="feedback" element={<AdminPlaceholder title="Feedback" />} />
            <Route path="messages" element={<AdminPlaceholder title="Messages" />} />
            <Route path="schools" element={<AdminSchools />} />
            <Route path="curriculum" element={<AdminCurriculum />} />
            <Route path="validation" element={<AdminValidation />} />
          </Route>
          <Route
            path="/reviewer"
            element={roleElement(["CONTENT_REVIEWER", "ADMIN"], <Reviewer />)}
          />
          <Route path="/curriculum" element={<Curriculum />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/about" element={<SiteInfo kind="about" />} />
          <Route path="/terms" element={<SiteInfo kind="terms" />} />
          <Route path="/login" element={<Login />} />
          <Route path="/signup" element={<Signup />} />
        </Routes>
      </Suspense>
    </div>
  );
}
export default App;
