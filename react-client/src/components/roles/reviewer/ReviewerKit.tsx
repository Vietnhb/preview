import { Badge, Button, Card, Heading, Select, Text, TextField } from "@radix-ui/themes";
import type { ComponentProps, ReactNode } from "react";
import { reviewerStatusLabel } from "./reviewerUtils";

const PATHS = {
  queue: "M4 5h16v14H4z M8 9h8 M8 13h5",
  schema: "M4 4h7v6H4z M14 4h6v6h-6z M4 14h7v6H4z M14 14h6v6h-6z",
  solver: "M5 4h14v16H5z M8 8h8 M8 12h2 M14 12h2 M8 16h2 M14 16h2",
  module: "M4 5h7l2 2h7v13H4z M8 13l3 3 5-6",
  benchmark: "M4 20h16 M7 16V9 M12 16V4 M17 16v-5",
  library: "M4 4h5v16H4z M9 6h5v14H9z M15 5l4-1 3 15-4 1z",
  search: "M21 21l-5-5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0",
  refresh: "M20 7v5h-5 M4 17v-5h5 M5 8a8 8 0 0 1 13-3l2 3 M19 16a8 8 0 0 1-13 3l-2-3",
  check: "M5 12l4 4L19 6",
  clock: "M12 8v5l3 2 M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0",
  arrow: "M5 12h14 M13 6l6 6-6 6",
  add: "M12 5v14 M5 12h14",
  close: "M6 6l12 12 M18 6 6 18",
};
export type ReviewerIconName = keyof typeof PATHS;

export function ReviewerIcon({ name, size = 20 }: Readonly<{ name: ReviewerIconName; size?: number }>) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={PATHS[name]} /></svg>;
}

export function ReviewerHeader({ title, icon, count, actions }: Readonly<{ title: string; icon: ReviewerIconName; count?: number; actions?: ReactNode }>) {
  return <header className="reviewer-panel-heading"><div className="reviewer-title-group"><span className={`reviewer-icon-tile reviewer-icon-${icon}`}><ReviewerIcon name={icon} size={23} /></span><Heading as="h1" size="6">{title}</Heading>{count !== undefined && <Badge size="2" variant="soft">{count}</Badge>}</div>{actions && <div className="reviewer-actions">{actions}</div>}</header>;
}

export function ReviewerSearch(props: ComponentProps<typeof TextField.Root>) {
  return <TextField.Root size="3" type="search" className="reviewer-search" {...props}><TextField.Slot><ReviewerIcon name="search" size={18} /></TextField.Slot></TextField.Root>;
}

export function ReviewerFilter({ label, value, onChange, statuses }: Readonly<{ label: string; value: string; onChange: (value: string) => void; statuses: string[] }>) {
  return <Select.Root size="3" value={value || "ALL"} onValueChange={(next) => onChange(next === "ALL" ? "" : next)}><Select.Trigger aria-label={label} className="reviewer-filter" /><Select.Content><Select.Item value="ALL">Tất cả trạng thái</Select.Item><Select.Separator />{statuses.map((status) => <Select.Item key={status} value={status}>{reviewerStatusLabel(status)}</Select.Item>)}</Select.Content></Select.Root>;
}

export function ReviewerFormSelect({ name, label, defaultValue, required, options, placeholder = "Chọn giá trị" }: Readonly<{ name: string; label: string; defaultValue?: string; required?: boolean; options: { value: string; label: string }[]; placeholder?: string }>) {
  return <Select.Root size="3" name={name} defaultValue={defaultValue || undefined} required={required}><Select.Trigger aria-label={label} placeholder={placeholder} className="reviewer-field-select" /><Select.Content>{options.map((option) => <Select.Item key={option.value} value={option.value}>{option.label}</Select.Item>)}</Select.Content></Select.Root>;
}

export function ReviewStatus({ status }: Readonly<{ status: string }>) {
  const color = ["APPROVED", "FEATURED", "GOLD_READY", "COMPLETED", "SUCCESS", "SUCCEEDED"].includes(status) ? "cyan" : ["RETIRED", "REJECTED", "REMOVED", "FAILED", "ARCHIVED"].includes(status) ? "gray" : ["PENDING", "DRAFT", "DISAGREEMENT"].includes(status) ? "amber" : "indigo";
  return <Badge color={color} variant="soft" size="2">{reviewerStatusLabel(status)}</Badge>;
}

export function ReviewerMetric({ label, value, icon, tone }: Readonly<{ label: string; value: ReactNode; icon: ReviewerIconName; tone: "indigo" | "cyan" | "amber" }>) {
  return <Card size="2" className="reviewer-metric"><span className={`reviewer-icon-tile reviewer-tone-${tone}`}><ReviewerIcon name={icon} /></span><div><Text as="div" size="2" color="gray">{label}</Text><Heading as="h3" size="6" mt="1">{value}</Heading></div></Card>;
}

export function ReviewerRefresh({ refresh, loading }: Readonly<{ refresh: () => void; loading: boolean }>) {
  return <Button type="button" variant="soft" color="gray" size="2" onClick={refresh} disabled={loading}><ReviewerIcon name="refresh" size={16} />Làm mới</Button>;
}
