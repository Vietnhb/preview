/**
 * Who a slider belongs to, derived only from the signed plan's bindings (never from names or topics):
 *  - a parameter bound by exactly one participant is that participant's own;
 *  - a parameter bound by several participants (or by none, e.g. the duration) is common to the scene;
 *  - own parameters of different participants that feed the SAME input of the SAME law are "peers":
 *    the same property of like objects, which the interface can change together or one by one.
 */
export type GroupedParameter = { name: string };
export type PlanModel = { id: string; label?: string; capabilityId: string; inputs: Record<string, string | number> };
export type ParameterGroup<P extends GroupedParameter> = { id: string; label: string; parameters: P[] };
export type ParameterGrouping<P extends GroupedParameter> = {
  shared: P[];
  groups: ParameterGroup<P>[];
  /** parameter name → names of all its peers (itself included, plan order); only families of two or more. */
  peers: Record<string, string[]>;
};

export function groupParameters<P extends GroupedParameter>(parameters: readonly P[], models: readonly PlanModel[] = []): ParameterGrouping<P> {
  const owners = new Map<string, Array<{ model: PlanModel; key: string }>>();
  for (const model of models) for (const [key, bound] of Object.entries(model.inputs ?? {})) {
    if (typeof bound !== "string") continue;
    const list = owners.get(bound) ?? [];
    if (!list.some(entry => entry.model.id === model.id)) list.push({ model, key });
    owners.set(bound, list);
  }
  const shared: P[] = [], byModel = new Map<string, P[]>(), families = new Map<string, string[]>();
  for (const parameter of parameters) {
    const bound = owners.get(parameter.name) ?? [];
    if (bound.length !== 1) { shared.push(parameter); continue; }
    const { model, key } = bound[0];
    byModel.set(model.id, [...(byModel.get(model.id) ?? []), parameter]);
    const family = model.capabilityId + "\u0000" + key;
    families.set(family, [...(families.get(family) ?? []), parameter.name]);
  }
  const peers: Record<string, string[]> = {};
  for (const names of families.values()) if (names.length > 1) for (const name of names) peers[name] = names;
  const groups = models.filter(model => byModel.has(model.id))
    .map(model => ({ id: model.id, label: model.label?.trim() || model.id, parameters: byModel.get(model.id)! }));
  return { shared, groups, peers };
}

/** True when every peer family currently holds one common value (like objects still set alike). */
export function peersAligned(peers: Record<string, string[]>, values: Record<string, number>, fallback: (name: string) => number) {
  const value = (name: string) => values[name] ?? fallback(name);
  return Object.values(peers).every(names => names.every(name => value(name) === value(names[0])));
}
