import type { ComponentProps } from "react";
import SvgPixiScene from "./SvgPixiScene";
import "../styles/simulation.css";
import type { GeneratedSimulationResult } from "../api/simulationUnderstandingApi";
import type { BackendFieldMeta } from "../model/sceneModel";

/** A saved simulation exactly as stored: its illustration, timeline, parameters and watched values. */
export default function SavedScene({ scene, ...player }: Readonly<{ scene: GeneratedSimulationResult }
  & Pick<ComponentProps<typeof SvgPixiScene>, "cover" | "coverPlaying" | "onCoverFailed">>) {
  const spec = scene.simulationSpec;
  if (!spec.solverTimeline) return null;
  return <SvgPixiScene {...player} program={spec.visualProgram ?? { code: "" }} timeline={spec.solverTimeline}
    parameters={scene.savedParameters ?? Object.fromEntries(scene.parameters.map(parameter => [parameter.name, parameter.value]))}
    parameterInfo={scene.parameters} models={spec.physicsModels}
    fieldMeta={spec.solverFieldMeta as BackendFieldMeta | undefined} observables={spec.observables}
    verificationStatus={scene.validation?.status ?? "VISUAL_ONLY_UNVERIFIED"} />;
}
