import { useCallback, useEffect, useRef, useState } from "react";
import { communityLibrary } from "../../../api/libraryApi";
import { curriculum as getCurriculum } from "../../../api/curriculumApi";
import { getSharedSimulation } from "../../../api/simulationApi";
import type { Curriculum, LibraryItem, Simulation } from "../../../types/physlive";
import { indexAtTime } from "../../../utils/learningModel";

export function useCommunityLibrary() {
  const [items, setItems] = useState<LibraryItem[]>([]);
  const [curriculum, setCurriculum] = useState<Curriculum | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedItem, setSelectedItem] = useState<LibraryItem | null>(null);
  const [simulation, setSimulation] = useState<Simulation | null>(null);
  const [simulationLoading, setSimulationLoading] = useState(false);
  const [simulationError, setSimulationError] = useState("");
  const [frame, setFrame] = useState(0);
  const [time, setTime] = useState(0);
  const [playing, setPlaying] = useState(false);
  const requestRef = useRef(0);
  const listRequestRef = useRef(0);

  const load = useCallback(async () => {
    const requestId = ++listRequestRef.current;
    setLoading(true);
    setError("");
    try {
      const [itemsResult, catalogResult] = await Promise.allSettled([communityLibrary(), getCurriculum()]);
      if (requestId !== listRequestRef.current) return;
      if (itemsResult.status === "rejected") throw itemsResult.reason;
      setItems(itemsResult.value);
      setCurriculum(catalogResult.status === "fulfilled" ? catalogResult.value : null);
    } catch {
      if (requestId === listRequestRef.current) setError("Vui lòng kiểm tra kết nối hoặc đăng nhập lại.");
    } finally {
      if (requestId === listRequestRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    return () => { listRequestRef.current += 1; requestRef.current += 1; };
  }, [load]);

  const close = () => {
    requestRef.current += 1;
    setSelectedItem(null);
    setSimulation(null);
    setSimulationLoading(false);
    setSimulationError("");
    setFrame(0);
    setTime(0);
    setPlaying(false);
  };

  const open = async (item: LibraryItem) => {
    const requestId = ++requestRef.current;
    setSelectedItem(item);
    setSimulation(null);
    setSimulationError("");
    setSimulationLoading(true);
    setFrame(0);
    setTime(0);
    setPlaying(false);
    try {
      const loaded = await getSharedSimulation(item.simulationId);
      if (requestId !== requestRef.current) return;
      setSimulation(loaded);
      setTime(loaded.time[0] ?? 0);
    } catch {
      if (requestId === requestRef.current) setSimulationError("Không thể mở mô phỏng này.");
    } finally {
      if (requestId === requestRef.current) setSimulationLoading(false);
    }
  };

  return {
    items, curriculum, loading, error, load, selectedItem, simulation, time,
    simulationLoading, simulationError, frame, playing, open, close,
    togglePlaying: () => {
      if (simulation && time >= (simulation.time.at(-1) ?? 0)) {
        setFrame(0);
        setTime(simulation.time[0] ?? 0);
      }
      setPlaying(value => !value);
    },
    reset: () => { setPlaying(false); setFrame(0); setTime(simulation?.time[0] ?? 0); },
    seek: (nextFrame: number) => { setPlaying(false); setFrame(nextFrame); setTime(simulation?.time[nextFrame] ?? 0); },
    updateTime: (nextTime: number) => { if (simulation) { setFrame(indexAtTime(simulation.time, nextTime)); setTime(nextTime); } },
    stop: () => setPlaying(false),
  };
}
