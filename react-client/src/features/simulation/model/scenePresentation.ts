import type { SceneDescriptor } from "./sceneModel";

/** The readings shown first under the stage: the values the plan asks the learner to watch, else the fields the scene
    binds to its drawings (instruments, annotations) in the order the scene lists them. */
export function presentedFields(scene: SceneDescriptor, spec: Record<string, unknown> | null) {
  if (scene.observables.length) return scene.observables.map(key => ({ key, label: scene.fields[key].caption ?? scene.fields[key].label }));
  const result: Array<{ key: string; label: string }> = [];
  const seen = new Set<string>();
  for (const name of ["instruments", "annotations"]) {
    const entries = spec?.[name];
    if (!Array.isArray(entries)) continue;
    for (const entry of entries) {
      if (!entry || typeof entry.field !== "string" || !scene.fields[entry.field] || seen.has(entry.field)) continue;
      seen.add(entry.field);
      result.push({ key: entry.field, label: typeof entry.label === "string" && entry.label.trim()
        ? entry.label.trim() : scene.fields[entry.field].label });
    }
  }
  return result;
}
