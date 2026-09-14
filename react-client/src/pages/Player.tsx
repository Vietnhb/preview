import { Link } from "react-router-dom";
import LearningWorkspace, { LearningHeader } from "../components/LearningWorkspace";
import Icon from "../components/LearningIcon";
import { usePhysliveStore } from "../store/usePhysliveStore";
import "../styles/learning.css";

export default function Player() {
  const simulation = usePhysliveStore(state => state.simulation);
  const problem = usePhysliveStore(state => state.problem);
  const setSimulation = usePhysliveStore(state => state.setSimulation);
  if (!simulation) return <div className="learning-app"><LearningHeader /><main className="learn-empty"><span className="learn-empty-icon"><Icon name="atom" /></span><span className="learn-small-label">Phòng học vật lý</span><h1>Chưa có mô phỏng đã kiểm chứng.</h1><p>Simulation chỉ được hiển thị sau khi AI tạo specification đầy đủ và hai solver xác nhận kết quả.</p><Link className="learn-empty-back" to="/">Bắt đầu từ đề bài của bạn <Icon name="arrow" /></Link></main></div>;
  return <LearningWorkspace key={simulation.runId || simulation.simulationId} simulation={simulation} problem={problem} onUpdate={setSimulation} />;
}
