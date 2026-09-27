import { useEffect, useMemo, useRef, useState } from "react";
import pixiBundle from "../../node_modules/pixi.js/dist/webworker.min.js?raw";
import purifierBundle from "../../node_modules/dompurify/dist/purify.min.js?raw";
import { sampleTimeline, type SolverTimeline, type PixiVisualProgram } from "./svgScene";

// No scene construction here: generated code owns all PixiJS/SVG presentation.
// A terminable worker in an opaque-origin iframe cannot access the host or network.
const WORKER = String.raw`
let app, lifecycle, data, playing = true, t = 0, last = performance.now(), nextAsset = 0;
const assets = new Map();
function ranges(timeline) {
  const result = {};
  for (const frame of timeline.frames) for (const [name, value] of Object.entries(frame.values)) {
    const item = result[name] ||= {min: value, max: value};
    item.min = Math.min(item.min, value); item.max = Math.max(item.max, value);
  }
  return Object.freeze(result);
}
const send = (type, extra = {}) => postMessage({type, ...extra});
function frame(dt = 0) {
  return Object.freeze({t, dt, fields: Object.freeze(sampleTimeline(data.timeline, t)),
    parameters: Object.freeze({...data.parameters}), width: app.screen.width,
    height: app.screen.height, theme: data.theme, verificationStatus: data.verificationStatus});
}
async function start(message) {
  data = message;
  data.ranges = ranges(data.timeline);
  app = new PIXI.Application();
  await app.init({canvas: message.canvas, width: message.width, height: message.height,
    backgroundAlpha: 0, antialias: true, preference: 'webgl', autoStart: false});
  const api = Object.freeze({
    get width() { return app.screen.width; }, get height() { return app.screen.height; },
    getFrame: () => frame(),
    getFieldRanges: () => data.ranges,
    svgTexture(svg) {
      const id = ++nextAsset;
      return new Promise((resolve, reject) => { assets.set(id, {resolve, reject}); send('svg', {id, svg}); });
    }
  });
  const AsyncFunction = Object.getPrototypeOf(async function() {}).constructor;
  const mount = await new AsyncFunction('"use strict"; return (' + message.code + '\n);')();
  if (typeof mount !== 'function') throw Error('PixiJS code must be a mount function.');
  lifecycle = await mount(PIXI, app, api);
  if (!lifecycle || typeof lifecycle.update !== 'function') throw Error('PixiJS code must return update(frame).');
  send('ready'); last = performance.now();
  setInterval(() => {
    try {
      const now = performance.now(), dt = Math.min(0.1, (now - last) / 1000); last = now;
      if (playing) t = Math.min(data.timeline.durationSeconds, t + dt);
      lifecycle.update(frame(dt)); app.render(); send('tick', {t});
    } catch (error) { send('error', {message: String(error.message)}); close(); }
  }, 1000 / 60);
}
onmessage = async ({data: message}) => {
  try {
    switch (message.type) {
      case 'start': await start(message); break;
      case 'data':
        data = {...data, ...message}; data.ranges = ranges(data.timeline); t = 0;
        lifecycle?.resize?.(app.screen.width, app.screen.height); break;
      case 'theme': data.theme = message.theme; break;
      case 'play': playing = message.playing; break;
      case 'seek': t = Math.max(0, Math.min(data.timeline.durationSeconds, message.t)); break;
      case 'resize': app.renderer.resize(message.width, message.height); lifecycle?.resize?.(message.width, message.height); break;
      case 'pointer': lifecycle?.pointer?.(message.event); break;
      case 'asset': {
        const pending = assets.get(message.id); assets.delete(message.id);
        if (!pending) break;
        if (message.error) pending.reject(Error(message.error));
        else pending.resolve(PIXI.Texture.from(message.bitmap));
        break;
      }
    }
  } catch (error) { send('error', {message: String(error.message)}); close(); }
};
`;
const BRIDGE = String.raw`
const canvas = document.querySelector('canvas');
let worker, lastHeartbeat = Date.now(), stopped = false, limits, texturePixels = 0;
const send = (type, extra = {}) => parent.postMessage({channel: 'pixi-runtime', type, ...extra}, '*');
function stop(message) { stopped = true; worker?.terminate(); send('error', {message}); }
async function svgBitmap(source) {
  if (typeof source !== 'string' || source.length > limits.maxCode) throw Error('SVG resource budget exceeded.');
  const cleaned = DOMPurify.sanitize(source, {USE_PROFILES: {svg: true, svgFilters: true},
    FORBID_TAGS: ['script','foreignObject','image','a','style','animate','animateTransform','set'],
    FORBID_ATTR: ['href','xlink:href','style']});
  const root = new DOMParser().parseFromString(cleaned, 'text/html').body.firstElementChild;
  if (!root || root.localName !== 'svg') throw Error('SVG asset requires an svg root.');
  for (const node of [root, ...root.querySelectorAll('*')]) for (const attr of [...node.attributes])
    if (/url\s*\(/i.test(attr.value) && !/^url\(#[\w.-]+\)$/.test(attr.value)) throw Error('External SVG resources forbidden.');
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
    texturePixels += Math.ceil(width) * Math.ceil(height);
    if (texturePixels > limits.maxTexturePixels) throw Error('SVG texture memory budget exceeded.');
    const surface = document.createElement('canvas'); surface.width = Math.ceil(width); surface.height = Math.ceil(height);
    surface.getContext('2d').drawImage(image, 0, 0, surface.width, surface.height);
    return await createImageBitmap(surface);
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
          const bitmap = await svgBitmap(result.svg);
          if (stopped) { bitmap.close(); return; }
          worker.postMessage({type: 'asset', id: result.id, bitmap}, [bitmap]);
        } catch (error) { worker.postMessage({type: 'asset', id: result.id, error: String(error.message)}); }
      } else if (['tick','ready'].includes(result.type)) {
        lastHeartbeat = Date.now(); send(result.type, {t: result.t});
      } else if (result.type === 'error') stop(String(result.message).slice(0,1000));
    };
    worker.onerror = event => stop(event.message || 'PixiJS worker failed.');
    lastHeartbeat = Date.now();
    const offscreen = canvas.transferControlToOffscreen();
    worker.postMessage({...data, canvas: offscreen, width: innerWidth, height: innerHeight,
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

export default function SvgPixiScene({ program, timeline, parameters, verificationStatus, onRenderError }: Readonly<{
  program: PixiVisualProgram; timeline: SolverTimeline; parameters: Record<string, number>; verificationStatus: string;
  onRenderError?: (message: string) => void;
}>) {
  const iframe = useRef<HTMLIFrameElement>(null);
  const dataRef = useRef({ timeline, parameters, verificationStatus });
  const callbackRef = useRef(onRenderError);
  useEffect(() => { callbackRef.current = onRenderError; }, [onRenderError]);
  const [error, setError] = useState("");
  const [ready, setReady] = useState(false);
  const [time, setTime] = useState(0);
  const [playing, setPlaying] = useState(true);
  const nonce = useMemo(() => crypto.randomUUID(), []);
  const limits = useMemo(() => ({
    maxCode: Number(import.meta.env.VITE_SIMULATION_MAX_CODE_CHARACTERS || 250000),
    maxTextureSide: Number(import.meta.env.VITE_SIMULATION_MAX_TEXTURE_SIDE || 4096),
    maxTexturePixels: Number(import.meta.env.VITE_SIMULATION_MAX_TEXTURE_PIXELS || 16777216),
    timeoutMs: Number(import.meta.env.VITE_SIMULATION_RUNTIME_TIMEOUT_MS || 15000),
  }), []);
  const html = useMemo(() => {
    const script = (purifierBundle + "\n" + BRIDGE).replace(/<\/script/gi, "<\\/script");
    return '<!doctype html><html><head><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'nonce-'
      + nonce + '\' \'unsafe-eval\'; worker-src blob:; img-src blob: data:; style-src \'unsafe-inline\'; connect-src \'none\'; font-src \'none\';">'
      + '<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent}canvas{display:block;width:100%;height:100%;touch-action:none}</style>'
      + '</head><body><canvas></canvas><script nonce="' + nonce + '">' + script + '</script></body></html>';
  }, [nonce]);
  const theme = () => document.documentElement.classList.contains("dark")
    || document.documentElement.dataset.theme === "dark" ? "DARK" : "LIGHT";
  const send = (message: Record<string, unknown>) => iframe.current?.contentWindow?.postMessage(
    { channel: "pixi-host", ...message }, "*");
  useEffect(() => {
    dataRef.current = { timeline, parameters, verificationStatus };
    send({ type: "data", ...dataRef.current, theme: theme() });
  }, [timeline, parameters, verificationStatus]);
  useEffect(() => {
    const observer = new MutationObserver(() => send({ type: "theme", theme: theme() }));
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class", "data-theme"] });
    return () => observer.disconnect();
  }, []);
  useEffect(() => {
    const receive = (event: MessageEvent) => {
      if (event.source !== iframe.current?.contentWindow || event.data?.channel !== "pixi-runtime") return;
      if (event.data.type === "error") {
        const message = String(event.data.message); setError(message); callbackRef.current?.(message);
      }
      if (event.data.type === "ready") setReady(true);
      if (event.data.type === "tick" && Number.isFinite(event.data.t)) setTime(event.data.t);
    };
    addEventListener("message", receive);
    return () => removeEventListener("message", receive);
  }, []);
  return <div className="simulation-svg-player">
    {!error && <iframe key={program.code} ref={iframe} title={program.description || "Mô phỏng PixiJS do AI sinh"}
      sandbox="allow-scripts" referrerPolicy="no-referrer" srcDoc={html}
      style={{ width: "100%", height: "min(60vh, 560px)", border: 0 }} onLoad={() => {
        if (!program.code.trim() || program.code.length > limits.maxCode) { setError("Generated code exceeds its resource budget."); return; }
        send({ type: "start", code: program.code, ...dataRef.current, theme: theme(), limits, bundle: pixiBundle,
          runtime: "const sampleTimeline = (" + sampleTimeline.toString() + ");\n" + WORKER });
      }} />}
    {error ? <p role="alert" className="simulation-error">{error}</p>
      : !ready && <p role="status">Đang chạy code PixiJS do AI sinh…</p>}
    <div className="simulation-playback">
      <button type="button" onClick={() => { const next = !playing; setPlaying(next); send({ type: "play", playing: next }); }}>
        {playing ? "Tạm dừng" : "Tiếp tục"}</button>
      <button type="button" onClick={() => send({ type: "seek", t: 0 })}>Chạy lại</button>
      <input aria-label="Thời gian mô phỏng" type="range" min={0} max={timeline.durationSeconds} step="any"
        value={time} onChange={event => send({ type: "seek", t: Number(event.target.value) })} />
      <output>{time.toFixed(2)} / {timeline.durationSeconds} s</output>
    </div>
  </div>;
}
