/** LLM-authored PixiJS lifecycle. `scene` is retained only to diagnose older saved programs. */
export type PixiVisualProgram = { code: string; description?: string; playbackRate?: number; scene?: Record<string, unknown> | null };
export type SolverTimeline = { durationSeconds: number; frames: Array<{ t: number; values: Record<string, number> }>;
  /** For a plan made of stages: when each participant ran, in seconds (null when it did not start or did not end). */
  phases?: Record<string, { start: number | null; end: number | null }> };

export function sampleTimeline(timeline: SolverTimeline, time: number): Record<string, number> {
  const frames = timeline.frames;
  if (!frames.length) throw new Error("Dữ liệu diễn tiến mô phỏng đang trống.");
  const t = Math.max(0, Math.min(timeline.durationSeconds, time));
  let lo = 0, hi = frames.length - 1;
  while (lo + 1 < hi) {
    const mid = (lo + hi) >>> 1;
    if (frames[mid].t <= t) lo = mid; else hi = mid;
  }
  const a = frames[lo], b = frames[hi];
  const ratio = b.t === a.t ? 0 : Math.max(0, Math.min(1, (t - a.t) / (b.t - a.t)));
  const result: Record<string, number> = { t };
  for (const [key, value] of Object.entries(a.values)) {
    if (!Number.isFinite(value) || !Number.isFinite(b.values[key])) throw new Error("Dữ liệu mẫu của bộ giải không hợp lệ.");
    if (key !== "t") result[key] = value + (b.values[key] - value) * ratio;
  }
  return result;
}
