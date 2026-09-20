import type { ActorFrame, CanvasPalette, Point } from "../components/simulation-canvas/model";
import { assetRegistry as legacyAssets, propRegistry as legacyProps, type AssetPainter, type PropPainter } from "../components/simulation-canvas/assets";
import { selectAsset, tokenizeAssetHint } from "./AssetSelector";
import { svgAssetManifest, type SvgAssetDefinition, type SvgAssetVariant } from "./SvgAssetManifest";

/**
 * Registry for visual assets. SVG variants are selected from semantic metadata
 * first; the historical canvas painters remain a compatibility fallback for
 * old saved scene graphs and non-browser test environments.
 */
export class AssetRegistry {
  private readonly assets = new Map<string, AssetPainter>();
  private readonly props = new Map<string, PropPainter>();
  private readonly imageCache = new Map<string, CanvasImageSource>();
  private readonly svgImageCache = new Map<string, CanvasImageSource>();
  private readonly svgLoading = new Map<string, Promise<void>>();
  private readonly svgDefinitions: SvgAssetDefinition[] = [...svgAssetManifest];
  private svgGeneration = 0;

  public constructor() {
    for (const [key, painter] of Object.entries(legacyAssets)) this.assets.set(key, painter);
    for (const [key, painter] of Object.entries(legacyProps)) this.props.set(key, painter);
  }

  public registerAsset(key: string, painter: AssetPainter): void { this.assets.set(key, painter); }
  public registerProp(key: string, painter: PropPainter): void { this.props.set(key, painter); }
  public registerSvgAsset(definition: SvgAssetDefinition): void { this.svgDefinitions.push(definition); }

  public drawAsset(key: string, ctx: CanvasRenderingContext2D, actor: ActorFrame, palette: CanvasPalette): void {
    const image = this.imageCache.get(key);
    if (image) {
      this.drawImage(ctx, image, actor.position, actor.scale ?? 1, 0, palette, actor.label);
      return;
    }

    const selection = selectAsset(key, "actor", actor.config.id, this.svgDefinitions);
    if (selection) {
      const svgImage = this.svgImageCache.get(selection.variant.id);
      if (svgImage) {
        this.drawImage(ctx, svgImage, actor.position, actor.scale ?? 1, actor.rotation ?? 0, palette, actor.label, selection.variant.viewBox);
        return;
      }
      this.ensureSvgImage(selection.variant);
    }

    // Keep old specs rendering immediately while an SVG image is decoded.
    this.legacyPainterFor(key)?.(ctx, actor, palette);
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
    this.props.get(key)?.(ctx, anchor, angle, palette);
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

  /** Match an old canvas painter by tokens when an SVG is still loading. */
  private legacyPainterFor(hint: string): AssetPainter | undefined {
    const direct = this.assets.get(hint);
    if (direct) return direct;
    const hintTokens = new Set(tokenizeAssetHint(hint));
    let best: { painter: AssetPainter; score: number; key: string } | undefined;
    for (const [key, painter] of this.assets) {
      const score = tokenizeAssetHint(key).filter(token => hintTokens.has(token)).length;
      if (score > 0 && (!best || score > best.score || (score === best.score && key < best.key))) best = { painter, score, key };
    }
    return best?.painter ?? this.assets.get("object.block.amber");
  }

  /** Optional image assets can be registered and preloaded without entering the RAF loop. */
  public async preload(urls: Record<string, string>): Promise<void> {
    await Promise.all(Object.entries(urls).map(async ([key, url]) => {
      if (this.imageCache.has(key)) return;
      if (typeof globalThis.createImageBitmap === "function") {
        const response = await fetch(url);
        this.imageCache.set(key, await globalThis.createImageBitmap(await response.blob()));
        return;
      }
      const image = new Image(); image.src = url; await image.decode(); this.imageCache.set(key, image);
    }));
  }

  public image(key: string): CanvasImageSource | undefined { return this.imageCache.get(key) ?? this.svgImageCache.get(key); }
  public clear(): void {
    for (const image of this.imageCache.values()) {
      if ("close" in image && typeof image.close === "function") image.close();
    }
    this.imageCache.clear();
    this.svgImageCache.clear();
    this.svgLoading.clear();
    this.svgGeneration += 1;
  }
}

export const canvasAssetRegistry = new AssetRegistry();
