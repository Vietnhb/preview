import { createPortal } from "react-dom";
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from "react";
import pixiBundle from "../../../../node_modules/pixi.js/dist/webworker.min.js?raw";
import purifierBundle from "../../../../node_modules/dompurify/dist/purify.min.js?raw";
import Icon from "../../../shared/ui/LearningIcon";
import { sampleTimeline, type SolverTimeline, type PixiVisualProgram } from "../model/svgScene";
import { describeScene, formatNumber, formatTime, seriesColor,
  type BackendFieldMeta, type SceneDescriptor, type SceneObservable, type SimulationModelRef } from "../model/sceneModel";
import { PIXI_WORKER, PIXI_BRIDGE } from "../engine/pixiRuntime";
import SimulationCharts from "./SimulationCharts";
import { useWorkspaceTheme } from "../hooks/useWorkspaceTheme";

const RUNTIME = "const sampleTimeline = (" + sampleTimeline.toString() + ");\n" + PIXI_WORKER;
const SPEEDS = [0.25, 0.5, 1, 2];
const formatRate = (value: number) => formatNumber(value >= 100 ? value : Math.round(value * 10) / 10, 3);

export default function SvgPixiScene({ program, timeline, parameters, parameterInfo, verificationStatus, models, fieldMeta, observables, onRenderError, toolbarActions, cover = false, coverPlaying = false, onCoverFailed, readoutsTarget }: Readonly<{
  program: PixiVisualProgram; timeline: SolverTimeline; parameters: Record<string, number>; verificationStatus: string;
  models?: readonly SimulationModelRef[]; fieldMeta?: BackendFieldMeta; onRenderError?: (message: string) => void;
  /** The values the plan asks the learner to watch (simulationSpec.observables). */
  observables?: readonly SceneObservable[];
  /** The plan's parameters (name, label, unit), so the drawing can show a slider's current value. */
  parameterInfo?: readonly { name: string; label?: string; unit?: string }[];
  /** Page-level buttons shown at the end of the toolbar so the page needs no heading row of its own. */
  toolbarActions?: ReactNode;
  /** undefined: inline for standalone previews; null: workspace target not mounted yet. */
  readoutsTarget?: HTMLElement | null;
  /** Card cover: only the scene, no toolbar, playback bar or charts. It rests on the first frame. */
  cover?: boolean;
  /** In cover mode, plays (looping) while true, e.g. while the card is hovered. */
  coverPlaying?: boolean;
  /** In cover mode, called when the authored scene cannot be drawn. */
  onCoverFailed?: () => void;
}>) {
  const theme = useWorkspaceTheme();
  const code = program.code?.trim() ?? "";
  const [failure, setFailure] = useState<{ code: string; message: string } | null>(null);
  const error = failure?.code === code ? failure.message : "";
  const setError = useCallback((message: string) => setFailure({ code, message }), [code]);
  const [ready, setReady] = useState(false);
  const [time, setTime] = useState(0);
  const [playing, setPlaying] = useState(!cover);
  const [speed, setSpeed] = useState(1);
  // The author chooses the time scale; the transport never infers a replay speed from physical quantities.
  const rate = Number.isFinite(program.playbackRate) && program.playbackRate! > 0 ? program.playbackRate! : 1;
  const effectiveRate = rate * speed;
  const rateNote = effectiveRate > 1.5 ? "Nhanh hơn thời gian thực " + formatRate(effectiveRate) + " lần"
    : effectiveRate < 1 / 1.5 ? "Chậm hơn thời gian thực " + formatRate(1 / effectiveRate) + " lần" : "";
  const [loop, setLoop] = useState(cover);
  const iframe = useRef<HTMLIFrameElement>(null);
  const scene = useMemo(() => describeScene(timeline, models ?? [], fieldMeta ?? {}, observables ?? [], parameterInfo ?? []),
    [timeline, models, fieldMeta, observables, parameterInfo]);
  const dataRef = useRef({ timeline, parameters, verificationStatus, fieldMeta, models, observables, parameterInfo });
  const callbackRef = useRef(onRenderError);
  const coverRef = useRef(cover);
  useEffect(() => { callbackRef.current = onRenderError; }, [onRenderError]);
  const nonce = useMemo(() => crypto.randomUUID(), []);
  const limits = useMemo(() => ({
    maxCode: Number(import.meta.env.VITE_SIMULATION_MAX_CODE_CHARACTERS || 250000),
    maxTextureSide: Number(import.meta.env.VITE_SIMULATION_MAX_TEXTURE_SIDE || 4096),
    maxTexturePixels: Number(import.meta.env.VITE_SIMULATION_MAX_TEXTURE_PIXELS || 33554432),
    timeoutMs: Number(import.meta.env.VITE_SIMULATION_RUNTIME_TIMEOUT_MS || 15000),
  }), []);
  const html = useMemo(() => {
    const script = (purifierBundle + "\n" + PIXI_BRIDGE).replace(/<\/script/gi, "<\\/script");
    return '<!doctype html><html><head><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'nonce-'
      + nonce + '\' \'unsafe-eval\'; worker-src blob:; img-src blob: data:; style-src \'unsafe-inline\'; connect-src \'none\'; font-src \'none\';">'
      + '<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent}canvas{display:block;width:100%;height:100%;touch-action:none}</style>'
      + '</head><body><canvas></canvas><script nonce="' + nonce + '">' + script + '</script></body></html>';
  }, [nonce]);
  const send = useCallback((message: Record<string, unknown>) => iframe.current?.contentWindow?.postMessage(
    { channel: "pixi-host", ...message }, "*"), []);

  useEffect(() => {
    dataRef.current = { timeline, parameters, verificationStatus, fieldMeta, models, observables, parameterInfo };
    send({ type: "data", ...dataRef.current });
  }, [timeline, parameters, verificationStatus, fieldMeta, models, observables, parameterInfo, send]);
  useEffect(() => { send({ type: "theme", theme }); }, [theme, send]);
  useEffect(() => {
    const receive = (event: MessageEvent) => {
      if (event.source !== iframe.current?.contentWindow || event.data?.channel !== "pixi-runtime") return;
      if (event.data.type === "error") {
        const message = String(event.data.message);
        setError(message); setReady(false);
        callbackRef.current?.(message);
      }
      if (event.data.type === "ready") setReady(true);
      if (event.data.type === "ended") setPlaying(false);
      if ((event.data.type === "tick" || event.data.type === "ready" || event.data.type === "ended") && Number.isFinite(event.data.t)) {
        // Coalesce worker ticks into one React update per animation frame so a
        // busy main thread never builds a backlog (clock and charts stay in sync).
        latestTime = event.data.t;
        // A cover shows no clock or charts, so it skips the per-frame React update entirely.
        if (coverRef.current) return;
        if (!pending) pending = requestAnimationFrame(() => { pending = 0; setTime(latestTime); });
      }
    };
    let latestTime = 0, pending = 0;
    addEventListener("message", receive);
    return () => { removeEventListener("message", receive); cancelAnimationFrame(pending); };
  }, [setError]);

  const start = () => {
    if (!code) {
      const message = program.scene ? "Cảnh này dùng định dạng StageKit cũ. Hãy yêu cầu AI dựng lại hình minh họa." : "Chưa có mã hình minh họa. Hãy yêu cầu AI dựng cảnh.";
      setError(message); callbackRef.current?.(message); return;
    }
    if (code.length > limits.maxCode) { const message = "Generated code exceeds its resource budget."; setError(message); callbackRef.current?.(message); return; }
    setReady(false); setError("");
    send({ type: "start", code, ...dataRef.current, theme, limits, bundle: pixiBundle, runtime: RUNTIME });
    send({ type: "speed", speed: speed * rate }); send({ type: "loop", loop }); send({ type: "play", playing: cover ? coverPlaying : playing });
  };
  const togglePlay = () => { const next = !playing; setPlaying(next); send({ type: "play", playing: next }); };
  const restart = () => { send({ type: "seek", t: 0 }); setTime(0); setPlaying(true); send({ type: "play", playing: true }); };
  const seek = (value: number) => { send({ type: "seek", t: value }); setTime(value); };
  const coverFailedRef = useRef(onCoverFailed);
  useEffect(() => { coverFailedRef.current = onCoverFailed; }, [onCoverFailed]);
  useEffect(() => { if (cover && error) coverFailedRef.current?.(); }, [cover, error]);
  useEffect(() => {
    if (!cover) return;
    if (!coverPlaying) send({ type: "seek", t: 0 });
    send({ type: "play", playing: coverPlaying });
  }, [cover, coverPlaying, send]);
  const progress = timeline.durationSeconds > 0 ? Math.min(100, time / timeline.durationSeconds * 100) : 0;

  if (cover) return <div className="sim-player sim-player--cover" data-theme={theme === "DARK" ? "dark" : "light"}>
    <div className="sim-stage" data-ready={ready}>
      {!error && <iframe key={code} ref={iframe} className="sim-stage__frame" title={program.description || "Mô phỏng vật lý"}
        sandbox="allow-scripts" referrerPolicy="no-referrer" tabIndex={-1} srcDoc={html} onLoad={start} />}
      {!error && !ready && <div className="sim-stage__loading" role="status"><span className="sim-spinner" /></div>}
    </div>
  </div>;

  const readouts = !error && timeline.frames.length > 0
    ? <SceneReadouts scene={scene} values={sampleTimeline(timeline, time)} theme={theme} /> : null;
  return <div className="sim-player" data-theme={theme === "DARK" ? "dark" : "light"}>
    <div className="sim-player__toolbar">
      <span className={"sim-status sim-status--" + (/^VERIFIED/.test(verificationStatus) ? "ok" : verificationStatus === "PENDING" ? "pending" : "warn")}>
        {/^VERIFIED/.test(verificationStatus) ? "Dữ liệu vật lý đã xác minh" : verificationStatus === "PENDING" ? "Đang tính lại…" : "Chưa xác minh vật lý"}
      </span>
      {toolbarActions && <div className="sim-player__actions">{toolbarActions}</div>}
    </div>
    <div className="sim-stage" data-ready={ready}>
      {!error && <iframe key={code} ref={iframe} className="sim-stage__frame"
        title={program.description || "Mô phỏng vật lý"} sandbox="allow-scripts" referrerPolicy="no-referrer"
        srcDoc={html} onLoad={start} />}
      {error && <p role="alert" className="simulation-error">{error}</p>}
      {!error && !ready && <div className="sim-stage__loading" role="status"><span className="sim-spinner" /> Đang dựng cảnh…</div>}
    </div>
    {readoutsTarget === undefined ? readouts : readoutsTarget ? createPortal(
      <div className="sim-player" data-theme={theme === "DARK" ? "dark" : "light"}>{readouts}</div>, readoutsTarget) : null}
    <div className="sim-transport">
      <button type="button" className="sim-icon-button sim-icon-button--primary" onClick={togglePlay}
        aria-label={playing ? "Tạm dừng" : "Phát"} title={playing ? "Tạm dừng" : "Phát"}>
        <Icon name={playing ? "pause" : "play"} /></button>
      <button type="button" className="sim-icon-button" onClick={restart} aria-label="Phát lại từ đầu" title="Phát lại từ đầu">
        <Icon name="reset" /></button>
      <input className="sim-scrubber" aria-label="Thời gian mô phỏng" type="range" min={0} max={timeline.durationSeconds} step="any"
        value={time} style={{ "--progress": progress + "%" } as CSSProperties} onChange={event => seek(Number(event.target.value))} />
      <output className="sim-clock" aria-label="Thời gian mô phỏng" title="Thời gian của hiện tượng trong mô phỏng">
        {formatTime(time, timeline.durationSeconds)} <span>/ {formatTime(timeline.durationSeconds, timeline.durationSeconds)}</span>
      </output>
      <select className="sim-select" aria-label="Tốc độ phát" value={speed}
        onChange={event => { const value = Number(event.target.value); setSpeed(value); send({ type: "speed", speed: value * rate }); }}>
        {SPEEDS.map(value => <option key={value} value={value}>Phát {value}×</option>)}
      </select>
      <label className="sim-toggle" title="Lặp lại khi hết thời gian">
        <input type="checkbox" checked={loop} onChange={event => { setLoop(event.target.checked); send({ type: "loop", loop: event.target.checked }); }} />
        Lặp
      </label>
      {rateNote && <span className="sim-rate-note">{rateNote}</span>}
    </div>
    <SimulationCharts scene={scene} timeline={timeline} time={time} theme={theme} onSeek={seek} />
  </div>;
}

/** The signed plan selects focused numeric readings; no scene artwork is interpreted here. */
function SceneReadouts({ scene, values, theme }: {
  scene: SceneDescriptor; values: Record<string, number>; theme: "LIGHT" | "DARK";
}) {
  if (!scene.participants.length) return null;
  const focus = scene.observables.map(key => ({ key, label: scene.fields[key].caption ?? scene.fields[key].label }));
  const reading = (key: string) => {
    const meta = scene.fields[key];
    return formatNumber(values[key], 4, Math.max(Math.abs(meta.min), Math.abs(meta.max))) + (meta.unit ? " " + meta.unit : "");
  };
  return <section className="sim-measurements" aria-label="Số liệu mô phỏng">
    {focus.length > 0 && <dl className="sim-focus-readouts">
      {focus.map(item => <div key={item.key}><dt>{item.label}</dt><dd>{reading(item.key)}</dd></div>)}
    </dl>}
    <details className="sim-data-details">
      <summary>Toàn bộ đại lượng và kết quả tính ({Object.keys(scene.fields).length})</summary>
      <dl className="sim-readouts">
        {scene.participants.map(participant => <div className="sim-readouts__row" key={participant.id}>
          <dt><span className="sim-readouts__dot" style={{ background: seriesColor(theme, participant.colorIndex) }} />{participant.label}</dt>
          {Object.values(scene.fields).filter(meta => meta.participantId === participant.id).map(meta =>
            <dd key={meta.key}><span title={meta.label}>{meta.symbol}</span>{reading(meta.key)}</dd>)}
        </div>)}
      </dl>
    </details>
  </section>;
}
