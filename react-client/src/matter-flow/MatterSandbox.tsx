import { useEffect, useMemo, useRef, useState } from "react";
import matterBundle from "matter-js/build/matter.min.js?raw";
import validationSource from "./validation-core.mjs?raw";
import type { MatterValidation } from "../api/matterFlowApi";
import { validateMatterCode } from "./codeSafety";

type SandboxProps = {
  code: string;
  parameters: Record<string, number>;
  simulationSpec?: { externalForces?: boolean; friction?: boolean; conservativeInteractions?: boolean;
    durationSeconds?: number; expectedContacts?: Array<[string, string] | { bodyA: string; bodyB: string }> };
  onValidation: (result: MatterValidation) => void;
};

const WIDTH = 960;
const HEIGHT = 540;

// This source runs only in dedicated workers created by an opaque-origin,
// sandboxed iframe. Generated code is inserted as the setup function body.
const WORKER_RUNTIME = String.raw`
const MAX_BODIES = 120;
const MAX_CONSTRAINTS = 150;
const MAX_FRAMES = 2400;
const WORLD_WIDTH = 960;
const WORLD_HEIGHT = 540;
let engine = null;
let canvas = null;
let ctx = null;
let timer = null;
let frameCount = 0;
let frameLimit = MAX_FRAMES;
let dragging = null;
let dragOffset = { x: 0, y: 0 };
let camera = { zoom: 1, centerX: WORLD_WIDTH / 2, centerY: WORLD_HEIGHT / 2 };

function project(point) {
  return { x: WORLD_WIDTH / 2 + (point.x - camera.centerX) * camera.zoom,
    y: WORLD_HEIGHT / 2 + (point.y - camera.centerY) * camera.zoom };
}
function configureCamera(current) {
  const bodies = Matter.Composite.allBodies(current.world);
  if (!bodies.length) return;
  let minX = Math.min(...bodies.map((body) => body.bounds.min.x));
  let maxX = Math.max(...bodies.map((body) => body.bounds.max.x));
  let minY = Math.min(...bodies.map((body) => body.bounds.min.y));
  let maxY = Math.max(...bodies.map((body) => body.bounds.max.y));
  for (const constraint of Matter.Composite.allConstraints(current.world)) {
    for (const point of [
      constraint.bodyA && constraint.pointA
        ? Matter.Vector.add(constraint.bodyA.position, constraint.pointA) : constraint.pointA,
      constraint.bodyB && constraint.pointB
        ? Matter.Vector.add(constraint.bodyB.position, constraint.pointB) : constraint.pointB,
    ]) {
      if (!point) continue;
      minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
      minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
    }
  }
  const spanX = maxX - minX;
  const spanY = maxY - minY;
  if (![spanX, spanY].every(Number.isFinite)
    || Math.max(spanX / WORLD_WIDTH, spanY / WORLD_HEIGHT) >= 0.18) return;
  const largestBody = Math.max(...bodies.map((body) => Math.max(
    body.bounds.max.x - body.bounds.min.x, body.bounds.max.y - body.bounds.min.y)));
  const zoom = Math.min(1000, 160 / Math.max(largestBody, 0.01),
    (WORLD_WIDTH - 160) / Math.max(spanX, 0.01),
    (WORLD_HEIGHT - 160) / Math.max(spanY, 0.01));
  if (zoom <= 1.3) return;
  camera = { zoom, centerX: (minX + maxX) / 2, centerY: (minY + maxY) / 2 };
}

function send(type, extra) { self.postMessage(Object.assign({ type }, extra || {})); }
async function complete() {
  if (timer) clearInterval(timer);
  timer = null;
  let bitmap = null;
  try {
    bitmap = await createImageBitmap(canvas);
    self.postMessage({ type: 'paused', bitmap }, [bitmap]);
  } catch { bitmap?.close(); send('paused'); }
}
function fail(message) {
  if (timer) clearInterval(timer);
  send('error', { message: String(message).slice(0, 250) });
}
function finiteState(current) {
  const bodies = Matter.Composite.allBodies(current.world);
  if (bodies.length > MAX_BODIES || Matter.Composite.allConstraints(current.world).length > MAX_CONSTRAINTS)
    throw Error('Simulation exceeds the body or constraint limit.');
  for (const body of bodies) {
    if (![body.position.x, body.position.y, body.velocity.x, body.velocity.y, body.angle].every(Number.isFinite))
      throw Error('Simulation produced a non-finite state.');
  }
  return bodies;
}
function createEngine(params) {
  if (!params || Object.values(params).some((value) => !Number.isFinite(value) || Math.abs(value) > 1000000))
    throw Error('Simulation parameters are outside the local runtime limit.');
  const created = setup(Matter, Object.freeze(params), WORLD_WIDTH, WORLD_HEIGHT);
  if (!created || !created.world || !created.gravity) throw Error('Generated code did not return a Matter.Engine.');
  finiteState(created);
  return created;
}
function render() {
  const bodies = finiteState(engine);
  ctx.clearRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
  ctx.fillStyle = '#0b1525';
  ctx.fillRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
  ctx.strokeStyle = 'rgba(166, 198, 235, 0.08)';
  ctx.lineWidth = 1;
  for (let x = 0; x < WORLD_WIDTH; x += 48) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, WORLD_HEIGHT); ctx.stroke(); }
  for (let y = 0; y < WORLD_HEIGHT; y += 48) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(WORLD_WIDTH, y); ctx.stroke(); }
  for (const constraint of Matter.Composite.allConstraints(engine.world)) {
    if (!constraint.bodyA && !constraint.pointA || !constraint.bodyB && !constraint.pointB) continue;
    const a = constraint.bodyA
      ? Matter.Vector.add(constraint.bodyA.position, constraint.pointA || { x: 0, y: 0 }) : constraint.pointA;
    const b = constraint.bodyB
      ? Matter.Vector.add(constraint.bodyB.position, constraint.pointB || { x: 0, y: 0 }) : constraint.pointB;
    if (!a || !b) continue;
    const start = project(a);
    const end = project(b);
    ctx.beginPath(); ctx.moveTo(start.x, start.y); ctx.lineTo(end.x, end.y);
    ctx.strokeStyle = '#95b4da'; ctx.lineWidth = 2; ctx.stroke();
  }
  const labels = [];
  const labelCounts = new Map();
  for (const body of bodies) labelCounts.set(body.label, (labelCounts.get(body.label) || 0) + 1);
  bodies.forEach((body, index) => {
    if (body.render.visible === false) return;
    const vertices = body.vertices;
    if (!vertices || vertices.length === 0) return;
    const defaultFill = body.isStatic ? '#14151f' : '#f5d259';
    const color = body.render.fillStyle && body.render.fillStyle !== defaultFill
      ? body.render.fillStyle
      : body.isStatic ? '#647995' : ['#63b7ff', '#ffad73', '#85daba', '#d3a0f5'][index % 4];
    ctx.save();
    ctx.globalAlpha = Math.max(0, Math.min(1, body.render.opacity ?? 1));
    const first = project(vertices[0]);
    const pos = project(body.position);

    // Modern universal procedural rendering for all physics bodies
    ctx.shadowColor = body.isStatic ? 'rgba(0, 0, 0, 0.25)' : 'rgba(0, 0, 0, 0.4)';
    ctx.shadowBlur = body.isStatic ? 6 : 10;
    ctx.shadowOffsetY = body.isStatic ? 2 : 4;

    // Draw the actual physical boundary polygon
    ctx.beginPath();
    ctx.moveTo(first.x, first.y);
    for (let i = 1; i < vertices.length; i += 1) {
      const point = project(vertices[i]);
      ctx.lineTo(point.x, point.y);
    }
    ctx.closePath();

    // Universal aesthetic gradient tailored to the object's palette
    const grad = ctx.createLinearGradient(pos.x - 20, pos.y - 20, pos.x + 20, pos.y + 20);
    grad.addColorStop(0, color);
    grad.addColorStop(1, body.isStatic ? '#2d3b4e' : '#1e293b');
    ctx.fillStyle = grad;
    ctx.strokeStyle = body.isStatic ? '#7d95b3' : '#dbeafe';
    ctx.lineWidth = 1.5;
    ctx.fill();
    ctx.stroke();

    // For circular rolling bodies, draw an orientation mark so rotation is clearly visible
    if (body.circleRadius && body.circleRadius > 0) {
      const r = body.circleRadius * camera.zoom;
      const angle = body.angle;
      ctx.beginPath();
      ctx.moveTo(pos.x, pos.y);
      ctx.lineTo(pos.x + Math.cos(angle) * r, pos.y + Math.sin(angle) * r);
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.65)';
      ctx.lineWidth = 1.5;
      ctx.stroke();
    }
    ctx.restore();
    if (!body.isStatic) {
      const speed = Math.hypot(body.velocity.x, body.velocity.y);
      if (speed * camera.zoom > 0.12) {
        const dx = body.velocity.x / speed;
        const dy = body.velocity.y / speed;
        const length = Math.min(48, Math.max(16, speed * camera.zoom * 8));
        const origin = project(body.position);
        const startX = origin.x;
        const startY = origin.y;
        const tipX = startX + dx * length;
        const tipY = startY + dy * length;
        ctx.beginPath(); ctx.moveTo(startX, startY); ctx.lineTo(tipX, tipY);
        ctx.moveTo(tipX, tipY);
        ctx.lineTo(tipX - dx * 8 - dy * 5, tipY - dy * 8 + dx * 5);
        ctx.moveTo(tipX, tipY);
        ctx.lineTo(tipX - dx * 8 + dy * 5, tipY - dy * 8 - dx * 5);
        ctx.strokeStyle = '#f8fafc'; ctx.lineWidth = 2; ctx.stroke();
      }
    }
    if (body.label && body.label !== 'Body') {
      const position = project(body.position);
      ctx.fillStyle = '#eef6ff'; ctx.font = '600 14px system-ui';
      const identity = body.plugin?.physliveId || String(index + 1);
      const text = labelCounts.get(body.label) > 1
        ? String(body.label).slice(0, 18) + ' · ' + identity : String(body.label).slice(0, 32);
      const halfWidth = Math.min(WORLD_WIDTH / 2 - 8, ctx.measureText(text).width / 2 + 5);
      const labelX = Math.max(halfWidth + 8, Math.min(WORLD_WIDTH - halfWidth - 8, position.x));
      let labelY = Math.max(20, project({ x: body.position.x, y: body.bounds.min.y }).y - 9);
      for (let attempt = 0; attempt < bodies.length; attempt += 1) {
        if (!labels.some(box => Math.abs(box.x - labelX) < box.halfWidth + halfWidth
          && Math.abs(box.y - labelY) < 18)) break;
        labelY = labelY >= 38 ? labelY - 18 : labelY + 18 * (attempt + 1);
      }
      labels.push({ x: labelX, y: labelY, halfWidth });
      ctx.textAlign = 'center';
      ctx.shadowColor = '#071324'; ctx.shadowBlur = 5;
      ctx.fillText(text, labelX, labelY);
      ctx.shadowBlur = 0;
    }
  });
  if (camera.zoom > 1.3) {
    ctx.fillStyle = '#b7cee8'; ctx.font = '12px system-ui'; ctx.textAlign = 'right';
    ctx.fillText('View zoom ×' + camera.zoom.toFixed(1), WORLD_WIDTH - 16, 24);
  }
}
function startMain(message) {
  canvas = message.canvas;
  ctx = canvas.getContext('2d', { alpha: false });
  if (!ctx) throw Error('OffscreenCanvas 2D is unavailable.');
  const requestedSeconds = message.spec?.durationSeconds;
  frameLimit = Number.isFinite(requestedSeconds)
    ? Math.min(MAX_FRAMES, Math.max(1, Math.ceil(requestedSeconds * 60))) : MAX_FRAMES;
  engine = createEngine(message.params);
  configureCamera(engine);
  render();
  send('ready');
  timer = setInterval(() => {
    try {
      if (frameCount >= frameLimit) { complete(); return; }
      const began = performance.now();
      Matter.Engine.update(engine, 1000 / 60);
      render();
      frameCount += 1;
      if (performance.now() - began > 40) throw Error('Simulation exceeded the per-frame CPU budget.');
      if (frameCount % 60 === 0) send('heartbeat');
    } catch (error) { fail(error instanceof Error ? error.message : error); }
  }, 1000 / 60);
}
function handleDrag(message) {
  if (!engine) return;
  const point = { x: camera.centerX + (message.x - WORLD_WIDTH / 2) / camera.zoom,
    y: camera.centerY + (message.y - WORLD_HEIGHT / 2) / camera.zoom };
  if (message.type === 'drag-start') {
    const found = Matter.Query.point(Matter.Composite.allBodies(engine.world), point)
      .find((body) => !body.isStatic);
    dragging = found || null;
    if (dragging) dragOffset = { x: dragging.position.x - point.x, y: dragging.position.y - point.y };
  } else if (message.type === 'drag-move' && dragging) {
    Matter.Body.setPosition(dragging, { x: point.x + dragOffset.x, y: point.y + dragOffset.y });
    Matter.Body.setVelocity(dragging, { x: 0, y: 0 });
  } else if (message.type === 'drag-end') dragging = null;
}
function validate(message) {
  const result = validateEngineResolution(Matter, createEngine, message.params, message.spec, WORLD_WIDTH, WORLD_HEIGHT);
  send('validation', { result });
}
self.onmessage = (event) => {
  const message = event.data;
  try {
    if (message.type === 'start') startMain(message);
    else if (message.type === 'validate') validate(message);
    else handleDrag(message);
  } catch (error) { fail(error instanceof Error ? error.message : error); }
};
`;

function safeJson(value: unknown): string {
  return JSON.stringify(value).replace(/</g, "\\u003c");
}

function sandboxHtml(code: string, params: Record<string, number>, spec: SandboxProps["simulationSpec"], nonce: string) {
  const validationCore = validationSource.replace("export function validateEngineResolution", "function validateEngineResolution");
  const workerSource = `${matterBundle}\nfunction setup(Matter, params, width, height) {\n${code}\n}\n${validationCore}\n${WORKER_RUNTIME}`;
  return `<!doctype html><html><head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' blob:; worker-src blob:; connect-src 'none'; style-src 'unsafe-inline'; img-src 'none'; font-src 'none'; media-src 'none'; object-src 'none'; frame-src 'none'; form-action 'none'; base-uri 'none'">
    <style>html,body{width:100%;height:100%;margin:0;overflow:hidden;background:#0b1525}canvas{display:block;width:100%;height:100%;touch-action:none;cursor:grab}canvas:active{cursor:grabbing}</style>
    </head><body><canvas width="${WIDTH}" height="${HEIGHT}" aria-label="Matter.js simulation"></canvas>
    <script>
      (() => {
        const nonce = ${safeJson(nonce)};
        const params = ${safeJson(params)};
        const spec = ${safeJson(spec ?? {})};
        const source = ${safeJson(workerSource)};
        const canvas = document.querySelector('canvas');
        const send = (type, details = {}) => parent.postMessage({ channel: 'physlive-matter', nonce, type, ...details }, '*');
        if (!canvas.transferControlToOffscreen) { send('error', { message: 'OffscreenCanvas is unavailable in this browser.' }); return; }
        let url;
        let mainWorker;
        let validationWorker;
        let lastHeartbeat = Date.now();
        let ready = false;
        let stopped = false;
        let validationDone = false;
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
          stopped = true; validationDone = true;
          mainWorker?.terminate(); validationWorker?.terminate();
          if (url) URL.revokeObjectURL(url);
        }, { once: true });
        const flagRuntimeFailure = (message) => {
          if (!validationDone) {
            validationDone = true;
            validationWorker?.terminate();
            validationWorker = null;
          }
          send('validation', { result: { status: 'FLAGGED', flags: [message], metrics: {} } });
        };
        try {
          url = URL.createObjectURL(new Blob([source], { type: 'text/javascript' }));
          mainWorker = new Worker(url);
          mainWorker.onmessage = (event) => {
            const message = event.data;
            if (stopped) { message.bitmap?.close(); return; }
            if (message.type === 'ready') {
              ready = true; lastHeartbeat = Date.now(); send('ready');
              validationWorker = new Worker(url);
              validationWorker.onmessage = (validationEvent) => {
                if (validationDone) return;
                const result = validationEvent.data;
                if (result.type === 'validation') { validationDone = true; send('validation', { result: result.result }); validationWorker.terminate(); }
                else if (result.type === 'error') { validationDone = true; send('validation', { result: { status: 'FLAGGED', flags: ['Validation could not complete: ' + result.message], metrics: {} } }); validationWorker.terminate(); }
              };
              validationWorker.onerror = () => {
                if (validationDone) return;
                validationDone = true;
                send('validation', { result: { status: 'FLAGGED', flags: ['Validation worker failed.'], metrics: {} } });
                validationWorker.terminate();
              };
              validationWorker.postMessage({ type: 'validate', params, spec });
              setTimeout(() => {
                if (!validationDone && validationWorker) {
                  validationDone = true;
                  validationWorker.terminate(); validationWorker = null;
                  send('validation', { result: { status: 'FLAGGED', flags: ['Validation exceeded its CPU time limit.'], metrics: {} } });
                }
              }, 8000);
            } else if (message.type === 'heartbeat') lastHeartbeat = Date.now();
            else if (message.type === 'paused') {
              stopped = true;
              if (preserveFrame(message.bitmap)) mainWorker.terminate();
              send('paused');
            }
            else if (message.type === 'error') {
              stopped = true;
              flagRuntimeFailure('Simulation stopped: ' + message.message);
              send('error', { message: message.message });
              mainWorker.terminate();
            }
          };
          mainWorker.onerror = () => {
            if (stopped) return;
            stopped = true;
            flagRuntimeFailure('Simulation worker failed.');
            send('error', { message: 'Simulation worker failed.' });
            mainWorker.terminate();
          };
          const offscreen = canvas.transferControlToOffscreen();
          mainWorker.postMessage({ type: 'start', canvas: offscreen, params, spec }, [offscreen]);
        } catch (error) {
          stopped = true;
          const message = String(error).slice(0, 250);
          flagRuntimeFailure('Simulation could not start: ' + message);
          send('error', { message });
          mainWorker?.terminate();
          return;
        }
        // Stop work when the document is hidden. Browser throttling can delay
        // heartbeat timers, so resetting the deadline on hide leaves a stuck
        // worker consuming CPU with no effective watchdog.
        const pauseForBackground = () => {
          if (!document.hidden) return;
          if (!validationDone) {
            validationDone = true;
            validationWorker?.terminate(); validationWorker = null;
            send('validation', { result: { status: 'PAUSED',
              flags: ['Background check was interrupted while this tab was hidden. Restart to verify.'], metrics: {} } });
          }
          if (!stopped) {
            stopped = true;
            mainWorker.terminate();
            send('paused', { reason: 'Simulation paused while the tab was hidden. Restart to continue.' });
          }
        };
        document.addEventListener('visibilitychange', pauseForBackground);
        pauseForBackground();
        setInterval(() => {
          if (!stopped && Date.now() - lastHeartbeat > 5000) {
            stopped = true;
            mainWorker.terminate();
            flagRuntimeFailure('Simulation exceeded its CPU time limit.');
            send('error', { message: 'Simulation exceeded its CPU time limit.' });
          }
        }, 1000);
        const point = (event) => {
          const bounds = canvas.getBoundingClientRect();
          return { x: (event.clientX - bounds.left) * ${WIDTH} / bounds.width,
            y: (event.clientY - bounds.top) * ${HEIGHT} / bounds.height };
        };
        canvas.addEventListener('pointerdown', (event) => {
          if (stopped) return;
          canvas.setPointerCapture(event.pointerId);
          mainWorker.postMessage({ type: 'drag-start', ...point(event) });
        });
        canvas.addEventListener('pointermove', (event) => {
          if (!stopped && event.buttons) mainWorker.postMessage({ type: 'drag-move', ...point(event) });
        });
        const endDrag = () => { if (!stopped) mainWorker.postMessage({ type: 'drag-end' }); };
        canvas.addEventListener('pointerup', endDrag);
        canvas.addEventListener('pointercancel', endDrag);
      })();
    </script></body></html>`;
}

export default function MatterSandbox({ code, parameters, simulationSpec, onValidation }: Readonly<SandboxProps>) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const callbackRef = useRef(onValidation);
  const [status, setStatus] = useState<"starting" | "ready" | "paused" | "error">("starting");
  const [error, setError] = useState("");
  const [pausedReason, setPausedReason] = useState("Simulation reached its time limit. Adjust a parameter to restart.");
  useEffect(() => { callbackRef.current = onValidation; }, [onValidation]);
  const safetyError = useMemo(() => validateMatterCode(code, Object.keys(parameters)), [code, parameters]);
  const nonce = useMemo(() => crypto.randomUUID() + JSON.stringify({ code, parameters, simulationSpec }).length,
    [code, parameters, simulationSpec]);
  const html = useMemo(() => safetyError ? "" : sandboxHtml(code, parameters, simulationSpec, nonce),
    [code, parameters, simulationSpec, nonce, safetyError]);

  useEffect(() => {
    if (safetyError) return;
    let started = false;
    const handleMessage = (event: MessageEvent) => {
      if (event.source !== iframeRef.current?.contentWindow) return;
      const message = event.data;
      if (!message || message.channel !== "physlive-matter" || message.nonce !== nonce) return;
      if (message.type === "ready") { started = true; setStatus("ready"); }
      else if (message.type === "paused") {
        started = true; setPausedReason(String(message.reason ?? "Simulation reached its time limit. Adjust a parameter to restart."));
        setStatus("paused");
      }
      else if (message.type === "error") {
        started = true;
        setStatus("error"); setError(String(message.message ?? "Simulation failed."));
      }
      else if (message.type === "validation") callbackRef.current(message.result as MatterValidation);
    };
    window.addEventListener("message", handleMessage);
    let visibleWaitMs = 0;
    const startupCheck = window.setInterval(() => {
      if (started || document.hidden) return;
      visibleWaitMs += 500;
      if (visibleWaitMs < 5500) return;
      started = true;
      const message = "Simulation did not start within five seconds.";
      setStatus("error"); setError(message);
      callbackRef.current({ status: "FLAGGED", flags: [message], metrics: {} });
    }, 500);
    return () => { window.removeEventListener("message", handleMessage); window.clearInterval(startupCheck); };
  }, [nonce, safetyError]);

  if (safetyError) return <div className="matter-sandbox-error" role="alert">{safetyError}</div>;
  return <div className="matter-sandbox" aria-busy={status === "starting"}>
    {status !== "error" && <iframe key={nonce} ref={iframeRef} title="Interactive Matter.js simulation"
      sandbox="allow-scripts" referrerPolicy="no-referrer" srcDoc={html} />}
    {status === "starting" && <div className="matter-sandbox-overlay" role="status">Starting simulation…</div>}
    {status === "paused" && <div className="matter-sandbox-note" role="status">{pausedReason}</div>}
    {status === "error" && <div className="matter-sandbox-error" role="alert">{error}</div>}
  </div>;
}
