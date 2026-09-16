import { useEffect, useMemo, useRef, useState } from "react";
import { numberLabel, type LearningSeries } from "../../utils/learningModel";

export default function LearningChart({ series, times, index, onSeek }: Readonly<{ series: LearningSeries; times: number[]; index: number; onSeek: (time: number) => void }>) {
  const host = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(720);
  useEffect(() => {
    if (!host.current) return;
    const observer = new ResizeObserver(entries => setWidth(Math.max(240, entries[0].contentRect.width)));
    observer.observe(host.current);
    return () => observer.disconnect();
  }, []);
  const plot = useMemo(() => {
    const low = Math.min(...series.data), high = Math.max(...series.data);
    const pad = Math.max((high - low) * 0.15, Math.abs(high) * 0.08, 0.01);
    const min = low - pad, max = high + pad;
    const start = times[0], duration = (times.at(-1) ?? start) - start || 1;
    const left = 62, right = width - 18, top = 18, bottom = 126;
    const x = (t: number) => left + (t - start) / duration * (right - left);
    const y = (value: number) => bottom - (value - min) / (max - min) * (bottom - top);
    const stride = Math.max(1, Math.ceil(times.length / 900));
    const points = series.data.flatMap((value, i) => i % stride === 0 || i === times.length - 1 ? [`${x(times[i])},${y(value)}`] : []).join(" ");
    return { x, y, left, right, top, bottom, min, max, points, start, duration };
  }, [series, times, width]);
  return <div className="learn-chart" ref={host}>
    <svg viewBox={`0 0 ${width} 160`} aria-label={`Đồ thị ${series.label.toLowerCase()} theo thời gian. ${numberLabel(series.data[index], 4)} ${series.unit} tại ${numberLabel(times[index])} giây.`}
      onPointerDown={event => {
        const rect = event.currentTarget.getBoundingClientRect();
        const x = (event.clientX - rect.left) / rect.width * width;
        const ratio = Math.max(0, Math.min(1, (x - plot.left) / (plot.right - plot.left)));
        onSeek(plot.start + ratio * plot.duration);
      }}>
      <title>{series.label} ({series.unit}) theo thời gian (s)</title>
      {[0, 0.5, 1].map(ratio => {
        const value = plot.min + (plot.max - plot.min) * ratio, y = plot.y(value);
        return <g key={ratio}><line className="learn-chart-grid" x1={plot.left} x2={plot.right} y1={y} y2={y} /><text x={plot.left - 10} y={y + 4} textAnchor="end">{numberLabel(value, Math.abs(value) < 0.01 ? 4 : 1)}</text></g>;
      })}
      {[0, 0.25, 0.5, 0.75, 1].map(ratio => <text key={ratio} x={plot.x(plot.start + ratio * plot.duration)} y="148" textAnchor="middle">{numberLabel(plot.start + ratio * plot.duration, 1)} s</text>)}
      <polyline points={plot.points} fill="none" stroke={series.color} strokeWidth="2.5" strokeLinejoin="round" />
      <line className="learn-chart-cursor" x1={plot.x(times[index])} x2={plot.x(times[index])} y1={plot.top} y2={plot.bottom} />
      <circle cx={plot.x(times[index])} cy={plot.y(series.data[index])} r="5" fill={series.color} stroke="white" strokeWidth="2" />
    </svg>
  </div>;
}
