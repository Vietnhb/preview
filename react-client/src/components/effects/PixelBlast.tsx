import { Effect, EffectComposer, EffectPass, RenderPass } from 'postprocessing';
import React, { useEffect, useRef } from 'react';
import * as THREE from 'three';

type PixelBlastVariant = 'square' | 'circle' | 'triangle' | 'diamond';

interface TouchPoint {
  x: number;
  y: number;
  vx: number;
  vy: number;
  force: number;
  age: number;
}

interface TouchTexture {
  canvas: HTMLCanvasElement;
  texture: THREE.Texture;
  addTouch: (norm: { x: number; y: number }) => void;
  update: () => void;
  radiusScale: number;
  size: number;
}

interface ReinitConfig {
  antialias: boolean;
  liquid: boolean;
  noiseAmount: number;
}

function updateEffectUniforms(composer: EffectComposer | undefined, elapsed: number) {
  if (!composer) return;
  for (const pass of composer.passes) {
    const typedPass = pass as { effects?: Array<Effect & { uniforms: Map<string, THREE.Uniform> }> };
    if (!typedPass.effects) continue;
    for (const effect of typedPass.effects) {
      const timeUniform = effect.uniforms?.get('uTime');
      if (timeUniform) timeUniform.value = elapsed;
    }
  }
}

type PixelBlastProps = {
  variant?: PixelBlastVariant;
  pixelSize?: number;
  color?: string;
  className?: string;
  style?: React.CSSProperties;
  antialias?: boolean;
  patternScale?: number;
  patternDensity?: number;
  liquid?: boolean;
  liquidStrength?: number;
  liquidRadius?: number;
  pixelSizeJitter?: number;
  enableRipples?: boolean;
  rippleIntensityScale?: number;
  rippleThickness?: number;
  rippleSpeed?: number;
  liquidWobbleSpeed?: number;
  autoPauseOffscreen?: boolean;
  speed?: number;
  transparent?: boolean;
  edgeFade?: number;
  noiseAmount?: number;
};

const createTouchTexture = (): TouchTexture => {
  const size = 64;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('2D context not available');
  ctx.fillStyle = 'black';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  const texture = new THREE.Texture(canvas);
  texture.minFilter = THREE.LinearFilter;
  texture.magFilter = THREE.LinearFilter;
  texture.generateMipmaps = false;
  const trail: TouchPoint[] = [];
  let last: { x: number; y: number } | null = null;
  const maxAge = 64;
  let radius = 0.1 * size;
  const speed = 1 / maxAge;
  const clear = () => {
    ctx.fillStyle = 'black';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
  };
  const drawPoint = (p: TouchPoint) => {
    const pos = { x: p.x * size, y: (1 - p.y) * size };
    let intensity = 1;
    const easeOutSine = (t: number) => Math.sin((t * Math.PI) / 2);
    const easeOutQuad = (t: number) => -t * (t - 2);
    if (p.age < maxAge * 0.3) intensity = easeOutSine(p.age / (maxAge * 0.3));
    else intensity = easeOutQuad(1 - (p.age - maxAge * 0.3) / (maxAge * 0.7)) || 0;
    intensity *= p.force;
    const color = `${((p.vx + 1) / 2) * 255}, ${((p.vy + 1) / 2) * 255}, ${intensity * 255}`;
    const offset = size * 5;
    ctx.shadowOffsetX = offset;
    ctx.shadowOffsetY = offset;
    ctx.shadowBlur = radius;
    ctx.shadowColor = `rgba(${color},${0.22 * intensity})`;
    ctx.beginPath();
    ctx.fillStyle = 'rgba(255,0,0,1)';
    ctx.arc(pos.x - offset, pos.y - offset, radius, 0, Math.PI * 2);
    ctx.fill();
  };
  const addTouch = (norm: { x: number; y: number }) => {
    let force = 0;
    let vx = 0;
    let vy = 0;
    if (last) {
      const dx = norm.x - last.x;
      const dy = norm.y - last.y;
      if (dx === 0 && dy === 0) return;
      const dd = dx * dx + dy * dy;
      const d = Math.sqrt(dd);
      vx = dx / (d || 1);
      vy = dy / (d || 1);
      force = Math.min(dd * 10000, 1);
    }
    last = { x: norm.x, y: norm.y };
    trail.push({ x: norm.x, y: norm.y, age: 0, force, vx, vy });
  };
  const update = () => {
    clear();
    for (let i = trail.length - 1; i >= 0; i--) {
      const point = trail[i];
      const f = point.force * speed * (1 - point.age / maxAge);
      point.x += point.vx * f;
      point.y += point.vy * f;
      point.age++;
      if (point.age > maxAge) trail.splice(i, 1);
    }
    for (const point of trail) drawPoint(point);
    texture.needsUpdate = true;
  };
  return {
    canvas,
    texture,
    addTouch,
    update,
    set radiusScale(v: number) {
      radius = 0.1 * size * v;
    },
    get radiusScale() {
      return radius / (0.1 * size);
    },
    size
  };
};

const createLiquidEffect = (texture: THREE.Texture, opts?: { strength?: number; freq?: number }) => {
  const fragment = `
    uniform sampler2D uTexture;
    uniform float uStrength;
    uniform float uTime;
    uniform float uFreq;

    void mainUv(inout vec2 uv) {
      vec4 tex = texture2D(uTexture, uv);
      float vx = tex.r * 2.0 - 1.0;
      float vy = tex.g * 2.0 - 1.0;
      float intensity = tex.b;

      float wave = 0.5 + 0.5 * sin(uTime * uFreq + intensity * 6.2831853);

      float amt = uStrength * intensity * wave;

      uv += vec2(vx, vy) * amt;
    }
    `;
  return new Effect('LiquidEffect', fragment, {
    uniforms: new Map<string, THREE.Uniform>([
      ['uTexture', new THREE.Uniform(texture)],
      ['uStrength', new THREE.Uniform(opts?.strength ?? 0.025)],
      ['uTime', new THREE.Uniform(0)],
      ['uFreq', new THREE.Uniform(opts?.freq ?? 4.5)]
    ])
  });
};

const SHAPE_MAP: Record<PixelBlastVariant, number> = {
  square: 0,
  circle: 1,
  triangle: 2,
  diamond: 3
};

const VERTEX_SRC = `
void main() {
  gl_Position = vec4(position, 1.0);
}
`;
const FRAGMENT_SRC = `
precision highp float;

uniform vec3  uColor;
uniform vec2  uResolution;
uniform float uTime;
uniform float uPixelSize;
uniform float uScale;
uniform float uDensity;
uniform float uPixelJitter;
uniform int   uEnableRipples;
uniform float uRippleSpeed;
uniform float uRippleThickness;
uniform float uRippleIntensity;
uniform float uEdgeFade;

uniform int   uShapeType;
const int SHAPE_SQUARE   = 0;
const int SHAPE_CIRCLE   = 1;
const int SHAPE_TRIANGLE = 2;
const int SHAPE_DIAMOND  = 3;

const int   MAX_CLICKS = 10;

uniform vec2  uClickPos  [MAX_CLICKS];
uniform float uClickTimes[MAX_CLICKS];

out vec4 fragColor;

float Bayer2(vec2 a) {
  a = floor(a);
  return fract(a.x / 2. + a.y * a.y * .75);
}
#define Bayer4(a) (Bayer2(.5*(a))*0.25 + Bayer2(a))
#define Bayer8(a) (Bayer4(.5*(a))*0.25 + Bayer2(a))

#define FBM_OCTAVES     5
#define FBM_LACUNARITY  1.25
#define FBM_GAIN        1.0

float hash11(float n){ return fract(sin(n)*43758.5453); }

float vnoise(vec3 p){
  vec3 ip = floor(p);
  vec3 fp = fract(p);
  float n000 = hash11(dot(ip + vec3(0.0,0.0,0.0), vec3(1.0,57.0,113.0)));
  float n100 = hash11(dot(ip + vec3(1.0,0.0,0.0), vec3(1.0,57.0,113.0)));
  float n010 = hash11(dot(ip + vec3(0.0,1.0,0.0), vec3(1.0,57.0,113.0)));
  float n110 = hash11(dot(ip + vec3(1.0,1.0,0.0), vec3(1.0,57.0,113.0)));
  float n001 = hash11(dot(ip + vec3(0.0,0.0,1.0), vec3(1.0,57.0,113.0)));
  float n101 = hash11(dot(ip + vec3(1.0,0.0,1.0), vec3(1.0,57.0,113.0)));
  float n011 = hash11(dot(ip + vec3(0.0,1.0,1.0), vec3(1.0,57.0,113.0)));
  float n111 = hash11(dot(ip + vec3(1.0,1.0,1.0), vec3(1.0,57.0,113.0)));
  vec3 w = fp*fp*fp*(fp*(fp*6.0-15.0)+10.0);
  float x00 = mix(n000, n100, w.x);
  float x10 = mix(n010, n110, w.x);
  float x01 = mix(n001, n101, w.x);
  float x11 = mix(n011, n111, w.x);
  float y0  = mix(x00, x10, w.y);
  float y1  = mix(x01, x11, w.y);
  return mix(y0, y1, w.z) * 2.0 - 1.0;
}

float fbm2(vec2 uv, float t){
  vec3 p = vec3(uv * uScale, t);
  float amp = 1.0;
  float freq = 1.0;
  float sum = 1.0;
  for (int i = 0; i < FBM_OCTAVES; ++i){
    sum  += amp * vnoise(p * freq);
    freq *= FBM_LACUNARITY;
    amp  *= FBM_GAIN;
  }
  return sum * 0.5 + 0.5;
}

float maskCircle(vec2 p, float cov){
  float r = sqrt(cov) * .25;
  float d = length(p - 0.5) - r;
  float aa = 0.5 * fwidth(d);
  return cov * (1.0 - smoothstep(-aa, aa, d * 2.0));
}

float maskTriangle(vec2 p, vec2 id, float cov){
  bool flip = mod(id.x + id.y, 2.0) > 0.5;
  if (flip) p.x = 1.0 - p.x;
  float r = sqrt(cov);
  float d  = p.y - r*(1.0 - p.x);
  float aa = fwidth(d);
  return cov * clamp(0.5 - d/aa, 0.0, 1.0);
}

float maskDiamond(vec2 p, float cov){
  float r = sqrt(cov) * 0.564;
  return step(abs(p.x - 0.49) + abs(p.y - 0.49), r);
}

void main(){
  float pixelSize = uPixelSize;
  vec2 fragCoord = gl_FragCoord.xy - uResolution * .5;
  float aspectRatio = uResolution.x / uResolution.y;

  vec2 pixelId = floor(fragCoord / pixelSize);
  vec2 pixelUV = fract(fragCoord / pixelSize);

  float cellPixelSize = 8.0 * pixelSize;
  vec2 cellId = floor(fragCoord / cellPixelSize);
  vec2 cellCoord = cellId * cellPixelSize;
  vec2 uv = cellCoord / uResolution * vec2(aspectRatio, 1.0);

  float base = fbm2(uv, uTime * 0.05);
  base = base * 0.5 - 0.65;

  float feed = base + (uDensity - 0.5) * 0.3;

  float speed     = uRippleSpeed;
  float thickness = uRippleThickness;
  const float dampT     = 1.0;
  const float dampR     = 10.0;

  if (uEnableRipples == 1) {
    for (int i = 0; i < MAX_CLICKS; ++i){
      vec2 pos = uClickPos[i];
      if (pos.x < 0.0) continue;
      float cellPixelSize = 8.0 * pixelSize;
      vec2 cuv = (((pos - uResolution * .5 - cellPixelSize * .5) / (uResolution))) * vec2(aspectRatio, 1.0);
      float t = max(uTime - uClickTimes[i], 0.0);
      float r = distance(uv, cuv);
      float waveR = speed * t;
      float ring  = exp(-pow((r - waveR) / thickness, 2.0));
      float atten = exp(-dampT * t) * exp(-dampR * r);
      feed = max(feed, ring * atten * uRippleIntensity);
    }
  }

  float bayer = Bayer8(fragCoord / uPixelSize) - 0.5;
  float bw = step(0.5, feed + bayer);

  float h = fract(sin(dot(floor(fragCoord / uPixelSize), vec2(127.1, 311.7))) * 43758.5453);
  float jitterScale = 1.0 + (h - 0.5) * uPixelJitter;
  float coverage = bw * jitterScale;
  float M;
  if      (uShapeType == SHAPE_CIRCLE)   M = maskCircle (pixelUV, coverage);
  else if (uShapeType == SHAPE_TRIANGLE) M = maskTriangle(pixelUV, pixelId, coverage);
  else if (uShapeType == SHAPE_DIAMOND)  M = maskDiamond(pixelUV, coverage);
  else                                   M = coverage;

  if (uEdgeFade > 0.0) {
    vec2 norm = gl_FragCoord.xy / uResolution;
    float edge = min(min(norm.x, norm.y), min(1.0 - norm.x, 1.0 - norm.y));
    float fade = smoothstep(0.0, uEdgeFade, edge);
    M *= fade;
  }

  vec3 color = uColor;

  // sRGB gamma correction - convert linear to sRGB for accurate color output
  vec3 srgbColor = mix(
    color * 12.92,
    1.055 * pow(color, vec3(1.0 / 2.4)) - 0.055,
    step(0.0031308, color)
  );

  fragColor = vec4(srgbColor, M);
}
`;

const MAX_CLICKS = 10;

type PixelBlastUniforms = {
  uResolution: { value: THREE.Vector2 };
  uTime: { value: number };
  uColor: { value: THREE.Color };
  uClickPos: { value: THREE.Vector2[] };
  uClickTimes: { value: Float32Array };
  uShapeType: { value: number };
  uPixelSize: { value: number };
  uScale: { value: number };
  uDensity: { value: number };
  uPixelJitter: { value: number };
  uEnableRipples: { value: number };
  uRippleSpeed: { value: number };
  uRippleThickness: { value: number };
  uRippleIntensity: { value: number };
  uEdgeFade: { value: number };
};

type PixelBlastConfig = {
  variant: PixelBlastVariant;
  pixelSize: number;
  color: string;
  antialias: boolean;
  patternScale: number;
  patternDensity: number;
  liquid: boolean;
  liquidStrength: number;
  liquidRadius: number;
  pixelSizeJitter: number;
  enableRipples: boolean;
  rippleIntensityScale: number;
  rippleThickness: number;
  rippleSpeed: number;
  liquidWobbleSpeed: number;
  autoPauseOffscreen: boolean;
  speed: number;
  transparent: boolean;
  edgeFade: number;
  noiseAmount: number;
};

type PixelBlastState = {
  renderer: THREE.WebGLRenderer;
  scene: THREE.Scene;
  camera: THREE.OrthographicCamera;
  material: THREE.ShaderMaterial;
  timer: THREE.Timer;
  clickIx: number;
  uniforms: PixelBlastUniforms;
  resizeObserver?: ResizeObserver;
  raf?: number;
  quad?: THREE.Mesh<THREE.PlaneGeometry, THREE.ShaderMaterial>;
  timeOffset?: number;
  composer?: EffectComposer;
  touch?: ReturnType<typeof createTouchTexture>;
  liquidEffect?: Effect;
  dispose: () => void;
};

function createPixelUniforms(renderer: THREE.WebGLRenderer, config: PixelBlastConfig): PixelBlastUniforms {
  return {
    uResolution: { value: new THREE.Vector2(0, 0) },
    uTime: { value: 0 },
    uColor: { value: new THREE.Color(config.color) },
    uClickPos: { value: Array.from({ length: MAX_CLICKS }, () => new THREE.Vector2(-1, -1)) },
    uClickTimes: { value: new Float32Array(MAX_CLICKS) },
    uShapeType: { value: SHAPE_MAP[config.variant] ?? 0 },
    uPixelSize: { value: config.pixelSize * renderer.getPixelRatio() },
    uScale: { value: config.patternScale },
    uDensity: { value: config.patternDensity },
    uPixelJitter: { value: config.pixelSizeJitter },
    uEnableRipples: { value: config.enableRipples ? 1 : 0 },
    uRippleSpeed: { value: config.rippleSpeed },
    uRippleThickness: { value: config.rippleThickness },
    uRippleIntensity: { value: config.rippleIntensityScale },
    uEdgeFade: { value: config.edgeFade },
  };
}

function createPixelEffects(renderer: THREE.WebGLRenderer, scene: THREE.Scene, camera: THREE.OrthographicCamera, config: PixelBlastConfig) {
  let composer: EffectComposer | undefined;
  let touch: ReturnType<typeof createTouchTexture> | undefined;
  let liquidEffect: Effect | undefined;
  if (config.liquid) {
    touch = createTouchTexture();
    touch.radiusScale = config.liquidRadius;
    composer = new EffectComposer(renderer);
    composer.addPass(new RenderPass(scene, camera));
    liquidEffect = createLiquidEffect(touch.texture, { strength: config.liquidStrength, freq: config.liquidWobbleSpeed });
    const effectPass = new EffectPass(camera, liquidEffect);
    effectPass.renderToScreen = true;
    composer.addPass(effectPass);
  }
  if (config.noiseAmount > 0) {
    if (!composer) {
      composer = new EffectComposer(renderer);
      composer.addPass(new RenderPass(scene, camera));
    }
    const noiseEffect = new Effect(
      'NoiseEffect',
      `uniform float uTime; uniform float uAmount; float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453);} void mainUv(inout vec2 uv){} void mainImage(const in vec4 inputColor,const in vec2 uv,out vec4 outputColor){ float n=hash(floor(uv*vec2(1920.0,1080.0))+floor(uTime*60.0)); float g=(n-0.5)*uAmount; outputColor=inputColor+vec4(vec3(g),0.0);} `,
      { uniforms: new Map<string, THREE.Uniform>([['uTime', new THREE.Uniform(0)], ['uAmount', new THREE.Uniform(config.noiseAmount)]]) },
    );
    const noisePass = new EffectPass(camera, noiseEffect);
    noisePass.renderToScreen = true;
    for (const pass of composer.passes) {
      const typedPass = pass as { renderToScreen?: boolean };
      typedPass.renderToScreen = false;
    }
    composer.addPass(noisePass);
  }
  return { composer, touch, liquidEffect };
}

function hasPixelReinitConfigChanged(current: PixelBlastConfig, previous: ReinitConfig | null) {
  return current.antialias !== previous?.antialias || current.liquid !== previous?.liquid || current.noiseAmount !== previous?.noiseAmount;
}

function updatePixelBlastState(state: PixelBlastState, config: PixelBlastConfig) {
  const { uniforms, renderer } = state;
  uniforms.uShapeType.value = SHAPE_MAP[config.variant] ?? 0;
  uniforms.uPixelSize.value = config.pixelSize * renderer.getPixelRatio();
  uniforms.uColor.value.set(config.color);
  uniforms.uScale.value = config.patternScale;
  uniforms.uDensity.value = config.patternDensity;
  uniforms.uPixelJitter.value = config.pixelSizeJitter;
  uniforms.uEnableRipples.value = config.enableRipples ? 1 : 0;
  uniforms.uRippleIntensity.value = config.rippleIntensityScale;
  uniforms.uRippleThickness.value = config.rippleThickness;
  uniforms.uRippleSpeed.value = config.rippleSpeed;
  uniforms.uEdgeFade.value = config.edgeFade;
  if (config.transparent) renderer.setClearAlpha(0);
  else renderer.setClearColor(0x000000, 1);
  if (state.liquidEffect) {
    const effect = state.liquidEffect as Effect & { uniforms: Map<string, THREE.Uniform> };
    const strength = effect.uniforms.get('uStrength');
    const frequency = effect.uniforms.get('uFreq');
    if (strength) strength.value = config.liquidStrength;
    if (frequency) frequency.value = config.liquidWobbleSpeed;
  }
  if (state.touch) state.touch.radiusScale = config.liquidRadius;
}

function updatePixelFrame(state: PixelBlastState, speed: number) {
  const { timer, uniforms, liquidEffect, composer, touch, scene, camera, renderer } = state;
  timer.update();
  uniforms.uTime.value = (state.timeOffset ?? 0) + timer.getElapsed() * speed;
  if (liquidEffect) {
    const timeUniform = (liquidEffect as Effect & { uniforms: Map<string, THREE.Uniform> }).uniforms.get('uTime');
    if (timeUniform) timeUniform.value = uniforms.uTime.value;
  }
  if (composer) {
    touch?.update();
    updateEffectUniforms(composer, uniforms.uTime.value);
    composer.render();
  } else renderer.render(scene, camera);
}

function createPixelBlastState(container: HTMLDivElement, config: PixelBlastConfig, visibilityRef: { current: { visible: boolean } }, speedRef: { current: number }): PixelBlastState {
  const canvas = document.createElement('canvas');
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: config.antialias, alpha: true, powerPreference: 'high-performance' });
  renderer.domElement.style.width = '100%';
  renderer.domElement.style.height = '100%';
  renderer.setPixelRatio(Math.min(globalThis.devicePixelRatio || 1, 2));
  container.appendChild(renderer.domElement);
  if (config.transparent) renderer.setClearAlpha(0);
  else renderer.setClearColor(0x000000, 1);
  const uniforms = createPixelUniforms(renderer, config);
  const scene = new THREE.Scene();
  const camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);
  const material = new THREE.ShaderMaterial({ vertexShader: VERTEX_SRC, fragmentShader: FRAGMENT_SRC, uniforms, transparent: true, depthTest: false, depthWrite: false, glslVersion: THREE.GLSL3 });
  const quad = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), material);
  scene.add(quad);
  const timer = new THREE.Timer();
  timer.connect(document);
  const effects = createPixelEffects(renderer, scene, camera, config);
  const setSize = () => {
    const width = container.clientWidth || 1;
    const height = container.clientHeight || 1;
    renderer.setSize(width, height, false);
    uniforms.uResolution.value.set(renderer.domElement.width, renderer.domElement.height);
    effects.composer?.setSize(renderer.domElement.width, renderer.domElement.height);
    uniforms.uPixelSize.value = config.pixelSize * renderer.getPixelRatio();
  };
  setSize();
  const resizeObserver = new ResizeObserver(setSize);
  resizeObserver.observe(container);
  const timeOffset = randomFloat() * 1000;
  const state: PixelBlastState = { renderer, scene, camera, material, timer, clickIx: 0, uniforms, resizeObserver, quad, timeOffset, ...effects, dispose: () => undefined };
  const mapToPixels = (event: PointerEvent) => {
    const rect = renderer.domElement.getBoundingClientRect();
    const scaleX = renderer.domElement.width / rect.width;
    const scaleY = renderer.domElement.height / rect.height;
    return { fx: (event.clientX - rect.left) * scaleX, fy: (rect.height - (event.clientY - rect.top)) * scaleY, w: renderer.domElement.width, h: renderer.domElement.height };
  };
  const onPointerDown = (event: PointerEvent) => {
    const { fx, fy } = mapToPixels(event);
    const index = state.clickIx;
    uniforms.uClickPos.value[index].set(fx, fy);
    uniforms.uClickTimes.value[index] = uniforms.uTime.value;
    state.clickIx = (index + 1) % MAX_CLICKS;
  };
  const onPointerMove = (event: PointerEvent) => {
    if (!effects.touch) return;
    const { fx, fy, w, h } = mapToPixels(event);
    effects.touch.addTouch({ x: fx / w, y: fy / h });
  };
  renderer.domElement.addEventListener('pointerdown', onPointerDown, { passive: true });
  renderer.domElement.addEventListener('pointermove', onPointerMove, { passive: true });
  let raf = 0;
  const animate = () => {
    if (config.autoPauseOffscreen && !visibilityRef.current.visible) {
      raf = requestAnimationFrame(animate);
      state.raf = raf;
      return;
    }
    updatePixelFrame(state, speedRef.current);
    raf = requestAnimationFrame(animate);
    state.raf = raf;
  };
  raf = requestAnimationFrame(animate);
  state.raf = raf;
  state.dispose = () => {
    resizeObserver.disconnect();
    cancelAnimationFrame(state.raf ?? 0);
    renderer.domElement.removeEventListener('pointerdown', onPointerDown);
    renderer.domElement.removeEventListener('pointermove', onPointerMove);
    quad.geometry.dispose();
    material.dispose();
    effects.composer?.dispose();
    timer.dispose();
    renderer.dispose();
    renderer.forceContextLoss();
    if (renderer.domElement.parentElement === container) renderer.domElement.remove();
  };
  return state;
}

function randomFloat() {
  if (globalThis.crypto?.getRandomValues) {
    const values = new Uint32Array(1);
    globalThis.crypto.getRandomValues(values);
    return values[0] / 0xffffffff;
  }
  return Math.random();
}

const PixelBlast: React.FC<PixelBlastProps> = ({
  variant = 'square',
  pixelSize = 3,
  color = '#B497CF',
  className,
  style,
  antialias = true,
  patternScale = 2,
  patternDensity = 1,
  liquid = false,
  liquidStrength = 0.1,
  liquidRadius = 1,
  pixelSizeJitter = 0,
  enableRipples = true,
  rippleIntensityScale = 1,
  rippleThickness = 0.1,
  rippleSpeed = 0.3,
  liquidWobbleSpeed = 4.5,
  autoPauseOffscreen = true,
  speed = 0.5,
  transparent = true,
  edgeFade = 0.5,
  noiseAmount = 0
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const visibilityRef = useRef({ visible: true });
  const speedRef = useRef(speed);

  const threeRef = useRef<PixelBlastState | null>(null);
  const prevConfigRef = useRef<ReinitConfig | null>(null);
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    speedRef.current = speed;
    const config: PixelBlastConfig = {
      variant, pixelSize, color, antialias, patternScale, patternDensity, liquid,
      liquidStrength, liquidRadius, pixelSizeJitter, enableRipples, rippleIntensityScale,
      rippleThickness, rippleSpeed, liquidWobbleSpeed, autoPauseOffscreen, speed,
      transparent, edgeFade, noiseAmount,
    };
    const mustReinit = !threeRef.current || hasPixelReinitConfigChanged(config, prevConfigRef.current);
    if (mustReinit) {
      threeRef.current?.dispose();
      threeRef.current = createPixelBlastState(container, config, visibilityRef, speedRef);
    } else if (threeRef.current) {
      updatePixelBlastState(threeRef.current, config);
    }
    prevConfigRef.current = { antialias, liquid, noiseAmount };
    return () => {
      if (threeRef.current && mustReinit) return;
      threeRef.current?.dispose();
      threeRef.current = null;
    };
  }, [
    antialias,
    liquid,
    noiseAmount,
    pixelSize,
    patternScale,
    patternDensity,
    enableRipples,
    rippleIntensityScale,
    rippleThickness,
    rippleSpeed,
    pixelSizeJitter,
    edgeFade,
    transparent,
    liquidStrength,
    liquidRadius,
    liquidWobbleSpeed,
    autoPauseOffscreen,
    variant,
    color,
    speed
  ]);

  return (
    <div
      ref={containerRef}
      className={`w-full h-full relative overflow-hidden ${className ?? ''}`}
      style={style}
      aria-label="PixelBlast interactive background"
    />
  );
};

export default PixelBlast;
