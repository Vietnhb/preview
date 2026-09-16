export type CanvasQuality = "low" | "medium" | "high";

const DPR_BY_QUALITY: Record<CanvasQuality, number> = { low: 1, medium: 1.25, high: 1.5 };

export function canvasDpr(quality: CanvasQuality = "medium"): number {
  return Math.min(globalThis.devicePixelRatio || 1, DPR_BY_QUALITY[quality]);
}

