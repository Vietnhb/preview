import type { VectorScene, VectorShape } from '../simulation-scene/VectorScene';
import type { VisualizationBinding } from '../types/physlive';
import type { PrimitiveRenderInput } from './PrimitiveRendererRegistry';

/** SVG-style geometry in an explicit coordinate system, independent of lesson IDs. */
export function drawVectorScene({ frame, node, resolver }: PrimitiveRenderInput): void {
  const scene = node.properties.vector as VectorScene;
  if (!scene?.viewBox || !scene.shapes) return;
  const ctx = frame.ctx;
  const [x, y, width, height] = scene.viewBox;
  const scale = Math.min(frame.width / width, frame.height / height);
  const resolve = (value: VisualizationBinding | undefined, fallback = 0) => {
    if (value === undefined) return fallback;
    const result = resolver.resolve(value, frame.runtime.time, frame.runtime.index);
    return Number.isFinite(result) ? result : fallback;
  };
  function draw(shape: VectorShape): void {
    ctx.save();
    ctx.translate(resolve(shape.x), resolve(shape.y));
    ctx.rotate(resolve(shape.rotation));
    ctx.scale(resolve(shape.scaleX, 1), resolve(shape.scaleY, 1));
    ctx.globalAlpha *= Math.max(0, Math.min(1, resolve(shape.opacity, 1)));
    ctx.fillStyle = shape.fill ?? 'transparent';
    ctx.strokeStyle = shape.stroke ?? 'transparent';
    ctx.lineWidth = Math.max(0.001, resolve(shape.lineWidth, 1));
    if (shape.kind === 'text') {
      ctx.font = `${Math.max(1, resolve(shape.fontSize, 14))}px sans-serif`;
      ctx.fillText((shape.text ?? '').replace('{value}', shape.value === undefined ? '' : String(resolve(shape.value))), 0, 0);
    } else if (shape.kind !== 'group') {
      ctx.beginPath();
      if (shape.kind === 'rect') ctx.rect(0, 0, resolve(shape.width), resolve(shape.height));
      if (shape.kind === 'ellipse') ctx.ellipse(0, 0, Math.max(0, resolve(shape.radiusX)), Math.max(0, resolve(shape.radiusY)), 0, 0, Math.PI * 2);
      for (const command of shape.commands ?? []) {
        const a = command.args.map(arg => resolve(arg));
        switch (command.op) {
          case 'M': ctx.moveTo(a[0], a[1]); break;
          case 'L': ctx.lineTo(a[0], a[1]); break;
          case 'Q': ctx.quadraticCurveTo(a[0], a[1], a[2], a[3]); break;
          case 'C': ctx.bezierCurveTo(a[0], a[1], a[2], a[3], a[4], a[5]); break;
          case 'Z': ctx.closePath(); break;
        }
      }
      if (shape.fill && shape.fill !== 'none') ctx.fill();
      if (shape.stroke && shape.stroke !== 'none') ctx.stroke();
    }
    for (const child of shape.children ?? []) draw(child);
    ctx.restore();
  }
  ctx.save();
  ctx.translate((frame.width - width * scale) / 2, (frame.height - height * scale) / 2);
  ctx.scale(scale, scale);
  ctx.translate(-x, -y);
  for (const shape of scene.shapes) draw(shape);
  ctx.restore();
}
