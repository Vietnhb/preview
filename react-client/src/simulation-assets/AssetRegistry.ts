import type { ActorFrame, CanvasPalette, Point } from "../components/simulation-canvas/model";
import { assetRegistry as legacyAssets, propRegistry as legacyProps, type AssetPainter, type PropPainter } from "../components/simulation-canvas/assets";

export class AssetRegistry {
  private readonly assets = new Map<string, AssetPainter>();
  private readonly props = new Map<string, PropPainter>();
  private readonly imageCache = new Map<string, CanvasImageSource>();

  public constructor() {
    for (const [key, painter] of Object.entries(legacyAssets)) this.assets.set(key, painter);
    for (const [key, painter] of Object.entries(legacyProps)) this.props.set(key, painter);
  }

  public registerAsset(key: string, painter: AssetPainter): void { this.assets.set(key, painter); }
  public registerProp(key: string, painter: PropPainter): void { this.props.set(key, painter); }

  public drawAsset(key: string, ctx: CanvasRenderingContext2D, actor: ActorFrame, palette: CanvasPalette): void {
    const image = this.imageCache.get(key);
    if (image) {
      const sizedImage = image as CanvasImageSource & { width?: number; height?: number };
      const width = (sizedImage.width ?? 56) * (actor.scale ?? 1);
      const height = (sizedImage.height ?? 56) * (actor.scale ?? 1);
      ctx.drawImage(image, actor.position.x - width / 2, actor.position.y - height / 2, width, height);
      return;
    }
    (this.assets.get(key) ?? this.assets.get("object.block.amber"))?.(ctx, actor, palette);
  }

  public drawProp(key: string, ctx: CanvasRenderingContext2D, anchor: Point, angle: number, palette: CanvasPalette): void {
    this.props.get(key)?.(ctx, anchor, angle, palette);
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

  public image(key: string): CanvasImageSource | undefined { return this.imageCache.get(key); }
  public clear(): void {
    for (const image of this.imageCache.values()) {
      if ("close" in image && typeof image.close === "function") image.close();
    }
    this.imageCache.clear();
  }
}

export const canvasAssetRegistry = new AssetRegistry();
