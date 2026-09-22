import catalog from "../../../backend/src/main/resources/assets/svg-catalog.json";
import { createAssetLookup, type AssetMetadata } from "./AssetLookup";

export type SvgAsset = AssetMetadata & { markup: string };

const sources = import.meta.glob<string>("./svg/*.svg", { query: "?raw", import: "default", eager: true });

/** The backend and renderer share the same catalog; only approved local SVG files are bundled. */
export const svgAssetManifest: readonly SvgAsset[] = catalog.assets.map((asset): SvgAsset => {
  const { kind, anchor, viewBox } = asset;
  if ((kind !== "actor" && kind !== "prop") || (anchor !== "center" && anchor !== "right" && anchor !== "topRight")
    || viewBox.length !== 4 || !viewBox.every(Number.isFinite) || viewBox[2] <= 0 || viewBox[3] <= 0) {
    throw new Error(`Invalid SVG catalog metadata: ${asset.id}`);
  }
  const markup = sources[`./svg/${asset.file}`];
  if (!markup) throw new Error(`Missing SVG file for catalog asset: ${asset.id}`);
  return { ...asset, kind, anchor, viewBox: [viewBox[0], viewBox[1], viewBox[2], viewBox[3]], markup };
});

export const getSvgAsset = createAssetLookup(svgAssetManifest);
