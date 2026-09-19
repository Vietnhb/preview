import { lazy, Suspense } from "react";
import { Link } from "react-router-dom";
import LearningHeader from "../../components/common/LearningHeader";
import { usePhysliveStore } from "../../store/usePhysliveStore";
import { canManageLearning } from "../../types/roles";
import "../../styles/learning.css";

const Assignments = lazy(() => import("./Assignments"));

export default function AssignmentWorkspace() {
  const user = usePhysliveStore((state) => state.user);
  return (
    <div className="learning-app">
      <LearningHeader />
      {canManageLearning(user?.role) ? (
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
