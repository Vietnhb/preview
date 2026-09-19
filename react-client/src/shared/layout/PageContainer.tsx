import type { ComponentPropsWithoutRef } from "react";
import styles from "./PageContainer.module.css";

/** Standard page below the horizontal navigation. Full-screen studios own their layout. */
export default function PageContainer({
  className = "",
  ...props
}: ComponentPropsWithoutRef<"main">) {
  return <main {...props} className={`${styles.container} ${className}`} />;
}
