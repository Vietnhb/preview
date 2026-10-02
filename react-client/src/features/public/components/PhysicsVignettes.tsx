import s from "./PhysicsVignettes.module.css";

/** Small looping illustrations for the topic bento. Pure SVG + CSS keyframes. */
export function Pendulum() {
  return (
    <svg className={s.svg} viewBox="0 0 200 140" aria-hidden="true">
      <line x1="60" x2="140" y1="14" y2="14" className={s.support} />
      <g className={s.pendulum}>
        <line x1="100" y1="14" x2="100" y2="104" className={s.string} />
        <circle cx="100" cy="112" r="11" className={s.bob} />
      </g>
      <path d="M58 108 A 98 98 0 0 0 142 108" className={s.arc} />
    </svg>
  );
}

export function Wave() {
  const path = Array.from({ length: 61 }, (_, i) => {
    const x = i * 10;
    const y = 70 - Math.sin((i / 60) * Math.PI * 6) * 26;
    return `${i ? "L" : "M"}${x},${y.toFixed(1)}`;
  }).join(" ");
  return (
    <svg className={s.svg} viewBox="0 0 200 140" aria-hidden="true">
      <line x1="0" x2="200" y1="70" y2="70" className={s.axis} />
      <g className={s.wave}><path d={path} className={s.wavePath} /></g>
      <circle cx="100" cy="70" r="5" className={s.probe} />
    </svg>
  );
}

export function Circuit() {
  return (
    <svg className={s.svg} viewBox="0 0 200 140" aria-hidden="true">
      <rect x="30" y="24" width="140" height="92" rx="10" className={s.wire} />
      <rect x="30" y="24" width="140" height="92" rx="10" className={s.current} />
      <g transform="translate(30 70)"><line x1="-8" x2="8" y1="-6" y2="-6" className={s.cell} /><line x1="-4" x2="4" y1="4" y2="4" className={s.cell} /></g>
      <circle cx="170" cy="70" r="13" className={s.bulb} />
      <circle cx="170" cy="70" r="24" className={s.glow} />
    </svg>
  );
}

export function Collision() {
  return (
    <svg className={s.svg} viewBox="0 0 200 140" aria-hidden="true">
      <line x1="10" x2="190" y1="96" y2="96" className={s.axis} />
      <circle cx="40" cy="80" r="16" className={`${s.ball} ${s.ballA}`} />
      <circle cx="104" cy="80" r="16" className={`${s.ball} ${s.ballB}`} />
    </svg>
  );
}
