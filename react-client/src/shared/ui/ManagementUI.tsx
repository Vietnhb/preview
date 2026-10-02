import { type ReactNode } from "react";
import { Button as ThemeButton, Callout, Card, Heading, Spinner, Text } from "@radix-ui/themes";
import { motion, useReducedMotion } from "motion/react";
import LearningIcon from "./LearningIcon";

type IconName = "grid" | "users" | "book" | "activity" | "shield" | "refresh" | "plus" | "check" | "search" | "close";

export function PageHeader({ title, action }: Readonly<{ title: string; description: string; action?: ReactNode }>) {
  return <header className="admin-content-header"><div><Heading as="h1" size="7" className="admin-content-title">{title}</Heading></div>{action}</header>;
}

export function Panel({ title, description, children, action, className }: Readonly<{ title: string; description?: string; children: ReactNode; action?: ReactNode; className?: string }>) {
  return <Card asChild size="3" className={`admin-panel ${className ?? ""}`}><section><div className="admin-panel-heading"><div><Heading as="h2" size="4">{title}</Heading>{description && <Text as="p" color="gray" size="2" className="admin-panel-description">{description}</Text>}</div>{action}</div>{children}</section></Card>;
}

export function Loading({ text = "Đang tải dữ liệu…" }: Readonly<{ text?: string }>) { return <div className="admin-loading compact" role="status"><Spinner size="3" /><Text as="p" color="gray">{text}</Text></div>; }

export function ErrorNotice({ error, onRetry }: Readonly<{ error: string; onRetry?: () => void }>) { return <Callout.Root color="red" className="admin-error-banner" role="alert"><Callout.Text>{error}</Callout.Text>{onRetry && <ThemeButton type="button" variant="ghost" color="red" onClick={onRetry}>Thử lại</ThemeButton>}</Callout.Root>; }

export function Button({ children, onClick, primary = false, disabled = false, type = "button" }: Readonly<{ children: ReactNode; onClick?: () => void; primary?: boolean; disabled?: boolean; type?: "button" | "submit" }>) { return <ThemeButton type={type} variant={primary ? "solid" : "surface"} size="2" onClick={onClick} disabled={disabled}>{children}</ThemeButton>; }

export function FormField({ label, htmlFor, children }: Readonly<{ label: string; htmlFor: string; children: ReactNode }>) { return <div style={{ display: "grid", gap: 8 }}><Text as="label" size="2" weight="medium" htmlFor={htmlFor}>{label}</Text>{children}</div>; }

export function Stat({ label, value, icon, tone, note }: Readonly<{ label: string; value: string | number; icon: IconName; tone: string; note?: string }>) {
  const reducedMotion = useReducedMotion();
  return <Card asChild size="3" className="admin-stat-card"><motion.article initial={reducedMotion ? false : { opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} whileHover={reducedMotion ? undefined : { y: -3 }} transition={{ duration: .18 }}><div className={`admin-stat-icon ${tone}`}><LearningIcon name={icon} /></div><Text as="p" color="gray" size="2">{label}</Text><Heading as="h2" size="8">{value}</Heading>{note && <Text as="p" color="gray" size="1">{note}</Text>}</motion.article></Card>;
}
