import { lazy, Suspense, useEffect, useState, type ReactElement } from "react";
import axios from "axios";
import { Route, Routes, useLocation, Navigate } from "react-router-dom";
import { getMe } from "./api/userApi";
import NavBar from "./components/common/NavBar";
import { usePhysliveStore } from "./store/usePhysliveStore";
import { clearToken, getToken } from "./utils/token";
import { isTokenExpired } from "./utils/jwt";
import "./styles/app.css";
import "./styles/app-refresh.css";

// Keep the shell small. Page code and its page-specific CSS are fetched only
// when the matching route is rendered.
const Admin = lazy(() => import("./pages/admin/AdminConsole"));
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
    pathname === "/admin";
  const isReviewerPage = pathname === "/reviewer";
  const setUser = usePhysliveStore((state) => state.setUser);
  const user = usePhysliveStore((state) => state.user);
  const [authReady, setAuthReady] = useState(false);
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
  const canAccessWorkspace = ["TEACHER", "REVIEWER", "ADMIN"].includes(
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
          <Route path="/admin" element={roleElement(["ADMIN"], <Admin />)} />
          <Route
            path="/reviewer"
            element={roleElement(["REVIEWER", "ADMIN"], <Reviewer />)}
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
