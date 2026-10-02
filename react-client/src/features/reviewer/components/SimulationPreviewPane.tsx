import { useEffect, useState } from "react";
import { Button, Spinner, Text } from "@radix-ui/themes";
import { getSharedSimulation } from "../../simulation/api/simulationApi";
import PhysicsScene from "../../simulation/components/CanvasPhysicsScene";
import type { Simulation } from "../../../shared/types/physlive";
import { indexAtTime } from "../../simulation/model/learningModel";
import { ReviewerIcon } from "./ReviewerKit";

/** Inline player so the reviewer judges the simulation itself, not just its title. */
export function SimulationPreviewPane({ simulationId, onLoaded }: Readonly<{ simulationId: string; onLoaded?: (ok: boolean) => void }>) {
  const [state, setState] = useState<{ id: string; simulation?: Simulation; error?: string }>({ id: "" });
  const [time, setTime] = useState(0);
  const [playing, setPlaying] = useState(false);
  useEffect(() => {
    let active = true;
    void getSharedSimulation(simulationId)
      .then(value => { if (active) { setState({ id: simulationId, simulation: value }); setTime(value.time[0] ?? 0); setPlaying(false); onLoaded?.(true); } })
      .catch(() => { if (active) { setState({ id: simulationId, error: "Không mở được mô phỏng này. Có thể dữ liệu mô phỏng bị lỗi — bạn nên từ chối và ghi rõ lý do." }); onLoaded?.(false); } });
    return () => { active = false; };
  }, [simulationId, onLoaded]);
  const simulation = state.id === simulationId ? state.simulation : undefined;
  const error = state.id === simulationId ? state.error : undefined;
  if (error) return <div className="reviewer-preview reviewer-preview-message" role="alert"><ReviewerIcon name="ban" size={22} /><Text size="2">{error}</Text></div>;
  if (!simulation) return <div className="reviewer-preview reviewer-preview-message" role="status"><Spinner /><Text size="2">Đang mở mô phỏng…</Text></div>;
  const end = simulation.time.at(-1) ?? 0;
  const start = simulation.time[0] ?? 0;
  return <div className="reviewer-preview">
    <div className="reviewer-preview-stage"><PhysicsScene simulation={simulation} index={indexAtTime(simulation.time, time)} overlays={{ grid: true, trajectory: true, velocity: false, acceleration: false }} time={time} playing={playing} onTimeChange={setTime} onPlaybackEnd={() => setPlaying(false)} /></div>
    <div className="reviewer-preview-controls">
      <Button size="2" onClick={() => { if (time >= end) setTime(start); setPlaying(value => !value); }}><ReviewerIcon name={playing ? "pause" : "play"} size={14} />{playing ? "Tạm dừng" : "Chạy"}</Button>
      <input type="range" aria-label="Thời gian mô phỏng" min={start} max={end || 1} step={(end - start) / 200 || 0.01} value={time} onChange={event => { setPlaying(false); setTime(Number(event.target.value)); }} />
      <Text size="2" color="gray" className="reviewer-preview-time">{time.toFixed(2)} / {end.toFixed(2)} s</Text>
    </div>
  </div>;
}
