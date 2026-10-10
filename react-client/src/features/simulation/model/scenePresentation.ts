import type { SceneDescriptor } from "./sceneModel";

/** The readings shown first under the stage: the values the plan asks the learner to watch, else the fields the scene
    binds to its drawings (parts and notes; instruments and annotations in older scenes) in the order the scene lists them. */
export function presentedFields(scene: SceneDescriptor, spec: Record<string, unknown> | null) {
  if (scene.observables?.length) return scene.observables.map(key => ({ key, label: scene.fields[key].caption ?? scene.fields[key].label }));
  const result: Array<{ key: string; label: string }> = [];
  const seen = new Set<string>();
  const add = (field: unknown, label: unknown) => {
    if (typeof field !== "string" || !scene.fields[field] || seen.has(field)) return;
    seen.add(field);
    result.push({ key: field, label: typeof label === "string" && label.trim() ? label.trim() : scene.fields[field].label });
  };
  const list = (value: unknown): Array<Record<string, unknown> | null> => Array.isArray(value) ? value : [];
  for (const drawing of list(spec?.drawings)) {
    for (const part of list(drawing?.parts)) {
      const channels = list(part?.channels);
      /* a part's name belongs to its value only when it follows one */
      for (const channel of channels) add(channel?.field, channels.length === 1 ? part?.label : "");
    }
    for (const note of list(drawing?.notes)) add(note?.field, note?.label);
  }
  for (const entry of [...list(spec?.instruments), ...list(spec?.annotations)]) add(entry?.field, entry?.label);
  return result;
}
