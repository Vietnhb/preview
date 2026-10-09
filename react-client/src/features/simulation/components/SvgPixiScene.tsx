import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from "react";
import pixiBundle from "../../../../node_modules/pixi.js/dist/webworker.min.js?raw";
import purifierBundle from "../../../../node_modules/dompurify/dist/purify.min.js?raw";
import Icon from "../../../shared/ui/LearningIcon";
import { sampleTimeline, type SolverTimeline, type PixiVisualProgram } from "../model/svgScene";
import { describeScene, displayValue, formatNumber, formatTime, presentationRate, prettyUnit, replayTooFast, seriesColor,
  type BackendFieldMeta, type SceneDescriptor, type SceneObservable, type SimulationModelRef } from "../model/sceneModel";
import { createStageKit } from "../engine/stageKit";
import { presentedFields } from "../model/scenePresentation";
import SimulationCharts from "./SimulationCharts";
import { useWorkspaceTheme } from "../hooks/useWorkspaceTheme";
import { diagnoseGeneratedCode, formatIssues, repairGeneratedCode } from "../model/codeRepair";

/**
 * Academic reference scene: composed only from the generic stage kit and the
 * semantic scene descriptor. Used when the learner selects it and as the
 * automatic fallback when AI-generated drawing code fails or draws nothing.
 */
export const STANDARD_SCENE_CODE = "async function(PIXI, app, api) { return api.kit.standardScene(); }";
/** Declarative AI scene (SVG roles only) assembled by the kit. */
export const ILLUSTRATED_SCENE_CODE = "async function(PIXI, app, api) { return api.kit.illustratedScene(api.sceneSpec); }";

// A terminable worker in an opaque-origin iframe cannot access the host or network.
const WORKER = String.raw`
let app, lifecycle, data, kit, stage = null, playing = true, loop = false, speed = 1, t = 0, last = performance.now(), nextAsset = 0;
const kitScenes = [];
// Irregular fractions of the run so periodic motion cannot hide between samples.
const VERIFY_TIMES = [0, 0.137, 0.291, 0.463, 0.618, 0.779, 0.912, 1];
// The visual cross-check: every changing visual must be placed by the kit from the verified solver
// timeline. Generated code only supplies artwork and static decoration.
const KIT_GUIDE = ' Build the stage with api.kit.illustratedScene(api.sceneSpec) (or api.kit.standardScene()) and hand every changing visual to it:'
  + ' moving bodies -> scene.bodies / base.attach(id, sprite); things riding on a body -> base.follow(id, item, {dx, dy});'
  + ' ropes, wires, rods between bodies or fixed points -> scene.links / base.link(a, b); any changing state (a needle, a liquid level, a glow,'
  + ' moving charges …) -> scene.instruments / base.instrument({field, art, part, drive}) with your own mapping; things standing at a physical place'
  + ' (wall, stop, fixed charge) -> scene.fixtures / base.fixture(sprite, {x, y, anchor, solid}). Your own objects must stay static (add them to base.props).';
let frames = 0, lastSent = -1;
const assets = new Map(), textureIds = new WeakMap(), textureLuma = new WeakMap(), textureInk = new WeakMap();
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
function frame(dt = 0) { return frameAt(t, dt); }
function frameAt(time, dt = 0) {
  return Object.freeze({t: time, dt, fields: strictFields(sampleTimeline(data.timeline, time)),
    parameters: Object.freeze({...data.parameters}), width: app.screen.width,
    height: app.screen.height, theme: data.theme, verificationStatus: data.verificationStatus, scene: data.scene});
}
// Generated update first, then the kit re-applies every solver-bound placement (positions, instruments).
function step(f) { stage.begin(); lifecycle.update(f); stage.finish(f); }
// Render at several times of the CURRENT solver timeline and compare the stage with it. Runs when the
// program starts and again whenever the backend recomputes the timeline (parameter edits).
function crossCheck() {
  const duration = data.timeline.durationSeconds;
  const issues = stage.verify(time => step(frameAt(time)), VERIFY_TIMES.map(k => k * duration));
  if (issues.length) throw Error('Visual cross-check against the verified solver data failed:\n- ' + issues.join('\n- ') + '\n' + KIT_GUIDE);
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
    // Generated code sometimes passes the SVG-building function instead of its result.
    if (typeof svg === 'function') svg = svg();
    if (typeof svg !== 'string' || !svg.trim())
      return Promise.reject(Error('api.svgTexture(svg) expects SVG markup (a string), got ' + (svg === null ? 'null' : typeof svg) + '.'));
    const id = ++nextAsset;
    return new Promise((resolve, reject) => {
      assets.set(id, {resolve: texture => { textureIds.set(texture, id); resolve(texture); }, reject});
      send('svg', {id, svg, screen: Boolean(options && options.screen),
        width: Number(options && options.width) || 0, height: Number(options && options.height) || 0});
    });
  };
  const releaseTexture = texture => {
    const id = texture && textureIds.get(texture);
    if (id) { textureIds.delete(texture); send('release', {id}); }
    try { texture?.destroy(true); } catch {}
  };
  kit = createStageKit(PIXI, app, {data: () => data, sample: time => sampleTimeline(data.timeline, time),
    svg: svgTexture, release: releaseTexture, luma: texture => textureLuma.get(texture), ink: texture => textureInk.get(texture),
    fail: message => { send('error', {message: String(message)}); close(); },
    register: scene => { kitScenes.push(scene); }});
  const api = Object.freeze({
    get width() { return app.screen.width; }, get height() { return app.screen.height; },
    get scene() { return data.scene; },
    get sceneSpec() { return data.sceneSpec || null; },
    kit,
    palette: kit.palette,
    format: kit.format,
    getFrame: () => frame(),
    getFieldRanges: () => data.ranges,
    svgTexture,
    releaseTexture
  });
  const AsyncFunction = Object.getPrototypeOf(async function() {}).constructor;
  const mount = await new AsyncFunction('"use strict"; return (' + message.code + '\n);')();
  if (typeof mount !== 'function') throw Error('PixiJS code must be a mount function.');
  // Compatibility helpers for generated code; leave the PixiJS namespace untouched.
  const compatiblePIXI = Object.assign({}, PIXI, {
    utils: Object.assign({}, PIXI.utils || {}, {
      hsv2rgb([h, s, v]) {
        h = ((h % 1) + 1) % 1;
        s = Math.max(0, Math.min(1, s));
        v = Math.max(0, Math.min(1, v));
        const channel = n => {
          const k = (n + h * 6) % 6;
          return v * (1 - s * Math.max(0, Math.min(k, 4 - k, 1)));
        };
        return [channel(5), channel(3), channel(1)];
      },
      rgb2hex(rgb) {
        const byte = value => Math.round(Math.max(0, Math.min(1, value)) * 255);
        return (byte(rgb[0]) << 16) | (byte(rgb[1]) << 8) | byte(rgb[2]);
      }
    })
  });
  lifecycle = await mount(compatiblePIXI, app, api);
  if (!lifecycle || typeof lifecycle.update !== 'function') throw Error('PixiJS code must return update(frame).');
  if (!kitScenes.length) throw Error('Visual cross-check: the program draws its own stage, so nothing on it can be checked against the verified solver data.' + KIT_GUIDE);
  if (kitScenes.length > 1) throw Error('Visual cross-check: build exactly one kit scene (found ' + kitScenes.length + ').');
  stage = kitScenes[0];
  crossCheck();
  step(frame(0)); app.render();
  last = performance.now();
  const renderFrame = () => {
    try {
      const now = performance.now(), dt = Math.min(0.1, (now - last) / 1000) * speed; last = now;
      const duration = data.timeline.durationSeconds;
      if (playing) {
        t += dt;
        if (t >= duration) {
          if (loop) t = 0; else { t = duration; playing = false; send('ended', {t}); }
        }
      }
      step(frame(dt)); app.render(); frames++;
      // Shown once an animation frame has reached the screen (the previous one was committed), so the stage never flashes blank.
      if (frames === 2) send('ready', {t});
      if (frames === 20) checkVisible();
      if (t !== lastSent && (frames % 6 === 0 || !playing)) { lastSent = t; send('tick', {t}); }
      else if (frames % 30 === 0) send('tick', {t});
    } catch (error) { send('error', {message: String(error && error.message || error)}); close(); }
  };
  // OffscreenCanvas follows display refresh; older workers retain the timer fallback.
  if (typeof requestAnimationFrame === 'function') {
    const animate = () => { renderFrame(); requestAnimationFrame(animate); };
    requestAnimationFrame(animate);
    // Background tabs may suspend animation frames; keep the watchdog informed without redrawing.
    setInterval(() => send('tick', {t}), 1000);
  } else setInterval(renderFrame, 1000 / 60);
}
// Errors thrown in un-awaited async callbacks (e.g. forEach(async …)) must not vanish.
if (typeof addEventListener === 'function') addEventListener('unhandledrejection', event => {
  send('error', {message: String(event.reason && event.reason.message || event.reason)}); close();
});
onmessage = async ({data: message}) => {
  try {
    switch (message.type) {
      case 'start': await start(message); break;
      case 'data':
        data = {...data, ...message}; data.ranges = ranges(data.timeline);
        t = Math.min(t, data.timeline.durationSeconds);
        stage?.refresh(); lifecycle?.setData?.(); lifecycle?.resize?.(app.screen.width, app.screen.height);
        if (stage) { crossCheck(); step(frame(0)); app.render(); }
        break;
      case 'theme': data.theme = message.theme; stage?.refresh(); lifecycle?.setData?.(); lifecycle?.resize?.(app.screen.width, app.screen.height); break;
      case 'play':
        if (message.playing && t >= data.timeline.durationSeconds) t = 0;
        playing = message.playing; break;
      case 'speed': speed = Math.max(1e-15, Math.min(1e15, Number(message.speed) || 1)); break;
      case 'loop': loop = Boolean(message.loop); break;
      case 'seek': t = Math.max(0, Math.min(data.timeline.durationSeconds, message.t)); break;
      case 'resize': app.renderer.resize(message.width, message.height); lifecycle?.resize?.(message.width, message.height); break;
      case 'pointer': lifecycle?.pointer?.(message.event); break;
      case 'asset': {
        const pending = assets.get(message.id); assets.delete(message.id);
        if (!pending) break;
        if (message.error) pending.reject(Error(message.error));
        else {
          const texture = textureFrom(message.bitmap, message.resolution);
          if (typeof message.luma === 'number') textureLuma.set(texture, message.luma);
          if (message.ink) textureInk.set(texture, message.ink);
          pending.resolve(texture);
        }
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
    FORBID_TAGS: ['script','foreignObject','image','a','style','animate','animateTransform','set','text','tspan','textPath'],
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
  // Artwork drawn in tiny units (e.g. metres: viewBox 0.1 wide) is given a workable pixel size; the viewBox, and
  // with it every coordinate the generator declared, is unchanged.
  if (hasBox && !(numeric('width') > 0 && numeric('height') > 0) && Math.max(box[2], box[3]) < 64) {
    const grow = 128 / Math.max(box[2], box[3]);
    root.setAttribute('width', String(box[2] * grow)); root.setAttribute('height', String(box[3] * grow));
  }
  if (!root.getAttribute('xmlns')) root.setAttribute('xmlns', SVG_NS);
  return root;
}
async function svgBitmap(source, screen, fitWidth, fitHeight) {
  if (typeof source !== 'string' || source.length > limits.maxCode) throw Error('SVG resource budget exceeded.');
  const root = svgRoot(source);
  // Viewport art: render the document at the stage size, covering it without distortion.
  if (fitWidth > 0 && fitHeight > 0) {
    if (!root.getAttribute('viewBox')) {
      const w = parseFloat(root.getAttribute('width')), h = parseFloat(root.getAttribute('height'));
      if (w > 0 && h > 0) root.setAttribute('viewBox', '0 0 ' + w + ' ' + h);
    }
    root.setAttribute('width', String(Math.round(fitWidth))); root.setAttribute('height', String(Math.round(fitHeight)));
    if (!root.getAttribute('preserveAspectRatio')) root.setAttribute('preserveAspectRatio', 'xMidYMid slice');
  }
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
      : Math.max(1, Math.min(Math.max(devicePixelRatio * 1.5, 2, 640 / longest), cap));
    const surface = document.createElement('canvas');
    surface.width = Math.ceil(width * resolution); surface.height = Math.ceil(height * resolution);
    texturePixels += surface.width * surface.height;
    if (texturePixels > limits.maxTexturePixels) throw Error('SVG texture memory budget exceeded.');
    surface.getContext('2d').drawImage(image, 0, 0, surface.width, surface.height);
    // Where an artwork is drawn (a coarse ink mask): the kit keeps callouts off the drawing and checks declared object boxes.
    let ink = null;
    if (!screen) {
      const s = Math.min(1, 160 / Math.max(surface.width, surface.height));
      const probe = document.createElement('canvas');
      probe.width = Math.max(1, Math.round(surface.width * s)); probe.height = Math.max(1, Math.round(surface.height * s));
      const pc = probe.getContext('2d'); pc.drawImage(surface, 0, 0, probe.width, probe.height);
      const px = pc.getImageData(0, 0, probe.width, probe.height).data, data = new Uint8Array(probe.width * probe.height);
      for (let i = 0; i < data.length; i++) data[i] = px[i * 4 + 3] > 24 ? 1 : 0;
      ink = {w: probe.width, h: probe.height, data};
    }
    let luma = null;
    if (screen) {
      // Average brightness of viewport art so in-scene labels pick a readable tone.
      const probe = document.createElement('canvas'); probe.width = 32; probe.height = 18;
      const pc = probe.getContext('2d'); pc.drawImage(surface, 0, 0, 32, 18);
      const px = pc.getImageData(0, 0, 32, 18).data;
      let sum = 0, weight = 0;
      for (let i = 0; i < px.length; i += 4) {
        const a = px[i + 3] / 255;
        sum += a * (0.2126 * px[i] + 0.7152 * px[i + 1] + 0.0722 * px[i + 2]) / 255; weight += a;
      }
      if (weight > 32 * 18 * 0.5) luma = sum / weight;
    }
    return {bitmap: await createImageBitmap(surface), resolution: surface.width / width, pixels: surface.width * surface.height, luma, ink};
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
          const {bitmap, resolution, pixels, luma, ink} = await svgBitmap(result.svg, result.screen, result.width, result.height);
          assetPixels.set(result.id, pixels);
          if (stopped) { bitmap.close(); return; }
          worker.postMessage({type: 'asset', id: result.id, bitmap, resolution, luma, ink}, [bitmap]);
        } catch (error) { worker.postMessage({type: 'asset', id: result.id, error: String(error.message)}); }
      } else if (result.type === 'release') {
        texturePixels = Math.max(0, texturePixels - (assetPixels.get(result.id) || 0)); assetPixels.delete(result.id);
      } else if (['tick','ready','ended'].includes(result.type)) {
        lastHeartbeat = Date.now(); send(result.type, {t: result.t});
      } else if (result.type === 'error') stop(String(result.message).slice(0,2400));
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
const formatRate = (value: number) => formatNumber(value >= 100 ? value : Math.round(value * 10) / 10, 3);

type ViewMode = "ai" | "standard";

export default function SvgPixiScene({ program, timeline, parameters, verificationStatus, models, fieldMeta, observables, onRenderError, toolbarActions, cover = false, coverPlaying = false, onCoverFailed }: Readonly<{
  program: PixiVisualProgram; timeline: SolverTimeline; parameters: Record<string, number>; verificationStatus: string;
  models?: readonly SimulationModelRef[]; fieldMeta?: BackendFieldMeta; onRenderError?: (message: string) => void;
  /** The values the plan asks the learner to watch (simulationSpec.observables). */
  observables?: readonly SceneObservable[];
  /** Page-level buttons shown at the end of the toolbar so the page needs no heading row of its own. */
  toolbarActions?: ReactNode;
  /** Card cover: only the scene, no toolbar, playback bar or charts. It rests on the first frame. */
  cover?: boolean;
  /** In cover mode, plays (looping) while true, e.g. while the card is hovered. */
  coverPlaying?: boolean;
  /** In cover mode, called when neither the authored nor the reference scene could be drawn. */
  onCoverFailed?: () => void;
}>) {
  const theme = useWorkspaceTheme();
  const sceneSpec = program.scene && typeof program.scene === "object" ? program.scene : null;
  const aiCode = program.code?.trim() ? program.code : sceneSpec ? ILLUSTRATED_SCENE_CODE : "";
  const hasAiCode = Boolean(aiCode);
  const [mode, setMode] = useState<ViewMode>(hasAiCode ? "ai" : "standard");
  const [aiError, setAiError] = useState("");
  const [error, setError] = useState("");
  const [ready, setReady] = useState(false);
  const [time, setTime] = useState(0);
  const [playing, setPlaying] = useState(!cover);
  const [speed, setSpeed] = useState(1);
  /* simulated seconds per real second at 1× (nanosecond and year-long runs stay watchable) */
  const rate = useMemo(() => presentationRate(timeline.durationSeconds, timeline), [timeline]);
  const tooFast = useMemo(() => replayTooFast(timeline), [timeline]);
  const rateNote = (rate > 1.5 ? "Tua nhanh ×" + formatRate(rate) : rate < 1 / 1.5 ? "Chiếu chậm ×" + formatRate(1 / rate) : "")
    + (tooFast ? " · dao động quá nhanh để hiện hết, xem đồ thị" : "");
  const [loop, setLoop] = useState(cover);
  const [run, setRun] = useState(0);
  const iframe = useRef<HTMLIFrameElement>(null);
  const scene = useMemo(() => describeScene(timeline, models ?? [], fieldMeta ?? {}, observables ?? []), [timeline, models, fieldMeta, observables]);
  const dataRef = useRef({ timeline, parameters, verificationStatus, scene });
  const callbackRef = useRef(onRenderError);
  const modeRef = useRef(mode);
  const coverRef = useRef(cover);
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
  const prepared = useMemo(() => mode === "ai" && hasAiCode ? repairGeneratedCode(aiCode) : { code: STANDARD_SCENE_CODE, fixes: [] },
    [mode, hasAiCode, aiCode]);
  const code = prepared.code;
  const codeRef = useRef(code);
  useEffect(() => { codeRef.current = code; }, [code]);

  useEffect(() => {
    dataRef.current = { timeline, parameters, verificationStatus, scene };
    send({ type: "data", ...dataRef.current });
  }, [timeline, parameters, verificationStatus, scene, send]);
  useEffect(() => { send({ type: "theme", theme }); }, [theme, send]);
  useEffect(() => {
    const receive = (event: MessageEvent) => {
      if (event.source !== iframe.current?.contentWindow || event.data?.channel !== "pixi-runtime") return;
      if (event.data.type === "error") {
        let message = String(event.data.message);
        if (modeRef.current === "ai") {
          // Line-numbered hints make the AI repair request actionable.
          const hints = formatIssues(diagnoseGeneratedCode(codeRef.current));
          if (hints) message += "\nHints:\n" + hints;
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
        // A cover shows no clock or charts, so it skips the per-frame React update entirely.
        if (coverRef.current) return;
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
    send({ type: "start", code, sceneSpec, ...dataRef.current, theme, limits, bundle: pixiBundle, runtime: RUNTIME });
    send({ type: "speed", speed: speed * rate }); send({ type: "loop", loop }); send({ type: "play", playing: cover ? coverPlaying : playing });
  };
  const togglePlay = () => { const next = !playing; setPlaying(next); send({ type: "play", playing: next }); };
  const restart = () => { send({ type: "seek", t: 0 }); setTime(0); setPlaying(true); send({ type: "play", playing: true }); };
  const seek = (value: number) => { send({ type: "seek", t: value }); setTime(value); };
  const selectMode = (next: ViewMode) => {
    if (next === mode) return;
    if (next === "ai") setAiError("");
    setMode(next); setRun(value => value + 1);
  };
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
      {!error && <iframe key={mode + ":" + run} ref={iframe} className="sim-stage__frame" title={program.description || "Mô phỏng vật lý"}
        sandbox="allow-scripts" referrerPolicy="no-referrer" tabIndex={-1} srcDoc={html} onLoad={start} />}
      {!error && !ready && <div className="sim-stage__loading" role="status"><span className="sim-spinner" /></div>}
    </div>
  </div>;

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
      {toolbarActions && <div className="sim-player__actions">{toolbarActions}</div>}
    </div>
    {aiError && mode === "standard" && <div className="sim-notice" role="status">
      <strong>Cảnh AI chưa hiển thị được</strong> — đang dùng cảnh chuẩn học thuật dựng từ dữ liệu backend.
      <details><summary>Chi tiết lỗi</summary><code>{aiError}</code></details>
    </div>}
    <div className="sim-stage" data-ready={ready}>
      {!error && <iframe key={mode + ":" + run + ":" + aiCode + ":" + JSON.stringify(sceneSpec ?? "").length} ref={iframe} className="sim-stage__frame"
        title={program.description || "Mô phỏng vật lý"} sandbox="allow-scripts" referrerPolicy="no-referrer"
        srcDoc={html} onLoad={start} />}
      {error && <p role="alert" className="simulation-error">{error}</p>}
      {!error && !ready && <div className="sim-stage__loading" role="status"><span className="sim-spinner" /> Đang dựng cảnh…</div>}
    </div>
    {!error && timeline.frames.length > 0 && <SceneReadouts scene={scene} spec={mode === "ai" ? sceneSpec : null} values={sampleTimeline(timeline, time)} theme={theme} />}
    <div className="sim-transport">
      <button type="button" className="sim-icon-button sim-icon-button--primary" onClick={togglePlay}
        aria-label={playing ? "Tạm dừng" : "Phát"} title={playing ? "Tạm dừng" : "Phát"}>
        <Icon name={playing ? "pause" : "play"} /></button>
      <button type="button" className="sim-icon-button" onClick={restart} aria-label="Phát lại từ đầu" title="Phát lại từ đầu">
        <Icon name="reset" /></button>
      <input className="sim-scrubber" aria-label="Thời gian mô phỏng" type="range" min={0} max={timeline.durationSeconds} step="any"
        value={time} style={{ "--progress": progress + "%" } as CSSProperties} onChange={event => seek(Number(event.target.value))} />
      <output className="sim-clock">{formatTime(time, timeline.durationSeconds).split(" ")[0]} <span>/ {formatTime(timeline.durationSeconds, timeline.durationSeconds)}</span></output>
      <select className="sim-select" aria-label="Tốc độ phát" value={speed}
        onChange={event => { const value = Number(event.target.value); setSpeed(value); send({ type: "speed", speed: value * rate }); }}>
        {SPEEDS.map(value => <option key={value} value={value}>{value}×</option>)}
      </select>
      {rateNote && <span className="sim-muted" style={{ fontSize: 12, whiteSpace: "nowrap" }}
        title="Thời gian thực của hiện tượng được co giãn để quan sát được">{rateNote}</span>}
      <label className="sim-toggle" title="Lặp lại khi hết thời gian">
        <input type="checkbox" checked={loop} onChange={event => { setLoop(event.target.checked); send({ type: "loop", loop: event.target.checked }); }} />
        Lặp
      </label>
    </div>
    <SimulationCharts scene={scene} timeline={timeline} time={time} theme={theme} onSeek={seek} />
  </div>;
}

/** Scene bindings select focused readings; intermediate calculations remain available in full. */
function SceneReadouts({ scene, spec, values, theme }: {
  scene: SceneDescriptor; spec: Record<string, unknown> | null; values: Record<string, number>; theme: "LIGHT" | "DARK";
}) {
  if (!scene.participants.length) return null;
  const focus = presentedFields(scene, spec);
  const reading = (key: string) => {
    const meta = scene.fields[key], shown = displayValue(meta, values[key]);
    const factor = values[key] ? shown.value / values[key] : 1;
    return formatNumber(shown.value, 4, Math.max(Math.abs(meta.min), Math.abs(meta.max)) * Math.abs(factor))
      + (shown.unit ? " " + prettyUnit(shown.unit) : "");
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
