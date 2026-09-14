import { Link } from "react-router-dom";
import LearningWorkspace, { LearningHeader } from "../components/LearningWorkspace";
import Icon from "../components/LearningIcon";
import { usePhysliveStore } from "../store/usePhysliveStore";
import "../styles/learning.css";

export default function Models() {
  const simulation = usePhysliveStore(state => state.simulation);
  const problem = usePhysliveStore(state => state.problem);
  const setSimulation = usePhysliveStore(state => state.setSimulation);
  if (!simulation) return <div className="learning-app"><LearningHeader /><main className="learn-empty"><span className="learn-empty-icon"><Icon name="atom" /></span><span className="learn-small-label">Phòng mô hình</span><h1>Chưa có mô phỏng để khám phá.</h1><p>Hãy tạo và xác nhận một specification trong Workspace trước khi mở mô hình.</p><Link className="learn-empty-back" to="/workspace">Tạo mô phỏng từ đề bài <Icon name="arrow" /></Link></main></div>;
  return <LearningWorkspace key={simulation.runId || simulation.simulationId} simulation={simulation} problem={problem} onUpdate={setSimulation} />;
}
