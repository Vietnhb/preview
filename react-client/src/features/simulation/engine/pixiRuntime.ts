/** Isolated renderer transport. No layout, physical inference or scene templates. */
// sampleTimeline is injected by the host. The generated lifecycle owns all stage objects.
export const PIXI_WORKER = String.raw`
(() => {
let app, lifecycle, data, playing = true, loop = false, speed = 1, t = 0, last = performance.now(), nextAsset = 0;
let frames = 0, lastSent = -1, dirty = true, disposed = false;
const assets = new Map(), textureIds = new WeakMap();
const QUIET_KEYS = new Set(['toJSON', 'then', 'asymmetricMatch', 'nodeType', '$$typeof']);
const send = (type, extra = {}) => postMessage({type, ...extra});
function freeze(value) {
  if (value && typeof value === 'object' && !Object.isFrozen(value)) {
    for (const child of Object.values(value)) freeze(child);
    Object.freeze(value);
  }
  return value;
}
function ranges(timeline) {
  const result = {};
  for (const frame of timeline.frames) for (const [name, value] of Object.entries(frame.values)) {
    const item = result[name] || (result[name] = {min: value, max: value});
    item.min = Math.min(item.min, value); item.max = Math.max(item.max, value);
  }
  return freeze(result);
}
function setData(message) {
  data = {...data, ...message};
  data.timeline = freeze(data.timeline);
  data.parameters = freeze(data.parameters || {});
  data.fieldMeta = freeze(data.fieldMeta || {});
  data.models = freeze(data.models || []);
  data.observables = freeze(data.observables || []);
  data.parameterInfo = freeze(data.parameterInfo || []);
  data.ranges = ranges(data.timeline);
}
function strictFields(values) {
  return new Proxy(Object.freeze(values), {get(target, key) {
    if (typeof key === 'string' && !(key in target) && !QUIET_KEYS.has(key))
      throw Error('Unknown solver field "' + key + '". frame.fields keys are: ' + Object.keys(target).join(', '));
    return target[key];
  }});
}
function frame(dt = 0) {
  return Object.freeze({t, dt, fields: strictFields(sampleTimeline(data.timeline, t)), parameters: data.parameters,
    width: app.screen.width, height: app.screen.height, theme: data.theme, verificationStatus: data.verificationStatus});
}
function render(dt = 0) { lifecycle.update(frame(dt)); app.render(); }
function fail(error) { disposed = true; send('error', {message: String(error && error.message || error)}); close(); }
function textureFrom(bitmap, resolution) {
  if (resolution > 1 && PIXI.ImageSource) return new PIXI.Texture({source: new PIXI.ImageSource({resource: bitmap, resolution})});
  return PIXI.Texture.from(bitmap);
}
function checkVisible() {
  if (typeof app.stage.getBounds !== 'function') return;
  const b = app.stage.getBounds(), w = app.screen.width, h = app.screen.height;
  if ([b.minX, b.minY, b.maxX, b.maxY].some(value => !Number.isFinite(value)) && (b.width || b.height))
    throw Error('Generated scene has non-finite coordinates (NaN/Infinity).');
  if (!(b.width > 0 && b.height > 0) || b.maxX < 0 || b.maxY < 0 || b.minX > w || b.minY > h)
    throw Error('Generated scene draws nothing visible inside the viewport. Update the generated layout for the current width and height.');
}
async function start(message) {
  setData(message);
  app = new PIXI.Application();
  await app.init({canvas: message.canvas, width: message.width, height: message.height, resolution: message.dpr || 1,
    backgroundAlpha: 0, antialias: true, preference: 'webgl', autoStart: false});
  const svgTexture = (svg, options = {}) => {
    if (typeof svg !== 'string' || !svg.trim()) return Promise.reject(Error('api.svgTexture(svg) expects SVG markup.'));
    const id = ++nextAsset;
    return new Promise((resolve, reject) => {
      assets.set(id, {resolve: texture => {textureIds.set(texture, id); resolve(texture);}, reject});
      send('svg', {id, svg, screen: Boolean(options.screen), width: Number(options.width) || 0, height: Number(options.height) || 0});
    });
  };
  const releaseTexture = texture => {
    const id = texture && textureIds.get(texture);
    if (id) {textureIds.delete(texture); send('release', {id});}
    try {texture?.destroy(true);} catch {}
  };
  const api = Object.freeze({
    get width() {return app.screen.width;}, get height() {return app.screen.height;},
    get theme() {return data.theme;}, get parameters() {return data.parameters;},
    get fieldMeta() {return data.fieldMeta;}, get models() {return data.models;},
    get observables() {return data.observables;}, get parameterInfo() {return data.parameterInfo;},
    get timeline() {return data.timeline;},
    getFrame: () => frame(), getFieldRanges: () => data.ranges,
    sample: time => strictFields(sampleTimeline(data.timeline, time)),
    svgTexture, releaseTexture,
  });
  const AsyncFunction = Object.getPrototypeOf(async function() {}).constructor;
  const mount = await new AsyncFunction('"use strict"; return (' + message.code + '\n);')();
  if (typeof mount !== 'function') throw Error('PixiJS code must be a mount function.');
  lifecycle = await mount(PIXI, app, api);
  if (!lifecycle || typeof lifecycle.update !== 'function') throw Error('PixiJS code must return update(frame).');
  render(); checkVisible();
  last = performance.now();
  const renderFrame = () => {
    if (disposed) return;
    try {
      const now = performance.now(), dt = Math.min(0.1, (now - last) / 1000) * speed; last = now;
      if (!playing && !dirty && frames >= 2) return;
      dirty = false;
      const duration = data.timeline.durationSeconds;
      if (playing) {
        t += dt;
        if (t >= duration) {
          if (loop) t = 0; else {t = duration; playing = false; send('ended', {t});}
        }
      }
      render(dt); frames++;
      if (frames === 2) send('ready', {t});
      if (t !== lastSent && (frames % 6 === 0 || !playing)) {lastSent = t; send('tick', {t});}
      else if (frames % 30 === 0) send('tick', {t});
    } catch (error) {fail(error);}
  };
  if (typeof requestAnimationFrame === 'function') {
    const animate = () => {renderFrame(); if (!disposed) requestAnimationFrame(animate);};
    requestAnimationFrame(animate);
    setInterval(() => send('tick', {t}), 1000);
  } else setInterval(renderFrame, 1000 / 60);
}
if (typeof addEventListener === 'function') addEventListener('unhandledrejection', event => fail(event.reason));
onmessage = async ({data: message}) => {
  dirty = true;
  try {
    switch (message.type) {
      case 'start': await start(message); break;
      case 'data':
        setData(message); t = Math.min(t, data.timeline.durationSeconds);
        await lifecycle?.setData?.(); await lifecycle?.resize?.(app.screen.width, app.screen.height);
        if (lifecycle) render(); break;
      case 'theme': data.theme = message.theme; await lifecycle?.setData?.(); if (lifecycle) render(); break;
      case 'play': if (message.playing && t >= data.timeline.durationSeconds) t = 0; playing = message.playing; break;
      case 'speed': speed = Math.max(1e-15, Math.min(1e15, Number(message.speed) || 1)); break;
      case 'loop': loop = Boolean(message.loop); break;
      case 'seek': t = Math.max(0, Math.min(data.timeline.durationSeconds, message.t)); break;
      case 'resize': app.renderer.resize(message.width, message.height); await lifecycle?.resize?.(message.width, message.height); break;
      case 'pointer': lifecycle?.pointer?.(message.event); break;
      case 'dispose': disposed = true; await lifecycle?.destroy?.(); close(); break;
      case 'asset': {
        const pending = assets.get(message.id); assets.delete(message.id);
        if (pending) {if (message.error) pending.reject(Error(message.error)); else pending.resolve(textureFrom(message.bitmap, message.resolution));}
        break;
      }
    }
  } catch (error) {fail(error);}
};
})();
`;

export const PIXI_BRIDGE = String.raw`
const canvas = document.querySelector('canvas');
const SVG_NS = 'http://www.w3.org/2000/svg';
let worker, lastHeartbeat = Date.now(), stopped = false, limits, texturePixels = 0;
const assetPixels = new Map();
const send = (type, extra = {}) => parent.postMessage({channel: 'pixi-runtime', type, ...extra}, '*');
function stop(message) { stopped = true; worker?.terminate(); send('error', {message}); }
// Sanitise the authored document without inventing colours, framing, text or geometry.
function svgRoot(source) {
  const markup = source.trim().replace(/^<\?xml[^>]*>\s*/i, '').replace(/^<!doctype[^>]*>\s*/i, '');
  if (!/^<svg[\s>]/i.test(markup)) throw Error('SVG asset must be a complete svg document.');
  const cleaned = DOMPurify.sanitize(markup, {USE_PROFILES: {svg: true, svgFilters: true},
    FORBID_TAGS: ['script','foreignObject','image','a','style','animate','animateTransform','set'],
    FORBID_ATTR: ['href','xlink:href','style']});
  const root = new DOMParser().parseFromString(cleaned, 'text/html').body.firstElementChild;
  if (!root || root.localName !== 'svg') throw Error('SVG asset must contain drawable SVG elements.');
  for (const node of [root, ...root.querySelectorAll('*')]) for (const attr of [...node.attributes])
    if (/url\s*\(/i.test(attr.value) && !/^url\(#[\w.-]+\)$/.test(attr.value)) throw Error('External SVG resources forbidden.');
  const box = (root.getAttribute('viewBox') || '').trim().split(/[ ,]+/).map(Number);
  const hasBox = box.length === 4 && box.every(Number.isFinite) && box[2] > 0 && box[3] > 0;
  const numeric = name => /^[\d.]+(?:px)?$/.test(root.getAttribute(name) || '') ? parseFloat(root.getAttribute(name)) : NaN;
  if (!hasBox && !(numeric('width') > 0 && numeric('height') > 0))
    throw Error('SVG asset needs an explicit viewBox or positive width and height.');
  if (!root.getAttribute('xmlns')) root.setAttribute('xmlns', SVG_NS);
  return root;
}
async function svgBitmap(source, screen, fitWidth, fitHeight) {
  if (typeof source !== 'string' || source.length > limits.maxCode) throw Error('SVG resource budget exceeded.');
  const root = svgRoot(source);
  // Raster dimensions are changed only when the authored code explicitly requests them.
  if (fitWidth > 0 && fitHeight > 0) {
    if (!root.getAttribute('viewBox')) {
      const w = parseFloat(root.getAttribute('width')), h = parseFloat(root.getAttribute('height'));
      if (w > 0 && h > 0) root.setAttribute('viewBox', '0 0 ' + w + ' ' + h);
    }
    root.setAttribute('width', String(Math.round(fitWidth))); root.setAttribute('height', String(Math.round(fitHeight)));
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
          const {bitmap, resolution, pixels} = await svgBitmap(result.svg, result.screen, result.width, result.height);
          assetPixels.set(result.id, pixels);
          if (stopped) { bitmap.close(); return; }
          worker.postMessage({type: 'asset', id: result.id, bitmap, resolution}, [bitmap]);
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
