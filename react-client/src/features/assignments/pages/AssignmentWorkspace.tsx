import { lazy, Suspense } from "react";
import { Link } from "react-router-dom";
import LearningHeader from "../../../shared/layout/LearningHeader";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import { canTeach } from "../../../shared/auth/permissions";
import "../../simulation/styles/learning.css";

const Assignments = lazy(() => import("./Assignments"));

export default function AssignmentWorkspace() {
  const user = useSessionStore((state) => state.user);
  return (
    <div className="learning-app">
      <LearningHeader />
      {canTeach(user) ? (
        <Suspense fallback={<main className="route-loading" aria-busy="true" />}>
          <Assignments workspaceLayout />
        </Suspense>
      ) : (
        <main className="learn-empty">
          <h1>Giao bài cho học sinh</h1>
          <p>Đăng nhập bằng tài khoản giáo viên để tạo bài giao.</p>
          <Link to="/login">Đăng nhập</Link>
        </main>
      )}
    </div>
  );
}