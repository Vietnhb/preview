import type { VisualizationBinding } from '../types/physlive';

export type VectorShape = {
  kind: 'path' | 'ellipse' | 'rect' | 'text' | 'group';
  x?: VisualizationBinding;
  y?: VisualizationBinding;
  rotation?: VisualizationBinding;
  scaleX?: VisualizationBinding;
  scaleY?: VisualizationBinding;
  width?: VisualizationBinding;
  height?: VisualizationBinding;
  radiusX?: VisualizationBinding;
  radiusY?: VisualizationBinding;
  opacity?: VisualizationBinding;
  fill?: string;
  stroke?: string;
  lineWidth?: VisualizationBinding;
  fontSize?: VisualizationBinding;
  text?: string;
  value?: VisualizationBinding;
  commands?: { op: 'M' | 'L' | 'Q' | 'C' | 'Z'; args: VisualizationBinding[] }[];
  children?: VectorShape[];
};

export type VectorScene = { viewBox: [number, number, number, number]; shapes: VectorShape[] };

const arity: Record<string, number> = { M: 2, L: 2, Q: 4, C: 6, Z: 0 };
const numeric = ['x', 'y', 'rotation', 'scaleX', 'scaleY', 'width', 'height', 'radiusX', 'radiusY', 'opacity', 'lineWidth', 'fontSize', 'value'];

/** Validate the drawing language before calling Canvas. No executable SVG/JS. */
export function validateVectorScene(value: unknown, bindingCheck: (value: unknown) => string | undefined): string[] {
  const errors: string[] = [];
  if (!value || typeof value !== 'object') return ['vector scene is required'];
  const scene = value as Record<string, unknown>;
  if (!Array.isArray(scene.viewBox) || scene.viewBox.length !== 4 || !scene.viewBox.every(Number.isFinite)
    || scene.viewBox[2] <= 0 || scene.viewBox[3] <= 0) errors.push('viewBox requires finite x,y and positive width,height');
  let count = 0;
  let commandCount = 0;
  function visit(items: unknown, depth: number): void {
    if (!Array.isArray(items)) { errors.push('shapes/children must be arrays'); return; }
    if (depth > 32 || items.length > 2000) { errors.push('vector scene resource limit exceeded'); return; }
    for (const item of items) {
      if (++count > 2000) { errors.push('vector scene resource limit exceeded'); return; }
      if (!item || typeof item !== 'object') { errors.push('invalid shape'); continue; }
      if (!['path', 'ellipse', 'rect', 'text', 'group'].includes(item.kind)) errors.push(`unsupported shape ${item.kind}`);
      for (const key of numeric) if (item[key] !== undefined) {
        const error = bindingCheck(item[key]);
        if (error) errors.push(`${key}: ${error}`);
      }
      for (const key of ['fill', 'stroke', 'text']) if (item[key] !== undefined && typeof item[key] !== 'string') errors.push(`${key} must be a string`);
      if (item.kind === 'path') {
        if (!Array.isArray(item.commands) || item.commands.length > 4096) errors.push('path requires at most 4096 commands');
        else {
          commandCount += item.commands.length;
          if (commandCount > 20_000) { errors.push('vector path resource limit exceeded'); return; }
          for (const command of item.commands) {
          if (!command || !Object.hasOwn(arity, command.op) || !Array.isArray(command.args) || command.args.length !== arity[command.op]) { errors.push('invalid path command'); continue; }
          for (const arg of command.args) { const error = bindingCheck(arg); if (error) errors.push(`path: ${error}`); }
          }
        }
      }
      if (item.children !== undefined) visit(item.children, depth + 1);
    }
  }
  visit(scene.shapes, 0);
  return errors;
}
