import type { Simulation } from "../types/physlive";
import { prepareSimulationData, type RuntimeData } from "./SimulationData";
import { indexAtTime, type TimelineCursor } from "./interpolate";

export type RuntimeFrame = {
  time: number;
  index: number;
  data: RuntimeData;
};

export type SimulationRuntimeOptions = {
  onFrame?: (frame: RuntimeFrame) => void;
  onPlaybackEnd?: () => void;
};

/** Owns the simulation clock. It has no React dependency and one RAF loop. */
export class SimulationRuntime {
  private data: RuntimeData | null = null;
  private readonly cursor: TimelineCursor = { index: 0 };
  private readonly onFrame?: (frame: RuntimeFrame) => void;
  private readonly onPlaybackEnd?: () => void;
  private readonly listeners = new Set<(frame: RuntimeFrame) => void>();
  private readonly playbackEndListeners = new Set<() => void>();
  private animationFrame: number | null = null;
  private playing = false;
  private time = 0;
  private speed = 1;
  private lastWallTime = 0;

  public constructor(options: SimulationRuntimeOptions = {}) {
    this.onFrame = options.onFrame;
    this.onPlaybackEnd = options.onPlaybackEnd;
  }

  public setSimulation(simulation: Simulation | RuntimeData | null): void {
    this.pause();
    this.data = simulation && "series" in simulation ? simulation : simulation ? prepareSimulationData(simulation) : null;
    this.time = this.data?.time[0] ?? 0;
    this.cursor.index = 0;
    this.emit();
  }

  public play(): void {
    if (!this.data || this.data.length < 2 || this.animationFrame !== null) return;
    const last = this.lastTime();
    if (this.time >= last) {
      this.time = this.data.time[0] ?? 0;
      this.cursor.index = 0;
      this.emit();
    }
    this.playing = true;
    this.lastWallTime = performance.now();
    this.animationFrame = requestAnimationFrame(this.tick);
  }

  public pause(): void {
    this.playing = false;
    if (this.animationFrame !== null) {
      cancelAnimationFrame(this.animationFrame);
      this.animationFrame = null;
    }
  }

  public seek(time: number): void {
    if (!this.data) return;
    this.time = this.clampTime(time);
    indexAtTime(this.data.time, this.time, this.cursor);
    this.emit();
  }

  public setSpeed(speed: number): void {
    this.speed = Number.isFinite(speed) ? Math.max(0.01, speed) : 1;
  }

  public subscribe(listener: (frame: RuntimeFrame) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  public subscribePlaybackEnd(listener: () => void): () => void {
    this.playbackEndListeners.add(listener);
    return () => this.playbackEndListeners.delete(listener);
  }

  public getTime(): number { return this.time; }
  public getIndex(): number { return this.cursor.index; }
  public isPlaying(): boolean { return this.playing; }

  public destroy(): void {
    this.pause();
    this.listeners.clear();
    this.playbackEndListeners.clear();
    this.data = null;
  }

  private readonly tick = (now: number): void => {
    if (!this.playing || !this.data) {
      this.animationFrame = null;
      return;
    }
    const elapsed = Math.max(0, now - this.lastWallTime) / 1000;
    this.lastWallTime = now;
    this.time = Math.min(this.lastTime(), this.time + elapsed * this.speed);
    indexAtTime(this.data.time, this.time, this.cursor);
    this.emit();
    if (this.time >= this.lastTime()) {
      this.pause();
      this.onPlaybackEnd?.();
      for (const listener of this.playbackEndListeners) listener();
      return;
    }
    this.animationFrame = requestAnimationFrame(this.tick);
  };

  private emit(): void {
    if (!this.data) return;
    const frame: RuntimeFrame = { time: this.time, index: this.cursor.index, data: this.data };
    this.onFrame?.(frame);
    for (const listener of this.listeners) listener(frame);
  }

  private lastTime(): number {
    return this.data?.time[this.data.length - 1] ?? this.time;
  }

  private clampTime(time: number): number {
    const first = this.data?.time[0] ?? 0;
    return Math.max(first, Math.min(this.lastTime(), Number.isFinite(time) ? time : first));
  }
}
