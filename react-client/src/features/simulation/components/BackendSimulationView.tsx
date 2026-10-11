import { useEffect, useMemo, useRef } from "react";
import type { Simulation } from "../../../shared/types/physlive";
import { hasSavedScene } from "../api/simulationUnderstandingApi";
import { learningSeries, interpolateAtTime, numberLabel } from "../model/learningModel";
import SavedScene from "./SavedScene";

type Props = Readonly<{
  simulation: Simulation; index: number; time?: number; playing?: boolean; speed?: number;
  overlays?: { grid: boolean; trajectory: boolean; velocity: boolean; acceleration: boolean };
  quality?: string; seekRevision?: number;
  onTimeChange?: (time: number) => void; onPlaybackEnd?: () => void;
}>;

/** Displays only backend-authored visuals or backend-declared samples. No inferred apparatus or physics. */
export default function BackendSimulationView(props: Props) {
  const { simulation, playing, speed = 1, onTimeChange, onPlaybackEnd } = props;
  const time = props.time ?? simulation.time[props.index];
  const cursor = useRef(time);
  useEffect(() => { cursor.current = time; }, [time]);
  useEffect(() => {
    if (!playing || !onTimeChange || !Number.isFinite(cursor.current)) return;
    const end = simulation.time.at(-1);
    if (end === undefined) return;
    let previous: number | undefined, request = 0;
    const tick = (now: number) => {
      if (previous !== undefined) cursor.current = Math.min(end, cursor.current + (now - previous) / 1000 * speed);
      previous = now;
      onTimeChange(cursor.current);
      if (cursor.current >= end) onPlaybackEnd?.();
      else request = requestAnimationFrame(tick);
    };
    request = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(request);
  }, [playing, speed, simulation, onTimeChange, onPlaybackEnd]);
  const series = useMemo(() => learningSeries(simulation), [simulation]);
  if (hasSavedScene(simulation.result)) return <SavedScene scene={simulation.result} />;
  return <section className="simulation-card" aria-label="Số liệu mô phỏng từ máy chủ">
    <p>Bản lưu này có số liệu từ máy chủ; chưa có mã trình bày mô phỏng.</p>
    {series.length > 0 && <dl className="sim-readouts">{series.map(item => <div key={item.key}>
      <dt>{item.label}</dt>
      <dd>{numberLabel(interpolateAtTime(simulation.time, item.data, time))} {item.unit}</dd>
    </div>)}</dl>}
  </section>;
}
