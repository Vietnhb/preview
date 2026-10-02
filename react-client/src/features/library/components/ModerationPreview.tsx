import { useEffect, useState } from "react";
import { Button, Flex, Spinner } from "@radix-ui/themes";
import { getSharedSimulation } from "../../simulation/api/simulationApi";
import PhysicsScene from "../../simulation/components/CanvasPhysicsScene";
import type { LibraryItem, Simulation } from "../../../shared/types/physlive";
import { ReviewerDialog } from "../../reviewer/components/ReviewerDialog";
import { indexAtTime } from "../../simulation/model/learningModel";

export function ModerationPreview({ item, onClose }: { item: Pick<LibraryItem, "simulationId" | "title">; onClose: () => void }) {
  const [simulation, setSimulation] = useState<Simulation | null>(null);
  const [error, setError] = useState("");
  const [time, setTime] = useState(0);
  const [playing, setPlaying] = useState(false);
  useEffect(() => {
    let active = true;
    void getSharedSimulation(item.simulationId).then(value => { if (active) { setSimulation(value); setTime(value.time[0] ?? 0); } })
      .catch(() => { if (active) setError("Không thể mở bản mô phỏng cần duyệt."); });
    return () => { active = false; };
  }, [item.simulationId]);
  return <ReviewerDialog title={item.title} onClose={onClose} wide>
    {error ? <p role="alert">{error}</p> : !simulation ? <p role="status"><Spinner /> Đang mở mô phỏng…</p> : <>
      <div style={{ height: 360, borderRadius: 12, overflow: "hidden" }}><PhysicsScene simulation={simulation} index={indexAtTime(simulation.time, time)} overlays={{ grid: true, trajectory: true, velocity: false, acceleration: false }} time={time} playing={playing} onTimeChange={setTime} onPlaybackEnd={() => setPlaying(false)} /></div>
      <Flex gap="3" align="center" mt="4"><Button onClick={() => { if (time >= (simulation.time.at(-1) ?? 0)) setTime(simulation.time[0] ?? 0); setPlaying(value => !value); }}>{playing ? "Tạm dừng" : "Chạy mô phỏng"}</Button><Button variant="soft" color="gray" onClick={() => { setPlaying(false); setTime(simulation.time[0] ?? 0); }}>Về đầu</Button><span>{time.toFixed(2)} s</span></Flex>
    </>}
  </ReviewerDialog>;
}