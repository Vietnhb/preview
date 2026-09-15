import { Link } from "react-router-dom";
import LearningWorkspace, { LearningHeader } from "../components/workspace/LearningWorkspace";
import Icon from "../components/common/LearningIcon";
import { usePhysliveStore } from "../store/usePhysliveStore";
import "../styles/learning.css";

export default function Lab() {
  const simulation = usePhysliveStore(state => state.simulation);
  const problem = usePhysliveStore(state => state.problem);
  const setSimulation = usePhysliveStore(state => state.setSimulation);
  if (!simulation) return <div className="learning-app"><LearningHeader /><main className="learn-empty"><span className="learn-empty-icon"><Icon name="atom" /></span><span className="learn-small-label">Phòng thí nghiệm</span><h1>Chưa có mô phỏng để thử nghiệm.</h1><p>Chỉ simulation đã được AI và hai solver kiểm chứng mới được mở trong phòng thí nghiệm.</p><Link className="learn-empty-back" to="/workspace">Bắt đầu từ đề bài <Icon name="arrow" /></Link></main></div>;
  return <LearningWorkspace key={simulation.runId || simulation.simulationId} simulation={simulation} problem={problem} onUpdate={setSimulation} />;
}
