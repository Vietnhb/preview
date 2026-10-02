import { useCallback, useEffect, useRef, useState, type ComponentProps } from "react";
import { getSharedSimulation } from "../../simulation/api/simulationApi";
import { communityLibrary } from "../../library/api/libraryApi";
import { curriculum } from "../../curriculum/api/curriculumApi";
import { studentClasses, type StudentClassSummary } from "../../school/api/schoolApi";
import { indexAtTime } from "../../simulation/model/learningModel";
import { useSessionStore } from "../../../shared/auth/sessionStore";
import type { Curriculum, LibraryItem, Simulation } from "../../../shared/types/physlive";
import type { ResourceDiscovery } from "../../library/components/ResourceDiscovery";

export function useStudentResources(userId: number | undefined) {
  const [items, setItems] = useState<LibraryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [catalog, setCatalog] = useState<Curriculum | null>(null);
  const [classes, setClasses] = useState<StudentClassSummary[]>([]);
  const [classesLoading, setClassesLoading] = useState(true);
  const [selectedItem, setSelectedItem] = useState<LibraryItem | null>(null);
  const [simulation, setSimulation] = useState<Simulation | null>(null);
  const [simulationLoading, setSimulationLoading] = useState(false);
  const [simulationError, setSimulationError] = useState("");
  const [frame, setFrame] = useState(0);
  const [time, setTime] = useState(0);
  const [playing, setPlaying] = useState(false);
  const mountedRef = useRef(true);
  const listRequestRef = useRef(0);
  const simulationRequestRef = useRef(0);
  const isCurrentSession = useCallback(() => mountedRef.current && userId !== undefined && useSessionStore.getState().user?.id === userId, [userId]);

  const load = useCallback(async () => {
    if (!isCurrentSession()) return;
    const requestId = ++listRequestRef.current;
    setLoading(true);
    setError("");
    try {
      const [data, loadedCurriculum] = await Promise.all([communityLibrary(), curriculum()]);
      if (!isCurrentSession() || requestId !== listRequestRef.current) return;
      setItems(data.filter(item => item.visibility === "SHARED" || item.visibility === "PUBLIC"));
      setCatalog(loadedCurriculum);
    } catch {
      if (isCurrentSession() && requestId === listRequestRef.current) setError("Không thể tải tài nguyên cộng đồng.");
    } finally {
      if (isCurrentSession() && requestId === listRequestRef.current) setLoading(false);
    }
  }, [isCurrentSession]);

  useEffect(() => {
    mountedRef.current = true;
    let active = true;
    void load();
    void studentClasses().then(data => {
      if (active && isCurrentSession()) setClasses(data);
    }).catch(() => {
      if (active && isCurrentSession()) setClasses([]);
    }).finally(() => {
      if (active && isCurrentSession()) setClassesLoading(false);
    });
    return () => {
      active = false;
      mountedRef.current = false;
      listRequestRef.current += 1;
      simulationRequestRef.current += 1;
    };
  }, [load, isCurrentSession]);

  const close = () => {
    simulationRequestRef.current += 1;
    setSelectedItem(null);
    setSimulation(null);
    setFrame(0);
    setTime(0);
    setSimulationLoading(false);
    setSimulationError("");
    setPlaying(false);
  };
  const open = async (item: LibraryItem) => {
    if (!isCurrentSession()) return;
    const requestId = ++simulationRequestRef.current;
    setSelectedItem(item);
    setSimulation(null);
    setFrame(0);
    setTime(0);
    setPlaying(false);
    setSimulationLoading(true);
    setSimulationError("");
    try {
      const loaded = await getSharedSimulation(item.simulationId);
      if (!isCurrentSession() || requestId !== simulationRequestRef.current) return;
      setSimulation(loaded);
      setTime(loaded.time[0] ?? 0);
    } catch {
      if (isCurrentSession() && requestId === simulationRequestRef.current) setSimulationError("Chưa tải được mô phỏng trong tài nguyên này.");
    } finally {
      if (isCurrentSession() && requestId === simulationRequestRef.current) setSimulationLoading(false);
    }
  };
  const discovery: Omit<ComponentProps<typeof ResourceDiscovery>, "vectors"> = {
    items, curriculum: catalog, loading, error,
    onRetry: () => { void load(); },
    selectedItem, simulation, time, simulationLoading, simulationError, frame, playing,
    onOpen: item => { void open(item); },
    onClose: close,
    onTogglePlaying: () => setPlaying(value => !value),
    onReset: () => {
      setPlaying(false);
      setFrame(0);
      setTime(simulation?.time[0] ?? 0);
    },
    onFrameChange: frameValue => {
      setPlaying(false);
      const nextFrame = Math.min(Math.max(0, frameValue), Math.max(0, (simulation?.time.length ?? 1) - 1));
      setFrame(nextFrame);
      setTime(simulation?.time[nextFrame] ?? 0);
    },
    onTimeChange: nextTime => {
      if (!simulation) return;
      setFrame(indexAtTime(simulation.time, nextTime));
      setTime(nextTime);
    },
    onPlaybackEnd: () => setPlaying(false),
  };
  return { classes, classesLoading, discovery, close };
}