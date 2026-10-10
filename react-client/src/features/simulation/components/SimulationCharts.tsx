import { useEffect, useMemo, useRef, useState, type PointerEvent } from "react";
import { sampleTimeline, type SolverTimeline } from "../model/svgScene";
import { formatNumber, niceStep, seriesColor, type FieldMeta, type SceneDescriptor, type ThemeName } from "../model/sceneModel";

type Series = { key: string; name: string; color: string; dashed: boolean; meta: FieldMeta; points: Array<[number, number]> };
type Group = { id: string; title: string; axis: string; unit: string; series: Series[] };

/**
 * One chart per unit, so values that can be compared share an axis. The plan's watched values are charted when it
 * names any, otherwise every result; nothing is grouped or ordered by what a quantity is called.
 */
function buildGroups(scene: SceneDescriptor, timeline: SolverTimeline, theme: ThemeName): Group[] {
  const groups = new Map<string, Group>();
  const colorOf = new Map(scene.participants.map(item => [item.id, item.colorIndex]));
  const labelOf = new Map(scene.participants.map(item => [item.id, item.label]));
  const every = Math.max(1, Math.floor(timeline.frames.length / 480));
  const pointsOf = (meta: FieldMeta) => {
    const points: Array<[number, number]> = [], frames = timeline.frames;
    for (let i = 0; i < frames.length; i += every) points.push([frames[i].t, frames[i].values[meta.key]]);
    const lastFrame = frames[frames.length - 1];
    if (lastFrame && points[points.length - 1]?.[0] !== lastFrame.t) points.push([lastFrame.t, lastFrame.values[meta.key]]);
    return points;
  };
  const watched = scene.observables.length > 0;
  const metas = watched ? scene.observables.map(key => scene.fields[key]) : Object.values(scene.fields);
  const several = scene.participants.length > 1;
  metas.forEach((meta, index) => {
    const id = "unit:" + meta.unit;
    const group = groups.get(id) ?? { id, title: "", axis: "", unit: meta.unit, series: [] };
    groups.set(id, group);
    const who = labelOf.get(meta.participantId) ?? meta.participantId;
    group.series.push({
      key: meta.key, meta, points: pointsOf(meta),
      /* a participant's second line on the same chart is dashed; watched values each take their own colour */
      dashed: !watched && group.series.some(item => item.meta.participantId === meta.participantId),
      color: seriesColor(theme, watched ? index : colorOf.get(meta.participantId) ?? 0),
      name: meta.caption ?? (several ? meta.label + " · " + who : meta.label),
    });
  });
  for (const group of groups.values()) {
    const labels = [...new Set(group.series.map(series => series.meta.caption ?? series.meta.label))];
    const symbols = [...new Set(group.series.map(series => series.meta.symbol))];
    group.title = (labels.length > 2 ? labels.slice(0, 2).join(", ") + " +" + (labels.length - 2) : labels.join(", ")) + " – thời gian";
    group.axis = symbols.length === 1 && symbols[0] !== labels[0] ? symbols[0] : "";
  }
  return [...groups.values()];
}

function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(640);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    const observer = new ResizeObserver(entries => setWidth(Math.max(280, entries[0].contentRect.width)));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  return [ref, width] as const;
}

function Chart({ group, duration, time, onSeek }: Readonly<{ group: Group; duration: number; time: number; onSeek: (t: number) => void }>) {
  const [ref, width] = useWidth<HTMLDivElement>();
  const height = 230, m = { l: 56, r: 18, t: 14, b: 34 };
  const plotW = width - m.l - m.r, plotH = height - m.t - m.b;
  const layout = useMemo(() => {
    let min = 0, max = 0;
    for (const series of group.series) for (const [, v] of series.points) if (Number.isFinite(v)) { min = Math.min(min, v); max = Math.max(max, v); }
    /* flat or tiny-valued series (10⁻²⁹ kg, 10⁻¹⁹ C): pad relative to the data, not by ±1 */
    const magnitude = Math.max(Math.abs(min), Math.abs(max));
    if (!(max - min > magnitude * 1e-9)) { const d = magnitude > 0 ? magnitude * 0.5 : 1; max += d; min -= d; }
    const pad = (max - min) * 0.08;
    min -= min < 0 ? pad : 0; max += pad;
    const yStep = niceStep(max - min, 5), xStep = niceStep(duration, Math.max(3, Math.round(plotW / 90)));
    const x = (t: number) => m.l + (duration > 0 ? t / duration : 0) * plotW;
    const y = (v: number) => m.t + plotH - (v - min) / (max - min) * plotH;
    const yTicks: number[] = [], xTicks: number[] = [];
    for (let v = Math.ceil(min / yStep) * yStep; v <= max + yStep * 1e-9; v += yStep) yTicks.push(Number(v.toPrecision(12)));
    for (let t = 0; t <= duration + 1e-9; t += xStep) xTicks.push(Number(t.toPrecision(12)));
    const paths = group.series.map(series => series.points
      .map(([t, v], i) => (i ? "L" : "M") + x(t).toFixed(1) + " " + y(v).toFixed(1)).join(""));
    return { x, y, yTicks, xTicks, paths, yStep, xStep };
  }, [group, duration, plotW, plotH, m.l, m.t]);
  const seekFrom = (event: PointerEvent<SVGSVGElement>) => {
    const box = event.currentTarget.getBoundingClientRect();
    const t = (event.clientX - box.left - m.l) / plotW * duration;
    onSeek(Math.max(0, Math.min(duration, t)));
  };
  const cursorX = layout.x(Math.min(time, duration));
  return <div ref={ref} className="sim-chart">
    <svg width={width} height={height} role="img" aria-label={group.title}
      onPointerDown={event => { event.currentTarget.setPointerCapture(event.pointerId); seekFrom(event); }}
      onPointerMove={event => { if (event.buttons) seekFrom(event); }}>
      {layout.yTicks.map(v => <g key={"y" + v}>
        <line x1={m.l} x2={m.l + plotW} y1={layout.y(v)} y2={layout.y(v)} className={Math.abs(v) < layout.yStep * 1e-6 ? "sim-chart__zero" : "sim-chart__grid"} />
        <text x={m.l - 8} y={layout.y(v)} className="sim-chart__tick" textAnchor="end" dominantBaseline="middle">{formatNumber(v, 4, layout.yStep)}</text>
      </g>)}
      {layout.xTicks.map(t => <g key={"x" + t}>
        <line x1={layout.x(t)} x2={layout.x(t)} y1={m.t} y2={m.t + plotH} className="sim-chart__grid" />
        <text x={layout.x(t)} y={m.t + plotH + 16} className="sim-chart__tick" textAnchor="middle">{formatNumber(t, 4, layout.xStep)}</text>
      </g>)}
      <line x1={m.l} x2={m.l} y1={m.t} y2={m.t + plotH} className="sim-chart__axis" />
      <line x1={m.l} x2={m.l + plotW} y1={m.t + plotH} y2={m.t + plotH} className="sim-chart__axis" />
      <text x={m.l + plotW} y={height - 3} className="sim-chart__label" textAnchor="end">t (s)</text>
      <text x={12} y={m.t + plotH / 2} className="sim-chart__label" textAnchor="middle"
        transform={`rotate(-90 12 ${m.t + plotH / 2})`}>{group.axis && group.unit ? group.axis + " (" + group.unit + ")" : group.axis || group.unit}</text>
      {group.series.map((series, index) => <path key={series.key} d={layout.paths[index]} fill="none" stroke={series.color}
        strokeWidth={2.2} strokeDasharray={series.dashed ? "7 5" : undefined} strokeLinejoin="round" strokeLinecap="round" />)}
      <line x1={cursorX} x2={cursorX} y1={m.t} y2={m.t + plotH} className="sim-chart__cursor" />
      {group.series.map(series => {
        const value = interpolate(series.points, time);
        return Number.isFinite(value) && <circle key={series.key} cx={cursorX} cy={layout.y(value)} r={4.2}
          fill={series.color} className="sim-chart__dot" />;
      })}
    </svg>
  </div>;
}

function interpolate(points: Array<[number, number]>, t: number) {
  if (!points.length) return NaN;
  if (t <= points[0][0]) return points[0][1];
  for (let i = 1; i < points.length; i++) if (points[i][0] >= t) {
    const [t0, v0] = points[i - 1], [t1, v1] = points[i];
    return t1 === t0 ? v1 : v0 + (v1 - v0) * (t - t0) / (t1 - t0);
  }
  return points[points.length - 1][1];
}

export default function SimulationCharts({ scene, timeline, time, theme, onSeek }: Readonly<{
  scene: SceneDescriptor; timeline: SolverTimeline; time: number; theme: ThemeName; onSeek: (t: number) => void;
}>) {
  const groups = useMemo(() => buildGroups(scene, timeline, theme), [scene, timeline, theme]);
  const [tab, setTab] = useState<string>("");
  const active = groups.find(group => group.id === tab) ?? (tab === "table" ? null : groups[0]);
  const values = useMemo(() => {
    try { return sampleTimeline(timeline, time); } catch { return {} as Record<string, number>; }
  }, [timeline, time]);
  if (!groups.length) return null;
  const labelOf = new Map(scene.participants.map(item => [item.id, item.label]));
  return <section className="sim-analysis" aria-label="Đồ thị và số liệu">
    <div className="sim-analysis__tabs" role="tablist">
      {groups.map(group => <button key={group.id} type="button" role="tab" aria-selected={active?.id === group.id}
        onClick={() => setTab(group.id)}>{group.title.split(" – ")[0]}</button>)}
      <button type="button" role="tab" aria-selected={!active} onClick={() => setTab("table")}>Bảng số liệu</button>
    </div>
    {active ? <>
      <div className="sim-legend">
        {active.series.map(series => {
          return <span key={series.key} className="sim-legend__item">
            <i style={{ background: series.color }} data-dashed={series.dashed || undefined} />
            {series.name}<b>{formatNumber(values[series.key], 4, Math.max(Math.abs(series.meta.min), Math.abs(series.meta.max)))} {series.meta.unit}</b>
          </span>;
        })}
      </div>
      <Chart group={active} duration={timeline.durationSeconds} time={time} onSeek={onSeek} />
      <p className="sim-analysis__hint">Đồ thị {active.title.toLowerCase()} từ dữ liệu solver backend · kéo trên đồ thị để tua thời gian.</p>
    </> : <div className="sim-table-wrap">
      <table className="sim-table">
        <thead><tr><th>Đối tượng</th><th>Đại lượng</th><th>Tại t = {time.toFixed(2)} s</th><th>Nhỏ nhất</th><th>Lớn nhất</th></tr></thead>
        <tbody>{Object.values(scene.fields).map(meta => {
          const scale = Math.max(Math.abs(meta.min), Math.abs(meta.max));
          return <tr key={meta.key}>
            <td>{meta.object ?? labelOf.get(meta.participantId) ?? meta.participantId}</td>
            <td>{meta.caption ?? meta.label}{!meta.caption && meta.symbol.toLowerCase() !== meta.label.toLowerCase() && <> <span className="sim-muted">({meta.symbol})</span></>}</td>
            <td className="sim-num">{formatNumber(values[meta.key], 4, scale)} {meta.unit}</td>
            <td className="sim-num">{formatNumber(meta.min, 4, scale)} {meta.unit}</td>
            <td className="sim-num">{formatNumber(meta.max, 4, scale)} {meta.unit}</td>
          </tr>;
        })}</tbody>
      </table>
    </div>}
  </section>;
}