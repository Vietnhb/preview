import { useEffect, useRef, useState, type CSSProperties, type PointerEvent, type ReactNode } from "react";
import { AnimatePresence, motion, useMotionValue, useReducedMotion, useSpring } from "motion/react";

const ease = [0.22, 1, 0.36, 1] as const;

/** Reveals a heading word by word (blur + lift). Screen readers get the full text once. */
export function SplitWords({ text, className = "", wordClassName, delay = 0, stagger = 0.07 }: Readonly<{ text: string; className?: string; wordClassName?: string; delay?: number; stagger?: number }>) {
  const reduced = useReducedMotion();
  const words = text.split(" ");
  return (
    <span className={className}>
      <span className="sr-only">{text}</span>
      {words.map((word, index) => (
        <motion.span
          key={`${word}-${index}`}
          aria-hidden="true"
          className={wordClassName}
          style={{ display: "inline-block", whiteSpace: "pre" }}
          initial={reduced ? false : { opacity: 0, y: "0.35em", filter: "blur(8px)" }}
          animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
          transition={{ duration: 0.7, ease, delay: delay + index * stagger }}
        >
          {word}{index < words.length - 1 ? " " : ""}
        </motion.span>
      ))}
    </span>
  );
}

/** Fades and lifts content into view once, when it scrolls into the viewport. */
export function Reveal({ children, className = "", delay = 0, as = "div" }: Readonly<{ children: ReactNode; className?: string; delay?: number; as?: "div" | "li" | "section" | "article" }>) {
  const reduced = useReducedMotion();
  const props = {
    className,
    initial: reduced ? false : { opacity: 0, y: 24 },
    whileInView: { opacity: 1, y: 0 },
    viewport: { once: true, amount: 0.2 },
    transition: { duration: 0.6, ease, delay },
  } as const;
  if (as === "li") return <motion.li {...props}>{children}</motion.li>;
  if (as === "section") return <motion.section {...props}>{children}</motion.section>;
  if (as === "article") return <motion.article {...props}>{children}</motion.article>;
  return <motion.div {...props}>{children}</motion.div>;
}

/** Pulls its child slightly toward the pointer, then springs back. */
export function Magnetic({ children, strength = 0.25, className = "" }: Readonly<{ children: ReactNode; strength?: number; className?: string }>) {
  const reduced = useReducedMotion();
  const x = useSpring(useMotionValue(0), { stiffness: 260, damping: 18 });
  const y = useSpring(useMotionValue(0), { stiffness: 260, damping: 18 });
  const ref = useRef<HTMLSpanElement>(null);
  const move = (event: PointerEvent) => {
    if (reduced || !ref.current) return;
    const rect = ref.current.getBoundingClientRect();
    x.set((event.clientX - rect.left - rect.width / 2) * strength);
    y.set((event.clientY - rect.top - rect.height / 2) * strength);
  };
  const leave = () => { x.set(0); y.set(0); };
  return (
    <motion.span ref={ref} className={className} style={{ display: "inline-flex", x, y }} onPointerMove={move} onPointerLeave={leave}>
      {children}
    </motion.span>
  );
}

/** Card with a soft light that follows the pointer (CSS custom properties, no re-render). */
export function SpotlightCard({ children, className = "", style }: Readonly<{ children: ReactNode; className?: string; style?: CSSProperties }>) {
  const move = (event: PointerEvent<HTMLDivElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    event.currentTarget.style.setProperty("--spot-x", `${event.clientX - rect.left}px`);
    event.currentTarget.style.setProperty("--spot-y", `${event.clientY - rect.top}px`);
  };
  return <div className={`spotlight-card ${className}`} style={style} onPointerMove={move}>{children}</div>;
}

/** Endless horizontal ticker. Items are duplicated once so the loop is seamless. */
export function Marquee({ items, className = "" }: Readonly<{ items: string[]; className?: string }>) {
  return (
    <div className={`marquee ${className}`}>
      <span className="sr-only">{items.join(", ")}</span>
      <div className="marquee__track" aria-hidden="true">
        {[...items, ...items].map((item, index) => <span key={`${item}-${index}`}>{item}</span>)}
      </div>
    </div>
  );
}

/** Cycles through phrases with a per-letter roll (react-bits style "rotating text"). */
export function RotatingText({ phrases, interval = 2600, className = "", letterClassName }: Readonly<{ phrases: string[]; interval?: number; className?: string; letterClassName?: string }>) {
  const reduced = useReducedMotion();
  const [index, setIndex] = useState(0);
  useEffect(() => {
    if (reduced || phrases.length < 2) return;
    const timer = window.setInterval(() => setIndex(value => (value + 1) % phrases.length), interval);
    return () => window.clearInterval(timer);
  }, [reduced, phrases.length, interval]);
  const phrase = phrases[index];
  return (
    <span className={`rotating-text ${className}`}>
      <span className="sr-only">{phrases[0]}</span>
      <AnimatePresence mode="wait" initial={false}>
        <motion.span key={phrase} aria-hidden="true" className="rotating-text__phrase">
          {Array.from(phrase).map((char, i) => (
            <motion.span
              key={`${char}-${i}`}
              className={letterClassName}
              style={{ display: "inline-block", whiteSpace: "pre" }}
              initial={{ y: "100%", opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              exit={{ y: "-110%", opacity: 0 }}
              transition={{ type: "spring", damping: 26, stiffness: 380, delay: i * 0.018 }}
            >
              {char}
            </motion.span>
          ))}
        </motion.span>
      </AnimatePresence>
    </span>
  );
}

/** Tilts toward the pointer in 3D and lifts slightly; resets on leave. */
export function TiltCard({ children, className = "", max = 8 }: Readonly<{ children: ReactNode; className?: string; max?: number }>) {
  const reduced = useReducedMotion();
  const rotateX = useSpring(0, { stiffness: 220, damping: 20 });
  const rotateY = useSpring(0, { stiffness: 220, damping: 20 });
  const move = (event: PointerEvent<HTMLDivElement>) => {
    if (reduced) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const px = (event.clientX - rect.left) / rect.width - 0.5;
    const py = (event.clientY - rect.top) / rect.height - 0.5;
    rotateY.set(px * max * 2);
    rotateX.set(-py * max * 2);
    event.currentTarget.style.setProperty("--spot-x", `${event.clientX - rect.left}px`);
    event.currentTarget.style.setProperty("--spot-y", `${event.clientY - rect.top}px`);
  };
  const leave = () => { rotateX.set(0); rotateY.set(0); };
  return (
    <motion.div className={`tilt-card spotlight-card ${className}`} style={{ rotateX, rotateY, transformPerspective: 900 }} onPointerMove={move} onPointerLeave={leave}>
      {children}
    </motion.div>
  );
}

/** Sliding highlight behind the active tab; tabs sharing an `id` animate between each other. */
export function ActivePill({ id, className = "" }: Readonly<{ id: string; className?: string }>) {
  const reduced = useReducedMotion();
  return <motion.span layoutId={id} className={`active-pill ${className}`} aria-hidden="true"
    transition={reduced ? { duration: 0 } : { type: "spring", stiffness: 420, damping: 34 }} />;
}
