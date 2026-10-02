import type { CSSProperties } from "react";

/** PhysLive logo (the original favicon) with optional wordmark. */
export default function BrandMark({
  size = 28,
  wordmark = true,
  caption,
  className = "",
  style,
}: Readonly<{ size?: number; wordmark?: boolean; caption?: string; className?: string; style?: CSSProperties }>) {
  return (
    <span className={`brand-mark ${className}`} style={style}>
      <img src="/favicon.ico" width={size} height={size} alt="" aria-hidden="true" className="brand-mark__logo" />
      {wordmark && (
        <span className="brand-mark__text">
          PhysLive{caption && <small>{caption}</small>}
        </span>
      )}
    </span>
  );
}
