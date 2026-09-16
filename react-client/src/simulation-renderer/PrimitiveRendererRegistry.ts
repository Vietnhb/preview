import type { BindingResolver } from "../simulation-scene/BindingResolver";
import type { SceneNode } from "../simulation-scene/SceneGraph";
import type { CanvasRenderFrame } from "./CanvasRenderer";

export type PrimitiveRenderInput = {
  frame: CanvasRenderFrame;
  node: SceneNode;
  layout: {
    mode: string;
    width: number;
    height: number;
    baseline: number;
    xMin: number;
    xMax: number;
    yMax: number;
    left: number;
    right: number;
    mapX: (value: number) => number;
    mapPoint: (value: number, y: number, lane: number) => { x: number; y: number };
  };
  resolver: BindingResolver;
  nodes: SceneNode[];
};

export type PrimitiveRenderer = (input: PrimitiveRenderInput) => void;

/** Registry of engine capabilities; AI specs select these by node.type. */
export class PrimitiveRendererRegistry {
  private readonly renderers = new Map<string, PrimitiveRenderer>();

  public register(type: string, renderer: PrimitiveRenderer): void { this.renderers.set(type, renderer); }
  public has(type: string): boolean { return this.renderers.has(type); }
  public draw(type: string, input: PrimitiveRenderInput): boolean {
    const renderer = this.renderers.get(type);
    if (!renderer) return false;
    renderer(input);
    return true;
  }
}

export const primitiveRenderers = new PrimitiveRendererRegistry();

