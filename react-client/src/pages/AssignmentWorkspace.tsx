import { Link } from "react-router-dom";
import { LearningHeader } from "../components/LearningWorkspace";
import { usePhysliveStore } from "../store/usePhysliveStore";
import Assignments from "./Assignments";
import "../styles/learning.css";

export default function AssignmentWorkspace() {
  const user = usePhysliveStore(state => state.user);
  return <div className="learning-app"><LearningHeader />
    {user?.role === "TEACHER" ? <Assignments studio /> : user?.role === "STUDENT" ? <Assignments />
      : <main className="learn-empty"><h1>Giao bài cho học sinh</h1><p>Đăng nhập bằng tài khoản giáo viên để tạo bài giao.</p><Link to="/login">Đăng nhập</Link></main>}
  </div>;
}
