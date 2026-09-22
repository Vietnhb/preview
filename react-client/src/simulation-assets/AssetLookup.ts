export type AssetKind = "actor" | "prop";

export type AssetMetadata = {
  id: string;
  family: string;
  kind: AssetKind;
  label: string;
  description: string;
  tags: readonly string[];
  aliases: readonly string[];
  file: string;
  viewBox: readonly [number, number, number, number];
  anchor: "center" | "topRight" | "right";
  effects: readonly string[];
};

/** Resolve a saved ID exactly. Explicit historical aliases never choose a variant. */
export function createAssetLookup<T extends Pick<AssetMetadata, "id" | "kind" | "aliases">>(assets: readonly T[]) {
  const byId = new Map<string, T>();
  for (const asset of assets) {
    for (const id of [asset.id, ...asset.aliases]) {
      const previous = byId.get(id);
      if (!id || (previous && previous !== asset)) throw new Error(`Duplicate or empty SVG asset ID: ${id}`);
      byId.set(id, asset);
    }
  }
  return (id: string, kind?: AssetKind): T | undefined => {
    const asset = byId.get(id);
    return asset && (!kind || asset.kind === kind) ? asset : undefined;
  };
}
