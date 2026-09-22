import { useEffect, useRef, useState } from "react";
import {
  AnimatePresence,
  LayoutGroup,
  motion,
  useMotionValue,
  useMotionValueEvent,
  useReducedMotion,
  useTransform,
  type MotionValue,
} from "motion/react";
import {
  GRAVITY,
  LAUNCH,
  PLOT,
  projectile,
  trajectory,
} from "./projectileModel";
import s from "./ProjectileExperiment.module.css";

const spring = { type: "spring" as const, stiffness: 380, damping: 30 };
const number = (n: number) =>
  n.toLocaleString("vi-VN", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  });

function Readout({
  value,
  label,
  unit,
}: {
  value: MotionValue<number>;
  label: string;
  unit: string;
}) {
  const ref = useRef<HTMLElement>(null);
  useMotionValueEvent(value, "change", (v) => {
    if (ref.current) ref.current.textContent = number(v);
  });
  return (
    <div>
      <span>{label}</span>
      <strong>
        <b ref={ref}>{number(value.get())}</b>
        <small>{unit}</small>
      </strong>
    </div>
  );
}

export default function ProjectileExperiment() {
  const reduced = useReducedMotion();
  const [angle, setAngle] = useState<number>(LAUNCH.angle.initial);
  const [speed, setSpeed] = useState<number>(LAUNCH.speed.initial);
  const [running, setRunning] = useState(false);
  const [status, setStatus] = useState("Sẵn sàng");
  const [mode, setMode] = useState<"observe" | "compare">("observe");
  const [reference, setReference] = useState({
    angle: LAUNCH.angle.initial as number,
    speed: LAUNCH.speed.initial as number,
  });
  const [formula, setFormula] = useState(false);
  const stage = useRef<HTMLElement>(null);
  const clock = useMotionValue(0);
  const model = projectile(angle, speed, 0);
  const x = useTransform(
    clock,
    (t) => PLOT.left + projectile(angle, speed, t).x * PLOT.scale,
  );
  const y = useTransform(
    clock,
    (t) => PLOT.ground - projectile(angle, speed, t).y * PLOT.scale,
  );
  const height = useTransform(clock, (t) => projectile(angle, speed, t).y);
  const distance = useTransform(clock, (t) => projectile(angle, speed, t).x);
  const vectorX = useTransform(x, (v) => v + model.vx * 3);
  const vectorY = useTransform(
    clock,
    (t) =>
      PLOT.ground -
      projectile(angle, speed, t).y * PLOT.scale -
      projectile(angle, speed, t).vy * 3,
  );
  const trail = useTransform(clock, (t) => trajectory(angle, speed, t));

  useEffect(() => {
    if (!running) return;
    let frame = 0;
    let last = 0;
    let visible = false;
    const tick = (now: number) => {
      if (!visible || document.hidden) {
        last = 0;
        return;
      }
      const next = Math.min(
        model.duration,
        clock.get() + (last ? (now - last) / 1000 : 0),
      );
      last = now;
      clock.set(next);
      if (next >= model.duration) {
        setRunning(false);
        setStatus("Đã chạm đất");
        return;
      }
      frame = requestAnimationFrame(tick);
    };
    const resume = () => {
      cancelAnimationFrame(frame);
      last = 0;
      if (visible && !document.hidden) frame = requestAnimationFrame(tick);
    };
    const observer = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting;
      resume();
    });
    if (stage.current) observer.observe(stage.current);
    document.addEventListener("visibilitychange", resume);
    return () => {
      observer.disconnect();
      cancelAnimationFrame(frame);
      document.removeEventListener("visibilitychange", resume);
    };
  }, [running, clock, model.duration]);

  const reset = () => {
    setRunning(false);
    clock.set(0);
    setStatus("Sẵn sàng");
  };
  const play = () => {
    if (running) {
      setRunning(false);
      setStatus("Tạm dừng");
      return;
    }
    if (clock.get() >= model.duration) clock.set(0);
    setRunning(true);
    setStatus("Đang chạy");
  };
  const chooseMode = (next: "observe" | "compare") => {
    if (next === "compare" && mode !== next) setReference({ angle, speed });
    setMode(next);
  };

  return (
    <section
      ref={stage}
      id="projectile-experiment"
      className={s.lab}
      aria-label="Thí nghiệm ném xiên"
    >
      <header className={s.header}>
        <div>
          <span className={s.index}>01 / CƠ HỌC</span>
          <h2>Ném một vật. Theo dấu chuyển động.</h2>
        </div>
        <LayoutGroup id="projectile-mode">
          <div className={s.modes} aria-label="Chế độ thí nghiệm">
            {(["observe", "compare"] as const).map((v) => (
              <button
                key={v}
                aria-pressed={mode === v}
                onClick={() => chooseMode(v)}
              >
                {mode === v && (
                  <motion.span
                    className={s.selected}
                    layoutId="mode"
                    transition={reduced ? { duration: 0 } : spring}
                  />
                )}
                <span>{v === "observe" ? "Quan sát" : "Đối chiếu"}</span>
              </button>
            ))}
          </div>
        </LayoutGroup>
      </header>
      <div className={s.canvas}>
        <div className={s.telemetry}>
          <span className={s.status} role="status">
            <i data-running={running} />
            {status}
          </span>
          <div className={s.readouts}>
            <Readout value={clock} label="Thời gian" unit="s" />
            <Readout value={height} label="Độ cao" unit="m" />
            <Readout value={distance} label="Vị trí x" unit="m" />
          </div>
        </div>
        <svg
          className={s.graph}
          viewBox="0 0 1000 410"
          role="img"
          aria-label="Quỹ đạo ném xiên với trục x và y tính bằng mét, cùng tỉ lệ"
        >
          <defs>
            <marker
              id="launch-velocity"
              markerWidth="6"
              markerHeight="6"
              refX="5"
              refY="3"
              orient="auto"
            >
              <path d="M0 0L6 3L0 6" fill="none" stroke="#73a9ff" />
            </marker>
          </defs>
          {[0, 10, 20, 30, 40].map((v) => (
            <g key={v}>
              <line
                x1="64"
                x2="944"
                y1={350 - v * PLOT.scale}
                y2={350 - v * PLOT.scale}
                className={s.grid}
              />
              <text x="45" y={355 - v * PLOT.scale} textAnchor="end">
                {v}
              </text>
            </g>
          ))}
          {[0, 20, 40, 60, 80, 100].map((v) => (
            <g key={v}>
              <line
                x1={64 + v * PLOT.scale}
                x2={64 + v * PLOT.scale}
                y1="55"
                y2="350"
                className={s.grid}
              />
              <text x={64 + v * PLOT.scale} y="378" textAnchor="middle">
                {v}
              </text>
            </g>
          ))}
          <text x="28" y="35">
            y (m)
          </text>
          <text x="940" y="402">
            x (m)
          </text>
          <line x1="64" x2="944" y1="350" y2="350" className={s.ground} />
          <AnimatePresence>
            {mode === "compare" && (
              <motion.path
                key="reference"
                d={trajectory(reference.angle, reference.speed)}
                className={s.reference}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: reduced ? 0 : 0.18 }}
              />
            )}
          </AnimatePresence>
          <path d={trajectory(angle, speed)} className={s.prediction} />
          <motion.path d={trail} className={s.trail} />
          <motion.line
            x1={x}
            y1={y}
            x2={vectorX}
            y2={vectorY}
            className={s.vector}
            markerEnd="url(#launch-velocity)"
          />
          <motion.circle cx={x} cy={y} r="9" className={s.ball} />
          <text
            x={PLOT.left + model.range * PLOT.scale}
            y="327"
            textAnchor="middle"
            className={s.landing}
          >
            R = {number(model.range)} m
          </text>
        </svg>
        <div className={s.dock}>
          <label>
            <span>
              Góc ném <output>{angle}°</output>
            </span>
            <input
              aria-label="Góc ném"
              type="range"
              min={LAUNCH.angle.min}
              max={LAUNCH.angle.max}
              step={LAUNCH.angle.step}
              value={angle}
              onChange={(e) => {
                reset();
                setAngle(+e.target.value);
              }}
            />
          </label>
          <label>
            <span>
              Vận tốc đầu <output>{number(speed)} m/s</output>
            </span>
            <input
              aria-label="Vận tốc ban đầu"
              type="range"
              min={LAUNCH.speed.min}
              max={LAUNCH.speed.max}
              step={LAUNCH.speed.step}
              value={speed}
              onChange={(e) => {
                reset();
                setSpeed(+e.target.value);
              }}
            />
          </label>
          <motion.button
            className={s.play}
            onClick={play}
            whileHover={reduced ? undefined : { scale: 1.03 }}
            whileTap={reduced ? undefined : { scale: 0.96 }}
            transition={spring}
          >
            <AnimatePresence mode="wait" initial={false}>
              <motion.span
                key={running ? "pause" : "play"}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: reduced ? 0 : 0.08 }}
              >
                {running ? "Ⅱ Tạm dừng" : "▷ Chạy"}
              </motion.span>
            </AnimatePresence>
          </motion.button>
          <motion.button
            className={s.reset}
            onClick={reset}
            aria-label="Đặt lại"
            title="Đặt lại"
            whileTap={reduced ? undefined : { rotate: -45 }}
            transition={spring}
          >
            ↺
          </motion.button>
        </div>
      </div>
      <motion.div layout={reduced ? false : "position"} className={s.foot}>
        <p>
          {mode === "compare"
            ? "Đường xanh giữ lần thiết lập trước. Thay đổi một thông số để so sánh điểm rơi."
            : "Kéo thanh trượt, hoặc dùng phím mũi tên. Nhấn Chạy để bắt đầu."}
        </p>
        <button
          onClick={() => setFormula(!formula)}
          aria-expanded={formula}
          aria-controls="launch-formula"
        >
          {formula ? "Ẩn" : "Xem"} mô hình{" "}
          <span aria-hidden="true">{formula ? "−" : "+"}</span>
        </button>
      </motion.div>
      <AnimatePresence initial={false}>
        {formula && (
          <motion.div
            id="launch-formula"
            className={s.formula}
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: reduced ? 0 : 0.2 }}
          >
            <p>
              x(t) = v₀ cos(θ)t
              <br />
              y(t) = v₀ sin(θ)t − ½gt²
            </p>
            <p>
              g = {GRAVITY} m/s². Điểm ném và điểm rơi cùng độ cao. Bỏ qua lực
              cản không khí.
            </p>
          </motion.div>
        )}
      </AnimatePresence>
      <p className={s.scope}>
        Thí nghiệm minh họa trên trình duyệt · Bỏ qua lực cản không khí.
      </p>
    </section>
  );
}
