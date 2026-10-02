import { useEffect, useRef } from "react";

const VERTEX = `#version 300 es
in vec2 position;
void main() { gl_Position = vec4(position, 0.0, 1.0); }`;

// Flowing aurora bands: layered simplex noise mapped onto three brand colours.
const FRAGMENT = `#version 300 es
precision highp float;
uniform float uTime;
uniform vec2 uResolution;
uniform vec3 uColorA;
uniform vec3 uColorB;
uniform vec3 uColorC;
uniform float uIntensity;
out vec4 fragColor;

vec3 permute(vec3 x) { return mod(((x * 34.0) + 1.0) * x, 289.0); }
float snoise(vec2 v) {
  const vec4 C = vec4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);
  vec2 i = floor(v + dot(v, C.yy));
  vec2 x0 = v - i + dot(i, C.xx);
  vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
  vec4 x12 = x0.xyxy + C.xxzz;
  x12.xy -= i1;
  i = mod(i, 289.0);
  vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0)) + i.x + vec3(0.0, i1.x, 1.0));
  vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
  m = m * m; m = m * m;
  vec3 x = 2.0 * fract(p * C.www) - 1.0;
  vec3 h = abs(x) - 0.5;
  vec3 ox = floor(x + 0.5);
  vec3 a0 = x - ox;
  m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
  vec3 g;
  g.x = a0.x * x0.x + h.x * x0.y;
  g.yz = a0.yz * x12.xz + h.yz * x12.yw;
  return 130.0 * dot(m, g);
}

void main() {
  vec2 uv = gl_FragCoord.xy / uResolution;
  float t = uTime * 0.08;
  float n1 = snoise(vec2(uv.x * 1.6 + t, t * 0.6)) * 0.5 + 0.5;
  float n2 = snoise(vec2(uv.x * 2.4 - t * 1.3, uv.y * 0.6 + t)) * 0.5 + 0.5;
  vec3 ramp = mix(uColorA, uColorB, smoothstep(0.0, 0.6, uv.x + (n1 - 0.5) * 0.6));
  ramp = mix(ramp, uColorC, smoothstep(0.55, 1.0, uv.x + (n2 - 0.5) * 0.5));
  float height = 0.42 + n1 * 0.38;
  float band = smoothstep(height - 0.55, height, uv.y) * (1.0 - smoothstep(height, height + 0.35, uv.y));
  float glow = band * (0.55 + n2 * 0.6) * uIntensity;
  fragColor = vec4(ramp * glow, glow);
}`;

function rgb(hex: string): [number, number, number] {
  const n = Number.parseInt(hex.replace("#", ""), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

/**
 * Animated aurora background drawn with a single WebGL2 fragment shader (no
 * library). Falls back to the CSS gradient on the wrapper when WebGL is not
 * available, pauses when off-screen, and renders a still frame for reduced motion.
 */
const DEFAULT_COLORS: [string, string, string] = ["#2563eb", "#22d3ee", "#8b5cf6"];

export default function Aurora({
  colors = DEFAULT_COLORS,
  intensity = 1,
  className = "",
}: Readonly<{ colors?: [string, string, string]; intensity?: number; className?: string }>) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const gl = canvas?.getContext("webgl2", { premultipliedAlpha: true, alpha: true, antialias: false });
    if (!canvas || !gl) return;
    const reduced = globalThis.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
    const compile = (type: number, source: string) => {
      const shader = gl.createShader(type)!;
      gl.shaderSource(shader, source);
      gl.compileShader(shader);
      return shader;
    };
    const program = gl.createProgram()!;
    gl.attachShader(program, compile(gl.VERTEX_SHADER, VERTEX));
    gl.attachShader(program, compile(gl.FRAGMENT_SHADER, FRAGMENT));
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) return;
    gl.useProgram(program);
    const buffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
    const position = gl.getAttribLocation(program, "position");
    gl.enableVertexAttribArray(position);
    gl.vertexAttribPointer(position, 2, gl.FLOAT, false, 0, 0);
    const uTime = gl.getUniformLocation(program, "uTime");
    const uResolution = gl.getUniformLocation(program, "uResolution");
    gl.uniform3fv(gl.getUniformLocation(program, "uColorA"), rgb(colors[0]));
    gl.uniform3fv(gl.getUniformLocation(program, "uColorB"), rgb(colors[1]));
    gl.uniform3fv(gl.getUniformLocation(program, "uColorC"), rgb(colors[2]));
    gl.uniform1f(gl.getUniformLocation(program, "uIntensity"), intensity);
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
    canvas.dataset.ready = "true";

    let frame = 0;
    let visible = true;
    const start = performance.now();
    const resize = () => {
      const ratio = Math.min(globalThis.devicePixelRatio || 1, 1.5) * 0.6; // soft shader: render below native resolution
      canvas.width = Math.max(1, Math.round(canvas.clientWidth * ratio));
      canvas.height = Math.max(1, Math.round(canvas.clientHeight * ratio));
      gl.viewport(0, 0, canvas.width, canvas.height);
      gl.uniform2f(uResolution, canvas.width, canvas.height);
    };
    const draw = (now: number) => {
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT);
      gl.uniform1f(uTime, reduced ? 12 : (now - start) / 1000 + 12);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      if (!reduced && visible) frame = requestAnimationFrame(draw);
    };
    resize();
    const observer = new ResizeObserver(() => { resize(); if (reduced) draw(start); });
    observer.observe(canvas);
    const intersection = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting;
      cancelAnimationFrame(frame);
      if (visible) frame = requestAnimationFrame(draw);
    });
    intersection.observe(canvas);
    frame = requestAnimationFrame(draw);
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
      intersection.disconnect();
      gl.deleteProgram(program);
      gl.deleteBuffer(buffer);
    };
  }, [colors, intensity]);

  return <canvas ref={canvasRef} className={`aurora ${className}`} aria-hidden="true" />;
}
