/** Semantic, deterministic selection for vector assets.
 *
 * Scene specifications can use descriptive asset hints (for example
 * `vehicle.cart.blue`) without requiring a registry entry for every spelling.
 * Selection is scored from metadata and then seeded by the actor id, so a
 * render is stable across frames while different actors can receive variants.
 */
export type AssetKind = "actor" | "prop";

export type SelectableAssetVariant = {
  id: string;
  tags: readonly string[];
  aliases?: readonly string[];
};

export type SelectableAssetDefinition = {
  id: string;
  kind: AssetKind;
  tags: readonly string[];
  aliases?: readonly string[];
  variants: readonly SelectableAssetVariant[];
};

export type AssetSelection<T extends SelectableAssetVariant = SelectableAssetVariant> = {
  definition: SelectableAssetDefinition;
  variant: T;
  score: number;
};

export function tokenizeAssetHint(value: string): string[] {
  return value.normalize("NFKD").replace(/[\u0300-\u036f]/g, "").toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);
}

function stableHash(value: string): number {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function tokenScore(hintTokens: readonly string[], values: readonly string[], exactHint: string): number {
  const normalized = values.flatMap(tokenizeAssetHint);
  let score = 0;
  for (const token of normalized) {
    if (hintTokens.includes(token)) score += 2;
  }
  if (values.some(value => value.toLowerCase() === exactHint)) score += 5;
  return score;
}

function score(definition: SelectableAssetDefinition, variant: SelectableAssetVariant, hint: string): number {
  const hintTokens = tokenizeAssetHint(hint);
  const exactHint = hint.toLowerCase();
  return tokenScore(hintTokens, [definition.id, ...definition.tags, ...(definition.aliases ?? [])], exactHint)
    + tokenScore(hintTokens, [variant.id, ...variant.tags, ...(variant.aliases ?? [])], exactHint);
}

/** Return the best semantic match, or null when no metadata matches the hint. */
export function selectAsset<T extends SelectableAssetVariant>(
  hint: string,
  kind: AssetKind,
  seed: string,
  definitions: readonly (SelectableAssetDefinition & { variants: readonly T[] })[],
): AssetSelection<T> | null {
  const candidates: AssetSelection<T>[] = [];
  for (const definition of definitions) {
    if (definition.kind !== kind) continue;
    for (const variant of definition.variants) {
      const variantScore = score(definition, variant, hint);
      if (variantScore > 0) candidates.push({ definition, variant, score: variantScore });
    }
  }
  if (candidates.length === 0) return null;
  const bestScore = Math.max(...candidates.map(candidate => candidate.score));
  const best = candidates.filter(candidate => candidate.score === bestScore)
    .sort((left, right) => left.variant.id.localeCompare(right.variant.id));
  return best[stableHash(`${seed}|${hint}`) % best.length] ?? best[0] ?? null;
}
