import { useEffect, useState } from "react";
import api from "../../../shared/api/client";
import type { Version } from "./reviewerTypes";

/** Vietnamese names for topic codes (KINEMATICS) and quantity keys (initial_velocity),
 *  read from the topic catalogue every signed-in role may read. */
export type ReviewerCatalog = { topicName: (code?: string | null) => string; quantityLabel: (key?: string | null) => string; humanize: (text?: string | null) => string };

type Maps = { topics: Map<string, string>; quantities: Map<string, string> };
let cache: Promise<Maps> | null = null;

function load(): Promise<Maps> {
  cache ??= api.get<Version[]>("/schemas").then(response => {
    const topics = new Map<string, string>();
    const quantities = new Map<string, string>();
    const ordered = [...response.data].sort((a, b) => Number(b.lifecycleStatus === "APPROVED") - Number(a.lifecycleStatus === "APPROVED"));
    for (const schema of ordered) {
      if (schema.topic && schema.name && !topics.has(schema.topic)) topics.set(schema.topic, schema.name);
      const definition = schema.definition as { quantityDefinitions?: { key?: string; label?: string }[] } | undefined;
      for (const quantity of definition?.quantityDefinitions ?? []) {
        if (quantity.key && quantity.label && !quantities.has(quantity.key)) quantities.set(quantity.key, quantity.label);
      }
    }
    return { topics, quantities };
  }).catch(() => { cache = null; return { topics: new Map(), quantities: new Map() }; });
  return cache;
}

const prettyKey = (key: string) => key.replace(/_/g, " ");

export function useReviewerCatalog(): ReviewerCatalog {
  const [maps, setMaps] = useState<Maps | null>(null);
  useEffect(() => { let active = true; void load().then(value => { if (active) setMaps(value); }); return () => { active = false; }; }, []);
  const quantityLabel = (key?: string | null) => !key ? "" : maps?.quantities.get(key) ?? prettyKey(key);
  return {
    topicName: code => !code ? "" : maps?.topics.get(code) ?? code,
    quantityLabel,
    // Replace snake_case quantity keys inside AI-generated sentences, e.g. "đại lượng initial_position".
    humanize: text => !text ? "" : text.replace(/\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\b/g, key => {
      const label = maps?.quantities.get(key);
      return label ? `“${label}”` : `“${prettyKey(key)}”`;
    }),
  };
}
