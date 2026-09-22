import type { ActorFrame, CanvasPalette, Point } from "../components/simulation-canvas/model";
import { selectAsset } from "./AssetSelector";
import { svgAssetManifest, type SvgAssetDefinition, type SvgAssetVariant } from "./SvgAssetManifest";

/**
 * Registry for visual assets. SVG variants are selected from semantic metadata
 * first. There is deliberately no generic painter fallback: an unrecognised
 * asset is a contract error and must be resolved/confirmed before rendering.
 */
export class AssetRegistry {
  private readonly svgImageCache = new Map<string, CanvasImageSource>();
  private readonly svgLoading = new Map<string, Promise<void>>();
  private readonly svgDefinitions: SvgAssetDefinition[] = [...svgAssetManifest];
  private svgGeneration = 0;

  public registerSvgAsset(definition: SvgAssetDefinition): void { this.svgDefinitions.push(definition); }

  public hasAsset(hint: string, kind: "actor" | "prop"): boolean {
    return Boolean(hint.trim() && selectAsset(hint, kind, hint, this.svgDefinitions));
  }

  public drawAsset(key: string, ctx: CanvasRenderingContext2D, actor: ActorFrame, palette: CanvasPalette): void {
    const selection = selectAsset(key, "actor", actor.config.id, this.svgDefinitions);
    if (selection) {
      const svgImage = this.svgImageCache.get(selection.variant.id);
      if (svgImage) {
        this.drawImage(ctx, svgImage, actor.position, actor.scale ?? 1, actor.rotation ?? 0, palette, actor.label, selection.variant.viewBox);
        return;
      }
      this.ensureSvgImage(selection.variant);
    }

    // Do not silently substitute a different physical object. The scene
    // validator reports unresolved hints before playback is enabled.
  }

  public drawProp(key: string, ctx: CanvasRenderingContext2D, anchor: Point, angle: number, palette: CanvasPalette): void {
    const selection = selectAsset(key, "prop", key, this.svgDefinitions);
    if (selection) {
      const svgImage = this.svgImageCache.get(selection.variant.id);
      if (svgImage) {
        const [,, width, height] = selection.variant.viewBox;
        const position = selection.variant.anchor === "topRight"
          ? { x: anchor.x - width / 2, y: anchor.y + height / 2 }
          : selection.variant.anchor === "right" ? { x: anchor.x - width / 2, y: anchor.y } : anchor;
        this.drawImage(ctx, svgImage, position, 1, angle, palette, undefined, selection.variant.viewBox);
        return;
      }
      this.ensureSvgImage(selection.variant);
    }
    // Do not silently substitute a different apparatus when a hint is absent.
  }

  private drawImage(
    ctx: CanvasRenderingContext2D,
    image: CanvasImageSource,
    position: Point,
    scale: number,
    rotation: number,
    palette: CanvasPalette,
    label?: string,
    viewBox?: readonly [number, number, number, number],
  ): void {
    const sizedImage = image as CanvasImageSource & { width?: number; height?: number };
    const width = (viewBox?.[2] ?? sizedImage.width ?? 56) * scale;
    const height = (viewBox?.[3] ?? sizedImage.height ?? 56) * scale;
    ctx.save();
    ctx.translate(position.x, position.y);
    if (rotation) ctx.rotate(rotation);
    ctx.drawImage(image, -width / 2, -height / 2, width, height);
    if (label) {
      ctx.fillStyle = palette.text;
      ctx.font = "800 12px Inter, sans-serif";
      ctx.textAlign = "center";
      ctx.fillText(label, 0, 4);
    }
    ctx.restore();
  }

  private ensureSvgImage(variant: SvgAssetVariant): void {
    if (this.svgImageCache.has(variant.id) || this.svgLoading.has(variant.id) || typeof Image === "undefined") return;
    const generation = this.svgGeneration;
    const image = new Image();
    const source = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(variant.markup)}`;
    const loading = new Promise<void>(resolve => {
      image.onload = () => {
        if (generation === this.svgGeneration) this.svgImageCache.set(variant.id, image);
        resolve();
      };
      image.onerror = () => resolve();
      image.src = source;
    });
    this.svgLoading.set(variant.id, loading);
  }

  public clear(): void {
    this.svgImageCache.clear();
    this.svgLoading.clear();
    this.svgGeneration += 1;
  }
}

export const canvasAssetRegistry = new AssetRegistry();
