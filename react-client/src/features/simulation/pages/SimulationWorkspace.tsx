import { useState } from "react";
import LearningHeader from "../../../shared/layout/LearningHeader";
import Icon from "../../../shared/ui/LearningIcon";
import TeacherLibraryPane from "../../library/components/TeacherLibraryPane";
import { useTeacherLibrary } from "../../library/hooks/useTeacherLibrary";
import SimulationStage from "../components/SimulationStage";
import SimulationInspector from "../components/SimulationInspector";
import { useSimulationWorkspace } from "../hooks/useSimulationWorkspace";
import "../styles/learning.css";
import "../styles/simulation.css";

export default function SimulationWorkspace() {
  const [libraryCollapsed, setLibraryCollapsed] = useState(false);
  const [mobilePanel, setMobilePanel] = useState<"observe" | "inspect">("observe");
  const [readoutsTarget, setReadoutsTarget] = useState<HTMLDivElement | null>(null);
  const libraryState = useTeacherLibrary();
  const { canManageLearningContent, events, library, input, preview, experiment, explanation, save } = useSimulationWorkspace(libraryState);
  return (
    <div className="learning-app simulation-workspace-app" onPaste={events.onPaste} onDrop={events.onDrop}
      onDragOver={event => { if (event.dataTransfer.types.includes("Files")) event.preventDefault(); }}>
      <LearningHeader onNewSimulation={input.reset} libraryCollapsed={libraryCollapsed}
        onToggleLibrary={canManageLearningContent ? () => setLibraryCollapsed(value => !value) : undefined} />
      <main className="learn-workspace" id="learning-workspace">
        <div className="learn-top-area" aria-hidden="true" />
        <nav className="learn-mobile-nav" aria-label="Chuyển vùng học tập">
          <button type="button" aria-pressed={mobilePanel === "observe"} onClick={() => setMobilePanel("observe")}>
            <Icon name="play" /> Quan sát
          </button>
          <button type="button" aria-pressed={mobilePanel === "inspect"} onClick={() => setMobilePanel("inspect")}>
            <Icon name="sliders" /> Thông số & Chi tiết
          </button>
        </nav>
        <div className="learn-layout" data-mobile-panel={mobilePanel} data-library-pane={canManageLearningContent}
          data-library-collapsed={libraryCollapsed}>
          {canManageLearningContent && <TeacherLibraryPane {...library} />}
          <SimulationStage input={input} preview={preview} readoutsTarget={readoutsTarget}
            complaint={canManageLearningContent ? { simulationId: save.currentSimulationId || undefined, description: experiment.simulation?.description, parameters: experiment.values } : undefined} />
          <SimulationInspector experiment={experiment} explanation={explanation} save={save} readoutsRef={setReadoutsTarget} />
        </div>
      </main>
    </div>
  );
}
