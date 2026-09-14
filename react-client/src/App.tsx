import { useEffect, useState } from "react";
import axios from "axios";
import { Route, Routes, useLocation, Navigate } from "react-router-dom";
import { getMe } from "./api/userApi";
import NavBar from "./components/NavBar";
import Admin from "./pages/AdminConsole";
import Assignments from "./pages/Assignments";
import Home from "./pages/Home";
import Library from "./pages/Library";
import Login from "./pages/Login";
import Reviewer from "./pages/ReviewerConsole";
import Signup from "./pages/Signup";
import Curriculum from "./pages/Curriculum";
import Workspace from "./pages/Workspace";
import AssignmentWorkspace from "./pages/AssignmentWorkspace";
import Lab from "./pages/Lab";
import { usePhysliveStore } from "./store/usePhysliveStore";
import { clearToken, getToken } from "./utils/token";
import { isTokenExpired } from "./utils/jwt";
import "./styles/app.css";

function App() {
  const pathname = useLocation().pathname;
  const isFullPage = pathname === "/" || pathname === "/player" || pathname === "/workspace" || pathname === "/assignments/workspace" || pathname === "/models" || pathname === "/lab";
  const setUser = usePhysliveStore((state) => state.setUser);
  const [authAttempt, setAuthAttempt] = useState(0);
  const [authError, setAuthError] = useState(false);
  useEffect(() => {
    const token = getToken();
    if (!token || isTokenExpired(token)) { if (token) clearToken(); setUser(null); return; }
    let active = true;
    void getMe().then(user => {
      if (active && getToken() === token) { setUser(user); setAuthError(false); }
    }).catch(error => {
      if (!active || getToken() !== token) return;
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        clearToken();
        setUser(null);
      } else setAuthError(true);
    });
    return () => { active = false; };
  }, [setUser, authAttempt]);
  return <div className={isFullPage ? "full-shell" : "shell"}>{!isFullPage && <NavBar />}
    {authError && <div className="workspace-error" role="alert">
      <span>Chưa tải được thông tin tài khoản. Vui lòng thử lại.</span>
      <button type="button" onClick={() => { setAuthError(false); setAuthAttempt(value => value + 1); }}>Thử lại</button>
    </div>}
    <Routes>
    <Route path="/" element={<Home />} />
    <Route path="/player" element={<Navigate to="/workspace" replace />} />
    <Route path="/workspace" element={<Workspace />} />
    <Route path="/assignments/workspace" element={<AssignmentWorkspace />} />
    <Route path="/models" element={<Navigate to={`/assignments/workspace${window.location.search}`} replace />} />
    <Route path="/lab" element={<Lab />} />
    <Route path="/library" element={<Library />} /><Route path="/assignments" element={<Assignments />} />
    <Route path="/admin" element={<Admin />} /><Route path="/reviewer" element={<Reviewer />} />
    <Route path="/curriculum" element={<Curriculum />} />
    <Route path="/login" element={<Login />} /><Route path="/signup" element={<Signup />} />
  </Routes></div>;
}
export default App;
