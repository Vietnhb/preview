import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from "react";
import pixiBundle from "../../node_modules/pixi.js/dist/webworker.min.js?raw";
import purifierBundle from "../../node_modules/dompurify/dist/purify.min.js?raw";
import Icon from "../components/common/LearningIcon";
import { sampleTimeline, type SolverTimeline, type PixiVisualProgram } from "./svgScene";
import { describeScene, type BackendFieldMeta, type SimulationModelRef } from "./sceneModel";
import { createStageKit } from "./stageKit";
import SimulationCharts from "./SimulationCharts";
import { useWorkspaceTheme } from "./useWorkspaceTheme";

/**
 * Academic reference scene: composed only from the generic stage kit and the
 * semantic scene descriptor. Used when the learner selects it and as the
 * automatic fallback when AI-generated drawing code fails or draws nothing.
 */
export const STANDARD_SCENE_CODE = "async function(PIXI, app, api) { return api.kit.standardScene(); }";

// A terminable worker in an opaque-origin iframe cannot access the host or network.
const WORKER = String.raw`
let app, lifecycle, data, kit, playing = true, loop = false, speed = 1, t = 0, last = performance.now(), nextAsset = 0;
let frames = 0, lastSent = -1;
const assets = new Map(), textureIds = new WeakMap();
const QUIET_KEYS = new Set(['toJSON', 'then', 'asymmetricMatch', 'nodeType', '$$typeof']);
function ranges(timeline) {
  const result = {};
  for (const frame of timeline.frames) for (const [name, value] of Object.entries(frame.values)) {
    const item = result[name] || (result[name] = {min: value, max: value});
    item.min = Math.min(item.min, value); item.max = Math.max(item.max, value);
  }
  return Object.freeze(result);
}
const send = (type, extra = {}) => postMessage({type, ...extra});
// Unknown field keys are the most common reason generated scenes silently draw
// nothing (NaN coordinates). Fail loudly so the host can fall back and report
// actionable diagnostics to the repair request.
function strictFields(values) {
  return new Proxy(Object.freeze(values), {get(target, key) {
    if (typeof key === 'string' && !(key in target) && !QUIET_KEYS.has(key))
      throw Error('Unknown solver field "' + key + '". frame.fields keys are: ' + Object.keys(target).join(', '));
    return target[key];
  }});
}
function frame(dt = 0) {
  return Object.freeze({t, dt, fields: strictFields(sampleTimeline(data.timeline, t)),
    parameters: Object.freeze({...data.parameters}), width: app.screen.width,
    height: app.screen.height, theme: data.theme, verificationStatus: data.verificationStatus, scene: data.scene});
}
function textureFrom(bitmap, resolution) {
  if (resolution > 1 && PIXI.ImageSource) return new PIXI.Texture({source: new PIXI.ImageSource({resource: bitmap, resolution})});
  return PIXI.Texture.from(bitmap);
}
function checkVisible() {
  if (typeof app.stage.getBounds !== 'function') return;
  const b = app.stage.getBounds(), w = app.screen.width, h = app.screen.height;
  const values = [b.minX, b.minY, b.maxX, b.maxY];
  if (values.some(value => !Number.isFinite(value)) && (b.width || b.height))
    throw Error('Generated scene has non-finite coordinates (NaN/Infinity). Map solver fields to finite screen positions.');
  if (!(b.width >= 2 && b.height >= 2) || b.maxX < 0 || b.maxY < 0 || b.minX > w || b.minY > h)
    throw Error('Generated scene draws nothing visible inside the ' + Math.round(w) + 'x' + Math.round(h)
      + ' viewport. Fit all content with the current solver ranges (api.getFieldRanges or api.kit.camera).');
}
async function start(message) {
  data = message;
  data.ranges = ranges(data.timeline);
  app = new PIXI.Application();
  await app.init({canvas: message.canvas, width: message.width, height: message.height, resolution: message.dpr || 1,
    backgroundAlpha: 0, antialias: true, preference: 'webgl', autoStart: false});
  const svgTexture = (svg, options = {}) => {
    const id = ++nextAsset;
    return new Promise((resolve, reject) => {
      assets.set(id, {resolve: texture => { textureIds.set(texture, id); resolve(texture); }, reject});
      send('svg', {id, svg, screen: Boolean(options && options.screen)});
    });
  };
  const releaseTexture = texture => {
    const id = texture && textureIds.get(texture);
    if (id) { textureIds.delete(texture); send('release', {id}); }
    try { texture?.destroy(true); } catch {}
  };
  kit = createStageKit(PIXI, app, {data: () => data, sample: time => sampleTimeline(data.timeline, time),
    svg: svgTexture, release: releaseTexture,
    fail: message => { send('error', {message: String(message)}); close(); }});
  const api = Object.freeze({
    get width() { return app.screen.width; }, get height() { return app.screen.height; },
    get scene() { return data.scene; },
    kit,
    getFrame: () => frame(),
    getFieldRanges: () => data.ranges,
    svgTexture,
    releaseTexture
  });
  const AsyncFunction = Object.getPrototypeOf(async function() {}).constructor;
  const mount = await new AsyncFunction('"use strict"; return (' + message.code + '\n);')();
  if (typeof mount !== 'function') throw Error('PixiJS code must be a mount function.');
  lifecycle = await mount(PIXI, app, api);
  if (!lifecycle || typeof lifecycle.update !== 'function') throw Error('PixiJS code must return update(frame).');
  lifecycle.update(frame(0)); app.render();
  send('ready', {t}); last = performance.now();
  setInterval(() => {
    try {
      const now = performance.now(), dt = Math.min(0.1, (now - last) / 1000) * speed; last = now;
      const duration = data.timeline.durationSeconds;
      if (playing) {
        t += dt;
        if (t >= duration) {
          if (loop) t = 0; else { t = duration; playing = false; send('ended', {t}); }
        }
      }
      lifecycle.update(frame(dt)); app.render(); frames++;
      if (frames === 20) checkVisible();
      if (t !== lastSent && (frames % 2 === 0 || !playing)) { lastSent = t; send('tick', {t}); }
      else if (frames % 30 === 0) send('tick', {t});
    } catch (error) { send('error', {message: String(error && error.message || error)}); close(); }
  }, 1000 / 60);
}
onmessage = async ({data: message}) => {
  try {
    switch (message.type) {
      case 'start': await start(message); break;
      case 'data':
        data = {...data, ...message}; data.ranges = ranges(data.timeline);
        t = Math.min(t, data.timeline.durationSeconds);
        lifecycle?.setData?.(); lifecycle?.resize?.(app.screen.width, app.screen.height); break;
      case 'theme': data.theme = message.theme; lifecycle?.setData?.(); lifecycle?.resize?.(app.screen.width, app.screen.height); break;
      case 'play':
        if (message.playing && t >= data.timeline.durationSeconds) t = 0;
        playing = message.playing; break;
      case 'speed': speed = Math.max(0.05, Math.min(8, Number(message.speed) || 1)); break;
      case 'loop': loop = Boolean(message.loop); break;
      case 'seek': t = Math.max(0, Math.min(data.timeline.durationSeconds, message.t)); break;
      case 'resize': app.renderer.resize(message.width, message.height); lifecycle?.resize?.(message.width, message.height); break;
      case 'pointer': lifecycle?.pointer?.(message.event); break;
      case 'asset': {
        const pending = assets.get(message.id); assets.delete(message.id);
        if (!pending) break;
        if (message.error) pending.reject(Error(message.error));
        else pending.resolve(textureFrom(message.bitmap, message.resolution));
        break;
      }
    }
  } catch (error) { send('error', {message: String(error && error.message || error)}); close(); }
};
`;
const BRIDGE = String.raw`
const canvas = document.querySelector('canvas');
const SVG_NS = 'http://www.w3.org/2000/svg';
let worker, lastHeartbeat = Date.now(), stopped = false, limits, texturePixels = 0;
const assetPixels = new Map();
const send = (type, extra = {}) => parent.postMessage({channel: 'pixi-runtime', type, ...extra}, '*');
function stop(message) { stopped = true; worker?.terminate(); send('error', {message}); }
// Accept complete <svg> documents and bare fragments (<circle/>, <g>…</g>):
// fragments are wrapped and sized from their measured bounding box.
function svgRoot(source) {
  let markup = source.trim().replace(/^<\?xml[^>]*>\s*/i, '').replace(/^<!doctype[^>]*>\s*/i, '');
  // Models often URL-encode colours as if writing a data: URI (fill='%23ff0000').
  markup = markup.replace(/=(["'])([^"']*%[0-9A-Fa-f]{2}[^"']*)\1/g, (whole, quote, value) => {
    try { return '=' + quote + decodeURIComponent(value) + quote; } catch { return whole; }
  });
  if (!/^<svg[\s>]/i.test(markup)) markup = '<svg xmlns="' + SVG_NS + '">' + markup + '</svg>';
  const cleaned = DOMPurify.sanitize(markup, {USE_PROFILES: {svg: true, svgFilters: true},
    FORBID_TAGS: ['script','foreignObject','image','a','style','animate','animateTransform','set'],
    FORBID_ATTR: ['href','xlink:href','style']});
  const root = new DOMParser().parseFromString(cleaned, 'text/html').body.firstElementChild;
  if (!root || root.localName !== 'svg') throw Error('SVG asset must contain drawable SVG elements.');
  for (const node of [root, ...root.querySelectorAll('*')]) for (const attr of [...node.attributes])
    if (/url\s*\(/i.test(attr.value) && !/^url\(#[\w.-]+\)$/.test(attr.value)) throw Error('External SVG resources forbidden.');
  // An unparseable paint silently renders black; use a neutral tone that reads on both themes.
  for (const node of [root, ...root.querySelectorAll('*')]) for (const name of ['fill', 'stroke', 'stop-color', 'flood-color']) {
    const value = (node.getAttribute(name) || '').trim();
    if (value && !/^(none|currentColor|transparent|inherit)$/i.test(value) && !/^url\(#[\w.-]+\)$/.test(value)
      && !CSS.supports('color', value)) node.setAttribute(name, '#64748b');
  }
  const box = (root.getAttribute('viewBox') || '').trim().split(/[ ,]+/).map(Number);
  const hasBox = box.length === 4 && box.every(Number.isFinite) && box[2] > 0 && box[3] > 0;
  const numeric = name => /^[\d.]+(?:px)?$/.test(root.getAttribute(name) || '') ? parseFloat(root.getAttribute(name)) : NaN;
  if (!hasBox && !(numeric('width') > 0 && numeric('height') > 0)) {
    root.style.cssText = 'position:absolute;left:-10000px;top:0;visibility:hidden;overflow:visible';
    document.body.appendChild(root);
    let bbox;
    try { bbox = root.getBBox(); } finally { root.remove(); root.removeAttribute('style'); }
    if (!(bbox && bbox.width > 0 && bbox.height > 0)) throw Error('SVG asset has no drawable area.');
    const pad = Math.max(2, 0.04 * Math.max(bbox.width, bbox.height));
    const vb = [bbox.x - pad, bbox.y - pad, bbox.width + 2 * pad, bbox.height + 2 * pad];
    root.setAttribute('viewBox', vb.join(' '));
    root.setAttribute('width', String(vb[2])); root.setAttribute('height', String(vb[3]));
  }
  if (!root.getAttribute('xmlns')) root.setAttribute('xmlns', SVG_NS);
  return root;
}
async function svgBitmap(source, screen) {
  if (typeof source !== 'string' || source.length > limits.maxCode) throw Error('SVG resource budget exceeded.');
  const root = svgRoot(source);
  const url = URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(root)], {type: 'image/svg+xml'}));
  try {
    const image = new Image(); image.src = url; await image.decode();
    const box = (root.getAttribute('viewBox') || '').trim().split(/[ ,]+/).map(Number);
    const size = (name, index, native) => {
      const attr = root.getAttribute(name) || '';
      return /^[\d.]+(?:px)?$/.test(attr) ? parseFloat(attr)
        : box.length === 4 && box.every(Number.isFinite) ? box[index] : native;
    };
    const width = size('width', 2, image.naturalWidth), height = size('height', 3, image.naturalHeight);
    if (!(width > 0 && height > 0 && width <= limits.maxTextureSide && height <= limits.maxTextureSide))
      throw Error('SVG texture exceeds the resource budget.');
    // Rasterise above the display density so sprites stay crisp when scaled.
    // Sprites: at least ~640 px on the long side so small viewBoxes stay sharp when enlarged.
    // Viewport art (backdrop): exactly the display density.
    const longest = Math.max(width, height), cap = limits.maxTextureSide / longest;
    const resolution = screen ? Math.max(0.5, Math.min(devicePixelRatio || 1, 2, cap))
      : Math.max(1, Math.min(Math.max(devicePixelRatio * 1.5, 2, 640 / longest), 8, cap));
    const surface = document.createElement('canvas');
    surface.width = Math.ceil(width * resolution); surface.height = Math.ceil(height * resolution);
    texturePixels += surface.width * surface.height;
    if (texturePixels > limits.maxTexturePixels) throw Error('SVG texture memory budget exceeded.');
    surface.getContext('2d').drawImage(image, 0, 0, surface.width, surface.height);
    return {bitmap: await createImageBitmap(surface), resolution: surface.width / width, pixels: surface.width * surface.height};
  } finally { URL.revokeObjectURL(url); }
}
addEventListener('message', ({source, data}) => {
  if (source !== parent || data?.channel !== 'pixi-host' || stopped) return;
  if (data.type === 'start') {
    if (worker) return;
    limits = data.limits;
    const url = URL.createObjectURL(new Blob([data.bundle, '\n', data.runtime], {type: 'text/javascript'}));
    worker = new Worker(url); URL.revokeObjectURL(url);
    worker.onmessage = async ({data: result}) => {
      if (stopped) return;
      if (result.type === 'svg') {
        try {
          const {bitmap, resolution, pixels} = await svgBitmap(result.svg, result.screen);
          assetPixels.set(result.id, pixels);
          if (stopped) { bitmap.close(); return; }
          worker.postMessage({type: 'asset', id: result.id, bitmap, resolution}, [bitmap]);
        } catch (error) { worker.postMessage({type: 'asset', id: result.id, error: String(error.message)}); }
      } else if (result.type === 'release') {
        texturePixels = Math.max(0, texturePixels - (assetPixels.get(result.id) || 0)); assetPixels.delete(result.id);
      } else if (['tick','ready','ended'].includes(result.type)) {
        lastHeartbeat = Date.now(); send(result.type, {t: result.t});
      } else if (result.type === 'error') stop(String(result.message).slice(0,1000));
    };
    worker.onerror = event => stop(event.message || 'PixiJS worker failed.');
    lastHeartbeat = Date.now();
    const offscreen = canvas.transferControlToOffscreen();
    worker.postMessage({...data, canvas: offscreen, width: innerWidth, height: innerHeight, dpr: Math.min(3, devicePixelRatio || 1),
      bundle: undefined, runtime: undefined}, [offscreen]);
  } else worker?.postMessage(data);
});
addEventListener('resize', () => worker?.postMessage({type:'resize',width:innerWidth,height:innerHeight}));
for (const type of ['pointerdown','pointermove','pointerup','pointercancel']) canvas.addEventListener(type, event => {
  if (type === 'pointerdown') canvas.setPointerCapture(event.pointerId);
  worker?.postMessage({type:'pointer',event:{type,x:event.offsetX,y:event.offsetY,buttons:event.buttons}});
});
setInterval(() => {
  if (worker && !stopped && Date.now() - lastHeartbeat > limits.timeoutMs) stop('Generated rendering code exceeded its runtime budget.');
},1000);
addEventListener('pagehide', () => worker?.terminate());
`;

const RUNTIME = "const sampleTimeline = (" + sampleTimeline.toString() + ");\n"
  + "const createStageKit = (" + createStageKit.toString() + ");\n" + WORKER;
const SPEEDS = [0.25, 0.5, 1, 2];

type ViewMode = "ai" | "standard";

export default function SvgPixiScene({ program, timeline, parameters, verificationStatus, models, fieldMeta, onRenderError }: Readonly<{
  program: PixiVisualProgram; timeline: SolverTimeline; parameters: Record<string, number>; verificationStatus: string;
  models?: readonly SimulationModelRef[]; fieldMeta?: BackendFieldMeta; onRenderError?: (message: string) => void;
}>) {
  const theme = useWorkspaceTheme();
  const hasAiCode = Boolean(program.code?.trim());
  const [mode, setMode] = useState<ViewMode>(hasAiCode ? "ai" : "standard");
  const [aiError, setAiError] = useState("");
  const [error, setError] = useState("");
  const [ready, setReady] = useState(false);
  const [time, setTime] = useState(0);
  const [playing, setPlaying] = useState(true);
  const [speed, setSpeed] = useState(1);
  const [loop, setLoop] = useState(false);
  const [run, setRun] = useState(0);
  const iframe = useRef<HTMLIFrameElement>(null);
  const scene = useMemo(() => describeScene(timeline, models ?? [], fieldMeta ?? {}), [timeline, models, fieldMeta]);
  const dataRef = useRef({ timeline, parameters, verificationStatus, scene });
  const callbackRef = useRef(onRenderError);
  const modeRef = useRef(mode);
  useEffect(() => { callbackRef.current = onRenderError; }, [onRenderError]);
  useEffect(() => { modeRef.current = mode; }, [mode]);
  const nonce = useMemo(() => crypto.randomUUID(), []);
  const limits = useMemo(() => ({
    maxCode: Number(import.meta.env.VITE_SIMULATION_MAX_CODE_CHARACTERS || 250000),
    maxTextureSide: Number(import.meta.env.VITE_SIMULATION_MAX_TEXTURE_SIDE || 4096),
    maxTexturePixels: Number(import.meta.env.VITE_SIMULATION_MAX_TEXTURE_PIXELS || 33554432),
    timeoutMs: Number(import.meta.env.VITE_SIMULATION_RUNTIME_TIMEOUT_MS || 15000),
  }), []);
  const html = useMemo(() => {
    const script = (purifierBundle + "\n" + BRIDGE).replace(/<\/script/gi, "<\\/script");
    return '<!doctype html><html><head><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'nonce-'
      + nonce + '\' \'unsafe-eval\'; worker-src blob:; img-src blob: data:; style-src \'unsafe-inline\'; connect-src \'none\'; font-src \'none\';">'
      + '<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent}canvas{display:block;width:100%;height:100%;touch-action:none}</style>'
      + '</head><body><canvas></canvas><script nonce="' + nonce + '">' + script + '</script></body></html>';
  }, [nonce]);
  const send = useCallback((message: Record<string, unknown>) => iframe.current?.contentWindow?.postMessage(
    { channel: "pixi-host", ...message }, "*"), []);
  const code = mode === "ai" && hasAiCode ? program.code : STANDARD_SCENE_CODE;

  useEffect(() => {
    dataRef.current = { timeline, parameters, verificationStatus, scene };
    send({ type: "data", ...dataRef.current });
  }, [timeline, parameters, verificationStatus, scene, send]);
  useEffect(() => { send({ type: "theme", theme }); }, [theme, send]);
  useEffect(() => {
    const receive = (event: MessageEvent) => {
      if (event.source !== iframe.current?.contentWindow || event.data?.channel !== "pixi-runtime") return;
      if (event.data.type === "error") {
        const message = String(event.data.message);
        if (modeRef.current === "ai") {
          // Keep the lesson usable: show the academic reference scene and let the
          // teacher request an AI repair with the precise diagnostic.
          setAiError(message); setMode("standard"); setReady(false);
          callbackRef.current?.(message);
        } else setError(message);
      }
      if (event.data.type === "ready") setReady(true);
      if (event.data.type === "ended") setPlaying(false);
      if ((event.data.type === "tick" || event.data.type === "ready" || event.data.type === "ended") && Number.isFinite(event.data.t)) {
        // Coalesce worker ticks into one React update per animation frame so a
        // busy main thread never builds a backlog (clock and charts stay in sync).
        latestTime = event.data.t;
        if (!pending) pending = requestAnimationFrame(() => { pending = 0; setTime(latestTime); });
      }
    };
    let latestTime = 0, pending = 0;
    addEventListener("message", receive);
    return () => { removeEventListener("message", receive); cancelAnimationFrame(pending); };
  }, []);

  const start = () => {
    if (!code.trim() || code.length > limits.maxCode) { setError("Generated code exceeds its resource budget."); return; }
    setReady(false); setError("");
    send({ type: "start", code, ...dataRef.current, theme, limits, bundle: pixiBundle, runtime: RUNTIME });
    send({ type: "speed", speed }); send({ type: "loop", loop }); send({ type: "play", playing });
  };
  const togglePlay = () => { const next = !playing; setPlaying(next); send({ type: "play", playing: next }); };
  const restart = () => { send({ type: "seek", t: 0 }); setTime(0); setPlaying(true); send({ type: "play", playing: true }); };
  const seek = (value: number) => { send({ type: "seek", t: value }); setTime(value); };
  const selectMode = (next: ViewMode) => {
    if (next === mode) return;
    if (next === "ai") setAiError("");
    setMode(next); setRun(value => value + 1);
  };
  const progress = timeline.durationSeconds > 0 ? Math.min(100, time / timeline.durationSeconds * 100) : 0;

  return <div className="sim-player" data-theme={theme === "DARK" ? "dark" : "light"}>
    <div className="sim-player__toolbar">
      <div className="sim-segmented" role="tablist" aria-label="Kiểu hiển thị">
        <button type="button" role="tab" aria-selected={mode === "ai"} disabled={!hasAiCode} onClick={() => selectMode("ai")}
          title={hasAiCode ? "Cảnh minh họa theo ngữ cảnh do AI thiết kế" : "Chưa có cảnh do AI sinh"}>Minh họa AI</button>
        <button type="button" role="tab" aria-selected={mode === "standard"} onClick={() => selectMode("standard")}
          title="Hệ trục đo, quỹ đạo, vectơ và ảnh hoạt nghiệm dựng từ dữ liệu solver">Chuẩn học thuật</button>
      </div>
      <span className={"sim-status sim-status--" + (/^VERIFIED/.test(verificationStatus) ? "ok" : verificationStatus === "PENDING" ? "pending" : "warn")}>
        {/^VERIFIED/.test(verificationStatus) ? "Đã xác minh vật lý" : verificationStatus === "PENDING" ? "Đang tính lại…" : "Chưa xác minh vật lý"}
      </span>
    </div>
    {aiError && mode === "standard" && <div className="sim-notice" role="status">
      <strong>Cảnh AI chưa hiển thị được</strong> — đang dùng cảnh chuẩn học thuật dựng từ dữ liệu backend.
      <details><summary>Chi tiết lỗi</summary><code>{aiError}</code></details>
    </div>}
    <div className="sim-stage">
      {!error && <iframe key={mode + ":" + run + ":" + program.code} ref={iframe} className="sim-stage__frame"
        title={program.description || "Mô phỏng vật lý"} sandbox="allow-scripts" referrerPolicy="no-referrer"
        srcDoc={html} onLoad={start} />}
      {error && <p role="alert" className="simulation-error">{error}</p>}
      {!error && !ready && <div className="sim-stage__loading" role="status"><span className="sim-spinner" /> Đang dựng cảnh…</div>}
    </div>
    <div className="sim-transport">
      <button type="button" className="sim-icon-button sim-icon-button--primary" onClick={togglePlay}
        aria-label={playing ? "Tạm dừng" : "Phát"} title={playing ? "Tạm dừng" : "Phát"}>
        <Icon name={playing ? "pause" : "play"} /></button>
      <button type="button" className="sim-icon-button" onClick={restart} aria-label="Phát lại từ đầu" title="Phát lại từ đầu">
        <Icon name="reset" /></button>
      <input className="sim-scrubber" aria-label="Thời gian mô phỏng" type="range" min={0} max={timeline.durationSeconds} step="any"
        value={time} style={{ "--progress": progress + "%" } as CSSProperties} onChange={event => seek(Number(event.target.value))} />
      <output className="sim-clock">{time.toFixed(2)} <span>/ {timeline.durationSeconds.toFixed(2)} s</span></output>
      <select className="sim-select" aria-label="Tốc độ phát" value={speed}
        onChange={event => { const value = Number(event.target.value); setSpeed(value); send({ type: "speed", speed: value }); }}>
        {SPEEDS.map(value => <option key={value} value={value}>{value}×</option>)}
      </select>
      <label className="sim-toggle" title="Lặp lại khi hết thời gian">
        <input type="checkbox" checked={loop} onChange={event => { setLoop(event.target.checked); send({ type: "loop", loop: event.target.checked }); }} />
        Lặp
      </label>
    </div>
    <SimulationCharts scene={scene} timeline={timeline} time={time} theme={theme} onSeek={seek} />
  </div>;
}
