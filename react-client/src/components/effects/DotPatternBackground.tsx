import { lazy, Suspense } from "react";

const PixelBlast = lazy(() => import("./PixelBlast"));

type DotPatternBackgroundProps = Readonly<{ className?: string }>;

/** Learning Hub's account background: PixelBlast with its original settings. */
export function DotPatternBackground({ className }: DotPatternBackgroundProps) {
  return (
    <div className={`account-dot-pattern ${className ?? ""}`}>
      <Suspense fallback={null}>
        <PixelBlast
          color="#8b5cf6"
          pixelSize={4}
          liquid={false}
          enableRipples={true}
          transparent={true}
          patternScale={2}
          patternDensity={1.65}
          edgeFade={0.35}
          speed={0.4}
        />
      </Suspense>
    </div>
  );
}
