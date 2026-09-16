/**
 * Compatibility entry point for the canvas component. Rendering now lives in
 * the generic SceneGraph -> CanvasRenderer pipeline.
 */
import { CanvasRenderer, type CanvasRenderFrame } from "../../simulation-renderer/CanvasRenderer";

const renderer = new CanvasRenderer();

export function renderScene(frame: CanvasRenderFrame): boolean {
  renderer.render(frame);
  return true;
}
