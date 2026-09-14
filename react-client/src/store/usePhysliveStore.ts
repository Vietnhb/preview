import { create } from "zustand";
import type { Problem, Simulation, User } from "../types/physlive";

type Store = {
  draftText: string | null; setDraftText: (text: string) => void;
  user: User | null; problem: Problem | null; simulation: Simulation | null; playing: boolean; currentTime: number;
  setUser: (user: User | null) => void; setProblem: (problem: Problem | null) => void; setSimulation: (simulation: Simulation | null) => void;
  setPlaying: (playing: boolean) => void; setCurrentTime: (time: number) => void;
};

export const usePhysliveStore = create<Store>((set) => ({
  draftText: null, setDraftText: (draftText) => set({ draftText }),
  user: null, problem: null, simulation: null, playing: false, currentTime: 0,
  setUser: (user) => set({ user }), setProblem: (problem) => set({ problem }), setSimulation: (simulation) => set({ simulation }),
  setPlaying: (playing) => set({ playing }), setCurrentTime: (currentTime) => set({ currentTime }),
}));
