import { useEffect, useMemo, useRef, useState } from "react";
import type { MatterValidation } from "../api/matterFlowApi";
import { validateVisualProgram, type VisualProgram } from "./visualCodeSafety";
import { instrumentVisualProgram } from "./visualWorkBudget";

type Props = {
  program: VisualProgram;
  parameters: Record<string, number>;
  durationSeconds: number;
  onValidation?: (result: MatterValidation) => void;
};

const WIDTH = 960;
const HEIGHT = 540;

// Runs only inside a dedicated worker owned by an opaque-origin sandbox iframe.
const WORKER_RUNTIME = String.raw`
const WIDTH = 960;
const HEIGHT = 540;
const MAX_STATE_BYTES = 256000;
const MAX_COMMANDS = 1000;
let ctx = null;
let canvas = null;
let state = null;
let params = null;
let timer = null;
let frame = 0;
let frameLimit = 0;
let commands = [];
let running = false;
const hexColor = (value) => typeof value === 'string'
  && /^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.test(value)
  ? value : '#d9e9ff';
const finite = (value) => typeof value === 'number' && Number.isFinite(value) && Math.abs(value) <= 1e12;
function add(kind, values) {
  if (commands.length >= MAX_COMMANDS) throw Error('Drawing command budget exceeded.');
  if (!values.every((item) => finite(item))) throw Error('Drawing received a non-finite coordinate.');
  commands.push({ kind, values });
}
const paint = Object.freeze({
  background(color) { add('background', [0, 0]); commands[commands.length - 1].color = hexColor(color); },
  circle(x, y, r, color) { add('circle', [x, y, r]); commands[commands.length - 1].color = hexColor(color); },
  rect(x, y, w, h, color) { add('rect', [x, y, w, h]); commands[commands.length - 1].color = hexColor(color); },
  line(x1, y1, x2, y2, color, strokeWidth = 2) {
    add('line', [x1, y1, x2, y2, strokeWidth]); commands[commands.length - 1].color = hexColor(color);
  },
  arrow(x1, y1, x2, y2, color, strokeWidth = 2) {
    add('arrow', [x1, y1, x2, y2, strokeWidth]); commands[commands.length - 1].color = hexColor(color);
  },
  text(x, y, label, color, fontSize = 16) {
    if (!['string','number','boolean'].includes(typeof label)
      || typeof label === 'string' && label.length > 2000)
      throw Error('Drawing labels must be short text or scalar values.');
    add('text', [x, y, fontSize]);
    const item = commands[commands.length - 1];
    item.label = String(label).slice(0, 200); item.color = hexColor(color);
  },
  strokeCircle(x, y, r, color, strokeWidth = 2) {
    add('strokeCircle', [x, y, r, strokeWidth]); commands[commands.length - 1].color = hexColor(color);
  },
  strokeRect(x, y, w, h, color, strokeWidth = 2) {
    add('strokeRect', [x, y, w, h, strokeWidth]); commands[commands.length - 1].color = hexColor(color);
  },
  roundRect(x, y, w, h, radius, color) {
    add('roundRect', [x, y, w, h, radius]); commands[commands.length - 1].color = hexColor(color);
  },
  arc(x, y, r, startAngle, endAngle, color, strokeWidth = 2) {
    add('arc', [x, y, r, startAngle, endAngle, strokeWidth]); commands[commands.length - 1].color = hexColor(color);
  },
  dashedLine(x1, y1, x2, y2, color, strokeWidth = 2) {
    add('dashedLine', [x1, y1, x2, y2, strokeWidth]); commands[commands.length - 1].color = hexColor(color);
  },
  gradientRect(x, y, w, h, color1, color2, vertical = false) {
    add('gradientRect', [x, y, w, h, vertical ? 1 : 0]);
    const item = commands[commands.length - 1];
    item.color = hexColor(color1); item.color2 = hexColor(color2);
  },
  gradientCircle(x, y, r, colorCenter, colorEdge) {
    add('gradientCircle', [x, y, r]);
    const item = commands[commands.length - 1];
    item.color = hexColor(colorCenter); item.color2 = hexColor(colorEdge);
  },
  polygon(points, color) {
    if (!Array.isArray(points) || points.length < 6 || points.length > 200 || points.length % 2 !== 0)
      throw Error('Polygon needs 3-100 [x,y] pairs as a flat array.');
    if (!points.every((p) => finite(p))) throw Error('Polygon received a non-finite coordinate.');
    if (commands.length >= MAX_COMMANDS) throw Error('Drawing command budget exceeded.');
    commands.push({ kind: 'polygon', values: points.slice(0, 200), color: hexColor(color) });
  },
  defs(xml) {
    if (typeof xml !== 'string' || xml.length > 50000) throw Error('defs expects an SVG string under 50KB.');
    add('defs', [0, 0]);
    commands[commands.length - 1].xml = xml;
  },
  svg(xml, x = 0, y = 0) {
    if (typeof xml !== 'string' || xml.length > 50000) throw Error('svg expects an SVG string under 50KB.');
    add('svg', [x, y]);
    commands[commands.length - 1].xml = xml;
  },
  svgPath(d, fill, stroke, strokeWidth = 2, shadowColor = null, shadowBlur = 0, shadowOffsetY = 0) {
    if (typeof d !== 'string' || d.length > 20000) throw Error('svgPath expects a path string.');
    add('svgPath', [strokeWidth, shadowBlur, shadowOffsetY]);
    const item = commands[commands.length - 1];
    item.d = d;
    item.fill = fill ? hexColor(fill) : 'transparent';
    item.stroke = stroke ? hexColor(stroke) : 'transparent';
    item.shadowColor = shadowColor ? hexColor(shadowColor) : null;
  },
});
function checkState() {
  if (!state || typeof state !== 'object' || Array.isArray(state))
    throw Error('The generated model did not return a state object.');
  let visited = 0;
  const encoded = JSON.stringify(state, (_key, value) => {
    if (++visited > 12000 || Array.isArray(value) && value.length > 2000
      || typeof value === 'string' && value.length > MAX_STATE_BYTES)
      throw Error('The generated state exceeded its data budget.');
    if (['function','symbol','bigint'].includes(typeof value))
      throw Error('The generated state must contain serializable data.');
    if (typeof value === 'number' && !Number.isFinite(value))
      throw Error('The generated model produced a non-finite state.');
    return value;
  });
  if (!encoded || encoded.length > MAX_STATE_BYTES)
    throw Error('The generated state exceeded its memory budget.');
}
function drawFrame() {
  commands = [];
  draw(state, paint, params, WIDTH, HEIGHT);
  const background = commands.find((command) => command.kind === 'background');
  ctx.fillStyle = background?.color || '#101418'; ctx.fillRect(0, 0, WIDTH, HEIGHT);
  let defsXml = '';
  let svgMarkup = '';
  let hasSvg = false;
  for (const command of commands) {
    const v = command.values;
    if (command.kind === 'background') {
      continue;
    }
    if (command.kind === 'defs') {
      defsXml += command.xml;
      hasSvg = true;
      continue;
    }
    if (command.kind === 'svg') {
      const vx = v[0] || 0;
      const vy = v[1] || 0;
      svgMarkup += '<g transform="translate(' + vx + ',' + vy + ')">' + command.xml + '</g>';
      hasSvg = true;
      continue;
    }
    ctx.fillStyle = command.color; ctx.strokeStyle = command.color;
    ctx.globalAlpha = 1.0;
    ctx.setLineDash([]);
    if (command.kind === 'circle') {
      ctx.beginPath(); ctx.arc(v[0], v[1], Math.min(5000, Math.max(0.5, v[2])), 0, Math.PI * 2); ctx.fill();
    } else if (command.kind === 'rect') {
      ctx.fillRect(v[0], v[1], v[2], v[3]);
    } else if (command.kind === 'strokeCircle') {
      ctx.lineWidth = Math.min(16, Math.max(1, v[3]));
      ctx.beginPath(); ctx.arc(v[0], v[1], Math.min(5000, Math.max(0.5, v[2])), 0, Math.PI * 2); ctx.stroke();
    } else if (command.kind === 'strokeRect') {
      ctx.lineWidth = Math.min(16, Math.max(1, v[4]));
      ctx.strokeRect(v[0], v[1], v[2], v[3]);
    } else if (command.kind === 'roundRect') {
      const rx = v[0], ry = v[1], rw = v[2], rh = v[3], rr = Math.min(Math.abs(v[4]), Math.abs(rw)/2, Math.abs(rh)/2);
      ctx.beginPath();
      if (ctx.roundRect) { ctx.roundRect(rx, ry, rw, rh, rr); } else { ctx.rect(rx, ry, rw, rh); }
      ctx.fill();
    } else if (command.kind === 'arc') {
      ctx.lineWidth = Math.min(16, Math.max(1, v[5]));
      ctx.beginPath(); ctx.arc(v[0], v[1], Math.min(5000, Math.max(0.5, v[2])), v[3], v[4]); ctx.stroke();
    } else if (command.kind === 'dashedLine') {
      ctx.lineWidth = Math.min(16, Math.max(1, v[4]));
      ctx.setLineDash([6, 4]);
      ctx.beginPath(); ctx.moveTo(v[0], v[1]); ctx.lineTo(v[2], v[3]); ctx.stroke();
      ctx.setLineDash([]);
    } else if (command.kind === 'gradientRect') {
      const grd = v[4] > 0
        ? ctx.createLinearGradient(v[0], v[1], v[0], v[1] + v[3])
        : ctx.createLinearGradient(v[0], v[1], v[0] + v[2], v[1]);
      grd.addColorStop(0, command.color); grd.addColorStop(1, command.color2);
      ctx.fillStyle = grd; ctx.fillRect(v[0], v[1], v[2], v[3]);
    } else if (command.kind === 'gradientCircle') {
      const r = Math.min(5000, Math.max(0.5, v[2]));
      const grd = ctx.createRadialGradient(v[0], v[1], 0, v[0], v[1], r);
      grd.addColorStop(0, command.color); grd.addColorStop(1, command.color2);
      ctx.fillStyle = grd; ctx.beginPath(); ctx.arc(v[0], v[1], r, 0, Math.PI * 2); ctx.fill();
    } else if (command.kind === 'polygon') {
      ctx.beginPath();
      ctx.moveTo(v[0], v[1]);
      for (let i = 2; i < v.length; i += 2) ctx.lineTo(v[i], v[i + 1]);
      ctx.closePath(); ctx.fill();
    } else if (command.kind === 'line' || command.kind === 'arrow') {
      ctx.lineWidth = Math.min(16, Math.max(1, v[4]));
      ctx.lineCap = 'round';
      ctx.beginPath(); ctx.moveTo(v[0], v[1]); ctx.lineTo(v[2], v[3]); ctx.stroke();
      if (command.kind === 'arrow') {
        const angle = Math.atan2(v[3] - v[1], v[2] - v[0]);
        const headLen = Math.max(8, Math.min(18, v[4] * 4));
        ctx.beginPath(); ctx.moveTo(v[2], v[3]);
        ctx.lineTo(v[2] - headLen * Math.cos(angle - 0.4), v[3] - headLen * Math.sin(angle - 0.4));
        ctx.lineTo(v[2] - headLen * Math.cos(angle + 0.4), v[3] - headLen * Math.sin(angle + 0.4));
        ctx.closePath(); ctx.fill();
      }
      ctx.lineCap = 'butt';
    } else if (command.kind === 'text') {
      const fsize = Math.min(48, Math.max(10, v[2]));
      ctx.font = '600 ' + fsize + 'px "Inter", "SF Pro", system-ui, sans-serif';
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.shadowColor = 'rgba(0,0,0,0.6)'; ctx.shadowBlur = 4;
      ctx.fillText(command.label, v[0], v[1], WIDTH);
      ctx.shadowBlur = 0; ctx.textBaseline = 'alphabetic';
    } else if (command.kind === 'svgPath') {
      const p2d = new Path2D(command.d);
      if (command.shadowColor && v[1] > 0) {
        ctx.save();
        ctx.shadowColor = command.shadowColor;
        ctx.shadowBlur = v[1];
        ctx.shadowOffsetY = v[2];
        if (command.fill && command.fill !== 'transparent') { ctx.fillStyle = command.fill; ctx.fill(p2d); }
        ctx.restore();
      } else if (command.fill && command.fill !== 'transparent') {
        ctx.fillStyle = command.fill; ctx.fill(p2d);
      }
      if (command.stroke && command.stroke !== 'transparent') {
        ctx.strokeStyle = command.stroke;
        ctx.lineWidth = Math.min(16, Math.max(0.5, v[0]));
        ctx.stroke(p2d);
      }
    }
  }
  self.postMessage({ type: 'svg-frame', defs: defsXml, markup: svgMarkup });
}
function fail(error) {
  running = false; if (timer) clearInterval(timer);
  self.postMessage({ type: 'error', message: String(error).slice(0, 250) });
}
async function complete() {
  running = false;
  if (timer) clearInterval(timer);
  timer = null;
  let bitmap = null;
  try {
    bitmap = await createImageBitmap(canvas);
    self.postMessage({ type: 'paused', bitmap }, [bitmap]);
  } catch { bitmap?.close(); self.postMessage({ type: 'paused' }); }
}
self.onmessage = (event) => {
  const message = event.data;
  if (message.type === 'pointer') {
    if (state && typeof state === 'object') state.pointer = {
      x: Math.max(0, Math.min(WIDTH, message.x)), y: Math.max(0, Math.min(HEIGHT, message.y)),
      down: !!message.down,
    };
    return;
  }
  if (message.type !== 'start') return;
  try {
    canvas = message.canvas;
    ctx = canvas.getContext('2d', { alpha: false });
    if (!ctx) throw Error('OffscreenCanvas 2D is unavailable.');
    params = Object.freeze(message.params);
    state = init(params, WIDTH, HEIGHT);
    if (state && typeof state === 'object' && !Array.isArray(state))
      state.pointer = { x: WIDTH / 2, y: HEIGHT / 2, down: false };
    checkState();
    frameLimit = Math.min(2400, Math.max(1, Math.ceil(message.durationSeconds * 60)));
    drawFrame();
    running = true; self.postMessage({ type: 'ready' });
    timer = setInterval(() => {
      if (!running) return;
      try {
        if (frame >= frameLimit) {
          complete(); return;
        }
        const started = performance.now();
        step(state, 1 / 60, params, WIDTH, HEIGHT);
        checkState(); drawFrame(); frame += 1;
        if (performance.now() - started > 40) throw Error('Visual model exceeded the frame CPU budget.');
        if (frame % 60 === 0) self.postMessage({ type: 'heartbeat' });
      } catch (error) { fail(error instanceof Error ? error.message : error); }
    }, 1000 / 60);
  } catch (error) { fail(error instanceof Error ? error.message : error); }
};
`;

function safeJson(value: unknown): string {
  return JSON.stringify(value).replace(/</g, "\\u003c");
}

function sandboxHtml(program: VisualProgram, parameters: Record<string, number>,
  durationSeconds: number, nonce: string) {
  const guarded = instrumentVisualProgram(program);
  const source = `function init(params,width,height) { "use strict";\n${guarded.init}\n}\n`
    + `function step(state,dt,params,width,height) { "use strict";\n${guarded.step}\n}\n`
    + `function draw(state,paint,params,width,height) { "use strict";\n${guarded.draw}\n}\n`
    + WORKER_RUNTIME;
  return `<!doctype html><html><head><meta charset="utf-8">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' blob:; worker-src blob:; connect-src 'none'; style-src 'unsafe-inline'; img-src 'none'; font-src 'none'; media-src 'none'; object-src 'none'; frame-src 'none'; form-action 'none'; base-uri 'none'">
    <style>html,body{width:100%;height:100%;margin:0;overflow:hidden;background:#0b1729}.stage-wrap{position:relative;width:100%;height:100%}canvas,svg{position:absolute;top:0;left:0;width:100%;height:100%;display:block}canvas{touch-action:none;cursor:crosshair;z-index:1}svg{pointer-events:none;z-index:2}</style>
    </head><body><div class="stage-wrap"><canvas width="${WIDTH}" height="${HEIGHT}" aria-label="Interactive physics model"></canvas><svg id="svg-layer" viewBox="0 0 ${WIDTH} ${HEIGHT}" width="${WIDTH}" height="${HEIGHT}" xmlns="http://www.w3.org/2000/svg"><defs id="svg-defs"></defs><g id="svg-stage"></g></svg></div>
    <script>(() => {
      const nonce = ${safeJson(nonce)};
      const source = ${safeJson(source)};
      const params = ${safeJson(parameters)};
      const durationSeconds = ${safeJson(durationSeconds)};
      const canvas = document.querySelector('canvas');
      const send = (type, details = {}) => parent.postMessage({ channel: 'physlive-visual', nonce, type, ...details }, '*');
      if (!canvas.transferControlToOffscreen) { send('error', { message: 'OffscreenCanvas is unavailable.' }); return; }
      let url;
      let worker;
      try {
        url = URL.createObjectURL(new Blob([source], { type: 'text/javascript' }));
        worker = new Worker(url);
      } catch (error) {
        if (url) URL.revokeObjectURL(url);
        send('error', { message: 'Visual worker could not start: ' + String(error).slice(0, 160) });
        return;
      }
      let stopped = false;
      let lastHeartbeat = Date.now();
      const preserveFrame = (bitmap) => {
        if (!bitmap) return false;
        try {
          if (bitmap.width !== ${WIDTH} || bitmap.height !== ${HEIGHT}) return false;
          const frozen = document.createElement('canvas');
          frozen.width = ${WIDTH}; frozen.height = ${HEIGHT};
          frozen.setAttribute('aria-label', canvas.getAttribute('aria-label') || 'Completed simulation');
          const context = frozen.getContext('2d', { alpha: false });
          if (!context) return false;
          context.drawImage(bitmap, 0, 0);
          canvas.replaceWith(frozen);
          return true;
        } catch { return false; }
        finally { bitmap.close(); }
      };
      window.addEventListener('pagehide', () => {
        stopped = true; worker.terminate(); URL.revokeObjectURL(url);
      }, { once: true });
      const defsEl = document.getElementById('svg-defs');
      const stageEl = document.getElementById('svg-stage');
      const sanitizeSvg = (str) => {
        let s = String(str || '');
        const sc = 'scr' + 'ipt';
        while (s.toLowerCase().indexOf('<' + sc) >= 0) s = s.replace(new RegExp('<' + sc, 'gi'), '');
        while (s.toLowerCase().indexOf('</' + sc) >= 0) s = s.replace(new RegExp('</' + sc, 'gi'), '');
        while (s.toLowerCase().indexOf('javascript:') >= 0) s = s.replace(new RegExp('javascript:', 'gi'), '');
        return s;
      };
      worker.onmessage = (event) => {
        const message = event.data;
        if (stopped) { message.bitmap?.close(); return; }
        if (message.type === 'svg-frame') {
          if (defsEl && message.defs !== undefined) defsEl.innerHTML = sanitizeSvg(message.defs);
          if (stageEl && message.markup !== undefined) stageEl.innerHTML = sanitizeSvg(message.markup);
        }
        else if (message.type === 'ready') { lastHeartbeat = Date.now(); send('ready'); }
        else if (message.type === 'heartbeat') lastHeartbeat = Date.now();
        else if (message.type === 'paused') {
          stopped = true;
          if (preserveFrame(message.bitmap)) worker.terminate();
          URL.revokeObjectURL(url);
          send('paused');
        }
        else if (message.type === 'error') {
          stopped = true; send('error', { message: message.message });
          worker.terminate(); URL.revokeObjectURL(url);
        }
      };
      worker.onerror = () => {
        if (!stopped) {
          stopped = true; send('error', { message: 'Visual worker failed.' });
          worker.terminate(); URL.revokeObjectURL(url);
        }
      };
      try {
        const offscreen = canvas.transferControlToOffscreen();
        worker.postMessage({ type: 'start', canvas: offscreen, params, durationSeconds }, [offscreen]);
      } catch (error) {
        stopped = true; worker.terminate(); URL.revokeObjectURL(url);
        send('error', { message: 'Visual canvas could not start: ' + String(error).slice(0, 160) });
        return;
      }
      const pauseForBackground = () => {
        if (document.hidden && !stopped) {
          stopped = true; worker.terminate();
          URL.revokeObjectURL(url);
          send('paused', { reason: 'Simulation paused while the tab was hidden. Restart to continue.' });
        }
      };
      document.addEventListener('visibilitychange', pauseForBackground);
      pauseForBackground();
      setInterval(() => {
        if (!stopped && Date.now() - lastHeartbeat > 5000) {
          stopped = true; worker.terminate(); URL.revokeObjectURL(url);
          send('error', { message: 'Visual model exceeded its CPU time limit.' });
        }
      }, 1000);
      const point = (event) => {
        const box = canvas.getBoundingClientRect();
        return { x: (event.clientX - box.left) * ${WIDTH} / Math.max(1, box.width),
          y: (event.clientY - box.top) * ${HEIGHT} / Math.max(1, box.height) };
      };
      let down = false;
      canvas.addEventListener('pointerdown', (event) => {
        if (stopped) return;
        down = true; canvas.setPointerCapture(event.pointerId);
        worker.postMessage({ type: 'pointer', ...point(event), down });
      });
      canvas.addEventListener('pointermove', (event) => {
        if (!stopped) worker.postMessage({ type: 'pointer', ...point(event), down });
      });
      const release = (event) => {
        down = false;
        if (!stopped) worker.postMessage({ type: 'pointer', ...point(event), down });
      };
      canvas.addEventListener('pointerup', release);
      canvas.addEventListener('pointercancel', release);
    })();</script></body></html>`;
}

export default function VisualSandbox({ program, parameters, durationSeconds, onValidation }: Readonly<Props>) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const callbackRef = useRef(onValidation);
  const [status, setStatus] = useState<"starting" | "ready" | "paused" | "error">("starting");
  const [message, setMessage] = useState("");
  useEffect(() => { callbackRef.current = onValidation; }, [onValidation]);
  const safetyError = useMemo(() => {
    if (!Number.isFinite(durationSeconds) || durationSeconds < 0.1 || durationSeconds > 40
      || Object.values(parameters).some((value) => !Number.isFinite(value) || Math.abs(value) > 1e30))
      return "Visual model controls or duration are invalid.";
    return validateVisualProgram(program, Object.keys(parameters));
  }, [program, parameters, durationSeconds]);
  const nonce = useMemo(() => crypto.randomUUID()
    + JSON.stringify({ program, parameters, durationSeconds }).length,
  [program, parameters, durationSeconds]);
  const html = useMemo(() => safetyError ? "" : sandboxHtml(program, parameters, durationSeconds, nonce),
    [program, parameters, durationSeconds, nonce, safetyError]);

  useEffect(() => {
    if (safetyError) {
      callbackRef.current?.({ status: "FLAGGED",
        flags: ["Generated visual program cannot start: " + safetyError], metrics: {} });
      return;
    }
    let started = false;
    const receive = (event: MessageEvent) => {
      if (event.source !== iframeRef.current?.contentWindow) return;
      const result = event.data;
      if (!result || result.channel !== "physlive-visual" || result.nonce !== nonce) return;
      if (result.type === "ready") {
        started = true; setStatus("ready");
        callbackRef.current?.({ status: "UNVERIFIED", flags: [] });
      } else if (result.type === "paused") {
        started = true; setStatus("paused");
        setMessage(String(result.reason ?? "Simulation reached its time limit. Restart to continue."));
        callbackRef.current?.({ status: "PAUSED", flags: [] });
      } else if (result.type === "error") {
        started = true; setStatus("error"); setMessage(String(result.message ?? "Visual model failed."));
        callbackRef.current?.({ status: "FLAGGED", flags: [String(result.message ?? "Visual model failed.")],
          metrics: {} });
      }
    };
    window.addEventListener("message", receive);
    let waited = 0;
    const check = window.setInterval(() => {
      if (started || document.hidden) return;
      waited += 500;
      if (waited < 5500) return;
      started = true; setStatus("error"); setMessage("Visual model did not start within five seconds.");
      callbackRef.current?.({ status: "FLAGGED", flags: ["Visual model did not start within five seconds."],
        metrics: {} });
    }, 500);
    return () => { window.removeEventListener("message", receive); window.clearInterval(check); };
  }, [nonce, safetyError]);

  if (safetyError) return <div className="matter-sandbox-error" role="alert">{safetyError}</div>;
  return <div className="matter-sandbox" aria-busy={status === "starting"}>
    {status !== "error" && <iframe key={nonce} ref={iframeRef} title="Interactive visual physics model"
      sandbox="allow-scripts" referrerPolicy="no-referrer" srcDoc={html} />}
    {status === "starting" && <div className="matter-sandbox-overlay" role="status">Starting simulationâ€¦</div>}
    {status === "paused" && <div className="matter-sandbox-note" role="status">{message}</div>}
    {status === "error" && <div className="matter-sandbox-error" role="alert">{message}</div>}
  </div>;
}
