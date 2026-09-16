import type { NumericSeries, Timeline } from "./SimulationData";

export type TimelineCursor = { index: number };

export function indexAtTime(times: Timeline | NumericSeries | number[], time: number, cursor?: TimelineCursor): number {
  const last = times.length - 1;
  if (last <= 0 || time <= times[0]) {
    if (cursor) cursor.index = 0;
    return 0;
  }
  if (time >= times[last]) {
    if (cursor) cursor.index = last;
    return last;
  }

  let index = cursor?.index ?? 0;
  index = Math.max(0, Math.min(last - 1, index));
  if (times[index] > time) {
    while (index > 0 && times[index] > time) index--;
  } else {
    while (index < last - 1 && times[index + 1] <= time) index++;
  }
  if (cursor) cursor.index = index;
  return index;
}

export function interpolateAtTime(
  times: Timeline | NumericSeries | number[],
  values: NumericSeries | number[],
  time: number,
  index = indexAtTime(times, time),
): number {
  if (values.length === 0) return 0;
  if (times.length < 2 || time <= times[0]) return values[0] ?? 0;
  const last = Math.min(times.length, values.length) - 1;
  if (last <= 0 || time >= times[last]) return values[last] ?? values[values.length - 1] ?? 0;
  const i = Math.max(0, Math.min(last - 1, index));
  const t0 = times[i] ?? 0;
  const t1 = times[i + 1] ?? t0;
  const ratio = t1 - t0 <= Number.EPSILON ? 0 : (time - t0) / (t1 - t0);
  const start = values[i] ?? 0;
  return start + ((values[i + 1] ?? start) - start) * ratio;
}
